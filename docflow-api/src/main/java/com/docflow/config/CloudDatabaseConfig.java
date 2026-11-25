package com.docflow.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * In the cloud, the database is a service bound to the app. Cloud Foundry passes its credentials
 * as JSON in the VCAP_SERVICES environment variable; this turns them into a DataSource.
 * No password is ever written into the code or the repository.
 */
@Configuration
@Profile("cloud")
public class CloudDatabaseConfig {

    private static final Logger log = LoggerFactory.getLogger(CloudDatabaseConfig.class);
    static final String SERVICE_NAME = "docflow-db";
    /** DocFlow's tables live in their own schema, created by Flyway on first start. */
    static final String SCHEMA = "docflow";

    /** What we need from the bound service. */
    public record DbCredentials(String jdbcUrl, String username, String password) {
    }

    @Bean
    DataSource dataSource() {
        String vcap = System.getenv("VCAP_SERVICES");
        DbCredentials db = parse(vcap, new ObjectMapper()).orElseThrow(() -> new IllegalStateException(
                "No PostgreSQL service found in VCAP_SERVICES. Create and bind it: "
                        + "cf create-service postgresql-db trial " + SERVICE_NAME + " (the manifest binds it)."));
        log.info("Using bound PostgreSQL service at {}", db.jdbcUrl().replaceAll("//[^/]*@", "//***@"));
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(db.jdbcUrl());
        config.setUsername(db.username());
        config.setPassword(db.password());
        config.setMaximumPoolSize(5);          // trial databases allow only a few connections
        config.setPoolName("docflow-cloud");
        return new HikariDataSource(config);
    }

    /**
     * Finds the PostgreSQL service by name (docflow-db), label (postgresql-db) or tag, and reads either
     * a connection URI or separate hostname / port / dbname / username / password fields.
     */
    static Optional<DbCredentials> parse(String vcapServices, ObjectMapper json) {
        if (vcapServices == null || vcapServices.isBlank()) {
            return Optional.empty();
        }
        try {
            JsonNode root = json.readTree(vcapServices);
            JsonNode match = null;
            for (JsonNode instances : root) {
                for (JsonNode instance : instances) {
                    boolean byName = SERVICE_NAME.equals(instance.path("name").asText());
                    boolean byLabel = instance.path("label").asText().contains("postgres");
                    boolean byTag = instance.path("tags").toString().toLowerCase().contains("postgres");
                    if (byName || (match == null && (byLabel || byTag))) {
                        match = instance;
                    }
                }
            }
            return match == null ? Optional.empty() : fromCredentials(match.path("credentials"));
        } catch (Exception e) {
            throw new IllegalStateException("VCAP_SERVICES could not be read: " + e.getMessage(), e);
        }
    }

    private static Optional<DbCredentials> fromCredentials(JsonNode c) {
        String host = first(c, "hostname", "host");
        String port = first(c, "port");
        String db = first(c, "dbname", "database", "name");
        String user = first(c, "username", "user");
        String password = first(c, "password");

        String uri = first(c, "uri", "url");
        if (uri != null && uri.startsWith("postgres")) {
            URI u = URI.create(uri.replaceFirst("^postgres(ql)?://", "http://"));
            host = host != null ? host : u.getHost();
            port = port != null ? port : (u.getPort() > 0 ? String.valueOf(u.getPort()) : null);
            db = db != null ? db : u.getPath().replaceFirst("^/", "");
            if (u.getRawUserInfo() != null) {
                String[] parts = u.getRawUserInfo().split(":", 2);
                user = user != null ? user : decode(parts[0]);
                password = password != null ? password : (parts.length > 1 ? decode(parts[1]) : null);
            }
        }
        if (host == null || db == null || user == null) {
            return Optional.empty();
        }
        // Own schema: the service's default "public" schema is not empty on BTP, and Flyway rightly refuses to share it.
        String url = "jdbc:postgresql://" + host + ":" + (port == null ? "5432" : port) + "/" + db
                + "?sslmode=require&currentSchema=" + SCHEMA;
        return Optional.of(new DbCredentials(url, user, password));
    }

    private static String first(JsonNode node, String... keys) {
        for (String k : keys) {
            JsonNode v = node.path(k);
            if (!v.isMissingNode() && !v.isNull() && !v.asText().isBlank()) {
                return v.asText();
            }
        }
        return null;
    }

    private static String decode(String s) {
        return URLDecoder.decode(s, StandardCharsets.UTF_8);
    }
}
