package com.agentruntime.memory.shared;

import com.agentruntime.core.enums.ArbitrationPriority;
import com.agentruntime.core.enums.ResolutionPolicy;
import com.agentruntime.core.enums.ValueProvenance;
import com.agentruntime.core.valueobjects.*;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Vol.1 Ch.13 §"Multi-Agent Conflict Resolution" — 4-level hierarchy:
 *
 *   Policy 1: Agent Authority    — orchestrator/coordinator overrides general agents
 *   Policy 2: Consensus          — majority value wins (quorum agreement)
 *   Policy 3: Temporal Precedence — earliest write canonical among equally authoritative agents
 *   Policy 4: Human Arbitration  — unresolvable cases escalated
 *
 * Consensus (Policy 2) runs BEFORE Temporal (Policy 3) per Ch.13 §13.2.2:
 * "If a quorum of agents independently agree on a value, that is canonical regardless of timestamps."
 */
public class MultiAgentConflictResolver implements ConflictResolver {

    private static final Set<String> AUTHORITY_ROLES =
            Set.of("orchestrator", "coordinator", "specialist");

    private final HumanArbitrationQueue arbitrationQueue;

    public MultiAgentConflictResolver(HumanArbitrationQueue arbitrationQueue) {
        this.arbitrationQueue = arbitrationQueue;
    }

    @Override
    public ConflictResolution resolve(List<ConflictingRecord> conflicts) {
        if (conflicts == null || conflicts.isEmpty())
            throw new IllegalArgumentException("Cannot resolve empty conflict list");

        // ── Policy 1: Agent Authority ─────────────────────────────────────────
        var authoritative = conflicts.stream()
                .filter(c -> AUTHORITY_ROLES.contains(c.writer().role()))
                .toList();
        var nonAuthoritative = conflicts.stream()
                .filter(c -> !AUTHORITY_ROLES.contains(c.writer().role()))
                .toList();
        if (!authoritative.isEmpty() && !nonAuthoritative.isEmpty()) {
            var winner = authoritative.stream()
                    .max(Comparator.comparing(c -> c.source().sourceAuthorityRank()))
                    .get();
            return canonical(winner, ResolutionPolicy.SOURCE_AUTHORITY, conflicts);
        }

        // ── Policy 2: Consensus (majority quorum) ─────────────────────────────
        var byValue = conflicts.stream()
                .collect(Collectors.groupingBy(ConflictingRecord::value));
        var consensus = byValue.entrySet().stream()
                .filter(e -> e.getValue().size() > 1)
                .max(Comparator.comparingInt(e -> e.getValue().size()));

        if (consensus.isPresent()) {
            var agreeing = consensus.get().getValue();
            double avgConf = agreeing.stream()
                    .mapToDouble(ConflictingRecord::confidence).average().orElse(0.0);
            var representative = agreeing.get(0);
            var suppressed = conflicts.stream()
                    .filter(c -> !agreeing.contains(c))
                    .map(ConflictingRecord::recordId).toList();
            return new ConflictResolution(
                    representative.entityKey(), representative.attribute(),
                    new CanonicalValue(representative.value(), ValueProvenance.SYNTHESIZED,
                            Math.min(1.0, avgConf * 1.2)),
                    ResolutionPolicy.CONSENSUS, suppressed, Instant.now());
        }

        // ── Policy 3: Temporal Precedence ─────────────────────────────────────
        var earliest = conflicts.stream()
                .min(Comparator.comparing(ConflictingRecord::timestamp));
        if (earliest.isPresent()) {
            return canonical(earliest.get(), ResolutionPolicy.RECENCY, conflicts);
        }

        // ── Policy 4: Human Arbitration ───────────────────────────────────────
        arbitrationQueue.submit(new ArbitrationRequest(conflicts, ArbitrationPriority.HIGH));
        return ConflictResolution.pendingArbitration(conflicts);
    }

    private ConflictResolution canonical(ConflictingRecord winner, ResolutionPolicy policy,
                                         List<ConflictingRecord> all) {
        var suppressed = all.stream()
                .filter(c -> !c.recordId().equals(winner.recordId()))
                .map(ConflictingRecord::recordId).toList();
        return new ConflictResolution(
                winner.entityKey(), winner.attribute(),
                new CanonicalValue(winner.value(), ValueProvenance.AUTHORITATIVE_SOURCE,
                        winner.confidence()),
                policy, suppressed, Instant.now());
    }
}
