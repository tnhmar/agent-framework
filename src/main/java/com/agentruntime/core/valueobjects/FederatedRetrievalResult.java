package com.agentruntime.core.valueobjects;
import com.agentruntime.core.enums.SourceTrustLabel;
import java.time.Duration;
import java.util.List;
public record FederatedRetrievalResult(SourceDescriptor source, List<RetrievalHit> hits, SourceTrustLabel trustLabel, Duration latency) {}
