package com.agentruntime.memory.procedural;
import com.agentruntime.core.valueobjects.MemoryRecordId;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
public class ProceduralMemoryStore {
    public record Procedure(MemoryRecordId id, String name, List<String> steps, double successRate) {}
    private final Map<String, Procedure> procedures = new ConcurrentHashMap<>();
    public MemoryRecordId storeProcedure(String name, List<String> steps) {
        var id = MemoryRecordId.generate();
        procedures.put(name, new Procedure(id, name, steps, 1.0));
        return id;
    }
    public Optional<Procedure> recall(String name) { return Optional.ofNullable(procedures.get(name)); }
    public void updateSuccessRate(String name, double rate) {
        var p = procedures.get(name);
        if (p != null) procedures.put(name, new Procedure(p.id(), p.name(), p.steps(), rate));
    }
}
