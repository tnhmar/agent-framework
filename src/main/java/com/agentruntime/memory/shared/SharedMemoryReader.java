package com.agentruntime.memory.shared;

import com.agentruntime.core.valueobjects.*;
import java.util.List;
import java.util.Optional;

/**
 * Read-only shared memory interface.
 * V-ISP-02: Read-only consumers depend only on this, not on mutation methods.
 */
public interface SharedMemoryReader {
    Optional<VersionedRecord> read(String recordId, AgentIdentity reader);
    List<VersionedRecord> readBatch(SharedMemoryQuery query, AgentIdentity reader);
    Optional<VersionedRecord> getVersion(String recordId, int version);
}
