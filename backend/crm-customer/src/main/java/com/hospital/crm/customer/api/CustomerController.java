package com.hospital.crm.customer.api;

import com.hospital.crm.common.api.ApiResponse;
import com.hospital.crm.common.api.ErrorCode;
import com.hospital.crm.common.api.PageResponse;
import com.hospital.crm.common.exception.BusinessException;
import com.hospital.crm.customer.application.CustomerMergeService;
import com.hospital.crm.customer.application.CustomerService;
import com.hospital.crm.customer.application.HisPatientSyncPort;
import jakarta.validation.Valid;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/customers")
public class CustomerController {

    private final CustomerService customerService;
    private final CustomerMergeService customerMergeService;
    private final ObjectProvider<HisPatientSyncPort> hisPatientSyncPort;

    public CustomerController(CustomerService customerService,
                              CustomerMergeService customerMergeService,
                              ObjectProvider<HisPatientSyncPort> hisPatientSyncPort) {
        this.customerService = customerService;
        this.customerMergeService = customerMergeService;
        this.hisPatientSyncPort = hisPatientSyncPort;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('crm:customer:read')")
    public ApiResponse<PageResponse<CustomerDtos.CustomerSummary>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) UUID tagId,
            @RequestParam(required = false) String sourceCode,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String gender,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return ApiResponse.ok(customerService.list(keyword, tagId, sourceCode, status, gender, page, pageSize));
    }

    @GetMapping("/{customerId}")
    @PreAuthorize("hasAuthority('crm:customer:read')")
    public ApiResponse<CustomerDtos.CustomerDetail> detail(@PathVariable UUID customerId) {
        return ApiResponse.ok(customerService.detail(customerId));
    }

    @GetMapping("/duplicates")
    @PreAuthorize("hasAuthority('crm:customer:read')")
    public ApiResponse<List<CustomerDtos.DuplicateGroup>> duplicates() {
        return ApiResponse.ok(customerMergeService.findDuplicates());
    }

    @PostMapping("/{targetId}/merge")
    @PreAuthorize("hasAuthority('crm:customer:write')")
    public ApiResponse<CustomerDtos.MergeResponse> merge(@PathVariable UUID targetId,
                                                         @Valid @RequestBody CustomerDtos.MergeRequest request) {
        return ApiResponse.ok(customerMergeService.merge(targetId, request.sourceId()));
    }

    @PostMapping("/{targetId}/merge/{sourceId}")
    @PreAuthorize("hasAuthority('crm:customer:write')")
    public ApiResponse<CustomerDtos.MergeResponse> mergeByIds(@PathVariable UUID targetId,
                                                              @PathVariable UUID sourceId) {
        return ApiResponse.ok(customerMergeService.merge(targetId, sourceId));
    }

    @PostMapping("/sync-his")
    @PreAuthorize("hasAuthority('crm:customer:write')")
    public ApiResponse<CustomerDtos.HisSyncResult> syncHis() {
        HisPatientSyncPort port = hisPatientSyncPort.getIfAvailable();
        if (port == null) {
            throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE, "HIS 患者同步能力未启用");
        }
        return ApiResponse.ok(port.syncFromHis());
    }

    @PostMapping
    @PreAuthorize("hasAuthority('crm:customer:write')")
    public ApiResponse<CustomerDtos.CustomerSummary> create(@Valid @RequestBody CustomerDtos.CreateRequest request) {
        return ApiResponse.ok(customerService.create(request));
    }

    @PutMapping("/{customerId}")
    @PreAuthorize("hasAuthority('crm:customer:write')")
    public ApiResponse<CustomerDtos.CustomerSummary> update(@PathVariable UUID customerId,
                                                            @Valid @RequestBody CustomerDtos.UpdateRequest request) {
        return ApiResponse.ok(customerService.update(customerId, request));
    }

    @PatchMapping("/{customerId}/status")
    @PreAuthorize("hasAuthority('crm:customer:write')")
    public ApiResponse<Void> changeStatus(@PathVariable UUID customerId,
                                          @Valid @RequestBody CustomerDtos.StatusRequest request) {
        customerService.changeStatus(customerId, request.status());
        return ApiResponse.ok(null);
    }
}
