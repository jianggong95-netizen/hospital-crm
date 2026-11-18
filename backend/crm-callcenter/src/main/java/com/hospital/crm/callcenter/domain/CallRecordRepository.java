package com.hospital.crm.callcenter.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface CallRecordRepository
        extends JpaRepository<CallRecord, UUID>, JpaSpecificationExecutor<CallRecord> {

    /** 厂商通话唯一号，用于同步入库幂等去重。 */
    Optional<CallRecord> findByVendorCallId(String vendorCallId);

    @Query(value = "select nextval('crm_call_no_seq')", nativeQuery = true)
    long nextCallNo();

    /** 来电弹屏：按主叫哈希统计历史通话次数。 */
    long countByCallerHash(String callerHash);

    /** 来电弹屏：取该主叫号码最近一次通话。 */
    Optional<CallRecord> findFirstByCallerHashOrderByStartedAtDesc(String callerHash);

    /**
     * 关联更新：link 目标信息整体覆盖，customerId 由服务层决定（非客户目标保持原值传入）。
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update CallRecord c set c.linkType = :linkType, c.linkId = :linkId, c.linkNo = :linkNo, "
            + "c.customerId = :customerId, c.updatedAt = :now where c.id = :id")
    int linkRecord(@Param("id") UUID id, @Param("linkType") String linkType, @Param("linkId") UUID linkId,
                   @Param("linkNo") String linkNo, @Param("customerId") UUID customerId,
                   @Param("now") Instant now);
}
