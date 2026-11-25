package com.docflow.pipeline;

/** Published when a document is stored and waiting for the pipeline (upload or reprocess). */
public record DocumentReceivedEvent(String documentId) {
}
