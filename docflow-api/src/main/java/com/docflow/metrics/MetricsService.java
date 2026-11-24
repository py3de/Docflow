package com.docflow.metrics;

import com.docflow.document.DocStatus;
import com.docflow.document.DocumentMetricRow;
import com.docflow.document.DocumentRepository;
import com.docflow.document.PipelineStage;
import com.docflow.document.StageSample;
import com.docflow.metrics.OverviewMetrics.DailyVolume;
import com.docflow.metrics.OverviewMetrics.StageP95;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Computes the dashboard numbers from the database on every request.
 * Nothing here is hard-coded: upload or review a document and the numbers move.
 */
@Service
public class MetricsService {

    private static final int DAYS = 14;

    private final DocumentRepository documents;
    private final Clock clock;

    public MetricsService(DocumentRepository documents, Clock clock) {
        this.documents = documents;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public OverviewMetrics overview(String tenantId) {
        Instant now = clock.instant();
        Instant weekAgo = now.minus(Duration.ofDays(7));
        Instant twoWeeksAgo = now.minus(Duration.ofDays(14));

        List<DocumentMetricRow> current = completed(documents.findMetricRows(tenantId, weekAgo, now.plusSeconds(1)));
        List<DocumentMetricRow> previous = completed(documents.findMetricRows(tenantId, twoWeeksAgo, weekAgo));

        return new OverviewMetrics(
                current.size(),
                previous.size(),
                straightThroughRate(current),
                straightThroughRate(previous),
                round(current.stream().mapToDouble(DocumentMetricRow::avgConfidence).average().orElse(0), 4),
                p95Latency(current),
                p95Latency(previous),
                avgCost(current),
                avgCost(previous),
                documents.countByTenantIdAndStatus(tenantId, DocStatus.NEEDS_REVIEW),
                daily(tenantId, now),
                stageP95(tenantId, weekAgo, now.plusSeconds(1)));
    }

    /** Documents still in flight or failed are not "processed" yet. */
    private static List<DocumentMetricRow> completed(List<DocumentMetricRow> rows) {
        return rows.stream().filter(r -> switch (r.status()) {
            case NEEDS_REVIEW, APPROVED, REJECTED, MATCHED -> true;
            default -> false;
        }).toList();
    }

    private static double straightThroughRate(List<DocumentMetricRow> rows) {
        if (rows.isEmpty()) return 0;
        long straight = rows.stream().filter(r -> !r.reviewRequired()).count();
        return round((double) straight / rows.size(), 4);
    }

    private static long p95Latency(List<DocumentMetricRow> rows) {
        return Percentiles.of(rows.stream().mapToInt(DocumentMetricRow::totalLatencyMs).toArray(), 95);
    }

    private static BigDecimal avgCost(List<DocumentMetricRow> rows) {
        if (rows.isEmpty()) return BigDecimal.ZERO;
        BigDecimal sum = rows.stream().map(DocumentMetricRow::costEur).reduce(BigDecimal.ZERO, BigDecimal::add);
        return sum.divide(BigDecimal.valueOf(rows.size()), 5, RoundingMode.HALF_UP);
    }

    private List<DailyVolume> daily(String tenantId, Instant now) {
        LocalDate today = LocalDate.ofInstant(now, ZoneOffset.UTC);
        LocalDate first = today.minusDays(DAYS - 1);
        Instant from = first.atStartOfDay(ZoneOffset.UTC).toInstant();
        List<DocumentMetricRow> rows = completed(documents.findMetricRows(tenantId, from, now.plusSeconds(1)));

        Map<LocalDate, List<DocumentMetricRow>> byDay = rows.stream()
                .collect(Collectors.groupingBy(r -> LocalDate.ofInstant(r.receivedAt(), ZoneOffset.UTC)));
        List<DailyVolume> result = new ArrayList<>(DAYS);
        for (LocalDate d = first; !d.isAfter(today); d = d.plusDays(1)) {
            List<DocumentMetricRow> day = byDay.getOrDefault(d, List.of());
            long review = day.stream().filter(DocumentMetricRow::reviewRequired).count();
            result.add(new DailyVolume(d, day.size() - review, review));
        }
        return result;
    }

    private List<StageP95> stageP95(String tenantId, Instant from, Instant to) {
        Map<PipelineStage, List<Integer>> byStage = new EnumMap<>(PipelineStage.class);
        for (StageSample sample : documents.findStageSamples(tenantId, from, to)) {
            byStage.computeIfAbsent(sample.stage(), k -> new ArrayList<>()).add(sample.ms());
        }
        List<StageP95> result = new ArrayList<>();
        for (PipelineStage stage : PipelineStage.values()) {
            int[] values = byStage.getOrDefault(stage, List.of()).stream().mapToInt(Integer::intValue).toArray();
            result.add(new StageP95(stage, Percentiles.of(values, 95)));
        }
        return result;
    }

    private static double round(double v, int digits) {
        double f = Math.pow(10, digits);
        return Math.round(v * f) / f;
    }
}
