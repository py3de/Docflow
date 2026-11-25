package com.docflow.admin;

import com.docflow.common.NotFoundException;
import com.docflow.config.DocFlowProperties;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Operator-only actions, e.g. removing a document that should never have been uploaded.
 * Switched off unless DOCFLOW_ADMIN_TOKEN is set; a wrong or missing token gets the same 404 as "off",
 * so the endpoint does not reveal that it exists.
 */
@RestController
@RequestMapping("/api/v1/admin")
public class AdminController {

    private final DocFlowProperties.Admin admin;
    private final AdminService service;

    public AdminController(DocFlowProperties properties, AdminService service) {
        this.admin = properties.admin();
        this.service = service;
    }

    /** Deletes a document of any tenant with everything that belongs to it, including the stored file. */
    @DeleteMapping("/documents/{id}")
    public ResponseEntity<Map<String, Object>> deleteDocument(
            @RequestHeader(name = "X-Admin-Token", required = false) String token, @PathVariable String id) {
        requireToken(token);
        return ResponseEntity.ok(service.deleteDocument(id));
    }

    private void requireToken(String token) {
        boolean ok = admin != null && admin.enabled() && token != null
                && MessageDigest.isEqual(admin.token().getBytes(StandardCharsets.UTF_8), token.getBytes(StandardCharsets.UTF_8));
        if (!ok) {
            throw new NotFoundException("Not found.");
        }
    }
}
