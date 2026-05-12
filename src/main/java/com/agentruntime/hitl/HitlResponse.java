package com.agentruntime.hitl;
import java.time.Instant; import java.util.Map;
public record HitlResponse(String requestId, String humanAnswer, String responderId, Map<String, Object> supplementalData, Instant respondedAt) {}
