package com.hospital.crm.member.api;

import com.hospital.crm.common.api.ApiResponse;
import com.hospital.crm.common.api.PageResponse;
import com.hospital.crm.member.application.MemberCardService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * 次卡查询、扣次与返还接口。
 */
@RestController
@RequestMapping("/api/v1/memberships/cards")
public class MemberCardController {

    private final MemberCardService cardService;

    public MemberCardController(MemberCardService cardService) {
        this.cardService = cardService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('crm:member:read')")
    public ApiResponse<PageResponse<MemberDtos.CardView>> list(
            @RequestParam(required = false) UUID customerId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return ApiResponse.ok(cardService.list(customerId, status, page, pageSize));
    }

    @GetMapping("/{cardId}")
    @PreAuthorize("hasAuthority('crm:member:read')")
    public ApiResponse<MemberDtos.CardDetailView> detail(@PathVariable UUID cardId) {
        return ApiResponse.ok(cardService.detail(cardId));
    }

    @PostMapping("/{cardId}/consumptions")
    @PreAuthorize("hasAuthority('crm:member:write')")
    public ApiResponse<MemberDtos.CardView> consume(
            @PathVariable UUID cardId,
            @Valid @RequestBody MemberDtos.CardConsumeRequest request) {
        return ApiResponse.ok(cardService.consume(cardId, request));
    }

    @PostMapping("/{cardId}/refunds")
    @PreAuthorize("hasAuthority('crm:member:write')")
    public ApiResponse<MemberDtos.CardView> refund(
            @PathVariable UUID cardId,
            @Valid @RequestBody MemberDtos.CardRefundRequest request) {
        return ApiResponse.ok(cardService.refund(cardId, request));
    }
}
