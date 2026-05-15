package com.agentruntime.orchestrator.reasoning;

import com.agentruntime.statemanager.BeliefState;
import com.agentruntime.statemanager.GoalStack;

import java.util.Map;

/**
 * Snapshot of agent state passed to the reasoning module each cycle.
 *
 * P1-08/P1-09 fix: GoalStack and BeliefState are now first-class fields,
 * wiring them into the orchestrator loop per Vol.1 Ch.8.
 */
public record AgentState(
        String              conversationId,
        int                 currentIteration,
        Map<String, Object> workingMemorySnapshot,
        String              lastGoal,
        GoalStack           goalStack,
        BeliefState         beliefState
) {
    public AgentState {
        workingMemorySnapshot = workingMemorySnapshot != null
                ? Map.copyOf(workingMemorySnapshot) : Map.of();
        // goalStack and beliefState are nullable — default to empty instances
    }

    /** Minimal constructor for backwards compatibility (no GoalStack/BeliefState). */
    public AgentState(String conversationId, int currentIteration,
                      Map<String, Object> workingMemorySnapshot, String lastGoal) {
        this(conversationId, currentIteration, workingMemorySnapshot, lastGoal,
                new GoalStack(), new BeliefState());
    }

    public String currentGoal() { return lastGoal != null ? lastGoal : ""; }
    public int    stepCount()   { return currentIteration; }
}
