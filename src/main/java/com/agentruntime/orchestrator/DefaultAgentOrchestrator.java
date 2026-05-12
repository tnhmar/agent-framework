package com.agentruntime.orchestrator;

import com.agentruntime.core.enums.AgentStatus;
import com.agentruntime.core.valueobjects.*;
import com.agentruntime.orchestrator.action.*;
import com.agentruntime.orchestrator.delegation.DelegationContract;
import com.agentruntime.orchestrator.delegation.DelegationResult;
import com.agentruntime.orchestrator.failuredetection.*;
import com.agentruntime.orchestrator.perception.*;
import com.agentruntime.orchestrator.prompting.*;
import com.agentruntime.orchestrator.reasoning.*;
import com.agentruntime.orchestrator.reflection.*;
import com.agentruntime.orchestrator.termination.*;
import com.agentruntime.statemanager.StateManager;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Default agent orchestrator — implements the Vol.1 Ch.4 7-phase lifecycle loop:
 * perception → prompting → reasoning → action → reflection → termination check → repeat.
 *
 * StateManager is the single source of truth for execution status (Drift-03 fix).
 * delegate() validates contracts, enforces depth limit, and detects cycles (V-21 fix).
 */
public class DefaultAgentOrchestrator implements AgentOrchestrator {

    private final PerceptionModule      perceptionModule;
    private final PromptingModule       promptingModule;
    private final ReasoningModule       reasoningModule;
    private final ActionModule          actionModule;
    private final ReflectionModule      reflectionModule;
    private final TerminationModule     terminationModule;
    private final FailureDetectionModule failureDetectionModule;
    private final StateManager          stateManager;
    private final int                   maxDelegationDepth;

    // Cycle detection: tracks active agent IDs per root execution
    private final Map<String, Set<String>> activeDelegationChains = new ConcurrentHashMap<>();

    public DefaultAgentOrchestrator(
            PerceptionModule      perceptionModule,
            PromptingModule       promptingModule,
            ReasoningModule       reasoningModule,
            ActionModule          actionModule,
            ReflectionModule      reflectionModule,
            TerminationModule     terminationModule,
            FailureDetectionModule failureDetectionModule,
            StateManager          stateManager,
            int                   maxDelegationDepth) {
        this.perceptionModule      = perceptionModule;
        this.promptingModule       = promptingModule;
        this.reasoningModule       = reasoningModule;
        this.actionModule          = actionModule;
        this.reflectionModule      = reflectionModule;
        this.terminationModule     = terminationModule;
        this.failureDetectionModule = failureDetectionModule;
        this.stateManager          = stateManager;
        this.maxDelegationDepth    = maxDelegationDepth;
    }

