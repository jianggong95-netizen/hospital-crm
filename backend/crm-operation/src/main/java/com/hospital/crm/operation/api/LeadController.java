package com.hospital.crm.operation.api;

import com.hospital.crm.common.api.ApiResponse;
import com.hospital.crm.common.api.PageResponse;
import com.hospital.crm.operation.application.LeadService;
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

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/operation/leads")
public class LeadController {

    private final LeadService leadService;

    public LeadController(LeadService leadService) {
        this.leadService = leadService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('crm:lead:read')")
    public ApiResponse<PageResponse<OperationDtos.LeadSummary>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String channelCode,
            @RequestParam(required = false) UUID ownerUserId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return ApiResponse.ok(leadService.list(keyword, status, channelCode, ownerUserId, page, pageSize));
    }

    @GetMapping("/duplicates")
    @PreAuthorize("hasAuthority('crm:lead:read')")
    public ApiResponse<List<OperationDtos.DuplicateLead>> duplicates(
            @RequestParam(required = false) String phone,
            @RequestParam(required = false) String idNo) {
        return ApiResponse.ok(leadService.duplicates(phone, idNo));
    }

    @GetMapping("/assignees")
    @PreAuthorize("hasAuthority('crm:lead:read')")
    public ApiResponse<List<OperationDtos.AssigneeView>> assignees(
            @RequestParam(required = false) String keyword) {
        return ApiResponse.ok(leadService.assignees(keyword));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('crm:lead:write')")
    public ApiResponse<OperationDtos.LeadSummary> create(
            @Valid @RequestBody OperationDtos.LeadCreateRequest request) {
        return ApiResponse.ok(leadService.create(request));
    }

    @GetMapping("/{leadId}")
    @PreAuthorize("hasAuthority('crm:lead:read')")
    public ApiResponse<OperationDtos.LeadDetail> detail(@PathVariable UUID leadId) {
        return ApiResponse.ok(leadService.detail(leadId));
    }

    @PutMapping("/{leadId}")
    @PreAuthorize("hasAuthority('crm:lead:write')")
    public ApiResponse<OperationDtos.LeadSummary> update(@PathVariable UUID leadId,
                                                         @Valid @RequestBody OperationDtos.LeadUpdateRequest request) {
        return ApiResponse.ok(leadService.update(leadId, request));
    }

    @PutMapping("/{leadId}/status")
    @PreAuthorize("hasAuthority('crm:lead:write')")
    public ApiResponse<OperationDtos.LeadSummary> changeStatus(
            @PathVariable UUID leadId,
            @Valid @RequestBody OperationDtos.LeadStatusRequest request) {
        return ApiResponse.ok(leadService.changeStatus(leadId, request));
    }

    @PostMapping("/{leadId}/convert")
    @PreAuthorize("hasAuthority('crm:lead:convert')")
    public ApiResponse<OperationDtos.ConversionView> convert(
            @PathVariable UUID leadId,
            @Valid @RequestBody OperationDtos.LeadConvertRequest request) {
        return ApiResponse.ok(leadService.convert(leadId, request));
    }

    @PostMapping("/{leadId}/merge")
    @PreAuthorize("hasAuthority('crm:lead:write')")
    public ApiResponse<OperationDtos.LeadSummary> merge(
            @PathVariable UUID leadId,
            @Valid @RequestBody OperationDtos.LeadMergeRequest request) {
        return ApiResponse.ok(leadService.merge(leadId, request));
    }

    @PostMapping("/{leadId}/assign")
    @PreAuthorize("hasAuthority('crm:lead:assign')")
    public ApiResponse<OperationDtos.LeadSummary> assign(
            @PathVariable UUID leadId,
            @Valid @RequestBody OperationDtos.LeadAssignRequest request) {
        return ApiResponse.ok(leadService.assign(leadId, request));
    }

    @GetMapping("/{leadId}/contacts")
    @PreAuthorize("hasAuthority('crm:lead:read')")
    public ApiResponse<PageResponse<OperationDtos.ContactRecordView>> contacts(
            @PathVariable UUID leadId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return ApiResponse.ok(leadService.contacts(leadId, page, pageSize));
    }

    @PostMapping("/{leadId}/contacts")
    @PreAuthorize("hasAuthority('crm:lead:write')")
    public ApiResponse<OperationDtos.ContactRecordView> addContact(
            @PathVariable UUID leadId,
            @Valid @RequestBody OperationDtos.ContactRecordCreateRequest request) {
        return ApiResponse.ok(leadService.addContact(leadId, request));
    }

    @GetMapping("/{leadId}/conversion")
    @PreAuthorize("hasAuthority('crm:lead:read')")
    public ApiResponse<OperationDtos.ConversionView> conversion(@PathVariable UUID leadId) {
        return ApiResponse.ok(leadService.conversion(leadId));
    }

    @GetMapping("/{leadId}/assignments")
    @PreAuthorize("hasAuthority('crm:lead:read')")
    public ApiResponse<List<OperationDtos.AssignmentView>> assignments(@PathVariable UUID leadId) {
        return ApiResponse.ok(leadService.assignments(leadId));
    }
}
