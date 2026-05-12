package com.agentruntime.modelclient;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.*;
import java.util.regex.*;

/**
 * OpenAI chat-completions ModelClient using only java.net.http (no external dependencies).
 * Vol.1 Ch.4: "model proposes, runtime disposes."
 */
public final class OpenAiModelClient implements ModelClient {

    private static final String OPENAI_URL = "https://api.openai.com/v1/chat/completions";

    private final String    apiKey;
    private final String    model;
    private final HttpClient http;

    public OpenAiModelClient(String apiKey, String model) {
        this.apiKey = Objects.requireNonNull(apiKey,  "apiKey must not be null");
        this.model  = Objects.requireNonNull(model,   "model must not be null");
        this.http   = HttpClient.newHttpClient();
    }

    public OpenAiModelClient(String apiKey) { this(apiKey, "gpt-4o"); }

    @Override
    public ModelOutput generate(ModelPrompt prompt, ModelRequestContext context)
            throws ModelClientException {
        try {
            String body = buildRequestJson(prompt);

            HttpRequest request = HttpRequest.newBuilder(URI.create(OPENAI_URL))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type",  "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> response =
                    http.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 400) {
                boolean retryable = response.statusCode() >= 500;
                throw new ModelClientException("OpenAI HTTP " + response.statusCode(), retryable);
            }

            String text = extractContent(response.body());
            return new ModelOutput(text, List.of(), Map.of());

        } catch (ModelClientException e) {
            throw e;
        } catch (Exception e) {
            throw new ModelClientException("Model call failed: " + e.getMessage(), true, e);
        }
    }

    // ── Pure-JDK JSON helpers ─────────────────────────────────────────────────

    private String buildRequestJson(ModelPrompt prompt) {
        StringBuilder sb = new StringBuilder("{\"model\":\"")
                .append(escapeJson(model))
                .append("\",\"messages\":[");
        for (int i = 0; i < prompt.messages().size(); i++) {
            if (i > 0) sb.append(',');
            ModelMessage m = prompt.messages().get(i);
            sb.append("{\"role\":\"").append(m.role().name().toLowerCase())
              .append("\",\"content\":\"").append(escapeJson(m.content())).append("\"}");
        }
        sb.append("]}");
        return sb.toString();
    }

    /** Extract content from OpenAI JSON response using regex (avoids Jackson). */
    private static String extractContent(String json) {
        // Matches: "content":"<value>" allowing escaped characters
        Pattern p = Pattern.compile("\"content\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"");
        Matcher m = p.matcher(json);
        if (m.find()) {
            return m.group(1).replace("\\n", "\n").replace("\\\"", "\"").replace("\\\\", "\\");
        }
        return "";
    }

    private static String escapeJson(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }
}
