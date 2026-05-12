package com.agentruntime.observability;
import java.time.Duration; import java.time.Instant; import java.util.Map;
public record TelemetryRecord(String traceId, String spanId, String operationName, Duration duration, boolean success, Map<String, Object> tags, Instant startTime) {}
