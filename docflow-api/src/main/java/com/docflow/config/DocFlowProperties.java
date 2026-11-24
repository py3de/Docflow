package com.docflow.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Typed view of the docflow.* settings in application.yml. */
@ConfigurationProperties(prefix = "docflow")
public record DocFlowProperties(Cors cors, Storage storage, DemoData demoData) {

    public record Cors(List<String> allowedOrigins) {
    }

    public record Storage(String root) {
    }

    public record DemoData(boolean enabled, int days) {
    }
}
