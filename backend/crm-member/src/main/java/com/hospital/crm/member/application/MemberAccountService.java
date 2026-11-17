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
import com.hospital.crm.member.domain.MemberLedger;
import com.hospital.crm.member.domain.MemberLedgerRepository;
import com.hospital.crm.member.domain.MemberLevel;
import com.hospital.crm.member.domain.MemberLevelRepository;
import com.hospital.crm.member.domain.MemberShare;
import com.hospital.crm.member.domain.MemberShareRepository;
import com.hospital.crm.system.audit.AuditService;
import com.hospital.crm.system.security.CurrentUser;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * 会员账户：开户、充值、消费、积分调整、账本与退款权益返还。
 * 余额/积分的扣减使用条件更新，保证并发消费不超扣；每次变动都写账本。
 */
@Service
public class MemberAccountService {

    private static final String STATUS_ACTIVE = "active";
    private static final String SHARE_ACTIVE = "ACTIVE";

    private final MemberAccountRepository accountRepository;
    private final MemberLedgerRepository ledgerRepository;
    private final MemberLevelRepository levelRepository;
    private final MemberCardRepository cardRepository;
    private final MemberShareRepository shareRepository;
    private final CustomerService customerService;
    private final AuditService auditService;

    public MemberAccountService(MemberAccountRepository accountRepository,
                                MemberLedgerRepository ledgerRepository,
                                MemberLevelRepository levelRepository,
                                MemberCardRepository cardRepository,
                                MemberShareRepository shareRepository,
                                CustomerService customerService,
                                AuditService auditService) {
        this.accountRepository = accountRepository;
        this.ledgerRepository = ledgerRepository;
        this.levelRepository = levelRepository;
        this.cardRepository = cardRepository;
        this.shareRepository = shareRepository;
        this.customerService = customerService;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<MemberDtos.LevelView> levels() {
        return levelRepository.findAllByOrderBySortNoAsc().stream()
                .map(level -> new MemberDtos.LevelView(level.getLevelCode(), level.getLevelName(),
                        level.getMinPoints(), level.getDiscountRate(), level.getSortNo()))
                .toList();
    }

    @Transactional(readOnly = true)
    public PageResponse<MemberDtos.AccountView> list(String keyword, String level, String status,
                                                    int page, int pageSize) {
        PageRequest pageable = PageRequest.of(Math.max(page - 1, 0), clamp(pageSize),
                Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<MemberAccount> result = accountRepository.search(
                blankToNull(keyword), blankToNull(level), blankToNull(status), pageable);
        List<MemberDtos.AccountView> items = result.getContent().stream().map(this::toView).toList();
        return PageResponse.of(items, result.getTotalElements(), page, pageSize);
    }

    @Transactional(readOnly = true)
    public MemberDtos.AccountDetailView detail(UUID accountId) {
        MemberAccount account = accountRepository.findById(accountId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "会员账户不存在"));
        List<MemberDtos.CardSummaryView> cards = cardRepository
                .findByCustomerIdAndStatusOrderByValidToAsc(account.getCustomerId(), "ACTIVE").stream()
                .map(this::toCardSummary)
                .toList();
        return new MemberDtos.AccountDetailView(toView(account), cards);
    }

    @Transactional
    public MemberDtos.AccountView open(MemberDtos.CreateAccountRequest request) {
        customerService.brief(request.customerId());
        if (accountRepository.findByCustomerId(request.customerId()).isPresent()) {
            throw new BusinessException(ErrorCode.BUSINESS_CONFLICT, "该客户已开通会员账户");
        }
        MemberAccount account = new MemberAccount();
        account.setAccountNo("M" + accountRepository.nextAccountNo());
        account.setCustomerId(request.customerId());
        account.setMemberLevel("standard");
        account.setBalance(BigDecimal.ZERO);
        account.setPoints(0L);
        account.setStatus(STATUS_ACTIVE);
        MemberAccount saved = accountRepository.save(account);
        auditService.record("MEMBER_ACCOUNT_OPEN", "CRM_MEMBER_ACCOUNT", saved.getId().toString(),
                Map.of("accountNo", saved.getAccountNo(), "customerId", request.customerId().toString()));
        return toView(saved);
    }

    @Transactional
    public MemberDtos.AccountView recharge(MemberDtos.RechargeRequest request) {
        MemberAccount account = requireAccount(request.accountId(), "会员账户不存在");
        if (!STATUS_ACTIVE.equals(account.getStatus())) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_FAILED, "账户已停用，不能充值");
        }
        if (accountRepository.addBalance(account.getId(), request.amount(), Instant.now()) == 0) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_FAILED, "账户已停用，不能充值");
        }
        MemberAccount refreshed = reload(account.getId());
        writeLedger(refreshed, "RECHARGE", request.amount(), 0L, request.orderId(), null,
                request.reason() == null ? "会员充值" : request.reason(), null);
        auditService.record("MEMBER_RECHARGE", "CRM_MEMBER_ACCOUNT", refreshed.getId().toString(),
                Map.of("accountNo", refreshed.getAccountNo(), "amount", request.amount().toPlainString(),
                        "orderId", request.orderId() == null ? "" : request.orderId().toString()));
        return toView(refreshed);
    }

    /**
     * 余额消费：本人余额不足且允许共享时，尝试由已授权的家庭成员账户代付。
     */
    @Transactional
    public MemberDtos.AccountView consume(MemberDtos.ConsumeRequest request) {
        MemberAccount account = accountRepository.findByCustomerId(request.customerId())
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "会员账户不存在"));
        if (!STATUS_ACTIVE.equals(account.getStatus())) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_FAILED, "账户已停用，不能消费");
        }

        MemberAccount payer = account;
        boolean shared = false;
        if (account.getBalance().compareTo(request.amount()) < 0 && Boolean.TRUE.equals(request.allowShared())) {
            Optional<MemberAccount> ownerAccount = resolveSharedPayer(request.customerId(), request.amount());
            if (ownerAccount.isPresent()) {
                payer = ownerAccount.get();
                shared = true;
            }
        }

        if (accountRepository.deductBalance(payer.getId(), request.amount(), Instant.now()) == 0) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_FAILED, "账户余额不足");
        }
        MemberAccount refreshed = reload(payer.getId());
        writeLedger(refreshed, "CONSUME", request.amount().negate(), 0L, null, request.bizNo(),
                "会员消费" + (request.bizType() == null ? "" : "（" + request.bizType() + "）"), null);
        auditService.record("MEMBER_CONSUME", "CRM_MEMBER_ACCOUNT", refreshed.getId().toString(),
                Map.of("accountNo", refreshed.getAccountNo(), "amount", request.amount().toPlainString(),
                        "shared", Boolean.toString(shared), "bizNo", request.bizNo() == null ? "" : request.bizNo()));
        return toView(refreshed);
    }

    @Transactional
    public MemberDtos.AccountView adjustPoints(MemberDtos.PointsAdjustRequest request) {
        MemberAccount account = requireAccount(request.accountId(), "会员账户不存在");
        if (request.points() == 0) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_FAILED, "积分变动不能为 0");
        }
        int updated = request.points() > 0
                ? accountRepository.addPoints(account.getId(), request.points(), Instant.now())
                : accountRepository.deductPoints(account.getId(), -request.points(), Instant.now());
        if (updated == 0) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_FAILED,
                    request.points() > 0 ? "账户已停用，不能调整积分" : "账户积分不足");
        }
        MemberAccount refreshed = reload(account.getId());
        writeLedger(refreshed, "POINTS_ADJUST", BigDecimal.ZERO, request.points(), null, null,
                request.reason() == null ? "积分调整" : request.reason(), null);
        refreshLevel(refreshed);
        auditService.record("MEMBER_POINTS_ADJUST", "CRM_MEMBER_ACCOUNT", refreshed.getId().toString(),
                Map.of("accountNo", refreshed.getAccountNo(), "points", request.points().toString()));
        return toView(reload(account.getId()));
    }

    @Transactional(readOnly = true)
    public PageResponse<MemberDtos.LedgerView> ledger(UUID accountId, int page, int pageSize) {
        requireAccount(accountId, "会员账户不存在");
        PageRequest pageable = PageRequest.of(Math.max(page - 1, 0), clamp(pageSize));
        Page<MemberLedger> result = ledgerRepository.findByAccountIdOrderByCreatedAtDesc(accountId, pageable);
        List<MemberDtos.LedgerView> items = result.getContent().stream()
                .map(entry -> new MemberDtos.LedgerView(entry.getId(), entry.getLedgerType(), entry.getAmount(),
                        entry.getPointsDelta(), entry.getBalanceAfter(), entry.getPointsAfter(),
                        entry.getRelatedOrderId(), entry.getBizNo(), entry.getReason(), entry.getCreatedAt()))
                .toList();
        return PageResponse.of(items, result.getTotalElements(), page, pageSize);
    }

    /**
     * 退款返还权益：充值按退款金额扣回余额；套餐次卡作废并按剩余次数返还；积分按退款比例扣回。
     */
    @Transactional
    public MemberDtos.RefundReversalResult reverseOnRefund(MemberDtos.RefundReversalRequest request) {
        List<MemberLedger> entries = ledgerRepository.findByRelatedOrderIdOrderByCreatedAtAsc(request.orderId());
        if (entries.isEmpty()) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "该订单没有可返还的会员权益");
        }
        BigDecimal remainingRefund = request.refundAmount();
        BigDecimal balanceReversed = BigDecimal.ZERO;
        long pointsReversed = 0L;
        int cardsVoided = 0;
        int cardTimesReturned = 0;

        for (MemberLedger entry : entries) {
            if (remainingRefund.signum() <= 0) {
                break;
            }
            if ("RECHARGE".equals(entry.getLedgerType()) && entry.getAmount().signum() > 0) {
                BigDecimal target = remainingRefund.min(entry.getAmount());
                MemberAccount account = accountRepository.findById(entry.getAccountId()).orElse(null);
                if (account == null) {
                    continue;
                }
                BigDecimal capped = target.min(account.getBalance());
                if (capped.signum() > 0 && accountRepository.deductBalance(account.getId(), capped, Instant.now()) > 0) {
                    MemberAccount refreshed = reload(account.getId());
                    writeLedger(refreshed, "REFUND_REVERSAL", capped.negate(), 0L, request.orderId(), null,
                            "订单退款扣回充值余额", null);
                    balanceReversed = balanceReversed.add(capped);
                    remainingRefund = remainingRefund.subtract(capped);
                }
            } else if ("PACKAGE_PURCHASE".equals(entry.getLedgerType())
                    && entry.getPointsDelta() != null && entry.getPointsDelta() > 0
                    && entry.getAmount().signum() > 0) {
                BigDecimal ratio = request.refundAmount()
                        .divide(entry.getAmount(), 4, RoundingMode.HALF_UP)
                        .min(BigDecimal.ONE);
                long target = BigDecimal.valueOf(entry.getPointsDelta())
                        .multiply(ratio)
                        .setScale(0, RoundingMode.DOWN)
                        .longValue();
                MemberAccount account = accountRepository.findById(entry.getAccountId()).orElse(null);
                if (account == null || target <= 0) {
                    continue;
                }
                long capped = Math.min(target, account.getPoints());
                if (capped > 0 && accountRepository.deductPoints(account.getId(), capped, Instant.now()) > 0) {
                    MemberAccount refreshed = reload(account.getId());
                    writeLedger(refreshed, "REFUND_REVERSAL", BigDecimal.ZERO, -capped, request.orderId(), null,
                            "订单退款扣回套餐积分", null);
                    pointsReversed += capped;
                    remainingRefund = remainingRefund.subtract(entry.getAmount().multiply(ratio));
                }
            }
        }

        for (MemberCard card : cardRepository.findBySourceOrderId(request.orderId())) {
            if (cardRepository.markVoid(card.getId(), Instant.now()) > 0) {
                cardsVoided++;
                cardTimesReturned += card.getRemainingTimes();
                MemberAccount account = accountRepository.findByCustomerId(card.getCustomerId()).orElse(null);
                if (account != null) {
                    writeLedger(account, "CARD_VOID_ON_REFUND", BigDecimal.ZERO, 0L, request.orderId(),
                            card.getCardNo(), "订单退款作废次卡，返还剩余 " + card.getRemainingTimes() + " 次", null);
                }
            }
        }

        auditService.record("MEMBER_REFUND_REVERSAL", "CRM_MEMBER_LEDGER", request.orderId().toString(),
                Map.of("orderId", request.orderId().toString(),
                        "refundAmount", request.refundAmount().toPlainString(),
                        "balanceReversed", balanceReversed.toPlainString(),
                        "pointsReversed", Long.toString(pointsReversed),
                        "cardsVoided", Integer.toString(cardsVoided)));
        return new MemberDtos.RefundReversalResult(request.orderId(), balanceReversed, pointsReversed,
                cardsVoided, cardTimesReturned);
    }

    private Optional<MemberAccount> resolveSharedPayer(UUID beneficiaryCustomerId, BigDecimal amount) {
        List<MemberShare> shares = shareRepository
                .findByBeneficiaryCustomerIdAndResourceTypeAndStatusAndCanConsumeTrueOrderByCreatedAtAsc(
                        beneficiaryCustomerId, "BALANCE", SHARE_ACTIVE);
        Instant now = Instant.now();
        for (MemberShare share : shares) {
            if (share.getValidFrom() != null && share.getValidFrom().isAfter(now)) {
                continue;
            }
            if (share.getValidTo() != null && share.getValidTo().isBefore(now)) {
                continue;
            }
            Optional<MemberAccount> owner = accountRepository.findByCustomerId(share.getOwnerCustomerId());
            if (owner.isPresent() && STATUS_ACTIVE.equals(owner.get().getStatus())
                    && owner.get().getBalance().compareTo(amount) >= 0) {
                return owner;
            }
        }
        return Optional.empty();
    }

    private void refreshLevel(MemberAccount account) {
        long points = account.getPoints() == null ? 0L : account.getPoints();
        levelRepository.findFirstByStatusAndMinPointsLessThanEqualOrderByMinPointsDesc(STATUS_ACTIVE, points)
                .map(MemberLevel::getLevelCode)
                .filter(code -> !code.equals(account.getMemberLevel()))
                .ifPresent(code -> accountRepository.updateLevel(account.getId(), code, Instant.now()));
    }

    private void writeLedger(MemberAccount account, String type, BigDecimal amount, Long pointsDelta,
                             UUID orderId, String bizNo, String reason, UUID operatorId) {
        MemberLedger ledger = new MemberLedger();
        ledger.setAccountId(account.getId());
        ledger.setLedgerType(type);
        ledger.setAmount(amount);
        ledger.setPointsDelta(pointsDelta);
        ledger.setBalanceAfter(account.getBalance());
        ledger.setPointsAfter(account.getPoints());
        ledger.setRelatedOrderId(orderId);
        ledger.setBizNo(bizNo);
        ledger.setReason(reason);
        ledger.setOperatorId(operatorId == null ? currentUserId() : operatorId);
        ledgerRepository.save(ledger);
    }

    private UUID currentUserId() {
        return CurrentUser.principal() == null ? null : CurrentUser.principal().userId();
    }

    private MemberAccount requireAccount(UUID accountId, String message) {
        return accountRepository.findById(accountId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, message));
    }

    private MemberAccount reload(UUID accountId) {
        return accountRepository.findById(accountId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "会员账户不存在"));
    }

    private MemberDtos.AccountView toView(MemberAccount account) {
        MemberLevel level = levelRepository.findById(account.getMemberLevel()).orElse(null);
        String customerName = null;
        try {
            customerName = customerService.brief(account.getCustomerId()).name();
        } catch (RuntimeException ignored) {
            // 客户档案不可用时账户仍可展示，不阻断列表
        }
        return new MemberDtos.AccountView(account.getId(), account.getAccountNo(), account.getCustomerId(),
                customerName, account.getMemberLevel(),
                level == null ? account.getMemberLevel() : level.getLevelName(),
                level == null ? BigDecimal.ONE : level.getDiscountRate(),
                account.getBalance(), account.getFrozenBalance(), account.getPoints(), account.getStatus(),
                account.getCreatedAt(), account.getUpdatedAt());
    }

    private MemberDtos.CardSummaryView toCardSummary(MemberCard card) {
        return new MemberDtos.CardSummaryView(card.getId(), card.getCardNo(), card.getPackageCode(),
                card.getPackageName(), card.getTotalTimes(), card.getUsedTimes(), card.getRemainingTimes(),
                card.getValidFrom(), card.getValidTo(), card.getStatus());
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

    /** 供套餐服务复用：按客户取账户，不存在时自动开户。 */
    @Transactional
    public MemberAccount ensureAccount(UUID customerId) {
        return accountRepository.findByCustomerId(customerId).orElseGet(() -> {
            customerService.brief(customerId);
            MemberAccount account = new MemberAccount();
            account.setAccountNo("M" + accountRepository.nextAccountNo());
            account.setCustomerId(customerId);
            account.setMemberLevel("standard");
            account.setBalance(BigDecimal.ZERO);
            account.setPoints(0L);
            account.setStatus(STATUS_ACTIVE);
            return accountRepository.save(account);
        });
    }

    @Transactional
    public MemberDtos.AccountView addPackageBenefit(MemberAccount account, BigDecimal balance, long points,
                                                    UUID orderId, String reason) {
        if (balance != null && balance.signum() > 0) {
            accountRepository.addBalance(account.getId(), balance, Instant.now());
        }
        if (points > 0) {
            accountRepository.addPoints(account.getId(), points, Instant.now());
        }
        MemberAccount refreshed = reload(account.getId());
        writeLedger(refreshed, "PACKAGE_PURCHASE", balance == null ? BigDecimal.ZERO : balance, points,
                orderId, null, reason, null);
        refreshLevel(refreshed);
        return toView(reload(account.getId()));
    }
}
