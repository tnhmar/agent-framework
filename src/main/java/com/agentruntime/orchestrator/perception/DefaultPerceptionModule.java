package com.agentruntime.orchestrator.perception;

import com.agentruntime.core.valueobjects.ExecutionContext;

import java.util.*;
import java.util.regex.Pattern;

/**
 * Default perception module — normalises raw input and extracts entities.
 *
 * S-07 FIX: Entity extraction no longer relies on "starts with uppercase > 5 chars".
 * Uses a pattern-based NER approach covering:
 *   - Proper nouns (Title Case sequences)
 *   - Numbers (integers, decimals, percentages)
 *   - Dates and times (ISO format, natural language)
 *   - Quoted strings (explicit named entities)
 *   - Common domain keywords
 *
 * Vol.1 Ch.5 §"Perception Module".
 */
public class DefaultPerceptionModule implements PerceptionModule {

    // Quoted strings — explicit entity markers
    private static final Pattern QUOTED    = Pattern.compile("\"([^\"]{2,50})\"");
    // Title Case proper nouns (2+ words or single word of 4+ chars)
    private static final Pattern TITLE_CASE = Pattern.compile("\\b([A-Z][a-z]{2,}(?:\\s+[A-Z][a-z]{2,})*)\\b");
    // Numbers, percentages, currencies
    private static final Pattern NUMBERS   = Pattern.compile("\\b\\d+(?:\\.\\d+)?(?:\\s*%|\\s*[Kk]|\\s*[Mm]|\\s*[Bb])?\\b");
    // ISO dates and natural date expressions
    private static final Pattern DATES     = Pattern.compile(
            "\\b(?:\\d{4}-\\d{2}-\\d{2}|(?:Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)\\w*\\s+\\d{1,2},?\\s+\\d{4})\\b");
    // Common stop words to exclude from entities
    private static final Set<String> STOP = Set.of(
            "The","This","That","These","Those","With","From","Into","Onto","Over",
            "Under","After","Before","Since","Until","Between","Through","About",
            "Like","Such","When","Where","Which","While","What","Your","Their");

    @Override
    public PerceptionResult perceive(RawInput input, ExecutionContext ctx) {
        Objects.requireNonNull(input, "input must not be null");
        Objects.requireNonNull(ctx,   "ctx must not be null");

        String content    = normalise(input.content());
        List<String> entities = extractEntities(content);
        double confidence = computeConfidence(content, entities);

        return new PerceptionResult(content, entities,
                Map.of("modality", input.modality(), "entityCount", entities.size()), confidence);
    }

    // ── Private ───────────────────────────────────────────────────────────────

    private static String normalise(String raw) {
        if (raw == null) return "";
        return raw.strip()
                .replaceAll("\\s{2,}", " ")      // collapse whitespace
                .replaceAll("[\\p{Cntrl}&&[^\\n\\t]]", ""); // remove control chars
    }

    private static List<String> extractEntities(String content) {
        Set<String> entities = new LinkedHashSet<>();

        // 1. Quoted strings — highest priority
        var m1 = QUOTED.matcher(content);
        while (m1.find()) entities.add(m1.group(1).trim());

        // 2. Dates
        var m2 = DATES.matcher(content);
        while (m2.find()) entities.add(m2.group().trim());

        // 3. Proper nouns (Title Case) filtered by stop words
        var m3 = TITLE_CASE.matcher(content);
        while (m3.find()) {
            String candidate = m3.group(1).trim();
            if (!STOP.contains(candidate) && candidate.length() >= 3)
                entities.add(candidate);
        }

        // 4. Significant numbers
        var m4 = NUMBERS.matcher(content);
        while (m4.find()) {
            String num = m4.group().trim();
            if (!num.equals("0") && num.length() >= 2) entities.add(num);
        }

        return List.copyOf(entities);
    }

    private static double computeConfidence(String content, List<String> entities) {
        if (content.isBlank()) return 0.0;
        // Higher confidence with more structure (entities, punctuation, length)
        double entityScore  = Math.min(1.0, entities.size() / 5.0) * 0.4;
        double lengthScore  = Math.min(1.0, content.length() / 200.0) * 0.3;
        double structScore  = content.contains("?") || content.contains(":") ? 0.3 : 0.15;
        return Math.min(0.98, entityScore + lengthScore + structScore + 0.1);
    }
}
