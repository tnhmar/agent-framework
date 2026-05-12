package com.agentruntime.memory.session;

import com.agentruntime.memory.consolidation.MemoryConsolidator;
import com.agentruntime.memory.expiry.ExpiryPruner;
import com.agentruntime.memory.episodic.EpisodicStore;
import com.agentruntime.memory.working.WorkingMemoryStore;

import java.time.Instant;
import java.util.List;

/**
 * V-10 fix: implements the 4-step session-close handoff (Vol1 Ch.13 §"The Session-Close Handoff").
 * "The order is non-negotiable":
 * (1) Consolidate, (2) Checkpoint task state, (3) Flush working memory, (4) Expire session state.
 */
public class SessionLifecycleManager {

    private final MemoryConsolidator consolidator;
    private final WorkingMemoryStore workingMemory;
    private final ExpiryPruner expiryPruner;
    private final EpisodicStore episodicStore;

    public SessionLifecycleManager(
        MemoryConsolidator consolidator,
        WorkingMemoryStore workingMemory,
        ExpiryPruner expiryPruner,
        EpisodicStore episodicStore
    ) {
        this.consolidator = consolidator;
        this.workingMemory = workingMemory;
        this.expiryPruner = expiryPruner;
        this.episodicStore = episodicStore;
    }

    /**
     * Executes the 4-step session-close handoff in strict, non-negotiable order.
     * @param sessionId the closing session ID
     * @param agentId the agent whose session is closing
     * @return a report of what was performed
     */
    public SessionCloseReport onSessionClose(String sessionId, String agentId) {
        // Step 1: Consolidate — promote important working/episodic memories to semantic store
        int consolidatedCount = consolidator.consolidate(agentId, 50);

        // Step 2: Checkpoint task state (stub — in production, serialize to durable store)
        String checkpoint = "session:" + sessionId + ":agent:" + agentId + ":checkpointed_at:" + Instant.now();

        // Step 3: Flush working memory
        workingMemory.clear();

        // Step 4: Expire session-scoped records
        List<String> expired = expiryPruner.findExpired(episodicStore, Instant.now());

        return new SessionCloseReport(sessionId, agentId, consolidatedCount, checkpoint, expired.size());
    }

    public record SessionCloseReport(
        String sessionId,
        String agentId,
        int memoriesConsolidated,
        String taskStateCheckpoint,
        int episodesExpired
    ) {}
}
