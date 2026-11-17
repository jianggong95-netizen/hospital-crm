package com.hospital.crm.member.application;

import com.hospital.crm.common.api.ErrorCode;
import com.hospital.crm.common.api.PageResponse;
import com.hospital.crm.common.exception.BusinessException;
import com.hospital.crm.customer.application.CustomerService;
import com.hospital.crm.member.api.MemberDtos;
import com.hospital.crm.member.domain.MemberAccount;
import com.hospital.crm.member.domain.MemberAccountRepository;
import com.hospital.crm.member.domain.MemberCard;
import com.hospital.crm.member.domain.MemberCardRepository;
import com.hospital.crm.member.domain.MemberCardUsage;
import com.hospital.crm.member.domain.MemberCardUsageRepository;
import com.hospital.crm.member.domain.MemberLedger;
import com.hospital.crm.member.domain.MemberLedgerRepository;
import com.hospital.crm.system.audit.AuditService;
import com.hospital.crm.system.security.CurrentUser;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 次卡：扣次与返还。扣次使用条件更新（remaining_times >= times），并发下不会超扣；
 * 每次扣次与返还都写扣次记录与会员账本。
 */
@Service
public class MemberCardService {

    private static final String ACTIVE = "ACTIVE";
    private static final String CONSUMED = "CONSUMED";

    private final MemberCardRepository cardRepository;
    private final MemberCardUsageRepository usageRepository;
    private final MemberAccountRepository accountRepository;
    private final MemberLedgerRepository ledgerRepository;
    private final CustomerService customerService;
    private final AuditService auditService;

