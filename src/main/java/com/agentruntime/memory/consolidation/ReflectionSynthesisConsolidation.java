package com.agentruntime.memory.consolidation;

import com.agentruntime.memory.episodic.EpisodicStore;
import com.agentruntime.memory.semantic.SemanticStore;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Reflection-synthesis consolidation strategy.
 *
 * Vol.1 Ch.12 §"Reflection-Synthesis":
 * "Agent enters a dedicated reasoning pass over many episodes, generating
 *  higher-level insights about patterns and trends. Best for long-horizon agents;
 *  behavioural pattern recognition; strategic planning."
 *
 * Outputs are labelled source="reflection" and confidence=0.70 (inferred;
 * lower than explicit) per the Vol.1 specification.
 */
public class ReflectionSynthesisConsolidation implements ConsolidationStrategy {

    private static final int MIN_EPISODES_REQUIRED = 10;
    private static final double REFLECTION_CONFIDENCE = 0.70;

    @Override
    public String strategyName() { return "reflection-synthesis"; }

    @Override
    public int consolidate(String agentId, EpisodicStore episodic,
                            SemanticStore semantic, int batchSize) {
        List<EpisodicStore.Episode> episodes = episodic.recallByAgent(agentId).stream()
                .sorted(Comparator.comparing(EpisodicStore.Episode::timestamp).reversed())
                .limit(Math.max(batchSize, MIN_EPISODES_REQUIRED))
                .toList();

        // Vol.1: "if len(recent) < 10: return 0 — insufficient data for meaningful synthesis"
        if (episodes.size() < MIN_EPISODES_REQUIRED) return 0;

        // Synthesize a pattern summary from the episode window
        String patternSummary = synthesise(episodes);
        if (patternSummary.isBlank()) return 0;

        String insightKey = "reflection:" + agentId + ":pattern-" + System.currentTimeMillis();
        semantic.store(insightKey, patternSummary,
                Map.of("strategy",   "reflection-synthesis",
                       "agentId",    agentId,
                       "source",     "reflection",
                       "confidence", String.valueOf(REFLECTION_CONFIDENCE),
                       "episodeCount", String.valueOf(episodes.size())));
        return 1;
    }

    /**
     * Pattern synthesis. In production this would call a ModelClient.
     * The stub identifies recurring themes across episode summaries.
     */
    private String synthesise(List<EpisodicStore.Episode> episodes) {
        Map<String, Long> wordFrequencies = episodes.stream()
                .flatMap(ep -> List.of(ep.summary().toLowerCase().split("\\W+")).stream())
                .filter(w -> w.length() > 4)
                .collect(Collectors.groupingBy(w -> w, Collectors.counting()));

        return wordFrequencies.entrySet().stream()
                .filter(e -> e.getValue() >= 3)
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(5)
                .map(e -> "Recurring theme: '" + e.getKey() + "' (" + e.getValue() + " episodes)")
                .collect(Collectors.joining("; "));
    }
}
