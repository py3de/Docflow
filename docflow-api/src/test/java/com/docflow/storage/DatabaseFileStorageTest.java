package com.docflow.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/** The cloud's storage, tested against H2 with the same Flyway schema. */
@SpringBootTest(properties = {
        "docflow.storage.type=database",
        "docflow.demo-data.days=3",
        "docflow.storage.root=${java.io.tmpdir}/docflow-test-uploads"
})
class DatabaseFileStorageTest {

    @Autowired FileStorage storage;

    @Test
    void theDatabaseImplementationIsActive() {
        assertThat(storage).isInstanceOf(DatabaseFileStorage.class);
    }

    @Test
    void storesReadsAndDeduplicates() {
        byte[] content = ("%PDF-1.7 db test " + System.nanoTime()).getBytes(StandardCharsets.US_ASCII);
        String sha = Integer.toHexString(java.util.Arrays.hashCode(content));

        String path = storage.store("acme-de", sha, "pdf", content);
        String again = storage.store("acme-de", sha, "pdf", content);

        assertThat(again).isEqualTo(path).isEqualTo("acme-de/" + sha + ".pdf");
        assertThat(storage.read(path)).isEqualTo(content);
    }

    @Test
    void readingAMissingFileFails() {
        assertThatThrownBy(() -> storage.read("acme-de/nothing.pdf")).isInstanceOf(IllegalStateException.class);
    }
}
