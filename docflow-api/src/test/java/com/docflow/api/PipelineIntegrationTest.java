package com.docflow.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

/**
 * End to end: upload a real PDF, let the background pipeline run (offline extractor), check the outcome.
 * Same properties as DocumentApiTest, so both share one application context and one seeded database.
 */
@SpringBootTest(properties = {
        "docflow.demo-data.days=3",
        "docflow.storage.root=${java.io.tmpdir}/docflow-test-uploads"
})
@AutoConfigureMockMvc
class PipelineIntegrationTest {

    private static final String TENANT = "X-Tenant-Id";
    private static final Set<String> FINISHED = Set.of("MATCHED", "NEEDS_REVIEW", "FAILED", "APPROVED", "REJECTED");

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    @Test
    void aCleanInvoiceIsExtractedLocatedAndMatchedWithoutAHuman() throws Exception {
        JsonNode doc = uploadAndWait("sample-invoice-clean.pdf");

        assertThat(doc.get("status").asText()).isEqualTo("MATCHED");
        assertThat(field(doc, "gross_amount").get("value").asText()).isEqualTo("719.71");
        assertThat(field(doc, "invoice_number").get("value").asText()).isEqualTo("INV-2026-020001");
        assertThat(field(doc, "gross_amount").get("box").get("y").asDouble()).isBetween(0.6, 0.8);
        assertThat(doc.get("poMatch").get("poNumber").asText()).isEqualTo("PO-450099001");
        assertThat(doc.get("lineItems")).hasSize(2);
        assertThat(doc.get("timings")).hasSize(5);
        assertThat(doc.get("model").asText()).isEqualTo("offline-rules");
        assertThat(doc.get("hasOriginal").asBoolean()).isTrue();

        mvc.perform(get("/api/v1/documents/" + doc.get("id").asText() + "/pages/1").header(TENANT, "acme-de"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_PNG));
    }

    @Test
    void wrongTotalsSendTheInvoiceToReview() throws Exception {
        JsonNode doc = uploadAndWait("sample-invoice-vat-error.pdf");

        assertThat(doc.get("status").asText()).isEqualTo("NEEDS_REVIEW");
        List<String> rules = new ArrayList<>();
        field(doc, "vat_amount").get("issues").forEach(i -> rules.add(i.get("rule").asText()));
        assertThat(rules).contains("TOTALS_CONSISTENT");
    }

    @Test
    void aWrongPoNumberIsFuzzyMatchedButFlagged() throws Exception {
        JsonNode doc = uploadAndWait("sample-invoice-wrong-po.pdf");

        assertThat(doc.get("status").asText()).isEqualTo("NEEDS_REVIEW");
        assertThat(doc.get("poMatch").get("poNumber").asText()).isEqualTo("PO-450099003");
        assertThat(doc.get("poMatch").get("deltas")).isNotEmpty();
    }

    @Test
    void aDamagedPdfFailsWithAReasonAndCanBeRetried() throws Exception {
        byte[] broken = ("%PDF-1.4 broken " + System.nanoTime()).getBytes(StandardCharsets.US_ASCII);
        JsonNode doc = upload(new MockMultipartFile("file", "broken.pdf", "application/pdf", broken));

        assertThat(doc.get("status").asText()).isEqualTo("FAILED");
        assertThat(doc.get("failureReason").asText()).isNotBlank();

        mvc.perform(post("/api/v1/documents/" + doc.get("id").asText() + "/reprocess").header(TENANT, "acme-de"))
                .andExpect(status().isAccepted());
        assertThat(waitForFinish(doc.get("id").asText()).get("status").asText()).isEqualTo("FAILED");
    }

    @Test
    void onlyFailedDocumentsCanBeReprocessed() throws Exception {
        JsonNode doc = uploadAndWait("sample-invoice-clean.pdf");   // deduplicated: same document as above
        mvc.perform(post("/api/v1/documents/" + doc.get("id").asText() + "/reprocess").header(TENANT, "acme-de"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status", is(409)));
    }

    // ---- helpers ---------------------------------------------------------------

    private JsonNode uploadAndWait(String sample) throws Exception {
        byte[] bytes;
        try (InputStream in = getClass().getResourceAsStream("/samples/" + sample)) {
            bytes = in.readAllBytes();
        }
        return upload(new MockMultipartFile("file", sample, "application/pdf", bytes));
    }

    private JsonNode upload(MockMultipartFile file) throws Exception {
        String body = mvc.perform(multipart("/api/v1/documents").file(file).param("docType", "INVOICE")
                        .header(TENANT, "acme-de"))
                .andReturn().getResponse().getContentAsString();
        return waitForFinish(json.readTree(body).get("id").asText());
    }

    /** The pipeline runs in the background: poll until it has finished (max 20 s). */
    private JsonNode waitForFinish(String id) throws Exception {
        long deadline = System.currentTimeMillis() + 20_000;
        JsonNode doc;
        do {
            Thread.sleep(150);
            String body = mvc.perform(get("/api/v1/documents/" + id).header(TENANT, "acme-de"))
                    .andReturn().getResponse().getContentAsString();
            doc = json.readTree(body);
        } while (!FINISHED.contains(doc.get("status").asText()) && System.currentTimeMillis() < deadline);
        return doc;
    }

    private static JsonNode field(JsonNode doc, String key) {
        for (JsonNode f : doc.get("fields")) {
            if (f.get("key").asText().equals(key)) {
                return f;
            }
        }
        throw new AssertionError("No field " + key);
    }
}
