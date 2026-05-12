package com.agentruntime.core.valueobjects;
import java.util.Optional;
public record WriteResult(boolean success, int newVersion, Optional<String> conflictReason) {
    public static WriteResult success(int newVersion) { return new WriteResult(true, newVersion, Optional.empty()); }
    public static WriteResult conflict(int currentVersion, String reason) { return new WriteResult(false, currentVersion, Optional.of(reason)); }
}
