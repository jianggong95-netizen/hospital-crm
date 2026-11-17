package com.hospital.crm.order.application.gateway;

import java.math.BigDecimal;

/**
 * 支付网关抽象。生产环境接入微信支付/支付宝等渠道时替换实现；
 * CRM 仅记录资金流水并全部落 CRM 主库，不写 HIS 收费表。
 */
public interface PaymentGateway {

    /**
     * 发起渠道支付，返回渠道交易号（stub 返回占位号）。
     */
    String initiatePayment(String paymentNo, String channel, BigDecimal amount);

    /**
     * 校验渠道回调签名。规范化串按固定顺序拼接：
     * "paymentNo=&amount=&success=&tradeNo="，算法 HMAC-SHA256（hex）。
     */
    boolean verifyCallback(String paymentNo, BigDecimal amount, boolean success,
                           String channelTradeNo, String signature);

    /**
     * 执行渠道退款，返回是否成功（stub 恒为成功）。
     */
    boolean executeRefund(String refundNo, String paymentNo, String channel, BigDecimal amount);
}
