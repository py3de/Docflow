package com.docflow.document;

/** Lifecycle of a document as it moves through the pipeline. */
public enum DocStatus {
    RECEIVED,
    EXTRACTING,
    VALIDATING,
    NEEDS_REVIEW,
    APPROVED,
    REJECTED,
    MATCHED,
    FAILED
}
