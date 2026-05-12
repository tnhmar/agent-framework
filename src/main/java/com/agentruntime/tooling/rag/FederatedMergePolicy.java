package com.agentruntime.tooling.rag;
import com.agentruntime.core.valueobjects.FederatedRetrievalResult;
import com.agentruntime.core.valueobjects.RetrievalHit;
import java.util.List;
public interface FederatedMergePolicy { List<RetrievalHit> merge(List<FederatedRetrievalResult> sourceResults); }
