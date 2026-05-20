package com.agentruntime.orchestrator.failuredetection;

import com.agentruntime.core.valueobjects.ExecutionContext;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Failure classification using a pluggable chain of FailureClassifier strategies.
 *
 * V-OCP-02 FIX: Classification is now a List<FailureClassifier> chain, not hardcoded if/else.
 * New failure patterns are added by registering additional classifiers — no source changes needed.
 *
 * Evaluation order: first classifier to return a non-empty Optional wins.
 * Fallback: DETERMINISTIC.
 *
 * Vol.1 Ch.4 §"Reliability Through Failure Classification":
 *   TRANSIENT     — retry with exponential backoff
 *   DETERMINISTIC — fail fast with structured diagnostics
 *   POLICY        — stop execution or escalate
 *   SEMANTIC      — replan or request clarification
 */
public class DefaultFailureDetectionModule implements FailureDetectionModule {

    private final List<FailureClassifier> classifiers;

    public DefaultFailureDetectionModule(List<FailureClassifier> classifiers) {
        Objects.requireNonNull(classifiers, "classifiers must not be null");
        if (classifiers.isEmpty()) throw new IllegalArgumentException("At least one classifier required");
        this.classifiers = List.copyOf(classifiers);
    }

    /** Default constructor — wires the 3 standard classifiers in priority order. */
    public DefaultFailureDetectionModule() {
        this(List.of(
                new TransientClassifier(),
                new PolicyClassifier(),
                new SemanticClassifier()
        ));
    }

    @Override
    public FailureAssessment assess(Throwable error, String phase, ExecutionContext ctx) {
        if (error == null) return FailureAssessment.deterministic("NULL_ERROR", "null error received");

        String msg  = error.getMessage() != null ? error.getMessage() : error.getClass().getSimpleName();
        String type = error.getClass().getSimpleName();

        for (FailureClassifier classifier : classifiers) {
            Optional<FailureCategory> category = classifier.classify(error, phase, ctx);
            if (category.isPresent()) {
                return switch (category.get()) {
                    case TRANSIENT     -> FailureAssessment.transient_(type, msg);
                    case POLICY        -> FailureAssessment.policy(type, msg);
                    case SEMANTIC      -> FailureAssessment.semantic(type, msg);
                    case DETERMINISTIC -> FailureAssessment.deterministic(type, msg);
                };
            }
        }
        return FailureAssessment.deterministic(type, msg);
    }

    // ── Built-in classifier implementations ──────────────────────────────────

    public static class TransientClassifier implements FailureClassifier {
        @Override
        public Optional<FailureCategory> classify(Throwable e, String phase, ExecutionContext ctx) {
            String lower = e.getMessage() != null ? e.getMessage().toLowerCase() : "";
            if (e instanceof java.net.SocketTimeoutException
                    || e instanceof java.net.ConnectException
                    || lower.contains("timeout") || lower.contains("rate limit")
                    || lower.contains("temporarily") || lower.contains("503")
                    || lower.contains("429"))
                return Optional.of(FailureCategory.TRANSIENT);
            return Optional.empty();
        }
    }

    public static class PolicyClassifier implements FailureClassifier {
        @Override
        public Optional<FailureCategory> classify(Throwable e, String phase, ExecutionContext ctx) {
            String lower = e.getMessage() != null ? e.getMessage().toLowerCase() : "";
            if (e instanceof SecurityException
                    || lower.contains("permission") || lower.contains("denied")
                    || lower.contains("budget")    || lower.contains("safety")
                    || lower.contains("unauthorized") || lower.contains("forbidden"))
                return Optional.of(FailureCategory.POLICY);
            return Optional.empty();
        }
    }

    public static class SemanticClassifier implements FailureClassifier {
        @Override
        public Optional<FailureCategory> classify(Throwable e, String phase, ExecutionContext ctx) {
            String lower = e.getMessage() != null ? e.getMessage().toLowerCase() : "";
            if (lower.contains("irrelevant") || lower.contains("off-topic")
                    || lower.contains("misunderstood") || lower.contains("objective drift")
                    || lower.contains("wrong task")    || lower.contains("goal drift"))
                return Optional.of(FailureCategory.SEMANTIC);
            return Optional.empty();
        }
    }
}
