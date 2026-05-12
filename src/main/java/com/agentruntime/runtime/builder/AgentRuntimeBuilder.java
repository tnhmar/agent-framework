package com.agentruntime.runtime.builder;

import com.agentruntime.memory.checkpoint.CheckpointStore;
import com.agentruntime.modelclient.ModelClient;
import com.agentruntime.modelclient.StubModelClient;
import com.agentruntime.observability.ObservabilityBus;
import com.agentruntime.observability.StructuredEventExporter;
import com.agentruntime.orchestrator.DefaultAgentOrchestrator;
import com.agentruntime.orchestrator.action.ActionModule;
import com.agentruntime.orchestrator.action.DefaultActionModule;
import com.agentruntime.orchestrator.action.validation.ActionValidationPipeline;
import com.agentruntime.orchestrator.failuredetection.DefaultFailureDetectionModule;
import com.agentruntime.orchestrator.failuredetection.FailureDetectionModule;
import com.agentruntime.orchestrator.perception.DefaultPerceptionModule;
import com.agentruntime.orchestrator.perception.PerceptionModule;
import com.agentruntime.orchestrator.prompting.DefaultPromptingModule;
import com.agentruntime.orchestrator.prompting.PromptingModule;
import com.agentruntime.orchestrator.reasoning.DefaultReasoningModule;
import com.agentruntime.orchestrator.reasoning.ReasoningModule;
import com.agentruntime.orchestrator.reflection.DefaultReflectionModule;
import com.agentruntime.orchestrator.reflection.ReflectionModule;
import com.agentruntime.orchestrator.termination.DefaultTerminationModule;
import com.agentruntime.orchestrator.termination.TerminationModule;
import com.agentruntime.runtime.run.InMemoryRunRepository;
import com.agentruntime.runtime.run.RunRepository;
import com.agentruntime.security.SecurityEnforcer;
import com.agentruntime.security.SecurityPolicy;
import com.agentruntime.security.TenantPolicyEngine;
import com.agentruntime.statemanager.StateManager;
import com.agentruntime.tooling.registry.ToolRegistry;

import java.time.Clock;
import java.util.Objects;

/**
 * Fluent builder for the agent-runtime module graph.
 *
 * Two assembly methods:
 *   build()        → DefaultAgentOrchestrator (backwards-compat)
 *   buildRuntime() → AgentRuntime (orchestrator + all infrastructure objects)
 */
public final class AgentRuntimeBuilder {

    // Required
    private ModelClient modelClient;

    // Optional — all have defaults
    private ToolRegistry       toolRegistry       = ToolRegistry.empty();
    private RunRepository      runRepository      = new InMemoryRunRepository();
    private CheckpointStore    checkpointStore    = new CheckpointStore();
    private ObservabilityBus   observabilityBus   = new ObservabilityBus();
    private SecurityPolicy     securityPolicy     = SecurityPolicy.allowAll();
    private TenantPolicyEngine tenantPolicyEngine = new TenantPolicyEngine();
    private Clock              clock              = Clock.systemUTC();
    private int                maxDelegationDepth = 5;
    private boolean            verbose            = false;

    // Module overrides (null = use defaults)
    private PerceptionModule       perceptionModule;
    private PromptingModule        promptingModule;
    private ReasoningModule        reasoningModule;
    private ActionModule           actionModule;
    private ReflectionModule       reflectionModule;
    private TerminationModule      terminationModule;
    private FailureDetectionModule failureDetectionModule;
    private StateManager           stateManager;

    // ── Setters ───────────────────────────────────────────────────────────────

