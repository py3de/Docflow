package com.docflow.document;

/** Projection: one stage timing of one document (boxed for Hibernate's "select new"). */
public record StageSample(PipelineStage stage, Integer ms) {
}
