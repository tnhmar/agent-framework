package com.agentruntime.memory.scoring;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Set;

/**
 * Composite importance scorer for memory records.
 *
 * Vol.1 Ch.12 §"The Five Scoring Factors" + §"The Composite Scoring Formula":
 *
 *   I(m) = wr·freshness + wf·frequency + ws·salience + wc·source_weight + wv·task_relevance
 *
 *   Default weights: wr=0.35, wf=0.20, ws=0.20, wc=0.15, wv=0.10
 *
 * Freshness uses exponential decay:  r = e^(-λ·t)  where λ≈0.02 (half-life ≈35 days)
 * Frequency: log-normalised count:   f = log(1+n) / log(101)  (caps at access_count=100)
 * Salience: keyword presence in [0,1]
 * Source reliability: fixed weights per source type
 * Task relevance: binary boost when record matches active task
 */
public class ImportanceScorer {

    private static final double LAMBDA       = 0.02;  // decay constant → half-life ≈35 days
    private static final double LOG_CAP      = Math.log(101);

    // Default weights (Vol.1 §"Composite Scoring Formula")
    private final double wr; // freshness
    private final double wf; // frequency
    private final double ws; // salience
    private final double wc; // source reliability
    private final double wv; // task relevance

    // Salience keywords with their weights (Vol.1 §"Salience")
    private static final Map<String, Double> SALIENCE_KEYWORDS = Map.of(
            "critical",  1.00,
            "deadline",  0.90,
            "error",     0.90,
            "failed",    0.85,
            "corrected", 0.80,
            "decision",  0.75,
            "urgent",    0.75,
            "blocked",   0.70
    );

    // Source reliability weights (Vol.1 §"Source reliability")
    private static final Map<String, Double> SOURCE_WEIGHTS = Map.of(
            "user_explicit",  1.00,
            "tool_verified",  0.90,
            "agent_verified", 0.80,
            "agent_inferred", 0.60,
            "behavioural",    0.50,
            "default",        0.40
    );

    /** Default constructor with Vol.1 recommended weights. */
    public ImportanceScorer() {
        this(0.35, 0.20, 0.20, 0.15, 0.10);
    }

    public ImportanceScorer(double wr, double wf, double ws, double wc, double wv) {
        this.wr = wr; this.wf = wf; this.ws = ws; this.wc = wc; this.wv = wv;
    }

    /**
     * Compute the composite importance score.
     *
     * @param content        the memory record content (for salience keyword scan)
     * @param writtenAt      when the record was written (for freshness decay)
     * @param accessCount    how many times the record has been retrieved
     * @param source         source type key ("user_explicit", "tool_verified", etc.)
     * @param activeTaskTags keywords from the currently active task (for task relevance)
     */
    public ImportanceScore score(String content, Instant writtenAt, int accessCount,
                                  String source, Set<String> activeTaskTags) {
        double freshness     = computeFreshness(writtenAt);
        double frequency     = computeFrequency(accessCount);
        double salience      = computeSalience(content);
        double sourceWeight  = SOURCE_WEIGHTS.getOrDefault(source,
                               SOURCE_WEIGHTS.get("default"));
        double taskRelevance = computeTaskRelevance(content, activeTaskTags);

        double total = wr * freshness
                     + wf * frequency
                     + ws * salience
                     + wc * sourceWeight
                     + wv * taskRelevance;

        return new ImportanceScore(
                Math.min(1.0, total),
                freshness, frequency, salience, sourceWeight, taskRelevance);
    }

    // ── Private factor computations ───────────────────────────────────────────

    /** r = e^(-λ·t)  where t = age in days. */
    private static double computeFreshness(Instant writtenAt) {
        long ageHours = ChronoUnit.HOURS.between(writtenAt, Instant.now());
        double ageDays = ageHours / 24.0;
        return Math.exp(-LAMBDA * ageDays);
    }

    /** f = log(1 + access_count) / log(101) */
    private static double computeFrequency(int accessCount) {
        return Math.log(1 + Math.max(0, accessCount)) / LOG_CAP;
    }

    /** Keyword lookup returning the max weight found (or 0 if none). */
    private static double computeSalience(String content) {
        if (content == null || content.isBlank()) return 0.0;
        String lower = content.toLowerCase();
        return SALIENCE_KEYWORDS.entrySet().stream()
                .filter(e -> lower.contains(e.getKey()))
                .mapToDouble(Map.Entry::getValue)
                .max().orElse(0.0);
    }

    /** Binary boost: 1.0 if any active-task tag found in content, else 0.0. */
    private static double computeTaskRelevance(String content, Set<String> activeTaskTags) {
        if (content == null || activeTaskTags == null || activeTaskTags.isEmpty()) return 0.0;
        String lower = content.toLowerCase();
        return activeTaskTags.stream().anyMatch(tag -> lower.contains(tag.toLowerCase()))
                ? 1.0 : 0.0;
    }
}
