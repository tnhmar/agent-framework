package com.agentruntime.core.valueobjects;
import java.util.Map;
import java.util.UUID;
public record ExecutionContext(String executionId, AgentIdentity agentIdentity, String conversationId, Map<String, Object> metadata) {
    public static ExecutionContext of(AgentIdentity identity) {
        return new ExecutionContext(UUID.randomUUID().toString(), identity, UUID.randomUUID().toString(), Map.of());
    }
}
