package com.docflow.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.docflow.pipeline.extract.AnthropicInvoiceExtractor;
import com.docflow.pipeline.extract.ExtractedInvoice;
import com.docflow.pipeline.extract.ExtractionException;
import com.docflow.pipeline.extract.OpenAiInvoiceExtractor;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/** The HTTP contract with each provider, against a fake server: no API key, no cost. */
class LlmClientTest {

    private static final String FIELDS = """
            "vendor_name":{"value":"Nordwerk Logistik GmbH","confidence":0.97},
            "vendor_vat_id":{"value":"DE287451903","confidence":0.95},
            "invoice_number":{"value":"INV-2026-020001","confidence":0.99},
            "invoice_date":{"value":"2026-11-20","confidence":0.98},
            "po_number":{"value":"PO-450099001","confidence":0.9},
            "net_amount":{"value":"604.80","confidence":0.96},
            "vat_amount":{"value":"114,91","confidence":0.96},
            "gross_amount":{"value":"719.71","confidence":0.99},
            "due_date":{"value":null,"confidence":0},
            "currency":"EUR",
            "line_items":[{"description":"Transport","quantity":3,"unit_price":180,"total":540}]
            """;

    @Test
    void anthropicForcesTheToolAndReadsItsInputAndUsage() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        AnthropicInvoiceExtractor extractor = new AnthropicInvoiceExtractor(builder, "https://llm.test", "test-key", "claude-test");

        server.expect(requestTo("https://llm.test/v1/messages"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("x-api-key", "test-key"))
                .andExpect(header("anthropic-version", "2023-06-01"))
                .andExpect(jsonPath("$.tool_choice.name").value("record_invoice"))
                .andExpect(jsonPath("$.tools[0].input_schema.properties.gross_amount").exists())
                .andRespond(withSuccess("""
                        {"content":[{"type":"tool_use","id":"t1","name":"record_invoice","input":{%s}}],
                         "usage":{"input_tokens":1200,"output_tokens":300}}""".formatted(FIELDS), MediaType.APPLICATION_JSON));

        ExtractedInvoice result = extractor.extract("invoice text");

        assertThat(result.fields().get("gross_amount").value()).isEqualTo("719.71");
        assertThat(result.fields().get("vat_amount").value()).isEqualTo("114.91");   // normalised from "114,91"
        assertThat(result.fields().get("due_date").value()).isNull();
        assertThat(result.lineItems()).hasSize(1);
        assertThat(result.inputTokens()).isEqualTo(1200);
        assertThat(result.outputTokens()).isEqualTo(300);
        server.verify();
    }

    @Test
    void openAiUsesAStrictJsonSchemaAndReadsUsage() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        OpenAiInvoiceExtractor extractor = new OpenAiInvoiceExtractor(builder, new ObjectMapper(), "https://llm.test/v1",
                "test-key", "some-model");
        String content = new ObjectMapper().valueToTree("{" + FIELDS + "}").toString();   // JSON string inside JSON

        server.expect(requestTo("https://llm.test/v1/chat/completions"))
                .andExpect(header("Authorization", "Bearer test-key"))
                .andExpect(jsonPath("$.response_format.json_schema.strict").value(true))
                .andRespond(withSuccess("""
                        {"choices":[{"message":{"role":"assistant","content":%s}}],
                         "usage":{"prompt_tokens":900,"completion_tokens":250}}""".formatted(content), MediaType.APPLICATION_JSON));

        ExtractedInvoice result = extractor.extract("invoice text");

        assertThat(result.fields().get("invoice_number").value()).isEqualTo("INV-2026-020001");
        assertThat(result.inputTokens()).isEqualTo(900);
        assertThat(result.outputTokens()).isEqualTo(250);
        server.verify();
    }

    @Test
    void rateLimitsAreRetryableButABadKeyIsNot() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        AnthropicInvoiceExtractor extractor = new AnthropicInvoiceExtractor(builder, "https://llm.test", "k", "m");

        server.expect(requestTo("https://llm.test/v1/messages")).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));
        assertThatThrownBy(() -> extractor.extract("x")).isInstanceOfSatisfying(ExtractionException.class,
                e -> assertThat(e.isRetryable()).isTrue());

        server.reset();
        server.expect(requestTo("https://llm.test/v1/messages")).andRespond(withStatus(HttpStatus.UNAUTHORIZED));
        assertThatThrownBy(() -> extractor.extract("x")).isInstanceOfSatisfying(ExtractionException.class,
                e -> assertThat(e.isRetryable()).isFalse());
    }
}
