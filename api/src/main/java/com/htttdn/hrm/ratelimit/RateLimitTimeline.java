package com.htttdn.hrm.ratelimit;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import com.htttdn.hrm.dto.response.ratelimit.RateLimitTimelineResponse.Point;

/**
 * Requests and blocks per policy over time, in memory: per minute for the last hour, per 15
 * minutes for the last day and per hour for the last week. Each range is a ring of fixed slots, so
 * memory stays constant however long the backend runs; a slot is cleared when its time comes round again.
 */
public final class RateLimitTimeline {

    /** How far back a chart goes and how wide each of its points is. */
    public enum Range {
        HOUR(60, 60),
        DAY(15 * 60, 96),
        WEEK(60 * 60, 168);

        private final long slotSeconds;
        private final int slots;

        Range(long slotSeconds, int slots) {
            this.slotSeconds = slotSeconds;
            this.slots = slots;
        }

        public long slotSeconds() {
            return slotSeconds;
        }
    }

    private final Map<RateLimitPolicy, Map<Range, Ring>> rings = new EnumMap<>(RateLimitPolicy.class);

    RateLimitTimeline() {
        for (RateLimitPolicy policy : RateLimitPolicy.values()) {
            Map<Range, Ring> byRange = new EnumMap<>(Range.class);
            for (Range range : Range.values()) {
                byRange.put(range, new Ring(range));
            }
            rings.put(policy, byRange);
        }
    }

    void record(RateLimitPolicy policy, boolean wasBlocked, Instant at) {
        for (Ring ring : rings.get(policy).values()) {
            ring.add(at.getEpochSecond(), wasBlocked);
        }
    }

    /**
     * Every slot of the range up to the current one, oldest first. With a policy, its own requests
     * and blocks. Without one, {@code requests} adds up the IP policies only, since every request
     * passes exactly one of them, while {@code blocked} adds up every policy, since a request is
     * blocked at most once.
     */
    List<Point> points(Range range, RateLimitPolicy policy, Instant now) {
        long[] requests = new long[range.slots];
        long[] blocked = new long[range.slots];
        long nowSecond = now.getEpochSecond();
        for (RateLimitPolicy candidate : RateLimitPolicy.values()) {
            if (policy != null && candidate != policy) {
                continue;
            }
            boolean countRequests = policy != null || candidate.scope() == RateLimitPolicy.Scope.IP;
            rings.get(candidate).get(range).addTo(nowSecond, countRequests ? requests : null, blocked);
        }
        long firstSlot = nowSecond / range.slotSeconds - range.slots + 1;
        List<Point> points = new ArrayList<>(range.slots);
        for (int i = 0; i < range.slots; i++) {
            points.add(new Point(Instant.ofEpochSecond((firstSlot + i) * range.slotSeconds), requests[i], blocked[i]));
        }
        return points;
    }

    private static final class Ring {
        private final long slotSeconds;
        private final long[] slotIds;
        private final long[] requests;
        private final long[] blocked;

        Ring(Range range) {
            slotSeconds = range.slotSeconds;
            slotIds = new long[range.slots];
            requests = new long[range.slots];
            blocked = new long[range.slots];
            Arrays.fill(slotIds, -1);
        }

        synchronized void add(long epochSecond, boolean wasBlocked) {
            long slotId = epochSecond / slotSeconds;
            int index = (int) (slotId % slotIds.length);
            if (slotIds[index] != slotId) {
                slotIds[index] = slotId;
                requests[index] = 0;
                blocked[index] = 0;
            }
            requests[index]++;
            if (wasBlocked) {
                blocked[index]++;
            }
        }

        /** Adds the slots ending with the current one into the arrays, oldest first; {@code toRequests} may be null. */
        synchronized void addTo(long nowSecond, long[] toRequests, long[] toBlocked) {
            long firstSlot = nowSecond / slotSeconds - slotIds.length + 1;
            for (int i = 0; i < slotIds.length; i++) {
                long slotId = firstSlot + i;
                int index = (int) (slotId % slotIds.length);
                if (slotIds[index] != slotId) {
                    continue;
                }
                if (toRequests != null) {
                    toRequests[i] += requests[index];
                }
                toBlocked[i] += blocked[index];
            }
        }
    }
}
