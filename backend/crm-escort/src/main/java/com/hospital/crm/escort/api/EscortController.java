package com.hospital.crm.escort.api;

import com.hospital.crm.common.api.ApiResponse;
import com.hospital.crm.common.api.PageResponse;
import com.hospital.crm.escort.application.EscortService;
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
@RequestMapping("/api/v1/escorts")
public class EscortController {

    private final EscortService escortService;

    public EscortController(EscortService escortService) {
        this.escortService = escortService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('crm:escort:read')")
    public ApiResponse<PageResponse<EscortDtos.EscortSummary>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return ApiResponse.ok(escortService.list(keyword, status, page, pageSize));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('crm:escort:write')")
    public ApiResponse<EscortDtos.EscortDetail> create(
            @Valid @RequestBody EscortDtos.EscortCreateRequest request) {
        return ApiResponse.ok(escortService.create(request));
    }

    @GetMapping("/{escortId}")
    @PreAuthorize("hasAuthority('crm:escort:read')")
    public ApiResponse<EscortDtos.EscortDetail> detail(@PathVariable UUID escortId) {
        return ApiResponse.ok(escortService.detail(escortId));
    }

    @PutMapping("/{escortId}")
    @PreAuthorize("hasAuthority('crm:escort:write')")
    public ApiResponse<EscortDtos.EscortDetail> update(
            @PathVariable UUID escortId,
            @Valid @RequestBody EscortDtos.EscortUpdateRequest request) {
        return ApiResponse.ok(escortService.update(escortId, request));
    }

    @PutMapping("/{escortId}/status")
    @PreAuthorize("hasAuthority('crm:escort:write')")
    public ApiResponse<EscortDtos.EscortDetail> changeStatus(
            @PathVariable UUID escortId,
            @Valid @RequestBody EscortDtos.EscortStatusRequest request) {
        return ApiResponse.ok(escortService.changeStatus(escortId, request));
    }

    @PostMapping("/{escortId}/qualifications")
    @PreAuthorize("hasAuthority('crm:escort:write')")
    public ApiResponse<EscortDtos.EscortQualificationView> addQualification(
            @PathVariable UUID escortId,
            @Valid @RequestBody EscortDtos.QualificationCreateRequest request) {
        return ApiResponse.ok(escortService.addQualification(escortId, request));
    }

    @PutMapping("/{escortId}/qualifications/{qualificationId}/revoke")
    @PreAuthorize("hasAuthority('crm:escort:write')")
    public ApiResponse<EscortDtos.EscortQualificationView> revokeQualification(
            @PathVariable UUID escortId,
            @PathVariable UUID qualificationId) {
        return ApiResponse.ok(escortService.revokeQualification(escortId, qualificationId));
    }
}
