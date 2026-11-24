package com.docflow.document;

import com.fasterxml.jackson.annotation.JsonValue;

/** Severity of a failed validation rule. Serialised in lower case to match the UI contract. */
public enum Severity {
    WARNING,
    CRITICAL;

    @JsonValue
    public String json() {
        return name().toLowerCase();
    }
}
