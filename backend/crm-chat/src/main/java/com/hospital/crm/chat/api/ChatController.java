package com.hospital.crm.chat.api;

import com.hospital.crm.chat.application.ChatService;
import com.hospital.crm.common.api.ApiResponse;
import com.hospital.crm.common.api.PageResponse;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * 在线客服与人工工作台接口：会话、消息、排队、转人工、接管、转接、结束与历史。
 */
@RestController
@RequestMapping("/api/v1/chat")
public class ChatController {

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    @GetMapping("/queue")
    @PreAuthorize("hasAuthority('crm:chat:handle')")
    public ApiResponse<List<ChatDtos.QueueItemView>> queue() {
        return ApiResponse.ok(chatService.queue());
    }

    @GetMapping("/sessions")
    @PreAuthorize("hasAuthority('crm:chat:read')")
    public ApiResponse<PageResponse<ChatDtos.SessionView>> listSessions(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String channel,
            @RequestParam(required = false) UUID customerId,
            @RequestParam(required = false) UUID assigneeId,
            @RequestParam(required = false) String priority,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return ApiResponse.ok(chatService.listSessions(keyword, status, channel, customerId, assigneeId,
                priority, dateFrom, dateTo, page, pageSize));
    }

    @PostMapping("/sessions")
    @PreAuthorize("hasAuthority('crm:chat:handle')")
    public ApiResponse<ChatDtos.SessionDetail> createSession(
            @Valid @RequestBody ChatDtos.CreateSessionRequest request) {
        return ApiResponse.ok(chatService.createSession(request));
    }

    @GetMapping("/sessions/{sessionId}")
    @PreAuthorize("hasAuthority('crm:chat:read')")
    public ApiResponse<ChatDtos.SessionDetail> detail(@PathVariable UUID sessionId) {
        return ApiResponse.ok(chatService.detail(sessionId));
    }

    @GetMapping("/sessions/{sessionId}/messages")
    @PreAuthorize("hasAuthority('crm:chat:read')")
    public ApiResponse<PageResponse<ChatDtos.MessageView>> messages(
            @PathVariable UUID sessionId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "50") int pageSize) {
        return ApiResponse.ok(chatService.messages(sessionId, page, pageSize));
    }

    @PostMapping("/sessions/{sessionId}/messages")
    @PreAuthorize("hasAuthority('crm:chat:handle')")
    public ApiResponse<ChatDtos.SessionDetail> sendMessage(
            @PathVariable UUID sessionId,
            @Valid @RequestBody ChatDtos.MessageCreateRequest request) {
        return ApiResponse.ok(chatService.sendMessage(sessionId, request));
    }

    @PostMapping("/sessions/{sessionId}/handoff")
    @PreAuthorize("hasAuthority('crm:chat:handle')")
    public ApiResponse<ChatDtos.HandoffResult> requestHandoff(
            @PathVariable UUID sessionId,
            @Valid @RequestBody ChatDtos.HandoffRequest request) {
        return ApiResponse.ok(chatService.requestHandoff(sessionId, request));
    }

    @PostMapping("/sessions/{sessionId}/claim")
    @PreAuthorize("hasAuthority('crm:chat:handle')")
    public ApiResponse<ChatDtos.SessionDetail> claim(@PathVariable UUID sessionId) {
        return ApiResponse.ok(chatService.claim(sessionId));
    }

    @PostMapping("/sessions/{sessionId}/transfer")
    @PreAuthorize("hasAuthority('crm:chat:handle')")
    public ApiResponse<ChatDtos.SessionDetail> transfer(
            @PathVariable UUID sessionId,
            @Valid @RequestBody ChatDtos.TransferRequest request) {
        return ApiResponse.ok(chatService.transfer(sessionId, request));
    }

    @PostMapping("/sessions/{sessionId}/close")
    @PreAuthorize("hasAuthority('crm:chat:handle')")
    public ApiResponse<ChatDtos.SessionView> close(
            @PathVariable UUID sessionId,
            @Valid @RequestBody(required = false) ChatDtos.CloseRequest request) {
        return ApiResponse.ok(chatService.close(sessionId,
                request == null ? new ChatDtos.CloseRequest(null) : request));
    }
}
