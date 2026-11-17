package com.hospital.crm.followup.api;

import com.hospital.crm.common.api.ApiResponse;
import com.hospital.crm.common.api.PageResponse;
import com.hospital.crm.followup.application.FollowupTaskService;
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

/**
 * 随访任务接口：建单、事件触发、分配、执行、结果提交与异常升级。
 */
@RestController
@RequestMapping("/api/v1/followup")
public class FollowupTaskController {

    private final FollowupTaskService taskService;

    public FollowupTaskController(FollowupTaskService taskService) {
        this.taskService = taskService;
    }

    @GetMapping("/tasks")
    @PreAuthorize("hasAuthority('crm:followup:read')")
    public ApiResponse<PageResponse<FollowupDtos.TaskView>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String categoryCode,
            @RequestParam(required = false) String riskLevel,
            @RequestParam(required = false) UUID assigneeId,
            @RequestParam(required = false) UUID customerId,
            @RequestParam(required = false) Boolean escalationRequired,
            @RequestParam(required = false) LocalDate dateFrom,
            @RequestParam(required = false) LocalDate dateTo,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return ApiResponse.ok(taskService.list(keyword, status, categoryCode, riskLevel, assigneeId,
                customerId, escalationRequired, dateFrom, dateTo, page, pageSize));
    }

    @PostMapping("/tasks")
    @PreAuthorize("hasAuthority('crm:followup:write')")
    public ApiResponse<FollowupDtos.TaskView> create(
            @Valid @RequestBody FollowupDtos.TaskCreateRequest request) {
        return ApiResponse.ok(taskService.createManual(request));
    }

    @GetMapping("/tasks/{taskId}")
    @PreAuthorize("hasAuthority('crm:followup:read')")
    public ApiResponse<FollowupDtos.TaskDetail> detail(@PathVariable UUID taskId) {
        return ApiResponse.ok(taskService.detail(taskId));
    }

    @PostMapping("/triggers")
    @PreAuthorize("hasAuthority('crm:followup:write')")
    public ApiResponse<FollowupDtos.TriggerResult> trigger(
            @Valid @RequestBody FollowupDtos.TriggerRequest request) {
        return ApiResponse.ok(taskService.trigger(request));
    }

    @PutMapping("/tasks/{taskId}/assign")
    @PreAuthorize("hasAuthority('crm:followup:write')")
    public ApiResponse<FollowupDtos.TaskView> assign(
            @PathVariable UUID taskId,
            @Valid @RequestBody FollowupDtos.AssignRequest request) {
        return ApiResponse.ok(taskService.assign(taskId, request));
    }

    @PutMapping("/tasks/{taskId}/start")
    @PreAuthorize("hasAuthority('crm:followup:write')")
    public ApiResponse<FollowupDtos.TaskView> start(@PathVariable UUID taskId) {
        return ApiResponse.ok(taskService.start(taskId));
    }

    @PostMapping("/tasks/{taskId}/records")
    @PreAuthorize("hasAuthority('crm:followup:write')")
    public ApiResponse<FollowupDtos.TaskDetail> submitRecord(
            @PathVariable UUID taskId,
            @Valid @RequestBody FollowupDtos.RecordCreateRequest request) {
        return ApiResponse.ok(taskService.submitRecord(taskId, request));
    }

    @PutMapping("/tasks/{taskId}/escalate")
    @PreAuthorize("hasAuthority('crm:followup:manage')")
    public ApiResponse<FollowupDtos.TaskView> escalate(
            @PathVariable UUID taskId,
            @Valid @RequestBody FollowupDtos.ReasonRequest request) {
        return ApiResponse.ok(taskService.escalate(taskId, request));
    }

    @PutMapping("/tasks/{taskId}/resolve")
    @PreAuthorize("hasAuthority('crm:followup:manage')")
    public ApiResponse<FollowupDtos.TaskView> resolve(
            @PathVariable UUID taskId,
            @Valid @RequestBody(required = false) FollowupDtos.ResolveRequest request) {
        return ApiResponse.ok(taskService.resolve(taskId,
                request == null ? new FollowupDtos.ResolveRequest(null) : request));
    }

    @PutMapping("/tasks/{taskId}/cancel")
    @PreAuthorize("hasAuthority('crm:followup:manage')")
    public ApiResponse<FollowupDtos.TaskView> cancel(
            @PathVariable UUID taskId,
            @Valid @RequestBody FollowupDtos.ReasonRequest request) {
        return ApiResponse.ok(taskService.cancel(taskId, request));
    }
}
