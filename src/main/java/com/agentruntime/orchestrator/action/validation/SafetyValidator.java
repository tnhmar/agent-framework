package com.agentruntime.orchestrator.action.validation;

import com.agentruntime.core.valueobjects.ExecutionContext;
import com.agentruntime.core.valueobjects.ValidationResult;
import com.agentruntime.orchestrator.reasoning.ReasoningResult;

import java.util.Objects;

/**
 * Layer 4: Safety guard — absolute veto over unconditionally unsafe actions.
 * Vol.1 Ch.7 §"Action Validation", layer 4.
 *
 * V-OCP-01 FIX: Blocked action list is now externally injectable via SafetyPolicy.
 * The set is thread-safe and can be updated at runtime (e.g. from a config service).
 * defaultPipeline() still works unchanged via SafetyPolicy.defaults().
 */
public class SafetyValidator implements ActionValidator {

    private final SafetyPolicy policy;

    public SafetyValidator(SafetyPolicy policy) {
        this.policy = Objects.requireNonNull(policy, "policy must not be null");
    }

    /** Convenience constructor — uses the default blocked set. */
    public SafetyValidator() { this(SafetyPolicy.defaults()); }

    @Override
    public ValidationResult validate(ReasoningResult reasoning, ExecutionContext ctx) {
        for (String action : reasoning.selectedActions()) {
            if (policy.isBlocked(action)) {
                return ValidationResult.rejected(name(),
                        "Safety veto: action '" + action + "' is unconditionally blocked");
            }
        }
        return ValidationResult.passed(name());
    }

    @Override
    public String name() { return "SafetyValidator"; }

    public SafetyPolicy policy() { return policy; }
}
