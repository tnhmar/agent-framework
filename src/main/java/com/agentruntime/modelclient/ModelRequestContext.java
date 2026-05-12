package com.agentruntime.modelclient;

import java.util.Map;
import java.util.Objects;

/**
 * Lightweight context passed alongside a ModelPrompt.
 * Contains session/tenant information and a read-only working-memory snapshot.
 */
public record ModelRequestContext(String sessionId, String tenantId, Map<String, Object> workingMemory) {

    public ModelRequestContext {
        Objects.requireNonNull(sessionId, "sessionId must not be null");
        workingMemory = workingMemory != null ? Map.copyOf(workingMemory) : Map.of();
    }

    public static ModelRequestContext of(String sessionId, String tenantId) {
        return new ModelRequestContext(sessionId, tenantId, Map.of());
    }
}
