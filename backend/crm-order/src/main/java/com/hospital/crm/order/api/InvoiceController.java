package com.hospital.crm.order.api;

import com.hospital.crm.common.api.ApiResponse;
import com.hospital.crm.common.api.PageResponse;
import com.hospital.crm.order.application.InvoiceService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * 发票接口：申请、开具与红冲。
 */
@RestController
@RequestMapping("/api/v1/invoices")
public class InvoiceController {

    private final InvoiceService invoiceService;

    public InvoiceController(InvoiceService invoiceService) {
        this.invoiceService = invoiceService;
    }

    @PostMapping("/orders/{orderId}")
    @PreAuthorize("hasAuthority('crm:order:write')")
    public ApiResponse<OrderDtos.InvoiceView> apply(
            @PathVariable UUID orderId,
            @Valid @RequestBody OrderDtos.InvoiceApplyRequest request) {
        return ApiResponse.ok(invoiceService.apply(orderId, request));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('crm:payment:read')")
    public ApiResponse<PageResponse<OrderDtos.InvoiceView>> list(
            @RequestParam(required = false) UUID orderId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return ApiResponse.ok(invoiceService.list(orderId, status, page, pageSize));
    }

    @PutMapping("/{invoiceId}/issue")
    @PreAuthorize("hasAuthority('crm:payment:manage')")
    public ApiResponse<OrderDtos.InvoiceView> issue(@PathVariable UUID invoiceId) {
        return ApiResponse.ok(invoiceService.issue(invoiceId));
    }

    @PutMapping("/{invoiceId}/void")
    @PreAuthorize("hasAuthority('crm:payment:manage')")
    public ApiResponse<OrderDtos.InvoiceView> voidInvoice(
            @PathVariable UUID invoiceId,
            @Valid @RequestBody OrderDtos.ReasonRequest request) {
        return ApiResponse.ok(invoiceService.voidInvoice(invoiceId, request));
    }
}
