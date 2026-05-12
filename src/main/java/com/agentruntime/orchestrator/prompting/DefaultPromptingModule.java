package com.agentruntime.orchestrator.prompting;
import com.agentruntime.core.valueobjects.ExecutionContext;
import com.agentruntime.orchestrator.perception.PerceptionResult;
import java.util.*;
public class DefaultPromptingModule implements PromptingModule {
    @Override
    public PromptPackage build(PerceptionResult perception, String goal, ExecutionContext ctx) {
        String system = "You are an intelligent agent. Goal: " + goal + ". Entities: " + String.join(", ", perception.extractedEntities());
        return new PromptPackage(system, perception.normalizedContent(), List.of(), Map.of("executionId", ctx.executionId()));
    }
}
