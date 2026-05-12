package com.agentruntime.core.valueobjects;
import com.agentruntime.core.enums.ValidationOutcome;
/** Result from a single validator layer in the action validation pipeline (Vol1 Ch.7 §"Action Validation"). */
public record ValidationResult(
    ValidationOutcome outcome,
    String validatorName,
    String reason
) {
    public static ValidationResult passed(String validatorName) {
        return new ValidationResult(ValidationOutcome.PASSED, validatorName, null);
    }
    public static ValidationResult rejected(String validatorName, String reason) {
        return new ValidationResult(ValidationOutcome.REJECTED, validatorName, reason);
    }
    public boolean isPassed() { return outcome == ValidationOutcome.PASSED; }
}
