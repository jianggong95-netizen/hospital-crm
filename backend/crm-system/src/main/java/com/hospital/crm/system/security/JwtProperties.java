package com.hospital.crm.system.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "crm.security.jwt")
public record JwtProperties(
        String issuer,
        String signingKey,
        int accessTokenMinutes,
        int refreshTokenDays
) {
}
