package com.agentruntime.tooling.registry;

import com.agentruntime.core.enums.ToolCategory;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ToolRegistry {
    private final Map<String, ToolDefinition> tools = new ConcurrentHashMap<>();

    public void register(ToolDefinition tool) { tools.put(tool.toolId(), tool); }
    public Optional<ToolDefinition> find(String toolId) { return Optional.ofNullable(tools.get(toolId)); }
    public List<ToolDefinition> byCategory(ToolCategory category) {
        return tools.values().stream().filter(t -> t.category() == category).toList();
    }
    public List<ToolDefinition> all() { return List.copyOf(tools.values()); }
    public void deregister(String toolId) { tools.remove(toolId); }
    public int count() { return tools.size(); }

    /** Factory: returns an empty registry (no tools registered). */
    public static ToolRegistry empty() { return new ToolRegistry(); }
}
