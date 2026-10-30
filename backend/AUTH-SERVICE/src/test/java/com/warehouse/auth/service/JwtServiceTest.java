package com.warehouse.auth.service;

import com.warehouse.auth.entity.Role;
import com.warehouse.auth.entity.User;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    private static final String SECRET =
            "OmniStockSecretKeyForJWTAuthentication2026VerySecure";

    private final JwtService jwtService =
            new JwtService(SECRET, 3_600_000, "omnistock-auth");

    private User user() {
        User user = new User("manager", "encoded", "Warehouse Manager", Role.MANAGER);
        user.setId(7L);
        return user;
    }

    @Test
    void generatedTokenContainsIdentityRoleAndIssuer() {
        String token = jwtService.generateToken(user());

        Claims claims = jwtService.parseClaims(token);

        assertThat(claims.getSubject()).isEqualTo("manager");
        assertThat(claims.getIssuer()).isEqualTo("omnistock-auth");
        assertThat(claims.get("role", String.class)).isEqualTo("MANAGER");
        assertThat(((Number) claims.get("userId")).longValue()).isEqualTo(7L);
        assertThat(claims.getExpiration()).isAfter(claims.getIssuedAt());
    }

    @Test
    void extractUsernameReadsTokenSubject() {
        String token = jwtService.generateToken(user());

        assertThat(jwtService.extractUsername(token)).isEqualTo("manager");
    }

    @Test
    void tokenSignedWithDifferentSecretIsRejected() {
        JwtService otherService =
                new JwtService("ACompletelyDifferentSecretKeyForTesting123456", 60_000, "omnistock-auth");
        String foreignToken = otherService.generateToken(user());

        assertThatThrownBy(() -> jwtService.parseClaims(foreignToken))
                .isInstanceOf(Exception.class);
    }
}
