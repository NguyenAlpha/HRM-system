package com.htttdn.hrm.ratelimit;

import java.io.IOException;

import org.springframework.http.HttpMethod;
import org.springframework.http.server.PathContainer;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import lombok.RequiredArgsConstructor;

/**
 * The first layer, before authentication: the sign-in endpoints get their own per-IP quota and
 * every other {@code /api/**} request shares a wide per-IP ceiling. Created by SecurityConfig
 * rather than as a bean, so the servlet container does not register it a second time.
 */
@RequiredArgsConstructor
public class IpRateLimitFilter extends OncePerRequestFilter {

    /**
     * The rate-limit statistics and settings are never limited, so an administrator who set a quota
     * too tight can always loosen it again. They need a signed-in SYSTEM_ONLY permission anyway.
     */
    static final PathPattern SETTINGS = pattern("/api/system/rate-limit/**");

    static final PathPattern API = pattern("/api/**");

    private static final PathPattern LOGIN = pattern("/api/auth/login");
    private static final PathPattern REFRESH = pattern("/api/auth/refresh");
    private static final PathPattern ACTIVATION_COMPLETE = pattern("/api/account-activations/{token}/complete");

    private final RateLimitService rateLimitService;
    private final ClientIpResolver clientIpResolver;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        PathContainer path = path(request);
        return !rateLimitService.enabled()
            || HttpMethod.OPTIONS.matches(request.getMethod())
            || !API.matches(path)
            || SETTINGS.matches(path);
    }

    @Override
    protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain chain
    ) throws ServletException, IOException {
        RateLimitPolicy policy = policy(request);
        if (!rateLimitService.enabled(policy)) {
            chain.doFilter(request, response);
            return;
        }
        String key = ClientIpResolver.bucketKey(clientIpResolver.resolve(request));
        RateLimitDecision decision = rateLimitService.tryConsume(policy, key);
        if (!decision.allowed()) {
            RateLimitResponses.writeTooManyRequests(response, decision);
            return;
        }
        RateLimitResponses.applyHeaders(response, decision);
        chain.doFilter(request, response);
    }

    private static RateLimitPolicy policy(HttpServletRequest request) {
        if (!HttpMethod.POST.matches(request.getMethod())) {
            return RateLimitPolicy.IP_CEILING;
        }
        PathContainer path = path(request);
        if (LOGIN.matches(path)) {
            return RateLimitPolicy.LOGIN;
        }
        if (REFRESH.matches(path)) {
            return RateLimitPolicy.REFRESH;
        }
        if (ACTIVATION_COMPLETE.matches(path)) {
            return RateLimitPolicy.ACTIVATION;
        }
        return RateLimitPolicy.IP_CEILING;
    }

    /**
     * The request path as Spring MVC matches it: patterns compare decoded segments, so
     * {@code /%61pi/auth/%6Cogin} counts as {@code /api/auth/login}, the handler it reaches. Comparing
     * the raw URI as text would let an encoded path pick a wider quota or skip the limits.
     */
    static PathContainer path(HttpServletRequest request) {
        return PathContainer.parsePath(request.getRequestURI().substring(request.getContextPath().length()));
    }

    static PathPattern pattern(String pattern) {
        return PathPatternParser.defaultInstance.parse(pattern);
    }
}
