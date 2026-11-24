package com.docflow.document;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

/** A business rule that an extracted value failed, e.g. totals that don't add up. */
@Embeddable
public class ValidationIssue {

    @Column(nullable = false)
    private String rule;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Severity severity;

    @Column(nullable = false)
    private String message;

    protected ValidationIssue() {
        // for JPA
    }

    public ValidationIssue(String rule, Severity severity, String message) {
        this.rule = rule;
        this.severity = severity;
        this.message = message;
    }

    public String getRule() { return rule; }
    public Severity getSeverity() { return severity; }
    public String getMessage() { return message; }
}
