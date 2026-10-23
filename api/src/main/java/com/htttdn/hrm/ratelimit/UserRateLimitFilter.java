package com.htttdn.hrm.ratelimit;

import java.io.IOException;
import java.util.List;

import org.springframework.http.HttpMethod;
import org.springframework.http.server.PathContainer;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * The second layer, after the JWT is verified: each signed-in user has their own quota, so people
 * behind one address do not use up each other's. A rule below replaces the default {@code api}
 * quota for its endpoints. Requests without a valid token are left to the IP layer.
 */
public class UserRateLimitFilter extends OncePerRequestFilter {

    private static final List<Rule> RULES = List.of(
        new Rule(RateLimitPolicy.PASSWORD, "POST", "/api/auth/change-password"),
        new Rule(RateLimitPolicy.PASSWORD, "POST", "/api/accounts/{accountId}/password-reset"),
        // HEAD runs the GET handler, so it shares the export quota.
        new Rule(RateLimitPolicy.EXPORT, "GET", "/api/payroll/periods/{id}/export"),
        new Rule(RateLimitPolicy.EXPORT, "HEAD", "/api/payroll/periods/{id}/export"),
        new Rule(RateLimitPolicy.REPORT, "GET", "/api/reports/**"),
        new Rule(RateLimitPolicy.HEAVY_WRITE, "POST", "/api/payroll/periods/{id}/calculate"),
        new Rule(RateLimitPolicy.HEAVY_WRITE, "POST", "/api/attendance/prepare"),
        new Rule(RateLimitPolicy.HEAVY_WRITE, "PUT", "/api/company/logo")
    );

    private final RateLimitService rateLimitService;

    public UserRateLimitFilter(RateLimitService rateLimitService) {
        this.rateLimitService = rateLimitService;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !rateLimitService.enabled() || !path(request).startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain chain
    ) throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken) || !authentication.isAuthenticated()) {
            chain.doFilter(request, response);
            return;
        }
        RateLimitDecision decision = rateLimitService.tryConsume(policy(request), authentication.getName());
        if (!decision.allowed()) {
            RateLimitResponses.writeTooManyRequests(response, decision);
            return;
        }
        RateLimitResponses.applyHeaders(response, decision);
        chain.doFilter(request, response);
    }

    static RateLimitPolicy policy(HttpServletRequest request) {
        PathContainer path = PathContainer.parsePath(path(request));
        return RULES.stream()
            .filter(rule -> rule.method().equals(request.getMethod()) && rule.pattern().matches(path))
            .map(Rule::policy)
            .findFirst()
            .orElse(RateLimitPolicy.API);
    }

    private static String path(HttpServletRequest request) {
        return request.getRequestURI().substring(request.getContextPath().length());
    }

    private record Rule(RateLimitPolicy policy, String method, PathPattern pattern) {

        Rule(RateLimitPolicy policy, String method, String pattern) {
            this(policy, method, PathPatternParser.defaultInstance.parse(pattern));
        }
    }
}
