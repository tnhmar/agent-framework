package com.agentruntime.orchestrator;
import com.agentruntime.core.enums.AgentStatus;
import java.util.Map;
public record ExecutionResult(String executionId, AgentStatus finalStatus, Map<String, Object> outputs, String terminationReason, int iterationsUsed) {}
