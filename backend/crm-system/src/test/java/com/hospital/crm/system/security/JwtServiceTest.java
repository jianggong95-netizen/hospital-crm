package com.hospital.crm.system.security;

import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JwtServiceTest {

    private static final String SIGNING_KEY = "unit-test-only-hospital-crm-signing-key-32bytes";

    private final JwtService jwtService = new JwtService(
            new JwtProperties("hospital-crm", SIGNING_KEY, 15, 7));

    @Test
    void issuesTokenAndParsesUserId() {
        UUID userId = UUID.randomUUID();
        UserPrincipal principal = new UserPrincipal(userId, "admin", "系统管理员", null, null, null,
                Set.of("SUPER_ADMIN"), Set.of("system:user:read"), List.of());

        String token = jwtService.issueAccessToken(principal);

        assertEquals(userId, jwtService.parseUserId(token));
        assertEquals(900, jwtService.accessTokenSeconds());
    }

    @Test
    void rejectsTokenSignedWithAnotherKey() {
        UserPrincipal principal = new UserPrincipal(UUID.randomUUID(), "admin", "系统管理员", null, null, null,
                Set.of(), Set.of(), List.of());
        String token = jwtService.issueAccessToken(principal);

        JwtService other = new JwtService(new JwtProperties("hospital-crm", SIGNING_KEY + "-other", 15, 7));
        assertThrows(JwtException.class, () -> other.parseUserId(token));
    }

    @Test
    void rejectsTooShortSigningKeyAtStartup() {
        assertThrows(IllegalStateException.class,
                () -> new JwtService(new JwtProperties("hospital-crm", "too-short", 15, 7)));
    }
}
