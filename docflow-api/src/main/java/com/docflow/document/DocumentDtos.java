package com.docflow.document;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * API contract. Each record matches a type in the UI's src/types.ts one-to-one,
 * so the React app can switch from mock data to this API without changes.
 */
public final class DocumentDtos {

    private DocumentDtos() {
    }

    /** Row in the documents table (GET /documents). No fields or line items: keeps the list fast. */
    public record DocumentSummary(
            String id, String tenantId, String fileName, DocType docType, DocStatus status,
            Instant receivedAt, String vendor, BigDecimal grossAmount, String currency,
            double avgConfidence, String model, int tokens, BigDecimal costEur) {
    }

    /** Full document (GET /documents/{id}). Matches DocumentJob in the UI. */
    public record DocumentJob(
            String id, String tenantId, String fileName, DocType docType, DocStatus status,
            Instant receivedAt, String vendor, BigDecimal grossAmount, String currency,
            double avgConfidence, String model, int tokens, BigDecimal costEur,
            List<Timing> timings, List<Field> fields, List<Line> lineItems, PoMatch poMatch, String traceId,
            String failureReason, Integer pageCount, boolean hasOriginal) {
    }

    public record Timing(PipelineStage stage, int ms) {
    }

    public record Box(double x, double y, double w, double h) {
    }

    public record Issue(String rule, Severity severity, String message) {
    }

    public record Field(String key, String label, String value, String sourceText, double confidence,
                        Box box, List<Issue> issues, boolean corrected) {
    }

    public record Line(String description, int quantity, BigDecimal unitPrice, BigDecimal total) {
    }

    public record PoMatch(String poNumber, String vendor, BigDecimal poAmount, double score, List<String> deltas) {
    }

    public enum Decision { APPROVE, REJECT }

    public record FieldUpdate(@NotBlank String key, @NotNull @Size(max = 500) String value) {
    }

    /** Body of POST /documents/{id}/review. */
    public record ReviewRequest(@NotNull Decision decision, @NotNull @Valid List<FieldUpdate> fields) {
    }
}
