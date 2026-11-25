package com.docflow.admin;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
        "docflow.admin.token=test-admin-token-1234567890",
        "docflow.demo-data.days=3",
        "docflow.storage.root=${java.io.tmpdir}/docflow-test-uploads"
})
@AutoConfigureMockMvc
class AdminControllerTest {

    private static final String TOKEN = "test-admin-token-1234567890";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    private String upload() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "private.pdf", "application/pdf",
                ("%PDF-1.4 private " + System.nanoTime()).getBytes(StandardCharsets.US_ASCII));
        String body = mvc.perform(multipart("/api/v1/documents").file(file).header("X-Tenant-Id", "acme-de"))
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("id").asText();
    }

    @Test
    void theRightTokenDeletesTheDocumentForGood() throws Exception {
        String id = upload();

        mvc.perform(delete("/api/v1/admin/documents/" + id).header("X-Admin-Token", TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deleted").value(id))
                .andExpect(jsonPath("$.storedFileDeleted").value(true));

        mvc.perform(get("/api/v1/documents/" + id).header("X-Tenant-Id", "acme-de"))
                .andExpect(status().isNotFound());
    }

    @Test
    void aWrongOrMissingTokenLooksLikeTheEndpointDoesNotExist() throws Exception {
        String id = upload();
        mvc.perform(delete("/api/v1/admin/documents/" + id).header("X-Admin-Token", "wrong-token-1234567890"))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/v1/admin/documents/" + id))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/documents/" + id).header("X-Tenant-Id", "acme-de"))
                .andExpect(status().isOk());
    }
}
