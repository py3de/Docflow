package com.docflow.pipeline.extract;

import com.docflow.config.DocFlowProperties;
import com.docflow.config.DocFlowProperties.Llm;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/** Picks the extractor from docflow.llm.provider. Fails at startup with a clear message if it can't work. */
@Configuration
public class ExtractorConfig {

    private static final Logger log = LoggerFactory.getLogger(ExtractorConfig.class);

    @Bean
    InvoiceExtractor invoiceExtractor(DocFlowProperties properties, RestClient.Builder builder, ObjectMapper json) {
        Llm llm = properties.llm();
        String provider = llm.provider() == null ? "offline" : llm.provider().trim().toLowerCase();

        SimpleClientHttpRequestFactory timeouts = new SimpleClientHttpRequestFactory();
        timeouts.setConnectTimeout(Duration.ofSeconds(10));
        timeouts.setReadTimeout(Duration.ofSeconds(Math.max(5, llm.timeoutSeconds())));
        RestClient.Builder http = builder.requestFactory(timeouts);

        InvoiceExtractor extractor = switch (provider) {
            case "offline" -> new OfflineInvoiceExtractor();
            case "anthropic" -> {
                requireKey(llm, "anthropic");
                yield new AnthropicInvoiceExtractor(http, orDefault(llm.baseUrl(), AnthropicInvoiceExtractor.DEFAULT_BASE_URL),
                        llm.apiKey(), orDefault(llm.model(), AnthropicInvoiceExtractor.DEFAULT_MODEL));
            }
            case "openai" -> {
                if (llm.model() == null || llm.model().isBlank()) {
                    throw new IllegalStateException(
                            "docflow.llm.provider=openai needs a model: set DOCFLOW_LLM_MODEL (e.g. the model name from your provider).");
                }
                String baseUrl = orDefault(llm.baseUrl(), OpenAiInvoiceExtractor.DEFAULT_BASE_URL);
                if (baseUrl.equals(OpenAiInvoiceExtractor.DEFAULT_BASE_URL)) {
                    requireKey(llm, "openai");   // a local Ollama with its own base URL needs no key
                }
                yield new OpenAiInvoiceExtractor(http, json, baseUrl, llm.apiKey(), llm.model());
            }
            default -> throw new IllegalStateException(
                    "Unknown docflow.llm.provider '" + llm.provider() + "'. Use offline, anthropic or openai.");
        };
        log.info("Invoice extraction uses {}", extractor.describe());
        return extractor;
    }

    private static void requireKey(Llm llm, String provider) {
        if (!llm.hasApiKey()) {
            throw new IllegalStateException(
                    "docflow.llm.provider=" + provider + " needs an API key: set the environment variable DOCFLOW_LLM_API_KEY.");
        }
    }

    private static String orDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
