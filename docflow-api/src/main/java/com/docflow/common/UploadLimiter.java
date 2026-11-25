package com.docflow.common;

import com.docflow.config.DocFlowProperties;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * Keeps a public demo from being flooded: at most N uploads per client per hour and M per day overall.
 * In memory, per app instance: enough for a demo. A real service would use a shared store or the API gateway.
 */
@Component
public class UploadLimiter {

    private final int perIpPerHour;
    private final int perDay;
    private final Clock clock;
    private final Map<String, Deque<Instant>> byClient = new HashMap<>();
    private final Deque<Instant> all = new ArrayDeque<>();

    public UploadLimiter(DocFlowProperties properties, Clock clock) {
        DocFlowProperties.Limits limits = properties.limits();
        this.perIpPerHour = limits == null ? 0 : limits.uploadsPerIpPerHour();
        this.perDay = limits == null ? 0 : limits.uploadsPerDay();
        this.clock = clock;
    }

    /** Throws 429 Too Many Requests when a limit is reached; otherwise counts the upload. */
    public synchronized void check(String client) {
        if (perIpPerHour <= 0 && perDay <= 0) {
            return;
        }
        Instant now = clock.instant();
        prune(all, now.minus(Duration.ofDays(1)));
        Deque<Instant> mine = byClient.computeIfAbsent(client, k -> new ArrayDeque<>());
        prune(mine, now.minus(Duration.ofHours(1)));
        byClient.values().removeIf(d -> d.isEmpty() && d != mine);

        if (perDay > 0 && all.size() >= perDay) {
            throw new InvalidRequestException(HttpStatus.TOO_MANY_REQUESTS,
                    "This public demo has reached its upload limit for today. Please try again tomorrow.");
        }
        if (perIpPerHour > 0 && mine.size() >= perIpPerHour) {
            throw new InvalidRequestException(HttpStatus.TOO_MANY_REQUESTS,
                    "Upload limit reached (" + perIpPerHour + " per hour). Please try again later.");
        }
        mine.addLast(now);
        all.addLast(now);
    }

    private static void prune(Deque<Instant> times, Instant cutoff) {
        while (!times.isEmpty() && times.peekFirst().isBefore(cutoff)) {
            times.pollFirst();
        }
    }
}
