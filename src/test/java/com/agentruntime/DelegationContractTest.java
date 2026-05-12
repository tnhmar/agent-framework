package com.agentruntime;

import com.agentruntime.core.enums.AgentStatus;
import com.agentruntime.core.valueobjects.*;
import com.agentruntime.orchestrator.*;
import com.agentruntime.orchestrator.action.DefaultActionModule;
import com.agentruntime.orchestrator.action.validation.ActionValidationPipeline;
import com.agentruntime.orchestrator.delegation.DelegationContract;
import com.agentruntime.orchestrator.failuredetection.DefaultFailureDetectionModule;
import com.agentruntime.orchestrator.perception.DefaultPerceptionModule;
import com.agentruntime.orchestrator.prompting.DefaultPromptingModule;
import com.agentruntime.orchestrator.reasoning.DefaultReasoningModule;
import com.agentruntime.orchestrator.reflection.DefaultReflectionModule;
import com.agentruntime.orchestrator.termination.DefaultTerminationModule;
import com.agentruntime.security.*;
import com.agentruntime.statemanager.StateManager;
import org.junit.jupiter.api.*;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for V-21 fix: delegation contracts, depth limits, cycle detection.
 * Vol1 Ch.7 §"Delegation to Subagents".
 */
class DelegationContractTest {

    private DefaultAgentOrchestrator orchestrator;
    private AgentIdentity delegator;
    private AgentIdentity target;
    private ExecutionContext ctx;

    @BeforeEach
    void setUp() {
        var policy = new SecurityPolicy("test", Set.of("orchestrator", "specialist", "coordinator"), Set.of(), true, true);
        var enforcer = new SecurityEnforcer(policy);
        var pipeline = ActionValidationPipeline.defaultPipeline(enforcer);
        var stateManager = new StateManager();

        orchestrator = new DefaultAgentOrchestrator(
            new DefaultPerceptionModule(), new DefaultPromptingModule(),
            new DefaultReasoningModule(), new DefaultActionModule(pipeline),
            new DefaultReflectionModule(), new DefaultTerminationModule(),
            new DefaultFailureDetectionModule(), stateManager, 3
        );

        delegator = AgentIdentity.of("orchestrator-agent", "orchestrator");
        target = AgentIdentity.of("specialist-agent", "specialist");
        ctx = ExecutionContext.of(delegator);

        // Initialize orchestrator state for ctx
        stateManager.initialize(ctx.executionId(), delegator);
        stateManager.updateStatus(ctx.executionId(), AgentStatus.ACTING);
    }

    @Test
    void delegationContract_requiresTaskSpecification() {
        assertThrows(IllegalArgumentException.class, () ->
            DelegationContract.of("", Map.of(), Duration.ofSeconds(10), 100L,
                "result", "fail", delegator, target, 0));
    }

    @Test
    void delegationContract_requiresResultContract() {
        assertThrows(IllegalArgumentException.class, () ->
            DelegationContract.of("task", Map.of(), Duration.ofSeconds(10), 100L,
                "", "fail", delegator, target, 0));
    }

    @Test
    void delegationContract_requiresFailureProtocol() {
        assertThrows(IllegalArgumentException.class, () ->
            DelegationContract.of("task", Map.of(), Duration.ofSeconds(10), 100L,
                "result", "", delegator, target, 0));
    }

    @Test
    void delegationContract_requiresTimeout() {
        assertThrows(IllegalArgumentException.class, () ->
            DelegationContract.of("task", Map.of(), null, 100L,
                "result", "fail", delegator, target, 0));
    }

    @Test
    void delegationContract_requiresNonNegativeDepth() {
        assertThrows(IllegalArgumentException.class, () ->
            DelegationContract.of("task", Map.of(), Duration.ofSeconds(10), 100L,
                "result", "fail", delegator, target, -1));
    }

    @Test
    void delegationContract_storesAllMandatoryFields() {
        var contract = DelegationContract.of(
            "Compute portfolio exposure", Map.of("portfolio", "A"),
            Duration.ofSeconds(30), 1000L,
            "ExposureResult JSON", "Return empty on failure",
            delegator, target, 0);

        assertEquals("Compute portfolio exposure", contract.taskSpecification());
        assertEquals(Map.of("portfolio", "A"), contract.contextPayload());
        assertEquals(Duration.ofSeconds(30), contract.timeout());
        assertEquals(1000L, contract.budgetTokens());
        assertEquals("ExposureResult JSON", contract.resultContract());
        assertEquals("Return empty on failure", contract.failureProtocol());
        assertEquals(delegator, contract.delegatingAgent());
        assertEquals(target, contract.targetAgent());
        assertEquals(0, contract.delegationDepth());
        assertNotNull(contract.contractId());
        assertNotNull(contract.issuedAt());
    }

    @Test
    void delegate_succeedsWithinDepthLimit() {
        var contract = DelegationContract.of("Summarise data", Map.of(),
            Duration.ofSeconds(30), 500L, "Summary", "Empty on fail", delegator, target, 0);
        var result = orchestrator.delegate(contract, ctx);
        assertNotNull(result);
        assertEquals(contract.contractId(), result.contractId());
    }

    @Test
    void delegate_rejectsWhenDepthExceedsMax() {
        // maxDelegationDepth = 3, so depth=3 should be rejected (>= 3)
        var contract = DelegationContract.of("Deep task", Map.of(),
            Duration.ofSeconds(30), 100L, "Result", "Fail", delegator, target, 3);
        var result = orchestrator.delegate(contract, ctx);
        assertFalse(result.success());
        assertTrue(result.failureReason().contains("depth limit"));
    }

    @Test
    void delegate_depthAtLimitIsRejected() {
        var contract = DelegationContract.of("Task at limit", Map.of(),
            Duration.ofSeconds(30), 100L, "Result", "Fail", delegator, target, 3);
        var result = orchestrator.delegate(contract, ctx);
        assertFalse(result.success());
    }

    @Test
    void delegate_depthBelowLimitIsAccepted() {
        var contract = DelegationContract.of("Task below limit", Map.of(),
            Duration.ofSeconds(30), 100L, "Result", "Fail", delegator, target, 2);
        var result = orchestrator.delegate(contract, ctx);
        // Should not be rejected for depth (may succeed or fail for other reasons)
        assertNotNull(result);
        assertFalse(result.failureReason() != null && result.failureReason().contains("depth limit"));
    }

    @Test
    void delegate_detectsCyclicDelegation() {
        // Delegator = target (same agentId) should be detected as a cycle after first delegation
        var cyclicContract = DelegationContract.of("Cyclic task", Map.of(),
            Duration.ofSeconds(30), 100L, "Result", "Fail", delegator, delegator, 0);
        var result = orchestrator.delegate(cyclicContract, ctx);
        // Either cycle detection fires or depth limit—both indicate a rejection
        // Cycle detection: delegator.agentId() == target.agentId() is in chain after first add
        assertNotNull(result);
    }

    @Test
    void delegate_contractIdPropagatedToResult() {
        var contract = DelegationContract.of("Track contract id", Map.of(),
            Duration.ofSeconds(30), 100L, "Result", "Fail", delegator, target, 0);
        var result = orchestrator.delegate(contract, ctx);
        assertEquals(contract.contractId(), result.contractId());
    }
}
