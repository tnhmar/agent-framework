package com.agentruntime.runtime.run;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * A single recorded step in a run's execution history.
 * Provides the fine-grained audit trail needed for replay and debugging.
 */
public record RunStep(
        RunId runId,
        RunState fromState,
        RunState toState,
        Instant timestamp,
        String description,
        Map<String, Object> metadata
) {
    public RunStep {
        Objects.requireNonNull(runId);
        Objects.requireNonNull(toState);
        Objects.requireNonNull(timestamp);
        metadata = metadata != null ? Map.copyOf(metadata) : Map.of();
    }
}
