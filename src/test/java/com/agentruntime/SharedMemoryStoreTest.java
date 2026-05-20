package com.agentruntime;

import com.agentruntime.core.enums.ConsistencyModel;
import com.agentruntime.core.valueobjects.*;
import com.agentruntime.memory.shared.*;
import com.agentruntime.observability.ObservabilityBus;
import org.junit.jupiter.api.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for V-03 (RBAC), V-04 (per-namespace consistency), V-09 (rollback).
 * No Jackson — uses Map<String,Object> for content.
 */
class SharedMemoryStoreTest {

    private InMemorySharedMemoryStore store;
    private ObservabilityBus bus;
    private AgentIdentity writer, reader, unauthorizedWriter, unauthorizedReader;

    @BeforeEach
    void setUp() {
        bus   = new ObservabilityBus();
        store = new InMemorySharedMemoryStore(
            Map.of("strong:", ConsistencyModel.STRONG, "causal:", ConsistencyModel.CAUSAL),
            ConsistencyModel.EVENTUAL, bus);
        writer             = AgentIdentity.of("w", "orchestrator");
        reader             = AgentIdentity.of("r", "observer");
        unauthorizedWriter = AgentIdentity.of("u", "observer");
        unauthorizedReader = AgentIdentity.of("x", "unknown_role");
    }

    private static Map<String,Object> content(String k, String v) { return Map.of(k, v); }

    @Test void write_rejectsUnauthorizedRole() {
        assertThrows(SecurityException.class, () ->
            store.writeWithVersionCheck("rec1", content("k","v"), 0, unauthorizedWriter));
    }

    @Test void read_rejectsUnauthorizedRole() {
        store.writeWithVersionCheck("rec1", content("k","v"), 0, writer);
        assertThrows(SecurityException.class, () -> store.read("rec1", unauthorizedReader));
    }

    @Test void write_emitsAuditEventOnSuccess() {
        store.writeWithVersionCheck("rec2", content("k","v"), 0, writer);
        assertFalse(bus.auditLog().isEmpty());
        assertEquals("SHARED_MEMORY_WRITE", bus.auditLog().get(0).eventType());
        assertEquals(writer.agentId(), bus.auditLog().get(0).agent().agentId());
    }

    @Test void write_authorizedRoleSucceeds() {
        var r = store.writeWithVersionCheck("rec3", content("k","v"), 0, writer);
        assertTrue(r.success());
        assertEquals(1, r.newVersion());
    }

    @Test void write_rejectsVersionMismatch() {
        store.writeWithVersionCheck("rec4", content("k","v1"), 0, writer);
        var r = store.writeWithVersionCheck("rec4", content("k","v2"), 0, writer);
        assertFalse(r.success());
        assertTrue(r.conflictReason().get().contains("Version mismatch"));
    }

    @Test void write_incrementsVersionSequentially() {
        var r1 = store.writeWithVersionCheck("rec5", content("k","v1"), 0, writer);
        var r2 = store.writeWithVersionCheck("rec5", content("k","v2"), 1, writer);
        var r3 = store.writeWithVersionCheck("rec5", content("k","v3"), 2, writer);
        assertEquals(1, r1.newVersion());
        assertEquals(2, r2.newVersion());
        assertEquals(3, r3.newVersion());
    }

    @Test void write_strongNamespaceUsesStrongConsistency() {
        var r = store.writeWithVersionCheck("strong:rec1", content("k","v"), 0, writer);
        assertTrue(r.success());
        var event = bus.auditLog().stream()
            .filter(e -> "SHARED_MEMORY_WRITE".equals(e.eventType())).findFirst();
        assertTrue(event.isPresent());
        assertEquals("STRONG", event.get().details().get("consistency"));
    }

    @Test void write_causalNamespaceUsesCausalConsistency() {
        store.writeWithVersionCheck("causal:rec1", content("k","v"), 0, writer);
        var event = bus.auditLog().stream()
            .filter(e -> "SHARED_MEMORY_WRITE".equals(e.eventType())).findFirst();
        assertEquals("CAUSAL", event.get().details().get("consistency"));
    }

    @Test void write_unmatchedNamespaceFallsBackToDefault() {
        store.writeWithVersionCheck("other:rec1", content("k","v"), 0, writer);
        var event = bus.auditLog().stream()
            .filter(e -> "SHARED_MEMORY_WRITE".equals(e.eventType())).findFirst();
        assertEquals("EVENTUAL", event.get().details().get("consistency"));
    }

    @Test void getVersion_returnsSpecificVersion() {
        store.writeWithVersionCheck("rec6", content("k","v1"), 0, writer);
        store.writeWithVersionCheck("rec6", content("k","v2"), 1, writer);
        store.writeWithVersionCheck("rec6", content("k","v3"), 2, writer);
        var v1 = store.getVersion("rec6", 1);
        var v3 = store.getVersion("rec6", 3);
        assertTrue(v1.isPresent()); assertEquals("v1", v1.get().content().get("k"));
        assertTrue(v3.isPresent()); assertEquals("v3", v3.get().content().get("k"));
    }

    @Test void rollback_restoresOldVersion() {
        store.writeWithVersionCheck("rec7", content("k","v1"), 0, writer);
        store.writeWithVersionCheck("rec7", content("k","v2"), 1, writer);
        store.writeWithVersionCheck("rec7", content("k","v3"), 2, writer);
        assertTrue(store.rollback("rec7", 1, writer));
        var current = store.read("rec7", reader);
        assertTrue(current.isPresent());
        assertEquals("v1", current.get().content().get("k"));
    }

    @Test void rollback_emitsAuditEvent() {
        store.writeWithVersionCheck("rec8", content("k","v1"), 0, writer);
        store.writeWithVersionCheck("rec8", content("k","v2"), 1, writer);
        store.rollback("rec8", 1, writer);
        var events = bus.auditLog().stream()
            .filter(e -> "SHARED_MEMORY_ROLLBACK".equals(e.eventType())).toList();
        assertEquals(1, events.size());
        assertTrue(events.get(0).sensitive());
    }

    @Test void rollback_failsForNonExistentVersion() {
        store.writeWithVersionCheck("rec9", content("k","v1"), 0, writer);
        assertFalse(store.rollback("rec9", 99, writer));
    }

    @Test void rollback_rejectsUnauthorizedRole() {
        store.writeWithVersionCheck("rec10", content("k","v1"), 0, writer);
        assertThrows(SecurityException.class, () -> store.rollback("rec10", 1, unauthorizedWriter));
    }

    @Test void auditLog_isImmutable() {
        store.writeWithVersionCheck("rec11", content("k","v"), 0, writer);
        assertThrows(UnsupportedOperationException.class, () -> bus.auditLog().add(null));
    }

    @Test void readBatch_returnsMatchingRecords() {
        store.writeWithVersionCheck("batch1", content("a","1"), 0, writer);
        store.writeWithVersionCheck("batch2", content("b","2"), 0, writer);
        var query = new SharedMemoryQuery(List.of("batch1","batch2","nonexistent"), List.of(), 10);
        var results = store.readBatch(query, reader);
        assertEquals(2, results.size());
    }
}
