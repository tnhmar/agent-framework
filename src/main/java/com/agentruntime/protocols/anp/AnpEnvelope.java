package com.agentruntime.protocols.anp;
import java.time.Instant; import java.util.Map;
public record AnpEnvelope(String envelopeId, String sourceNode, String targetNode, String networkTopic, Map<String, Object> body, Instant dispatchedAt) {}
