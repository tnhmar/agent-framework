package com.agentruntime.memory.usermodel;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Structured, confidence-gated user model.
 * Vol.1 Ch.13 §"User Model Schema" + §"Constructing and Updating the User Model":
 *
 * Supports three-case merge logic:
 *   1. New fact   → insert
 *   2. Same key, higher confidence → upgrade
 *   3. Same key, lower confidence  → keep existing (no silent downgrade)
 *
 * Confidence-gated injection: only facts above a minimum threshold
 * are returned for context injection at session start.
 */
public class UserModel {

    private final String userId;
    private final ConcurrentHashMap<String, UserModelFact> facts = new ConcurrentHashMap<>();
    private volatile Instant updatedAt = Instant.now();

    public UserModel(String userId) {
        this.userId = Objects.requireNonNull(userId, "userId must not be null");
    }

    // ── Three-case merge (Vol.1 §"Constructing and Updating the User Model") ──

    /**
     * Merge a fact into the model using Vol.1 three-case logic:
     *   Case 1 — key not present → insert
     *   Case 2 — key present, incoming confidence HIGHER → upgrade
     *   Case 3 — key present, incoming confidence SAME/LOWER → retain existing
     *
     * @return the fact that is now canonical (inserted, upgraded, or retained)
     */
    public UserModelFact merge(UserModelFact incoming) {
        Objects.requireNonNull(incoming);
        UserModelFact result = facts.compute(incoming.key(), (key, existing) -> {
            if (existing == null) return incoming;                         // Case 1
            if (incoming.confidence().value > existing.confidence().value) // Case 2
                return incoming;
            return existing;                                              // Case 3
        });
        updatedAt = Instant.now();
        return result;
    }

    /** Remove a fact by key (GDPR erasure support). Returns true if removed. */
    public boolean remove(String key) {
        boolean removed = facts.remove(key) != null;
        if (removed) updatedAt = Instant.now();
        return removed;
    }

    // ── Queries ───────────────────────────────────────────────────────────────

    /**
     * Confidence-gated retrieval — Vol.1 §"Confidence-Gated Injection":
     * only facts at or above minConfidence are returned for context injection.
     */
    public List<UserModelFact> getByCategory(String category, double minConfidence) {
        Objects.requireNonNull(category);
        return facts.values().stream()
                .filter(f -> f.category().equals(category)
                        && f.confidence().value >= minConfidence)
                .sorted(Comparator.comparingDouble(
                        (UserModelFact f) -> f.confidence().value).reversed())
                .toList();
    }

    public Optional<UserModelFact> get(String key) {
        return Optional.ofNullable(facts.get(key));
    }

    public List<UserModelFact> all()      { return List.copyOf(facts.values()); }
    public String   userId()              { return userId; }
    public Instant  updatedAt()           { return updatedAt; }
    public int      factCount()           { return facts.size(); }
}
