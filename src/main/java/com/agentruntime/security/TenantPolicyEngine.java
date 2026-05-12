package com.agentruntime.security;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * Per-tenant operation deny-list policy engine.
 *
 * Thread-safe: ConcurrentHashMap of CopyOnWriteArraySet per tenant.
 * Composed with SecurityEnforcer for dual-layer enforcement:
 *   1. Role-based (SecurityEnforcer) — Vol.1 Ch.13 RBAC
 *   2. Tenant-scoped deny-list (TenantPolicyEngine) — Vol.2 multi-tenancy governance
 *
 * Both checks must pass. Compose as:
 *   boolean allowed = enforcer.check(agent, op) && tenantPolicy.permits(tenantId, op);
 */
public final class TenantPolicyEngine {

    private final ConcurrentHashMap<String, Set<String>> deniedByTenant =
            new ConcurrentHashMap<>();

    /**
     * Deny an operation for a specific tenant.
     * @return this, for fluent chaining
     */
    public TenantPolicyEngine deny(String tenantId, String operation) {
        Objects.requireNonNull(tenantId,  "tenantId must not be null");
        Objects.requireNonNull(operation, "operation must not be null");
        deniedByTenant
                .computeIfAbsent(tenantId, k -> new CopyOnWriteArraySet<>())
                .add(operation);
        return this;
    }

    /**
     * Returns true if the operation is permitted for the given tenant.
     */
    public boolean permits(String tenantId, String operation) {
        Set<String> denied = deniedByTenant.get(tenantId);
        return denied == null || !denied.contains(operation);
    }

    /**
     * Throws SecurityException if the operation is denied for the tenant.
     */
    public void enforce(String tenantId, String operation) {
        if (!permits(tenantId, operation)) {
            throw new SecurityException(
                    "Tenant '" + tenantId + "' is not permitted to perform: " + operation);
        }
    }

    /** Returns all denied operations for a tenant (unmodifiable). */
    public Set<String> deniedOperationsFor(String tenantId) {
        return Collections.unmodifiableSet(
                deniedByTenant.getOrDefault(tenantId, Set.of()));
    }
}
