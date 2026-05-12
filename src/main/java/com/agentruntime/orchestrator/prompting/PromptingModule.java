package com.agentruntime.orchestrator.prompting;
import com.agentruntime.core.valueobjects.ExecutionContext;
import com.agentruntime.orchestrator.perception.PerceptionResult;
public interface PromptingModule { PromptPackage build(PerceptionResult perception, String goal, ExecutionContext ctx); }
