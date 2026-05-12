package com.agentruntime.tooling.enterprise;
import com.agentruntime.core.valueobjects.ExecutionContext;
import java.util.Map;
public class DefaultEnterpriseAdapter implements EnterpriseAdapter {
    @Override
    public Map<String, Object> invoke(String operation, Map<String, Object> input, ExecutionContext ctx) {
        return Map.of("operation", operation, "status", "ok", "agent", ctx.agentIdentity().agentId());
    }
}
