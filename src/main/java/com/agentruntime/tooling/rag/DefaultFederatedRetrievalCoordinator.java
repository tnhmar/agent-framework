package com.agentruntime.tooling.rag;

import com.agentruntime.core.enums.SourceTrustLabel;
import com.agentruntime.core.valueobjects.*;
import com.agentruntime.memory.episodic.EpisodicStore;
import com.agentruntime.memory.semantic.SemanticStore;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

/**
 * Federated retrieval coordinator — queries registered memory stores and merges results.
 *
 * S-01 FIX: All simulated data replaced with real memory-backed retrieval.
 *   - SemanticStore sources: queried via search(query)
 *   - EpisodicStore sources: recalled by agentId extracted from ExecutionContext
 *   - Unregistered sources: raises IllegalStateException (fail loudly, not silently)
 *
 * Register memory stores via MemoryStoreRegistry before use.
 *
 * Vol.1 Ch.11 §"Federated Retrieval".
 */
public class DefaultFederatedRetrievalCoordinator implements FederatedRetrievalCoordinator {

    private final FederatedMergePolicy mergePolicy;
    private final MemoryStoreRegistry  registry;

    public DefaultFederatedRetrievalCoordinator(FederatedMergePolicy mergePolicy,
                                                 MemoryStoreRegistry registry) {
        this.mergePolicy = Objects.requireNonNull(mergePolicy, "mergePolicy must not be null");
        this.registry    = Objects.requireNonNull(registry,    "registry must not be null");
    }

    /** Convenience constructor — creates an empty registry (must be populated before use). */
    public DefaultFederatedRetrievalCoordinator(FederatedMergePolicy mergePolicy) {
        this(mergePolicy, new MemoryStoreRegistry());
    }

    @Override
    public FederatedRetrievalResult retrieve(FederatedRetrievalRequest request,
                                              ExecutionContext ctx) {
        Objects.requireNonNull(request, "request must not be null");
        Objects.requireNonNull(ctx,     "ctx must not be null");
        if (request.topK() < 1)
            throw new IllegalArgumentException("topK must be >= 1, got: " + request.topK());
        if (request.sources().isEmpty())
            return new FederatedRetrievalResult(null, List.of(),
                    SourceTrustLabel.UNVERIFIED, Duration.ZERO);

        Instant start = Instant.now();
        List<FederatedRetrievalResult> sourceResults = new ArrayList<>();

        for (SourceDescriptor source : request.sources()) {
            List<RetrievalHit> hits = retrieveFromSource(request.query(), source,
                    request.topK(), ctx);
            sourceResults.add(new FederatedRetrievalResult(source, hits,
                    source.trustLabel(), Duration.between(start, Instant.now())));
        }

        List<RetrievalHit> mergedHits = mergePolicy.merge(sourceResults);
        // Cap to topK
        if (mergedHits.size() > request.topK())
            mergedHits = mergedHits.subList(0, request.topK());

        SourceDescriptor primary = request.sources().get(0);
        return new FederatedRetrievalResult(primary, mergedHits,
                SourceTrustLabel.VERIFIED, Duration.between(start, Instant.now()));
    }

    // ── Real retrieval from registered stores ────────────────────────────────

    private List<RetrievalHit> retrieveFromSource(String query, SourceDescriptor source,
                                                   int topK, ExecutionContext ctx) {
        // Try semantic store first
        Optional<SemanticStore> sem = registry.semantic(source.sourceId());
        if (sem.isPresent()) return retrieveFromSemantic(query, source, sem.get(), topK);

        // Try episodic store
        Optional<EpisodicStore> epi = registry.episodic(source.sourceId());
        if (epi.isPresent()) return retrieveFromEpisodic(query, source, epi.get(), ctx, topK);

        // S-01 FIX: fail loudly instead of returning fabricated data
        throw new IllegalStateException(
                "No memory store registered for sourceId='" + source.sourceId()
                + "'. Register via MemoryStoreRegistry before calling retrieve().");
    }

    private List<RetrievalHit> retrieveFromSemantic(String query, SourceDescriptor source,
                                                      SemanticStore store, int topK) {
        return store.search(query).stream()
                .limit(topK)
                .map(entry -> new RetrievalHit(
                        entry.id(),
                        scoreQuery(query, entry.definition()),
                        entry.version() * 0.1,        // version as proxy for importance
                        source, 0, null,
                        entry.updatedAt(),
                        entry.definition()))
                .sorted(Comparator.comparingDouble(RetrievalHit::similarityScore).reversed())
                .toList();
    }

    private List<RetrievalHit> retrieveFromEpisodic(String query, SourceDescriptor source,
                                                      EpisodicStore store,
                                                      ExecutionContext ctx, int topK) {
        String agentId = ctx.agentIdentity() != null
                ? ctx.agentIdentity().agentId() : "";
        return store.recallByAgent(agentId).stream()
                .filter(ep -> ep.summary().toLowerCase().contains(query.toLowerCase())
                           || query.isBlank())
                .limit(topK)
                .map(ep -> new RetrievalHit(
                        ep.id(),
                        scoreQuery(query, ep.summary()),
                        0.5,
                        source, 0, null,
                        ep.timestamp(),
                        ep.summary()))
                .sorted(Comparator.comparingDouble(RetrievalHit::similarityScore).reversed())
                .toList();
    }

    /** Simple keyword-overlap similarity score in [0,1]. */
    private static double scoreQuery(String query, String content) {
        if (query.isBlank() || content.isBlank()) return 0.0;
        String[] queryWords   = query.toLowerCase().split("\\W+");
        String   lowerContent = content.toLowerCase();
        long matches = Arrays.stream(queryWords)
                .filter(w -> w.length() > 2 && lowerContent.contains(w))
                .count();
        return Math.min(1.0, (double) matches / Math.max(1, queryWords.length));
    }

    public MemoryStoreRegistry registry() { return registry; }
}
