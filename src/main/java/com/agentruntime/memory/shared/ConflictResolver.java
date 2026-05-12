package com.agentruntime.memory.shared;
import com.agentruntime.core.valueobjects.ConflictingRecord;
import com.agentruntime.core.valueobjects.ConflictResolution;
import java.util.List;
public interface ConflictResolver { ConflictResolution resolve(List<ConflictingRecord> conflicts); }
