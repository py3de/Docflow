package com.docflow.pipeline.extract;

/** Stage 2: turns the document's text into structured invoice fields. */
public interface InvoiceExtractor {

    /** @param documentText the (already redacted) text of the document */
    ExtractedInvoice extract(String documentText);

    /** Short name shown in logs, e.g. "anthropic:claude-haiku-5-5". */
    String describe();
}
