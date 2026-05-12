package com.agentruntime.memory.expiry;

import com.agentruntime.memory.episodic.EpisodicStore;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * TTL-based expiry pruner for episodic memory.
 * Vol.1 Ch.12 §"TTL/Expiry": identifies episodes older than the configured TTL.
 *
 * findExpired() is read-only — it returns IDs for the caller to act on.
 * Deletion is the caller's responsibility (separation of concerns).
 */
public class ExpiryPruner {

    private final Duration defaultTtl;

    public ExpiryPruner(Duration defaultTtl) {
        Objects.requireNonNull(defaultTtl, "defaultTtl must not be null");
        if (defaultTtl.isNegative() || defaultTtl.isZero())
            throw new IllegalArgumentException("defaultTtl must be positive");
        this.defaultTtl = defaultTtl;
    }

    /**
     * Returns the IDs of all episodes in {@code store} whose timestamp
     * is older than {@code now - defaultTtl}.
     *
     * @param store the episodic store to scan
     * @param now   the reference instant (use {@link Instant#now()} in production)
     * @return list of expired episode IDs (never null, may be empty)
     */
    public List<String> findExpired(EpisodicStore store, Instant now) {
        Objects.requireNonNull(store, "store must not be null");
        Objects.requireNonNull(now,   "now must not be null");

        Instant cutoff = now.minus(defaultTtl);
        return store.recallRecent(Integer.MAX_VALUE).stream()
                .filter(e -> e.timestamp().isBefore(cutoff))
                .map(e -> e.id().value())
                .toList();
    }

    public Duration defaultTtl() { return defaultTtl; }
}
