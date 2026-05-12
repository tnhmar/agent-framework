package com.agentruntime.core.valueobjects;
import java.util.UUID;
public record AgentIdentity(String agentId, String agentName, String role, String tenantId) {
    public static AgentIdentity of(String name, String role) {
        return new AgentIdentity(UUID.randomUUID().toString(), name, role, "default");
    }
}
