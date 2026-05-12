package com.agentruntime.modelclient;

import java.util.List;
import java.util.Map;

/**
 * Response returned by a ModelClient.
 * Contains the raw text response, optional structured tool calls, and metadata.
 */
public record ModelOutput(String text, List<ToolCallRequest> toolCalls, Map<String, Object> metadata) {

    public ModelOutput {
        toolCalls = toolCalls != null ? List.copyOf(toolCalls) : List.of();
        metadata  = metadata  != null ? Map.copyOf(metadata)   : Map.of();
    }

    /** True if the model requested one or more tool calls. */
    public boolean hasToolCalls() {
        return toolCalls != null && !toolCalls.isEmpty();
    }
}
