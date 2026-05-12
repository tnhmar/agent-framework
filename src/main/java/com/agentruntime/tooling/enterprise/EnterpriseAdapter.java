package com.agentruntime.tooling.enterprise;
import com.agentruntime.core.valueobjects.ExecutionContext;
import java.util.Map;
public interface EnterpriseAdapter { Map<String, Object> invoke(String operation, Map<String, Object> input, ExecutionContext ctx); }
