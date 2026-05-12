package com.agentruntime.protocols.a2a;
import java.time.Instant; import java.util.Map;
/** V-12 fix: A2A Task with status lifecycle. */
public record A2ATask(String taskId, A2ATaskStatus status, AgentCard sender, String intent, Map<String, Object> payload, Instant createdAt) {}
