package com.hospital.crm.operation.api;

import com.hospital.crm.common.api.ApiResponse;
import com.hospital.crm.common.api.PageResponse;
import com.hospital.crm.operation.application.InvitationService;
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

@RestController
@RequestMapping("/api/v1/operation/invitations")
public class InvitationController {

    private final InvitationService invitationService;

    public InvitationController(InvitationService invitationService) {
        this.invitationService = invitationService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('crm:invitation:read')")
    public ApiResponse<PageResponse<OperationDtos.InvitationView>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) UUID ownerUserId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return ApiResponse.ok(invitationService.list(keyword, status, ownerUserId, page, pageSize));
    }

    @GetMapping("/{invitationId}")
    @PreAuthorize("hasAuthority('crm:invitation:read')")
    public ApiResponse<OperationDtos.InvitationView> detail(@PathVariable UUID invitationId) {
        return ApiResponse.ok(invitationService.detail(invitationId));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('crm:invitation:write')")
    public ApiResponse<OperationDtos.InvitationView> create(
            @Valid @RequestBody OperationDtos.InvitationCreateRequest request) {
        return ApiResponse.ok(invitationService.create(request));
    }

    @PutMapping("/{invitationId}/status")
    @PreAuthorize("hasAuthority('crm:invitation:write')")
    public ApiResponse<OperationDtos.InvitationView> changeStatus(
            @PathVariable UUID invitationId,
            @Valid @RequestBody OperationDtos.InvitationStatusRequest request) {
        return ApiResponse.ok(invitationService.changeStatus(invitationId, request));
    }
}
