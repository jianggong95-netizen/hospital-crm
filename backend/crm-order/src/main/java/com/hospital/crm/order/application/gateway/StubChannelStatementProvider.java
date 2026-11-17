package com.hospital.crm.order.application.gateway;

import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

/**
 * 渠道账单 stub 实现：默认返回空账单（本地全部记为 LOCAL_ONLY 差异）。
 * 生产接入微信支付/支付宝时替换实现；CRM 仅记录资金流水，不写 HIS 收费表。
 */
@Component
public class StubChannelStatementProvider implements ChannelStatementProvider {

    @Override
    public List<ChannelStatementEntry> fetch(String channel, LocalDate reconcileDate) {
        return List.of();
    }
}
