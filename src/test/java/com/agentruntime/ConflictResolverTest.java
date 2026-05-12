package com.agentruntime;

import com.agentruntime.core.enums.*;
import com.agentruntime.core.valueobjects.*;
import com.agentruntime.memory.shared.*;
import com.agentruntime.observability.ObservabilityBus;
import org.junit.jupiter.api.*;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for V-02 (separate resolvers), V-05 (CONSENSUS), V-15 (4-tier source authority).
 */
class ConflictResolverTest {

    private InMemoryArbitrationQueue arbQueue;
    private SingleStoreConflictResolver singleStore;
    private MultiAgentConflictResolver multiAgent;

    @BeforeEach
    void setUp() {
        arbQueue = new InMemoryArbitrationQueue(new ObservabilityBus());
        singleStore = new SingleStoreConflictResolver(arbQueue);
        multiAgent = new MultiAgentConflictResolver(arbQueue);
    }

    // --- V-15: 4-tier source authority rank ---

    @Test
    void recordSource_verifiedRankIs4() {
        assertEquals(4, new RecordSource("id","n", SourceTrustLabel.VERIFIED, true).sourceAuthorityRank());
    }
    @Test
    void recordSource_trustedToolRankIs3() {
        assertEquals(3, new RecordSource("id","n", SourceTrustLabel.TRUSTED_TOOL, false).sourceAuthorityRank());
    }
    @Test
    void recordSource_agentInferredRankIs2() {
        assertEquals(2, new RecordSource("id","n", SourceTrustLabel.AGENT_INFERRED, false).sourceAuthorityRank());
    }
    @Test
    void recordSource_userGeneratedRankIs1() {
        assertEquals(1, new RecordSource("id","n", SourceTrustLabel.USER_GENERATED, false).sourceAuthorityRank());
    }
    @Test
    void recordSource_unverifiedRankIs0() {
        assertEquals(0, new RecordSource("id","n", SourceTrustLabel.UNVERIFIED, false).sourceAuthorityRank());
    }

    // --- V-02 Ch.12: SingleStore uses recency (most recent wins) ---

    @Test
    void singleStore_moreRecentWins() {
        var older = rec("e","a","old", RecordSource.trusted("s1","s1"), 0.8, Instant.now().minusSeconds(100));
        var newer = rec("e","a","new", RecordSource.trusted("s1","s1"), 0.7, Instant.now());
        var res = singleStore.resolve(List.of(older, newer));
        assertEquals("new", res.canonicalValue().value());
        assertEquals(ResolutionPolicy.RECENCY, res.policyApplied());
    }

    // --- V-02 Ch.13: MultiAgent uses temporal precedence (earliest wins) ---

    @Test
    void multiAgent_earliestWins() {
        var early = rec("e","a","early", new RecordSource("s1","s1", SourceTrustLabel.AGENT_INFERRED, false), 0.8, Instant.now().minusSeconds(200));
        var late  = rec("e","a","late",  new RecordSource("s2","s2", SourceTrustLabel.AGENT_INFERRED, false), 0.9, Instant.now());
        var res = multiAgent.resolve(List.of(early, late));
        assertEquals("early", res.canonicalValue().value());
    }

    @Test
    void multiAgent_oppositeToSingleStore_sameData() {
        var early = rec("e","a","early", new RecordSource("s1","s1", SourceTrustLabel.AGENT_INFERRED, false), 0.8, Instant.now().minusSeconds(100));
        var late  = rec("e","a","late",  new RecordSource("s2","s2", SourceTrustLabel.AGENT_INFERRED, false), 0.8, Instant.now());
        assertEquals("late",  singleStore.resolve(List.of(early, late)).canonicalValue().value());
        assertEquals("early", multiAgent.resolve(List.of(early, late)).canonicalValue().value());
    }

    // --- V-15: Source authority beats recency in Ch.12 ---

    @Test
    void singleStore_authorityBeatsRecency() {
        var auth   = rec("e","a","auth",   RecordSource.authoritative("v","v"), 0.7, Instant.now().minusSeconds(300));
        var recent = rec("e","a","recent", RecordSource.trusted("t","t"),       0.9, Instant.now());
        var res = singleStore.resolve(List.of(auth, recent));
        assertEquals("auth", res.canonicalValue().value());
        assertEquals(ResolutionPolicy.SOURCE_AUTHORITY, res.policyApplied());
    }

    // --- V-05: CONSENSUS policy exists and fires ---

