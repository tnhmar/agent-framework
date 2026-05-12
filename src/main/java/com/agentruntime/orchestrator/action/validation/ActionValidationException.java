package com.agentruntime.orchestrator.action.validation;

import com.agentruntime.core.valueobjects.ValidationResult;

/**
 * Thrown by ActionValidationPipeline when an action is rejected before dispatch.
 * Vol1 Ch.7: "The runtime must not dispatch without validation."
 */
public class ActionValidationException extends RuntimeException {
    private final ValidationResult result;

    public ActionValidationException(ValidationResult result) {
        super("Action rejected by " + result.validatorName() + ": " + result.reason());
        this.result = result;
    }

    public ValidationResult result() { return result; }
}
