package com.hospital.crm.operation.api;

import com.hospital.crm.common.api.ApiResponse;
import com.hospital.crm.common.api.PageResponse;
import com.hospital.crm.operation.application.ActivityService;
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

@RestController
@RequestMapping("/api/v1/operation/activities")
public class ActivityController {

    private final ActivityService activityService;

    public ActivityController(ActivityService activityService) {
        this.activityService = activityService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('crm:activity:read')")
    public ApiResponse<PageResponse<OperationDtos.ActivityView>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return ApiResponse.ok(activityService.list(keyword, status, page, pageSize));
    }

    @GetMapping("/{activityId}")
    @PreAuthorize("hasAuthority('crm:activity:read')")
    public ApiResponse<OperationDtos.ActivityView> detail(@PathVariable UUID activityId) {
        return ApiResponse.ok(activityService.detail(activityId));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('crm:activity:write')")
    public ApiResponse<OperationDtos.ActivityView> create(
            @Valid @RequestBody OperationDtos.ActivityCreateRequest request) {
        return ApiResponse.ok(activityService.create(request));
    }

    @PutMapping("/{activityId}/status")
    @PreAuthorize("hasAuthority('crm:activity:write')")
    public ApiResponse<OperationDtos.ActivityView> changeStatus(
            @PathVariable UUID activityId,
            @Valid @RequestBody OperationDtos.ActivityStatusRequest request) {
        return ApiResponse.ok(activityService.changeStatus(activityId, request));
    }

    @GetMapping("/{activityId}/participants")
    @PreAuthorize("hasAuthority('crm:activity:read')")
    public ApiResponse<PageResponse<OperationDtos.ParticipantView>> participants(
            @PathVariable UUID activityId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return ApiResponse.ok(activityService.participants(activityId, page, pageSize));
    }

    @PostMapping("/{activityId}/participants")
    @PreAuthorize("hasAuthority('crm:activity:write')")
    public ApiResponse<OperationDtos.ParticipantView> register(
            @PathVariable UUID activityId,
            @Valid @RequestBody OperationDtos.ParticipantCreateRequest request) {
        return ApiResponse.ok(activityService.register(activityId, request));
    }

    @PutMapping("/{activityId}/participants/{participantId}/status")
    @PreAuthorize("hasAuthority('crm:activity:write')")
    public ApiResponse<OperationDtos.ParticipantView> changeParticipantStatus(
            @PathVariable UUID activityId,
            @PathVariable UUID participantId,
            @Valid @RequestBody OperationDtos.ParticipantStatusRequest request) {
        return ApiResponse.ok(activityService.changeParticipantStatus(activityId, participantId, request));
    }
}
