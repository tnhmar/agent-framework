package com.agentruntime.orchestrator.action;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Result of an action dispatch cycle.
 * Extended to include validationOutcomes per Vol.1 Ch.7 action logging requirements (G-10).
 */
public record ActionResult(
        boolean             success,
        Map<String, Object> outputs,
        List<String>        executedActions,
        String              errorMessage,
        List<String>        validationOutcomes
) {
    public ActionResult {
        outputs            = outputs            != null ? Map.copyOf(outputs)            : Map.of();
        executedActions    = executedActions    != null ? List.copyOf(executedActions)   : List.of();
        validationOutcomes = validationOutcomes != null ? List.copyOf(validationOutcomes): List.of();
    }

    @SuppressWarnings("unchecked")
    public static ActionResult success(Map<String, Object> outputs, List<String> validationOutcomes) {
        List<String> actions = outputs != null && outputs.get("actions") instanceof List
                ? (List<String>) outputs.get("actions") : List.of();
        return new ActionResult(true, outputs, actions, null, validationOutcomes);
    }

    public static ActionResult failure(String errorMessage, List<String> validationOutcomes) {
        return new ActionResult(false, Map.of(), List.of(), errorMessage, validationOutcomes);
    }
}
