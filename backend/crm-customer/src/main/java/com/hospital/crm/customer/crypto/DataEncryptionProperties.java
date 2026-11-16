package com.hospital.crm.customer.crypto;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 客户敏感字段加密配置。
 *
 * @param key Base64 编码的 32 字节 AES-256 密钥，生产环境通过环境变量注入
 */
@ConfigurationProperties(prefix = "crm.security.data-encryption")
public record DataEncryptionProperties(String key) {
}
