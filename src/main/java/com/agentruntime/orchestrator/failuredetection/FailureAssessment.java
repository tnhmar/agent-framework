package com.agentruntime.orchestrator.failuredetection;

/**
 * Result of failure classification.
 * Vol.1 Ch.4: each failure class requires different handling.
 *   TRANSIENT     → retry with exponential backoff
 *   DETERMINISTIC → fail fast with structured diagnostics
 *   POLICY        → stop execution or escalate
 *   SEMANTIC      → replan or clarify
 */
public record FailureAssessment(
        FailureCategory category,
        String          errorType,
        String          message,
        boolean         retryable,
        boolean         requiresEscalation
) {
    public static FailureAssessment transient_(String errorType, String message) {
        return new FailureAssessment(FailureCategory.TRANSIENT, errorType, message, true, false);
    }
    public static FailureAssessment deterministic(String errorType, String message) {
        return new FailureAssessment(FailureCategory.DETERMINISTIC, errorType, message, false, false);
    }
    public static FailureAssessment policy(String errorType, String message) {
        return new FailureAssessment(FailureCategory.POLICY, errorType, message, false, true);
    }
    public static FailureAssessment semantic(String errorType, String message) {
        return new FailureAssessment(FailureCategory.SEMANTIC, errorType, message, false, true);
    }
}
