package com.agentruntime.orchestrator.action.validation;

import com.agentruntime.core.valueobjects.ExecutionContext;
import com.agentruntime.core.valueobjects.ValidationResult;
import com.agentruntime.orchestrator.reasoning.ReasoningResult;
import java.util.List;

/**
 * Composable, ordered validation pipeline (Vol1 Ch.7 §"Action Validation").
 * Executes schema → semantic → policy → safety in strict order.
 * Stops and returns the first rejection. All validators are independently injectable and testable.
 */
public class ActionValidationPipeline {

    private final List<ActionValidator> validators;

    public ActionValidationPipeline(List<ActionValidator> validators) {
        if (validators == null || validators.isEmpty())
            throw new IllegalArgumentException("Validation pipeline must contain at least one validator");
        this.validators = List.copyOf(validators);
    }

    /**
     * Runs all validators in declared order.
     * @return the first rejected result, or a passed result if all validators pass.
     * @throws ActionValidationException if any validator rejects the action.
     */
    public ValidationResult validate(ReasoningResult reasoning, ExecutionContext ctx) {
        for (ActionValidator validator : validators) {
            ValidationResult result = validator.validate(reasoning, ctx);
            if (!result.isPassed()) {
                throw new ActionValidationException(result);
            }
        }
        return ValidationResult.passed("ActionValidationPipeline");
    }

    public static ActionValidationPipeline defaultPipeline(
            com.agentruntime.security.SecurityEnforcer enforcer) {
        return new ActionValidationPipeline(List.of(
            new SchemaValidator(),
            new SemanticValidator(),
            new PolicyValidator(enforcer),
            new SafetyValidator()
        ));
    }

    public List<ActionValidator> validators() { return validators; }
}
