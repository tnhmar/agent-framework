package com.agentruntime.orchestrator;
import java.util.Map;
import java.util.Objects;

/**
 * Describes a single agent task to execute.
 * P0-03: maxIterations must be >= 1 to prevent infinite orchestrator loops.
 */
public record AgentTask(String taskId, String goal, Map<String, Object> inputs, int maxIterations) {
    public AgentTask {
        Objects.requireNonNull(taskId, "taskId must not be null");
        Objects.requireNonNull(goal,   "goal must not be null");
        if (maxIterations < 1)
            throw new IllegalArgumentException("maxIterations must be >= 1, got: " + maxIterations);
        inputs = inputs != null ? Map.copyOf(inputs) : Map.of();
    }
}
