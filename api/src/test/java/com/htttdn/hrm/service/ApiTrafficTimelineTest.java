package com.htttdn.hrm.service;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.htttdn.hrm.dto.response.traffic.ApiTrafficTimelineResponse.TrafficPoint;
import com.htttdn.hrm.timeline.TimelineRange;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ApiTrafficTimelineTest {

    private static final Instant NOW = Instant.parse("2026-10-24T10:30:20Z");

    @Test
    void countsRequestsErrorsAndTheMeanTimePerSlot() {
        ApiTrafficTimeline timeline = new ApiTrafficTimeline();
        timeline.record(200, 10_000_000, NOW);
        timeline.record(403, 20_000_000, NOW);
        timeline.record(500, 30_000_000, NOW);
        timeline.record(200, 4_000_000, NOW.minusSeconds(60));

        List<TrafficPoint> hour = timeline.points(TimelineRange.HOUR, NOW);

        assertEquals(new TrafficPoint(Instant.parse("2026-10-24T10:30:00Z"), 3, 1, 1, 20.0), hour.getLast());
        assertEquals(new TrafficPoint(Instant.parse("2026-10-24T10:29:00Z"), 1, 0, 0, 4.0), hour.get(58));
        assertNull(hour.getFirst().meanMillis());
        assertEquals(0, hour.getFirst().requests());
    }

    @Test
    void everyRangeSeesTheSameRequests() {
        ApiTrafficTimeline timeline = new ApiTrafficTimeline();
        timeline.record(200, 1_000_000, NOW.minusSeconds(120));
        timeline.record(429, 1_000_000, NOW);

        for (TimelineRange range : TimelineRange.values()) {
            List<TrafficPoint> points = timeline.points(range, NOW);
            assertEquals(range.slots(), points.size());
            assertEquals(2, points.stream().mapToLong(TrafficPoint::requests).sum());
            assertEquals(1, points.stream().mapToLong(TrafficPoint::clientErrors).sum());
        }
    }
}
