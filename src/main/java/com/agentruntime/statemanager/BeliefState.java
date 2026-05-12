package com.agentruntime.statemanager;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Provenance-tracked, confidence-annotated, conflict-detecting belief state.
 *
 * Vol.1 Ch.8 §"Belief State":
 *   "Belief state is not the same as the context window. It is a structured,
 *    queryable representation that exists separately from the token stream."
 *
 * Three guaranteed properties:
 *   1. Provenance tracking  — every belief links to its evidence source + timestamp
 *   2. Confidence annotation — every belief carries an explicit confidence level
 *   3. Conflict detection   — contradictions surface as BeliefConflict records,
 *                             never silently overwrite
 *
 * Thread-safe: ConcurrentHashMap for beliefs, CopyOnWriteArrayList for conflicts.
 */
public class BeliefState {

    private final ConcurrentHashMap<String, Belief>  beliefs   = new ConcurrentHashMap<>();
    private final CopyOnWriteArrayList<BeliefConflict> conflicts = new CopyOnWriteArrayList<>();

    // ── Mutation ─────────────────────────────────────────────────────────────

    /**
     * Assert a new belief or update an existing one.
     * If the incoming value contradicts the existing belief, records a conflict
     * and marks both the old and new belief as conflicted.
     *
     * @return the stored belief (may be flagged conflicted)
     */
    public Belief assertBelief(Belief incoming) {
        Objects.requireNonNull(incoming);
        return beliefs.compute(incoming.key(), (key, existing) -> {
            if (existing == null) return incoming;

            // Conflict detection: different value from different source
            if (!existing.value().equals(incoming.value())) {
                conflicts.add(new BeliefConflict(
                        existing.beliefId(), key,
                        existing.value(), incoming.value(),
                        incoming.sourceId(), Instant.now()));
                // Keep higher-confidence belief, mark both conflicted
                return incoming.confidence().value >= existing.confidence().value
                        ? incoming.withConflicted(true)
                        : existing.withConflicted(true);
            }
            // Same value — update confidence and evidence if incoming is stronger
            if (incoming.confidence().value > existing.confidence().value) {
                return existing.updatedValue(existing.value(), incoming.confidence(),
                        incoming.sourceId(), incoming.evidenceSummary(), incoming.evidenceAt());
            }
            return existing;
        });
    }

    /** Retract a belief by key. Returns true if it existed. */
    public boolean retract(String key) {
        return beliefs.remove(key) != null;
    }

    // ── Queries ───────────────────────────────────────────────────────────────

    public Optional<Belief> get(String key) {
        return Optional.ofNullable(beliefs.get(key));
    }

    public List<Belief> byMinConfidence(double minConfidence) {
        return beliefs.values().stream()
                .filter(b -> b.confidence().value >= minConfidence)
                .toList();
    }

    public List<Belief> conflicted() {
        return beliefs.values().stream().filter(Belief::conflicted).toList();
    }

    /** All recorded conflicts (audit trail — never cleared). */
    public List<BeliefConflict> conflictHistory() {
        return Collections.unmodifiableList(conflicts);
    }

    public boolean hasConflicts() {
        return !conflicts.isEmpty();
    }

    public int size()   { return beliefs.size(); }
    public boolean isEmpty() { return beliefs.isEmpty(); }
    public Collection<Belief> all() { return Collections.unmodifiableCollection(beliefs.values()); }
}
