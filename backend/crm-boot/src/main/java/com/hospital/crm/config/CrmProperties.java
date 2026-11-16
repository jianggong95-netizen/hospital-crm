package com.hospital.crm.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * 平台级配置项。
 */
@ConfigurationProperties(prefix = "crm")
public record CrmProperties(String version, Cors cors) {

    public record Cors(List<String> allowedOrigins) {
    }
}
