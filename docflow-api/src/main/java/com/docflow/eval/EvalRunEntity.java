package com.docflow.eval;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** One run of the extraction quality gate in CI. */
@Entity
@Table(name = "eval_run")
public class EvalRunEntity {

    @Id
    private String id;

    @Column(name = "commit_sha", nullable = false)
    private String commit;

    @Column(nullable = false)
    private String branch;

    @Column(nullable = false)
    private String model;

    @Column(nullable = false)
    private String dataset;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(nullable = false)
    private double f1;

    @Column(nullable = false)
    private double threshold;

    @Column(nullable = false)
    private boolean passed;

    @ElementCollection
    @CollectionTable(name = "eval_field_score", joinColumns = @JoinColumn(name = "eval_run_id"))
    @OrderColumn(name = "item_order")
    private List<FieldScore> fieldScores = new ArrayList<>();

    protected EvalRunEntity() {
        // for JPA
    }

    public EvalRunEntity(String id, String commit, String branch, String model, String dataset,
                         Instant startedAt, double f1, double threshold, List<FieldScore> fieldScores) {
        this.id = id;
        this.commit = commit;
        this.branch = branch;
        this.model = model;
        this.dataset = dataset;
        this.startedAt = startedAt;
        this.f1 = f1;
        this.threshold = threshold;
        this.passed = f1 >= threshold;
        this.fieldScores.addAll(fieldScores);
    }

    public String getId() { return id; }
    public String getCommit() { return commit; }
    public String getBranch() { return branch; }
    public String getModel() { return model; }
    public String getDataset() { return dataset; }
    public Instant getStartedAt() { return startedAt; }
    public double getF1() { return f1; }
    public double getThreshold() { return threshold; }
    public boolean isPassed() { return passed; }
    public List<FieldScore> getFieldScores() { return fieldScores; }
}
