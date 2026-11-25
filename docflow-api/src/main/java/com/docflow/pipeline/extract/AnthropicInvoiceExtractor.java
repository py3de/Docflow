package com.docflow.pipeline.extract;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.http.MediaType;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * Claude via the Anthropic Messages API. Structured output through a forced tool call:
 * the model must call "record_invoice" with arguments that match the JSON schema.
 */
public class AnthropicInvoiceExtractor implements InvoiceExtractor {

    static final String DEFAULT_BASE_URL = "https://api.anthropic.com";
    static final String DEFAULT_MODEL = "claude-haiku-5-5";
    private static final String API_VERSION = "2023-06-01";

    private final RestClient http;
    private final String model;

    public AnthropicInvoiceExtractor(RestClient.Builder builder, String baseUrl, String apiKey, String model) {
        this.model = model;
        this.http = builder.baseUrl(baseUrl)
                .defaultHeader("x-api-key", apiKey)
                .defaultHeader("anthropic-version", API_VERSION)
                .build();
    }

    @Override
    public ExtractedInvoice extract(String documentText) {
        JsonNodeFactory f = JsonNodeFactory.instance;
        ObjectNode body = f.objectNode()
                .put("model", model)
                .put("max_tokens", 2048)
                .put("temperature", 0)
                .put("system", InvoicePrompt.SYSTEM);
        body.putArray("messages").addObject()
                .put("role", "user")
                .put("content", InvoicePrompt.userMessage(documentText));
        ObjectNode tool = body.putArray("tools").addObject()
                .put("name", InvoicePrompt.TOOL_NAME)
                .put("description", "Record the fields extracted from the invoice.");
        tool.set("input_schema", InvoicePrompt.schema());
        body.putObject("tool_choice").put("type", "tool").put("name", InvoicePrompt.TOOL_NAME);

        JsonNode response = call(body);
        for (JsonNode block : response.path("content")) {
            if ("tool_use".equals(block.path("type").asText())) {
                return InvoicePrompt.parse(block.path("input"), model,
                        response.path("usage").path("input_tokens").asInt(),
                        response.path("usage").path("output_tokens").asInt());
            }
        }
        throw new ExtractionException("Claude did not return the expected tool call.", false, null);
    }

    @Override
    public String describe() {
        return "anthropic:" + model;
    }

    private JsonNode call(ObjectNode body) {
        try {
            return http.post().uri("/v1/messages")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientResponseException e) {
            int status = e.getStatusCode().value();
            throw new ExtractionException("Anthropic API returned " + status + ": " + shorten(e.getResponseBodyAsString()),
                    status == 429 || status >= 500, e);
        } catch (ResourceAccessException e) {
            throw new ExtractionException("Anthropic API not reachable: " + e.getMessage(), true, e);
        }
    }

    static String shorten(String s) {
        return s == null ? "" : s.length() > 300 ? s.substring(0, 300) + "…" : s;
    }
}
