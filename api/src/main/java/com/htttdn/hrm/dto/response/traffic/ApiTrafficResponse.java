package com.htttdn.hrm.dto.response.traffic;

import java.time.Instant;
import java.util.List;

/**
 * Requests answered since {@code startedAt}, the last backend start; Micrometer keeps them in
 * memory only. {@code endpoints} is ordered by request count, most used first.
 */
public record ApiTrafficResponse(
    Instant startedAt,
    long totalRequests,
    long clientErrors,
    long serverErrors,
    List<EndpointTraffic> endpoints
) {

    /**
     * One route: {@code uri} is the mapping template ({@code /api/employees/{id}}), or
     * {@code UNKNOWN} for requests answered before a controller was chosen (401, 429, 404).
     */
    public record EndpointTraffic(
        String method,
        String uri,
        long requests,
        long clientErrors,
        long serverErrors,
        double meanMillis
    ) {
    }
}
