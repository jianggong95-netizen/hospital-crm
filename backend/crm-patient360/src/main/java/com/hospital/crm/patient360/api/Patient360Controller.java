package com.hospital.crm.patient360.api;

import com.hospital.crm.common.api.ApiResponse;
import com.hospital.crm.patient360.application.Patient360Service;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * 患者360聚合接口。聚合视图不替代各领域接口的细粒度授权，
 * 敏感字段与诊疗分区仍按字段/分区权限逐项裁剪。
 */
@RestController
@RequestMapping("/api/v1/customers")
public class Patient360Controller {

    private final Patient360Service patient360Service;

    public Patient360Controller(Patient360Service patient360Service) {
        this.patient360Service = patient360Service;
    }

    @GetMapping("/{customerId}/360")
    @PreAuthorize("hasAuthority('crm:patient360:read')")
    public ApiResponse<Patient360Dtos.Patient360View> view(
            @PathVariable UUID customerId,
            @RequestParam(required = false) String include,
            @RequestParam(defaultValue = "1") int visitPage,
            @RequestParam(defaultValue = "10") int visitPageSize,
            @RequestParam(defaultValue = "1") int admissionPage,
            @RequestParam(defaultValue = "10") int admissionPageSize,
            @RequestParam(defaultValue = "false") boolean refresh) {
        return ApiResponse.ok(patient360Service.view(
                customerId, include, visitPage, visitPageSize, admissionPage, admissionPageSize, refresh));
    }
}
