package com.agentruntime.observability;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Write-immutable audit log + telemetry bus with optional structured exporters.
 *
 * V-08 invariant: audit log is append-only — records are never modified or cleared.
 * Vol.1 Ch.12 §12.7: "The audit log is write-immutable: records are appended, never modified."
 *
 * clearTelemetry() only clears operational telemetry; the audit log has no clear operation.
 * addExporter() wires a StructuredEventExporter for JSON-lines output without breaking immutability.
 */
public class ObservabilityBus {

    private final List<AuditEvent>             auditLog  = new CopyOnWriteArrayList<>();
    private final List<TelemetryRecord>        telemetry = new CopyOnWriteArrayList<>();
    private final List<StructuredEventExporter> exporters = new CopyOnWriteArrayList<>();

    // ── Exporter management ───────────────────────────────────────────────────

    /** Register a structured exporter. Events are forwarded after every emit(). */
    public void addExporter(StructuredEventExporter exporter) {
        if (exporter != null) exporters.add(exporter);
    }

    // ── Core emit / record ────────────────────────────────────────────────────

    /** Appends an audit event to the write-immutable log, then notifies exporters. */
    public void emit(AuditEvent event) {
        Objects.requireNonNull(event, "AuditEvent must not be null");
        auditLog.add(event);
        exporters.forEach(e -> e.export(event));   // best-effort; exporter must not throw
    }

    public void record(TelemetryRecord record) {
        if (record != null) telemetry.add(record);
    }

    // ── Queries ───────────────────────────────────────────────────────────────

    /** Unmodifiable view of the write-immutable audit log. */
    public List<AuditEvent> auditLog() {
        return Collections.unmodifiableList(auditLog);
    }

    public List<TelemetryRecord> telemetry() {
        return Collections.unmodifiableList(telemetry);
    }

    /** Null-safe filter by agent ID. */
    public List<AuditEvent> auditByAgent(String agentId) {
        Objects.requireNonNull(agentId, "agentId must not be null");
        return auditLog.stream()
                .filter(e -> e.agent() != null && agentId.equals(e.agent().agentId()))
                .toList();
    }

    public List<TelemetryRecord> slowSpans(long thresholdMs) {
        return telemetry.stream()
                .filter(t -> t.duration().toMillis() > thresholdMs)
                .toList();
    }

    /** Clears operational telemetry only. The audit log is NEVER cleared (V-08 invariant). */
    public void clearTelemetry() { telemetry.clear(); }
}
