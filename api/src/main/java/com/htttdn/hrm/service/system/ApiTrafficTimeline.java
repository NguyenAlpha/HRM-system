package com.htttdn.hrm.service.system;

import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.springframework.stereotype.Component;

import com.htttdn.hrm.dto.response.traffic.ApiTrafficTimelineResponse.TrafficPoint;
import com.htttdn.hrm.timeline.TimelineRange;
import com.htttdn.hrm.timeline.TimelineRing;

/**
 * Requests, errors and processing time of the whole API over time, in memory: per minute for the
 * last hour, per 15 minutes for the last day and per hour for the last week. Filled by
 * {@link com.htttdn.hrm.config.ApiTrafficTimelineFilter}; Micrometer's timers only keep totals.
 */
@Component
public class ApiTrafficTimeline {

    private static final int REQUESTS = 0;
    private static final int CLIENT_ERRORS = 1;
    private static final int SERVER_ERRORS = 2;
    private static final int MICROS = 3;

    private final Map<TimelineRange, TimelineRing> rings = new EnumMap<>(TimelineRange.class);

    public ApiTrafficTimeline() {
        for (TimelineRange range : TimelineRange.values()) {
            rings.put(range, new TimelineRing(range, 4));
        }
    }

    public void record(int status, long nanos, Instant at) {
        long micros = TimeUnit.NANOSECONDS.toMicros(nanos);
        long clientError = status >= 400 && status < 500 ? 1 : 0;
        long serverError = status >= 500 ? 1 : 0;
        for (TimelineRing ring : rings.values()) {
            ring.add(at.getEpochSecond(), 1, clientError, serverError, micros);
        }
    }

    /** Every slot of the range up to the current one, oldest first; a slot without requests has no mean. */
    List<TrafficPoint> points(TimelineRange range, Instant now) {
        long[][] totals = new long[4][range.slots()];
        rings.get(range).addTo(now.getEpochSecond(), totals);
        List<Instant> starts = range.slotStarts(now);
        List<TrafficPoint> points = new ArrayList<>(range.slots());
        for (int i = 0; i < range.slots(); i++) {
            long requests = totals[REQUESTS][i];
            points.add(new TrafficPoint(
                starts.get(i),
                requests,
                totals[CLIENT_ERRORS][i],
                totals[SERVER_ERRORS][i],
                requests == 0 ? null : totals[MICROS][i] / 1000.0 / requests
            ));
        }
        return points;
    }
}
