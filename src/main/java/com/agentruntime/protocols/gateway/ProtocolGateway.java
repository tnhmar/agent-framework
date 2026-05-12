package com.agentruntime.protocols.gateway;
import com.agentruntime.core.enums.ProtocolType;
import com.agentruntime.core.valueobjects.ExecutionContext;
import java.util.Map;
public interface ProtocolGateway { Map<String, Object> dispatch(ProtocolType protocol, String target, Map<String, Object> payload, ExecutionContext ctx); }
