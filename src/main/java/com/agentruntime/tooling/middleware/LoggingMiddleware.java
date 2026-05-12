package com.agentruntime.tooling.middleware;
import com.agentruntime.core.valueobjects.ExecutionContext;
import java.util.*;
public class LoggingMiddleware implements ToolMiddleware {
    private final List<String> log = new ArrayList<>();
    @Override
    public Map<String, Object> process(String toolId, Map<String, Object> params, ExecutionContext ctx) {
        log.add("[" + ctx.executionId() + "] Tool invoked: " + toolId + " params=" + params);
        return params;
    }
    public List<String> getLog() { return Collections.unmodifiableList(log); }
}
