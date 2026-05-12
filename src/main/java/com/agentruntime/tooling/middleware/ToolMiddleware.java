package com.agentruntime.tooling.middleware;
import com.agentruntime.core.valueobjects.ExecutionContext;
import java.util.Map;
public interface ToolMiddleware { Map<String, Object> process(String toolId, Map<String, Object> params, ExecutionContext ctx); }
