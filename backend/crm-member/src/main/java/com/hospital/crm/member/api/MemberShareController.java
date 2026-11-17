package com.hospital.crm.member.api;

import com.hospital.crm.common.api.ApiResponse;
import com.hospital.crm.member.application.MemberShareService;
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

/**
 * 会员权益共享授权接口。
 */
@RestController
@RequestMapping("/api/v1/memberships/shares")
public class MemberShareController {

    private final MemberShareService shareService;

    public MemberShareController(MemberShareService shareService) {
        this.shareService = shareService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('crm:member:read')")
    public ApiResponse<List<MemberDtos.ShareView>> list(
            @RequestParam(required = false) UUID ownerCustomerId,
            @RequestParam(required = false) UUID beneficiaryCustomerId) {
        return ApiResponse.ok(shareService.list(ownerCustomerId, beneficiaryCustomerId));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('crm:member:write')")
    public ApiResponse<MemberDtos.ShareView> create(
            @Valid @RequestBody MemberDtos.ShareCreateRequest request) {
        return ApiResponse.ok(shareService.create(request));
    }

    @PutMapping("/{shareId}/revoke")
    @PreAuthorize("hasAuthority('crm:member:write')")
    public ApiResponse<MemberDtos.ShareView> revoke(@PathVariable UUID shareId) {
        return ApiResponse.ok(shareService.revoke(shareId));
    }
}
