package com.hospital.crm.appointment.api;

import com.hospital.crm.appointment.application.AppointmentService;
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
@RequestMapping("/api/v1/appointments")
public class AppointmentController {

    private final AppointmentService appointmentService;

    public AppointmentController(AppointmentService appointmentService) {
        this.appointmentService = appointmentService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('crm:appointment:read')")
    public ApiResponse<PageResponse<AppointmentDtos.AppointmentSummary>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String typeCode,
            @RequestParam(required = false) UUID customerId,
            @RequestParam(required = false) LocalDate dateFrom,
            @RequestParam(required = false) LocalDate dateTo,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return ApiResponse.ok(appointmentService.list(keyword, status, typeCode, customerId,
                dateFrom, dateTo, page, pageSize));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('crm:appointment:write')")
    public ApiResponse<AppointmentDtos.AppointmentSummary> create(
            @Valid @RequestBody AppointmentDtos.AppointmentCreateRequest request) {
        return ApiResponse.ok(appointmentService.create(request));
    }

    @GetMapping("/{appointmentId}")
    @PreAuthorize("hasAuthority('crm:appointment:read')")
    public ApiResponse<AppointmentDtos.AppointmentDetail> detail(@PathVariable UUID appointmentId) {
        return ApiResponse.ok(appointmentService.detail(appointmentId));
    }

    @PutMapping("/{appointmentId}/reschedule")
    @PreAuthorize("hasAuthority('crm:appointment:write')")
    public ApiResponse<AppointmentDtos.AppointmentSummary> reschedule(
            @PathVariable UUID appointmentId,
            @Valid @RequestBody AppointmentDtos.RescheduleRequest request) {
        return ApiResponse.ok(appointmentService.reschedule(appointmentId, request));
    }

    @PutMapping("/{appointmentId}/cancel")
    @PreAuthorize("hasAuthority('crm:appointment:write')")
    public ApiResponse<AppointmentDtos.AppointmentSummary> cancel(
            @PathVariable UUID appointmentId,
            @Valid @RequestBody AppointmentDtos.CancelRequest request) {
        return ApiResponse.ok(appointmentService.cancel(appointmentId, request));
    }

    @PutMapping("/{appointmentId}/status")
    @PreAuthorize("hasAuthority('crm:appointment:manage')")
    public ApiResponse<AppointmentDtos.AppointmentSummary> changeStatus(
            @PathVariable UUID appointmentId,
            @Valid @RequestBody AppointmentDtos.StatusChangeRequest request) {
        return ApiResponse.ok(appointmentService.changeStatus(appointmentId, request));
    }
}
