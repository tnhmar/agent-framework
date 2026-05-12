package com.agentruntime.statemanager;

import java.util.*;

/**
 * A single entry in the agent goal stack.
 * Vol.1 Ch.8 §"Goal Stack and Subgoal Tracking":
 * "Each goal stack entry contains: a goal identifier, a parent reference,
 *  a status field (PENDING, ACTIVE, COMPLETED, FAILED, DEFERRED),
 *  success criteria, known dependencies, and allocated token and step budget."
 */
public record GoalEntry(
        String      goalId,
        String      parentGoalId,      // null for top-level goal
        String      description,
        GoalStatus  status,
        String      successCriteria,
        List<String> dependencies,
        int         allocatedSteps,
        int         allocatedTokens,
        String      resumeCondition    // non-null only when DEFERRED
) {
    public GoalEntry {
        Objects.requireNonNull(goalId,      "goalId must not be null");
        Objects.requireNonNull(description, "description must not be null");
        Objects.requireNonNull(status,      "status must not be null");
        dependencies = dependencies != null ? List.copyOf(dependencies) : List.of();
    }

    /** Return a copy of this entry with an updated status. */
    public GoalEntry withStatus(GoalStatus newStatus) {
        return new GoalEntry(goalId, parentGoalId, description, newStatus,
                successCriteria, dependencies, allocatedSteps, allocatedTokens, resumeCondition);
    }

    /** Return a copy of this entry with a resume condition (sets status to DEFERRED). */
    public GoalEntry deferred(String resumeCondition) {
        return new GoalEntry(goalId, parentGoalId, description, GoalStatus.DEFERRED,
                successCriteria, dependencies, allocatedSteps, allocatedTokens, resumeCondition);
    }
}
