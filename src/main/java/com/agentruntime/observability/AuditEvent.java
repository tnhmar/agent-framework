package com.agentruntime.observability;

import com.agentruntime.core.valueobjects.AgentIdentity;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Structured, write-immutable audit event.
 *
 * Vol.1 Ch.7 §"Action Logging" additions:
 *   stepNumber         — the orchestrator iteration index when the event occurred
 *   retryCount         — how many times this action was attempted before this event
 *   validationOutcomes — per-layer results from the ActionValidationPipeline
 *
 * All fields are immutable after construction (record).
 * Vol.1 Ch.12 §12.7: "The audit log is write-immutable: records are appended, never modified."
 */
public record AuditEvent(
        String              eventId,
        String              eventType,
        AgentIdentity       agent,
        String              operation,
        Map<String, Object> details,
        Instant             occurredAt,
        boolean             sensitive,
        int                 stepNumber,
        int                 retryCount,
        List<String>        validationOutcomes
) {
    public AuditEvent {
        Objects.requireNonNull(eventId,    "eventId must not be null");
        Objects.requireNonNull(eventType,  "eventType must not be null");
        Objects.requireNonNull(operation,  "operation must not be null");
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
        details            = details            != null ? Map.copyOf(details)               : Map.of();
        validationOutcomes = validationOutcomes != null ? List.copyOf(validationOutcomes)   : List.of();
    }

    /**
     * Backward-compatible 7-field factory for callers that don't need the new fields.
     * stepNumber=0, retryCount=0, validationOutcomes=empty.
     */
    public AuditEvent(String eventId, String eventType, AgentIdentity agent,
                      String operation, Map<String, Object> details,
                      Instant occurredAt, boolean sensitive) {
        this(eventId, eventType, agent, operation, details, occurredAt, sensitive, 0, 0, List.of());
    }

    /** Factory with step and retry context. */
    public static AuditEvent withContext(String eventId, String eventType, AgentIdentity agent,
                                         String operation, Map<String, Object> details,
                                         Instant occurredAt, boolean sensitive,
                                         int stepNumber, int retryCount,
                                         List<String> validationOutcomes) {
        return new AuditEvent(eventId, eventType, agent, operation, details,
                occurredAt, sensitive, stepNumber, retryCount, validationOutcomes);
    }
}
