package com.htttdn.hrm.ratelimit;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.htttdn.hrm.dto.response.ratelimit.RateLimitTimelineResponse.Point;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RateLimitTimelineTest {

    private static final Instant NOW = Instant.parse("2026-10-24T10:30:20Z");

    @Test
    void coversTheWholeRangeOldestFirstEndingWithTheCurrentSlot() {
        RateLimitTimeline timeline = new RateLimitTimeline();

        List<Point> hour = timeline.points(RateLimitTimeline.Range.HOUR, null, NOW);
        List<Point> day = timeline.points(RateLimitTimeline.Range.DAY, null, NOW);
        List<Point> week = timeline.points(RateLimitTimeline.Range.WEEK, null, NOW);

        assertEquals(60, hour.size());
        assertEquals(Instant.parse("2026-10-24T10:30:00Z"), hour.getLast().start());
        assertEquals(Instant.parse("2026-10-24T09:31:00Z"), hour.getFirst().start());
        assertEquals(96, day.size());
        assertEquals(Instant.parse("2026-10-24T10:30:00Z"), day.getLast().start());
        assertEquals(168, week.size());
        assertEquals(Instant.parse("2026-10-24T10:00:00Z"), week.getLast().start());
    }

    @Test
    void countsRequestsAndBlocksPerSlot() {
        RateLimitTimeline timeline = new RateLimitTimeline();
        timeline.record(RateLimitPolicy.API, false, NOW.minusSeconds(60));
        timeline.record(RateLimitPolicy.API, false, NOW);
        timeline.record(RateLimitPolicy.API, true, NOW);

        List<Point> hour = timeline.points(RateLimitTimeline.Range.HOUR, RateLimitPolicy.API, NOW);

        assertEquals(new Point(Instant.parse("2026-10-24T10:30:00Z"), 2, 1), hour.getLast());
        assertEquals(1, hour.get(58).requests());
        assertEquals(0, hour.get(58).blocked());
        assertEquals(3, timeline.points(RateLimitTimeline.Range.WEEK, RateLimitPolicy.API, NOW).getLast().requests());
    }

    @Test
    void allPoliciesCountEachRequestOnceAtTheIpLayerAndEveryBlock() {
        RateLimitTimeline timeline = new RateLimitTimeline();
        // One signed-in request passes both layers and is then blocked at the user layer.
        timeline.record(RateLimitPolicy.IP_CEILING, false, NOW);
        timeline.record(RateLimitPolicy.API, true, NOW);
        // One request blocked at the IP layer.
        timeline.record(RateLimitPolicy.LOGIN, true, NOW);

        Point all = timeline.points(RateLimitTimeline.Range.HOUR, null, NOW).getLast();

        assertEquals(2, all.requests());
        assertEquals(2, all.blocked());
    }

    @Test
    void dropsSlotsOlderThanTheRange() {
        RateLimitTimeline timeline = new RateLimitTimeline();
        timeline.record(RateLimitPolicy.API, true, NOW.minusSeconds(3_600));

        List<Point> hour = timeline.points(RateLimitTimeline.Range.HOUR, RateLimitPolicy.API, NOW);
        assertEquals(0, hour.stream().mapToLong(Point::requests).sum());

        // The same ring slot comes round again an hour later and starts from zero.
        timeline.record(RateLimitPolicy.API, false, NOW);
        assertEquals(new Point(Instant.parse("2026-10-24T10:30:00Z"), 1, 0),
            timeline.points(RateLimitTimeline.Range.HOUR, RateLimitPolicy.API, NOW).getLast());
        assertEquals(2, timeline.points(RateLimitTimeline.Range.DAY, RateLimitPolicy.API, NOW).stream()
            .mapToLong(Point::requests).sum());
    }
}
