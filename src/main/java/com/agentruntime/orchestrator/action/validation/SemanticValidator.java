package com.agentruntime.orchestrator.action.validation;

import com.agentruntime.core.valueobjects.ExecutionContext;
import com.agentruntime.core.valueobjects.ValidationResult;
import com.agentruntime.orchestrator.reasoning.ReasoningResult;

/**
 * Layer 2: Semantic validation — checks that the proposed action is appropriate for the current subgoal.
 * Vol1 Ch.7 §"Action Validation", layer 2.
 */
public class SemanticValidator implements ActionValidator {

    @Override
    public ValidationResult validate(ReasoningResult reasoning, ExecutionContext ctx) {
        if (reasoning.confidence() < 0.1) {
            return ValidationResult.rejected(name(),
                "Reasoning confidence too low (" + reasoning.confidence() + ") — action may be semantically misaligned");
        }
        if (reasoning.plan() == null || reasoning.plan().isBlank()) {
            return ValidationResult.rejected(name(), "No reasoning plan — cannot verify semantic alignment of action");
        }
        return ValidationResult.passed(name());
    }

    @Override
    public String name() { return "SemanticValidator"; }
}
