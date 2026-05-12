package com.agentruntime.observability;

import java.io.PrintStream;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * JSON-lines structured event exporter — pure JDK, no external dependencies.
 * Appends one JSON line per AuditEvent to the configured PrintStream.
 * Best-effort: never throws, never blocks.
 */
public final class StructuredEventExporter {

    private final PrintStream out;

    public StructuredEventExporter(PrintStream out) { this.out = out; }

    public static StructuredEventExporter stdout() { return new StructuredEventExporter(System.out); }
    public static StructuredEventExporter stderr() { return new StructuredEventExporter(System.err); }

    public void export(AuditEvent event) {
        if (event == null || out == null) return;
        try {
            StringBuilder sb = new StringBuilder("{");
            append(sb, "ts",        Instant.now().toString()); sb.append(',');
            append(sb, "eventId",   event.eventId()); sb.append(',');
            append(sb, "eventType", event.eventType()); sb.append(',');
            append(sb, "agentId",
                    event.agent() != null ? event.agent().agentId() : null); sb.append(',');
            append(sb, "operation", event.operation()); sb.append(',');
            appendBool(sb, "sensitive", event.sensitive());
            if (event.details() != null && !event.details().isEmpty()) {
                sb.append(',');
                sb.append("\"details\":{");
                boolean first = true;
                for (Map.Entry<String, Object> e : event.details().entrySet()) {
                    if (!first) sb.append(',');
                    first = false;
                    append(sb, e.getKey(), String.valueOf(e.getValue()));
                }
                sb.append('}');
            }
            sb.append('}');
            out.println(sb);
        } catch (Exception ignored) { /* best-effort */ }
    }

    private static void append(StringBuilder sb, String key, String value) {
        sb.append('"').append(escape(key)).append("\":");
        if (value == null) { sb.append("null"); return; }
        sb.append('"').append(escape(value)).append('"');
    }

    private static void appendBool(StringBuilder sb, String key, boolean value) {
        sb.append('"').append(escape(key)).append("\":").append(value);
    }

    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }
}
