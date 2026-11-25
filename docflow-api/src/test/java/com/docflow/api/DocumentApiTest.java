package com.docflow.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.docflow.document.DocStatus;
import com.docflow.document.DocumentEntity;
import com.docflow.document.DocumentRepository;
import com.docflow.document.FieldCorrectionRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Full HTTP tests: real Spring context, real database (H2), real Flyway schema, seeded demo data.
 * Each test picks its own not-yet-reviewed document, so the tests don't depend on their order.
 */
@SpringBootTest(properties = {
        "docflow.demo-data.days=3",
        "docflow.storage.root=${java.io.tmpdir}/docflow-test-uploads"
})
@AutoConfigureMockMvc
class DocumentApiTest {

    private static final String TENANT = "X-Tenant-Id";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired DocumentRepository documents;
    @Autowired FieldCorrectionRepository corrections;

    // ---- reading ---------------------------------------------------------------

    @Test
    void listReturnsOnlyTheTenantsDocumentsNewestFirst() throws Exception {
        String body = mvc.perform(get("/api/v1/documents?size=20&sort=receivedAt,desc").header(TENANT, "acme-de"))
                .andExpect(status().isOk())
                .andExpect(header().exists("X-Total-Count"))
                .andExpect(jsonPath("$.length()", is(20)))
                .andExpect(jsonPath("$[*].tenantId", everyItem(is("acme-de"))))
                .andReturn().getResponse().getContentAsString();

        List<Instant> times = new ArrayList<>();
        json.readTree(body).forEach(n -> times.add(Instant.parse(n.get("receivedAt").asText())));
        assertThat(times).isSortedAccordingTo(Comparator.reverseOrder());
    }

    @Test
    void missingTenantHeaderIsABadRequestProblem() throws Exception {
        mvc.perform(get("/api/v1/documents"))
                .andExpect(status().isBadRequest())
                .andExpect(header().string("Content-Type", containsString("application/problem+json")))
                .andExpect(jsonPath("$.detail", containsString("X-Tenant-Id")));
    }

