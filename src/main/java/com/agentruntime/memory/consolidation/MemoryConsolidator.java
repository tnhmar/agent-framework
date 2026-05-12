package com.agentruntime.memory.consolidation;

import com.agentruntime.memory.episodic.EpisodicStore;
import com.agentruntime.memory.semantic.SemanticStore;

import java.util.Objects;

/**
 * Memory consolidation orchestrator — applies one of the three Vol.1 Ch.12 strategies.
 *
 * Strategy selection:
 *   ROLLING_SUMMARY        — short-horizon agents, conversational memory, low LLM budget
 *   EXTRACTION_MERGE       — user modelling, structured fact accumulation
 *   REFLECTION_SYNTHESIS   — long-horizon agents, behavioural pattern recognition
 *
 * Vol.1 §"The Three Strategies": all strategies are non-destructive;
 * episodes remain in the episodic store and are promoted to semantic memory.
 */
public class MemoryConsolidator {

    public enum Strategy { ROLLING_SUMMARY, EXTRACTION_MERGE, REFLECTION_SYNTHESIS }

    private final EpisodicStore  episodic;
    private final SemanticStore  semantic;
    private final ConsolidationStrategy rolling;
    private final ConsolidationStrategy extraction;
    private final ConsolidationStrategy reflection;

    public MemoryConsolidator(EpisodicStore episodic, SemanticStore semantic) {
        this.episodic   = Objects.requireNonNull(episodic, "episodic store must not be null");
        this.semantic   = Objects.requireNonNull(semantic, "semantic store must not be null");
        this.rolling    = new RollingWindowConsolidation();
        this.extraction = new ExtractionMergeConsolidation();
        this.reflection = new ReflectionSynthesisConsolidation();
    }

    /**
     * Consolidate using the specified strategy.
     * @return count of records written to semantic store
     */
    public int consolidate(String agentId, int batchSize, Strategy strategy) {
        Objects.requireNonNull(agentId, "agentId must not be null");
        Objects.requireNonNull(strategy, "strategy must not be null");
        return switch (strategy) {
            case ROLLING_SUMMARY      -> rolling.consolidate(agentId, episodic, semantic, batchSize);
            case EXTRACTION_MERGE     -> extraction.consolidate(agentId, episodic, semantic, batchSize);
            case REFLECTION_SYNTHESIS -> reflection.consolidate(agentId, episodic, semantic, batchSize);
        };
    }

    /** Default: extraction-merge (most generally useful per Vol.1). */
    public int consolidate(String agentId, int batchSize) {
        return consolidate(agentId, batchSize, Strategy.EXTRACTION_MERGE);
    }
}
