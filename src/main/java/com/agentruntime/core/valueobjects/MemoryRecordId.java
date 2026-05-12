package com.agentruntime.core.valueobjects;
import java.util.UUID;
public record MemoryRecordId(String value) {
    public static MemoryRecordId generate() { return new MemoryRecordId(UUID.randomUUID().toString()); }
    public static MemoryRecordId of(String value) { return new MemoryRecordId(value); }
}
