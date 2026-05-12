package com.agentruntime.statemanager;
import com.agentruntime.core.enums.AgentStatus;
import com.agentruntime.core.valueobjects.AgentIdentity;
import java.time.Instant; import java.util.Map;
public record AgentStateRecord(String executionId, AgentIdentity agentIdentity, AgentStatus status, int currentIteration, Map<String, Object> stateSnapshot, Instant lastUpdated) {}
