package com.agentruntime.memory.semantic;

import com.agentruntime.core.valueobjects.MemoryRecordId;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Semantic memory store — structured, queryable concept-definition pairs.
 * Vol.1 Ch.10: "Semantic memory stores general world knowledge."
 *
 * P1-05: null guards on all mutation methods.
 * G-16: contradiction detection — when a concept is overwritten with a different
 *        definition, a ConceptConflict record is created (never silently lost).
 */
public class SemanticStore {

    public record SemanticEntry(
            MemoryRecordId      id,
            String              concept,
            String              definition,
            Map<String, Object> attributes,
            Instant             createdAt,
            Instant             updatedAt,
            int                 version
    ) {
        public SemanticEntry {
            Objects.requireNonNull(concept,    "concept must not be null");
            Objects.requireNonNull(definition, "definition must not be null");
            attributes = attributes != null ? Map.copyOf(attributes) : Map.of();
        }
    }

    /** Records a contradiction detected during overwrite. */
    public record ConceptConflict(
            String  concept,
            String  previousDefinition,
            String  newDefinition,
            Instant detectedAt
    ) {}

    private final ConcurrentHashMap<String, SemanticEntry>  store     = new ConcurrentHashMap<>();
    private final CopyOnWriteArrayList<ConceptConflict>     conflicts = new CopyOnWriteArrayList<>();

    /** Store or overwrite a concept. Contradiction is recorded when definition changes. */
    public MemoryRecordId store(String concept, String definition, Map<String, Object> attributes) {
        Objects.requireNonNull(concept,    "concept must not be null");
        Objects.requireNonNull(definition, "definition must not be null");

        String key = concept.toLowerCase();
        Instant now = Instant.now();
        final MemoryRecordId[] id = { null };

        store.compute(key, (k, existing) -> {
            if (existing != null && !existing.definition().equals(definition)) {
                // G-16: detect and record contradiction
                conflicts.add(new ConceptConflict(concept, existing.definition(), definition, now));
            }
            int version = existing != null ? existing.version() + 1 : 1;
            Instant created = existing != null ? existing.createdAt() : now;
            MemoryRecordId newId = existing != null ? existing.id() : MemoryRecordId.generate();
            id[0] = newId;
            return new SemanticEntry(newId, concept, definition, attributes, created, now, version);
        });
        return id[0];
    }

    /** Backward-compatible overload for String-valued attributes. */
    public MemoryRecordId store(String concept, String definition, Map<String, String> attributes,
                                boolean stringTyped) {
        Map<String, Object> attrs = new LinkedHashMap<>();
        if (attributes != null) attrs.putAll(attributes);
        return store(concept, definition, attrs);
    }

    public Optional<SemanticEntry> lookup(String concept) {
        Objects.requireNonNull(concept);
        return Optional.ofNullable(store.get(concept.toLowerCase()));
    }

    public List<SemanticEntry> search(String keyword) {
        Objects.requireNonNull(keyword);
        String lower = keyword.toLowerCase();
        return store.values().stream()
                .filter(e -> e.concept().toLowerCase().contains(lower)
                          || e.definition().toLowerCase().contains(lower))
                .toList();
    }

    /** All recorded contradictions (audit trail — never cleared). */
    public List<ConceptConflict> conceptConflicts() {
        return Collections.unmodifiableList(conflicts);
    }

    public boolean hasConflicts()      { return !conflicts.isEmpty(); }
    public int size()                  { return store.size(); }
    public Collection<SemanticEntry> all() { return Collections.unmodifiableCollection(store.values()); }
}
