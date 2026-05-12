package com.agentruntime.configuration;
import com.agentruntime.core.enums.SourceTrustLabel;
public record RagConfig(
    int topK,
    double similarityDedupThreshold,
    boolean enableFederatedMerge,
    SourceTrustLabel minimumTrustForInjection,
    int maxSourcesPerQuery
) {
    public static RagConfig defaults() {
        return new RagConfig(10, 0.85, true, SourceTrustLabel.AGENT_INFERRED, 5);
    }
}
