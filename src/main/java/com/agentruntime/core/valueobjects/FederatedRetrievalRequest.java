package com.agentruntime.core.valueobjects;
import java.util.List;
public record FederatedRetrievalRequest(String query, List<SourceDescriptor> sources, int topK, boolean requireAttribution) {}
