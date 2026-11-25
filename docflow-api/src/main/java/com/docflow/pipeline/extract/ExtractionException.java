package com.docflow.pipeline.extract;

/** The extractor failed: provider error, timeout, or an answer that does not fit the schema. */
public class ExtractionException extends RuntimeException {

    private final boolean retryable;

    public ExtractionException(String message, boolean retryable, Throwable cause) {
        super(message, cause);
        this.retryable = retryable;
    }

    /** Rate limits, timeouts and 5xx errors are worth retrying; a bad API key is not. */
    public boolean isRetryable() {
        return retryable;
    }
}
