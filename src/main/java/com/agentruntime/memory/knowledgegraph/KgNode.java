package com.agentruntime.memory.knowledgegraph;
import com.agentruntime.core.valueobjects.MemoryRecordId;
import java.util.Map;
public record KgNode(MemoryRecordId id, String label, Map<String, String> properties) {}
