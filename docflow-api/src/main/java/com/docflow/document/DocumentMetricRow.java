package com.docflow.document;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Projection used by the metrics service. Wrapper types on purpose:
 * Hibernate's "select new ..." matches constructors most reliably with boxed types.
 */
public record DocumentMetricRow(Instant receivedAt, Boolean reviewRequired, Double avgConfidence,
                                BigDecimal costEur, Integer totalLatencyMs, DocStatus status) {
}
