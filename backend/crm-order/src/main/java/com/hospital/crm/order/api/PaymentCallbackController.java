package com.hospital.crm.order.api;

import com.hospital.crm.common.api.ApiResponse;
import com.hospital.crm.order.application.PaymentService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 渠道支付回调入口：SecurityConfig 对 POST /api/v1/payment-callbacks/** permitAll，
 * 由 HMAC-SHA256 验签保护，控制器不做用户鉴权。
 */
@RestController
@RequestMapping("/api/v1/payment-callbacks")
public class PaymentCallbackController {

    private final PaymentService paymentService;

    public PaymentCallbackController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping("/{channel}")
    public ApiResponse<OrderDtos.CallbackResult> callback(
            @PathVariable String channel,
            @Valid @RequestBody OrderDtos.CallbackRequest request) {
        return ApiResponse.ok(paymentService.handleCallback(channel, request));
    }
}
