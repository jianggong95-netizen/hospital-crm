package com.hospital.crm.customer.application;

import com.hospital.crm.common.api.ErrorCode;
import com.hospital.crm.common.exception.BusinessException;
import com.hospital.crm.customer.api.CustomerIdentityDtos;
import com.hospital.crm.customer.crypto.SensitiveDataCipher;
import com.hospital.crm.customer.crypto.SensitiveDataHasher;
import com.hospital.crm.customer.domain.CustomerIdentity;
import com.hospital.crm.customer.domain.CustomerIdentityRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * 客户外部身份绑定：HIS 患者主索引等外部标识只保存密文与检索哈希。
 * 同一外部标识只允许绑定一个 CRM 客户，重复绑定返回冲突。
 */
@Service
public class CustomerIdentityService {

    private final CustomerIdentityRepository identityRepository;
    private final SensitiveDataCipher cipher;
    private final SensitiveDataHasher hasher;

    public CustomerIdentityService(CustomerIdentityRepository identityRepository,
                                   SensitiveDataCipher cipher,
                                   SensitiveDataHasher hasher) {
        this.identityRepository = identityRepository;
        this.cipher = cipher;
        this.hasher = hasher;
    }

    @Transactional
    public CustomerIdentityDtos.LinkedIdentity link(java.util.UUID customerId,
                                                    String sourceSystem,
                                                    String identityType,
                                                    String identityValue,
                                                    boolean verified) {
        String normalized = normalize(identityValue);
        if (normalized == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "外部患者标识不能为空");
        }
        String hash = hasher.hash(normalized);
        Optional<CustomerIdentity> existing = identityRepository
                .findFirstBySourceSystemAndIdentityTypeAndIdentityValueHash(sourceSystem, identityType, hash);
        if (existing.isPresent()) {
            CustomerIdentity identity = existing.get();
            if (!identity.getCustomerId().equals(customerId)) {
                throw new BusinessException(ErrorCode.BUSINESS_CONFLICT,
                        "该外部患者标识已绑定其他客户，请联系管理员核验");
            }
            if (verified && !identity.isVerified()) {
                identity.setVerified(true);
                identity = identityRepository.save(identity);
            }
            return toDto(identity);
        }

        CustomerIdentity identity = new CustomerIdentity();
        identity.setCustomerId(customerId);
        identity.setSourceSystem(sourceSystem);
        identity.setIdentityType(identityType);
        identity.setIdentityValueEncrypted(cipher.encrypt(normalized));
        identity.setIdentityValueHash(hash);
        identity.setVerified(verified);
        identity = identityRepository.save(identity);
        return toDto(identity);
    }

    /**
     * 按客户解析指定的有效外部身份，供患者360等内部聚合服务调用。
     * 返回值包含解密后的外部标识，禁止直接对外暴露。
     */
    @Transactional(readOnly = true)
    public Optional<CustomerIdentityDtos.ResolvedIdentity> resolve(java.util.UUID customerId,
                                                                   String sourceSystem,
                                                                   String identityType) {
        java.time.Instant now = java.time.Instant.now();
        return identityRepository.findByCustomerId(customerId).stream()
                .filter(identity -> sourceSystem.equals(identity.getSourceSystem()))
                .filter(identity -> identityType.equals(identity.getIdentityType()))
                .filter(identity -> identity.getValidTo() == null || identity.getValidTo().isAfter(now))
                .findFirst()
                .map(identity -> new CustomerIdentityDtos.ResolvedIdentity(
                        identity.getId(),
                        identity.getCustomerId(),
                        identity.getSourceSystem(),
                        identity.getIdentityType(),
                        cipher.decrypt(identity.getIdentityValueEncrypted()),
                        identity.isVerified(),
                        identity.getValidFrom()));
    }

    @Transactional(readOnly = true)
    public Optional<CustomerIdentityDtos.LinkedIdentity> find(String sourceSystem,
                                                              String identityType,
                                                              String identityValue) {
        String normalized = normalize(identityValue);
        if (normalized == null) {
            return Optional.empty();
        }
        return identityRepository
                .findFirstBySourceSystemAndIdentityTypeAndIdentityValueHash(
                        sourceSystem, identityType, hasher.hash(normalized))
                .map(this::toDto);
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private CustomerIdentityDtos.LinkedIdentity toDto(CustomerIdentity identity) {
        return new CustomerIdentityDtos.LinkedIdentity(
                identity.getId(),
                identity.getCustomerId(),
                identity.getSourceSystem(),
                identity.getIdentityType(),
                identity.isVerified(),
                identity.getValidFrom(),
                identity.getCreatedAt()
        );
    }
}
