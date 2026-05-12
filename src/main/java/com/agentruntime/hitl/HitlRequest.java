package com.agentruntime.hitl;
import com.agentruntime.core.valueobjects.AgentIdentity;
import java.time.Instant; import java.util.Map;
public record HitlRequest(String requestId, AgentIdentity requestingAgent, String question, Map<String, Object> context, HitlUrgency urgency, Instant submittedAt) {}
