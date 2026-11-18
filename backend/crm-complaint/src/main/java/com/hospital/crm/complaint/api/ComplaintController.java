package com.hospital.crm.complaint.api;

import com.hospital.crm.common.api.ApiResponse;
import com.hospital.crm.common.api.PageResponse;
import com.hospital.crm.complaint.application.ComplaintService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
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
 * 投诉表扬中心接口：建单、分派、处理动作、整改、反馈、复核结案、升级与超时扫描。
 */
@RestController
@RequestMapping("/api/v1/complaints")
public class ComplaintController {

    private final ComplaintService complaintService;

    public ComplaintController(ComplaintService complaintService) {
        this.complaintService = complaintService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('crm:complaint:read')")
    public ApiResponse<PageResponse<ComplaintDtos.TicketView>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String ticketType,
            @RequestParam(required = false) String severity,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) UUID assigneeId,
            @RequestParam(required = false) UUID customerId,
            @RequestParam(required = false) Boolean escalated,
            @RequestParam(required = false) Boolean overdue,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return ApiResponse.ok(complaintService.list(keyword, ticketType, severity, status, assigneeId,
                customerId, escalated, overdue, dateFrom, dateTo, page, pageSize));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('crm:complaint:write')")
    public ApiResponse<ComplaintDtos.TicketView> create(
            @Valid @RequestBody ComplaintDtos.CreateRequest request) {
        return ApiResponse.ok(complaintService.create(request));
    }

    @GetMapping("/{ticketId}")
    @PreAuthorize("hasAuthority('crm:complaint:read')")
    public ApiResponse<ComplaintDtos.TicketDetail> detail(@PathVariable UUID ticketId) {
        return ApiResponse.ok(complaintService.detail(ticketId));
    }

    @PutMapping("/{ticketId}/assign")
    @PreAuthorize("hasAuthority('crm:complaint:assign')")
    public ApiResponse<ComplaintDtos.TicketView> assign(
            @PathVariable UUID ticketId,
            @Valid @RequestBody ComplaintDtos.AssignRequest request) {
        return ApiResponse.ok(complaintService.assign(ticketId, request));
    }

    @PutMapping("/{ticketId}/start")
    @PreAuthorize("hasAuthority('crm:complaint:write')")
    public ApiResponse<ComplaintDtos.TicketView> start(@PathVariable UUID ticketId) {
        return ApiResponse.ok(complaintService.start(ticketId));
    }

    @PostMapping("/{ticketId}/actions")
    @PreAuthorize("hasAuthority('crm:complaint:write')")
    public ApiResponse<ComplaintDtos.TicketDetail> addAction(
            @PathVariable UUID ticketId,
            @Valid @RequestBody ComplaintDtos.ActionCreateRequest request) {
        return ApiResponse.ok(complaintService.addAction(ticketId, request));
    }

    @PostMapping("/{ticketId}/rectifications")
    @PreAuthorize("hasAuthority('crm:complaint:write')")
    public ApiResponse<ComplaintDtos.TicketDetail> addRectification(
            @PathVariable UUID ticketId,
            @Valid @RequestBody ComplaintDtos.RectificationCreateRequest request) {
        return ApiResponse.ok(complaintService.addRectification(ticketId, request));
    }

    @PutMapping("/{ticketId}/rectifications/{rectificationId}/submit")
    @PreAuthorize("hasAuthority('crm:complaint:write')")
    public ApiResponse<ComplaintDtos.TicketDetail> submitRectification(
            @PathVariable UUID ticketId,
            @PathVariable UUID rectificationId) {
        return ApiResponse.ok(complaintService.submitRectification(ticketId, rectificationId));
    }

    @PostMapping("/{ticketId}/feedback")
    @PreAuthorize("hasAuthority('crm:complaint:write')")
    public ApiResponse<ComplaintDtos.TicketDetail> addFeedback(
            @PathVariable UUID ticketId,
            @Valid @RequestBody ComplaintDtos.FeedbackCreateRequest request) {
        return ApiResponse.ok(complaintService.addFeedback(ticketId, request));
    }

    @PutMapping("/{ticketId}/submit-review")
    @PreAuthorize("hasAuthority('crm:complaint:write')")
    public ApiResponse<ComplaintDtos.TicketView> submitForReview(@PathVariable UUID ticketId) {
        return ApiResponse.ok(complaintService.submitForReview(ticketId));
    }

    @PutMapping("/{ticketId}/review")
    @PreAuthorize("hasAuthority('crm:complaint:review')")
    public ApiResponse<ComplaintDtos.TicketDetail> review(
            @PathVariable UUID ticketId,
            @Valid @RequestBody ComplaintDtos.ReviewRequest request) {
        return ApiResponse.ok(complaintService.review(ticketId, request));
    }

    @PutMapping("/{ticketId}/escalate")
    @PreAuthorize("hasAuthority('crm:complaint:manage')")
    public ApiResponse<ComplaintDtos.TicketView> escalate(
            @PathVariable UUID ticketId,
            @Valid @RequestBody ComplaintDtos.ReasonRequest request) {
        return ApiResponse.ok(complaintService.escalate(ticketId, request));
    }

    @PutMapping("/{ticketId}/cancel")
    @PreAuthorize("hasAuthority('crm:complaint:manage')")
    public ApiResponse<ComplaintDtos.TicketView> cancel(
            @PathVariable UUID ticketId,
            @Valid @RequestBody ComplaintDtos.ReasonRequest request) {
        return ApiResponse.ok(complaintService.cancel(ticketId, request));
    }

    @PostMapping("/escalation-scan")
    @PreAuthorize("hasAuthority('crm:complaint:manage')")
    public ApiResponse<ComplaintDtos.ScanResult> scanOverdue() {
        return ApiResponse.ok(new ComplaintDtos.ScanResult(complaintService.scanOverdue()));
    }
}
