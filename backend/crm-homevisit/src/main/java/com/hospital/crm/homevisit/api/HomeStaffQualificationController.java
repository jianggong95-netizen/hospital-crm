package com.hospital.crm.homevisit.api;

import com.hospital.crm.common.api.ApiResponse;
import com.hospital.crm.common.api.PageResponse;
import com.hospital.crm.homevisit.application.HomeStaffQualificationService;
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

/**
 * 上门服务人员资质接口：登记与停用走 write 权限，查询走 read 权限。
 */
@RestController
@RequestMapping("/api/v1/home-staff-qualifications")
public class HomeStaffQualificationController {

    private final HomeStaffQualificationService qualificationService;

    public HomeStaffQualificationController(HomeStaffQualificationService qualificationService) {
        this.qualificationService = qualificationService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('crm:homevisit:read')")
    public ApiResponse<PageResponse<HomeVisitDtos.StaffQualificationView>> list(
            @RequestParam(required = false) UUID userId,
            @RequestParam(required = false) String qualType,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return ApiResponse.ok(qualificationService.list(userId, qualType, page, pageSize));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('crm:homevisit:write')")
    public ApiResponse<HomeVisitDtos.StaffQualificationView> create(
            @Valid @RequestBody HomeVisitDtos.StaffQualificationCreateRequest request) {
        return ApiResponse.ok(qualificationService.create(request));
    }

    @PutMapping("/{staffQualificationId}/revoke")
    @PreAuthorize("hasAuthority('crm:homevisit:write')")
    public ApiResponse<HomeVisitDtos.StaffQualificationView> revoke(
            @PathVariable UUID staffQualificationId) {
        return ApiResponse.ok(qualificationService.revoke(staffQualificationId));
    }
}
