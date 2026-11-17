package com.hospital.crm.homevisit.application;

import com.hospital.crm.common.api.ErrorCode;
import com.hospital.crm.common.exception.BusinessException;
import com.hospital.crm.customer.application.CustomerService;
import com.hospital.crm.customer.crypto.SensitiveDataCipher;
import com.hospital.crm.customer.crypto.SensitiveDataHasher;
import com.hospital.crm.customer.support.CustomerMasking;
import com.hospital.crm.homevisit.api.HomeVisitDtos;
import com.hospital.crm.homevisit.domain.HomeAddress;
import com.hospital.crm.homevisit.domain.HomeAddressRepository;
import com.hospital.crm.system.audit.AuditService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 客户服务地址维护：沿用预约中心相同的加密工具（AES-GCM 密文 + HMAC 哈希 + 脱敏三列），
 * 每客户最多一个启用状态的默认地址，设为默认时自动取消该客户其他默认地址。
 */
@Service
public class HomeAddressService {

    private static final String RESOURCE = "HOME_ADDRESS";

    private final HomeAddressRepository addressRepository;
    private final CustomerService customerService;
    private final SensitiveDataCipher cipher;
    private final SensitiveDataHasher hasher;
    private final AuditService auditService;

    public HomeAddressService(HomeAddressRepository addressRepository,
                              CustomerService customerService,
                              SensitiveDataCipher cipher,
                              SensitiveDataHasher hasher,
                              AuditService auditService) {
        this.addressRepository = addressRepository;
        this.customerService = customerService;
        this.cipher = cipher;
        this.hasher = hasher;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<HomeVisitDtos.HomeAddressView> list(UUID customerId) {
        return addressRepository.findByCustomerIdOrderByIsDefaultDescCreatedAtDesc(customerId).stream()
                .map(this::toView)
                .toList();
    }

    @Transactional
    public HomeVisitDtos.HomeAddressView create(HomeVisitDtos.HomeAddressCreateRequest request) {
        // 客户不存在时 brief 抛出 RESOURCE_NOT_FOUND（404）
        customerService.brief(request.customerId());
        boolean isDefault = Boolean.TRUE.equals(request.isDefault());
        if (isDefault) {
            clearOtherDefaults(request.customerId(), null);
        }

        HomeAddress address = new HomeAddress();
        address.setCustomerId(request.customerId());
        address.setAddress(request.address().trim());
        address.setContactName(request.contactName().trim());
        applyPhone(address, request.contactPhone());
        address.setDefault(isDefault);
        address.setStatus("active");
        address.setRemark(trimToNull(request.remark()));
        address = addressRepository.save(address);

        Map<String, Object> audit = new LinkedHashMap<>();
        audit.put("addressId", address.getId().toString());
        auditService.record("HOMEADDR_CREATE", RESOURCE, address.getId().toString(), audit);

        return toView(address);
    }

    @Transactional
    public HomeVisitDtos.HomeAddressView update(UUID addressId,
                                                HomeVisitDtos.HomeAddressUpdateRequest request) {
        HomeAddress address = load(addressId);
        if (request.address() != null && !request.address().isBlank()) {
            address.setAddress(request.address().trim());
        }
        if (request.contactName() != null && !request.contactName().isBlank()) {
            address.setContactName(request.contactName().trim());
        }
        if (request.contactPhone() != null) {
            applyPhone(address, request.contactPhone());
        }
        if (request.isDefault() != null) {
            if (request.isDefault()) {
                clearOtherDefaults(address.getCustomerId(), addressId);
            }
            address.setDefault(request.isDefault());
        }
        if (request.remark() != null) {
            address.setRemark(trimToNull(request.remark()));
        }
        address = addressRepository.save(address);

        Map<String, Object> audit = new LinkedHashMap<>();
        audit.put("addressId", addressId.toString());
        auditService.record("HOMEADDR_UPDATE", RESOURCE, addressId.toString(), audit);

        return toView(address);
    }

    @Transactional
    public HomeVisitDtos.HomeAddressView changeStatus(UUID addressId,
                                                      HomeVisitDtos.HomeAddressStatusRequest request) {
        HomeAddress address = load(addressId);
        if ("disabled".equals(request.status()) && address.isDefault()) {
            // 停用默认地址以释放默认位，避免与部分唯一索引语义冲突
            address.setDefault(false);
        }
        address.setStatus(request.status());
        address = addressRepository.save(address);

        Map<String, Object> audit = new LinkedHashMap<>();
        audit.put("addressId", addressId.toString());
        audit.put("status", address.getStatus());
        auditService.record("HOMEADDR_STATUS", RESOURCE, addressId.toString(), audit);

        return toView(address);
    }

    @Transactional(readOnly = true)
    public HomeAddress load(UUID addressId) {
        return addressRepository.findById(addressId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "服务地址不存在"));
    }

    /**
     * 取消该客户其他默认地址；excludeId 非空时跳过自身（更新场景）。
     */
    private void clearOtherDefaults(UUID customerId, UUID excludeId) {
        List<HomeAddress> defaults =
                addressRepository.findByCustomerIdAndIsDefaultTrueAndStatus(customerId, "active");
        for (HomeAddress current : defaults) {
            if (excludeId != null && current.getId().equals(excludeId)) {
                continue;
            }
            current.setDefault(false);
            // saveAndFlush 保证旧行 UPDATE 先于新默认地址的 INSERT 执行，避免撞部分唯一索引
            addressRepository.saveAndFlush(current);
        }
    }

    private void applyPhone(HomeAddress address, String phone) {
        String normalized = CustomerMasking.normalizePhone(phone);
        if (normalized == null) {
            address.setContactPhoneEncrypted(null);
            address.setContactPhoneHash(null);
            address.setContactPhoneMasked(null);
            return;
        }
        address.setContactPhoneEncrypted(cipher.encrypt(normalized));
        address.setContactPhoneHash(hasher.hash(normalized));
        address.setContactPhoneMasked(CustomerMasking.maskPhone(normalized));
    }

    private HomeVisitDtos.HomeAddressView toView(HomeAddress address) {
        return new HomeVisitDtos.HomeAddressView(
                address.getId(),
                address.getCustomerId(),
                address.getAddress(),
                address.getContactName(),
                address.getContactPhoneMasked(),
                address.isDefault(),
                address.getStatus(),
                address.getRemark());
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
