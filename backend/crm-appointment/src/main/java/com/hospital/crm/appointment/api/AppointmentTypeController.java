package com.hospital.crm.appointment.api;

import com.hospital.crm.appointment.application.AppointmentTypeService;
import com.hospital.crm.common.api.ApiResponse;
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

@RestController
@RequestMapping("/api/v1/appointment-types")
public class AppointmentTypeController {

    private final AppointmentTypeService typeService;

    public AppointmentTypeController(AppointmentTypeService typeService) {
        this.typeService = typeService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('crm:appttype:read')")
    public ApiResponse<List<AppointmentDtos.TypeView>> list(
            @RequestParam(defaultValue = "false") boolean includeDisabled) {
        return ApiResponse.ok(typeService.list(includeDisabled));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('crm:appttype:write')")
    public ApiResponse<AppointmentDtos.TypeView> create(
            @Valid @RequestBody AppointmentDtos.TypeCreateRequest request) {
        return ApiResponse.ok(typeService.create(request));
    }

    @PutMapping("/{typeCode}")
    @PreAuthorize("hasAuthority('crm:appttype:write')")
    public ApiResponse<AppointmentDtos.TypeView> update(
            @PathVariable String typeCode,
            @Valid @RequestBody AppointmentDtos.TypeUpdateRequest request) {
        return ApiResponse.ok(typeService.update(typeCode, request));
    }

    @PutMapping("/{typeCode}/status")
    @PreAuthorize("hasAuthority('crm:appttype:write')")
    public ApiResponse<AppointmentDtos.TypeView> changeStatus(
            @PathVariable String typeCode,
            @Valid @RequestBody AppointmentDtos.TypeStatusRequest request) {
        return ApiResponse.ok(typeService.changeStatus(typeCode, request.status()));
    }
}
