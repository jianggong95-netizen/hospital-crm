package com.hospital.crm.system.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.UUID;

@Service
public class JwtService {

    private final JwtProperties properties;
    private final SecretKey signingKey;

    public JwtService(JwtProperties properties) {
        this.properties = properties;
        String key = properties.signingKey();
        if (key == null || key.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("CRM_JWT_SIGNING_KEY 未配置或长度不足 32 字节，服务拒绝启动");
        }
        this.signingKey = Keys.hmacShaKeyFor(key.getBytes(StandardCharsets.UTF_8));
    }

    public String issueAccessToken(UserPrincipal principal) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(properties.accessTokenMinutes(), ChronoUnit.MINUTES);
        String issuer = properties.issuer() == null || properties.issuer().isBlank()
                ? "hospital-crm" : properties.issuer();
        return Jwts.builder()
                .subject(principal.userId().toString())
                .issuer(issuer)
                .claim("username", principal.username())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .id(UUID.randomUUID().toString())
                .signWith(signingKey)
                .compact();
    }

    public UUID parseUserId(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(signingKey)
                .requireIssuer(properties.issuer() == null || properties.issuer().isBlank()
                        ? "hospital-crm" : properties.issuer())
                .build()
                .parseSignedClaims(token)
                .getPayload();
        return UUID.fromString(claims.getSubject());
    }

    public long accessTokenSeconds() {
        return properties.accessTokenMinutes() * 60L;
    }

    public int refreshTokenDays() {
        return properties.refreshTokenDays();
    }
}
