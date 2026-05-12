package com.agentruntime.memory.knowledgegraph;
import com.agentruntime.core.valueobjects.MemoryRecordId;
public record KgEdge(MemoryRecordId fromNode, MemoryRecordId toNode, String relationshipType, double weight) {}
