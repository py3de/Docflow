package com.docflow.document;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

/** How long one pipeline stage took for one document. */
@Embeddable
public class StageTiming {

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PipelineStage stage;

    @Column(nullable = false)
    private int ms;

    protected StageTiming() {
        // for JPA
    }

    public StageTiming(PipelineStage stage, int ms) {
        this.stage = stage;
        this.ms = ms;
    }

    public PipelineStage getStage() { return stage; }
    public int getMs() { return ms; }
}
