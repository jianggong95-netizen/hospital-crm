package com.hospital.crm.member.api;

import com.hospital.crm.common.api.ApiResponse;
import com.hospital.crm.common.api.PageResponse;
import com.hospital.crm.member.application.MemberAccountService;
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
 * 会员账户、等级、账本、充值与消费接口。
 */
@RestController
@RequestMapping("/api/v1/memberships")
public class MemberAccountController {

    private final MemberAccountService accountService;

    public MemberAccountController(MemberAccountService accountService) {
        this.accountService = accountService;
    }

    @GetMapping("/levels")
    @PreAuthorize("hasAuthority('crm:member:read')")
    public ApiResponse<List<MemberDtos.LevelView>> levels() {
        return ApiResponse.ok(accountService.levels());
    }

    @GetMapping("/accounts")
    @PreAuthorize("hasAuthority('crm:member:read')")
    public ApiResponse<PageResponse<MemberDtos.AccountView>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String level,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return ApiResponse.ok(accountService.list(keyword, level, status, page, pageSize));
    }

    @GetMapping("/accounts/{accountId}")
    @PreAuthorize("hasAuthority('crm:member:read')")
    public ApiResponse<MemberDtos.AccountDetailView> detail(@PathVariable UUID accountId) {
        return ApiResponse.ok(accountService.detail(accountId));
    }

    @GetMapping("/accounts/{accountId}/ledger")
    @PreAuthorize("hasAuthority('crm:member:read')")
    public ApiResponse<PageResponse<MemberDtos.LedgerView>> ledger(
            @PathVariable UUID accountId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return ApiResponse.ok(accountService.ledger(accountId, page, pageSize));
    }

    @PostMapping("/accounts")
    @PreAuthorize("hasAuthority('crm:member:write')")
    public ApiResponse<MemberDtos.AccountView> open(
            @Valid @RequestBody MemberDtos.CreateAccountRequest request) {
        return ApiResponse.ok(accountService.open(request));
    }

    @PostMapping("/recharges")
    @PreAuthorize("hasAuthority('crm:member:write')")
    public ApiResponse<MemberDtos.AccountView> recharge(
            @Valid @RequestBody MemberDtos.RechargeRequest request) {
        return ApiResponse.ok(accountService.recharge(request));
    }

    @PostMapping("/consumptions")
    @PreAuthorize("hasAuthority('crm:member:write')")
    public ApiResponse<MemberDtos.AccountView> consume(
            @Valid @RequestBody MemberDtos.ConsumeRequest request) {
        return ApiResponse.ok(accountService.consume(request));
    }

    @PostMapping("/points/adjustments")
    @PreAuthorize("hasAuthority('crm:member:write')")
    public ApiResponse<MemberDtos.AccountView> adjustPoints(
            @Valid @RequestBody MemberDtos.PointsAdjustRequest request) {
        return ApiResponse.ok(accountService.adjustPoints(request));
    }

    @PostMapping("/refund-reversals")
    @PreAuthorize("hasAuthority('crm:member:manage')")
    public ApiResponse<MemberDtos.RefundReversalResult> reverseOnRefund(
            @Valid @RequestBody MemberDtos.RefundReversalRequest request) {
        return ApiResponse.ok(accountService.reverseOnRefund(request));
    }
}
