package com.agentruntime.example;

import com.agentruntime.configuration.AgentRuntimeConfig;
import com.agentruntime.core.enums.*;
import com.agentruntime.core.valueobjects.*;
import com.agentruntime.memory.session.SessionLifecycleManager;
import com.agentruntime.memory.shared.*;
import com.agentruntime.memory.working.WorkingMemoryStore;
import com.agentruntime.memory.episodic.EpisodicStore;
import com.agentruntime.memory.semantic.SemanticStore;
import com.agentruntime.memory.consolidation.MemoryConsolidator;
import com.agentruntime.memory.expiry.ExpiryPruner;
import com.agentruntime.observability.*;
import com.agentruntime.orchestrator.*;
import com.agentruntime.orchestrator.action.*;
import com.agentruntime.orchestrator.action.validation.*;
import com.agentruntime.orchestrator.delegation.DelegationContract;
import com.agentruntime.orchestrator.failuredetection.DefaultFailureDetectionModule;
import com.agentruntime.orchestrator.perception.*;
import com.agentruntime.orchestrator.prompting.*;
import com.agentruntime.orchestrator.reasoning.*;
import com.agentruntime.orchestrator.reflection.*;
import com.agentruntime.orchestrator.termination.*;
import com.agentruntime.security.*;
import com.agentruntime.statemanager.StateManager;
import com.agentruntime.tooling.rag.*;
import com.agentruntime.tooling.registry.*;
import com.agentruntime.tooling.middleware.LoggingMiddleware;
import com.agentruntime.tooling.async.AsyncToolExecutor;
import com.agentruntime.tooling.enterprise.DefaultEnterpriseAdapter;
import com.agentruntime.hitl.*;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

public class FinancialReportUseCase {

    public static void main(String[] args) throws Exception {
        AgentRuntimeConfig config = AgentRuntimeConfig.defaults();
        ObservabilityBus bus = new ObservabilityBus();

        // Shared memory with per-namespace consistency (V-04 fix)
        var sharedMemory = new InMemorySharedMemoryStore(
            Map.of("strong:", ConsistencyModel.STRONG, "causal:", ConsistencyModel.CAUSAL),
            ConsistencyModel.EVENTUAL,
            bus
        );
        var arbQueue = new InMemoryArbitrationQueue(bus);
        var singleStoreResolver = new SingleStoreConflictResolver(arbQueue);
        var multiAgentResolver = new MultiAgentConflictResolver(arbQueue);

        // Security + validation pipeline (V-19 fix)
        var policy = new SecurityPolicy("compliance-policy",
            Set.of("compliance", "admin", "orchestrator", "specialist", "coordinator"),
            Set.of("purge_all"), true, true);
        var enforcer = new SecurityEnforcer(policy);
        var validationPipeline = ActionValidationPipeline.defaultPipeline(enforcer);

        // RAG tooling
        var similarity = new CosineSimilarity();
        var mergePolicy = new TrustAwareFederatedMergePolicy(similarity);
        var retrievalCoordinator = new DefaultFederatedRetrievalCoordinator(mergePolicy);

        // Tool registry
        var toolRegistry = new ToolRegistry();
        toolRegistry.register(ToolDefinition.of("retrieve_context", "ContextRetriever", "Retrieve relevant context", ToolCategory.DATA_RETRIEVAL, Map.of(), false));
        toolRegistry.register(ToolDefinition.of("generate_response", "ResponseGenerator", "Generate output", ToolCategory.COMPUTATION, Map.of(), false));
        toolRegistry.register(ToolDefinition.of("validate_output", "OutputValidator", "Validate generated output", ToolCategory.COMPUTATION, Map.of(), false));

        var loggingMiddleware = new LoggingMiddleware();
        var asyncExecutor = new AsyncToolExecutor();

        // Action module with V-19 validation pipeline
        ActionModule actionModule = new DefaultActionModule(validationPipeline);

        // State manager wired to orchestrator (Drift-03 fix)
        var stateManager = new StateManager();

        // Orchestrator with delegation support (V-21 fix)
        var orchestrator = new DefaultAgentOrchestrator(
            new DefaultPerceptionModule(),
            new DefaultPromptingModule(),
            new DefaultReasoningModule(),
            actionModule,
            new DefaultReflectionModule(),
            new DefaultTerminationModule(),
            new DefaultFailureDetectionModule(),
            stateManager,
            config.orchestrator().maxDelegationDepth()
        );

        // Session lifecycle manager (V-10 fix)
        var episodic = new EpisodicStore();
        var semantic = new SemanticStore();
        var working = new WorkingMemoryStore();
        var consolidator = new MemoryConsolidator(episodic, semantic);
        var pruner = new ExpiryPruner(Duration.ofDays(30));
        var sessionManager = new SessionLifecycleManager(consolidator, working, pruner, episodic);

        // HITL
        var hitl = new InMemoryHitlGateway();
        var metrics = new MetricsCollector();

        // Run the financial report agent
        var agent = AgentIdentity.of("compliance-bot", "compliance");
        var ctx = ExecutionContext.of(agent);
        var task = new AgentTask("fin-report-001", "Generate daily Net Exposure report",
            Map.of("portfolios", List.of("A", "B", "C")), 5);

        var result = orchestrator.run(task, ctx);

        bus.emit(new AuditEvent(UUID.randomUUID().toString(), "EXECUTION_COMPLETE", agent,
            result.finalStatus().name(),
            Map.of("iterations", result.iterationsUsed(), "status", result.finalStatus().name()),
            Instant.now(), false));
        metrics.increment("report_generated");

        // Demonstrate delegation (V-21 fix)
        var subAgent = AgentIdentity.of("sub-report-agent", "specialist");
        var contract = DelegationContract.of(
            "Summarise portfolio B exposure",
            Map.of("portfolio", "B"),
            Duration.ofSeconds(30),
            1000L,
            "ExposureSummary JSON object",
            "Return empty summary on failure",
            agent, subAgent, 0
        );
        var delegationResult = orchestrator.delegate(contract, ctx);
        System.out.println("Delegation result: " + delegationResult.success());

        // Session-close 4-step handoff (V-10 fix)
        var closeReport = sessionManager.onSessionClose(ctx.executionId(), agent.agentId());
        System.out.println("Session closed: consolidated=" + closeReport.memoriesConsolidated()
            + " expired=" + closeReport.episodesExpired());

        if (hitl.pendingCount() > 0) {
            hitl.resolve(hitl.allPending().get(0).requestId(), "APPROVED", "human-compliance-officer");
        }

        System.out.println("Execution finished: " + result.finalStatus());
        System.out.println("Audit events (immutable): " + bus.auditLog().size());
        System.out.println("Metrics: " + metrics.snapshot());
        System.out.println("State manager active: " + stateManager.isActive(ctx.executionId()));
    }
}
