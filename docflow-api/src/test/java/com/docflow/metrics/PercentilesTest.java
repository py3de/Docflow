package com.docflow.metrics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class PercentilesTest {

    @Test
    void emptyInputIsZero() {
        assertThat(Percentiles.of(new int[0], 95)).isZero();
    }

    @Test
    void nearestRankOnOneToHundred() {
        int[] values = new int[100];
        for (int i = 0; i < 100; i++) values[i] = i + 1;
        assertThat(Percentiles.of(values, 95)).isEqualTo(95);
        assertThat(Percentiles.of(values, 50)).isEqualTo(50);
        assertThat(Percentiles.of(values, 100)).isEqualTo(100);
    }

    @Test
    void doesNotModifyTheInput() {
        int[] values = {30, 10, 20};
        Percentiles.of(values, 95);
        assertThat(values).containsExactly(30, 10, 20);
    }

    @Test
    void rejectsOutOfRangePercentiles() {
        assertThatThrownBy(() -> Percentiles.of(new int[]{1}, 0)).isInstanceOf(IllegalArgumentException.class);
    }
}
