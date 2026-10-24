package com.htttdn.hrm.config;

import java.io.IOException;
import java.time.Clock;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.htttdn.hrm.service.ApiTrafficTimeline;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Records every request into {@link ApiTrafficTimeline}. It runs right after Spring Boot's own
 * request metrics and before Spring Security, so it counts the same requests Micrometer does,
 * including those refused with 401 or 429.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 2)
public class ApiTrafficTimelineFilter extends OncePerRequestFilter {

    private final ApiTrafficTimeline timeline;
    private final Clock clock;

    public ApiTrafficTimelineFilter(ApiTrafficTimeline timeline, Clock clock) {
        this.timeline = timeline;
        this.clock = clock;
    }

    @Override
    protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain chain
    ) throws ServletException, IOException {
        long started = System.nanoTime();
        int status = HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
        try {
            chain.doFilter(request, response);
            status = response.getStatus();
        } finally {
            // An exception that escapes the chain ends as a 500.
            timeline.record(status, System.nanoTime() - started, clock.instant());
        }
    }
}
