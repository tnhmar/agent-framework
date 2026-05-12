package com.agentruntime.orchestrator.action;
import com.agentruntime.core.valueobjects.ExecutionContext;
import com.agentruntime.orchestrator.reasoning.ReasoningResult;
public interface ActionModule { ActionResult execute(ReasoningResult reasoning, ExecutionContext ctx); }
