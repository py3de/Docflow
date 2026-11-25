package com.docflow.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.docflow.config.CloudDatabaseConfig.DbCredentials;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class CloudDatabaseConfigTest {

    private final ObjectMapper json = new ObjectMapper();

    @Test
    void readsSeparateCredentialFields() {
        String vcap = """
                {"postgresql-db":[{"name":"docflow-db","label":"postgresql-db","tags":["postgresql","relational"],
                  "credentials":{"hostname":"db.example.internal","port":"6543","dbname":"abc123",
                                 "username":"u1","password":"secret"}}]}""";
        Optional<DbCredentials> db = CloudDatabaseConfig.parse(vcap, json);
        assertThat(db).contains(new DbCredentials(
                "jdbc:postgresql://db.example.internal:6543/abc123?sslmode=require&currentSchema=docflow", "u1", "secret"));
    }

    @Test
    void readsAConnectionUriWithAnEncodedPassword() {
        String vcap = """
                {"postgresql-db":[{"name":"docflow-db","credentials":{"uri":"postgres://u2:p%40ss@db.example:5432/mydb"}}]}""";
        assertThat(CloudDatabaseConfig.parse(vcap, json)).contains(new DbCredentials(
                "jdbc:postgresql://db.example:5432/mydb?sslmode=require&currentSchema=docflow", "u2", "p@ss"));
    }

    @Test
    void prefersTheServiceNamedDocflowDb() {
        String vcap = """
                {"postgresql-db":[
                  {"name":"other-db","label":"postgresql-db","credentials":{"hostname":"a","dbname":"a","username":"a","password":"a"}},
                  {"name":"docflow-db","label":"postgresql-db","credentials":{"hostname":"b","dbname":"b","username":"b","password":"b"}}]}""";
        assertThat(CloudDatabaseConfig.parse(vcap, json)).map(DbCredentials::username).contains("b");
    }

    @Test
    void noBoundDatabaseMeansEmpty() {
        assertThat(CloudDatabaseConfig.parse(null, json)).isEmpty();
        assertThat(CloudDatabaseConfig.parse("{}", json)).isEmpty();
        assertThat(CloudDatabaseConfig.parse("{\"xsuaa\":[{\"name\":\"auth\",\"credentials\":{}}]}", json)).isEmpty();
    }
}
