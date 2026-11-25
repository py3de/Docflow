package com.docflow.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Typed view of the docflow.* settings in application.yml. */
@ConfigurationProperties(prefix = "docflow")
public record DocFlowProperties(Cors cors, Storage storage, DemoData demoData, Pipeline pipeline, Llm llm) {

    public record Cors(List<String> allowedOrigins) {
    }

    public record Storage(String root) {
    }

    public record DemoData(boolean enabled, int days) {
    }

    public record Pipeline(double reviewThreshold, String poNumberPattern) {
    }

    public record Llm(String provider, String apiKey, String model, String baseUrl, int timeoutSeconds,
                      double inputPricePerMillion, double outputPricePerMillion) {

        public boolean hasApiKey() {
            return apiKey != null && !apiKey.isBlank();
        }
    }
}
