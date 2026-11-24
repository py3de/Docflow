package com.docflow.document;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** Remembers that a request with this Idempotency-Key was already applied. */
@Entity
@Table(name = "idempotency_record")
public class IdempotencyRecord {

    @Id
    @Column(name = "idempotency_key")
    private String key;

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @Column(name = "document_id", nullable = false)
    private String documentId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected IdempotencyRecord() {
        // for JPA
    }

    public IdempotencyRecord(String key, String tenantId, String documentId, Instant createdAt) {
        this.key = key;
        this.tenantId = tenantId;
        this.documentId = documentId;
        this.createdAt = createdAt;
    }

    public String getKey() { return key; }
    public String getTenantId() { return tenantId; }
    public String getDocumentId() { return documentId; }
    public Instant getCreatedAt() { return createdAt; }
}
