package com.agentruntime.orchestrator.failuredetection;

import com.agentruntime.core.valueobjects.ExecutionContext;

/**
 * Default failure detection module.
 *
 * Vol.1 Ch.4 §"Reliability Through Failure Classification":
 * Classifies exceptions into 4 categories with appropriate handling signals:
 *   TRANSIENT     — retry with backoff
 *   DETERMINISTIC — fail fast
 *   POLICY        — stop or escalate
 *   SEMANTIC      — replan or clarify
 *
 * "Retry policy must also respect idempotency. Retrying a non-idempotent action
 *  without a deduplication key will generate duplicate side effects."
 */
public class DefaultFailureDetectionModule implements FailureDetectionModule {

    @Override
    public FailureAssessment assess(Throwable error, String phase, ExecutionContext ctx) {
        if (error == null) return FailureAssessment.deterministic("NULL_ERROR", "null error received");

        String msg = error.getMessage() != null ? error.getMessage() : error.getClass().getSimpleName();
        String type = error.getClass().getSimpleName();

        // ── TRANSIENT: network / rate-limit errors ────────────────────────────
        if (isTransient(error, msg)) return FailureAssessment.transient_(type, msg);

        // ── POLICY: security / budget / safety violations ─────────────────────
        if (isPolicy(error, msg)) return FailureAssessment.policy(type, msg);

        // ── SEMANTIC: planning / reasoning failures ───────────────────────────
        if (isSemantic(phase, msg)) return FailureAssessment.semantic(type, msg);

        // ── DETERMINISTIC: everything else (schema, invalid input, etc.) ──────
        return FailureAssessment.deterministic(type, msg);
    }

    // ── Classification heuristics ─────────────────────────────────────────────

    private boolean isTransient(Throwable e, String msg) {
        String lower = msg.toLowerCase();
        return e instanceof java.net.SocketTimeoutException
                || e instanceof java.net.ConnectException
                || lower.contains("timeout") || lower.contains("rate limit")
                || lower.contains("temporarily") || lower.contains("503")
                || lower.contains("429");
    }

    private boolean isPolicy(Throwable e, String msg) {
        String lower = msg.toLowerCase();
        return e instanceof SecurityException
                || lower.contains("permission") || lower.contains("denied")
                || lower.contains("budget") || lower.contains("safety")
                || lower.contains("unauthorized") || lower.contains("forbidden");
    }

    private boolean isSemantic(String phase, String msg) {
        String lowerPhase = phase != null ? phase.toLowerCase() : "";
        String lowerMsg   = msg.toLowerCase();
        // Semantic errors require message-level evidence — a timeout in the reasoning phase
        // is still TRANSIENT, not semantic. Phase alone is insufficient.
        boolean semanticMessage = lowerMsg.contains("irrelevant") || lowerMsg.contains("off-topic")
                || lowerMsg.contains("misunderstood") || lowerMsg.contains("objective drift")
                || lowerMsg.contains("wrong task") || lowerMsg.contains("goal drift");
        boolean reasoningPhase = lowerPhase.contains("reasoning") || lowerPhase.contains("reflection");
        return semanticMessage || (reasoningPhase && semanticMessage);
    }
}
