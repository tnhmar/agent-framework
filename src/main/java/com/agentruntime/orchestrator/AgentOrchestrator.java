package com.agentruntime.orchestrator;

import com.agentruntime.core.valueobjects.ExecutionContext;
import com.agentruntime.orchestrator.delegation.DelegationContract;
import com.agentruntime.orchestrator.delegation.DelegationResult;

/**
 * V-21 fix: delegate() added per Vol1 Ch.7 §"Delegation to Subagents".
 */
public interface AgentOrchestrator {
    ExecutionResult run(AgentTask task, ExecutionContext ctx);
    void suspend(String executionId);
    void resume(String executionId);
    void terminate(String executionId, String reason);

    /**
     * Validates the delegation contract, enforces the configured depth limit,
     * and performs cycle detection before executing the delegated task.
     * Vol1 Ch.7 §"Delegation to Subagents".
     */
    DelegationResult delegate(DelegationContract contract, ExecutionContext ctx);
}
