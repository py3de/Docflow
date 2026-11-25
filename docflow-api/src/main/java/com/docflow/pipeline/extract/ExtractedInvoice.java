package com.docflow.pipeline.extract;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/** What an extractor returns, before validation. */
public record ExtractedInvoice(
        Map<String, FieldGuess> fields,
        String currency,
        List<LineGuess> lineItems,
        String model,
        int inputTokens,
        int outputTokens) {

    /** A value as the model read it, with the model's own confidence (0..1). Null value = not found. */
    public record FieldGuess(String value, double confidence) {
    }

    public record LineGuess(String description, BigDecimal quantity, BigDecimal unitPrice, BigDecimal total) {
    }
}
