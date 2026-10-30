package com.warehouse.gateway.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;

class JwtUtilTest {

    private static final String SECRET =
            "OmniStockSecretKeyForJWTAuthentication2026VerySecure";
    private static final String ISSUER = "omnistock-auth";

    private final JwtUtil jwtUtil = new JwtUtil(SECRET, ISSUER);

    private String token(String issuer, long expiresInMs) {
        SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        return Jwts.builder()
                .subject("manager")
                .issuer(issuer)
                .claim("role", "MANAGER")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expiresInMs))
                .signWith(key)
                .compact();
    }

    @Test
    void acceptsTokenSignedByAuthService() {
        assertThat(jwtUtil.validateToken(token(ISSUER, 60_000))).isTrue();
        assertThat(jwtUtil.getRole(token(ISSUER, 60_000))).isEqualTo("MANAGER");
    }

    @Test
    void rejectsExpiredToken() {
        assertThat(jwtUtil.validateToken(token(ISSUER, -60_000))).isFalse();
    }

    @Test
    void rejectsTokenFromUnexpectedIssuer() {
        assertThat(jwtUtil.validateToken(token("some-other-issuer", 60_000))).isFalse();
    }

    @Test
    void rejectsMalformedToken() {
        assertThat(jwtUtil.validateToken("not-a-jwt")).isFalse();
    }
}
