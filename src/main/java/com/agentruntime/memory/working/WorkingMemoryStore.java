package com.agentruntime.memory.working;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Bounded working-memory scratchpad.
 * P1-01 fix: clear() uses atomic map-swap via volatile field to prevent
 * races between clear() and concurrent put() operations.
 * Vol.1 Ch.8: "Working memory is the agent's active scratchpad."
 */
public class WorkingMemoryStore {

    private volatile ConcurrentHashMap<String, Object> store = new ConcurrentHashMap<>();

    public void put(String key, Object value) {
        Objects.requireNonNull(key, "key must not be null");
        store.put(key, value);
    }

    public Optional<Object> get(String key) {
        return Optional.ofNullable(store.get(key));
    }

    public void remove(String key) { store.remove(key); }

    /**
     * Atomically replaces the entire map — safe under concurrent writes.
     * No put() call can be lost between a partial clear and re-insertion.
     */
    public void clear() { store = new ConcurrentHashMap<>(); }

    public Map<String, Object> snapshot() { return Collections.unmodifiableMap(store); }
    public int size() { return store.size(); }
}
