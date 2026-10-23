package com.htttdn.hrm.ratelimit;

import java.io.IOException;
import java.util.List;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;

import com.htttdn.hrm.dto.response.common.ErrorCode;
import com.htttdn.hrm.security.SecurityErrorResponseWriter;

import jakarta.servlet.http.HttpServletResponse;

/** The quota headers sent with every limited request, and the 429 body. */
public final class RateLimitResponses {

    public static final String LIMIT = "RateLimit-Limit";
    public static final String REMAINING = "RateLimit-Remaining";
    public static final String RESET = "RateLimit-Reset";

    /** Headers the browser may read on a cross-origin response. */
    public static final List<String> EXPOSED_HEADERS = List.of(HttpHeaders.RETRY_AFTER, LIMIT, REMAINING, RESET);

    private RateLimitResponses() {
    }

    public static void applyHeaders(HttpServletResponse response, RateLimitDecision decision) {
        response.setHeader(LIMIT, Long.toString(decision.limit()));
        response.setHeader(REMAINING, Long.toString(decision.remaining()));
        response.setHeader(RESET, Long.toString(decision.resetSeconds()));
        if (!decision.allowed()) {
            response.setHeader(HttpHeaders.RETRY_AFTER, Long.toString(decision.retryAfterSeconds()));
        }
    }

    public static void writeTooManyRequests(HttpServletResponse response, RateLimitDecision decision)
        throws IOException {
        applyHeaders(response, decision);
        SecurityErrorResponseWriter.write(response, HttpStatus.TOO_MANY_REQUESTS.value(),
            ErrorCode.RATE_LIMIT_EXCEEDED, "Too many requests. Please try again later.");
    }
}
