package com.hospital.crm.order.api;

import com.hospital.crm.common.api.ApiResponse;
import com.hospital.crm.common.api.PageResponse;
import com.hospital.crm.order.application.PaymentService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * 支付单接口：订单下发起支付、支付列表与详情。
 */
@RestController
@RequestMapping("/api/v1")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping("/orders/{orderId}/payments")
    @PreAuthorize("hasAuthority('crm:order:write')")
    public ApiResponse<OrderDtos.PaymentView> initiate(
            @PathVariable UUID orderId,
            @Valid @RequestBody OrderDtos.PaymentCreateRequest request) {
        return ApiResponse.ok(paymentService.initiate(orderId, request));
    }

    @GetMapping("/payments")
    @PreAuthorize("hasAuthority('crm:order:read')")
    public ApiResponse<PageResponse<OrderDtos.PaymentView>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String channel,
            @RequestParam(required = false) UUID orderId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return ApiResponse.ok(paymentService.list(keyword, status, channel, orderId, page, pageSize));
    }

    @GetMapping("/payments/{paymentId}")
    @PreAuthorize("hasAuthority('crm:order:read')")
    public ApiResponse<OrderDtos.PaymentView> detail(@PathVariable UUID paymentId) {
        return ApiResponse.ok(paymentService.detail(paymentId));
    }
}
