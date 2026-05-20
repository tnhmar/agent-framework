package com.agentruntime.memory.shared;

import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * Externally-configurable role policy for shared memory access.
 * V-OCP-03 FIX: WRITE_ROLES and READ_ROLES are no longer hardcoded constants
 * in InMemorySharedMemoryStore. They are injected via this class.
 */
public class RolePolicy {

    private final Set<String> writeRoles;
    private final Set<String> readRoles;

    public RolePolicy(Set<String> writeRoles, Set<String> readRoles) {
        Objects.requireNonNull(writeRoles, "writeRoles must not be null");
        Objects.requireNonNull(readRoles,  "readRoles must not be null");
        this.writeRoles = new CopyOnWriteArraySet<>(writeRoles);
        this.readRoles  = new CopyOnWriteArraySet<>(readRoles);
    }

    /** Default role policy matching the original Vol.1 Ch.13 specification. */
    public static RolePolicy defaults() {
        return new RolePolicy(
                Set.of("orchestrator", "specialist", "coordinator", "admin", "compliance"),
                Set.of("orchestrator", "specialist", "coordinator", "observer",
                       "admin", "compliance", "agent")
        );
    }

    public boolean canWrite(String role) { return role != null && writeRoles.contains(role); }
    public boolean canRead(String role)  { return role != null && readRoles.contains(role); }
    public Set<String> writeRoles()      { return Set.copyOf(writeRoles); }
    public Set<String> readRoles()       { return Set.copyOf(readRoles); }
}
