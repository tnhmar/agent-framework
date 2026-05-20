package com.agentruntime.orchestrator;

import com.agentruntime.core.enums.AgentStatus;
import com.agentruntime.core.valueobjects.*;
import com.agentruntime.orchestrator.action.*;
import com.agentruntime.orchestrator.delegation.*;
import com.agentruntime.orchestrator.failuredetection.*;
import com.agentruntime.orchestrator.perception.*;
import com.agentruntime.orchestrator.prompting.*;
import com.agentruntime.orchestrator.reasoning.*;
import com.agentruntime.orchestrator.reflection.*;
import com.agentruntime.orchestrator.termination.*;
import com.agentruntime.statemanager.StateManager;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Default agent orchestrator — Vol.1 Ch.4 7-phase lifecycle loop.
 *
 * V-SRP-02: Delegation is handled by DelegationEngine (no longer inline here).
 * The run() method contains only the perception→reasoning→action→reflection loop.
 *
 * Implements all three segregated interfaces (V-ISP-01):
 *   AgentRunner     — run()
 *   AgentDelegate   — delegate()
 *   AgentLifecycle  — suspend(), resume(), terminate()
 */
public class DefaultAgentOrchestrator implements AgentOrchestrator {

    private static final int  MAX_TRANSIENT_RETRIES = 3;
    private static final long RETRY_BASE_MS         = 200L;

    private final PerceptionModule       perceptionModule;
    private final PromptingModule        promptingModule;
    private final ReasoningModule        reasoningModule;
    private final ActionModule           actionModule;
    private final ReflectionModule       reflectionModule;
    private final TerminationModule      terminationModule;
    private final FailureDetectionModule failureDetectionModule;
    private final StateManager           stateManager;
    private final DelegationEngine       delegationEngine;

    public DefaultAgentOrchestrator(
            PerceptionModule       perceptionModule,
            PromptingModule        promptingModule,
            ReasoningModule        reasoningModule,
            ActionModule           actionModule,
            ReflectionModule       reflectionModule,
            TerminationModule      terminationModule,
            FailureDetectionModule failureDetectionModule,
            StateManager           stateManager,
            int                    maxDelegationDepth) {
        this.perceptionModule      = Objects.requireNonNull(perceptionModule);
        this.promptingModule       = Objects.requireNonNull(promptingModule);
        this.reasoningModule       = Objects.requireNonNull(reasoningModule);
        this.actionModule          = Objects.requireNonNull(actionModule);
        this.reflectionModule      = Objects.requireNonNull(reflectionModule);
        this.terminationModule     = Objects.requireNonNull(terminationModule);
        this.failureDetectionModule = Objects.requireNonNull(failureDetectionModule);
        this.stateManager          = Objects.requireNonNull(stateManager);
        this.delegationEngine      = new DelegationEngine(maxDelegationDepth, this, stateManager);
    }

    // ── AgentRunner ───────────────────────────────────────────────────────────

