package com.docflow.document;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DocumentRepository extends JpaRepository<DocumentEntity, String> {

    /** Tenant isolation: every lookup is scoped to the caller's tenant. */
    Optional<DocumentEntity> findByIdAndTenantId(String id, String tenantId);

    Page<DocumentEntity> findByTenantId(String tenantId, Pageable pageable);

    Optional<DocumentEntity> findByTenantIdAndSha256(String tenantId, String sha256);

    long countByTenantIdAndStatus(String tenantId, DocStatus status);

    /** Lightweight rows for the metrics calculation (no fields or line items loaded). */
    @Query("""
            select new com.docflow.document.DocumentMetricRow(
                d.receivedAt, d.reviewRequired, d.avgConfidence, d.costEur, d.totalLatencyMs, d.status)
            from DocumentEntity d
            where d.tenantId = :tenantId and d.receivedAt >= :from and d.receivedAt < :to
            """)
    List<DocumentMetricRow> findMetricRows(@Param("tenantId") String tenantId,
                                           @Param("from") Instant from,
                                           @Param("to") Instant to);

    @Query("""
            select new com.docflow.document.StageSample(t.stage, t.ms)
            from DocumentEntity d join d.timings t
            where d.tenantId = :tenantId and d.receivedAt >= :from and d.receivedAt < :to
            """)
    List<StageSample> findStageSamples(@Param("tenantId") String tenantId,
                                       @Param("from") Instant from,
                                       @Param("to") Instant to);
}
