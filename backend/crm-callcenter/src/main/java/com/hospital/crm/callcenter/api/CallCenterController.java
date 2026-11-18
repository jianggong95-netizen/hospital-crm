package com.hospital.crm.callcenter.api;

import com.hospital.crm.callcenter.application.CallCenterService;
import com.hospital.crm.callcenter.application.SmsService;
import com.hospital.crm.common.api.ApiResponse;
import com.hospital.crm.common.api.PageResponse;
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
 * 呼叫中心与短信接口：来电弹屏、通话记录、录音访问、短信模板与消息发送队列。
 */
@RestController
@RequestMapping("/api/v1/callcenter")
public class CallCenterController {

    private final CallCenterService callCenterService;
    private final SmsService smsService;

    public CallCenterController(CallCenterService callCenterService, SmsService smsService) {
        this.callCenterService = callCenterService;
        this.smsService = smsService;
    }

    // ---------- 通话 ----------

    @GetMapping("/incoming-popup")
    @PreAuthorize("hasAuthority('crm:call:read')")
    public ApiResponse<CallCenterDtos.PopupView> incomingPopup(@RequestParam String phone) {
        return ApiResponse.ok(callCenterService.incomingPopup(phone));
    }

    @GetMapping("/calls")
    @PreAuthorize("hasAuthority('crm:call:read')")
    public ApiResponse<PageResponse<CallCenterDtos.CallRecordView>> listCalls(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String direction,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) UUID customerId,
            @RequestParam(required = false) String linkType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return ApiResponse.ok(callCenterService.listCalls(keyword, direction, status, customerId,
                linkType, dateFrom, dateTo, page, pageSize));
    }

    @PostMapping("/calls")
    @PreAuthorize("hasAuthority('crm:call:write')")
    public ApiResponse<CallCenterDtos.CallRecordDetailView> createCall(
            @Valid @RequestBody CallCenterDtos.CreateCallRequest request) {
        return ApiResponse.ok(callCenterService.createCall(request));
    }

    @GetMapping("/calls/{id}")
    @PreAuthorize("hasAuthority('crm:call:read')")
    public ApiResponse<CallCenterDtos.CallRecordDetailView> callDetail(@PathVariable UUID id) {
        return ApiResponse.ok(callCenterService.detail(id));
    }

    @PostMapping("/calls/{id}/link")
    @PreAuthorize("hasAuthority('crm:call:write')")
    public ApiResponse<CallCenterDtos.CallRecordDetailView> linkCall(
            @PathVariable UUID id, @Valid @RequestBody CallCenterDtos.LinkRequest request) {
        return ApiResponse.ok(callCenterService.link(id, request));
    }

    @GetMapping("/calls/{id}/recording")
    @PreAuthorize("hasAuthority('crm:call:recording:read')")
    public ApiResponse<CallCenterDtos.RecordingAccessView> recordingAccess(@PathVariable UUID id) {
        return ApiResponse.ok(callCenterService.recordingAccess(id));
    }

    // ---------- 短信模板 ----------

    @GetMapping("/sms/templates")
    @PreAuthorize("hasAuthority('crm:sms:read')")
    public ApiResponse<PageResponse<CallCenterDtos.SmsTemplateView>> listTemplates(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return ApiResponse.ok(smsService.listTemplates(keyword, status, page, pageSize));
    }

    @GetMapping("/sms/templates/{id}")
    @PreAuthorize("hasAuthority('crm:sms:read')")
    public ApiResponse<CallCenterDtos.SmsTemplateView> templateDetail(@PathVariable UUID id) {
        return ApiResponse.ok(smsService.templateDetail(id));
    }

    @PostMapping("/sms/templates")
    @PreAuthorize("hasAuthority('crm:sms:template:manage')")
    public ApiResponse<CallCenterDtos.SmsTemplateView> createTemplate(
            @Valid @RequestBody CallCenterDtos.TemplateCreateRequest request) {
        return ApiResponse.ok(smsService.createTemplate(request));
    }

    @PutMapping("/sms/templates/{id}")
    @PreAuthorize("hasAuthority('crm:sms:template:manage')")
    public ApiResponse<CallCenterDtos.SmsTemplateView> updateTemplate(
            @PathVariable UUID id, @Valid @RequestBody CallCenterDtos.TemplateUpdateRequest request) {
        return ApiResponse.ok(smsService.updateTemplate(id, request));
    }

    @PostMapping("/sms/templates/{id}/status")
    @PreAuthorize("hasAuthority('crm:sms:template:manage')")
    public ApiResponse<CallCenterDtos.SmsTemplateView> updateTemplateStatus(
            @PathVariable UUID id, @Valid @RequestBody CallCenterDtos.TemplateStatusRequest request) {
        return ApiResponse.ok(smsService.updateTemplateStatus(id, request.status()));
    }

    // ---------- 消息队列 ----------

    @PostMapping("/messages")
    @PreAuthorize("hasAuthority('crm:sms:send')")
    public ApiResponse<CallCenterDtos.MessageView> enqueue(
            @Valid @RequestBody CallCenterDtos.MessageSendRequest request) {
        return ApiResponse.ok(smsService.enqueue(request));
    }

    @GetMapping("/messages")
    @PreAuthorize("hasAuthority('crm:sms:read')")
    public ApiResponse<PageResponse<CallCenterDtos.MessageView>> listMessages(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String channel,
            @RequestParam(required = false) String scene,
            @RequestParam(required = false) String bizKey,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return ApiResponse.ok(smsService.listMessages(status, channel, scene, bizKey, keyword, page, pageSize));
    }

    @PostMapping("/messages/{id}/retry")
    @PreAuthorize("hasAuthority('crm:sms:send')")
    public ApiResponse<CallCenterDtos.MessageView> retryMessage(@PathVariable UUID id) {
        return ApiResponse.ok(smsService.retry(id));
    }

    @PostMapping("/messages/dispatch")
    @PreAuthorize("hasAuthority('crm:sms:send')")
    public ApiResponse<CallCenterDtos.DispatchResult> dispatch() {
        return ApiResponse.ok(smsService.dispatch());
    }
}
