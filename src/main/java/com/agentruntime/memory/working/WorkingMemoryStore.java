package com.agentruntime.memory.working;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
public class WorkingMemoryStore {
    private final Map<String, Object> store = new ConcurrentHashMap<>();
    public void put(String key, Object value) { store.put(key, value); }
    public Optional<Object> get(String key) { return Optional.ofNullable(store.get(key)); }
    public void remove(String key) { store.remove(key); }
    public void clear() { store.clear(); }
    public Map<String, Object> snapshot() { return Collections.unmodifiableMap(store); }
    public int size() { return store.size(); }
}
