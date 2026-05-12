package com.agentruntime.orchestrator.action.validation;

import com.agentruntime.core.valueobjects.ExecutionContext;
import com.agentruntime.core.valueobjects.ValidationResult;
import com.agentruntime.orchestrator.reasoning.ReasoningResult;

/**
 * Single layer in the composable action validation pipeline (Vol1 Ch.7 §"Action Validation").
 * Each validator is independently configurable and testable.
 */
public interface ActionValidator {
    ValidationResult validate(ReasoningResult reasoning, ExecutionContext ctx);
    String name();
}
