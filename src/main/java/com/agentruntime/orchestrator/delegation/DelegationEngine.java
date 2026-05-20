package com.agentruntime.orchestrator.delegation;

import com.agentruntime.core.enums.AgentStatus;
import com.agentruntime.core.valueobjects.ExecutionContext;
import com.agentruntime.orchestrator.*;
import com.agentruntime.statemanager.StateManager;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Encapsulates delegation logic: depth limit, cycle detection, sub-agent invocation.
 *
 * V-SRP-02 FIX: Delegation responsibility extracted from DefaultAgentOrchestrator.
 * The orchestrator now delegates to this class, keeping the main run() loop clean.
 *
 * Vol.1 Ch.7 §"Delegation to Subagents" (V-21 fix).
 */
public class DelegationEngine {

    private final int                          maxDepth;
    private final AgentRunner                  runner;
    private final StateManager                 stateManager;
    private final Map<String, Set<String>>     activeChains = new ConcurrentHashMap<>();

    public DelegationEngine(int maxDepth, AgentRunner runner, StateManager stateManager) {
        if (maxDepth < 1) throw new IllegalArgumentException("maxDepth must be >= 1");
        this.maxDepth     = Objects.requireNonNull(maxDepth + "", "") != null ? maxDepth : 5;
        this.runner       = Objects.requireNonNull(runner,       "runner must not be null");
        this.stateManager = Objects.requireNonNull(stateManager, "stateManager must not be null");
    }

    public DelegationResult delegate(DelegationContract contract, ExecutionContext ctx) {
        Objects.requireNonNull(contract, "contract must not be null");
        Objects.requireNonNull(ctx,      "ctx must not be null");

        if (contract.delegationDepth() >= maxDepth)
            return DelegationResult.failure(contract.contractId(),
                    "Delegation depth limit exceeded: max=" + maxDepth
                    + " current=" + contract.delegationDepth(), contract.delegationDepth());

        String rootId  = ctx.executionId();
        Set<String> chain = activeChains.computeIfAbsent(rootId, k -> ConcurrentHashMap.newKeySet());
        String targetId   = contract.targetAgent().agentId();
        String delegatorId = contract.delegatingAgent().agentId();

        if (chain.contains(targetId))
            return DelegationResult.failure(contract.contractId(),
                    "Delegation cycle detected: agent " + targetId + " is already active",
                    contract.delegationDepth());

        chain.add(delegatorId);
        chain.add(targetId);
        stateManager.updateStatus(ctx.executionId(), AgentStatus.DELEGATING);

        try {
            AgentTask subTask = new AgentTask(
                    contract.contractId(), contract.taskSpecification(),
                    contract.contextPayload(), 10);
            ExecutionContext subCtx = ExecutionContext.of(contract.targetAgent());
            ExecutionResult result  = runner.run(subTask, subCtx);

            stateManager.updateStatus(ctx.executionId(), AgentStatus.ACTING);
            return result.finalStatus() == AgentStatus.IDLE
                    ? DelegationResult.success(contract.contractId(),
                            result.outputs(), contract.delegationDepth())
                    : DelegationResult.failure(contract.contractId(),
                            result.terminationReason(), contract.delegationDepth());
        } finally {
            chain.remove(targetId);
            if (chain.isEmpty()) activeChains.remove(rootId);
        }
    }
}
