package com.agentruntime.tooling.rag;

import com.agentruntime.memory.episodic.EpisodicStore;
import com.agentruntime.memory.semantic.SemanticStore;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry that maps source endpoint identifiers to live memory stores.
 *
 * Used by DefaultFederatedRetrievalCoordinator to route retrieval requests
 * to the correct backing store instead of returning simulated data.
 *
 * Register stores at startup:
 *   registry.registerEpisodic("episodic-store", myEpisodicStore);
 *   registry.registerSemantic("semantic-store", mySemanticStore);
 */
public class MemoryStoreRegistry {

    private final ConcurrentHashMap<String, EpisodicStore> episodic = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, SemanticStore> semantic  = new ConcurrentHashMap<>();

    public void registerEpisodic(String sourceId, EpisodicStore store) {
        Objects.requireNonNull(sourceId);
        Objects.requireNonNull(store);
        episodic.put(sourceId, store);
    }

    public void registerSemantic(String sourceId, SemanticStore store) {
        Objects.requireNonNull(sourceId);
        Objects.requireNonNull(store);
        semantic.put(sourceId, store);
    }

    public Optional<EpisodicStore> episodic(String sourceId) {
        return Optional.ofNullable(episodic.get(sourceId));
    }

    public Optional<SemanticStore> semantic(String sourceId) {
        return Optional.ofNullable(semantic.get(sourceId));
    }

    /** True if at least one store is registered (coordinator is live). */
    public boolean hasStores() {
        return !episodic.isEmpty() || !semantic.isEmpty();
    }
}
