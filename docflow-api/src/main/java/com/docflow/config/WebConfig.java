package com.docflow.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final DocFlowProperties properties;

    public WebConfig(DocFlowProperties properties) {
        this.properties = properties;
    }

    /** Lets the React dev server (localhost:5173) call the API from the browser. */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(properties.cors().allowedOrigins().toArray(String[]::new))
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .exposedHeaders("Location", "X-Total-Count");
    }

    /** One injectable clock, so tests can control "now". */
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
