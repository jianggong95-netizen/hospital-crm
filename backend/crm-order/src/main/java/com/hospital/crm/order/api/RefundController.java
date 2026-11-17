package com.hospital.crm.order.api;

import com.hospital.crm.common.api.ApiResponse;
import com.hospital.crm.common.api.PageResponse;
import com.hospital.crm.order.application.RefundService;
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
 * 退款接口：支付单下申请退款、退款列表与审核。
 */
@RestController
@RequestMapping("/api/v1")
public class RefundController {

    private final RefundService refundService;

    public RefundController(RefundService refundService) {
        this.refundService = refundService;
    }

    @PostMapping("/payments/{paymentId}/refunds")
    @PreAuthorize("hasAuthority('crm:order:write')")
    public ApiResponse<OrderDtos.RefundView> apply(
            @PathVariable UUID paymentId,
            @Valid @RequestBody OrderDtos.RefundApplyRequest request) {
        return ApiResponse.ok(refundService.apply(paymentId, request));
    }

    @GetMapping("/refunds")
    @PreAuthorize("hasAuthority('crm:payment:read')")
    public ApiResponse<PageResponse<OrderDtos.RefundView>> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) UUID orderId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return ApiResponse.ok(refundService.list(status, orderId, page, pageSize));
    }

    @GetMapping("/refunds/{refundId}")
    @PreAuthorize("hasAuthority('crm:payment:read')")
    public ApiResponse<OrderDtos.RefundView> detail(@PathVariable UUID refundId) {
        return ApiResponse.ok(refundService.detail(refundId));
    }

    @PutMapping("/refunds/{refundId}/approve")
    @PreAuthorize("hasAuthority('crm:payment:manage')")
    public ApiResponse<OrderDtos.RefundView> approve(@PathVariable UUID refundId) {
        return ApiResponse.ok(refundService.approve(refundId));
    }

    @PutMapping("/refunds/{refundId}/reject")
    @PreAuthorize("hasAuthority('crm:payment:manage')")
    public ApiResponse<OrderDtos.RefundView> reject(
            @PathVariable UUID refundId,
            @Valid @RequestBody OrderDtos.ReasonRequest request) {
        return ApiResponse.ok(refundService.reject(refundId, request));
    }
}
