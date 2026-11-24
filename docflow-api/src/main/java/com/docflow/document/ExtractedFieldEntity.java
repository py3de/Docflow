package com.docflow.document;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.List;

/** One field the LLM extracted from a document, e.g. the invoice number. */
@Entity
@Table(name = "extracted_field")
public class ExtractedFieldEntity {

    @Id
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "document_id")
    private DocumentEntity document;

    @Column(name = "field_order", nullable = false)
    private int fieldOrder;

    @Column(name = "field_key", nullable = false)
    private String key;

    @Column(nullable = false)
    private String label;

    @Column(name = "field_value", nullable = false)
    private String value;

    /** The text as it appears on the page (OCR layer); may differ from what the model returned. */
    @Column(name = "source_text")
    private String sourceText;

    @Column(nullable = false)
    private double confidence;

    @Embedded
    private BoundingBox box;

    @Column(nullable = false)
    private boolean corrected;

    @ElementCollection
    @CollectionTable(name = "field_issue", joinColumns = @JoinColumn(name = "field_id"))
    @OrderColumn(name = "item_order")
    private List<ValidationIssue> issues = new ArrayList<>();

    protected ExtractedFieldEntity() {
        // for JPA
    }

    public ExtractedFieldEntity(String id, String key, String label, String value, String sourceText,
                                double confidence, BoundingBox box) {
        this.id = id;
        this.key = key;
        this.label = label;
        this.value = value;
        this.sourceText = sourceText;
        this.confidence = confidence;
        this.box = box;
    }

    /** Applies a reviewer's decision. Returns true when the value actually changed. */
    public boolean applyReview(String newValue) {
        boolean changed = !value.equals(newValue);
        if (changed) {
            value = newValue;
            corrected = true;
        }
        // A human has looked at the field: open rule failures are resolved either way.
        issues.clear();
        return changed;
    }

    void attachTo(DocumentEntity document, int order) {
        this.document = document;
        this.fieldOrder = order;
    }

    public void addIssue(ValidationIssue issue) { issues.add(issue); }

    public String getId() { return id; }
    public DocumentEntity getDocument() { return document; }
    public int getFieldOrder() { return fieldOrder; }
    public String getKey() { return key; }
    public String getLabel() { return label; }
    public String getValue() { return value; }
    public String getSourceText() { return sourceText; }
    public double getConfidence() { return confidence; }
    public BoundingBox getBox() { return box; }
    public boolean isCorrected() { return corrected; }
    public List<ValidationIssue> getIssues() { return issues; }
}
