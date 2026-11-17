package com.hospital.crm.appointment.api;

import com.hospital.crm.appointment.application.AppointmentService;
import com.hospital.crm.appointment.application.ReminderService;
import com.hospital.crm.common.api.ApiResponse;
import com.hospital.crm.common.api.PageResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class AppointmentReminderController {

    private final ReminderService reminderService;
    private final AppointmentService appointmentService;

    public AppointmentReminderController(ReminderService reminderService, AppointmentService appointmentService) {
        this.reminderService = reminderService;
        this.appointmentService = appointmentService;
    }

    @PostMapping("/appointment-reminders/dispatch")
    @PreAuthorize("hasAuthority('crm:appointment:manage')")
    public ApiResponse<AppointmentDtos.DispatchResult> dispatch() {
        return ApiResponse.ok(reminderService.dispatchDue());
    }

    @PostMapping("/appointment-reminders/{reminderId}/retry")
    @PreAuthorize("hasAuthority('crm:appointment:manage')")
    public ApiResponse<AppointmentDtos.ReminderView> retry(@PathVariable UUID reminderId) {
        return ApiResponse.ok(reminderService.retry(reminderId));
    }

    @GetMapping("/no-shows")
    @PreAuthorize("hasAuthority('crm:appointment:read')")
    public ApiResponse<PageResponse<AppointmentDtos.NoShowView>> noShows(
            @RequestParam(required = false) UUID customerId,
            @RequestParam(required = false) LocalDate dateFrom,
            @RequestParam(required = false) LocalDate dateTo,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return ApiResponse.ok(appointmentService.noShows(customerId, dateFrom, dateTo, page, pageSize));
    }
}
