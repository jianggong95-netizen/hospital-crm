package com.hospital.crm.order.application.gateway;

import org.springframework.stereotype.Component;

/**
 * 发票服务 stub 实现：恒开成功并返回占位文件引用。
 * 生产接入开票服务商时替换实现；CRM 仅记录资金流水，不写 HIS 收费表。
 */
@Component
public class StubInvoiceProvider implements InvoiceProvider {

    @Override
    public InvoiceIssueResult issue(InvoiceIssueCommand command) {
        return new InvoiceIssueResult(true, "stub-invoice", "stub 开票成功");
    }
}
