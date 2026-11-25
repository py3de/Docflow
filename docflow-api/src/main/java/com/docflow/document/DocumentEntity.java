package com.docflow.document;

import jakarta.persistence.CascadeType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Aggregate root: one uploaded document and everything the pipeline produced for it.
 * Fields, line items and timings are only changed through this class.
 */
@Entity
@Table(name = "document")
public class DocumentEntity {

    @Id
    private String id;

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @Column(name = "file_name", nullable = false)
    private String fileName;

    @Enumerated(EnumType.STRING)
    @Column(name = "doc_type", nullable = false)
    private DocType docType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DocStatus status;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    @Column(nullable = false)
    private String vendor;

    @Column(name = "gross_amount", nullable = false)
    private BigDecimal grossAmount;

    @Column(nullable = false)
    private String currency;

    @Column(name = "avg_confidence", nullable = false)
    private double avgConfidence;

    @Column(nullable = false)
    private String model;

    @Column(nullable = false)
    private int tokens;

    @Column(name = "cost_eur", nullable = false)
    private BigDecimal costEur;

    /** Sum of all stage timings; stored so metrics don't need to join every timing row. */
    @Column(name = "total_latency_ms", nullable = false)
    private int totalLatencyMs;

    /** True when at least one field went to a human; used for the straight-through rate. */
    @Column(name = "review_required", nullable = false)
    private boolean reviewRequired;

    @Column(name = "trace_id", nullable = false)
    private String traceId;

    @Column(name = "sha256")
    private String sha256;

    @Column(name = "storage_path")
    private String storagePath;

    @Column(name = "po_number")
    private String poNumber;

    @Column(name = "po_vendor")
    private String poVendor;

    @Column(name = "po_amount")
    private BigDecimal poAmount;

    @Column(name = "po_score")
    private Double poScore;

    /** Why processing failed, shown to the user (e.g. "This PDF has no text layer"). */
    @Column(name = "failure_reason")
    private String failureReason;

    @Column(name = "page_count")
    private Integer pageCount;

    /**
     * Optimistic locking: two reviewers saving at once get a 409 instead of overwriting each other.
     * A wrapper type on purpose: null tells Spring Data the entity is new, so save() inserts
     * directly instead of SELECTing first (matters for bulk inserts with assigned IDs).
     */
    @Version
    private Long version;

