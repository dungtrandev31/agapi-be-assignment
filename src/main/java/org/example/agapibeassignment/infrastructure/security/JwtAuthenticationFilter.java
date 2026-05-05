package org.example.agapibeassignment.infrastructure.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private static final String BEARER = "Bearer ";
    private static final String DENYLIST = "jwt:denylist:";

    private final JwtTokenProvider jwt;
    private final RedisTemplate<String, Object> redis;

    public JwtAuthenticationFilter(JwtTokenProvider jwt, RedisTemplate<String, Object> redis) {
        this.jwt = jwt;
        this.redis = redis;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        String token = extractToken(req);
        if (token != null && jwt.isTokenValid(token)
                && !Boolean.TRUE.equals(redis.hasKey(DENYLIST + token))
                && "access".equals(jwt.getTokenType(token))) {
            Long userId = jwt.getUserIdFromToken(token);
            String role = jwt.parseToken(token).get("role", String.class);
            var auth = new UsernamePasswordAuthenticationToken(userId, null,
                    List.of(new SimpleGrantedAuthority("ROLE_" + role)));
            SecurityContextHolder.getContext().setAuthentication(auth);
        }
        chain.doFilter(req, res);
    }

    private String extractToken(HttpServletRequest req) {
        String h = req.getHeader("Authorization");
        return StringUtils.hasText(h) && h.startsWith(BEARER) ? h.substring(BEARER.length()) : null;
    }
}
