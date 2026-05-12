package com.agentruntime.usecase.research;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.Objects;

/**
 * Fetches a web page and returns its text content (HTML tags stripped).
 *
 * Ported from agent-framework usecase.research.FetchPageTool; namespace-adapted.
 * Uses Java's built-in HttpClient (Java 11+) with redirect following enabled.
 */
public final class FetchPageTool {

    public static final String NAME = "fetchPage";

    private final HttpClient http = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    public Map<String, Object> execute(Map<String, Object> input) throws Exception {
        String url = Objects.toString(input.getOrDefault("url", ""), "").trim();
        if (url.isBlank()) throw new IllegalArgumentException("FetchPageTool: 'url' parameter is required");

        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .header("User-Agent", "AgentRuntime/1.0")
                .GET()
                .build();

        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() >= 400) {
            throw new RuntimeException("FetchPageTool: HTTP " + response.statusCode() + " for " + url);
        }

        // Strip HTML tags and collapse whitespace
        String text = response.body()
                .replaceAll("<[^>]+>", " ")
                .replaceAll("\\s{3,}", " ")
                .trim();

        // Truncate to avoid context overflow
        if (text.length() > 4000) text = text.substring(0, 4000) + "...";

        return Map.of("url", url, "text", text);
    }
}
