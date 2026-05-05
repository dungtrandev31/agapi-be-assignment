package org.example.agapibeassignment.infrastructure.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * Redis-based sliding window rate limiter.
 * Limits each IP to 100 requests per minute.
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private static final String RATE_KEY = "rate_limit:";
    private static final int MAX_REQUESTS = 100;
    private static final int WINDOW_SECONDS = 60;

    private final RedisTemplate<String, Object> redis;

    public RateLimitFilter(RedisTemplate<String, Object> redis) {
        this.redis = redis;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {

        String clientIp = getClientIp(req);
        String key = RATE_KEY + clientIp;

        Long count = redis.opsForValue().increment(key);
        if (count != null && count == 1) {
            redis.expire(key, WINDOW_SECONDS, TimeUnit.SECONDS);
        }

        if (count != null && count > MAX_REQUESTS) {
            res.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            res.setContentType("application/json");
            res.getWriter().write("{\"success\":false,\"message\":\"Rate limit exceeded. Try again later.\",\"errorCode\":\"RATE_LIMITED\"}");
            return;
        }

        // Set rate limit headers
        res.setHeader("X-RateLimit-Limit", String.valueOf(MAX_REQUESTS));
        res.setHeader("X-RateLimit-Remaining", String.valueOf(Math.max(0, MAX_REQUESTS - (count != null ? count : 0))));

        chain.doFilter(req, res);
    }

    private String getClientIp(HttpServletRequest req) {
        String xff = req.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isEmpty()) {
            return xff.split(",")[0].trim();
        }
        return req.getRemoteAddr();
    }
}
