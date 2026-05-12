package com.agentruntime.orchestrator.perception;
import com.agentruntime.core.valueobjects.ExecutionContext;
import java.util.*;
public class DefaultPerceptionModule implements PerceptionModule {
    @Override
    public PerceptionResult perceive(RawInput input, ExecutionContext ctx) {
        String normalized = input.content().trim().toLowerCase();
        List<String> entities = extractEntities(input.content());
        return new PerceptionResult(normalized, entities, Map.copyOf(input.metadata()), 0.95);
    }
    private List<String> extractEntities(String content) {
        var entities = new ArrayList<String>();
        for (String word : content.split("\\s+"))
            if (word.length() > 5 && Character.isUpperCase(word.charAt(0))) entities.add(word);
        return entities;
    }
}