    @Test
    void resolutionPolicy_enumContainsConsensus() {
        assertTrue(EnumSet.allOf(ResolutionPolicy.class).contains(ResolutionPolicy.CONSENSUS));
    }

    @Test
    void multiAgent_consensusPicksMajority() {
        var a1 = rec("e","status","active", new RecordSource("a1","A1", SourceTrustLabel.AGENT_INFERRED, false), 0.6, Instant.now().minusSeconds(30));
        var a2 = rec("e","status","active", new RecordSource("a2","A2", SourceTrustLabel.AGENT_INFERRED, false), 0.7, Instant.now().minusSeconds(20));
        var a3 = rec("e","status","inactive", new RecordSource("a3","A3", SourceTrustLabel.AGENT_INFERRED, false), 0.9, Instant.now().minusSeconds(10));
        var res = multiAgent.resolve(List.of(a1, a2, a3));
        assertEquals("active", res.canonicalValue().value());
        assertEquals(ResolutionPolicy.CONSENSUS, res.policyApplied());
    }

    @Test
    void multiAgent_consensusBoostsConfidence() {
        var a1 = rec("e","attr","X", new RecordSource("a1","A1", SourceTrustLabel.AGENT_INFERRED, false), 0.5, Instant.now().minusSeconds(10));
        var a2 = rec("e","attr","X", new RecordSource("a2","A2", SourceTrustLabel.AGENT_INFERRED, false), 0.5, Instant.now().minusSeconds(5));
        var res = multiAgent.resolve(List.of(a1, a2));
        assertEquals(ResolutionPolicy.CONSENSUS, res.policyApplied());
        assertTrue(res.canonicalValue().confidence() > 0.5);
    }

    // --- V-07: Arbitration audit ---

    @Test
    void arbitration_emitsAuditOnManualResolution() {
        var bus = new ObservabilityBus();
        var queue = new InMemoryArbitrationQueue(bus);
        var arbitrator = AgentIdentity.of("human", "admin");
        var resolution = new ConflictResolution("e","a",
            new CanonicalValue("v1", ValueProvenance.AUTHORITATIVE_SOURCE, 0.9),
            ResolutionPolicy.HUMAN_ARBITRATION, List.of(), Instant.now());
        queue.resolveManually("e::a", resolution, arbitrator);
        assertFalse(bus.auditLog().isEmpty());
        assertEquals("HUMAN_ARBITRATION_RESOLUTION", bus.auditLog().get(0).eventType());
        assertTrue(bus.auditLog().get(0).sensitive());
    }

    @Test
    void arbitration_emitsSupersessionEventOnOverwrite() {
        var bus = new ObservabilityBus();
        var queue = new InMemoryArbitrationQueue(bus);
        var arbitrator = AgentIdentity.of("human", "admin");
        var res = new ConflictResolution("e","a",
            new CanonicalValue("v", ValueProvenance.AUTHORITATIVE_SOURCE, 0.9),
            ResolutionPolicy.HUMAN_ARBITRATION, List.of(), Instant.now());
        queue.resolveManually("e::a", res, arbitrator);
        queue.resolveManually("e::a", res, arbitrator); // second call = supersession
        long superEvents = bus.auditLog().stream()
            .filter(e -> e.eventType().equals("HUMAN_ARBITRATION_SUPERSEDED")).count();
        assertEquals(1, superEvents);
    }

    // --- HierarchicalConflictResolver backward compat ---

    @Test
    void hierarchicalResolver_delegatesToSingleStore() {
        var resolver = new HierarchicalConflictResolver(arbQueue);
        var r1 = rec("e","a","v1", RecordSource.authoritative("src","s"), 0.9, Instant.now().minusSeconds(10));
        var r2 = rec("e","a","v2", RecordSource.trusted("src2","s2"), 0.5, Instant.now());
        assertEquals("v1", resolver.resolve(List.of(r1, r2)).canonicalValue().value());
    }

    // --- Guard: empty list throws ---

    @Test
    void singleStore_throwsOnEmptyList() {
        assertThrows(IllegalArgumentException.class, () -> singleStore.resolve(List.of()));
    }
    @Test
    void multiAgent_throwsOnEmptyList() {
        assertThrows(IllegalArgumentException.class, () -> multiAgent.resolve(List.of()));
    }

    // Helper
    private ConflictingRecord rec(String entity, String attr, String value, RecordSource source, double conf, Instant timestamp) {
        return new ConflictingRecord(MemoryRecordId.generate(), entity, attr, value, source, conf, timestamp, AgentIdentity.of("agent", source.sourceId()));
    }
}
