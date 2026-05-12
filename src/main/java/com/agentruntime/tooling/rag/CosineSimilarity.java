package com.agentruntime.tooling.rag;

import java.util.*;

/**
 * Drift-01 fix: implements proper TF-vector cosine similarity.
 * Previous implementation was a character-overlap heuristic, not cosine similarity.
 * This computes term-frequency vectors and returns the true cosine similarity.
 */
public class CosineSimilarity implements SemanticSimilarity {

    @Override
    public double similarity(String text1, String text2) {
        if (text1 == null || text2 == null) return 0.0;
        if (text1.equals(text2)) return 1.0;
        if (text1.isBlank() || text2.isBlank()) return 0.0;

        Map<String, Integer> vec1 = termFrequency(text1);
        Map<String, Integer> vec2 = termFrequency(text2);

        double dot = dotProduct(vec1, vec2);
        double mag1 = magnitude(vec1);
        double mag2 = magnitude(vec2);

        if (mag1 == 0.0 || mag2 == 0.0) return 0.0;
        return dot / (mag1 * mag2);
    }

    private Map<String, Integer> termFrequency(String text) {
        Map<String, Integer> freq = new HashMap<>();
        for (String token : text.toLowerCase().split("\\W+")) {
            if (!token.isBlank()) freq.merge(token, 1, Integer::sum);
        }
        return freq;
    }

    private double dotProduct(Map<String, Integer> v1, Map<String, Integer> v2) {
        double dot = 0.0;
        for (Map.Entry<String, Integer> entry : v1.entrySet()) {
            Integer v2val = v2.get(entry.getKey());
            if (v2val != null) dot += (double) entry.getValue() * v2val;
        }
        return dot;
    }

    private double magnitude(Map<String, Integer> vec) {
        double sum = 0.0;
        for (int v : vec.values()) sum += (double) v * v;
        return Math.sqrt(sum);
    }
}
