package com.agentruntime.modelclient;

import java.util.List;
import java.util.Map;

/**
 * Deterministic stub ModelClient for tests and examples.
 *
 * Ported from agent-framework StubModelClient; namespace-adapted.
 * If a toolName is configured, it emits a tool-call on the first turn;
 * subsequent calls return the configured answer directly.
 */
public final class StubModelClient implements ModelClient {

    private final String toolName;
    private final String answer;
    private int callCount = 0;

    public StubModelClient(String toolName, String answer) {
        this.toolName = toolName;
        this.answer   = answer;
    }

    /** A stub that always returns a direct text answer (no tool calls). */
    public static StubModelClient direct(String answer) {
        return new StubModelClient(null, answer);
    }

    @Override
    public ModelOutput generate(ModelPrompt prompt, ModelRequestContext context) {
        callCount++;
        boolean hasToolResult = context.workingMemory().keySet().stream()
                .anyMatch(k -> k.startsWith("toolResult."));

        if (toolName != null && !hasToolResult) {
            // Emit a tool-call on first invocation
            ToolCallRequest call = new ToolCallRequest(toolName, Map.of("query", "agent systems"));
            return new ModelOutput("<tool_call>" + toolName + "</tool_call>", List.of(call), Map.of());
        }

        return new ModelOutput(answer, List.of(), Map.of());
    }

    public int callCount() { return callCount; }
}
