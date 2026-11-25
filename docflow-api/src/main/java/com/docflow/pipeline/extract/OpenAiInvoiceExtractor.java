package com.docflow.pipeline.extract;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.http.MediaType;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * Any OpenAI-compatible Chat Completions API (OpenAI, Mistral, Groq, a local Ollama ...).
 * Structured output through response_format json_schema in strict mode.
 */
public class OpenAiInvoiceExtractor implements InvoiceExtractor {

    static final String DEFAULT_BASE_URL = "https://api.openai.com/v1";

    private final RestClient http;
    private final ObjectMapper json;
    private final String model;

    public OpenAiInvoiceExtractor(RestClient.Builder builder, ObjectMapper json, String baseUrl, String apiKey,
                                  String model) {
        this.json = json;
        this.model = model;
        RestClient.Builder b = builder.baseUrl(baseUrl);
        if (apiKey != null && !apiKey.isBlank()) {
            b = b.defaultHeader("Authorization", "Bearer " + apiKey);
        }
        this.http = b.build();
    }

    @Override
    public ExtractedInvoice extract(String documentText) {
        JsonNodeFactory f = JsonNodeFactory.instance;
        // No temperature: some newer models reject it. The strict schema keeps answers consistent.
        ObjectNode body = f.objectNode().put("model", model);
        var messages = body.putArray("messages");
        messages.addObject().put("role", "system").put("content", InvoicePrompt.SYSTEM);
        messages.addObject().put("role", "user").put("content", InvoicePrompt.userMessage(documentText));
        ObjectNode format = body.putObject("response_format").put("type", "json_schema");
        ObjectNode schema = format.putObject("json_schema").put("name", InvoicePrompt.TOOL_NAME).put("strict", true);
        schema.set("schema", InvoicePrompt.schema());

        JsonNode response = call(body);
        String content = response.path("choices").path(0).path("message").path("content").asText(null);
        if (content == null || content.isBlank()) {
            throw new ExtractionException("The model returned no content.", false, null);
        }
        try {
            return InvoicePrompt.parse(json.readTree(content), model,
                    response.path("usage").path("prompt_tokens").asInt(),
                    response.path("usage").path("completion_tokens").asInt());
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new ExtractionException("The model's answer is not valid JSON.", false, e);
        }
    }

    @Override
    public String describe() {
        return "openai-compatible:" + model;
    }

    private JsonNode call(ObjectNode body) {
        try {
            return http.post().uri("/chat/completions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientResponseException e) {
            int status = e.getStatusCode().value();
            throw new ExtractionException("LLM API returned " + status + ": "
                    + AnthropicInvoiceExtractor.shorten(e.getResponseBodyAsString()), status == 429 || status >= 500, e);
        } catch (ResourceAccessException e) {
            throw new ExtractionException("LLM API not reachable: " + e.getMessage(), true, e);
        }
    }
}
