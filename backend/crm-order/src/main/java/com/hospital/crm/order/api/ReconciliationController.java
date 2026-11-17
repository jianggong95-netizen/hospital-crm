package com.hospital.crm.order.api;

import com.hospital.crm.common.api.ApiResponse;
import com.hospital.crm.common.api.PageResponse;
import com.hospital.crm.order.application.ReconciliationService;
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
 * 渠道对账接口。
 */
@RestController
@RequestMapping("/api/v1/reconciliations")
public class ReconciliationController {

    private final ReconciliationService reconciliationService;

    public ReconciliationController(ReconciliationService reconciliationService) {
        this.reconciliationService = reconciliationService;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('crm:payment:manage')")
    public ApiResponse<OrderDtos.ReconciliationDetail> run(
            @Valid @RequestBody OrderDtos.ReconRunRequest request) {
        return ApiResponse.ok(reconciliationService.run(request));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('crm:payment:read')")
    public ApiResponse<PageResponse<OrderDtos.ReconciliationView>> list(
            @RequestParam(required = false) String channel,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return ApiResponse.ok(reconciliationService.list(channel, status, page, pageSize));
    }

    @GetMapping("/{batchId}/diffs")
    @PreAuthorize("hasAuthority('crm:payment:read')")
    public ApiResponse<OrderDtos.ReconciliationDetail> diffs(@PathVariable UUID batchId) {
        return ApiResponse.ok(reconciliationService.detail(batchId));
    }
}
