package com.agentruntime.usecase.research;

import com.agentruntime.core.enums.ToolCategory;
import com.agentruntime.modelclient.*;
import com.agentruntime.orchestrator.DefaultAgentOrchestrator;
import com.agentruntime.runtime.builder.AgentRuntimeBuilder;
import com.agentruntime.tooling.registry.ToolDefinition;
import com.agentruntime.tooling.registry.ToolRegistry;

import java.util.List;
import java.util.Map;

public final class ResearchAgentFactory {

    private static final String DEFAULT_MODEL = "gpt-4o";
    private ResearchAgentFactory() {}

    public static DefaultAgentOrchestrator create(String openAiApiKey) {
        return create(openAiApiKey, DEFAULT_MODEL);
    }

    public static DefaultAgentOrchestrator create(String openAiApiKey, String model) {
        boolean useStub = openAiApiKey == null || openAiApiKey.isBlank()
                       || "stub".equalsIgnoreCase(openAiApiKey.trim());
        ModelClient mc = useStub
                ? StubModelClient.direct("ReAct, event-driven orchestration, and hierarchical delegation are the key patterns.")
                : new OpenAiModelClient(openAiApiKey, model != null ? model : DEFAULT_MODEL);
        return new AgentRuntimeBuilder()
                .modelClient(mc)
                .toolRegistry(buildToolRegistry())
                .maxDelegationDepth(5)
                .verbose(true)
                .build();
    }

    public static DefaultAgentOrchestrator createWithStub() { return create(null); }

    private static ToolRegistry buildToolRegistry() {
        ToolRegistry registry = new ToolRegistry();
        registry.register(new ToolDefinition(
                "webSearch", "webSearch",
                "Search the web for information.",
                ToolCategory.DATA_RETRIEVAL,
                Map.of("query", "string"),
                Map.of("results", "List<Map>"),
                List.of("RATE_LIMITED", "TIMEOUT", "QUOTA_EXCEEDED"),
                true, "real-time", false));
        registry.register(new ToolDefinition(
                "fetchPage", "fetchPage",
                "Fetch and extract text from a URL.",
                ToolCategory.DATA_RETRIEVAL,
                Map.of("url", "string"),
                Map.of("text", "string", "url", "string"),
                List.of("HTTP_ERROR", "TIMEOUT", "PARSE_FAILURE"),
                true, "real-time", false));
        registry.register(new ToolDefinition(
                "findingStore", "findingStore",
                "Manage research findings.",
                ToolCategory.DATA_RETRIEVAL,
                Map.of("action", "string"),
                Map.of("result", "string"),
                List.of("INVALID_ACTION"),
                false, "session", false));
        return registry;
    }
}
