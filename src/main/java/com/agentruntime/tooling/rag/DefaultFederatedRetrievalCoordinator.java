package com.agentruntime.tooling.rag;
import com.agentruntime.core.enums.SourceTrustLabel;
import com.agentruntime.core.valueobjects.*;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
public class DefaultFederatedRetrievalCoordinator implements FederatedRetrievalCoordinator {
    private final FederatedMergePolicy mergePolicy;
    public DefaultFederatedRetrievalCoordinator(FederatedMergePolicy mergePolicy) { this.mergePolicy = mergePolicy; }
    @Override
    public FederatedRetrievalResult retrieve(FederatedRetrievalRequest request, ExecutionContext ctx) {
        if (request.topK() < 1)
            throw new IllegalArgumentException("topK must be >= 1, got: " + request.topK());
        var start = Instant.now();
        var sourceResults = new ArrayList<FederatedRetrievalResult>();
        for (var source : request.sources()) {
            var hits = simulateSourceRetrieval(request.query(), source, request.topK());
            sourceResults.add(new FederatedRetrievalResult(source, hits, source.trustLabel(), Duration.ofMillis(10)));
        }
        var mergedHits = mergePolicy.merge(sourceResults);
        var primarySource = request.sources().isEmpty() ? null : request.sources().get(0);
        return new FederatedRetrievalResult(primarySource, mergedHits, SourceTrustLabel.VERIFIED, Duration.between(start, Instant.now()));
    }
    private List<RetrievalHit> simulateSourceRetrieval(String query, SourceDescriptor source, int topK) {
        var hits = new ArrayList<RetrievalHit>();
        for (int i = 0; i < Math.min(topK, 3); i++) {
            String content = "Sample content from " + source.sourceId() + " item " + i + " for query " + query;
            hits.add(new RetrievalHit(MemoryRecordId.of(source.sourceId() + "-" + i), 0.9 - i * 0.05, 0.8 - i * 0.05, source, i, null, Instant.now().minusSeconds(i), content));
        }
        return hits;
    }
}
