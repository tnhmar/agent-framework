package com.agentruntime.memory.consolidation;

import com.agentruntime.memory.episodic.EpisodicStore;
import com.agentruntime.memory.semantic.SemanticStore;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Rolling summary consolidation strategy.
 *
 * Vol.1 Ch.12 §"Rolling Summary":
 * "Append new episodes to a growing summary; compress when buffer exceeds a
 *  token threshold. Best for short-horizon agents; conversational memory."
 *
 * Implementation uses recency-sorted batch → concatenated summary string.
 * In production replace the string join with a real LLM summarisation call.
 */
public class RollingWindowConsolidation implements ConsolidationStrategy {

    private final int maxTokenBudget;

    public RollingWindowConsolidation(int maxTokenBudget) {
        this.maxTokenBudget = maxTokenBudget;
    }

    public RollingWindowConsolidation() { this(800); }

    @Override
    public String strategyName() { return "rolling-summary"; }

    @Override
    public int consolidate(String agentId, EpisodicStore episodic,
                            SemanticStore semantic, int batchSize) {
        List<EpisodicStore.Episode> episodes = episodic.recallByAgent(agentId).stream()
                .sorted(Comparator.comparing(EpisodicStore.Episode::timestamp).reversed())
                .limit(batchSize)
                .toList();

        if (episodes.isEmpty()) return 0;

        StringBuilder buffer = new StringBuilder();
        for (EpisodicStore.Episode ep : episodes) {
            buffer.append(ep.summary()).append('\n');
            // Approximate token check: truncate if budget exceeded
            if (buffer.length() / 4 > maxTokenBudget) break;
        }

        String rollingKey  = "rolling-summary:" + agentId;
        String rollingText = buffer.toString().trim();
        semantic.store(rollingKey, rollingText, Map.<String,Object>of("strategy", "rolling-summary", "agentId", agentId));
        return 1;
    }
}
