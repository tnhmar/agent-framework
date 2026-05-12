package com.agentruntime.core.valueobjects;
import com.agentruntime.core.enums.SourceTrustLabel;
public record SourceDescriptor(String sourceId, String endpoint, SourceTrustLabel trustLabel, String description) {}
