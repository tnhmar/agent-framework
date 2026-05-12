package com.agentruntime.core.valueobjects;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A versioned, RBAC-tagged record stored in shared memory.
 * Vol.1 Ch.13 §"Shared Memory" — per-record version history and access tags.
 */
public record VersionedRecord(
        String              recordId,
        Map<String, Object> content,
        int                 version,
        Instant             lastModified,
        AgentIdentity       lastWriter,
        List<String>        accessTags) {

    public VersionedRecord {
        Objects.requireNonNull(recordId,     "recordId must not be null");
        Objects.requireNonNull(lastModified, "lastModified must not be null");
        content    = content    != null ? Map.copyOf(content)    : Map.of();
        accessTags = accessTags != null ? List.copyOf(accessTags): List.of();
    }
}
