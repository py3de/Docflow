package com.docflow.pipeline;

/** The file is damaged, encrypted, has no text, or is a type the pipeline cannot read yet. */
public class UnreadableDocumentException extends RuntimeException {

    public UnreadableDocumentException(String message, Throwable cause) {
        super(message, cause);
    }
}
