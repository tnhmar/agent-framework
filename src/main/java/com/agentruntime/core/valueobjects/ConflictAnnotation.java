package com.agentruntime.core.valueobjects;
import com.agentruntime.core.enums.ConflictSeverity;
import java.util.List;
public record ConflictAnnotation(String entityKey, List<ValueConflict> conflictingValues, ConflictSeverity severity) {}