    @Override
    public ExecutionResult run(AgentTask task, ExecutionContext ctx) {
        Objects.requireNonNull(task, "task must not be null");
        Objects.requireNonNull(ctx,  "ctx must not be null");

        stateManager.initialize(ctx.executionId(), ctx.agentIdentity());
        stateManager.updateStatus(ctx.executionId(), AgentStatus.PERCEIVING);

        ActionResult lastAction = null;

        try {
            for (int iteration = 0; iteration < task.maxIterations(); iteration++) {
                // Suspension check
                var stateOpt = stateManager.get(ctx.executionId());
                if (stateOpt.isEmpty()) break;
                if (stateOpt.get().status() == AgentStatus.SUSPENDED) {
                    try { Thread.sleep(50); } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        stateManager.updateStatus(ctx.executionId(), AgentStatus.FAILED);
                        return new ExecutionResult(ctx.executionId(), AgentStatus.FAILED,
                                Map.of(), "Interrupted during suspension", iteration);
                    }
                    continue;
                }

                // Phase 1: Perception
                stateManager.updateStatus(ctx.executionId(), AgentStatus.PERCEIVING);
                var rawInput   = new RawInput(task.goal(), "text", task.inputs());
                var perception = perceptionModule.perceive(rawInput, ctx);

                // Phase 2: Prompting
                promptingModule.build(perception, task.goal(), ctx);

                // Phase 3: Reasoning (with TRANSIENT retry)
                stateManager.updateStatus(ctx.executionId(), AgentStatus.REASONING);
                var agentState = new AgentState(ctx.conversationId(), iteration, Map.of(), task.goal());
                var reasoning  = reasonWithRetry(perception, agentState, ctx);

                // Phase 4: Action
                stateManager.updateStatus(ctx.executionId(), AgentStatus.ACTING);
                lastAction = actionModule.execute(reasoning, ctx);

                // Phase 5: Reflection
                stateManager.updateStatus(ctx.executionId(), AgentStatus.REFLECTING);
                var reflection = reflectionModule.reflect(lastAction, ctx);

                // Phase 6: Termination check
                stateManager.updateStatus(ctx.executionId(), AgentStatus.TERMINATING);
                var decision = terminationModule.evaluate(reflection, iteration,
                        task.maxIterations(), ctx);

                if (decision.shouldTerminate()) {
                    AgentStatus finalStatus = decision.isSuccess() ? AgentStatus.IDLE : AgentStatus.FAILED;
                    stateManager.updateStatus(ctx.executionId(), finalStatus);
                    return new ExecutionResult(ctx.executionId(), finalStatus,
                            lastAction != null ? lastAction.outputs() : Map.of(),
                            decision.reason(), iteration + 1);
                }

                stateManager.incrementIteration(ctx.executionId());
                stateManager.updateStatus(ctx.executionId(), AgentStatus.PERCEIVING);
            }

        } catch (Exception e) {
            var assessment = failureDetectionModule.assess(e, "main-loop", ctx);
            stateManager.updateStatus(ctx.executionId(), AgentStatus.FAILED);
            return new ExecutionResult(ctx.executionId(), AgentStatus.FAILED, Map.of(),
                    "Failure [" + assessment.category() + "]: " + e.getMessage(),
                    task.maxIterations());
        }

        stateManager.updateStatus(ctx.executionId(), AgentStatus.FAILED);
        return new ExecutionResult(ctx.executionId(), AgentStatus.FAILED,
                Map.of(), "Max iterations exhausted", task.maxIterations());
    }

    // ── AgentDelegate ─────────────────────────────────────────────────────────

    @Override
    public DelegationResult delegate(DelegationContract contract, ExecutionContext ctx) {
        return delegationEngine.delegate(contract, ctx);
    }

    // ── AgentLifecycle ────────────────────────────────────────────────────────

    @Override
    public void suspend(String executionId)              { stateManager.updateStatus(executionId, AgentStatus.SUSPENDED); }
    @Override
    public void resume(String executionId)               { stateManager.updateStatus(executionId, AgentStatus.PERCEIVING); }
    @Override
    public void terminate(String executionId, String r)  { stateManager.updateStatus(executionId, AgentStatus.TERMINATING); }

    // ── Private: retry-aware reasoning ───────────────────────────────────────

    private ReasoningResult reasonWithRetry(PerceptionResult perception,
                                             AgentState state, ExecutionContext ctx) {
        for (int attempt = 0; attempt <= MAX_TRANSIENT_RETRIES; attempt++) {
            try {
                ReasoningResult result = reasoningModule.reason(perception, state, ctx);
                if (result.confidence() > 0) return result;
            } catch (Exception e) {
                var assessment = failureDetectionModule.assess(e, "reasoning", ctx);
                if (assessment.category() != FailureCategory.TRANSIENT || attempt == MAX_TRANSIENT_RETRIES) {
                    return new ReasoningResult("Reasoning failed", List.of("retry_or_escalate"),
                            e.getMessage(), 0.0, assessment.requiresEscalation());
                }
                try { Thread.sleep(RETRY_BASE_MS * (1L << attempt)); }
                catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return new ReasoningResult("Interrupted", List.of("retry_or_escalate"),
                            "Interrupted during retry", 0.0, true);
                }
            }
        }
        return new ReasoningResult("Reasoning failed after retries",
                List.of("retry_or_escalate"), "All retries exhausted", 0.0, true);
    }
}
