package com.agentruntime.orchestrator.failuredetection;

import com.agentruntime.core.valueobjects.ExecutionContext;
import java.util.Optional;

/**
 * SPI for individual failure classification strategies.
 * V-OCP-02 FIX: New failure patterns can be added without modifying
 * DefaultFailureDetectionModule — just implement and register.
 */
@FunctionalInterface
public interface FailureClassifier {
    /**
     * Attempt to classify the throwable.
     * Return Optional.empty() if this classifier cannot determine the category.
     */
    Optional<FailureCategory> classify(Throwable error, String phase, ExecutionContext ctx);
}
