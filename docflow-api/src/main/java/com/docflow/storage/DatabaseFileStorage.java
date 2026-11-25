package com.docflow.storage;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Files stored in the database. Used in the cloud, where an app instance's own disk is wiped on every
 * restart or redeploy. Fine for a demo's invoice volume; at scale this becomes an object store (S3 / BTP Object Store)
 * behind the same FileStorage interface.
 */
@Component
@ConditionalOnProperty(prefix = "docflow.storage", name = "type", havingValue = "database")
public class DatabaseFileStorage implements FileStorage {

    private final JdbcTemplate jdbc;
    private final Clock clock;

    public DatabaseFileStorage(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Override
    public String store(String tenantId, String sha256, String extension, byte[] content) {
        String path = tenantId + "/" + sha256 + "." + extension;
        Integer existing = jdbc.queryForObject("SELECT COUNT(*) FROM stored_file WHERE storage_path = ?", Integer.class, path);
        if (existing == null || existing == 0) {
            jdbc.update("INSERT INTO stored_file (storage_path, tenant_id, content, size_bytes, created_at) VALUES (?, ?, ?, ?, ?)",
                    path, tenantId, content, content.length, OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC));
        }
        return path;
    }

    @Override
    public byte[] read(String storagePath) {
        byte[] content = jdbc.query("SELECT content FROM stored_file WHERE storage_path = ?",
                rs -> rs.next() ? rs.getBytes(1) : null, storagePath);
        if (content == null) {
            throw new IllegalStateException("Stored file not found: " + storagePath);
        }
        return content;
    }
}
