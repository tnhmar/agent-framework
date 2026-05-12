package com.agentruntime.protocols.gateway;
import com.agentruntime.core.enums.ProtocolType;
import com.agentruntime.core.valueobjects.ExecutionContext;
import java.util.*;
public class DefaultProtocolGateway implements ProtocolGateway {
    @Override
    public Map<String, Object> dispatch(ProtocolType protocol, String target, Map<String, Object> payload, ExecutionContext ctx) {
        var response = new HashMap<String, Object>(payload);
        response.put("protocol", protocol.name()); response.put("target", target);
        response.put("status", "dispatched"); response.put("executionId", ctx.executionId());
        return response;
    }
}
