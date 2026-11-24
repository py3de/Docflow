package com.docflow.metrics;

import java.util.Arrays;

/** Nearest-rank percentile, the same definition most monitoring tools use for p95. */
public final class Percentiles {

    private Percentiles() {
    }

    public static long of(int[] values, double percentile) {
        if (values.length == 0) {
            return 0;
        }
        if (percentile <= 0 || percentile > 100) {
            throw new IllegalArgumentException("percentile must be in (0, 100]");
        }
        int[] sorted = values.clone();
        Arrays.sort(sorted);
        int rank = (int) Math.ceil(percentile / 100.0 * sorted.length);
        return sorted[Math.max(0, rank - 1)];
    }
}
