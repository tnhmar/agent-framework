package com.agentruntime.statemanager;

import java.time.Instant;
import java.util.Objects;

/**
 * A single provenance-tracked belief about the world.
 *
 * Vol.1 Ch.8 §"Belief State":
 *   "Provenance tracking: each belief links to the evidence that produced it,
 *    the source identifier, and the timestamp of that evidence."
 *   "Confidence annotation: beliefs from authoritative sources carry higher
 *    confidence than those inferred from circumstantial evidence."
 */
public record Belief(
        String          beliefId,
        String          key,              // e.g. "order.88931.duplicate_charge"
        String          value,            // e.g. "confirmed"
        BeliefConfidence confidence,
        String          sourceId,         // tool call id, document id, etc.
        String          evidenceSummary,  // human-readable evidence description
        Instant         evidenceAt,       // timestamp of the evidence
        Instant         createdAt,
        Instant         updatedAt,
        boolean         conflicted        // true if a contradiction was detected
) {
    public Belief {
        Objects.requireNonNull(beliefId,   "beliefId must not be null");
        Objects.requireNonNull(key,        "key must not be null");
        Objects.requireNonNull(value,      "value must not be null");
        Objects.requireNonNull(confidence, "confidence must not be null");
        Objects.requireNonNull(sourceId,   "sourceId must not be null");
        Objects.requireNonNull(evidenceAt, "evidenceAt must not be null");
    }

    /** Canonical factory — sets createdAt and updatedAt to now. */
    public static Belief of(String beliefId, String key, String value,
                            BeliefConfidence confidence, String sourceId,
                            String evidenceSummary, Instant evidenceAt) {
        Instant now = Instant.now();
        return new Belief(beliefId, key, value, confidence,
                sourceId, evidenceSummary, evidenceAt, now, now, false);
    }

    public Belief withConflicted(boolean conflicted) {
        return new Belief(beliefId, key, value, confidence, sourceId,
                evidenceSummary, evidenceAt, createdAt, Instant.now(), conflicted);
    }

    public Belief updatedValue(String newValue, BeliefConfidence newConfidence,
                               String newSourceId, String newEvidence, Instant newEvidenceAt) {
        return new Belief(beliefId, key, newValue, newConfidence, newSourceId,
                newEvidence, newEvidenceAt, createdAt, Instant.now(), false);
    }
}
