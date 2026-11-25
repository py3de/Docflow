package com.docflow.pipeline;

import com.docflow.common.Ids;
import com.docflow.config.DocFlowProperties;
import com.docflow.document.BoundingBox;
import com.docflow.document.DocStatus;
import com.docflow.document.DocumentEntity;
import com.docflow.document.DocumentRepository;
import com.docflow.document.ExtractedFieldEntity;
import com.docflow.document.LineItem;
import com.docflow.document.PipelineStage;
import com.docflow.document.StageTiming;
import com.docflow.document.ValidationIssue;
import com.docflow.document.Severity;
import com.docflow.pipeline.FieldCatalog.FieldDef;
import com.docflow.pipeline.extract.ExtractedInvoice;
import com.docflow.pipeline.extract.ExtractedInvoice.FieldGuess;
import com.docflow.pipeline.extract.ExtractedInvoice.LineGuess;
import com.docflow.pipeline.extract.ExtractionException;
import com.docflow.pipeline.extract.InvoiceExtractor;
import com.docflow.pipeline.po.PoMatcher;
import com.docflow.storage.FileStorage;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The extraction pipeline for one document:
 * <pre>
 *   text (PDFBox) → redact → LLM extraction → locate on page → validate → match PO → route
 * </pre>
 * Each status change is its own short transaction, so the UI can follow the progress.
 * Any failure ends in FAILED with a reason the user can read; nothing is left half-written.
 */
@Service
public class DocumentPipeline {

    private static final Logger log = LoggerFactory.getLogger(DocumentPipeline.class);
    private static final int MAX_ATTEMPTS = 3;

    private final DocumentRepository documents;
    private final FileStorage storage;
    private final PdfTextExtractor pdfText;
    private final InvoiceExtractor extractor;
    private final PoMatcher poMatcher;
    private final InvoiceValidator validator;
    private final TransactionTemplate tx;
    private final Clock clock;
    private final double reviewThreshold;
    private final double inputPrice;
    private final double outputPrice;

    public DocumentPipeline(DocumentRepository documents, FileStorage storage, PdfTextExtractor pdfText,
                            InvoiceExtractor extractor, PoMatcher poMatcher, TransactionTemplate tx, Clock clock,
                            DocFlowProperties properties) {
        this.documents = documents;
        this.storage = storage;
        this.pdfText = pdfText;
        this.extractor = extractor;
        this.poMatcher = poMatcher;
        this.tx = tx;
        this.clock = clock;
        this.validator = new InvoiceValidator(properties.pipeline().poNumberPattern());
        this.reviewThreshold = properties.pipeline().reviewThreshold();
        this.inputPrice = properties.llm().inputPricePerMillion();
        this.outputPrice = properties.llm().outputPricePerMillion();
    }

