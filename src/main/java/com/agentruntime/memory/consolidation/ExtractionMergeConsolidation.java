package com.agentruntime.memory.consolidation;

import com.agentruntime.memory.episodic.EpisodicStore;
import com.agentruntime.memory.semantic.SemanticStore;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Extraction-merge consolidation strategy.
 *
 * Vol.1 Ch.12 §"Extraction-Merge":
 * "LLM extracts discrete facts from episodes; each fact is merged into the
 *  semantic store independently. Best for user modelling; preference extraction;
 *  structured fact accumulation."
 *
 * Facts are stored individually with stable concept keys for independent
 * retrieval, update, and GDPR erasure.
 */
public class ExtractionMergeConsolidation implements ConsolidationStrategy {

    @Override
    public String strategyName() { return "extraction-merge"; }

    @Override
    public int consolidate(String agentId, EpisodicStore episodic,
                            SemanticStore semantic, int batchSize) {
        List<EpisodicStore.Episode> episodes = episodic.recallByAgent(agentId).stream()
                .sorted(Comparator.comparing(EpisodicStore.Episode::timestamp).reversed())
                .filter(ep -> ep.summary() != null && ep.summary().length() > 10)
                .limit(batchSize)
                .toList();

        int written = 0;
        for (EpisodicStore.Episode ep : episodes) {
            // Stable, idempotent concept key per episode
            String conceptKey = "fact:" + agentId + ":" + ep.id().value().substring(0, 8);
            semantic.store(conceptKey, ep.summary(),
                    Map.<String,Object>of("strategy", "extraction-merge", "agentId", agentId, "episodeId", ep.id().value(), "source", "episodic"));
            written++;
        }
        return written;
    }
}
