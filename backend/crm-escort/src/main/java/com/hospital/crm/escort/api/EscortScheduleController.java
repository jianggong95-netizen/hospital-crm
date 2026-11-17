package com.hospital.crm.escort.api;

import com.hospital.crm.common.api.ApiResponse;
import com.hospital.crm.common.api.PageResponse;
import com.hospital.crm.escort.application.EscortScheduleService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
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
@RequestMapping("/api/v1/escort-schedules")
public class EscortScheduleController {

    private final EscortScheduleService scheduleService;

    public EscortScheduleController(EscortScheduleService scheduleService) {
        this.scheduleService = scheduleService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('crm:escort:read')")
    public ApiResponse<PageResponse<EscortDtos.ScheduleView>> list(
            @RequestParam(required = false) UUID escortId,
            @RequestParam(required = false) LocalDate dateFrom,
            @RequestParam(required = false) LocalDate dateTo,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return ApiResponse.ok(scheduleService.list(escortId, dateFrom, dateTo, page, pageSize));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('crm:escort:write')")
    public ApiResponse<EscortDtos.ScheduleView> create(
            @Valid @RequestBody EscortDtos.ScheduleCreateRequest request) {
        return ApiResponse.ok(scheduleService.create(request));
    }

    @PutMapping("/{scheduleId}")
    @PreAuthorize("hasAuthority('crm:escort:write')")
    public ApiResponse<EscortDtos.ScheduleView> update(
            @PathVariable UUID scheduleId,
            @Valid @RequestBody EscortDtos.ScheduleUpdateRequest request) {
        return ApiResponse.ok(scheduleService.update(scheduleId, request));
    }

    @DeleteMapping("/{scheduleId}")
    @PreAuthorize("hasAuthority('crm:escort:write')")
    public ApiResponse<Void> delete(@PathVariable UUID scheduleId) {
        scheduleService.delete(scheduleId);
        return ApiResponse.ok(null);
    }
}
