package com.agentruntime.security;
import com.agentruntime.core.valueobjects.AgentIdentity;
public class SecurityEnforcer {
    private final SecurityPolicy policy;
    public SecurityEnforcer(SecurityPolicy policy) { this.policy = policy; }
    public void enforce(AgentIdentity agent, String operation) {
        if (!policy.permits(agent, operation))
            throw new SecurityException("Agent '" + agent.agentName() + "' with role '" + agent.role() + "' is not permitted to perform: " + operation);
    }
    public boolean check(AgentIdentity agent, String operation) { return policy.permits(agent, operation); }
}
