package com.agentruntime.modelclient;

import java.util.Map;
import java.util.Objects;

/** A single tool call requested by the model. */
public record ToolCallRequest(String name, Map<String, Object> arguments) {
    public ToolCallRequest {
        Objects.requireNonNull(name, "tool name must not be null");
        arguments = arguments != null ? Map.copyOf(arguments) : Map.of();
    }
}
