package com.htttdn.hrm.exception;

import org.springframework.http.HttpStatus;

import com.htttdn.hrm.dto.response.common.ErrorCode;
import com.htttdn.hrm.ratelimit.RateLimitDecision;

/** A quota checked inside a service ran out; answered like the filters' 429, with the same headers. */
public class RateLimitExceededException extends AppException {

    private final transient RateLimitDecision decision;

    public RateLimitExceededException(RateLimitDecision decision) {
        super(ErrorCode.RATE_LIMIT_EXCEEDED, HttpStatus.TOO_MANY_REQUESTS, "Too many requests. Please try again later.");
        this.decision = decision;
    }

    public RateLimitDecision getDecision() {
        return decision;
    }
}
