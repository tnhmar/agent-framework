package com.agentruntime.orchestrator.reasoning;
import java.util.List;
public record ReasoningResult(String plan, List<String> selectedActions, String rationale, double confidence, boolean requiresHumanInput) {}
