package com.agentruntime.runtime.run;

/**
 * States in the agent-run lifecycle state machine.
 * Ported from agent-framework model.RunState; adapted for agentruntime.
 *
 * Corresponds to Vol.1 Ch.4 lifecycle phases:
 * perception → prompting → reasoning → action → reflection → termination.
 */
public enum RunState {
    CREATED,
    VALIDATING,
    PLANNING,
    MODEL_CALL,
    TOOL_EXECUTION,
    MEMORY_UPDATE,
    RESPONDING,
    PAUSED,
    COMPLETED,
    FAILED,
    CANCELLED,
    TIMED_OUT;

    public boolean isTerminal() {
        return this == COMPLETED || this == FAILED || this == CANCELLED || this == TIMED_OUT;
    }
}
