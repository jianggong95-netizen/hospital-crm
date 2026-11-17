package com.hospital.crm.order.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface RefundRepository extends JpaRepository<Refund, UUID>,
        JpaSpecificationExecutor<Refund> {

    List<Refund> findByOrderIdOrderByCreatedAtDesc(UUID orderId);

    List<Refund> findByPaymentIdOrderByCreatedAtDesc(UUID paymentId);

    @Query(value = "select nextval('crm_refund_no_seq')", nativeQuery = true)
    Long nextRefundNo();

    /**
     * 渠道退款成功原子置终态：仅 APPROVED 时置 SUCCESS 并记录完成时间，
     * 返回 0 表示并发重复审核。
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Refund r set r.status = 'SUCCESS', r.completedAt = :now, "
            + "r.channelRefundNo = :channelRefundNo "
            + "where r.id = :id and r.status = 'APPROVED'")
    int markSucceeded(@Param("id") UUID id, @Param("channelRefundNo") String channelRefundNo,
                      @Param("now") Instant now);
}
