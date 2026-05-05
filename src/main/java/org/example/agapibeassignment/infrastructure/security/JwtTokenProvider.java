package org.example.agapibeassignment.infrastructure.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
public class JwtTokenProvider {
    private final SecretKey key;
    private final long accessTokenExpiryMs;
    private final long refreshTokenExpiryMs;

    public JwtTokenProvider(@Value("${app.jwt.secret}") String secret,
                            @Value("${app.jwt.access-token-expiry-ms}") long accessMs,
                            @Value("${app.jwt.refresh-token-expiry-ms}") long refreshMs) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessTokenExpiryMs = accessMs;
        this.refreshTokenExpiryMs = refreshMs;
    }

    public String generateAccessToken(Long userId, String role) {
        Date now = new Date();
        return Jwts.builder().subject(String.valueOf(userId)).claim("role", role).claim("type", "access")
                .issuedAt(now).expiration(new Date(now.getTime() + accessTokenExpiryMs)).signWith(key).compact();
    }

    public String generateRefreshToken(Long userId) {
        Date now = new Date();
        return Jwts.builder().subject(String.valueOf(userId)).claim("type", "refresh")
                .issuedAt(now).expiration(new Date(now.getTime() + refreshTokenExpiryMs)).signWith(key).compact();
    }

    public Claims parseToken(String token) {
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
    }

    public boolean isTokenValid(String token) {
        try { parseToken(token); return true; } catch (JwtException | IllegalArgumentException e) { return false; }
    }

    public Long getUserIdFromToken(String token) { return Long.parseLong(parseToken(token).getSubject()); }
    public String getTokenType(String token) { return parseToken(token).get("type", String.class); }

    public long getRemainingTtlMs(String token) {
        return Math.max(0, parseToken(token).getExpiration().getTime() - System.currentTimeMillis());
    }

    public long getAccessTokenExpiryMs() { return accessTokenExpiryMs; }
}
