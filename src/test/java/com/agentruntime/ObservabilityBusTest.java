package com.agentruntime;

import com.agentruntime.core.valueobjects.AgentIdentity;
import com.agentruntime.observability.*;
import org.junit.jupiter.api.*;
import java.time.*;
import java.util.Map;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

/** Tests for V-08: audit log immutability. */
class ObservabilityBusTest {

    private ObservabilityBus bus;

    @BeforeEach
    void setUp() { bus = new ObservabilityBus(); }

    @Test
    void auditLog_returnedViewIsUnmodifiable() {
        bus.emit(event("TEST"));
        var log = bus.auditLog();
        assertThrows(UnsupportedOperationException.class, () -> log.add(null));
        assertThrows(UnsupportedOperationException.class, () -> log.remove(0));
        assertThrows(UnsupportedOperationException.class, log::clear);
    }

    @Test
    void auditLog_appendsInOrder() {
        bus.emit(event("FIRST"));
        bus.emit(event("SECOND"));
        bus.emit(event("THIRD"));
        var log = bus.auditLog();
        assertEquals(3, log.size());
        assertEquals("FIRST",  log.get(0).eventType());
        assertEquals("SECOND", log.get(1).eventType());
        assertEquals("THIRD",  log.get(2).eventType());
    }

    @Test
    void auditLog_notClearedByTelemetryClear() {
        bus.emit(event("AUDIT"));
        bus.record(new TelemetryRecord("t1","s1","op", Duration.ofMillis(5), true, Map.of(), Instant.now()));
        bus.clearTelemetry();
        assertEquals(1, bus.auditLog().size(), "Audit log must survive clearTelemetry()");
        assertEquals(0, bus.telemetry().size());
    }

    @Test
    void telemetry_canBeCleared() {
        bus.record(new TelemetryRecord("t1","s1","op", Duration.ofMillis(5), true, Map.of(), Instant.now()));
        assertEquals(1, bus.telemetry().size());
        bus.clearTelemetry();
        assertEquals(0, bus.telemetry().size());
    }

    @Test
    void auditLog_survivesMultipleClearTelemetryCalls() {
        bus.emit(event("E1")); bus.emit(event("E2"));
        bus.clearTelemetry();
        bus.clearTelemetry();
        assertEquals(2, bus.auditLog().size());
    }

    @Test
    void auditByAgent_filtersCorrectly() {
        var agent = AgentIdentity.of("bot", "orchestrator");
        var other = AgentIdentity.of("other", "specialist");
        bus.emit(new AuditEvent(UUID.randomUUID().toString(),"OP", agent, "action", Map.of(), Instant.now(), false));
        bus.emit(new AuditEvent(UUID.randomUUID().toString(),"OP", other, "action", Map.of(), Instant.now(), false));
        assertEquals(1, bus.auditByAgent(agent.agentId()).size());
    }

    private AuditEvent event(String type) {
        return new AuditEvent(UUID.randomUUID().toString(), type,
            AgentIdentity.of("sys","admin"), "op", Map.of(), Instant.now(), false);
    }
}
