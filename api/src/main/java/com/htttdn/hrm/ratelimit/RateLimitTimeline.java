package com.htttdn.hrm.ratelimit;

import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import com.htttdn.hrm.dto.response.ratelimit.RateLimitTimelineResponse.Point;
import com.htttdn.hrm.timeline.TimelineRange;
import com.htttdn.hrm.timeline.TimelineRing;

/**
 * Requests and blocks per policy over time, in memory: per minute for the last hour, per 15
 * minutes for the last day and per hour for the last week, each kept in a {@link TimelineRing}.
 */
public final class RateLimitTimeline {

    private static final int REQUESTS = 0;
    private static final int BLOCKED = 1;

    private final Map<RateLimitPolicy, Map<TimelineRange, TimelineRing>> rings = new EnumMap<>(RateLimitPolicy.class);

    RateLimitTimeline() {
        for (RateLimitPolicy policy : RateLimitPolicy.values()) {
            Map<TimelineRange, TimelineRing> byRange = new EnumMap<>(TimelineRange.class);
            for (TimelineRange range : TimelineRange.values()) {
                byRange.put(range, new TimelineRing(range, 2));
            }
            rings.put(policy, byRange);
        }
    }

    void record(RateLimitPolicy policy, boolean wasBlocked, Instant at) {
        for (TimelineRing ring : rings.get(policy).values()) {
            ring.add(at.getEpochSecond(), 1, wasBlocked ? 1 : 0);
        }
    }

    /**
     * Every slot of the range up to the current one, oldest first. With a policy, its own requests
     * and blocks. Without one, {@code requests} adds up the IP policies only, since every request
     * passes exactly one of them, while {@code blocked} adds up every policy, since a request is
     * blocked at most once.
     */
    List<Point> points(TimelineRange range, RateLimitPolicy policy, Instant now) {
        long[] requests = new long[range.slots()];
        long[] blocked = new long[range.slots()];
        for (RateLimitPolicy candidate : RateLimitPolicy.values()) {
            if (policy != null && candidate != policy) {
                continue;
            }
            boolean countRequests = policy != null || candidate.scope() == RateLimitPolicy.Scope.IP;
            long[][] into = new long[2][];
            into[REQUESTS] = countRequests ? requests : null;
            into[BLOCKED] = blocked;
            rings.get(candidate).get(range).addTo(now.getEpochSecond(), into);
        }
        List<Instant> starts = range.slotStarts(now);
        List<Point> points = new ArrayList<>(range.slots());
        for (int i = 0; i < range.slots(); i++) {
            points.add(new Point(starts.get(i), requests[i], blocked[i]));
        }
        return points;
    }
}
