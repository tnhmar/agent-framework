package com.agentruntime.orchestrator.perception;
import java.util.Map;
public record RawInput(String content, String modality, Map<String, Object> metadata) {}
