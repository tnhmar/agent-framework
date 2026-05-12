package com.agentruntime.orchestrator.reflection;
import com.agentruntime.core.valueobjects.ExecutionContext;
import com.agentruntime.orchestrator.action.ActionResult;
public interface ReflectionModule { ReflectionResult reflect(ActionResult actionResult, ExecutionContext ctx); }
