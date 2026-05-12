package com.agentruntime.security;

import com.agentruntime.core.valueobjects.AgentIdentity;
import java.util.Set;

public record SecurityPolicy(
        String policyId,
        Set<String> allowedRoles,
        Set<String> deniedOperations,
        boolean taintTrackingEnabled,
        boolean safeFailureEnabled) {

    public boolean permits(AgentIdentity agent, String operation) {
        if (deniedOperations.contains(operation)) return false;
        return allowedRoles.isEmpty() || allowedRoles.contains(agent.role());
    }

    /** Factory: allows all roles, denies nothing. Safe default for tests. */
    public static SecurityPolicy allowAll() {
        return new SecurityPolicy("allow-all", Set.of(), Set.of(), false, true);
    }
}