    public void process(String documentId) {
        // 1. Claim the document and clear what an earlier run left.
        Optional<Snapshot> claimed = tx.execute(s -> documents.findById(documentId)
                .filter(d -> d.getStatus() == DocStatus.RECEIVED || d.getStatus() == DocStatus.FAILED)
                .map(d -> {
                    d.startProcessing();
                    return new Snapshot(d.getTenantId(), d.getStoragePath());
                }));
        if (claimed == null || claimed.isEmpty()) {
            log.info("Document {} is not waiting for processing; skipped.", documentId);
            return;
        }
        Snapshot doc = claimed.get();
        List<StageTiming> timings = new ArrayList<>();

        try {
            if (doc.storagePath() == null || !doc.storagePath().toLowerCase().endsWith(".pdf")) {
                throw new UnreadableDocumentException(
                        "Images and scans need OCR, which is not supported yet. Please upload a digital PDF.", null);
            }

            // 2. Text and word positions
            long t = System.nanoTime();
            PageText page = pdfText.extract(storage.read(doc.storagePath()));
            timings.add(new StageTiming(PipelineStage.OCR, ms(t)));
            if (page.isEmpty()) {
                throw new UnreadableDocumentException(
                        "This PDF has no text layer (probably a scan). OCR support is planned.", null);
            }

            // 3. LLM extraction on redacted text
            t = System.nanoTime();
            ExtractedInvoice extracted = extractWithRetry(Redactor.redact(page.text()));
            timings.add(new StageTiming(PipelineStage.LLM_EXTRACTION, ms(t)));
            setStatus(documentId, DocStatus.VALIDATING, timings);

            // 4. Locate on page, then validate
            t = System.nanoTime();
            List<ExtractedFieldEntity> fields = new ArrayList<>();
            Map<String, String> values = new LinkedHashMap<>();
            for (FieldDef def : FieldCatalog.FIELDS) {
                FieldGuess guess = extracted.fields().getOrDefault(def.key(), new FieldGuess(null, 0));
                values.put(def.key(), guess.value());
                double confidence = guess.confidence();
                BoundingBox box = null;
                String sourceText = null;
                if (guess.value() != null) {
                    Optional<FieldLocator.Location> where = FieldLocator.locate(def, guess.value(), page);
                    if (where.isPresent()) {
                        box = where.get().box();
                        sourceText = where.get().pageText();
                    } else {
                        // Returned by the model but not printed on page 1: don't trust it blindly.
                        confidence = Math.min(confidence, 0.6);
                    }
                }
                fields.add(new ExtractedFieldEntity(Ids.uuid(), def.key(), def.label(),
                        guess.value() == null ? "" : truncate(guess.value()), sourceText == null ? null : truncate(sourceText), round2(confidence), box));
            }
            List<LineItem> lines = toLineItems(extracted.lineItems());
            Map<String, List<ValidationIssue>> issues =
                    validator.validate(values, lines, LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC));
            timings.add(new StageTiming(PipelineStage.VALIDATION, ms(t)));

            // 5. Purchase order
            t = System.nanoTime();
            BigDecimal gross = Amounts.parse(values.get("gross_amount")).orElse(null);
            Optional<PoMatcher.Result> po = poMatcher.match(doc.tenantId(), values.get("po_number"), values.get("vendor_name"), gross);
            if (po.isEmpty()) {
                issues.computeIfAbsent("po_number", k -> new ArrayList<>()).add(new ValidationIssue("PO_NOT_FOUND",
                        Severity.WARNING, "No matching purchase order found."));
            }
            timings.add(new StageTiming(PipelineStage.PO_MATCHING, ms(t)));

            // 6. Route and save
            for (ExtractedFieldEntity f : fields) {
                issues.getOrDefault(f.getKey(), List.of()).forEach(f::addIssue);
            }
            boolean confident = fields.stream().allMatch(f -> f.getValue().isEmpty() || f.getConfidence() >= reviewThreshold);
            boolean clean = issues.values().stream().allMatch(List::isEmpty);
            boolean poOk = po.isPresent() && po.get().deltas().isEmpty() && po.get().score() >= 0.9;
            DocStatus outcome = confident && clean && poOk ? DocStatus.MATCHED : DocStatus.NEEDS_REVIEW;

            String currency = extracted.currency() == null ? "EUR" : extracted.currency().toUpperCase();
            tx.executeWithoutResult(s -> documents.findById(documentId).ifPresent(d -> {
                timings.stream().filter(x -> d.getTimings().stream().noneMatch(e -> e.getStage() == x.getStage()))
                        .forEach(d::addTiming);
                fields.forEach(d::addField);
                lines.forEach(d::addLineItem);
                d.recordPageCount(page.pageCount());
                d.recordExtraction(orBlank(values.get("vendor_name")), gross == null ? BigDecimal.ZERO : gross, currency,
                        extracted.model(), extracted.inputTokens() + extracted.outputTokens(),
                        cost(extracted.inputTokens(), extracted.outputTokens()), Ids.traceId());
                po.ifPresent(m -> d.recordPoMatch(m.po().getPoNumber(), m.po().getVendor(), m.po().getAmount(),
                        m.score(), m.deltas()));
                d.changeStatus(outcome);
            }));
            log.info("Document {} processed with {}: {}", documentId, extractor.describe(), outcome);

        } catch (UnreadableDocumentException | ExtractionException e) {
            fail(documentId, e.getMessage(), timings);
        } catch (RuntimeException e) {
            log.error("Pipeline failed for document {}", documentId, e);
            fail(documentId, "Unexpected error: " + e.getClass().getSimpleName(), timings);
        }
    }

    // ---- stages helpers -----------------------------------------------------------

    /** Retries rate limits, timeouts and server errors with a growing pause (1 s, 2 s). */
    private ExtractedInvoice extractWithRetry(String text) {
        for (int attempt = 1; ; attempt++) {
            try {
                return extractor.extract(text);
            } catch (ExtractionException e) {
                if (!e.isRetryable() || attempt >= MAX_ATTEMPTS) {
                    throw e;
                }
                log.warn("Extraction attempt {} failed ({}), retrying", attempt, e.getMessage());
                try {
                    Thread.sleep(1000L * attempt);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw e;
                }
            }
        }
    }

    private void setStatus(String id, DocStatus status, List<StageTiming> timings) {
        tx.executeWithoutResult(s -> documents.findById(id).ifPresent(d -> {
            timings.stream().filter(x -> d.getTimings().stream().noneMatch(e -> e.getStage() == x.getStage()))
                    .forEach(d::addTiming);
            d.changeStatus(status);
        }));
    }

    private void fail(String id, String reason, List<StageTiming> timings) {
        log.warn("Document {} failed: {}", id, reason);
        tx.executeWithoutResult(s -> documents.findById(id).ifPresent(d -> {
            timings.stream().filter(x -> d.getTimings().stream().noneMatch(e -> e.getStage() == x.getStage()))
                    .forEach(d::addTiming);
            d.fail(reason);
        }));
    }

    private static List<LineItem> toLineItems(List<LineGuess> guesses) {
        List<LineItem> lines = new ArrayList<>();
        for (LineGuess g : guesses) {
            if (g.description() == null || g.description().isBlank()) continue;
            BigDecimal unit = g.unitPrice() == null ? BigDecimal.ZERO : g.unitPrice().setScale(2, RoundingMode.HALF_UP);
            BigDecimal total = g.total() != null ? g.total().setScale(2, RoundingMode.HALF_UP)
                    : g.quantity() != null ? unit.multiply(g.quantity()).setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO;
            int qty = g.quantity() == null ? 1 : g.quantity().setScale(0, RoundingMode.HALF_UP).intValue();
            String desc = g.description().length() > 300 ? g.description().substring(0, 300) : g.description();
            lines.add(new LineItem(desc, qty, unit, total));
        }
        return lines;
    }

    private BigDecimal cost(int inputTokens, int outputTokens) {
        double eur = inputTokens * inputPrice / 1_000_000 + outputTokens * outputPrice / 1_000_000;
        return BigDecimal.valueOf(eur).setScale(6, RoundingMode.HALF_UP);
    }

    private static int ms(long startNanos) {
        return (int) Math.max(1, (System.nanoTime() - startNanos) / 1_000_000);
    }

    private static double round2(double v) {
        return Math.round(v * 100) / 100.0;
    }

    /** Column limit is 500 characters; a model can return more. */
    private static String truncate(String s) {
        return s.length() > 500 ? s.substring(0, 500) : s;
    }

    private static String orBlank(String s) {
        return s == null || s.isBlank() ? "Unknown vendor" : (s.length() > 200 ? s.substring(0, 200) : s);
    }

    private record Snapshot(String tenantId, String storagePath) {
    }
}
