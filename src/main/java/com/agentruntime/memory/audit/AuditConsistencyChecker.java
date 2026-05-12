package com.agentruntime.memory.audit;

import com.agentruntime.core.enums.ConflictSeverity;
import com.agentruntime.core.valueobjects.*;
import com.agentruntime.memory.knowledgegraph.KnowledgeGraph;
import com.agentruntime.memory.knowledgegraph.KgNode;
import com.agentruntime.memory.semantic.SemanticStore;
import com.agentruntime.memory.shared.ConflictResolver;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * V-06 fix: implements cross-store consistency check per Vol1 Ch.12 §"Cross-Store Consistency Check".
 * Compares semantic facts with knowledge graph attributes for the same entity.
 * Detects and routes divergences to the ConflictResolver.
 */
public class AuditConsistencyChecker {

    public record AuditReport(int checkedEntities, int inconsistencies, List<String> issues) {}

    private final ConflictResolver conflictResolver;

    public AuditConsistencyChecker(ConflictResolver conflictResolver) {
        this.conflictResolver = conflictResolver;
    }

    /**
     * Performs daily cross-store entity-attribute consistency check.
     * @param semanticStore the semantic vector store
     * @param knowledgeGraph the knowledge graph
     * @return audit report with conflicts detected and routed
     */
    public AuditReport check(SemanticStore semanticStore, KnowledgeGraph knowledgeGraph) {
        var issues = new ArrayList<String>();
        int checked = 0;

        for (KgNode node : knowledgeGraph.allNodes()) {
            checked++;
            String label = node.label().toLowerCase();

            // Look up the same concept in the semantic store
            var semanticEntry = semanticStore.lookup(label);
            if (semanticEntry.isEmpty()) continue;

            var semantic = semanticEntry.get();
            // Compare KG properties with semantic attributes
            for (var kgProp : node.properties().entrySet()) {
                String semanticAttrValue = semantic.attributes().get(kgProp.getKey());
                if (semanticAttrValue != null && !semanticAttrValue.equals(kgProp.getValue())) {
                    // Divergence detected — build conflicting records and route to resolver
                    issues.add("Divergence for entity '" + label + "' attribute '" + kgProp.getKey()
                        + "': KG=" + kgProp.getValue() + " vs Semantic=" + semanticAttrValue);

                    var kgRecord = new ConflictingRecord(
                        MemoryRecordId.generate(), label, kgProp.getKey(), kgProp.getValue(),
                        new RecordSource("knowledge-graph", "KnowledgeGraph",
                            com.agentruntime.core.enums.SourceTrustLabel.AGENT_INFERRED, false),
                        0.7, Instant.now(),
                        AgentIdentity.of("audit-system", "orchestrator")
                    );
                    var semRecord = new ConflictingRecord(
                        MemoryRecordId.generate(), label, kgProp.getKey(), semanticAttrValue,
                        new RecordSource("semantic-store", "SemanticStore",
                            com.agentruntime.core.enums.SourceTrustLabel.AGENT_INFERRED, false),
                        0.7, Instant.now(),
                        AgentIdentity.of("audit-system", "orchestrator")
                    );
                    // Route divergence to conflict resolver
                    conflictResolver.resolve(List.of(kgRecord, semRecord));
                }
            }
        }
        return new AuditReport(checked, issues.size(), issues);
    }
}
