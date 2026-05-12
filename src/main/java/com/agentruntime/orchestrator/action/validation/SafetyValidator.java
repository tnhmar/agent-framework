package com.agentruntime.orchestrator.action.validation;

import com.agentruntime.core.valueobjects.ExecutionContext;
import com.agentruntime.core.valueobjects.ValidationResult;
import com.agentruntime.orchestrator.reasoning.ReasoningResult;
import java.util.Set;

/**
 * Layer 4: Safety guard — absolute veto over actions that are unconditionally unsafe.
 * Vol1 Ch.7 §"Action Validation", layer 4.
 * This validator runs last and cannot be bypassed by any upstream validator passing.
 */
public class SafetyValidator implements ActionValidator {

    private static final Set<String> HARD_BLOCKED_ACTIONS = Set.of(
        "purge_all", "admin_delete", "self_replicate", "modify_own_code"
    );

    @Override
    public ValidationResult validate(ReasoningResult reasoning, ExecutionContext ctx) {
        for (String action : reasoning.selectedActions()) {
            if (HARD_BLOCKED_ACTIONS.contains(action.toLowerCase())) {
                return ValidationResult.rejected(name(),
                    "Safety veto: action '" + action + "' is unconditionally blocked");
            }
        }
        return ValidationResult.passed(name());
    }

    @Override
    public String name() { return "SafetyValidator"; }
}
