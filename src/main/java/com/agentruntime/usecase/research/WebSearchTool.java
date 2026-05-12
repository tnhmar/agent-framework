package com.agentruntime.usecase.research;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Web search tool for the research use-case.
 *
 * Ported from agent-framework usecase.research.WebSearchTool; namespace-adapted.
 * Currently returns stub results — replace the execute() body with a real
 * search provider (Brave, Serper, Tavily) for production use.
 *
 * Register this tool's ToolDefinition via ResearchAgentFactory
 * and provide an executor that calls this class.
 */
public final class WebSearchTool {

    public static final String NAME = "webSearch";

    public Map<String, Object> execute(Map<String, Object> input) {
        String query = Objects.toString(input.getOrDefault("query", ""), "").trim();
        if (query.isBlank()) throw new IllegalArgumentException("WebSearchTool: 'query' parameter is required");

        // Stub — replace with real provider (Brave Search API, Serper, Tavily, etc.)
        return Map.of(
                "query",   query,
                "results", List.of(
                    Map.of("title",   "Agent Systems Overview",
                           "url",     "https://example.com/agents",
                           "snippet", "Production AI agents use event-driven loops and ReAct patterns..."),
                    Map.of("title",   "ReAct Pattern",
                           "url",     "https://example.com/react",
                           "snippet", "ReAct interleaves reasoning and acting in LLM workflows...")));
    }
}
