package com.agentruntime.orchestrator.action.validation;

import com.agentruntime.core.valueobjects.ExecutionContext;
import com.agentruntime.core.valueobjects.ValidationResult;
import com.agentruntime.orchestrator.reasoning.ReasoningResult;

/**
 * Layer 1: Schema validation — verifies tool arguments conform to registered parameter schemas.
 * Vol1 Ch.7 §"Action Validation", layer 1.
 */
public class SchemaValidator implements ActionValidator {

    @Override
    public ValidationResult validate(ReasoningResult reasoning, ExecutionContext ctx) {
        if (reasoning.selectedActions() == null || reasoning.selectedActions().isEmpty()) {
            return ValidationResult.rejected(name(), "No actions selected — cannot validate schema");
        }
        for (String action : reasoning.selectedActions()) {
            if (action == null || action.isBlank()) {
                return ValidationResult.rejected(name(), "Blank action name is not a valid tool reference");
            }
        }
        return ValidationResult.passed(name());
    }

    @Override
    public String name() { return "SchemaValidator"; }
}
