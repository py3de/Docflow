package com.docflow.admin;

import com.docflow.common.NotFoundException;
import com.docflow.storage.FileStorage;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminService {

    private static final Logger log = LoggerFactory.getLogger(AdminService.class);

    private final JdbcTemplate jdbc;
    private final FileStorage storage;

    public AdminService(JdbcTemplate jdbc, FileStorage storage) {
        this.jdbc = jdbc;
        this.storage = storage;
    }

    /**
     * Removes the document row (fields, issues, line items, timings, PO differences and corrections go with it
     * through ON DELETE CASCADE), its idempotency records and the stored original, unless another document
     * points to the same file.
     */
    @Transactional
    public Map<String, Object> deleteDocument(String id) {
        List<String> paths = jdbc.queryForList("SELECT storage_path FROM document WHERE id = ?", String.class, id);
        if (paths.isEmpty()) {
            throw new NotFoundException("Document " + id + " not found.");
        }
        String path = paths.get(0);
        jdbc.update("DELETE FROM idempotency_record WHERE document_id = ?", id);
        int rows = jdbc.update("DELETE FROM document WHERE id = ?", id);
        boolean fileDeleted = false;
        if (path != null) {
            Integer others = jdbc.queryForObject("SELECT COUNT(*) FROM document WHERE storage_path = ?", Integer.class, path);
            if (others == null || others == 0) {
                storage.delete(path);
                fileDeleted = true;
            }
        }
        log.info("Admin deleted document {} (stored file deleted: {})", id, fileDeleted);
        return Map.of("deleted", id, "documentRows", rows, "storedFileDeleted", fileDeleted);
    }
}
