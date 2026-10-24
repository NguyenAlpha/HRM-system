package com.htttdn.hrm.dto.response.traffic;

import java.time.Instant;
import java.util.List;

import jakarta.annotation.Nullable;

/**
 * Requests, errors and mean processing time of the whole API over time. {@code points} cover the
 * whole range, oldest first, each {@code slotSeconds} wide; slots before {@code startedAt} are
 * empty because the counters live in memory since the last backend start.
 */
public record ApiTrafficTimelineResponse(
    String range,
    long slotSeconds,
    Instant startedAt,
    List<TrafficPoint> points
) {

    /** {@code start} is when the slot begins; {@code meanMillis} is null for a slot without requests. */
    public record TrafficPoint(
        Instant start,
        long requests,
        long clientErrors,
        long serverErrors,
        @Nullable
        Double meanMillis
    ) {
    }
}
