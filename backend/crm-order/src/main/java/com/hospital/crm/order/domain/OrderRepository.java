package com.hospital.crm.order.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface OrderRepository extends JpaRepository<Order, UUID>,
        JpaSpecificationExecutor<Order> {

    Optional<Order> findByOrderNo(String orderNo);

    Optional<Order> findByIdempotencyKey(String idempotencyKey);

    @Query(value = "select nextval('crm_order_no_seq')", nativeQuery = true)
    Long nextOrderNo();

    /**
     * 支付成功原子入账：仅 CREATED/PAID 且入账后不超过应付金额时累加 paid_amount，
     * 付清自动置 PAID。返回 0 表示金额校验失败或状态不允许入账，由调用方拒绝。
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Order o set o.paidAmount = o.paidAmount + :amt, "
            + "o.status = case when o.paidAmount + :amt >= o.payableAmount then 'PAID' else o.status end, "
            + "o.updatedAt = :now "
            + "where o.id = :id and o.status in ('CREATED','PAID') and o.paidAmount + :amt <= o.payableAmount")
    int accumulatePaid(@Param("id") UUID id, @Param("amt") BigDecimal amt, @Param("now") Instant now);

    /**
     * 退款成功原子累加 refunded_amount：仅已支付相关状态且退款后不超过实付金额时生效，
     * 退清置 REFUNDED，否则 PARTIAL_REFUNDED。返回 0 表示并发超退，由调用方拒绝。
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Order o set o.refundedAmount = o.refundedAmount + :amt, "
            + "o.status = case when o.refundedAmount + :amt >= o.paidAmount then 'REFUNDED' else 'PARTIAL_REFUNDED' end, "
            + "o.updatedAt = :now "
            + "where o.id = :id and o.refundedAmount + :amt <= o.paidAmount "
            + "and o.status in ('PAID','COMPLETED','PARTIAL_REFUNDED')")
    int accumulateRefunded(@Param("id") UUID id, @Param("amt") BigDecimal amt, @Param("now") Instant now);
}
