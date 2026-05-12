package com.agentruntime.memory.shared;

import com.agentruntime.core.enums.ArbitrationPriority;
import com.agentruntime.core.enums.ResolutionPolicy;
import com.agentruntime.core.enums.ValueProvenance;
import com.agentruntime.core.valueobjects.*;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;

/**
 * Vol1 Ch.12 §"Conflict Resolution Policies" — single-store 4-policy hierarchy:
 * (1) Source Authority (4-tier rank, V-15 fix), (2) Recency, (3) Confidence, (4) Human Arbitration.
 */
public class SingleStoreConflictResolver implements ConflictResolver {

    private final HumanArbitrationQueue arbitrationQueue;

    public SingleStoreConflictResolver(HumanArbitrationQueue arbitrationQueue) {
        this.arbitrationQueue = arbitrationQueue;
    }

    @Override
    public ConflictResolution resolve(List<ConflictingRecord> conflicts) {
        if (conflicts == null || conflicts.isEmpty())
            throw new IllegalArgumentException("Cannot resolve empty conflict list");

        // Policy 1: Source Authority — highest 4-tier rank wins (V-15 fix: uses sourceAuthorityRank())
        var byAuthority = conflicts.stream()
            .max(Comparator.comparingInt(c -> c.source().sourceAuthorityRank()));
        if (byAuthority.isPresent()) {
            var winner = byAuthority.get();
            var others = conflicts.stream()
                .filter(c -> !c.recordId().equals(winner.recordId()))
                .map(ConflictingRecord::recordId).toList();
            // Only apply if winner is strictly more authoritative than all others
            boolean dominated = conflicts.stream()
                .allMatch(c -> c.recordId().equals(winner.recordId()) ||
                    winner.source().sourceAuthorityRank() > c.source().sourceAuthorityRank());
            if (dominated) return canonical(winner, ResolutionPolicy.SOURCE_AUTHORITY, conflicts);
        }

        // Policy 2: Recency — most recent record is canonical (equal-source tiebreaker, Ch.12)
        var mostRecent = conflicts.stream().max(Comparator.comparing(ConflictingRecord::timestamp));
        if (mostRecent.isPresent()) {
            return canonical(mostRecent.get(), ResolutionPolicy.RECENCY, conflicts);
        }

        // Policy 3: Confidence — highest confidence wins
        var highestConf = conflicts.stream().max(Comparator.comparingDouble(ConflictingRecord::confidence));
        if (highestConf.isPresent()) {
            return canonical(highestConf.get(), ResolutionPolicy.CONFIDENCE, conflicts);
        }

        // Policy 4: Human Arbitration fallback
        arbitrationQueue.submit(new ArbitrationRequest(conflicts, ArbitrationPriority.HIGH));
        return ConflictResolution.pendingArbitration(conflicts);
    }

    private ConflictResolution canonical(ConflictingRecord winner, ResolutionPolicy policy, List<ConflictingRecord> all) {
        var suppressed = all.stream().filter(c -> !c.recordId().equals(winner.recordId())).map(ConflictingRecord::recordId).toList();
        return new ConflictResolution(winner.entityKey(), winner.attribute(),
            new CanonicalValue(winner.value(), ValueProvenance.AUTHORITATIVE_SOURCE, winner.confidence()),
            policy, suppressed, Instant.now());
    }
}
