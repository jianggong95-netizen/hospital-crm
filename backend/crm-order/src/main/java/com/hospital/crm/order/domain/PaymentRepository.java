package com.hospital.crm.order.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentRepository extends JpaRepository<Payment, UUID>,
        JpaSpecificationExecutor<Payment> {

    Optional<Payment> findByPaymentNo(String paymentNo);

    Optional<Payment> findByIdempotencyKey(String idempotencyKey);

    Optional<Payment> findByChannelAndChannelTradeNo(String channel, String channelTradeNo);

    List<Payment> findByOrderIdOrderByCreatedAtDesc(UUID orderId);

    @Query(value = "select nextval('crm_payment_no_seq')", nativeQuery = true)
    Long nextPaymentNo();

    /**
     * 渠道账单拉取：指定渠道与支付成功时间窗内的成功支付。
     */
    @Query("select p from Payment p where p.channel = :channel and p.status = 'SUCCESS' "
            + "and p.paidAt >= :from and p.paidAt < :to")
    List<Payment> findSuccessInPeriod(@Param("channel") String channel,
                                      @Param("from") Instant from,
                                      @Param("to") Instant to);

    /**
     * 回调成功原子入账：仅 PROCESSING 时置 SUCCESS 并记录渠道交易号与回调报文哈希，
     * 返回 0 表示并发重复回调，由调用方按幂等处理。
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Payment p set p.status = 'SUCCESS', p.paidAt = :now, "
            + "p.channelTradeNo = :tradeNo, p.callbackPayloadHash = :hash "
            + "where p.id = :id and p.status = 'PROCESSING'")
    int markSuccess(@Param("id") UUID id, @Param("tradeNo") String tradeNo,
                    @Param("hash") String hash, @Param("now") Instant now);

    /**
     * 回调失败原子关闭：仅 PROCESSING 时置 FAILED。
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Payment p set p.status = 'FAILED' where p.id = :id and p.status = 'PROCESSING'")
    int markFailed(@Param("id") UUID id);

    /**
     * 退款成功原子累加 refunded_amount，退款后不得超过支付金额；
     * 返回 0 表示并发超退，由调用方拒绝。
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Payment p set p.refundedAmount = p.refundedAmount + :amt "
            + "where p.id = :id and p.refundedAmount + :amt <= p.amount")
    int accumulateRefunded(@Param("id") UUID id, @Param("amt") BigDecimal amt);
}
