package com.hospital.crm.order.application.gateway;

import java.math.BigDecimal;

/**
 * 发票服务提供者抽象。生产环境接入税务开票服务商时替换实现；
 * CRM 仅记录开票流水与票面文件引用，不写 HIS 收费表。
 */
public interface InvoiceProvider {

    /**
     * 开具发票，返回开票结果（成功时携带票面文件引用）。
     */
    InvoiceIssueResult issue(InvoiceIssueCommand command);

    /**
     * 开票指令。
     */
    record InvoiceIssueCommand(String invoiceNo, String orderNo, String title, String taxNo,
                               BigDecimal amount, String invoiceType) {
    }

    /**
     * 开票结果。
     */
    record InvoiceIssueResult(boolean success, String fileRef, String message) {
    }
}
