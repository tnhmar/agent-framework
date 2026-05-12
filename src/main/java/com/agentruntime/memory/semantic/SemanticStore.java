package com.agentruntime.memory.semantic;
import com.agentruntime.core.valueobjects.MemoryRecordId;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
public class SemanticStore {
    public record SemanticEntry(MemoryRecordId id, String concept, String definition, Map<String, String> attributes) {}
    private final Map<String, SemanticEntry> store = new ConcurrentHashMap<>();
    public MemoryRecordId store(String concept, String definition, Map<String, String> attributes) {
        var id = MemoryRecordId.generate();
        store.put(concept.toLowerCase(), new SemanticEntry(id, concept, definition, attributes));
        return id;
    }
    public Optional<SemanticEntry> lookup(String concept) { return Optional.ofNullable(store.get(concept.toLowerCase())); }
    public List<SemanticEntry> search(String keyword) {
        return store.values().stream().filter(e -> e.concept().toLowerCase().contains(keyword.toLowerCase()) || e.definition().toLowerCase().contains(keyword.toLowerCase())).toList();
    }
    public Collection<SemanticEntry> all() { return Collections.unmodifiableCollection(store.values()); }
}
