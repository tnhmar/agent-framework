package com.agentruntime;

import com.agentruntime.core.valueobjects.*;
import com.agentruntime.orchestrator.action.validation.*;
import com.agentruntime.orchestrator.reasoning.ReasoningResult;
import com.agentruntime.security.*;
import org.junit.jupiter.api.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for V-19 fix: 4-layer action validation pipeline.
 * Vol1 Ch.7 §"Action Validation": runtime must not dispatch without validation.
 */
class ActionValidationPipelineTest {

    private SecurityPolicy policy;
    private SecurityEnforcer enforcer;
    private AgentIdentity agent;
    private ExecutionContext ctx;

    @BeforeEach
    void setUp() {
        policy = new SecurityPolicy("test-policy",
            Set.of("orchestrator", "specialist", "coordinator"),
            Set.of("purge_all", "admin_delete"), true, true);
        enforcer = new SecurityEnforcer(policy);
        agent = AgentIdentity.of("test-agent", "orchestrator");
        ctx = ExecutionContext.of(agent);
    }

    @Test
    void defaultPipeline_passesValidAction() {
        var pipeline = ActionValidationPipeline.defaultPipeline(enforcer);
        var reasoning = new ReasoningResult("valid plan", List.of("retrieve_context"), "good rationale", 0.9, false);
        var result = pipeline.validate(reasoning, ctx);
        assertTrue(result.isPassed());
        assertEquals("ActionValidationPipeline", result.validatorName());
    }

    @Test
    void schemaValidator_rejectsBlankAction() {
        var pipeline = new ActionValidationPipeline(List.of(new SchemaValidator()));
        var reasoning = new ReasoningResult("plan", List.of(""), "rationale", 0.9, false);
        var ex = assertThrows(ActionValidationException.class, () -> pipeline.validate(reasoning, ctx));
        assertTrue(ex.getMessage().contains("SchemaValidator"));
    }

    @Test
    void schemaValidator_rejectsEmptyActionList() {
        var pipeline = new ActionValidationPipeline(List.of(new SchemaValidator()));
        var reasoning = new ReasoningResult("plan", List.of(), "rationale", 0.9, false);
        assertThrows(ActionValidationException.class, () -> pipeline.validate(reasoning, ctx));
    }

    @Test
    void semanticValidator_rejectsLowConfidence() {
        var pipeline = new ActionValidationPipeline(List.of(new SemanticValidator()));
        var reasoning = new ReasoningResult("plan", List.of("tool"), "rationale", 0.05, false);
        var ex = assertThrows(ActionValidationException.class, () -> pipeline.validate(reasoning, ctx));
        assertTrue(ex.getMessage().contains("SemanticValidator"));
    }

    @Test
    void semanticValidator_rejectsNullPlan() {
        var pipeline = new ActionValidationPipeline(List.of(new SemanticValidator()));
        var reasoning = new ReasoningResult(null, List.of("tool"), "rationale", 0.9, false);
        assertThrows(ActionValidationException.class, () -> pipeline.validate(reasoning, ctx));
    }

    @Test
    void policyValidator_rejectsDeniedOperation() {
        var pipeline = new ActionValidationPipeline(List.of(new PolicyValidator(enforcer)));
        var reasoning = new ReasoningResult("plan", List.of("purge_all"), "rationale", 0.9, false);
        var ex = assertThrows(ActionValidationException.class, () -> pipeline.validate(reasoning, ctx));
        assertTrue(ex.getMessage().contains("PolicyValidator"));
    }

    @Test
    void policyValidator_passesPermittedOperation() {
        var pipeline = new ActionValidationPipeline(List.of(new PolicyValidator(enforcer)));
        var reasoning = new ReasoningResult("plan", List.of("retrieve_context"), "rationale", 0.9, false);
        var result = pipeline.validate(reasoning, ctx);
        assertTrue(result.isPassed());
    }

    @Test
    void safetyValidator_rejectsHardBlockedAction() {
        var pipeline = new ActionValidationPipeline(List.of(new SafetyValidator()));
        var reasoning = new ReasoningResult("plan", List.of("purge_all"), "rationale", 0.9, false);
        var ex = assertThrows(ActionValidationException.class, () -> pipeline.validate(reasoning, ctx));
        assertTrue(ex.getMessage().contains("SafetyValidator"));
    }

    @Test
    void safetyValidator_rejectsSelfReplicate() {
        var pipeline = new ActionValidationPipeline(List.of(new SafetyValidator()));
        var reasoning = new ReasoningResult("plan", List.of("self_replicate"), "rationale", 0.9, false);
        assertThrows(ActionValidationException.class, () -> pipeline.validate(reasoning, ctx));
    }

    @Test
    void pipeline_stopsAtFirstRejection() {
        // SafetyValidator is the last, but PolicyValidator should reject first
        var pipeline = ActionValidationPipeline.defaultPipeline(enforcer);
        var reasoning = new ReasoningResult("plan", List.of("admin_delete"), "rationale", 0.9, false);
        var ex = assertThrows(ActionValidationException.class, () -> pipeline.validate(reasoning, ctx));
        // PolicyValidator rejects admin_delete before SafetyValidator gets a chance
        assertNotNull(ex.result());
    }

    @Test
    void pipeline_requiresAtLeastOneValidator() {
        assertThrows(IllegalArgumentException.class,
            () -> new ActionValidationPipeline(List.of()));
    }

    @Test
    void pipeline_hasCorrectValidatorOrder() {
        var pipeline = ActionValidationPipeline.defaultPipeline(enforcer);
        var validators = pipeline.validators();
        assertEquals(4, validators.size());
        assertEquals("SchemaValidator", validators.get(0).name());
        assertEquals("SemanticValidator", validators.get(1).name());
        assertEquals("PolicyValidator", validators.get(2).name());
        assertEquals("SafetyValidator", validators.get(3).name());
    }

    @Test
    void actionModule_rejectsWithoutValidAction() {
        var module = new com.agentruntime.orchestrator.action.DefaultActionModule(
            ActionValidationPipeline.defaultPipeline(enforcer));
        var reasoning = new ReasoningResult("plan", List.of(""), "rationale", 0.9, false);
        var result = module.execute(reasoning, ctx);
        assertFalse(result.success());
        assertTrue(result.errorMessage().toLowerCase().contains("validation"));
    }

    @Test
    void actionModule_succeedsWithValidAction() {
        var module = new com.agentruntime.orchestrator.action.DefaultActionModule(
            ActionValidationPipeline.defaultPipeline(enforcer));
        var reasoning = new ReasoningResult("plan", List.of("retrieve_context"), "rationale", 0.9, false);
        var result = module.execute(reasoning, ctx);
        assertTrue(result.success());
    }
}
