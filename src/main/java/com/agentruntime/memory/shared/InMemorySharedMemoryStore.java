package com.agentruntime.memory.shared;

import com.agentruntime.core.enums.ConsistencyModel;
import com.agentruntime.core.valueobjects.*;
import com.agentruntime.observability.AuditEvent;
import com.agentruntime.observability.ObservabilityBus;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory shared memory store with RBAC, versioning, and audit.
 * Vol.1 Ch.13 fixes: V-03 (RBAC), V-04 (per-namespace consistency), V-09 (version history).
 * No external dependencies — uses Map<String,Object> instead of JsonNode.
 */
public class InMemorySharedMemoryStore implements SharedMemoryStore {

    private static final Set<String> WRITE_ROLES =
            Set.of("orchestrator", "specialist", "coordinator", "admin", "compliance");
    private static final Set<String> READ_ROLES =
            Set.of("orchestrator", "specialist", "coordinator", "observer",
                   "admin", "compliance", "agent");

    private final Map<String, List<VersionedRecord>> versionHistory = new ConcurrentHashMap<>();
    private final Map<String, ConsistencyModel>      namespaceConsistency;
    private final ConsistencyModel                   defaultConsistency;
    private final ObservabilityBus                   observabilityBus;

    public InMemorySharedMemoryStore(Map<String, ConsistencyModel> namespaceConsistency,
                                     ConsistencyModel defaultConsistency,
                                     ObservabilityBus observabilityBus) {
        this.namespaceConsistency = new ConcurrentHashMap<>(namespaceConsistency);
        this.defaultConsistency   = defaultConsistency;
        this.observabilityBus     = observabilityBus;
    }

    public InMemorySharedMemoryStore() {
        this(Map.of(), ConsistencyModel.EVENTUAL, new ObservabilityBus());
    }

    @Override
    public VersionedRecord read(String recordId, AgentIdentity reader) {
        if (!READ_ROLES.contains(reader.role()))
            throw new SecurityException("Role '" + reader.role() + "' not in READ_ROLES");
        var history = versionHistory.get(recordId);
        if (history == null || history.isEmpty()) return null;
        var record = history.get(history.size() - 1);
        if (!hasTagAccess(record, reader))
            throw new SecurityException("Agent " + reader.agentId() + " lacks tag access to " + recordId);
        return record;
    }

    @Override
    public synchronized WriteResult writeWithVersionCheck(String recordId,
            Map<String, Object> content, int expectedVersion, AgentIdentity writer) {
        if (!WRITE_ROLES.contains(writer.role()))
            throw new SecurityException("Role '" + writer.role() + "' not in WRITE_ROLES");

        ConsistencyModel model = resolveConsistencyModel(recordId);
        var history = versionHistory.computeIfAbsent(recordId, k -> new ArrayList<>());
        int currentVersion = history.isEmpty() ? 0 : history.get(history.size() - 1).version();

        if (currentVersion != expectedVersion)
            return WriteResult.conflict(currentVersion,
                    "Version mismatch: expected " + expectedVersion + ", found " + currentVersion);

        int newVersion = currentVersion + 1;
        var record = new VersionedRecord(recordId, content, newVersion,
                Instant.now(), writer, List.of("public"));
        history.add(record);

        observabilityBus.emit(new AuditEvent(
                UUID.randomUUID().toString(), "SHARED_MEMORY_WRITE", writer,
                "writeWithVersionCheck",
                Map.of("recordId", recordId, "newVersion", newVersion,
                       "consistency", model.name()),
                Instant.now(), false));

        return WriteResult.success(newVersion);
    }

    @Override
    public List<VersionedRecord> readBatch(SharedMemoryQuery query, AgentIdentity reader) {
        return query.recordIds().stream()
                .map(id -> read(id, reader))
                .filter(Objects::nonNull)
                .toList();
    }

    @Override
    public Optional<VersionedRecord> getVersion(String recordId, int version) {
        var history = versionHistory.get(recordId);
        if (history == null) return Optional.empty();
        return history.stream().filter(r -> r.version() == version).findFirst();
    }

    @Override
    public synchronized boolean rollback(String recordId, int targetVersion, AgentIdentity requester) {
        if (!WRITE_ROLES.contains(requester.role()))
            throw new SecurityException("Role '" + requester.role() + "' cannot rollback");
        var target = getVersion(recordId, targetVersion);
        if (target.isEmpty()) return false;

        var history = versionHistory.get(recordId);
        int newVersion = history.get(history.size() - 1).version() + 1;
        history.add(new VersionedRecord(recordId, target.get().content(), newVersion,
                Instant.now(), requester, target.get().accessTags()));

        observabilityBus.emit(new AuditEvent(
                UUID.randomUUID().toString(), "SHARED_MEMORY_ROLLBACK", requester, "rollback",
                Map.of("recordId", recordId, "targetVersion", targetVersion, "newVersion", newVersion),
                Instant.now(), true));
        return true;
    }

    private ConsistencyModel resolveConsistencyModel(String recordId) {
        for (var entry : namespaceConsistency.entrySet())
            if (recordId.startsWith(entry.getKey())) return entry.getValue();
        return defaultConsistency;
    }

    private boolean hasTagAccess(VersionedRecord record, AgentIdentity reader) {
        return record.accessTags().contains("public") || record.accessTags().contains(reader.role());
    }
}