    @Test
    void aTenantCannotSeeAnotherTenantsDocument() throws Exception {
        String globexId = documents.findAll().stream()
                .filter(d -> d.getTenantId().equals("globex-eu")).findFirst().orElseThrow().getId();

        mvc.perform(get("/api/v1/documents/" + globexId).header(TENANT, "acme-de"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type", endsWith("/not-found")));
        mvc.perform(get("/api/v1/documents/" + globexId).header(TENANT, "globex-eu"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(globexId)));
    }

    @Test
    void documentDetailHasFieldsBoxesAndIssues() throws Exception {
        String id = unreviewedDocumentId();
        mvc.perform(get("/api/v1/documents/" + id).header(TENANT, "acme-de"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("NEEDS_REVIEW")))
                .andExpect(jsonPath("$.fields.length()", is(9)))
                .andExpect(jsonPath("$.fields[0].box.x").isNumber())
                .andExpect(jsonPath("$.timings.length()", is(5)));

        List<String> severities = new ArrayList<>();
        fetch(id).get("fields").forEach(f -> f.get("issues").forEach(i -> severities.add(i.get("severity").asText())));
        assertThat(severities).isNotEmpty().allMatch(s -> s.equals("warning") || s.equals("critical"));
    }

    // ---- review ----------------------------------------------------------------

    @Test
    void approvingWithCorrectionsStoresThemAndBlocksASecondReview() throws Exception {
        String id = unreviewedDocumentId();
        JsonNode doc = fetch(id);
        ObjectNode body = reviewBody("APPROVE", doc, true);

        mvc.perform(post("/api/v1/documents/" + id + "/review").header(TENANT, "acme-de")
                        .contentType(MediaType.APPLICATION_JSON).content(body.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("APPROVED")))
                .andExpect(jsonPath("$.fields[?(@.corrected == true)]").isNotEmpty());

        assertThat(corrections.findByDocumentId(id)).isNotEmpty();

        mvc.perform(post("/api/v1/documents/" + id + "/review").header(TENANT, "acme-de")
                        .contentType(MediaType.APPLICATION_JSON).content(body.toString()))
                .andExpect(status().isConflict());
    }

    @Test
    void approvingWhileIssuesAreStillOpenIsRejected() throws Exception {
        String id = unreviewedDocumentId();
        String body = """
                {"decision":"APPROVE","fields":[]}""";
        mvc.perform(post("/api/v1/documents/" + id + "/review").header(TENANT, "acme-de")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail", containsString("open validation issues")));
        assertThat(documents.findById(id).orElseThrow().getStatus()).isEqualTo(DocStatus.NEEDS_REVIEW);
    }

    @Test
    void aRetriedReviewWithTheSameIdempotencyKeyIsAppliedOnce() throws Exception {
        String id = unreviewedDocumentId();
        JsonNode before = fetch(id);
        int expectedCorrections = 0;
        for (JsonNode f : before.get("fields")) {
            if (f.hasNonNull("sourceText") && !f.get("sourceText").asText().equals(f.get("value").asText())) {
                expectedCorrections++;
            }
        }
        ObjectNode body = reviewBody("REJECT", before, true);

        for (int attempt = 0; attempt < 2; attempt++) {
            mvc.perform(post("/api/v1/documents/" + id + "/review").header(TENANT, "acme-de")
                            .header("Idempotency-Key", "test-key-" + id)
                            .contentType(MediaType.APPLICATION_JSON).content(body.toString()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status", is("REJECTED")));
        }
        assertThat(expectedCorrections).isPositive();
        assertThat(corrections.findByDocumentId(id)).hasSize(expectedCorrections);
    }

    @Test
    void unknownFieldKeyIsUnprocessable() throws Exception {
        String id = unreviewedDocumentId();
        String body = """
                {"decision":"REJECT","fields":[{"key":"iban","value":"x"}]}""";
        mvc.perform(post("/api/v1/documents/" + id + "/review").header(TENANT, "acme-de")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnprocessableEntity());
    }

    // ---- upload ----------------------------------------------------------------

    @Test
    void uploadingTheSameFileTwiceReturnsTheSameDocument() throws Exception {
        MockMultipartFile pdf = new MockMultipartFile("file", "invoice.pdf", "application/pdf",
                ("%PDF-1.7 test " + System.nanoTime()).getBytes(StandardCharsets.US_ASCII));

        String first = mvc.perform(multipart("/api/v1/documents").file(pdf).param("docType", "INVOICE")
                        .header(TENANT, "acme-de"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", containsString("/api/v1/documents/doc_")))
                .andExpect(jsonPath("$.status", is("RECEIVED")))
                .andReturn().getResponse().getContentAsString();
        String id = json.readTree(first).get("id").asText();

        mvc.perform(multipart("/api/v1/documents").file(pdf).header(TENANT, "acme-de"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(id)));
    }

    @Test
    void uploadingSomethingThatIsNotADocumentIsRefused() throws Exception {
        MockMultipartFile fake = new MockMultipartFile("file", "invoice.pdf", "application/pdf",
                "definitely not a pdf".getBytes(StandardCharsets.US_ASCII));
        mvc.perform(multipart("/api/v1/documents").file(fake).header(TENANT, "acme-de"))
                .andExpect(status().isUnsupportedMediaType());
    }

    // ---- metrics & evals -------------------------------------------------------

    @Test
    void overviewMetricsAreComputedFromTheDatabase() throws Exception {
        mvc.perform(get("/api/v1/metrics/overview").header(TENANT, "acme-de"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.daily.length()", is(14)))
                .andExpect(jsonPath("$.stageP95.length()", is(5)))
                .andExpect(jsonPath("$.processed7d").isNumber())
                .andExpect(jsonPath("$.straightThroughRate").isNumber())
                .andExpect(jsonPath("$.reviewQueue").isNumber());
    }

    @Test
    void evalRunsAreNewestFirst() throws Exception {
        String body = mvc.perform(get("/api/v1/evals?limit=5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()", is(5)))
                .andExpect(jsonPath("$[0].fieldF1.length()", is(9)))
                .andReturn().getResponse().getContentAsString();
        List<Instant> times = new ArrayList<>();
        json.readTree(body).forEach(n -> times.add(Instant.parse(n.get("startedAt").asText())));
        assertThat(times).isSortedAccordingTo(Comparator.reverseOrder());
    }

    // ---- helpers ---------------------------------------------------------------

    /**
     * A seeded NEEDS_REVIEW document of acme-de that no test has touched yet.
     * Seeded documents have no stored file; uploads from the pipeline tests are left out.
     */
    private String unreviewedDocumentId() {
        return documents.findAll().stream()
                .filter(d -> d.getTenantId().equals("acme-de") && d.getStatus() == DocStatus.NEEDS_REVIEW
                        && d.getStoragePath() == null)
                .map(DocumentEntity::getId)
                .sorted()
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Seed data has no review documents"));
    }

    private JsonNode fetch(String id) throws Exception {
        String body = mvc.perform(get("/api/v1/documents/" + id).header(TENANT, "acme-de"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return json.readTree(body);
    }

    /** Builds a review body that keeps every value, or replaces it with the page text when usePageValues. */
    private ObjectNode reviewBody(String decision, JsonNode doc, boolean usePageValues) {
        ObjectNode body = json.createObjectNode().put("decision", decision);
        ArrayNode fields = body.putArray("fields");
        doc.get("fields").forEach(f -> {
            String value = usePageValues && f.hasNonNull("sourceText") ? f.get("sourceText").asText() : f.get("value").asText();
            fields.addObject().put("key", f.get("key").asText()).put("value", value);
        });
        return body;
    }
}
