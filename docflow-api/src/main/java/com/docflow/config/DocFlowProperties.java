package com.docflow.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Typed view of the docflow.* settings in application.yml. */
@ConfigurationProperties(prefix = "docflow")
public record DocFlowProperties(Cors cors, Storage storage, DemoData demoData, Pipeline pipeline, Llm llm, Limits limits) {

    public record Cors(List<String> allowedOrigins) {
    }

    /** type: "local" (files on disk, default) or "database" (PostgreSQL, used in the cloud). */
    public record Storage(String root, String type) {
    }

    /** Protection for a public demo. 0 = no limit. */
    public record Limits(int uploadsPerIpPerHour, int uploadsPerDay) {
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
