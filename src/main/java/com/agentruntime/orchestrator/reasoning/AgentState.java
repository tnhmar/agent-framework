package com.agentruntime.orchestrator.reasoning;

import java.util.Map;

/**
 * Snapshot of agent state passed to the reasoning module each cycle.
 */
public record AgentState(String conversationId, int currentIteration,
                         Map<String, Object> workingMemorySnapshot, String lastGoal) {

    /** Alias: the current goal (same as lastGoal for backwards compat). */
    public String currentGoal() { return lastGoal != null ? lastGoal : ""; }

    /** Alias: number of iterations completed so far. */
    public int stepCount() { return currentIteration; }
}
