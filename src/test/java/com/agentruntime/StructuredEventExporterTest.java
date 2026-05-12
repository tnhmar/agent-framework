package com.agentruntime;

import com.agentruntime.core.valueobjects.AgentIdentity;
import com.agentruntime.observability.*;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for StructuredEventExporter and ObservabilityBus exporter integration.
 */
class StructuredEventExporterTest {

    private AuditEvent sampleEvent() {
        AgentIdentity agent = new AgentIdentity("agent-1", "TestAgent", "ORCHESTRATOR", "default");
        return new AuditEvent("evt-1", "MEMORY_WRITE", agent,
                "write_shared_memory", Map.of("namespace", "ns1"), Instant.now(), false);
    }

    @Test
    void exportWritesJsonLineToStream() {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintStream ps = new PrintStream(baos);
        StructuredEventExporter exporter = new StructuredEventExporter(ps);

        exporter.export(sampleEvent());

        String output = baos.toString();
        assertFalse(output.isBlank(), "Expected JSON output but got empty string");
        assertTrue(output.contains("\"eventType\""), "Missing eventType field");
        assertTrue(output.contains("MEMORY_WRITE"),  "Missing event type value");
        assertTrue(output.contains("agent-1"),       "Missing agentId");
    }

    @Test
    void exportNeverThrows() {
        // Export should be silent even with a broken stream
        StructuredEventExporter exporter = new StructuredEventExporter(new PrintStream(new ByteArrayOutputStream()));
        assertDoesNotThrow(() -> exporter.export(sampleEvent()));
        assertDoesNotThrow(() -> exporter.export(null));
    }

    @Test
    void observabilityBusForwardsEventsToExporter() {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        StructuredEventExporter exporter = new StructuredEventExporter(new PrintStream(baos));

        ObservabilityBus bus = new ObservabilityBus();
        bus.addExporter(exporter);
        bus.emit(sampleEvent());

        // Audit log is immutable and has 1 entry
        assertEquals(1, bus.auditLog().size());
        // Exporter should have produced output
        assertFalse(baos.toString().isBlank());
    }

    @Test
    void auditLogRemainsImmutableAfterExport() {
        ObservabilityBus bus = new ObservabilityBus();
        bus.addExporter(StructuredEventExporter.stdout());

        bus.emit(sampleEvent());
        bus.emit(sampleEvent());

        assertEquals(2, bus.auditLog().size());
        // auditLog() must return unmodifiable view
        assertThrows(UnsupportedOperationException.class,
                () -> bus.auditLog().clear());
    }
}
