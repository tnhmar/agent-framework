package com.agentruntime.memory.shared;

import com.agentruntime.core.valueobjects.*;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface SharedMemoryStore {
    VersionedRecord read(String recordId, AgentIdentity reader);
    WriteResult writeWithVersionCheck(String recordId, Map<String, Object> content,
                                      int expectedVersion, AgentIdentity writer);
    List<VersionedRecord> readBatch(SharedMemoryQuery query, AgentIdentity reader);
    Optional<VersionedRecord> getVersion(String recordId, int version);
    boolean rollback(String recordId, int targetVersion, AgentIdentity requester);
}
