package com.hospital.crm.customer.crypto;

import com.hospital.crm.common.api.ErrorCode;
import com.hospital.crm.common.exception.BusinessException;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HexFormat;

/**
 * 敏感字段检索哈希：HMAC-SHA-256，保证相同规范化输入得到稳定哈希且不可逆。
 */
@Component
public class SensitiveDataHasher {

    private final SecretKeySpec hashKey;

    public SensitiveDataHasher(DataEncryptionProperties properties) {
        this.hashKey = new SecretKeySpec(deriveHashKey(properties.key()), "HmacSHA256");
    }

    public String hash(String normalizedValue) {
        if (normalizedValue == null || normalizedValue.isBlank()) {
            return null;
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(hashKey);
            return HexFormat.of().formatHex(mac.doFinal(normalizedValue.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "敏感数据哈希计算失败");
        }
    }

    /**
     * 由加密密钥派生独立的 HMAC 密钥，避免直接复用加密密钥。
     */
    private byte[] deriveHashKey(String base64Key) {
        if (base64Key == null || base64Key.isBlank()) {
            throw new IllegalStateException("crm.security.data-encryption.key 未配置");
        }
        try {
            byte[] encryptionKey = Base64.getDecoder().decode(base64Key.trim());
            if (encryptionKey.length != 32) {
                throw new IllegalStateException("crm.security.data-encryption.key 必须解码为 32 字节");
            }
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(encryptionKey);
            digest.update("hospital-crm:sensitive-hash".getBytes(StandardCharsets.UTF_8));
            return digest.digest();
        } catch (GeneralSecurityException | IllegalArgumentException ex) {
            throw new IllegalStateException("无法由加密密钥派生检索哈希密钥", ex);
        }
    }
}
