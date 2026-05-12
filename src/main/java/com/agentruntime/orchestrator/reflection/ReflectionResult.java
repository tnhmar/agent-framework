package com.agentruntime.orchestrator.reflection;
import java.util.List;
public record ReflectionResult(boolean goalAchieved, List<String> lessons, String nextStepSuggestion, boolean shouldTerminate) {}
