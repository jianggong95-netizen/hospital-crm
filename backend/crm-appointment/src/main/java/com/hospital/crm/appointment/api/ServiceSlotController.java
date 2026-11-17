package com.hospital.crm.appointment.api;

import com.hospital.crm.appointment.application.ServiceSlotService;
import com.hospital.crm.common.api.ApiResponse;
import com.hospital.crm.common.api.PageResponse;
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

import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/service-slots")
public class ServiceSlotController {

    private final ServiceSlotService slotService;

    public ServiceSlotController(ServiceSlotService slotService) {
        this.slotService = slotService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('crm:appttype:read')")
    public ApiResponse<PageResponse<AppointmentDtos.SlotView>> list(
            @RequestParam(required = false) String typeCode,
            @RequestParam(required = false) LocalDate dateFrom,
            @RequestParam(required = false) LocalDate dateTo,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return ApiResponse.ok(slotService.list(typeCode, dateFrom, dateTo, status, page, pageSize));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('crm:appttype:write')")
    public ApiResponse<AppointmentDtos.SlotView> create(
            @Valid @RequestBody AppointmentDtos.SlotCreateRequest request) {
        return ApiResponse.ok(slotService.create(request));
    }

    @PutMapping("/{slotId}")
    @PreAuthorize("hasAuthority('crm:appttype:write')")
    public ApiResponse<AppointmentDtos.SlotView> update(
            @PathVariable UUID slotId,
            @Valid @RequestBody AppointmentDtos.SlotUpdateRequest request) {
        return ApiResponse.ok(slotService.update(slotId, request));
    }

    @PutMapping("/{slotId}/status")
    @PreAuthorize("hasAuthority('crm:appttype:write')")
    public ApiResponse<AppointmentDtos.SlotView> changeStatus(
            @PathVariable UUID slotId,
            @Valid @RequestBody AppointmentDtos.SlotStatusRequest request) {
        return ApiResponse.ok(slotService.changeStatus(slotId, request.status()));
    }
}
