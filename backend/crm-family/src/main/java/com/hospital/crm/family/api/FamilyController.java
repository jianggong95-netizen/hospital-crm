package com.hospital.crm.family.api;

import com.hospital.crm.common.api.ApiResponse;
import com.hospital.crm.common.api.PageResponse;
import com.hospital.crm.family.application.FamilyService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
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
@RequestMapping("/api/v1/families")
public class FamilyController {

    private final FamilyService familyService;

    public FamilyController(FamilyService familyService) {
        this.familyService = familyService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('crm:family:read')")
    public ApiResponse<PageResponse<FamilyDtos.FamilySummary>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return ApiResponse.ok(familyService.list(keyword, status, page, pageSize));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('crm:family:write')")
    public ApiResponse<FamilyDtos.FamilyDetail> create(@Valid @RequestBody FamilyDtos.CreateRequest request) {
        return ApiResponse.ok(familyService.create(request));
    }

    @GetMapping("/{familyId}")
    @PreAuthorize("hasAuthority('crm:family:read')")
    public ApiResponse<FamilyDtos.FamilyDetail> detail(@PathVariable UUID familyId) {
        return ApiResponse.ok(familyService.detail(familyId));
    }

    @GetMapping("/{familyId}/members")
    @PreAuthorize("hasAuthority('crm:family:read')")
    public ApiResponse<List<FamilyDtos.MemberItem>> members(
            @PathVariable UUID familyId,
            @RequestParam(defaultValue = "false") boolean includeEnded) {
        return ApiResponse.ok(familyService.members(familyId, includeEnded));
    }

    @PostMapping("/{familyId}/members")
    @PreAuthorize("hasAuthority('crm:family:write')")
    public ApiResponse<FamilyDtos.MemberItem> addMember(@PathVariable UUID familyId,
                                                        @Valid @RequestBody FamilyDtos.AddMemberRequest request) {
        return ApiResponse.ok(familyService.addMember(familyId, request));
    }

    @PutMapping("/{familyId}/members/{memberId}")
    @PreAuthorize("hasAuthority('crm:family:write')")
    public ApiResponse<FamilyDtos.MemberItem> updateMember(
            @PathVariable UUID familyId,
            @PathVariable UUID memberId,
            @Valid @RequestBody FamilyDtos.UpdateMemberRequest request) {
        return ApiResponse.ok(familyService.updateMember(familyId, memberId, request));
    }

    @DeleteMapping("/{familyId}/members/{memberId}")
    @PreAuthorize("hasAuthority('crm:family:write')")
    public ApiResponse<Void> endMembership(@PathVariable UUID familyId, @PathVariable UUID memberId) {
        familyService.endMembership(familyId, memberId);
        return ApiResponse.ok(null);
    }

    @GetMapping("/{familyId}/members/{memberId}/authorizations")
    @PreAuthorize("hasAuthority('crm:family:read')")
    public ApiResponse<List<FamilyDtos.AuthorizationItem>> authorizations(
            @PathVariable UUID familyId,
            @PathVariable UUID memberId) {
        return ApiResponse.ok(familyService.authorizations(familyId, memberId));
    }

    @PostMapping("/{familyId}/members/{memberId}/authorizations")
    @PreAuthorize("hasAuthority('crm:family:authorize')")
    public ApiResponse<FamilyDtos.AuthorizationItem> createAuthorization(
            @PathVariable UUID familyId,
            @PathVariable UUID memberId,
            @Valid @RequestBody FamilyDtos.CreateAuthorizationRequest request) {
        return ApiResponse.ok(familyService.createAuthorization(familyId, memberId, request));
    }

    @DeleteMapping("/{familyId}/members/{memberId}/authorizations/{authorizationId}")
    @PreAuthorize("hasAuthority('crm:family:authorize')")
    public ApiResponse<FamilyDtos.AuthorizationItem> revokeAuthorization(
            @PathVariable UUID familyId,
            @PathVariable UUID memberId,
            @PathVariable UUID authorizationId) {
        return ApiResponse.ok(familyService.revokeAuthorization(familyId, memberId, authorizationId));
    }

    @PostMapping("/{familyId}/delegation-checks")
    @PreAuthorize("hasAuthority('crm:family:read')")
    public ApiResponse<FamilyDtos.DelegationCheckResponse> checkDelegation(
            @PathVariable UUID familyId,
            @Valid @RequestBody FamilyDtos.DelegationCheckRequest request) {
        return ApiResponse.ok(familyService.checkDelegation(familyId, request));
    }
}
