package com.docflow.document;

import com.docflow.common.Tenants;
import com.docflow.document.DocumentDtos.DocumentJob;
import com.docflow.document.DocumentDtos.DocumentSummary;
import com.docflow.document.DocumentDtos.ReviewRequest;
import com.docflow.document.DocumentService.UploadResult;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/documents")
public class DocumentController {

    private final DocumentService service;

    public DocumentController(DocumentService service) {
        this.service = service;
    }

    /**
     * GET /api/v1/documents?size=100&sort=receivedAt,desc
     * Returns a plain JSON array (what the UI expects); the total count is in X-Total-Count.
     */
    @GetMapping
    public ResponseEntity<List<DocumentSummary>> list(
            @RequestHeader(name = Tenants.HEADER, required = false) String tenantId,
            @PageableDefault(size = 50, sort = "receivedAt", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<DocumentSummary> page = service.list(Tenants.require(tenantId), pageable);
        return ResponseEntity.ok()
                .header("X-Total-Count", String.valueOf(page.getTotalElements()))
                .body(page.getContent());
    }

    @GetMapping("/{id}")
    public DocumentJob get(@RequestHeader(name = Tenants.HEADER, required = false) String tenantId,
                           @PathVariable String id) {
        return service.get(Tenants.require(tenantId), id);
    }

    /** Multipart upload: form fields "file" and "docType". 201 for a new document, 200 for a duplicate. */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<DocumentJob> upload(
            @RequestHeader(name = Tenants.HEADER, required = false) String tenantId,
            @RequestPart("file") MultipartFile file,
            @RequestParam(name = "docType", defaultValue = "INVOICE") DocType docType) {
        UploadResult result = service.upload(Tenants.require(tenantId), file, docType);
        if (!result.created()) {
            return ResponseEntity.ok(result.document());
        }
        return ResponseEntity.created(URI.create("/api/v1/documents/" + result.document().id()))
                .body(result.document());
    }

    /** Runs the extraction pipeline again for a FAILED document. 202: it runs in the background. */
    @PostMapping("/{id}/reprocess")
    public ResponseEntity<DocumentJob> reprocess(@RequestHeader(name = Tenants.HEADER, required = false) String tenantId,
                                                 @PathVariable String id) {
        return ResponseEntity.accepted().body(service.reprocess(Tenants.require(tenantId), id));
    }

    /** The original PDF's page as PNG, for the review screen. */
    @GetMapping(value = "/{id}/pages/{page}", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> page(@RequestHeader(name = Tenants.HEADER, required = false) String tenantId,
                                       @PathVariable String id, @PathVariable int page) {
        byte[] png = service.pageImage(Tenants.require(tenantId), id, page);
        return ResponseEntity.ok()
                .cacheControl(org.springframework.http.CacheControl.maxAge(java.time.Duration.ofHours(1)).cachePrivate())
                .body(png);
    }

    @PostMapping("/{id}/review")
    public DocumentJob review(@RequestHeader(name = Tenants.HEADER, required = false) String tenantId,
                              @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
                              @PathVariable String id,
                              @Valid @RequestBody ReviewRequest request) {
        return service.review(Tenants.require(tenantId), id, request, idempotencyKey);
    }
}
