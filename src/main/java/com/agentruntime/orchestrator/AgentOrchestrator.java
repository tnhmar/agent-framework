package com.agentruntime.orchestrator;

/**
 * Full agent orchestrator — composes all three segregated capability interfaces.
 * V-ISP-01: Clients that need only a subset depend on AgentRunner, AgentLifecycle,
 * or AgentDelegate directly. This interface is for consumers needing all capabilities.
 */
public interface AgentOrchestrator extends AgentRunner, AgentDelegate, AgentLifecycle {}
