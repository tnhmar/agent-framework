package com.agentruntime.modelclient;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.*;

/**
 * OpenAI chat-completions ModelClient — pure JDK, no external dependencies.
 *
 * P2-02 improvements:
 *   - HttpClient is injectable (shared instance recommended for production)
 *   - Built-in retry: up to 3 attempts with exponential backoff for HTTP 5xx/429
 *   - Empty content raises a non-retryable exception rather than returning silently
 */
public final class OpenAiModelClient implements ModelClient {

    private static final String OPENAI_URL    = "https://api.openai.com/v1/chat/completions";
    private static final int    MAX_RETRIES   = 3;
    private static final long   RETRY_BASE_MS = 200L;

    private final String     apiKey;
    private final String     model;
    private final HttpClient http;

    /** Full constructor — inject a shared HttpClient for production use. */
    public OpenAiModelClient(String apiKey, String model, HttpClient http) {
        this.apiKey = Objects.requireNonNull(apiKey, "apiKey must not be null");
        this.model  = Objects.requireNonNull(model,  "model must not be null");
        this.http   = Objects.requireNonNull(http,   "http must not be null");
    }

    public OpenAiModelClient(String apiKey, String model) {
        this(apiKey, model, HttpClient.newHttpClient());
    }

    public OpenAiModelClient(String apiKey) { this(apiKey, "gpt-4o"); }

    @Override
    public ModelOutput generate(ModelPrompt prompt, ModelRequestContext context)
            throws ModelClientException {

        String body = buildRequestJson(prompt);
        ModelClientException lastEx = null;

        for (int attempt = 0; attempt <= MAX_RETRIES; attempt++) {
            try {
                HttpRequest request = HttpRequest.newBuilder(URI.create(OPENAI_URL))
                        .header("Authorization", "Bearer " + apiKey)
                        .header("Content-Type",  "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .build();

                HttpResponse<String> response =
                        http.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200) {
                    String text = extractContent(response.body());
                    if (text.isEmpty())
                        throw new ModelClientException(
                                "Empty content in OpenAI response (possible API format change)", false);
                    return new ModelOutput(text, List.of(), Map.of());
                }

                boolean retryable = response.statusCode() >= 500 || response.statusCode() == 429;
                lastEx = new ModelClientException("OpenAI HTTP " + response.statusCode(), retryable);
                if (!retryable || attempt == MAX_RETRIES) throw lastEx;

                Thread.sleep(RETRY_BASE_MS * (1L << attempt));

            } catch (ModelClientException e) {
                throw e;
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                throw new ModelClientException("Interrupted during retry backoff", false);
            } catch (Exception e) {
                lastEx = new ModelClientException("Model call failed: " + e.getMessage(), true, e);
                if (attempt == MAX_RETRIES) throw lastEx;
                try { Thread.sleep(RETRY_BASE_MS * (1L << attempt)); }
                catch (InterruptedException ie) { Thread.currentThread().interrupt(); throw lastEx; }
            }
        }
        throw lastEx;
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private String buildRequestJson(ModelPrompt prompt) {
        StringBuilder sb = new StringBuilder("{\"model\":\"")
                .append(escapeJson(model)).append("\",\"messages\":[");
        for (int i = 0; i < prompt.messages().size(); i++) {
            if (i > 0) sb.append(',');
            ModelMessage m = prompt.messages().get(i);
            sb.append("{\"role\":\"").append(m.role().name().toLowerCase())
              .append("\",\"content\":\"").append(escapeJson(m.content())).append("\"}");
        }
        return sb.append("]}").toString();
    }

    private static String extractContent(String json) {
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
