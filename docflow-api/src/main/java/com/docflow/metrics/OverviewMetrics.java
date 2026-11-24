package com.docflow.metrics;

import com.docflow.document.PipelineStage;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Matches OverviewMetrics in the UI's types.ts. */
public record OverviewMetrics(
        long processed7d,
        long processedPrev7d,
        double straightThroughRate,
        double straightThroughPrev,
        double avgConfidence,
        long p95LatencyMs,
        long p95LatencyPrevMs,
        BigDecimal costPerDocEur,
        BigDecimal costPerDocPrevEur,
        long reviewQueue,
        List<DailyVolume> daily,
        List<StageP95> stageP95) {

    public record DailyVolume(LocalDate date, long straightThrough, long humanReview) {
    }

    public record StageP95(PipelineStage stage, long ms) {
    }
}
