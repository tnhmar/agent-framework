package com.agentruntime.orchestrator.prompting;
import java.util.List; import java.util.Map;
public record PromptPackage(String systemPrompt, String userPrompt, List<Map<String, Object>> fewShotExamples, Map<String, Object> toolContext) {}
