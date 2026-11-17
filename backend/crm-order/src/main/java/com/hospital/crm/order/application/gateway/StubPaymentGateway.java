package com.hospital.crm.order.application.gateway;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * 支付网关 stub 实现：不接真实渠道，仅按 HMAC-SHA256 验签并返回占位交易号。
 * 生产接入微信支付/支付宝时替换实现；CRM 仅记录资金流水，不写 HIS 收费表。
 */
@Component
public class StubPaymentGateway implements PaymentGateway {

    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private final String callbackSecret;

    public StubPaymentGateway(@Value("${crm.payment.callback-secret:crm_dev_callback_secret}") String callbackSecret) {
        this.callbackSecret = callbackSecret;
    }

    @Override
    public String initiatePayment(String paymentNo, String channel, BigDecimal amount) {
        return "STUB-" + paymentNo;
    }

    @Override
    public boolean verifyCallback(String paymentNo, BigDecimal amount, boolean success,
                                  String channelTradeNo, String signature) {
        if (signature == null || signature.isBlank()) {
            return false;
        }
        String canonical = canonicalString(paymentNo, amount, success, channelTradeNo);
        String expected = sign(canonical, callbackSecret);
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                signature.trim().toLowerCase().getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public boolean executeRefund(String refundNo, String paymentNo, String channel, BigDecimal amount) {
        return true;
    }

    /**
     * 回调规范化串，顺序固定为 paymentNo、amount、success、tradeNo。
     */
    public static String canonicalString(String paymentNo, BigDecimal amount, boolean success, String channelTradeNo) {
        return "paymentNo=" + paymentNo
                + "&amount=" + (amount == null ? "" : amount.toPlainString())
                + "&success=" + success
                + "&tradeNo=" + (channelTradeNo == null ? "" : channelTradeNo);
    }

    /**
     * 对规范化串做 HMAC-SHA256（hex 小写），测试与渠道侧对接可复用。
     */
    public static String sign(String canonical, String secret) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            return toHex(mac.doFinal(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("回调验签失败", e);
        }
    }

    private static String toHex(byte[] bytes) {
        StringBuilder builder = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            builder.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
        }
        return builder.toString();
    }
}
