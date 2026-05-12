package com.agentruntime;

import com.agentruntime.core.enums.SourceTrustLabel;
import com.agentruntime.core.valueobjects.AgentIdentity;
import com.agentruntime.security.*;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the security layer:
 * SecurityEnforcer, TaintTracker, TrustBoundary, SecurityPolicy.
 * Covers Vol.1 Ch.12/Ch.13/Ch.22 security requirements.
 */
class SecurityLayerTest {

    // ── SecurityPolicy ────────────────────────────────────────────────────────

    @Test
    void allowAll_permitsAnyRoleAndOperation() {
        SecurityPolicy policy = SecurityPolicy.allowAll();
        AgentIdentity agent = AgentIdentity.of("agent", "any-role");
        assertTrue(policy.permits(agent, "WRITE"));
        assertTrue(policy.permits(agent, "DELETE_ALL"));
    }

    @Test
    void policy_deniedOperationIsRejected() {
        SecurityPolicy policy = new SecurityPolicy("p1",
                Set.of("orchestrator"), Set.of("admin_delete"), false, true);
        AgentIdentity agent = AgentIdentity.of("agent", "orchestrator");
        assertFalse(policy.permits(agent, "admin_delete"));
    }

    @Test
    void policy_allowedRoleIsPermitted() {
        SecurityPolicy policy = new SecurityPolicy("p1",
                Set.of("orchestrator", "specialist"), Set.of(), false, true);
        assertTrue(policy.permits(AgentIdentity.of("a", "orchestrator"), "WRITE"));
        assertTrue(policy.permits(AgentIdentity.of("b", "specialist"),   "READ"));
    }

    @Test
    void policy_disallowedRoleIsRejected() {
        SecurityPolicy policy = new SecurityPolicy("p1",
                Set.of("orchestrator"), Set.of(), false, true);
        AgentIdentity guest = AgentIdentity.of("guest", "guest");
        assertFalse(policy.permits(guest, "WRITE"));
    }

    @Test
    void policy_emptyAllowedRolesPermitsAll() {
        SecurityPolicy policy = new SecurityPolicy("p1",
                Set.of(), Set.of(), false, true);
        assertTrue(policy.permits(AgentIdentity.of("any", "unknown-role"), "OP"));
    }

    // ── SecurityEnforcer ──────────────────────────────────────────────────────

    @Test
    void enforcer_throwsWhenNotPermitted() {
        SecurityPolicy policy = new SecurityPolicy("p1",
                Set.of("orchestrator"), Set.of("admin_op"), false, true);
        SecurityEnforcer enforcer = new SecurityEnforcer(policy);
        AgentIdentity agent = AgentIdentity.of("agent", "orchestrator");
        assertThrows(SecurityException.class, () -> enforcer.enforce(agent, "admin_op"));
    }

    @Test
    void enforcer_doesNotThrowWhenPermitted() {
        SecurityEnforcer enforcer = new SecurityEnforcer(SecurityPolicy.allowAll());
        AgentIdentity agent = AgentIdentity.of("agent", "orchestrator");
        assertDoesNotThrow(() -> enforcer.enforce(agent, "WRITE"));
    }

    @Test
    void enforcer_checkReturnsFalseForDenied() {
        SecurityPolicy policy = new SecurityPolicy("p1",
                Set.of("admin"), Set.of(), false, true);
        SecurityEnforcer enforcer = new SecurityEnforcer(policy);
        AgentIdentity nonAdmin = AgentIdentity.of("user", "user");
        assertFalse(enforcer.check(nonAdmin, "OP"));
    }

    @Test
    void enforcer_checkReturnsTrueForAllowed() {
        SecurityEnforcer enforcer = new SecurityEnforcer(SecurityPolicy.allowAll());
        assertTrue(enforcer.check(AgentIdentity.of("a", "r"), "any-op"));
    }

    // ── TaintTracker ──────────────────────────────────────────────────────────

    @Test
    void taintTracker_untaintedDataIsClean() {
        TaintTracker tracker = new TaintTracker();
        assertFalse(tracker.isTainted("clean-data-id"));
        assertTrue(tracker.taintSources("clean-data-id").isEmpty());
    }

