package com.hospital.crm.homevisit.api;

import com.hospital.crm.common.api.ApiResponse;
import com.hospital.crm.common.api.PageResponse;
import com.hospital.crm.homevisit.application.HomeVisitOrderService;
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
 * 上门服务订单与异常处置接口。
 */
@RestController
@RequestMapping("/api/v1")
public class HomeVisitOrderController {

    private final HomeVisitOrderService orderService;

    public HomeVisitOrderController(HomeVisitOrderService orderService) {
        this.orderService = orderService;
    }

    @GetMapping("/home-visit-orders")
    @PreAuthorize("hasAuthority('crm:homevisit:read')")
    public ApiResponse<PageResponse<HomeVisitDtos.OrderSummary>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String serviceCode,
            @RequestParam(required = false) UUID customerId,
            @RequestParam(required = false) UUID staffUserId,
            @RequestParam(required = false) LocalDate dateFrom,
            @RequestParam(required = false) LocalDate dateTo,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return ApiResponse.ok(orderService.list(keyword, status, serviceCode, customerId,
                staffUserId, dateFrom, dateTo, page, pageSize));
    }

    @PostMapping("/home-visit-orders")
    @PreAuthorize("hasAuthority('crm:homevisit:write')")
    public ApiResponse<HomeVisitDtos.OrderSummary> create(
            @Valid @RequestBody HomeVisitDtos.OrderCreateRequest request) {
        return ApiResponse.ok(orderService.create(request));
    }

    @GetMapping("/home-visit-orders/{orderId}")
    @PreAuthorize("hasAuthority('crm:homevisit:read')")
    public ApiResponse<HomeVisitDtos.OrderDetail> detail(@PathVariable UUID orderId) {
        return ApiResponse.ok(orderService.detail(orderId));
    }

    @PutMapping("/home-visit-orders/{orderId}/review")
    @PreAuthorize("hasAuthority('crm:homevisit:manage')")
    public ApiResponse<HomeVisitDtos.OrderSummary> review(
            @PathVariable UUID orderId,
            @Valid @RequestBody HomeVisitDtos.OrderReviewRequest request) {
        return ApiResponse.ok(orderService.review(orderId, request));
    }

    @PutMapping("/home-visit-orders/{orderId}/dispatch")
    @PreAuthorize("hasAuthority('crm:homevisit:write')")
    public ApiResponse<HomeVisitDtos.OrderSummary> dispatch(
            @PathVariable UUID orderId,
            @Valid @RequestBody HomeVisitDtos.DispatchRequest request) {
        return ApiResponse.ok(orderService.dispatch(orderId, request));
    }

    @PutMapping("/home-visit-orders/{orderId}/accept")
    @PreAuthorize("hasAuthority('crm:homevisit:write')")
    public ApiResponse<HomeVisitDtos.OrderSummary> accept(@PathVariable UUID orderId) {
        return ApiResponse.ok(orderService.accept(orderId));
    }

    @PutMapping("/home-visit-orders/{orderId}/reject")
    @PreAuthorize("hasAuthority('crm:homevisit:write')")
    public ApiResponse<HomeVisitDtos.OrderSummary> reject(
            @PathVariable UUID orderId,
            @Valid @RequestBody HomeVisitDtos.ReasonRequest request) {
        return ApiResponse.ok(orderService.reject(orderId, request));
    }

    @PutMapping("/home-visit-orders/{orderId}/start")
    @PreAuthorize("hasAuthority('crm:homevisit:write')")
    public ApiResponse<HomeVisitDtos.OrderSummary> start(@PathVariable UUID orderId) {
        return ApiResponse.ok(orderService.start(orderId));
    }

    @PostMapping("/home-visit-orders/{orderId}/executions")
    @PreAuthorize("hasAuthority('crm:homevisit:write')")
    public ApiResponse<HomeVisitDtos.ExecutionView> addExecution(
            @PathVariable UUID orderId,
            @Valid @RequestBody HomeVisitDtos.ExecutionCreateRequest request) {
        return ApiResponse.ok(orderService.addExecution(orderId, request));
    }

    @PostMapping("/home-visit-orders/{orderId}/exceptions")
    @PreAuthorize("hasAuthority('crm:homevisit:write')")
    public ApiResponse<HomeVisitDtos.ExceptionView> reportException(
            @PathVariable UUID orderId,
            @Valid @RequestBody HomeVisitDtos.ExceptionCreateRequest request) {
        return ApiResponse.ok(orderService.reportException(orderId, request));
    }

    @PutMapping("/home-visit-exceptions/{exceptionId}/escalate")
    @PreAuthorize("hasAuthority('crm:homevisit:manage')")
    public ApiResponse<HomeVisitDtos.ExceptionView> escalateException(
            @PathVariable UUID exceptionId,
            @Valid @RequestBody HomeVisitDtos.ReasonRequest request) {
        return ApiResponse.ok(orderService.escalateException(exceptionId, request));
    }

    @PutMapping("/home-visit-exceptions/{exceptionId}/resolve")
    @PreAuthorize("hasAuthority('crm:homevisit:manage')")
    public ApiResponse<HomeVisitDtos.ExceptionView> resolveException(
            @PathVariable UUID exceptionId,
            @Valid @RequestBody HomeVisitDtos.ResolveRequest request) {
        return ApiResponse.ok(orderService.resolveException(exceptionId, request));
    }

    @PutMapping("/home-visit-orders/{orderId}/finish")
    @PreAuthorize("hasAuthority('crm:homevisit:write')")
    public ApiResponse<HomeVisitDtos.OrderSummary> finish(
            @PathVariable UUID orderId,
            @Valid @RequestBody(required = false) HomeVisitDtos.FinishRequest request) {
        return ApiResponse.ok(orderService.finish(orderId, request));
    }

    @PutMapping("/home-visit-orders/{orderId}/cancel")
    @PreAuthorize("hasAuthority('crm:homevisit:manage')")
    public ApiResponse<HomeVisitDtos.OrderSummary> cancel(
            @PathVariable UUID orderId,
            @Valid @RequestBody HomeVisitDtos.ReasonRequest request) {
        return ApiResponse.ok(orderService.cancel(orderId, request));
    }

    @GetMapping("/home-visit-exceptions")
    @PreAuthorize("hasAuthority('crm:homevisit:read')")
    public ApiResponse<PageResponse<HomeVisitDtos.ExceptionView>> listExceptions(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String level,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return ApiResponse.ok(orderService.listExceptions(status, level, page, pageSize));
    }
}
