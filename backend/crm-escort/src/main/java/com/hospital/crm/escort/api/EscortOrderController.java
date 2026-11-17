package com.hospital.crm.escort.api;

import com.hospital.crm.common.api.ApiResponse;
import com.hospital.crm.common.api.PageResponse;
import com.hospital.crm.escort.application.EscortOrderService;
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

@RestController
@RequestMapping("/api/v1")
public class EscortOrderController {

    private final EscortOrderService orderService;

    public EscortOrderController(EscortOrderService orderService) {
        this.orderService = orderService;
    }

    @GetMapping("/escort-orders")
    @PreAuthorize("hasAuthority('crm:escortorder:read')")
    public ApiResponse<PageResponse<EscortDtos.OrderSummary>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) UUID escortId,
            @RequestParam(required = false) UUID customerId,
            @RequestParam(required = false) LocalDate dateFrom,
            @RequestParam(required = false) LocalDate dateTo,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return ApiResponse.ok(orderService.list(keyword, status, escortId, customerId,
                dateFrom, dateTo, page, pageSize));
    }

    @PostMapping("/escort-orders")
    @PreAuthorize("hasAuthority('crm:escortorder:write')")
    public ApiResponse<EscortDtos.OrderSummary> create(
            @Valid @RequestBody EscortDtos.OrderCreateRequest request) {
        return ApiResponse.ok(orderService.create(request));
    }

    @GetMapping("/escort-orders/{orderId}")
    @PreAuthorize("hasAuthority('crm:escortorder:read')")
    public ApiResponse<EscortDtos.OrderDetail> detail(@PathVariable UUID orderId) {
        return ApiResponse.ok(orderService.detail(orderId));
    }

    @PutMapping("/escort-orders/{orderId}/dispatch")
    @PreAuthorize("hasAuthority('crm:escortorder:write')")
    public ApiResponse<EscortDtos.OrderSummary> dispatch(
            @PathVariable UUID orderId,
            @Valid @RequestBody EscortDtos.DispatchRequest request) {
        return ApiResponse.ok(orderService.dispatch(orderId, request));
    }

    @PutMapping("/escort-orders/{orderId}/accept")
    @PreAuthorize("hasAuthority('crm:escortorder:write')")
    public ApiResponse<EscortDtos.OrderSummary> accept(@PathVariable UUID orderId) {
        return ApiResponse.ok(orderService.accept(orderId));
    }

    @PutMapping("/escort-orders/{orderId}/reject")
    @PreAuthorize("hasAuthority('crm:escortorder:write')")
    public ApiResponse<EscortDtos.OrderSummary> reject(
            @PathVariable UUID orderId,
            @Valid @RequestBody EscortDtos.ReasonRequest request) {
        return ApiResponse.ok(orderService.reject(orderId, request));
    }

    @PutMapping("/escort-orders/{orderId}/start")
    @PreAuthorize("hasAuthority('crm:escortorder:manage')")
    public ApiResponse<EscortDtos.OrderSummary> start(@PathVariable UUID orderId) {
        return ApiResponse.ok(orderService.start(orderId));
    }

    @PutMapping("/escort-orders/{orderId}/finish")
    @PreAuthorize("hasAuthority('crm:escortorder:manage')")
    public ApiResponse<EscortDtos.OrderSummary> finish(
            @PathVariable UUID orderId,
            @Valid @RequestBody(required = false) EscortDtos.FinishRequest request) {
        return ApiResponse.ok(orderService.finish(orderId, request));
    }

    @PutMapping("/escort-orders/{orderId}/cancel")
    @PreAuthorize("hasAuthority('crm:escortorder:manage')")
    public ApiResponse<EscortDtos.OrderSummary> cancel(
            @PathVariable UUID orderId,
            @Valid @RequestBody EscortDtos.ReasonRequest request) {
        return ApiResponse.ok(orderService.cancel(orderId, request));
    }

    @PostMapping("/escort-orders/{orderId}/review")
    @PreAuthorize("hasAuthority('crm:escortorder:manage')")
    public ApiResponse<EscortDtos.OrderReviewView> review(
            @PathVariable UUID orderId,
            @Valid @RequestBody EscortDtos.ReviewRequest request) {
        return ApiResponse.ok(orderService.review(orderId, request));
    }

    @GetMapping("/escort-reviews")
    @PreAuthorize("hasAuthority('crm:escort:read')")
    public ApiResponse<PageResponse<EscortDtos.ReviewView>> reviews(
            @RequestParam(required = false) UUID escortId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return ApiResponse.ok(orderService.reviews(escortId, page, pageSize));
    }
}
