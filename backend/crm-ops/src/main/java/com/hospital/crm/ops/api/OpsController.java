package com.hospital.crm.ops.api;

import com.hospital.crm.common.api.ApiResponse;
import com.hospital.crm.common.api.PageResponse;
import com.hospital.crm.ops.application.AlertService;
import com.hospital.crm.ops.application.BackupService;
import com.hospital.crm.ops.application.JobStatusService;
import com.hospital.crm.ops.application.OverviewService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * 运维监控接口：服务总览、定时任务状态与运行记录、告警规则/告警处置、备份记录与恢复演练结果。
 *
 * <p>查询权限 crm:ops:read；备份上报、演练登记、告警确认与外部告警接入需要 crm:ops:manage。</p>
 */
@RestController
@RequestMapping("/api/v1/ops")
public class OpsController {

    private final OverviewService overviewService;
    private final JobStatusService jobStatusService;
    private final AlertService alertService;
    private final BackupService backupService;

    public OpsController(OverviewService overviewService,
                         JobStatusService jobStatusService,
                         AlertService alertService,
                         BackupService backupService) {
        this.overviewService = overviewService;
        this.jobStatusService = jobStatusService;
        this.alertService = alertService;
        this.backupService = backupService;
    }

    @GetMapping("/overview")
    @PreAuthorize("hasAuthority('crm:ops:read')")
    public ApiResponse<OpsDtos.OverviewView> overview() {
        return ApiResponse.ok(overviewService.overview());
    }

    @GetMapping("/jobs")
    @PreAuthorize("hasAuthority('crm:ops:read')")
    public ApiResponse<List<OpsDtos.JobStatusView>> jobs() {
        return ApiResponse.ok(jobStatusService.list());
    }

    @GetMapping("/jobs/{jobCode}/runs")
    @PreAuthorize("hasAuthority('crm:ops:read')")
    public ApiResponse<List<OpsDtos.JobRunView>> jobRuns(
            @PathVariable String jobCode,
            @RequestParam(required = false) Integer limit) {
        return ApiResponse.ok(jobStatusService.runs(jobCode, OpsDtos.runLimit(limit)));
    }

    @GetMapping("/alerts")
    @PreAuthorize("hasAuthority('crm:ops:read')")
    public ApiResponse<PageResponse<OpsDtos.AlertView>> alerts(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return ApiResponse.ok(alertService.list(status, page, pageSize));
    }

    @GetMapping("/alerts/rules")
    @PreAuthorize("hasAuthority('crm:ops:read')")
    public ApiResponse<List<OpsDtos.AlertRuleView>> alertRules() {
        return ApiResponse.ok(alertService.rules());
    }

    @PostMapping("/alerts/{alertId}/ack")
    @PreAuthorize("hasAuthority('crm:ops:manage')")
    public ApiResponse<OpsDtos.AlertView> ackAlert(@PathVariable UUID alertId) {
        return ApiResponse.ok(alertService.ack(alertId));
    }

    @PostMapping("/alerts/raise")
    @PreAuthorize("hasAuthority('crm:ops:manage')")
    public ApiResponse<OpsDtos.AlertView> raiseAlert(
            @Valid @RequestBody OpsDtos.RaiseAlertRequest request) {
        return ApiResponse.ok(alertService.raiseExternal(request));
    }

    @GetMapping("/backups")
    @PreAuthorize("hasAuthority('crm:ops:read')")
    public ApiResponse<PageResponse<OpsDtos.BackupView>> backups(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return ApiResponse.ok(backupService.list(status, page, pageSize));
    }

    @PostMapping("/backups")
    @PreAuthorize("hasAuthority('crm:ops:manage')")
    public ApiResponse<OpsDtos.BackupView> reportBackup(
            @Valid @RequestBody OpsDtos.BackupReportRequest request) {
        return ApiResponse.ok(backupService.report(request));
    }

    @PostMapping("/backups/{backupId}/verify")
    @PreAuthorize("hasAuthority('crm:ops:manage')")
    public ApiResponse<OpsDtos.BackupView> verifyBackup(
            @PathVariable UUID backupId,
            @Valid @RequestBody OpsDtos.BackupVerifyRequest request) {
        return ApiResponse.ok(backupService.verify(backupId, request));
    }
}
