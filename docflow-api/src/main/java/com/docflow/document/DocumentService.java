package com.docflow.document;

import com.docflow.common.ConflictException;
import com.docflow.common.Ids;
import com.docflow.common.InvalidRequestException;
import com.docflow.common.NotFoundException;
import com.docflow.document.DocumentDtos.Decision;
import com.docflow.document.DocumentDtos.DocumentJob;
import com.docflow.document.DocumentDtos.DocumentSummary;
import com.docflow.document.DocumentDtos.FieldUpdate;
import com.docflow.document.DocumentDtos.ReviewRequest;
import com.docflow.storage.FileStorage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class DocumentService {

    private final DocumentRepository documents;
    private final FieldCorrectionRepository corrections;
    private final IdempotencyRecordRepository idempotency;
    private final FileStorage storage;
    private final Clock clock;

    public DocumentService(DocumentRepository documents, FieldCorrectionRepository corrections,
                           IdempotencyRecordRepository idempotency, FileStorage storage, Clock clock) {
        this.documents = documents;
        this.corrections = corrections;
        this.idempotency = idempotency;
        this.storage = storage;
        this.clock = clock;
    }

    // ---- read ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public Page<DocumentSummary> list(String tenantId, Pageable pageable) {
        return documents.findByTenantId(tenantId, pageable).map(DocumentMapper::toSummary);
    }

    @Transactional(readOnly = true)
    public DocumentJob get(String tenantId, String id) {
        return DocumentMapper.toJob(load(tenantId, id));
    }

    // ---- review ----------------------------------------------------------------

    /**
     * Applies a human reviewer's decision.
     * <ul>
     *   <li>Only documents in NEEDS_REVIEW can be reviewed (409 otherwise).</li>
     *   <li>Every changed value is stored as a {@link FieldCorrection} for the evaluation set.</li>
     *   <li>With an Idempotency-Key, a retried request returns the first result instead of applying twice.</li>
     *   <li>@Version on the document turns two simultaneous reviews into a 409, not a lost update.</li>
     * </ul>
     */
    @Transactional
    public DocumentJob review(String tenantId, String id, ReviewRequest request, String idempotencyKey) {
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            Optional<IdempotencyRecord> seen = idempotency.findById(idempotencyKey);
            if (seen.isPresent()) {
                IdempotencyRecord record = seen.get();
                if (!record.getTenantId().equals(tenantId) || !record.getDocumentId().equals(id)) {
                    throw new ConflictException("This Idempotency-Key was already used for a different request.");
                }
                return get(tenantId, id);
            }
        }

        DocumentEntity doc = load(tenantId, id);
        if (doc.getStatus() != DocStatus.NEEDS_REVIEW) {
            throw new ConflictException("Document " + id + " is " + doc.getStatus() + " and cannot be reviewed.");
        }

        Map<String, ExtractedFieldEntity> byKey = doc.getFields().stream()
                .collect(Collectors.toMap(ExtractedFieldEntity::getKey, Function.identity()));
        for (FieldUpdate update : request.fields()) {
            if (!byKey.containsKey(update.key())) {
                throw new InvalidRequestException(HttpStatus.UNPROCESSABLE_ENTITY,
                        "Unknown field '" + update.key() + "' for document " + id + ".");
            }
        }

        for (FieldUpdate update : request.fields()) {
            ExtractedFieldEntity field = byKey.get(update.key());
            String oldValue = field.getValue();
            if (field.applyReview(update.value())) {
                corrections.save(new FieldCorrection(Ids.uuid(), id, tenantId, field.getKey(),
                        oldValue, update.value(), clock.instant()));
            }
        }

        if (request.decision() == Decision.APPROVE) {
            List<String> stillOpen = doc.getFields().stream()
                    .filter(f -> !f.getIssues().isEmpty())
                    .map(ExtractedFieldEntity::getLabel)
                    .toList();
            if (!stillOpen.isEmpty()) {
                throw new InvalidRequestException(HttpStatus.UNPROCESSABLE_ENTITY,
                        "Cannot approve: open validation issues on " + String.join(", ", stillOpen) + ".");
            }
            doc.changeStatus(DocStatus.APPROVED);
        } else {
            doc.changeStatus(DocStatus.REJECTED);
        }

        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            idempotency.save(new IdempotencyRecord(idempotencyKey, tenantId, id, clock.instant()));
        }
        return DocumentMapper.toJob(doc);
    }

    // ---- upload ----------------------------------------------------------------

    public record UploadResult(DocumentJob document, boolean created) {
    }

    /**
     * Stores an uploaded file and creates a RECEIVED document.
     * Uploading the same bytes again returns the existing document (deduplicated by SHA-256).
     * Extraction itself is the next pipeline step (step 2 of the project).
     */
    @Transactional
    public UploadResult upload(String tenantId, MultipartFile file, DocType docType) {
        if (file == null || file.isEmpty()) {
            throw new InvalidRequestException(HttpStatus.BAD_REQUEST, "The upload contains no file.");
        }
        byte[] bytes = readBytes(file);
        FileKind kind = FileKind.detect(bytes);
        if (kind == null) {
            throw new InvalidRequestException(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                    "Only PDF, PNG, JPEG and TIFF files are accepted.");
        }

        String sha256 = sha256(bytes);
        Optional<DocumentEntity> existing = documents.findByTenantIdAndSha256(tenantId, sha256);
        if (existing.isPresent()) {
            return new UploadResult(DocumentMapper.toJob(existing.get()), false);
        }

        long started = System.nanoTime();
        String path = storage.store(tenantId, sha256, kind.extension, bytes);
        DocumentEntity doc = new DocumentEntity(Ids.documentId(), tenantId, safeName(file.getOriginalFilename()),
                docType, clock.instant());
        doc.markStored(sha256, path);
        doc.addTiming(new StageTiming(PipelineStage.INGEST, (int) ((System.nanoTime() - started) / 1_000_000)));
        doc.recordExtraction("Pending extraction", BigDecimal.ZERO, "EUR", "none", 0, BigDecimal.ZERO, Ids.traceId());
        documents.save(doc);
        return new UploadResult(DocumentMapper.toJob(doc), true);
    }

    // ---- helpers ---------------------------------------------------------------

    private DocumentEntity load(String tenantId, String id) {
        // A document of another tenant is reported as "not found", never as "forbidden":
        // that way the API does not reveal that the ID exists.
        return documents.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new NotFoundException("Document " + id + " not found."));
    }

    private static byte[] readBytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String safeName(String original) {
        String name = original == null || original.isBlank() ? "upload" : original;
        name = name.replaceAll("[\\\\/]", "_").replaceAll("[^\\w.\\- ]", "");
        return name.length() > 200 ? name.substring(0, 200) : name;
    }

    /** Detects the real file type from its first bytes instead of trusting the client's Content-Type. */
    enum FileKind {
        PDF("pdf"), PNG("png"), JPEG("jpg"), TIFF("tif");

        final String extension;

        FileKind(String extension) {
            this.extension = extension;
        }

        static FileKind detect(byte[] b) {
            if (startsWith(b, 0x25, 0x50, 0x44, 0x46)) return PDF;               // %PDF
            if (startsWith(b, 0x89, 0x50, 0x4E, 0x47)) return PNG;               // .PNG
            if (startsWith(b, 0xFF, 0xD8, 0xFF)) return JPEG;
            if (startsWith(b, 0x49, 0x49, 0x2A, 0x00) || startsWith(b, 0x4D, 0x4D, 0x00, 0x2A)) return TIFF;
            return null;
        }

        private static boolean startsWith(byte[] b, int... magic) {
            if (b.length < magic.length) return false;
            for (int i = 0; i < magic.length; i++) {
                if ((b[i] & 0xFF) != magic[i]) return false;
            }
            return true;
        }
    }
}
