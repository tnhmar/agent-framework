package com.agentruntime;

import com.agentruntime.core.valueobjects.*;
import com.agentruntime.memory.audit.AuditConsistencyChecker;
import com.agentruntime.memory.knowledgegraph.*;
import com.agentruntime.memory.semantic.SemanticStore;
import com.agentruntime.memory.shared.SingleStoreConflictResolver;
import com.agentruntime.memory.shared.InMemoryArbitrationQueue;
import com.agentruntime.observability.ObservabilityBus;
import org.junit.jupiter.api.*;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

/** Tests for V-06: cross-store consistency checker. */
class AuditConsistencyCheckerTest {

    private AuditConsistencyChecker checker;
    private SemanticStore semantic;
    private KnowledgeGraph kg;

    @BeforeEach
    void setUp() {
        var arbQueue = new InMemoryArbitrationQueue(new ObservabilityBus());
        var resolver = new SingleStoreConflictResolver(arbQueue);
        checker = new AuditConsistencyChecker(resolver);
        semantic = new SemanticStore();
        kg = new KnowledgeGraph();
    }

    @Test
    void check_emptyStores_noIssues() {
        var report = checker.check(semantic, kg);
        assertEquals(0, report.checkedEntities());
        assertEquals(0, report.inconsistencies());
        assertTrue(report.issues().isEmpty());
    }

    @Test
    void check_consistentEntries_noIssues() {
        // Both stores agree on "status=active"
        var nodeId = MemoryRecordId.generate();
        kg.addNode(new KgNode(nodeId, "portfolio-a", Map.of("status", "active")));
        semantic.store("portfolio-a", "Portfolio A definition", Map.of("status", "active"));
        var report = checker.check(semantic, kg);
        assertEquals(1, report.checkedEntities());
        assertEquals(0, report.inconsistencies());
    }

    @Test
    void check_inconsistentEntries_detectsDivergence() {
        // KG says "active", semantic says "inactive"
        var nodeId = MemoryRecordId.generate();
        kg.addNode(new KgNode(nodeId, "portfolio-b", Map.of("status", "active")));
        semantic.store("portfolio-b", "Portfolio B", Map.of("status", "inactive"));
        var report = checker.check(semantic, kg);
        assertEquals(1, report.checkedEntities());
        assertEquals(1, report.inconsistencies());
        assertFalse(report.issues().isEmpty());
        assertTrue(report.issues().get(0).contains("portfolio-b"));
        assertTrue(report.issues().get(0).contains("status"));
    }

    @Test
    void check_multipleEntities_detectsAllInconsistencies() {
        kg.addNode(new KgNode(MemoryRecordId.generate(), "entity-x", Map.of("a", "1", "b", "2")));
        kg.addNode(new KgNode(MemoryRecordId.generate(), "entity-y", Map.of("c", "3")));
        semantic.store("entity-x", "X", Map.of("a", "1", "b", "WRONG"));
        semantic.store("entity-y", "Y", Map.of("c", "ALSO_WRONG"));
        var report = checker.check(semantic, kg);
        assertEquals(2, report.checkedEntities());
        assertEquals(2, report.inconsistencies());
    }

    @Test
    void check_missingInSemantic_noFalsePositive() {
        // KG has entity, semantic doesn't — should not throw or report false inconsistency
        kg.addNode(new KgNode(MemoryRecordId.generate(), "orphan-entity", Map.of("x", "y")));
        var report = checker.check(semantic, kg);
        assertEquals(1, report.checkedEntities());
        assertEquals(0, report.inconsistencies());
    }

    @Test
    void check_issueMessageContainsBothValues() {
        kg.addNode(new KgNode(MemoryRecordId.generate(), "asset-z", Map.of("price", "100")));
        semantic.store("asset-z", "Asset Z", Map.of("price", "200"));
        var report = checker.check(semantic, kg);
        String issue = report.issues().get(0);
        assertTrue(issue.contains("100"));
        assertTrue(issue.contains("200"));
    }
}
