package com.agentruntime.memory.knowledgegraph;
import com.agentruntime.core.valueobjects.MemoryRecordId;
import java.util.Map;
public record KgNode(MemoryRecordId id, String label, Map<String, String> properties) {
    public KgNode {
        java.util.Objects.requireNonNull(id,    "id must not be null");
        java.util.Objects.requireNonNull(label, "label must not be null");
        properties = properties != null ? java.util.Map.copyOf(properties) : java.util.Map.of();
    }
}
