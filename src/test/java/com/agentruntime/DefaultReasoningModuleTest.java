package com.agentruntime;

import com.agentruntime.core.valueobjects.AgentIdentity;
import com.agentruntime.core.valueobjects.ExecutionContext;
import com.agentruntime.modelclient.*;
import com.agentruntime.orchestrator.perception.PerceptionResult;
import com.agentruntime.orchestrator.reasoning.*;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for DefaultReasoningModule with stub ModelClient.
 * Covers: stub mode, model-client wired mode, graceful error degradation.
 */
class DefaultReasoningModuleTest {

    private ExecutionContext ctx() {
        return ExecutionContext.of(AgentIdentity.of("test-agent", "orchestrator"));
    }

    private PerceptionResult perception(String content) {
        return new PerceptionResult(content, List.of("entity-1"), Map.of(), 0.9);
    }

    private AgentState state(String goal, int iteration) {
        return new AgentState("conv-1", iteration, Map.of(), goal);
    }

    // ── Stub mode (no ModelClient) ────────────────────────────────────────────

    @Test
    void stubMode_returnsNonNullResult() {
        DefaultReasoningModule module = new DefaultReasoningModule();
        ReasoningResult result = module.reason(perception("hello"), state("do something", 0), ctx());
        assertNotNull(result);
    }

    @Test
    void stubMode_planIsNonBlank() {
        DefaultReasoningModule module = new DefaultReasoningModule();
        ReasoningResult result = module.reason(perception("hello"), state("goal", 0), ctx());
        assertFalse(result.plan().isBlank());
    }

    @Test
    void stubMode_selectedActionsNonEmpty() {
        DefaultReasoningModule module = new DefaultReasoningModule();
        ReasoningResult result = module.reason(perception("test"), state("goal", 0), ctx());
        assertFalse(result.selectedActions().isEmpty());
    }

    @Test
    void stubMode_confidenceInRange() {
        DefaultReasoningModule module = new DefaultReasoningModule();
        ReasoningResult result = module.reason(perception("test"), state("goal", 0), ctx());
        assertTrue(result.confidence() >= 0.0 && result.confidence() <= 1.0);
    }

    @Test
    void stubMode_doesNotRequireHumanInputByDefault() {
        DefaultReasoningModule module = new DefaultReasoningModule();
        ReasoningResult result = module.reason(perception("test"), state("goal", 0), ctx());
        assertFalse(result.requiresHumanInput());
    }

    // ── With StubModelClient ──────────────────────────────────────────────────

    @Test
    void withStubModelClient_parsesStructuredResponse() throws ModelClientException {
        String modelResponse = """
                PLAN: Retrieve documents and summarize
                ACTIONS: retrieve_context, generate_response
                RATIONALE: Standard retrieval-augmented generation pattern
                CONFIDENCE: 0.92
                HUMAN_INPUT: false
                """;
        ModelClient client = (prompt, ctx1) -> new ModelOutput(modelResponse, List.of(), Map.of());

        DefaultReasoningModule module = new DefaultReasoningModule(client);
        ReasoningResult result = module.reason(perception("input"), state("goal", 0), ctx());

        assertEquals("Retrieve documents and summarize", result.plan());
        assertTrue(result.selectedActions().contains("retrieve_context"));
        assertTrue(result.selectedActions().contains("generate_response"));
        assertEquals(0.92, result.confidence(), 0.001);
        assertFalse(result.requiresHumanInput());
    }

    @Test
    void withStubModelClient_hitlTrueTriggersHumanInput() throws ModelClientException {
        String modelResponse = "PLAN: Escalate\nACTIONS: escalate\nRATIONALE: Needs review\nCONFIDENCE: 0.5\nHUMAN_INPUT: true";
        ModelClient client = (prompt, ctx1) -> new ModelOutput(modelResponse, List.of(), Map.of());

        DefaultReasoningModule module = new DefaultReasoningModule(client);
        ReasoningResult result = module.reason(perception("edge case"), state("edge goal", 1), ctx());
        assertTrue(result.requiresHumanInput());
    }

    @Test
    void withStubModelClient_callsModelWithSessionContext() throws ModelClientException {
        var capturedContext = new ModelRequestContext[1];
        ModelClient client = (prompt, ctx1) -> {
            capturedContext[0] = ctx1;
            return new ModelOutput("PLAN: p\nACTIONS: a\nRATIONALE: r\nCONFIDENCE: 0.9\nHUMAN_INPUT: false",
                    List.of(), Map.of());
        };

        ExecutionContext execCtx = ctx();
        DefaultReasoningModule module = new DefaultReasoningModule(client);
        module.reason(perception("test"), state("g", 0), execCtx);

        assertNotNull(capturedContext[0]);
        assertEquals(execCtx.executionId(), capturedContext[0].sessionId());
    }

    @Test
    void withModelClientException_retryable_returnsLowConfidenceFallback() {
        ModelClient client = (prompt, ctx1) -> { throw new ModelClientException("timeout", true); };

        DefaultReasoningModule module = new DefaultReasoningModule(client);
        ReasoningResult result = module.reason(perception("test"), state("goal", 0), ctx());

        assertEquals(0.0, result.confidence());
        assertFalse(result.requiresHumanInput()); // retryable → no HITL
    }

    @Test
    void withModelClientException_notRetryable_requiresHumanInput() {
        ModelClient client = (prompt, ctx1) -> { throw new ModelClientException("auth error", false); };

        DefaultReasoningModule module = new DefaultReasoningModule(client);
        ReasoningResult result = module.reason(perception("test"), state("goal", 0), ctx());

        assertEquals(0.0, result.confidence());
        assertTrue(result.requiresHumanInput()); // non-retryable → HITL
    }

    @Test
    void withMalformedModelResponse_usesDefaults() throws ModelClientException {
        ModelClient client = (prompt, ctx1) -> new ModelOutput("not structured at all", List.of(), Map.of());

        DefaultReasoningModule module = new DefaultReasoningModule(client);
        ReasoningResult result = module.reason(perception("test"), state("goal", 0), ctx());

        assertNotNull(result.plan());
        assertFalse(result.selectedActions().isEmpty());
        assertTrue(result.confidence() > 0);
    }

    // ── AgentState helpers ────────────────────────────────────────────────────

    @Test
    void agentState_currentGoalAliasesLastGoal() {
        AgentState state = new AgentState("conv", 3, Map.of(), "my-goal");
        assertEquals("my-goal", state.currentGoal());
        assertEquals(3, state.stepCount());
    }

    @Test
    void agentState_nullGoalReturnsEmptyString() {
        AgentState state = new AgentState("conv", 0, Map.of(), null);
        assertEquals("", state.currentGoal());
    }
}
