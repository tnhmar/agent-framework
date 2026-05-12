package com.agentruntime.protocols.a2a;
import com.agentruntime.core.valueobjects.AgentIdentity;
import java.time.Instant; import java.util.Map;
public record A2AMessage(String messageId, AgentIdentity sender, AgentIdentity receiver, String intent, Map<String, Object> payload, Instant timestamp) {}
