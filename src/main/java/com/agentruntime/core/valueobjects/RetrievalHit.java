package com.agentruntime.core.valueobjects;
import java.time.Instant;
public record RetrievalHit(
    MemoryRecordId recordId,
    double similarityScore,
    double importanceScore,
    SourceDescriptor source,
    int sourceRank,
    ConflictAnnotation conflictAnnotation,
    Instant timestamp,
    String content
) {
    public RetrievalHit withConflictAnnotation(ConflictAnnotation annotation) {
        return new RetrievalHit(recordId, similarityScore, importanceScore, source, sourceRank, annotation, timestamp, content);
    }
    public boolean hasConflict() { return conflictAnnotation != null; }
}
