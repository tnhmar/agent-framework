package com.agentruntime.memory.shared;

import com.agentruntime.core.valueobjects.*;
import java.util.Map;

/**
 * Write-only shared memory interface.
 * V-ISP-02: Write-only producers depend only on this.
 */
public interface SharedMemoryWriter {
    WriteResult writeWithVersionCheck(String recordId, Map<String, Object> content,
                                      int expectedVersion, AgentIdentity writer);
    boolean rollback(String recordId, int targetVersion, AgentIdentity requester);
}
