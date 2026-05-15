package com.agentruntime.memory.knowledgegraph;
import com.agentruntime.core.valueobjects.MemoryRecordId;
import java.util.Objects;
/**
 * Directed edge in the knowledge graph.
 * P2-07: null guards on all mandatory fields.
 */
public record KgEdge(MemoryRecordId fromNode, MemoryRecordId toNode,
                     String relationshipType, double weight) {
    public KgEdge {
        Objects.requireNonNull(fromNode,         "fromNode must not be null");
        Objects.requireNonNull(toNode,           "toNode must not be null");
        Objects.requireNonNull(relationshipType, "relationshipType must not be null");
        if (weight < 0 || weight > 1)
            throw new IllegalArgumentException("weight must be in [0,1], got: " + weight);
    }
}
