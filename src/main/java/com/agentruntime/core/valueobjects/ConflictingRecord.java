package com.agentruntime.core.valueobjects;
import java.time.Instant;
public record ConflictingRecord(
    MemoryRecordId recordId,
    String entityKey,
    String attribute,
    String value,
    RecordSource source,
    double confidence,
    Instant timestamp,
    AgentIdentity writer
) {}
