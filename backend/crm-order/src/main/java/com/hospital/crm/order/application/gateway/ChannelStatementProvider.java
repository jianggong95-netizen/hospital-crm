package com.hospital.crm.order.application.gateway;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 渠道账单提供者抽象。生产环境从微信支付/支付宝对账单文件解析后实现本接口；
 * CRM 仅记录资金流水，不写 HIS 收费表。
 */
public interface ChannelStatementProvider {

    /**
     * 拉取指定渠道与账期的账单明细。
     */
    List<ChannelStatementEntry> fetch(String channel, LocalDate reconcileDate);

    /**
     * 渠道账单行：渠道交易号 + 金额（paymentNo 可选，用于交叉核对）。
     */
    record ChannelStatementEntry(String channelTradeNo, String paymentNo, BigDecimal amount) {
    }
}
