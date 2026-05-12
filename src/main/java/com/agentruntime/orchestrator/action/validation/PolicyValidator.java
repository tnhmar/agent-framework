package com.agentruntime.orchestrator.action.validation;

import com.agentruntime.core.valueobjects.ExecutionContext;
import com.agentruntime.core.valueobjects.ValidationResult;
import com.agentruntime.orchestrator.reasoning.ReasoningResult;
import com.agentruntime.security.SecurityEnforcer;

/**
 * Layer 3: Policy / permission validation — checks the agent is authorized to execute the selected actions.
 * Vol1 Ch.7 §"Action Validation", layer 3.
 */
public class PolicyValidator implements ActionValidator {

    private final SecurityEnforcer securityEnforcer;

    public PolicyValidator(SecurityEnforcer securityEnforcer) {
        this.securityEnforcer = securityEnforcer;
    }

    @Override
    public ValidationResult validate(ReasoningResult reasoning, ExecutionContext ctx) {
        for (String action : reasoning.selectedActions()) {
            try {
                securityEnforcer.enforce(ctx.agentIdentity(), action);
            } catch (SecurityException e) {
                return ValidationResult.rejected(name(), "Policy violation: " + e.getMessage());
            }
        }
        return ValidationResult.passed(name());
    }

    @Override
    public String name() { return "PolicyValidator"; }
}
