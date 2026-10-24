package com.htttdn.hrm.dto.response.ratelimit;

import java.time.Instant;
import java.util.List;

import jakarta.annotation.Nullable;

/**
 * Requests and blocks over time for one policy, or for all of them when {@code policy} is null.
 * {@code points} cover the whole range, oldest first, each {@code slotSeconds} wide; slots before
 * {@code startedAt} are zero because the counters live in memory since the last backend start.
 */
public record RateLimitTimelineResponse(
    String range,
    @Nullable
    String policy,
    long slotSeconds,
    Instant startedAt,
    List<Point> points
) {

    /** {@code start} is when the slot begins. */
    public record Point(Instant start, long requests, long blocked) {
    }
}
