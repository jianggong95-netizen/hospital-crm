package com.hospital.crm.customer.api;

import java.time.Instant;
import java.util.UUID;

public final class CustomerIdentityDtos {

    private CustomerIdentityDtos() {
    }

    /**
     * 外部身份绑定结果，仅返回标识与核验状态，不返回任何身份值明文。
     */
    public record LinkedIdentity(
            UUID identityId,
            UUID customerId,
            String sourceSystem,
            String identityType,
            boolean verified,
            Instant validFrom,
            Instant createdAt
    ) {
    }

    /**
     * 内部身份解析结果：identityValue 为解密后的外部标识，仅供服务内部调用，
     * 禁止通过 Controller 直接返回、写日志或写入审计。
     */
    public record ResolvedIdentity(
            UUID identityId,
            UUID customerId,
            String sourceSystem,
            String identityType,
            String identityValue,
            boolean verified,
            Instant validFrom
    ) {
    }
}
