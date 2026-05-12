package com.agentruntime.statemanager;

/**
 * Status values for entries in the goal stack.
 * Vol.1 Ch.8 §"Goal Stack and Subgoal Tracking":
 * "PENDING, ACTIVE, COMPLETED, FAILED, DEFERRED"
 */
public enum GoalStatus {
    PENDING,    // created but not yet started
    ACTIVE,     // currently being pursued
    COMPLETED,  // success criteria met
    FAILED,     // could not be achieved
    DEFERRED    // suspended with a resume condition
}