    @Override
    public ExecutionResult run(AgentTask task, ExecutionContext ctx) {
        stateManager.initialize(ctx.executionId(), ctx.agentIdentity());
        stateManager.updateStatus(ctx.executionId(), AgentStatus.PERCEIVING);

        int iteration = 0;
        ActionResult lastAction = null;

        try {
            while (iteration < task.maxIterations()) {

                // ── Suspension check ──────────────────────────────────────────
                var stateOpt = stateManager.get(ctx.executionId());
                if (stateOpt.isEmpty()) break;
                AgentStatus status = stateOpt.get().status();
                if (status == AgentStatus.SUSPENDED) {
                    try {
                        Thread.sleep(50);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        stateManager.updateStatus(ctx.executionId(), AgentStatus.FAILED);
                        return new ExecutionResult(ctx.executionId(), AgentStatus.FAILED,
                                Map.of(), "Interrupted during suspension", iteration);
                    }
                    continue;
                }

                // ── Phase 1: Perception ───────────────────────────────────────
                stateManager.updateStatus(ctx.executionId(), AgentStatus.PERCEIVING);
                var rawInput   = new RawInput(task.goal(), "text", task.inputs());
                var perception = perceptionModule.perceive(rawInput, ctx);

                // ── Phase 2: Prompting ────────────────────────────────────────
                promptingModule.build(perception, task.goal(), ctx);

                // ── Phase 3: Reasoning ────────────────────────────────────────
                stateManager.updateStatus(ctx.executionId(), AgentStatus.REASONING);
                var agentState = new AgentState(
                        ctx.conversationId(), iteration,
                        Map.of(), task.goal());
                var reasoning = reasoningModule.reason(perception, agentState, ctx);

                // ── Phase 4: Action ───────────────────────────────────────────
                stateManager.updateStatus(ctx.executionId(), AgentStatus.ACTING);
                lastAction = actionModule.execute(reasoning, ctx);

                // ── Phase 5: Reflection ───────────────────────────────────────
                stateManager.updateStatus(ctx.executionId(), AgentStatus.REFLECTING);
                var reflection = reflectionModule.reflect(lastAction, ctx);

                // ── Phase 6: Termination check ────────────────────────────────
                stateManager.updateStatus(ctx.executionId(), AgentStatus.TERMINATING);
                var decision = terminationModule.evaluate(
                        reflection, iteration, task.maxIterations(), ctx);

                if (decision.shouldTerminate()) {
                    AgentStatus finalStatus = decision.isSuccess()
                            ? AgentStatus.IDLE : AgentStatus.FAILED;
                    stateManager.updateStatus(ctx.executionId(), finalStatus);
                    return new ExecutionResult(
                            ctx.executionId(), finalStatus,
                            lastAction != null ? lastAction.outputs() : Map.of(),
                            decision.reason(), iteration + 1);
                }

                // ── Phase 7: Iterate ──────────────────────────────────────────
                stateManager.incrementIteration(ctx.executionId());
                stateManager.updateStatus(ctx.executionId(), AgentStatus.PERCEIVING);
                iteration++;
            }

        } catch (Exception e) {
            var assessment = failureDetectionModule.assess(e, "main-loop", ctx);
            stateManager.updateStatus(ctx.executionId(), AgentStatus.FAILED);
            return new ExecutionResult(ctx.executionId(), AgentStatus.FAILED,
                    Map.of(), "Failure [" + assessment.errorType() + "]: " + e.getMessage(),
                    iteration);
        }

        stateManager.updateStatus(ctx.executionId(), AgentStatus.FAILED);
        return new ExecutionResult(ctx.executionId(), AgentStatus.FAILED,
                Map.of(), "Max iterations exhausted", task.maxIterations());
    }

    /**
     * V-21 fix: validates contract, enforces depth limit, detects cycles.
     * Vol.1 Ch.7 §"Delegation to Subagents".
     */
    @Override
    public DelegationResult delegate(DelegationContract contract, ExecutionContext ctx) {
        // Depth limit
        if (contract.delegationDepth() >= maxDelegationDepth) {
            return DelegationResult.failure(contract.contractId(),
                    "Delegation depth limit exceeded: max=" + maxDelegationDepth
                    + " current=" + contract.delegationDepth(),
                    contract.delegationDepth());
        }

        // Cycle detection
        String rootExecution = ctx.executionId();
        Set<String> chain = activeDelegationChains
                .computeIfAbsent(rootExecution, k -> ConcurrentHashMap.newKeySet());
        String targetId    = contract.targetAgent().agentId();
        String delegatorId = contract.delegatingAgent().agentId();

        if (chain.contains(targetId)) {
            return DelegationResult.failure(contract.contractId(),
                    "Delegation cycle detected: agent " + targetId
                    + " is already in the active chain",
                    contract.delegationDepth());
        }

        chain.add(delegatorId);
        chain.add(targetId);

        try {
            stateManager.updateStatus(ctx.executionId(), AgentStatus.DELEGATING);

            var subTask = new AgentTask(
                    contract.contractId(),
                    contract.taskSpecification(),
                    contract.contextPayload(),
                    10);
            var subCtx    = ExecutionContext.of(contract.targetAgent());
            var subResult = run(subTask, subCtx);

            stateManager.updateStatus(ctx.executionId(), AgentStatus.ACTING);
            return subResult.finalStatus() == AgentStatus.IDLE
                    ? DelegationResult.success(contract.contractId(),
                            subResult.outputs(), contract.delegationDepth())
                    : DelegationResult.failure(contract.contractId(),
                            subResult.terminationReason(), contract.delegationDepth());
        } finally {
            chain.remove(targetId);
            if (chain.isEmpty()) activeDelegationChains.remove(rootExecution);
        }
    }

    @Override
    public void suspend(String executionId) {
        stateManager.updateStatus(executionId, AgentStatus.SUSPENDED);
    }

    @Override
    public void resume(String executionId) {
        stateManager.updateStatus(executionId, AgentStatus.PERCEIVING);
    }

    @Override
    public void terminate(String executionId, String reason) {
        stateManager.updateStatus(executionId, AgentStatus.TERMINATING);
    }
}
