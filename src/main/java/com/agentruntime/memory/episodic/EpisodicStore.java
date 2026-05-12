package com.agentruntime.memory.episodic;

import com.agentruntime.core.valueobjects.MemoryRecordId;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Episodic memory store — one of the 5 Vol.1 Ch.10 memory types.
 * Thread-safe: uses CopyOnWriteArrayList so concurrent reads never block writes.
 */
public class EpisodicStore {

    public record Episode(
            MemoryRecordId id,
            String agentId,
            String summary,
            Instant timestamp,
            Map<String, Object> context) {

        public Episode {
            Objects.requireNonNull(id, "id must not be null");
            Objects.requireNonNull(agentId, "agentId must not be null");
            Objects.requireNonNull(summary, "summary must not be null");
            context = context != null ? Map.copyOf(context) : Map.of();
        }
    }

    private final List<Episode> episodes = new CopyOnWriteArrayList<>();

    public MemoryRecordId store(String agentId, String summary, Map<String, Object> context) {
        Objects.requireNonNull(agentId, "agentId must not be null");
        Objects.requireNonNull(summary, "summary must not be null");
        var id = MemoryRecordId.generate();
        episodes.add(new Episode(id, agentId, summary, Instant.now(), context));
        return id;
    }

    public List<Episode> recallByAgent(String agentId) {
        Objects.requireNonNull(agentId);
        return episodes.stream().filter(e -> e.agentId().equals(agentId)).toList();
    }

    public List<Episode> recallRecent(int n) {
        if (n <= 0) return List.of();
        int size = episodes.size();
        return List.copyOf(episodes.subList(Math.max(0, size - n), size));
    }

    public int count() { return episodes.size(); }
}
