package com.agentruntime.memory.session;

import com.agentruntime.memory.checkpoint.CheckpointStore;
import com.agentruntime.memory.checkpoint.TaskCheckpoint;
import com.agentruntime.memory.consolidation.MemoryConsolidator;
import com.agentruntime.memory.episodic.EpisodicStore;
import com.agentruntime.memory.expiry.ExpiryPruner;
import com.agentruntime.memory.working.WorkingMemoryStore;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 4-step session-close handoff — Vol.1 Ch.13 §"The Session-Close Handoff".
 * "The order is non-negotiable."
 *
 * P0-01 fix: each step is wrapped in an independent try/catch so that a failure
 * in Step 1 never prevents Steps 2–4 from executing. All exceptions are logged
 * with context; the report always captures which steps succeeded and which failed.
 *
 * Step 2 now uses CheckpointStore instead of a stub string.
 */
public class SessionLifecycleManager {

    private static final Logger LOG = Logger.getLogger(SessionLifecycleManager.class.getName());

    private final MemoryConsolidator  consolidator;
    private final WorkingMemoryStore  workingMemory;
    private final ExpiryPruner        expiryPruner;
    private final EpisodicStore       episodicStore;
    private final CheckpointStore     checkpointStore;

    public SessionLifecycleManager(
            MemoryConsolidator consolidator,
            WorkingMemoryStore workingMemory,
            ExpiryPruner expiryPruner,
            EpisodicStore episodicStore,
            CheckpointStore checkpointStore) {
        this.consolidator    = Objects.requireNonNull(consolidator);
        this.workingMemory   = Objects.requireNonNull(workingMemory);
        this.expiryPruner    = Objects.requireNonNull(expiryPruner);
        this.episodicStore   = Objects.requireNonNull(episodicStore);
        this.checkpointStore = Objects.requireNonNull(checkpointStore);
    }

    /** Backward-compatible constructor without CheckpointStore. */
    public SessionLifecycleManager(
            MemoryConsolidator consolidator,
            WorkingMemoryStore workingMemory,
            ExpiryPruner expiryPruner,
            EpisodicStore episodicStore) {
        this(consolidator, workingMemory, expiryPruner, episodicStore, new CheckpointStore());
    }

    /**
     * Executes the 4-step session-close handoff in strict, non-negotiable order.
     * Each step is isolated — a failure in one step never prevents the remaining steps.
     */
    public SessionCloseReport onSessionClose(String sessionId, String agentId) {
        Objects.requireNonNull(sessionId, "sessionId must not be null");
        Objects.requireNonNull(agentId,   "agentId must not be null");

        // ── Step 1: Consolidate ───────────────────────────────────────────────
        int  consolidatedCount = 0;
        String step1Error      = null;
        try {
            consolidatedCount = consolidator.consolidate(agentId, 50);
        } catch (Exception e) {
            step1Error = e.getMessage();
            LOG.log(Level.WARNING, "Session-close Step 1 (consolidate) failed for session "
                    + sessionId + ": " + e.getMessage(), e);
        }

        // ── Step 2: Checkpoint task state ─────────────────────────────────────
        String checkpointRef = null;
        String step2Error    = null;
        try {
            TaskCheckpoint cp = TaskCheckpoint.initial(
                    "session-close:" + sessionId, agentId, sessionId,
                    "Session close checkpoint",
                    List.of(),
                    "Session " + sessionId + " closed at " + Instant.now()
                            + " after consolidating " + consolidatedCount + " memories.");
            checkpointStore.save(cp);
            checkpointRef = "session-close:" + sessionId + ":v" + cp.version();
        } catch (Exception e) {
            step2Error = e.getMessage();
            LOG.log(Level.WARNING, "Session-close Step 2 (checkpoint) failed for session "
                    + sessionId + ": " + e.getMessage(), e);
        }

        // ── Step 3: Flush working memory ──────────────────────────────────────
        boolean flushed  = false;
        String step3Error = null;
        try {
            workingMemory.clear();
            flushed = true;
        } catch (Exception e) {
            step3Error = e.getMessage();
            LOG.log(Level.WARNING, "Session-close Step 3 (flush) failed for session "
                    + sessionId + ": " + e.getMessage(), e);
        }

        // ── Step 4: Expire session-scoped records ─────────────────────────────
        int    expiredCount = 0;
        String step4Error   = null;
        try {
            List<String> expired = expiryPruner.findExpired(episodicStore, Instant.now());
            expiredCount = expired.size();
        } catch (Exception e) {
            step4Error = e.getMessage();
            LOG.log(Level.WARNING, "Session-close Step 4 (expire) failed for session "
                    + sessionId + ": " + e.getMessage(), e);
        }

        return new SessionCloseReport(
                sessionId, agentId,
                consolidatedCount, checkpointRef, flushed, expiredCount,
                step1Error, step2Error, step3Error, step4Error);
    }

    /**
     * Vol.1 Ch.13 §"Confidence-Gated Injection": injects UserModel facts into working memory
     * at session start, filtered to confidence >= minConfidence.
     * P3 gap fix: UserModel wired into session start.
     */
    public void onSessionStart(String sessionId,
            com.agentruntime.memory.usermodel.UserModel userModel,
            com.agentruntime.memory.working.WorkingMemoryStore workingMemory,
            double minConfidence) {
        Objects.requireNonNull(sessionId);
        if (userModel == null) return;
        userModel.all().stream()
                .filter(f -> f.confidence().value >= minConfidence)
                .forEach(f -> workingMemory.put(
                        "userModel:" + f.category() + ":" + f.key(), f.value()));
    }

    public record SessionCloseReport(
            String  sessionId,
            String  agentId,
            int     memoriesConsolidated,
            String  taskStateCheckpointRef,
            boolean workingMemoryFlushed,
            int     episodesExpired,
            String  step1Error,
            String  step2Error,
            String  step3Error,
            String  step4Error
    ) {
        /** True only when all 4 steps completed without error. */
        public boolean fullySuccessful() {
            return step1Error == null && step2Error == null
                && step3Error == null && step4Error == null;
        }
    }
}
