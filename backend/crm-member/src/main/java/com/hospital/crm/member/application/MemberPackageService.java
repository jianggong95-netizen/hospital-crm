package com.hospital.crm.member.application;

import com.hospital.crm.common.api.ErrorCode;
import com.hospital.crm.common.exception.BusinessException;
import com.hospital.crm.member.api.MemberDtos;
import com.hospital.crm.member.domain.MemberAccount;
import com.hospital.crm.member.domain.MemberCard;
import com.hospital.crm.member.domain.MemberCardRepository;
import com.hospital.crm.member.domain.MemberPackage;
import com.hospital.crm.member.domain.MemberPackageRepository;
import com.hospital.crm.system.audit.AuditService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 会员套餐：目录维护与购买。TIMES 类型生成次卡，BALANCE 入余额，POINTS 入积分。
 * 仅启用状态的套餐可购买，购买权益统一写会员账本。
 */
@Service
public class MemberPackageService {

    private static final String ACTIVE = "active";

    private final MemberPackageRepository packageRepository;
    private final MemberCardRepository cardRepository;
    private final MemberAccountService accountService;
    private final AuditService auditService;

    public MemberPackageService(MemberPackageRepository packageRepository,
                                MemberCardRepository cardRepository,
                                MemberAccountService accountService,
                                AuditService auditService) {
        this.packageRepository = packageRepository;
        this.cardRepository = cardRepository;
        this.accountService = accountService;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<MemberDtos.PackageView> list(String status) {
        List<MemberPackage> packages = status == null || status.isBlank()
                ? packageRepository.findAllByOrderByCreatedAtAsc()
                : packageRepository.findAllByStatusOrderByCreatedAtAsc(status);
        return packages.stream().map(this::toView).toList();
    }

    @Transactional
    public MemberDtos.PackageView create(MemberDtos.PackageCreateRequest request) {
        if (packageRepository.existsById(request.packageCode())) {
            throw new BusinessException(ErrorCode.BUSINESS_CONFLICT, "套餐编码已存在");
        }
        MemberPackage entity = new MemberPackage();
        entity.setPackageCode(request.packageCode());
        entity.setPackageName(request.packageName());
        entity.setPackageType(request.packageType());
        entity.setPrice(request.price());
        entity.setTimes(request.times());
        entity.setBalanceAmount(request.balanceAmount());
        entity.setPointsAmount(request.pointsAmount());
        entity.setValidDays(request.validDays() == null ? 365 : request.validDays());
        entity.setDescription(request.description());
        entity.setStatus(ACTIVE);
        validatePayload(entity);
        MemberPackage saved = packageRepository.save(entity);
        auditService.record("MEMBER_PACKAGE_CREATE", "CRM_MEMBER_PACKAGE", saved.getPackageCode(),
                Map.of("packageName", saved.getPackageName(), "packageType", saved.getPackageType()));
        return toView(saved);
    }

    @Transactional
    public MemberDtos.PackageView update(String packageCode, MemberDtos.PackageUpdateRequest request) {
        MemberPackage entity = requirePackage(packageCode);
        entity.setPackageName(request.packageName());
        entity.setPrice(request.price());
        entity.setTimes(request.times());
        entity.setBalanceAmount(request.balanceAmount());
        entity.setPointsAmount(request.pointsAmount());
        entity.setValidDays(request.validDays() == null ? entity.getValidDays() : request.validDays());
        entity.setDescription(request.description());
        validatePayload(entity);
        MemberPackage saved = packageRepository.save(entity);
        auditService.record("MEMBER_PACKAGE_UPDATE", "CRM_MEMBER_PACKAGE", saved.getPackageCode(),
                Map.of("packageName", saved.getPackageName()));
        return toView(saved);
    }

    @Transactional
    public MemberDtos.PackageView changeStatus(String packageCode, String status) {
        MemberPackage entity = requirePackage(packageCode);
        entity.setStatus(status);
        MemberPackage saved = packageRepository.save(entity);
        auditService.record("MEMBER_PACKAGE_STATUS", "CRM_MEMBER_PACKAGE", saved.getPackageCode(),
                Map.of("status", status));
        return toView(saved);
    }

    @Transactional
    public MemberDtos.PurchaseResult purchase(String packageCode, MemberDtos.PurchaseRequest request) {
        MemberPackage entity = requirePackage(packageCode);
        if (!ACTIVE.equals(entity.getStatus())) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_FAILED, "套餐未启用，不能购买");
        }
        MemberAccount account = accountService.ensureAccount(request.customerId());
        String reason = "购买套餐 " + entity.getPackageName();

