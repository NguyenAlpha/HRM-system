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

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import lombok.RequiredArgsConstructor;

/**
 * The second layer, after the JWT is verified: each signed-in user has their own quota, so people
 * behind one address do not use up each other's. A rule below replaces the default {@code api}
 * quota for its endpoints. Requests without a valid token are left to the IP layer.
 */
@RequiredArgsConstructor
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

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        PathContainer path = IpRateLimitFilter.path(request);
        return !rateLimitService.enabled()
            || !IpRateLimitFilter.API.matches(path)
            || IpRateLimitFilter.SETTINGS.matches(path);
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
        RateLimitPolicy policy = policy(request);
        if (!rateLimitService.enabled(policy)) {
            chain.doFilter(request, response);
            return;
        }
        RateLimitDecision decision = rateLimitService.tryConsume(policy, authentication.getName());
        if (!decision.allowed()) {
            RateLimitResponses.writeTooManyRequests(response, decision);
            return;
        }
        RateLimitResponses.applyHeaders(response, decision);
        chain.doFilter(request, response);
    }

    static RateLimitPolicy policy(HttpServletRequest request) {
        PathContainer path = IpRateLimitFilter.path(request);
        return RULES.stream()
            .filter(rule -> rule.method().equals(request.getMethod()) && rule.pattern().matches(path))
            .map(Rule::policy)
            .findFirst()
            .orElse(RateLimitPolicy.API);
    }

    private record Rule(RateLimitPolicy policy, String method, PathPattern pattern) {

        Rule(RateLimitPolicy policy, String method, String pattern) {
            this(policy, method, IpRateLimitFilter.pattern(pattern));
        }
    }
}