    public MemberCardService(MemberCardRepository cardRepository,
                             MemberCardUsageRepository usageRepository,
                             MemberAccountRepository accountRepository,
                             MemberLedgerRepository ledgerRepository,
                             CustomerService customerService,
                             AuditService auditService) {
        this.cardRepository = cardRepository;
        this.usageRepository = usageRepository;
        this.accountRepository = accountRepository;
        this.ledgerRepository = ledgerRepository;
        this.customerService = customerService;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public PageResponse<MemberDtos.CardView> list(UUID customerId, String status, int page, int pageSize) {
        PageRequest pageable = PageRequest.of(Math.max(page - 1, 0), clamp(pageSize));
        Page<MemberCard> result = cardRepository.search(customerId, blankToNull(status), pageable);
        List<MemberDtos.CardView> items = result.getContent().stream().map(this::toView).toList();
        return PageResponse.of(items, result.getTotalElements(), page, pageSize);
    }

    @Transactional(readOnly = true)
    public MemberDtos.CardDetailView detail(UUID cardId) {
        MemberCard card = requireCard(cardId);
        List<MemberDtos.CardUsageView> usages = usageRepository
                .findByCardIdOrderByCreatedAtDesc(cardId, PageRequest.of(0, 50)).getContent().stream()
                .map(usage -> new MemberDtos.CardUsageView(usage.getId(), usage.getTimes(), usage.getBizType(),
                        usage.getBizNo(), usage.getStatus(), usage.getRemark(), usage.getCreatedAt(),
                        usage.getRefundedAt()))
                .toList();
        return new MemberDtos.CardDetailView(toView(card), usages);
    }

    @Transactional
    public MemberDtos.CardView consume(UUID cardId, MemberDtos.CardConsumeRequest request) {
        MemberCard card = requireCard(cardId);
        if (!ACTIVE.equals(card.getStatus())) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_FAILED, "次卡当前状态不可扣次");
        }
        if (card.getValidTo() != null && card.getValidTo().isBefore(LocalDate.now())) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_FAILED, "次卡已过有效期");
        }
        if (cardRepository.consumeTimes(cardId, request.times(), Instant.now()) == 0) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_FAILED, "次卡剩余次数不足");
        }

        MemberCardUsage usage = new MemberCardUsage();
        usage.setCardId(cardId);
        usage.setTimes(request.times());
        usage.setBizType(request.bizType());
        usage.setBizNo(request.bizNo());
        usage.setRemark(request.remark());
        usage.setStatus(CONSUMED);
        usage.setOperatorId(currentUserId());
        usageRepository.save(usage);

        MemberCard refreshed = requireCard(cardId);
        if (refreshed.getRemainingTimes() != null && refreshed.getRemainingTimes() == 0) {
            cardRepository.markExhausted(cardId, Instant.now());
            refreshed = requireCard(cardId);
        }
        writeLedger(refreshed, "CARD_CONSUME", "次卡扣次 " + request.times() + " 次（剩余 "
                + refreshed.getRemainingTimes() + " 次）");
        auditService.record("MEMBER_CARD_CONSUME", "CRM_MEMBER_CARD", cardId.toString(),
                Map.of("cardNo", refreshed.getCardNo(), "times", request.times().toString(),
                        "remainingTimes", String.valueOf(refreshed.getRemainingTimes()),
                        "bizNo", request.bizNo() == null ? "" : request.bizNo()));
        return toView(refreshed);
    }

    /**
     * 返还次数：若指定扣次记录，则将原记录置为 REFUNDED（不可重复返还），并回补次数。
     */
    @Transactional
    public MemberDtos.CardView refund(UUID cardId, MemberDtos.CardRefundRequest request) {
        MemberCard card = requireCard(cardId);
        if ("VOID".equals(card.getStatus())) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_FAILED, "次卡已作废，不能返还");
        }
        if (request.usageId() != null) {
            MemberCardUsage usage = usageRepository.findById(request.usageId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "扣次记录不存在"));
            if (!usage.getCardId().equals(cardId)) {
                throw new BusinessException(ErrorCode.BUSINESS_RULE_FAILED, "扣次记录与次卡不匹配");
            }
            if (usageRepository.markRefunded(usage.getId(), Instant.now()) == 0) {
                throw new BusinessException(ErrorCode.BUSINESS_RULE_FAILED, "该扣次记录已返还，请勿重复操作");
            }
        }
        if (cardRepository.refundTimes(cardId, request.times(), Instant.now()) == 0) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_FAILED, "返还次数超过已使用次数");
        }
        MemberCard refreshed = requireCard(cardId);
        writeLedger(refreshed, "CARD_REFUND", "次卡返还 " + request.times() + " 次（剩余 "
                + refreshed.getRemainingTimes() + " 次）");
        auditService.record("MEMBER_CARD_REFUND", "CRM_MEMBER_CARD", cardId.toString(),
                Map.of("cardNo", refreshed.getCardNo(), "times", request.times().toString(),
                        "remainingTimes", String.valueOf(refreshed.getRemainingTimes()),
                        "reason", request.reason() == null ? "" : request.reason()));
        return toView(refreshed);
    }

    private void writeLedger(MemberCard card, String type, String reason) {
        accountRepository.findByCustomerId(card.getCustomerId()).ifPresent(account -> {
            MemberLedger ledger = new MemberLedger();
            ledger.setAccountId(account.getId());
            ledger.setLedgerType(type);
            ledger.setAmount(BigDecimal.ZERO);
            ledger.setPointsDelta(0L);
            ledger.setBalanceAfter(account.getBalance());
            ledger.setPointsAfter(account.getPoints());
            ledger.setBizNo(card.getCardNo());
            ledger.setReason(reason);
            ledger.setOperatorId(currentUserId());
            ledgerRepository.save(ledger);
        });
    }

    private MemberCard requireCard(UUID cardId) {
        return cardRepository.findById(cardId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "次卡不存在"));
    }

    private MemberDtos.CardView toView(MemberCard card) {
        String customerName = null;
        try {
            customerName = customerService.brief(card.getCustomerId()).name();
        } catch (RuntimeException ignored) {
            // 客户档案不可用时仍展示次卡本身
        }
        return new MemberDtos.CardView(card.getId(), card.getCardNo(), card.getCustomerId(), customerName,
                card.getPackageCode(), card.getPackageName(), card.getTotalTimes(), card.getUsedTimes(),
                card.getRemainingTimes(), card.getValidFrom(), card.getValidTo(), card.getStatus(),
                card.getSourceOrderId());
    }

    private UUID currentUserId() {
        return CurrentUser.principal() == null ? null : CurrentUser.principal().userId();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static int clamp(int pageSize) {
        if (pageSize <= 0) {
            return 20;
        }
        return Math.min(pageSize, 100);
    }
}
