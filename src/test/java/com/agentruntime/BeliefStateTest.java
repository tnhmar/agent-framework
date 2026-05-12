package com.agentruntime;

import com.agentruntime.statemanager.*;
import org.junit.jupiter.api.*;
import java.time.Instant;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for BeliefState — Vol.1 Ch.8 §"Belief State".
 * Covers: provenance tracking, confidence annotation, conflict detection.
 */
class BeliefStateTest {

    private BeliefState state;

    @BeforeEach void setUp() { state = new BeliefState(); }

    private Belief belief(String id, String key, String value, BeliefConfidence conf, String src) {
        return Belief.of(id, key, value, conf, src, "Evidence: " + value, Instant.now());
    }

    // ── Empty state ───────────────────────────────────────────────────────────

    @Test void emptyState_isEmpty() {
        assertTrue(state.isEmpty());
        assertEquals(0, state.size());
        assertFalse(state.hasConflicts());
    }

    @Test void get_unknownKey_returnsEmpty() {
        assertTrue(state.get("nonexistent").isEmpty());
    }

    // ── Assertion and retrieval ───────────────────────────────────────────────

    @Test void assertBelief_insertNewBelief() {
        Belief b = belief("b1", "user.language", "TypeScript", BeliefConfidence.AUTHORITATIVE, "user-msg-1");
        state.assertBelief(b);
        assertTrue(state.get("user.language").isPresent());
        assertEquals("TypeScript", state.get("user.language").get().value());
    }

    @Test void assertBelief_higherConfidenceUpgradesExisting() {
        state.assertBelief(belief("b1", "k", "v1", BeliefConfidence.LOW, "src-A"));
        state.assertBelief(belief("b2", "k", "v1", BeliefConfidence.HIGH, "src-B"));
        assertEquals(BeliefConfidence.HIGH, state.get("k").get().confidence());
        assertEquals("src-B", state.get("k").get().sourceId());
    }

    @Test void assertBelief_lowerConfidenceRetainsExisting() {
        state.assertBelief(belief("b1", "k", "v1", BeliefConfidence.AUTHORITATIVE, "src-A"));
        state.assertBelief(belief("b2", "k", "v1", BeliefConfidence.LOW,           "src-B"));
        assertEquals(BeliefConfidence.AUTHORITATIVE, state.get("k").get().confidence());
        assertEquals("src-A", state.get("k").get().sourceId());
    }

    // ── Conflict detection ────────────────────────────────────────────────────

    @Test void assertBelief_detectsConflictOnDifferentValues() {
        state.assertBelief(belief("b1", "order.status", "open",   BeliefConfidence.HIGH,   "tool-A"));
        state.assertBelief(belief("b2", "order.status", "closed", BeliefConfidence.MODERATE, "tool-B"));

        assertTrue(state.hasConflicts(), "Contradiction must surface as a conflict");
        assertEquals(1, state.conflictHistory().size());
        assertEquals("order.status", state.conflictHistory().get(0).key());
    }

    @Test void assertBelief_conflictFlagsBeliefAsConflicted() {
        state.assertBelief(belief("b1", "k", "v1", BeliefConfidence.MODERATE, "src-A"));
        state.assertBelief(belief("b2", "k", "v2", BeliefConfidence.HIGH,     "src-B"));
        assertTrue(state.get("k").get().conflicted(), "Winner must be flagged conflicted");
    }

    @Test void conflictHistory_isNeverCleared() {
        state.assertBelief(belief("b1", "k", "v1", BeliefConfidence.HIGH, "A"));
        state.assertBelief(belief("b2", "k", "v2", BeliefConfidence.HIGH, "B"));
        state.assertBelief(belief("b3", "k", "v3", BeliefConfidence.HIGH, "C"));
        assertEquals(2, state.conflictHistory().size());
    }

    @Test void conflictHistory_isUnmodifiable() {
        assertThrows(UnsupportedOperationException.class,
                () -> state.conflictHistory().add(null));
    }

    // ── Confidence-filtered retrieval ─────────────────────────────────────────

    @Test void byMinConfidence_filtersCorrectly() {
        state.assertBelief(belief("b1", "k1", "v1", BeliefConfidence.AUTHORITATIVE, "s1"));
        state.assertBelief(belief("b2", "k2", "v2", BeliefConfidence.HIGH,          "s2"));
        state.assertBelief(belief("b3", "k3", "v3", BeliefConfidence.SPECULATIVE,   "s3"));

        assertEquals(3, state.byMinConfidence(BeliefConfidence.SPECULATIVE.value).size());
        assertEquals(2, state.byMinConfidence(BeliefConfidence.HIGH.value).size());
        assertEquals(1, state.byMinConfidence(BeliefConfidence.AUTHORITATIVE.value).size());
    }

    // ── Retraction ───────────────────────────────────────────────────────────

    @Test void retract_removesExistingBelief() {
        state.assertBelief(belief("b1", "k", "v", BeliefConfidence.HIGH, "src"));
        assertTrue(state.retract("k"));
        assertTrue(state.get("k").isEmpty());
        assertEquals(0, state.size());
    }

    @Test void retract_returnsFalseForUnknownKey() {
        assertFalse(state.retract("nonexistent"));
    }

    // ── Provenance integrity ──────────────────────────────────────────────────

    @Test void assertBelief_preservesProvenanceFields() {
        Instant evidenceAt = Instant.parse("2026-01-15T10:00:00Z");
        Belief b = Belief.of("b1", "sys.version", "2.1.0",
                BeliefConfidence.AUTHORITATIVE, "tool-version-api",
                "API returned version 2.1.0", evidenceAt);
        state.assertBelief(b);

        Belief stored = state.get("sys.version").get();
        assertEquals("tool-version-api",         stored.sourceId());
        assertEquals(evidenceAt,                  stored.evidenceAt());
        assertEquals("API returned version 2.1.0", stored.evidenceSummary());
        assertEquals(BeliefConfidence.AUTHORITATIVE, stored.confidence());
    }

    // ── BeliefConfidence values ───────────────────────────────────────────────

    @Test void beliefConfidence_valuesAreOrdered() {
        assertTrue(BeliefConfidence.AUTHORITATIVE.value > BeliefConfidence.HIGH.value);
        assertTrue(BeliefConfidence.HIGH.value          > BeliefConfidence.MODERATE.value);
        assertTrue(BeliefConfidence.MODERATE.value      > BeliefConfidence.LOW.value);
        assertTrue(BeliefConfidence.LOW.value           > BeliefConfidence.SPECULATIVE.value);
    }

    @Test void beliefConfidence_authoritativeIsOne() {
        assertEquals(1.00, BeliefConfidence.AUTHORITATIVE.value, 0.001);
    }
}