    @Test
    void taintTracker_taintedDataIsDetected() {
        TaintTracker tracker = new TaintTracker();
        tracker.taint("data-1", "user-input");
        assertTrue(tracker.isTainted("data-1"));
    }

    @Test
    void taintTracker_multipleTaintSourcesAccumulate() {
        TaintTracker tracker = new TaintTracker();
        tracker.taint("data-1", "source-A");
        tracker.taint("data-1", "source-B");
        assertEquals(2, tracker.taintSources("data-1").size());
        assertTrue(tracker.taintSources("data-1").contains("source-A"));
        assertTrue(tracker.taintSources("data-1").contains("source-B"));
    }

    @Test
    void taintTracker_clearRemovesTaint() {
        TaintTracker tracker = new TaintTracker();
        tracker.taint("data-1", "untrusted");
        tracker.clear("data-1");
        assertFalse(tracker.isTainted("data-1"));
    }

    @Test
    void taintTracker_independentDataIdsAreIsolated() {
        TaintTracker tracker = new TaintTracker();
        tracker.taint("data-A", "source");
        assertFalse(tracker.isTainted("data-B"));
    }

    // ── TrustBoundary ─────────────────────────────────────────────────────────

    @Test
    void trustBoundary_allowsLabelMeetingMinimum() {
        TrustBoundary boundary = new TrustBoundary("b1", "internal", SourceTrustLabel.AGENT_INFERRED, true);
        assertTrue(boundary.allows(SourceTrustLabel.VERIFIED));       // 1.0 >= 0.6
        assertTrue(boundary.allows(SourceTrustLabel.TRUSTED_TOOL));   // 0.8 >= 0.6
        assertTrue(boundary.allows(SourceTrustLabel.AGENT_INFERRED)); // 0.6 >= 0.6
    }

    @Test
    void trustBoundary_rejectsLabelBelowMinimum() {
        TrustBoundary boundary = new TrustBoundary("b1", "strict", SourceTrustLabel.TRUSTED_TOOL, true);
        assertFalse(boundary.allows(SourceTrustLabel.AGENT_INFERRED)); // 0.6 < 0.8
        assertFalse(boundary.allows(SourceTrustLabel.USER_GENERATED)); // 0.2 < 0.8
        assertFalse(boundary.allows(SourceTrustLabel.UNVERIFIED));     // 0.3 < 0.8
    }

    @Test
    void trustBoundary_verifiedAlwaysPassesAnyBoundary() {
        for (SourceTrustLabel level : SourceTrustLabel.values()) {
            TrustBoundary boundary = new TrustBoundary("b", "test", level, false);
            // VERIFIED (1.0) should pass unless boundary requires exactly VERIFIED
            // All trust levels have trustMultiplier <= 1.0
            assertTrue(boundary.allows(SourceTrustLabel.VERIFIED),
                    "VERIFIED should pass boundary with minimum " + level);
        }
    }

    // ── TenantPolicyEngine + SecurityEnforcer composition ────────────────────

    @Test
    void composedPolicy_mustPassBothRoleAndTenantChecks() {
        SecurityPolicy policy = new SecurityPolicy("p1",
                Set.of("orchestrator"), Set.of(), false, true);
        SecurityEnforcer enforcer = new SecurityEnforcer(policy);
        TenantPolicyEngine tenantEngine = new TenantPolicyEngine()
                .deny("tenant-restricted", "EXPORT_DATA");

        AgentIdentity agent = AgentIdentity.of("agent", "orchestrator");

        // Role check: pass; Tenant check: fail
        assertTrue(enforcer.check(agent, "EXPORT_DATA"));
        assertFalse(tenantEngine.permits("tenant-restricted", "EXPORT_DATA"));

        // Composed: both must pass
        boolean allowed = enforcer.check(agent, "EXPORT_DATA")
                && tenantEngine.permits("tenant-restricted", "EXPORT_DATA");
        assertFalse(allowed, "Composed check must fail when tenant policy denies");

        // Unrestricted tenant: both pass
        boolean allowedOtherTenant = enforcer.check(agent, "EXPORT_DATA")
                && tenantEngine.permits("tenant-ok", "EXPORT_DATA");
        assertTrue(allowedOtherTenant, "Unrestricted tenant should pass composed check");
    }
}
