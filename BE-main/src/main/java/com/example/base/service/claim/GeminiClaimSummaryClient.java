package com.example.base.service.claim;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import java.util.*;

@Component
public class GeminiClaimSummaryClient {
    private final boolean enabled;
    private final String apiKey;
    private final String model;
    private final ObjectMapper json;
    private final RestClient client;

    public GeminiClaimSummaryClient(ObjectMapper json,
            @Value("${app.claim-summary.enabled:false}") boolean enabled,
            @Value("${app.claim-summary.api-key:}") String apiKey,
            @Value("${app.claim-summary.model:gemini-3.5-flash-lite}") String model,
            @Value("${app.claim-summary.timeout-ms:20000}") int timeout) {
        this.json = json; this.enabled = enabled; this.apiKey = apiKey.strip(); this.model = model;
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3000); factory.setReadTimeout(Math.clamp(timeout, 1000, 30000));
        client = RestClient.builder().requestFactory(factory).build();
    }

    public boolean available() { return enabled && !apiKey.isBlank() && model.matches("gemini-[a-z0-9.-]{1,64}"); }

    /** The provider sees only aliases, fixed criterion keys, fact IDs and canonical dictionary values. */
    Map<String, Object> request(List<ClaimSummarySanitizer.SafeRow> rows) {
        var input = rows.stream().map(row -> {
            var criteria = new LinkedHashMap<String, Object>();
            row.criteria().forEach((key, facts) -> criteria.put(key, facts.stream()
                    .map(f -> Map.of("id", f.id(), "text", f.text())).toList()));
            return Map.of("alias", row.alias(), "criteria", criteria);
        }).toList();
        var fields = new LinkedHashMap<String, Object>();
        fields.put("alias", Map.of("type", "string"));
        for (String criterion : ClaimSummarySanitizer.CRITERIA)
            fields.put(criterion, Map.of("type", "array", "items", Map.of("type", "string"), "maxItems", 3));
        var schema = Map.of("type", "object", "properties", Map.of("rows", Map.of("type", "array", "items",
                Map.of("type", "object", "properties", fields, "required", List.of("alias", "identity", "appearance", "accessories"),
                        "additionalProperties", false))), "required", List.of("rows"), "additionalProperties", false);
        try {
            String prompt = "Summarize these independent item descriptions by selecting up to 3 distinct fact IDs per criterion per row. "
                    + "Prefer specific brands/types over generic types and specific colors over unspecified colors. "
                    + "Only select IDs belonging to that same row and criterion. Select at least one if facts exist, else []. "
                    + "Return every row exactly once in input order. No new text, no ownership judgments, no scores.\n"
                    + json.writeValueAsString(input);
            return Map.of("contents", List.of(Map.of("role", "user", "parts", List.of(Map.of("text", prompt)))),
                    "generationConfig", Map.of("responseMimeType", "application/json", "responseJsonSchema", schema,
                            "maxOutputTokens", 4096));
        } catch (Exception ex) { throw new IllegalStateException("Summary input encoding failed"); }
    }

    public JsonNode select(List<ClaimSummarySanitizer.SafeRow> rows) {
        if (!available()) throw new IllegalStateException("Summary provider unavailable");
        // No custom host, redirects, credentials in URL, raw answers, images, tools or external retrieval.
        JsonNode response = client.post().uri("https://generativelanguage.googleapis.com/v1beta/models/" + model + ":generateContent")
                .header("x-goog-api-key", apiKey).contentType(MediaType.APPLICATION_JSON).body(request(rows))
                .retrieve().body(JsonNode.class);
        if (response == null || !"STOP".equals(response.path("candidates").path(0).path("finishReason").asText()))
            throw new IllegalStateException("Summary response incomplete");
        StringBuilder output = new StringBuilder();
        for (JsonNode part : response.path("candidates").path(0).path("content").path("parts"))
            if (!part.path("thought").asBoolean(false)) output.append(part.path("text").asText(""));
        if (output.length() > 50000) throw new IllegalStateException("Summary response too large");
        try { return json.readTree(output.toString()); }
        catch (Exception ex) { throw new IllegalStateException("Summary response invalid"); }
    }
}
