package com.agentruntime.tooling.rag;

import com.agentruntime.core.enums.ConflictSeverity;
import com.agentruntime.core.valueobjects.*;

import java.util.*;
import java.util.stream.Collectors;

public class TrustAwareFederatedMergePolicy implements FederatedMergePolicy {

    private static final double SIMILARITY_THRESHOLD = 0.85;
    private static final int DEFAULT_TOP_K = 10;
    private final SemanticSimilarity semanticSimilarity;

    public TrustAwareFederatedMergePolicy(SemanticSimilarity semanticSimilarity) {
        this.semanticSimilarity = semanticSimilarity;
    }

    @Override
    public List<RetrievalHit> merge(List<FederatedRetrievalResult> sourceResults) {
        if (sourceResults == null || sourceResults.isEmpty()) return List.of();
        var allHits = sourceResults.stream()
            .flatMap(r -> r.hits().stream().map(h -> new LabeledHit(h, r.source().trustLabel())))
            .collect(Collectors.toList());
        var uniqueHits = deduplicate(allHits);
        var annotatedHits = conflictAnnotate(uniqueHits);
        return trustRankAndSelect(annotatedHits, DEFAULT_TOP_K);
    }

    private List<RetrievalHit> deduplicate(List<LabeledHit> hits) {
        var clusters = new ArrayList<List<LabeledHit>>();
        for (var hit : hits) {
            boolean placed = false;
            for (var cluster : clusters) {
                double sim = semanticSimilarity.similarity(cluster.get(0).hit().content(), hit.hit().content());
                if (sim >= SIMILARITY_THRESHOLD) { cluster.add(hit); placed = true; break; }
            }
            if (!placed) { var c = new ArrayList<LabeledHit>(); c.add(hit); clusters.add(c); }
        }
        return clusters.stream().map(this::selectRepresentative).collect(Collectors.toList());
    }

    private RetrievalHit selectRepresentative(List<LabeledHit> cluster) {
        return cluster.stream()
            .max(Comparator.comparingDouble(lh -> lh.trustLabel().trustMultiplier * lh.hit().similarityScore()))
            .map(LabeledHit::hit).orElseThrow();
    }

    private List<RetrievalHit> conflictAnnotate(List<RetrievalHit> hits) {
        var byEntity = hits.stream().collect(Collectors.groupingBy(h -> h.recordId().value()));
        return hits.stream().map(h -> {
            var group = byEntity.get(h.recordId().value());
            if (group != null && group.size() > 1) {
                var conflicts = group.stream()
                    .map(g -> new ValueConflict(g.recordId().value(),
                        g.source() != null
                            ? new RecordSource(g.source().sourceId(), g.source().description(), g.source().trustLabel(), false)
                            : RecordSource.trusted("unknown", "unknown"),
                        g.similarityScore()))
                    .toList();
                return h.withConflictAnnotation(new ConflictAnnotation(h.recordId().value(), conflicts, ConflictSeverity.MEDIUM));
            }
            return h;
        }).collect(Collectors.toList());
    }

    private List<RetrievalHit> trustRankAndSelect(List<RetrievalHit> hits, int topK) {
        return hits.stream()
            .sorted(Comparator
                .comparingDouble((RetrievalHit h) -> h.source() != null ? h.source().trustLabel().trustMultiplier : 0.0)
                .thenComparing(Comparator.comparing(RetrievalHit::timestamp).reversed())
                .thenComparingDouble(RetrievalHit::importanceScore)
                .reversed())
            .limit(topK)
            .collect(Collectors.toList());
    }
}
