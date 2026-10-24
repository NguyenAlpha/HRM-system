package com.htttdn.hrm.service;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.springframework.stereotype.Service;

import com.htttdn.hrm.dto.response.traffic.ApiTrafficResponse;
import com.htttdn.hrm.dto.response.traffic.ApiTrafficResponse.EndpointTraffic;
import com.htttdn.hrm.dto.response.traffic.ApiTrafficTimelineResponse;
import com.htttdn.hrm.timeline.TimelineRange;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

/**
 * API traffic from the {@code http.server.requests} timers Spring Boot records for every request:
 * one timer per method, route template and status, summed here per method and route.
 */
@Service
public class ApiTrafficService {

    private static final String REQUEST_METRIC = "http.server.requests";

    private final MeterRegistry meterRegistry;
    private final ApiTrafficTimeline timeline;
    private final Clock clock;
    private final Instant startedAt;

    public ApiTrafficService(MeterRegistry meterRegistry, ApiTrafficTimeline timeline, Clock clock) {
        this.meterRegistry = meterRegistry;
        this.timeline = timeline;
        this.clock = clock;
        this.startedAt = clock.instant();
    }

    public ApiTrafficResponse stats() {
        Map<Route, Totals> byRoute = new LinkedHashMap<>();
        for (Timer timer : meterRegistry.find(REQUEST_METRIC).timers()) {
            Route route = new Route(tag(timer, "method"), tag(timer, "uri"));
            byRoute.computeIfAbsent(route, ignored -> new Totals()).add(timer, tag(timer, "status"));
        }
        List<EndpointTraffic> endpoints = byRoute.entrySet().stream()
            .filter(entry -> entry.getValue().requests > 0)
            .map(entry -> entry.getValue().toTraffic(entry.getKey()))
            .sorted(Comparator.comparingLong(EndpointTraffic::requests).reversed())
            .toList();
        return new ApiTrafficResponse(
            startedAt,
            endpoints.stream().mapToLong(EndpointTraffic::requests).sum(),
            endpoints.stream().mapToLong(EndpointTraffic::clientErrors).sum(),
            endpoints.stream().mapToLong(EndpointTraffic::serverErrors).sum(),
            endpoints
        );
    }

    /** Requests, errors and mean processing time of the whole API over the range. */
    public ApiTrafficTimelineResponse timeline(TimelineRange range) {
        return new ApiTrafficTimelineResponse(
            range.name(),
            range.slotSeconds(),
            startedAt,
            timeline.points(range, clock.instant())
        );
    }

    private static String tag(Timer timer, String key) {
        String value = timer.getId().getTag(key);
        return value == null ? "UNKNOWN" : value;
    }

    private record Route(String method, String uri) {
    }

    private static final class Totals {
        private long requests;
        private long clientErrors;
        private long serverErrors;
        private double totalMillis;

        void add(Timer timer, String status) {
            long count = timer.count();
            requests += count;
            totalMillis += timer.totalTime(TimeUnit.MILLISECONDS);
            if (status.startsWith("4")) {
                clientErrors += count;
            } else if (status.startsWith("5")) {
                serverErrors += count;
            }
        }

        EndpointTraffic toTraffic(Route route) {
            return new EndpointTraffic(route.method(), route.uri(), requests, clientErrors, serverErrors,
                totalMillis / requests);
        }
    }
}