    @OneToMany(mappedBy = "document", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("fieldOrder ASC")
    private List<ExtractedFieldEntity> fields = new ArrayList<>();

    @ElementCollection
    @CollectionTable(name = "document_line_item", joinColumns = @JoinColumn(name = "document_id"))
    @OrderColumn(name = "item_order")
    private List<LineItem> lineItems = new ArrayList<>();

    @ElementCollection
    @CollectionTable(name = "document_stage_timing", joinColumns = @JoinColumn(name = "document_id"))
    @OrderColumn(name = "item_order")
    private List<StageTiming> timings = new ArrayList<>();

    @ElementCollection
    @CollectionTable(name = "document_po_delta", joinColumns = @JoinColumn(name = "document_id"))
    @OrderColumn(name = "item_order")
    @Column(name = "delta", nullable = false)
    private List<String> poDeltas = new ArrayList<>();

    protected DocumentEntity() {
        // for JPA
    }

    public DocumentEntity(String id, String tenantId, String fileName, DocType docType, Instant receivedAt) {
        this.id = id;
        this.tenantId = tenantId;
        this.fileName = fileName;
        this.docType = docType;
        this.receivedAt = receivedAt;
        this.status = DocStatus.RECEIVED;
        this.vendor = "Pending extraction";
        this.grossAmount = BigDecimal.ZERO;
        this.currency = "EUR";
        this.model = "none";
        this.costEur = BigDecimal.ZERO;
        this.traceId = "";
    }

    // ---- behaviour --------------------------------------------------------

    public void addField(ExtractedFieldEntity field) {
        field.attachTo(this, fields.size());
        fields.add(field);
    }

    public void addLineItem(LineItem item) { lineItems.add(item); }

    public void addTiming(StageTiming timing) {
        timings.add(timing);
        totalLatencyMs += timing.getMs();
    }

    public void recordExtraction(String vendor, BigDecimal grossAmount, String currency, String model,
                                 int tokens, BigDecimal costEur, String traceId) {
        this.vendor = vendor;
        this.grossAmount = grossAmount;
        this.currency = currency;
        this.model = model;
        this.tokens = tokens;
        this.costEur = costEur;
        this.traceId = traceId;
        this.avgConfidence = fields.stream().mapToDouble(ExtractedFieldEntity::getConfidence).average().orElse(0);
    }

    public void recordPoMatch(String poNumber, String poVendor, BigDecimal poAmount, double score, List<String> deltas) {
        this.poNumber = poNumber;
        this.poVendor = poVendor;
        this.poAmount = poAmount;
        this.poScore = score;
        this.poDeltas.clear();
        this.poDeltas.addAll(deltas);
    }

    public void markStored(String sha256, String storagePath) {
        this.sha256 = sha256;
        this.storagePath = storagePath;
    }

    /**
     * Starts a (re)run of the pipeline: everything a previous run produced is removed,
     * only the upload (INGEST) timing is kept. Must be committed before new fields are added,
     * because each field key is unique per document.
     */
    public void startProcessing() {
        fields.clear();
        lineItems.clear();
        timings.removeIf(t -> t.getStage() != PipelineStage.INGEST);
        totalLatencyMs = timings.stream().mapToInt(StageTiming::getMs).sum();
        clearPoMatch();
        failureReason = null;
        reviewRequired = false;
        avgConfidence = 0;
        status = DocStatus.EXTRACTING;
    }

    public void clearPoMatch() {
        poNumber = null;
        poVendor = null;
        poAmount = null;
        poScore = null;
        poDeltas.clear();
    }

    public void fail(String reason) {
        String r = reason == null || reason.isBlank() ? "Processing failed." : reason;
        failureReason = r.length() > 500 ? r.substring(0, 497) + "..." : r;
        status = DocStatus.FAILED;
    }

    public void recordPageCount(int pages) {
        this.pageCount = pages;
    }

    /** True when the original is a PDF we can render for the review screen. */
    public boolean hasRenderableOriginal() {
        return storagePath != null && storagePath.toLowerCase().endsWith(".pdf");
    }

    public void changeStatus(DocStatus newStatus) {
        if (newStatus == DocStatus.NEEDS_REVIEW) {
            reviewRequired = true;
        }
        this.status = newStatus;
    }

    // ---- getters ----------------------------------------------------------

    public String getId() { return id; }
    public String getTenantId() { return tenantId; }
    public String getFileName() { return fileName; }
    public DocType getDocType() { return docType; }
    public DocStatus getStatus() { return status; }
    public Instant getReceivedAt() { return receivedAt; }
    public String getVendor() { return vendor; }
    public BigDecimal getGrossAmount() { return grossAmount; }
    public String getCurrency() { return currency; }
    public double getAvgConfidence() { return avgConfidence; }
    public String getModel() { return model; }
    public int getTokens() { return tokens; }
    public BigDecimal getCostEur() { return costEur; }
    public int getTotalLatencyMs() { return totalLatencyMs; }
    public boolean isReviewRequired() { return reviewRequired; }
    public String getTraceId() { return traceId; }
    public String getSha256() { return sha256; }
    public String getStoragePath() { return storagePath; }
    public String getPoNumber() { return poNumber; }
    public String getPoVendor() { return poVendor; }
    public BigDecimal getPoAmount() { return poAmount; }
    public Double getPoScore() { return poScore; }
    public String getFailureReason() { return failureReason; }
    public Integer getPageCount() { return pageCount; }
    public Long getVersion() { return version; }
    public List<ExtractedFieldEntity> getFields() { return fields; }
    public List<LineItem> getLineItems() { return lineItems; }
    public List<StageTiming> getTimings() { return timings; }
    public List<String> getPoDeltas() { return poDeltas; }
}
