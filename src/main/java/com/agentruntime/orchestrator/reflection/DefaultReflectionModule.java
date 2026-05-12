package com.agentruntime.orchestrator.reflection;
import com.agentruntime.core.valueobjects.ExecutionContext;
import com.agentruntime.orchestrator.action.ActionResult;
import java.util.*;
public class DefaultReflectionModule implements ReflectionModule {
    @Override
    public ReflectionResult reflect(ActionResult actionResult, ExecutionContext ctx) {
        boolean achieved = actionResult.success();
        List<String> lessons = achieved ? List.of("Action executed successfully") : List.of("Action failed: " + actionResult.errorMessage());
        return new ReflectionResult(achieved, lessons, achieved ? "Terminate if goal met" : "Retry with adjusted plan", !achieved);
    }
}