        if ("TIMES".equals(entity.getPackageType())) {
            LocalDate today = LocalDate.now();
            MemberCard card = new MemberCard();
            card.setCardNo("C" + cardRepository.nextCardNo());
            card.setCustomerId(request.customerId());
            card.setPackageCode(entity.getPackageCode());
            card.setPackageName(entity.getPackageName());
            card.setTotalTimes(entity.getTimes());
            card.setUsedTimes(0);
            card.setRemainingTimes(entity.getTimes());
            card.setValidFrom(today);
            card.setValidTo(today.plusDays(entity.getValidDays()));
            card.setStatus("ACTIVE");
            card.setSourceOrderId(request.orderId());
            MemberCard savedCard = cardRepository.save(card);
            MemberDtos.AccountView accountView = accountService.addPackageBenefit(
                    account, BigDecimal.ZERO, 0L, request.orderId(), reason);
            auditService.record("MEMBER_PACKAGE_PURCHASE", "CRM_MEMBER_PACKAGE", entity.getPackageCode(),
                    Map.of("customerId", request.customerId().toString(), "cardNo", savedCard.getCardNo(),
                            "totalTimes", String.valueOf(savedCard.getTotalTimes())));
            return new MemberDtos.PurchaseResult(accountView, toCardSummary(savedCard), BigDecimal.ZERO, 0L);
        }

        BigDecimal balance = "BALANCE".equals(entity.getPackageType())
                ? (entity.getBalanceAmount() == null ? BigDecimal.ZERO : entity.getBalanceAmount())
                : BigDecimal.ZERO;
        long points = "POINTS".equals(entity.getPackageType()) && entity.getPointsAmount() != null
                ? entity.getPointsAmount() : 0L;
        MemberDtos.AccountView accountView = accountService.addPackageBenefit(
                account, balance, points, request.orderId(), reason);
        auditService.record("MEMBER_PACKAGE_PURCHASE", "CRM_MEMBER_PACKAGE", entity.getPackageCode(),
                Map.of("customerId", request.customerId().toString(),
                        "balance", balance.toPlainString(), "points", Long.toString(points)));
        return new MemberDtos.PurchaseResult(accountView, null, balance, points);
    }

    private void validatePayload(MemberPackage entity) {
        if ("TIMES".equals(entity.getPackageType()) && (entity.getTimes() == null || entity.getTimes() <= 0)) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_FAILED, "次卡套餐必须配置大于 0 的次数");
        }
        if ("BALANCE".equals(entity.getPackageType())
                && (entity.getBalanceAmount() == null || entity.getBalanceAmount().signum() <= 0)) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_FAILED, "储值套餐必须配置大于 0 的金额");
        }
        if ("POINTS".equals(entity.getPackageType())
                && (entity.getPointsAmount() == null || entity.getPointsAmount() <= 0)) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_FAILED, "积分套餐必须配置大于 0 的积分");
        }
    }

    private MemberPackage requirePackage(String packageCode) {
        return packageRepository.findById(packageCode)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "套餐不存在"));
    }

    private MemberDtos.PackageView toView(MemberPackage entity) {
        return new MemberDtos.PackageView(entity.getPackageCode(), entity.getPackageName(), entity.getPackageType(),
                entity.getPrice(), entity.getTimes(), entity.getBalanceAmount(), entity.getPointsAmount(),
                entity.getValidDays(), entity.getStatus(), entity.getDescription());
    }

    private MemberDtos.CardSummaryView toCardSummary(MemberCard card) {
        return new MemberDtos.CardSummaryView(card.getId(), card.getCardNo(), card.getPackageCode(),
                card.getPackageName(), card.getTotalTimes(), card.getUsedTimes(), card.getRemainingTimes(),
                card.getValidFrom(), card.getValidTo(), card.getStatus());
    }
}
