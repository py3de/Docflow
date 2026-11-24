package com.docflow.eval;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** GET /api/v1/evals: results of the CI quality gate, newest first. Not tenant-specific. */
@RestController
@RequestMapping("/api/v1/evals")
public class EvalController {

    /** Matches EvalRun in the UI's types.ts. */
    public record EvalRun(String id, String commit, String branch, String model, String dataset,
                          Instant startedAt, double f1, double threshold, boolean passed,
                          List<FieldF1> fieldF1) {
    }

    public record FieldF1(String field, double f1) {
    }

    private final EvalRunRepository runs;

    public EvalController(EvalRunRepository runs) {
        this.runs = runs;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public List<EvalRun> list(@RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit) {
        return runs.findAllByOrderByStartedAtDesc(PageRequest.of(0, limit)).stream()
                .map(r -> new EvalRun(r.getId(), r.getCommit(), r.getBranch(), r.getModel(), r.getDataset(),
                        r.getStartedAt(), r.getF1(), r.getThreshold(), r.isPassed(),
                        r.getFieldScores().stream().map(s -> new FieldF1(s.getField(), s.getF1())).toList()))
                .toList();
    }
}
