package com.aihandwriting.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

@Service
public class AIService {

    // IMPORTANT: supply this via application.properties or environment variable.
    // Do NOT hard-code keys in source.
   
    @Value("${openai.api.key:}")
    private String openAiKey;

    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    /**
     * Extract structured JSON from raw OCR text by calling OpenAI chat completions.
     * Returns a JSON string. If OpenAI key is missing or API fails, returns a safe fallback JSON.
     */
    public String extractStructuredData(String rawText) {
        try {
            if (openAiKey == null || openAiKey.isBlank()) {
                return buildAgentFallback(rawText, "OpenAI API key not configured.");
            }

                        // Stronger prompt: require normalized values and numeric confidences
                        String system = """
                                        You are an AI agent that extracts and NORMALIZES data from noisy OCR/handwritten text.
                                        Produce a single VALID JSON object EXACTLY matching this schema (no extra keys, no markdown, no commentary):
                                        {
                                            "document_type": string,
                                            "pages": [
                                                {
                                                    "page": number,
                                                    "fields": [ { "name": string, "value": string|null } ],
                                                    "tables": []
                                                }
                                            ]
                                        }

                                        RULES:
                                        - Normalize values where possible: names in Title Case (e.g., "Paula Butler"), emails lowercase (e.g., "foo@bar.com"), phone numbers as digits (prefer international format if present).
                                        - If a value cannot be determined, set "value": null and "confidence": 0.0.
                                        - Confidence MUST be a number in [0.0, 1.0] with two-decimal precision when possible.
                                        - Include the raw OCR in a field with name "ocrText".
                                        - Include any additional fields you find (e.g., clinician, clinic_code, location) as entries in "fields".
                                        - Return only the JSON object and nothing else.
                                        """;

                        String user = "OCR input:\n" + rawText + "\n\nReturn only the JSON object in the schema described.";

            Map<String, Object> requestBody = Map.of(
                    "model", "gpt-4o-mini",
                    "messages", new Object[] {
                            Map.of("role", "system", "content", system),
                            Map.of("role", "user", "content", user)
                    },
                    "max_tokens", 1024,
                    "temperature", 0.0
            );

            String requestJson = mapper.writeValueAsString(requestBody);

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.openai.com/v1/chat/completions"))
                    .timeout(Duration.ofSeconds(30))
                    .header("Authorization", "Bearer " + openAiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestJson, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            int status = resp.statusCode();
            String body = resp.body();

            if (status / 100 != 2) {
                if (status == 401) {
                    return buildAgentFallback(rawText, "OpenAI returned 401 Unauthorized - check API key.");
                }
                return buildAgentFallback(rawText, "OpenAI API error: HTTP " + status);
            }

            JsonNode root = mapper.readTree(body);
            JsonNode choices = root.path("choices");
            if (!choices.isArray() || choices.size() == 0) {
                return buildAgentFallback(rawText, "OpenAI returned no choices.");
            }

            String assistantText = choices.get(0).path("message").path("content").asText();
            String jsonResult = tryExtractJsonFromAssistant(assistantText);
            if (jsonResult == null) {
                return buildAgentFallback(rawText, "OpenAI returned non-JSON output.");
            }
            JsonNode parsed = mapper.readTree(jsonResult);

            // If the assistant already returned the target schema, return it directly
            if (parsed.has("document_type") && parsed.has("pages")) {
                return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(parsed);
            }

            // Build the requested schema:
            // {
            //   "document_type": string,
            //   "pages": [ { "page": number, "fields": [ {name,value,confidence} ], "tables": [] } ]
            // }
            ObjectNode out = mapper.createObjectNode();
            out.put("document_type", "handwritten_form");

            ObjectNode page = mapper.createObjectNode();
            page.put("page", 1);

            ArrayNode fields = mapper.createArrayNode();

            // Map common keys into fields with a default confidence of 0.0
            String[] keys = new String[]{"name", "phone", "email", "address", "postcode", "dob", "ocrText"};
            for (String k : keys) {
                ObjectNode f = mapper.createObjectNode();
                f.put("name", k);
                JsonNode v = parsed.path(k);
                String sval = v.isMissingNode() || v.isNull() ? "" : v.asText();
                f.put("value", sval);
                f.put("confidence", 0.0);
                fields.add(f);
            }

            // If parsed contains other_fields object, add its entries as additional fields
            JsonNode other = parsed.path("other_fields");
            if (other != null && other.isObject()) {
                other.fieldNames().forEachRemaining(fn -> {
                    JsonNode vv = other.path(fn);
                    ObjectNode f = mapper.createObjectNode();
                    f.put("name", fn);
                    f.put("value", vv.isNull() ? "" : vv.asText());
                    f.put("confidence", 0.0);
                    fields.add(f);
                });
            }

            page.set("fields", fields);
            page.set("tables", mapper.createArrayNode());

            ArrayNode pages = mapper.createArrayNode();
            pages.add(page);

            out.set("pages", pages);

            return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(out);

        } catch (Exception e) {
            try {
                return buildAgentFallback(rawText, "Exception: " + e.getMessage());
            } catch (Exception ignore) {
                return "{\"document_type\":\"unknown\",\"pages\":[{\"page\":1,\"fields\":[],\"tables\":[]}]}";
            }
        }
    }

    // New fallback for agent schema — returns the same structured schema with empty/default values
    private String buildAgentFallback(String rawText, String note) throws Exception {
        ObjectNode out = mapper.createObjectNode();
        out.put("document_type", "unknown");

        ObjectNode page = mapper.createObjectNode();
        page.put("page", 1);

        ArrayNode fields = mapper.createArrayNode();
        String[] keys = new String[]{"name", "phone", "email", "address", "postcode", "dob", "ocrText"};
        for (String k : keys) {
            ObjectNode f = mapper.createObjectNode();
            f.put("name", k);
            if ("ocrText".equals(k)) {
                f.put("value", rawText == null ? "" : rawText);
            } else {
                f.put("value", "");
            }
            f.put("confidence", 0.0);
            fields.add(f);
        }

        // include the note as a field so consumers can see why fallback was used
        ObjectNode noteField = mapper.createObjectNode();
        noteField.put("name", "note");
        noteField.put("value", note == null ? "" : note);
        noteField.put("confidence", 0.0);
        fields.add(noteField);

        page.set("fields", fields);
        page.set("tables", mapper.createArrayNode());

        ArrayNode pages = mapper.createArrayNode();
        pages.add(page);
        out.set("pages", pages);

        return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(out);
    }

    private static String tryExtractJsonFromAssistant(String assistantText) {
        if (assistantText == null) return null;
        assistantText = assistantText.trim();

        // remove code fences if present
        if (assistantText.startsWith("```") && assistantText.contains("```")) {
            int first = assistantText.indexOf("```");
            int second = assistantText.indexOf("```", first + 3);
            if (second > first) {
                assistantText = assistantText.substring(first + 3, second).trim();
            }
        }
        // try direct parse
        try {
            final ObjectMapper m = new ObjectMapper();
            m.readTree(assistantText);
            return assistantText;
        } catch (Exception ignored) {}

        // fallback: extract first balanced JSON object
        String candidate = extractFirstJsonObject(assistantText);
        return candidate;
    }

    private static String extractFirstJsonObject(String text) {
        if (text == null) return null;
        int len = text.length();
        int start = -1;
        int depth = 0;
        boolean inString = false;
        for (int i = 0; i < len; i++) {
            char c = text.charAt(i);
            if (c == '"' && (i == 0 || text.charAt(i - 1) != '\\')) {
                inString = !inString;
            }
            if (inString) continue;
            if (c == '{') {
                if (start == -1) start = i;
                depth++;
            } else if (c == '}') {
                if (depth > 0) depth--;
                if (depth == 0 && start != -1) {
                    return text.substring(start, i + 1);
                }
            }
        }
        return null;
    }

    // Removed old simple fallback; use buildAgentFallback for consistent schema.
    //it is in json format now
}
