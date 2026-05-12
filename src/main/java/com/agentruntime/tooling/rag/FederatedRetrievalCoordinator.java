package com.agentruntime.tooling.rag;
import com.agentruntime.core.valueobjects.*;
public interface FederatedRetrievalCoordinator { FederatedRetrievalResult retrieve(FederatedRetrievalRequest request, ExecutionContext ctx); }