    public AgentRuntimeBuilder modelClient(ModelClient mc)          { this.modelClient = Objects.requireNonNull(mc); return this; }
    public AgentRuntimeBuilder toolRegistry(ToolRegistry tr)        { this.toolRegistry = Objects.requireNonNull(tr); return this; }
    public AgentRuntimeBuilder runRepository(RunRepository rr)      { this.runRepository = Objects.requireNonNull(rr); return this; }
    public AgentRuntimeBuilder checkpointStore(CheckpointStore cs)  { this.checkpointStore = Objects.requireNonNull(cs); return this; }
    public AgentRuntimeBuilder observabilityBus(ObservabilityBus ob){ this.observabilityBus = Objects.requireNonNull(ob); return this; }
    public AgentRuntimeBuilder securityPolicy(SecurityPolicy sp)    { this.securityPolicy = Objects.requireNonNull(sp); return this; }
    public AgentRuntimeBuilder tenantPolicyEngine(TenantPolicyEngine tpe) { this.tenantPolicyEngine = Objects.requireNonNull(tpe); return this; }
    public AgentRuntimeBuilder clock(Clock c)                       { this.clock = Objects.requireNonNull(c); return this; }
    public AgentRuntimeBuilder maxDelegationDepth(int d)            { this.maxDelegationDepth = d; return this; }
    public AgentRuntimeBuilder verbose(boolean v)                   { this.verbose = v; return this; }
    public AgentRuntimeBuilder perceptionModule(PerceptionModule m)      { this.perceptionModule = m; return this; }
    public AgentRuntimeBuilder promptingModule(PromptingModule m)        { this.promptingModule = m; return this; }
    public AgentRuntimeBuilder reasoningModule(ReasoningModule m)        { this.reasoningModule = m; return this; }
    public AgentRuntimeBuilder actionModule(ActionModule m)              { this.actionModule = m; return this; }
    public AgentRuntimeBuilder reflectionModule(ReflectionModule m)      { this.reflectionModule = m; return this; }
    public AgentRuntimeBuilder terminationModule(TerminationModule m)    { this.terminationModule = m; return this; }
    public AgentRuntimeBuilder failureDetectionModule(FailureDetectionModule m) { this.failureDetectionModule = m; return this; }
    public AgentRuntimeBuilder stateManager(StateManager sm)             { this.stateManager = sm; return this; }

    // ── Assembly ──────────────────────────────────────────────────────────────

    public DefaultAgentOrchestrator build()  { return buildRuntime().orchestrator(); }

    public AgentRuntime buildRuntime() {
        Objects.requireNonNull(modelClient, "modelClient is required — call .modelClient(...)");
        if (verbose) observabilityBus.addExporter(StructuredEventExporter.stdout());

        SecurityEnforcer enforcer = new SecurityEnforcer(securityPolicy);
        StateManager     sm       = stateManager != null ? stateManager
                                                         : new StateManager(observabilityBus);

        PerceptionModule       pm  = perceptionModule  != null ? perceptionModule  : new DefaultPerceptionModule();
        PromptingModule        prm = promptingModule   != null ? promptingModule   : new DefaultPromptingModule();
        ReasoningModule        rm  = reasoningModule   != null ? reasoningModule   : new DefaultReasoningModule(modelClient);
        ActionModule           am  = actionModule      != null ? actionModule
                : new DefaultActionModule(ActionValidationPipeline.defaultPipeline(enforcer), checkpointStore);
        ReflectionModule       ref = reflectionModule  != null ? reflectionModule  : new DefaultReflectionModule();
        TerminationModule      tm  = terminationModule != null ? terminationModule : new DefaultTerminationModule();
        FailureDetectionModule fm  = failureDetectionModule != null ? failureDetectionModule : new DefaultFailureDetectionModule();

        DefaultAgentOrchestrator orchestrator = new DefaultAgentOrchestrator(
                pm, prm, rm, am, ref, tm, fm, sm, maxDelegationDepth);

        return new AgentRuntime(orchestrator, runRepository, checkpointStore,
                observabilityBus, toolRegistry, tenantPolicyEngine);
    }

    public static DefaultAgentOrchestrator stub() {
        return new AgentRuntimeBuilder().modelClient(StubModelClient.direct("Stub")).build();
    }

    public static AgentRuntime stubRuntime() {
        return new AgentRuntimeBuilder().modelClient(StubModelClient.direct("Stub")).buildRuntime();
    }
}
