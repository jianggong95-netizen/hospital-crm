package com.hospital.crm.followup.api;

import com.hospital.crm.common.api.ApiResponse;
import com.hospital.crm.followup.application.FollowupConfigService;
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
 * 随访配置接口：分类、模板与计划。
 */
@RestController
@RequestMapping("/api/v1/followup")
public class FollowupConfigController {

    private final FollowupConfigService configService;

    public FollowupConfigController(FollowupConfigService configService) {
        this.configService = configService;
    }

    @GetMapping("/categories")
    @PreAuthorize("hasAuthority('crm:followup:read')")
    public ApiResponse<List<FollowupDtos.CategoryView>> listCategories(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String visitType) {
        return ApiResponse.ok(configService.listCategories(status, visitType));
    }

    @PostMapping("/categories")
    @PreAuthorize("hasAuthority('crm:followup:manage')")
    public ApiResponse<FollowupDtos.CategoryView> createCategory(
            @Valid @RequestBody FollowupDtos.CategoryCreateRequest request) {
        return ApiResponse.ok(configService.createCategory(request));
    }

    @PutMapping("/categories/{categoryCode}")
    @PreAuthorize("hasAuthority('crm:followup:manage')")
    public ApiResponse<FollowupDtos.CategoryView> updateCategory(
            @PathVariable String categoryCode,
            @Valid @RequestBody FollowupDtos.CategoryUpdateRequest request) {
        return ApiResponse.ok(configService.updateCategory(categoryCode, request));
    }

    @GetMapping("/templates")
    @PreAuthorize("hasAuthority('crm:followup:read')")
    public ApiResponse<List<FollowupDtos.TemplateView>> listTemplates(
            @RequestParam(required = false) String categoryCode,
            @RequestParam(required = false) String status) {
        return ApiResponse.ok(configService.listTemplates(categoryCode, status));
    }

    @PostMapping("/templates")
    @PreAuthorize("hasAuthority('crm:followup:manage')")
    public ApiResponse<FollowupDtos.TemplateView> createTemplate(
            @Valid @RequestBody FollowupDtos.TemplateCreateRequest request) {
        return ApiResponse.ok(configService.createTemplate(request));
    }

    @PutMapping("/templates/{templateCode}")
    @PreAuthorize("hasAuthority('crm:followup:manage')")
    public ApiResponse<FollowupDtos.TemplateView> updateTemplate(
            @PathVariable String templateCode,
            @Valid @RequestBody FollowupDtos.TemplateUpdateRequest request) {
        return ApiResponse.ok(configService.updateTemplate(templateCode, request));
    }

    @PutMapping("/templates/{templateCode}/status")
    @PreAuthorize("hasAuthority('crm:followup:manage')")
    public ApiResponse<FollowupDtos.TemplateView> changeTemplateStatus(
            @PathVariable String templateCode,
            @Valid @RequestBody FollowupDtos.StatusRequest request) {
        return ApiResponse.ok(configService.changeTemplateStatus(templateCode, request));
    }

    @GetMapping("/plans")
    @PreAuthorize("hasAuthority('crm:followup:read')")
    public ApiResponse<List<FollowupDtos.PlanView>> listPlans(@RequestParam(required = false) String status) {
        return ApiResponse.ok(configService.listPlans(status));
    }

    @PostMapping("/plans")
    @PreAuthorize("hasAuthority('crm:followup:manage')")
    public ApiResponse<FollowupDtos.PlanView> createPlan(
            @Valid @RequestBody FollowupDtos.PlanCreateRequest request) {
        return ApiResponse.ok(configService.createPlan(request));
    }

    @PutMapping("/plans/{planNo}")
    @PreAuthorize("hasAuthority('crm:followup:manage')")
    public ApiResponse<FollowupDtos.PlanView> updatePlan(
            @PathVariable String planNo,
            @Valid @RequestBody FollowupDtos.PlanUpdateRequest request) {
        return ApiResponse.ok(configService.updatePlan(planNo, request));
    }

    @PutMapping("/plans/{planNo}/status")
    @PreAuthorize("hasAuthority('crm:followup:manage')")
    public ApiResponse<FollowupDtos.PlanView> changePlanStatus(
            @PathVariable String planNo,
            @Valid @RequestBody FollowupDtos.StatusRequest request) {
        return ApiResponse.ok(configService.changePlanStatus(planNo, request));
    }
}
