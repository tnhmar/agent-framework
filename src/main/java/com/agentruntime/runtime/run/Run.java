package com.agentruntime.runtime.run;

import java.time.Clock;
import java.time.Instant;
import java.util.*;

/**
 * Durable run aggregate with enforced state-machine invariants.
 *
 * Every state change goes through transition() — no direct field mutations.
 * Satisfies Vol.1 Ch.4 §4.5 "durable state for retry resilience".
 */
public final class Run {

    private final RunId    runId;
    private final String   sessionId;
    private final String   agentId;
    private final Instant  startedAt;

    private RunState state;
    private Instant  updatedAt;
    private String   finalResponse;
    private String   failureReason;

    private final Map<String, Object> workingMemory = new HashMap<>();

    // ── Legal state-machine transition table ──────────────────────────────────
    private static final Map<RunState, Set<RunState>> LEGAL = buildLegalTransitions();

    private static Map<RunState, Set<RunState>> buildLegalTransitions() {
        Map<RunState, Set<RunState>> m = new EnumMap<>(RunState.class);
        m.put(RunState.CREATED,        Set.of(RunState.VALIDATING, RunState.CANCELLED));
        m.put(RunState.VALIDATING,     Set.of(RunState.PLANNING,   RunState.FAILED, RunState.CANCELLED));
        m.put(RunState.PLANNING,       Set.of(RunState.MODEL_CALL, RunState.TOOL_EXECUTION,
                                              RunState.RESPONDING, RunState.FAILED,
                                              RunState.PAUSED,     RunState.CANCELLED));
        m.put(RunState.MODEL_CALL,     Set.of(RunState.PLANNING,   RunState.FAILED,
                                              RunState.PAUSED,     RunState.CANCELLED));
        m.put(RunState.TOOL_EXECUTION, Set.of(RunState.MEMORY_UPDATE, RunState.FAILED,
                                              RunState.PAUSED,     RunState.CANCELLED));
        m.put(RunState.MEMORY_UPDATE,  Set.of(RunState.PLANNING,   RunState.RESPONDING,
                                              RunState.FAILED,     RunState.PAUSED,
                                              RunState.CANCELLED));
        m.put(RunState.RESPONDING,     Set.of(RunState.COMPLETED,  RunState.FAILED,
                                              RunState.CANCELLED));
        m.put(RunState.PAUSED,         Set.of(RunState.PLANNING,   RunState.CANCELLED,
                                              RunState.TIMED_OUT));
        return Collections.unmodifiableMap(m);
    }

    // ── Construction ──────────────────────────────────────────────────────────

    public Run(RunId runId, String sessionId, String agentId, Clock clock) {
        this.runId     = Objects.requireNonNull(runId,     "runId must not be null");
        this.sessionId = Objects.requireNonNull(sessionId, "sessionId must not be null");
        this.agentId   = Objects.requireNonNull(agentId,   "agentId must not be null");
        this.state     = RunState.CREATED;
        this.startedAt = clock.instant();
        this.updatedAt = this.startedAt;
    }

    // ── State-machine transitions ─────────────────────────────────────────────

    public void transition(RunState next, Clock clock) {
        Objects.requireNonNull(next,  "next state must not be null");
        Objects.requireNonNull(clock, "clock must not be null");
        if (state.isTerminal()) {
            throw new IllegalStateException(
                    "Run " + runId + " is already terminal (" + state + "); cannot transition to " + next);
        }
        Set<RunState> allowed = LEGAL.get(state);
        if (allowed == null || !allowed.contains(next)) {
            throw new IllegalStateException(
                    "Illegal transition " + state + " → " + next + " for run " + runId);
        }
        this.state     = next;
        this.updatedAt = clock.instant();
    }

    public void complete(String response, Clock clock) {
        this.finalResponse = response;
        transition(RunState.COMPLETED, clock);
    }

    public void fail(String reason, Clock clock) {
        this.failureReason = reason;
        transition(RunState.FAILED, clock);
    }

    public void cancel(Clock clock) {
        transition(RunState.CANCELLED, clock);
    }

    public void pause(Clock clock) {
        transition(RunState.PAUSED, clock);
    }

    public void timeout(String reason, Clock clock) {
        this.failureReason = reason;
        transition(RunState.TIMED_OUT, clock);
    }

    // ── Working-memory helpers ─────────────────────────────────────────────────

    public void putWorkingMemory(String key, Object value) {
        Objects.requireNonNull(key, "key must not be null");
        workingMemory.put(key, value);
    }

    public void putAllWorkingMemory(Map<String, Object> updates) {
        Objects.requireNonNull(updates);
        workingMemory.putAll(updates);
    }

    public Map<String, Object> workingMemory() {
        return Collections.unmodifiableMap(workingMemory);
    }

    // ── Snapshotting ──────────────────────────────────────────────────────────

    public RunSnapshot toSnapshot() {
        return new RunSnapshot(runId, sessionId, agentId, state, startedAt, updatedAt,
                Optional.ofNullable(finalResponse),
                Optional.ofNullable(failureReason));
    }

    // ── Accessors ─────────────────────────────────────────────────────────────

    public RunId    runId()         { return runId; }
    public String   sessionId()     { return sessionId; }
    public String   agentId()       { return agentId; }
    public RunState state()         { return state; }
    public Instant  startedAt()     { return startedAt; }
    public Instant  updatedAt()     { return updatedAt; }
    public String   finalResponse() { return finalResponse; }
    public String   failureReason() { return failureReason; }
}
