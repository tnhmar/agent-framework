package com.agentruntime.orchestrator.perception;
import java.util.List; import java.util.Map;
public record PerceptionResult(String normalizedContent, List<String> extractedEntities, Map<String, Object> structuredData, double confidenceScore) {}
