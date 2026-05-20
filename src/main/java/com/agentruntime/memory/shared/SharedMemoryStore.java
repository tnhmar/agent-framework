package com.agentruntime.memory.shared;

/**
 * Full shared memory store — composes reader and writer interfaces.
 * V-ISP-02: Consumers needing only read or write depend on the sub-interfaces.
 */
public interface SharedMemoryStore extends SharedMemoryReader, SharedMemoryWriter {}
