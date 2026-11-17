package com.hospital.crm.order.api;

import com.hospital.crm.common.api.ApiResponse;
import com.hospital.crm.common.api.PageResponse;
import com.hospital.crm.order.application.OrderService;
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

import java.time.LocalDate;
import java.util.UUID;

/**
 * 统一订单接口。
 */
@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('crm:order:read')")
    public ApiResponse<PageResponse<OrderDtos.OrderSummary>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) UUID customerId,
            @RequestParam(required = false) LocalDate dateFrom,
            @RequestParam(required = false) LocalDate dateTo,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return ApiResponse.ok(orderService.list(keyword, status, customerId, dateFrom, dateTo, page, pageSize));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('crm:order:write')")
    public ApiResponse<OrderDtos.OrderSummary> create(
            @Valid @RequestBody OrderDtos.OrderCreateRequest request) {
        return ApiResponse.ok(orderService.create(request));
    }

    @GetMapping("/{orderId}")
    @PreAuthorize("hasAuthority('crm:order:read')")
    public ApiResponse<OrderDtos.OrderDetail> detail(@PathVariable UUID orderId) {
        return ApiResponse.ok(orderService.detail(orderId));
    }

    @PutMapping("/{orderId}/cancel")
    @PreAuthorize("hasAuthority('crm:order:write')")
    public ApiResponse<OrderDtos.OrderSummary> cancel(
            @PathVariable UUID orderId,
            @Valid @RequestBody OrderDtos.ReasonRequest request) {
        return ApiResponse.ok(orderService.cancel(orderId, request));
    }

    @PutMapping("/{orderId}/complete")
    @PreAuthorize("hasAuthority('crm:order:write')")
    public ApiResponse<OrderDtos.OrderSummary> complete(@PathVariable UUID orderId) {
        return ApiResponse.ok(orderService.complete(orderId));
    }
}
