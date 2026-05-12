package com.agentruntime.core.valueobjects;
import com.agentruntime.core.enums.ValueProvenance;
public record CanonicalValue(String value, ValueProvenance provenance, double confidence) {}
