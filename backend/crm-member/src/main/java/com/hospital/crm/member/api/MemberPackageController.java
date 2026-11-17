package com.hospital.crm.member.api;

import com.hospital.crm.common.api.ApiResponse;
import com.hospital.crm.member.application.MemberPackageService;
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

/**
 * 会员套餐目录与购买接口：目录维护走 manage 权限，购买走 write 权限。
 */
@RestController
@RequestMapping("/api/v1/memberships/packages")
public class MemberPackageController {

    private final MemberPackageService packageService;

    public MemberPackageController(MemberPackageService packageService) {
        this.packageService = packageService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('crm:member:read')")
    public ApiResponse<List<MemberDtos.PackageView>> list(@RequestParam(required = false) String status) {
        return ApiResponse.ok(packageService.list(status));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('crm:member:manage')")
    public ApiResponse<MemberDtos.PackageView> create(
            @Valid @RequestBody MemberDtos.PackageCreateRequest request) {
        return ApiResponse.ok(packageService.create(request));
    }

    @PutMapping("/{packageCode}")
    @PreAuthorize("hasAuthority('crm:member:manage')")
    public ApiResponse<MemberDtos.PackageView> update(
            @PathVariable String packageCode,
            @Valid @RequestBody MemberDtos.PackageUpdateRequest request) {
        return ApiResponse.ok(packageService.update(packageCode, request));
    }

    @PutMapping("/{packageCode}/status")
    @PreAuthorize("hasAuthority('crm:member:manage')")
    public ApiResponse<MemberDtos.PackageView> changeStatus(
            @PathVariable String packageCode,
            @Valid @RequestBody MemberDtos.PackageStatusRequest request) {
        return ApiResponse.ok(packageService.changeStatus(packageCode, request.status()));
    }

    @PostMapping("/{packageCode}/purchase")
    @PreAuthorize("hasAuthority('crm:member:write')")
    public ApiResponse<MemberDtos.PurchaseResult> purchase(
            @PathVariable String packageCode,
            @Valid @RequestBody MemberDtos.PurchaseRequest request) {
        return ApiResponse.ok(packageService.purchase(packageCode, request));
    }
}
