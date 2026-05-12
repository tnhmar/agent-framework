package com.agentruntime.core.valueobjects;
import com.agentruntime.core.enums.SourceTrustLabel;
public record LabeledHit(RetrievalHit hit, SourceTrustLabel trustLabel) {}
