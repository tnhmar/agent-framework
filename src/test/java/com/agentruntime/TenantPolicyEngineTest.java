package com.agentruntime;

import com.agentruntime.security.TenantPolicyEngine;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for TenantPolicyEngine (ported from agent-framework).
 */
class TenantPolicyEngineTest {

    @Test
    void permitsOperationNotInDenyList() {
        TenantPolicyEngine engine = new TenantPolicyEngine()
                .deny("tenant-a", "DELETE_ALL");

        assertTrue(engine.permits("tenant-a", "READ"));
        assertTrue(engine.permits("tenant-a", "WRITE"));
    }

    @Test
    void deniesOperationInDenyList() {
        TenantPolicyEngine engine = new TenantPolicyEngine()
                .deny("tenant-a", "DELETE_ALL");

        assertFalse(engine.permits("tenant-a", "DELETE_ALL"));
    }

    @Test
    void denyListIsPerTenant() {
        TenantPolicyEngine engine = new TenantPolicyEngine()
                .deny("tenant-a", "EXPORT_RAW");

        // tenant-b should not be affected by tenant-a's deny list
        assertTrue(engine.permits("tenant-b", "EXPORT_RAW"));
    }

    @Test
    void unknownTenantPermitsEverything() {
        TenantPolicyEngine engine = new TenantPolicyEngine()
                .deny("tenant-a", "DELETE_ALL");

        assertTrue(engine.permits("unknown-tenant", "DELETE_ALL"));
    }

    @Test
    void enforceThrowsSecurityExceptionWhenDenied() {
        TenantPolicyEngine engine = new TenantPolicyEngine()
                .deny("tenant-restricted", "ADMIN_OP");

        assertThrows(SecurityException.class,
                () -> engine.enforce("tenant-restricted", "ADMIN_OP"));
    }

    @Test
    void enforceDoesNotThrowWhenPermitted() {
        TenantPolicyEngine engine = new TenantPolicyEngine()
                .deny("tenant-a", "DANGEROUS_OP");

        assertDoesNotThrow(() -> engine.enforce("tenant-a", "SAFE_OP"));
    }

    @Test
    void fluentDenyChaining() {
        TenantPolicyEngine engine = new TenantPolicyEngine()
                .deny("tenant-a", "OP_1")
                .deny("tenant-a", "OP_2")
                .deny("tenant-b", "OP_3");

        assertFalse(engine.permits("tenant-a", "OP_1"));
        assertFalse(engine.permits("tenant-a", "OP_2"));
        assertTrue( engine.permits("tenant-a", "OP_3"));
        assertFalse(engine.permits("tenant-b", "OP_3"));
    }
}
