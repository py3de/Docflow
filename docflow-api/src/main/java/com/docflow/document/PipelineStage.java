package com.docflow.document;

public enum PipelineStage {
    INGEST,
    OCR,
    LLM_EXTRACTION,
    VALIDATION,
    PO_MATCHING
}
