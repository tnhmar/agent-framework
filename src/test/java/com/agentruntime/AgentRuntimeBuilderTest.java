package com.agentruntime;

import com.agentruntime.modelclient.StubModelClient;
import com.agentruntime.orchestrator.DefaultAgentOrchestrator;
import com.agentruntime.runtime.builder.AgentRuntimeBuilder;
import com.agentruntime.usecase.research.ResearchAgentFactory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for AgentRuntimeBuilder and ResearchAgentFactory.
 * Validates that the fluent builder assembles a working DefaultAgentOrchestrator.
 */
class AgentRuntimeBuilderTest {

    @Test
    void builderWithStubModelClientProducesOrchestrator() {
        DefaultAgentOrchestrator orchestrator = new AgentRuntimeBuilder()
                .modelClient(StubModelClient.direct("test response"))
                .build();

        assertNotNull(orchestrator);
    }

    @Test
    void builderRequiresModelClient() {
        assertThrows(NullPointerException.class, () ->
                new AgentRuntimeBuilder().build()
        );
    }

    @Test
    void stubFactoryMethodProducesOrchestrator() {
        DefaultAgentOrchestrator orchestrator = AgentRuntimeBuilder.stub();
        assertNotNull(orchestrator);
    }

    @Test
    void researchAgentFactoryWithStubProducesOrchestrator() {
        DefaultAgentOrchestrator agent = ResearchAgentFactory.createWithStub();
        assertNotNull(agent);
    }

    @Test
    void researchAgentFactoryNullKeyUsesStub() {
        DefaultAgentOrchestrator agent = ResearchAgentFactory.create(null);
        assertNotNull(agent);
    }

    @Test
    void builderVerboseModeWiresStructuredExporter() {
        // Should complete without throwing — exporter is wired to ObservabilityBus
        assertDoesNotThrow(() ->
            new AgentRuntimeBuilder()
                    .modelClient(StubModelClient.direct("ok"))
                    .verbose(true)
                    .maxDelegationDepth(3)
                    .build()
        );
    }
}
