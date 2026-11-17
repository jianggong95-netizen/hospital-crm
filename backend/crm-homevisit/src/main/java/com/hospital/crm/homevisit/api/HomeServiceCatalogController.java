package com.hospital.crm.homevisit.api;

import com.hospital.crm.common.api.ApiResponse;
import com.hospital.crm.common.api.PageResponse;
import com.hospital.crm.homevisit.application.HomeServiceCatalogService;
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

/**
 * 上门服务目录与审核接口：创建/编辑/提交走 write 权限，审核与启停走 manage 权限。
 */
@RestController
@RequestMapping("/api/v1/home-services")
public class HomeServiceCatalogController {

    private final HomeServiceCatalogService catalogService;

    public HomeServiceCatalogController(HomeServiceCatalogService catalogService) {
        this.catalogService = catalogService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('crm:homevisit:read')")
    public ApiResponse<PageResponse<HomeVisitDtos.HomeServiceView>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return ApiResponse.ok(catalogService.list(keyword, category, status, page, pageSize));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('crm:homevisit:write')")
    public ApiResponse<HomeVisitDtos.HomeServiceView> create(
            @Valid @RequestBody HomeVisitDtos.HomeServiceCreateRequest request) {
        return ApiResponse.ok(catalogService.create(request));
    }

    @PutMapping("/{serviceCode}")
    @PreAuthorize("hasAuthority('crm:homevisit:write')")
    public ApiResponse<HomeVisitDtos.HomeServiceView> update(
            @PathVariable String serviceCode,
            @Valid @RequestBody HomeVisitDtos.HomeServiceUpdateRequest request) {
        return ApiResponse.ok(catalogService.update(serviceCode, request));
    }

    @PutMapping("/{serviceCode}/submit")
    @PreAuthorize("hasAuthority('crm:homevisit:write')")
    public ApiResponse<HomeVisitDtos.HomeServiceView> submit(@PathVariable String serviceCode) {
        return ApiResponse.ok(catalogService.submit(serviceCode));
    }

    @PutMapping("/{serviceCode}/review")
    @PreAuthorize("hasAuthority('crm:homevisit:manage')")
    public ApiResponse<HomeVisitDtos.HomeServiceView> review(
            @PathVariable String serviceCode,
            @Valid @RequestBody HomeVisitDtos.HomeServiceReviewRequest request) {
        return ApiResponse.ok(catalogService.review(serviceCode, request));
    }

    @PutMapping("/{serviceCode}/status")
    @PreAuthorize("hasAuthority('crm:homevisit:manage')")
    public ApiResponse<HomeVisitDtos.HomeServiceView> changeStatus(
            @PathVariable String serviceCode,
            @Valid @RequestBody HomeVisitDtos.HomeServiceStatusRequest request) {
        return ApiResponse.ok(catalogService.changeStatus(serviceCode, request));
    }
}
