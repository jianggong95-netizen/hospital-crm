package com.hospital.crm.system.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "crm.security.login")
public record LoginPolicyProperties(
        int maxFailedAttempts,
        int lockMinutes
) {
}
