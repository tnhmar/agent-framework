package com.agentruntime.hitl;
import java.util.Optional;
public interface HitlGateway {
    void submit(HitlRequest request);
    Optional<HitlResponse> poll(String requestId);
    boolean isResolved(String requestId);
}
