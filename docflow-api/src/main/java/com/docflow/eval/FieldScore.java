package com.docflow.eval;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

@Embeddable
public class FieldScore {

    @Column(nullable = false)
    private String field;

    @Column(nullable = false)
    private double f1;

    protected FieldScore() {
        // for JPA
    }

    public FieldScore(String field, double f1) {
        this.field = field;
        this.f1 = f1;
    }

    public String getField() { return field; }
    public double getF1() { return f1; }
}
