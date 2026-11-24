package com.docflow.document;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** A value a human reviewer changed. These rows become new labelled examples for evaluation. */
@Entity
@Table(name = "field_correction")
public class FieldCorrection {

    @Id
    private String id;

    @Column(name = "document_id", nullable = false)
    private String documentId;

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @Column(name = "field_key", nullable = false)
    private String fieldKey;

    @Column(name = "old_value", nullable = false)
    private String oldValue;

    @Column(name = "new_value", nullable = false)
    private String newValue;

    @Column(name = "corrected_at", nullable = false)
    private Instant correctedAt;

    protected FieldCorrection() {
        // for JPA
    }

    public FieldCorrection(String id, String documentId, String tenantId, String fieldKey,
                           String oldValue, String newValue, Instant correctedAt) {
        this.id = id;
        this.documentId = documentId;
        this.tenantId = tenantId;
        this.fieldKey = fieldKey;
        this.oldValue = oldValue;
        this.newValue = newValue;
        this.correctedAt = correctedAt;
    }

    public String getId() { return id; }
    public String getDocumentId() { return documentId; }
    public String getTenantId() { return tenantId; }
    public String getFieldKey() { return fieldKey; }
    public String getOldValue() { return oldValue; }
    public String getNewValue() { return newValue; }
    public Instant getCorrectedAt() { return correctedAt; }
}
