package com.docflow.common;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.docflow.config.DocFlowProperties;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class UploadLimiterTest {

    /** A clock the test can move forward. */
    private static final class TestClock extends Clock {
        Instant now = Instant.parse("2026-11-25T10:00:00Z");
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }

    private static UploadLimiter limiter(int perIpPerHour, int perDay, Clock clock) {
        DocFlowProperties props = new DocFlowProperties(null, null, null, null, null,
                new DocFlowProperties.Limits(perIpPerHour, perDay), null);
        return new UploadLimiter(props, clock);
    }

    @Test
    void noLimitsByDefault() {
        UploadLimiter l = limiter(0, 0, new TestClock());
        for (int i = 0; i < 100; i++) {
            l.check("1.2.3.4");
        }
    }

    @Test
    void perClientLimitResetsAfterAnHour() {
        TestClock clock = new TestClock();
        UploadLimiter l = limiter(2, 0, clock);
        l.check("1.2.3.4");
        l.check("1.2.3.4");
        assertThatThrownBy(() -> l.check("1.2.3.4")).isInstanceOfSatisfying(InvalidRequestException.class,
                e -> org.assertj.core.api.Assertions.assertThat(e.getStatus().value()).isEqualTo(429));
        assertThatCode(() -> l.check("5.6.7.8")).doesNotThrowAnyException();   // other clients unaffected

        clock.now = clock.now.plus(Duration.ofMinutes(61));
        assertThatCode(() -> l.check("1.2.3.4")).doesNotThrowAnyException();
    }

    @Test
    void dailyLimitCoversAllClients() {
        UploadLimiter l = limiter(0, 3, new TestClock());
        l.check("a");
        l.check("b");
        l.check("c");
        assertThatThrownBy(() -> l.check("d")).isInstanceOf(InvalidRequestException.class);
    }
}
