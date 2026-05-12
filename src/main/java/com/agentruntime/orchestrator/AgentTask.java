package com.agentruntime.orchestrator;
import java.util.Map;
public record AgentTask(String taskId, String goal, Map<String, Object> inputs, int maxIterations) {}
