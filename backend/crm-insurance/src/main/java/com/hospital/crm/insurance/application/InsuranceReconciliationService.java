package com.hospital.crm.insurance.application;

import com.hospital.crm.common.api.ErrorCode;
import com.hospital.crm.common.api.PageResponse;
import com.hospital.crm.common.exception.BusinessException;
import com.hospital.crm.insurance.api.InsuranceDtos;
import com.hospital.crm.insurance.domain.InsuranceCaseRepository;
import com.hospital.crm.insurance.domain.InsuranceCompany;
import com.hospital.crm.insurance.domain.InsuranceCompanyRepository;
import com.hospital.crm.insurance.domain.InsuranceReconciliation;
import com.hospital.crm.insurance.domain.InsuranceReconciliationRepository;
import com.hospital.crm.system.audit.AuditService;
import com.hospital.crm.system.security.CurrentUser;
import com.hospital.crm.system.user.SysUser;
import com.hospital.crm.system.user.SysUserRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 商保理赔对账服务：按保险公司 + 账期汇总账期内已决案件的医院应收与实际到账，
 * |差额| < 0.01 判为 matched，否则 mismatch；确认后不可重复确认。
 */
@Service
public class InsuranceReconciliationService {

    private static final String RESOURCE = "INSURANCE_RECONCILIATION";
    private static final Pattern PERIOD_PATTERN = Pattern.compile("\\d{4}-(0[1-9]|1[0-2])");
    private static final Set<String> STATUSES = Set.of("pending", "matched", "mismatch");

    private final InsuranceReconciliationRepository reconciliationRepository;
    private final InsuranceCompanyRepository companyRepository;
    private final InsuranceCaseRepository caseRepository;
    private final SysUserRepository userRepository;
    private final AuditService auditService;

    public InsuranceReconciliationService(InsuranceReconciliationRepository reconciliationRepository,
                                          InsuranceCompanyRepository companyRepository,
                                          InsuranceCaseRepository caseRepository,
                                          SysUserRepository userRepository,
                                          AuditService auditService) {
        this.reconciliationRepository = reconciliationRepository;
        this.companyRepository = companyRepository;
        this.caseRepository = caseRepository;
        this.userRepository = userRepository;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public PageResponse<InsuranceDtos.ReconciliationView> list(UUID companyId, String period,
                                                               String status, int page, int pageSize) {
        if (status != null && !status.isBlank() && !STATUSES.contains(status)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "对账状态取值不合法");
        }
        int safePage = Math.max(page, 1);
        int safePageSize = Math.min(Math.max(pageSize, 1), 100);
        Pageable pageable = PageRequest.of(safePage - 1, safePageSize,
                Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<InsuranceReconciliation> result = reconciliationRepository.findAll(
                buildSpecification(companyId, period, status), pageable);
        return PageResponse.of(toViews(result.getContent()), result.getTotalElements(),
                safePage, safePageSize);
    }

    @Transactional(readOnly = true)
    public InsuranceDtos.ReconciliationView detail(UUID reconciliationId) {
        return toViews(List.of(requireReconciliation(reconciliationId))).get(0);
    }

    /**
     * 生成对账：同公司同账期已存在则重算更新（Upsert），不重复产生批次。
     */
    @Transactional
    public InsuranceDtos.ReconciliationView generate(InsuranceDtos.ReconciliationCreateRequest request) {
        String period = request.period() == null ? null : request.period().trim();
        if (period == null || !PERIOD_PATTERN.matcher(period).matches()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "对账账期格式必须为 YYYY-MM");
        }
        InsuranceCompany company = companyRepository.findById(request.companyId())
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "保险公司不存在"));

        YearMonth yearMonth = YearMonth.parse(period);
        Instant from = yearMonth.atDay(1).atStartOfDay(ZoneId.systemDefault()).toInstant();
        Instant to = yearMonth.plusMonths(1).atDay(1).atStartOfDay(ZoneId.systemDefault()).toInstant();

        long caseCount = caseRepository
                .countByCompanyIdAndDecidedAtGreaterThanEqualAndDecidedAtLessThan(
                        company.getId(), from, to);
        BigDecimal receivable = zeroIfNull(caseRepository.sumReceivable(company.getId(), from, to));
        BigDecimal received = zeroIfNull(caseRepository.sumReceived(company.getId(), from, to));
        BigDecimal difference = received.subtract(receivable);

        InsuranceReconciliation reconciliation = reconciliationRepository
                .findByCompanyIdAndPeriod(company.getId(), period)
                .orElseGet(InsuranceReconciliation::new);
        reconciliation.setCompanyId(company.getId());
        reconciliation.setPeriod(period);
        reconciliation.setCaseCount((int) caseCount);
        reconciliation.setReceivableAmount(receivable);
        reconciliation.setReceivedAmount(received);
        reconciliation.setDifferenceAmount(difference);
        reconciliation.setStatus(difference.abs().compareTo(new BigDecimal("0.01")) < 0
                ? "matched" : "mismatch");
        if (request.remark() != null && !request.remark().isBlank()) {
            reconciliation.setRemark(request.remark().trim());
        }
        InsuranceReconciliation saved = reconciliationRepository.save(reconciliation);

        Map<String, Object> audit = new LinkedHashMap<>();
        audit.put("companyId", company.getId().toString());
        audit.put("period", period);
        audit.put("caseCount", saved.getCaseCount());
        audit.put("differenceAmount", saved.getDifferenceAmount());
        audit.put("status", saved.getStatus());
        auditService.record("INSURANCE_RECONCILE_GENERATE", RESOURCE, saved.getId().toString(), audit);
        return toViews(List.of(saved)).get(0);
    }

    @Transactional
    public InsuranceDtos.ReconciliationView confirm(UUID reconciliationId) {
        InsuranceReconciliation reconciliation = requireReconciliation(reconciliationId);
        if (reconciliation.getConfirmedAt() != null) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_FAILED, "该账期对账已确认，不能重复确认");
        }
        UUID operator = currentUserId();
        Instant now = Instant.now();
        reconciliationRepository.confirm(reconciliationId, operator, now);
        InsuranceReconciliation refreshed = requireReconciliation(reconciliationId);

        Map<String, Object> audit = new LinkedHashMap<>();
        audit.put("period", refreshed.getPeriod());
        audit.put("companyId", refreshed.getCompanyId().toString());
        audit.put("status", refreshed.getStatus());
        auditService.record("INSURANCE_RECONCILE_CONFIRM", RESOURCE, reconciliationId.toString(), audit);
        return toViews(List.of(refreshed)).get(0);
    }

    // ------------------------------------------------------------------
    // 内部方法
    // ------------------------------------------------------------------
    private InsuranceReconciliation requireReconciliation(UUID reconciliationId) {
        return reconciliationRepository.findById(reconciliationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "对账记录不存在"));
    }

    private Specification<InsuranceReconciliation> buildSpecification(UUID companyId, String period,
                                                                      String status) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (companyId != null) {
                predicates.add(cb.equal(root.get("companyId"), companyId));
            }
            if (period != null && !period.isBlank()) {
                predicates.add(cb.equal(root.get("period"), period.trim()));
            }
            if (status != null && !status.isBlank()) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private List<InsuranceDtos.ReconciliationView> toViews(List<InsuranceReconciliation> reconciliations) {
        if (reconciliations.isEmpty()) {
            return List.of();
        }
        Set<UUID> companyIds = reconciliations.stream().map(InsuranceReconciliation::getCompanyId)
                .filter(java.util.Objects::nonNull).collect(Collectors.toCollection(HashSet::new));
        Set<UUID> userIds = reconciliations.stream().map(InsuranceReconciliation::getConfirmedBy)
                .filter(java.util.Objects::nonNull).collect(Collectors.toCollection(HashSet::new));
        Map<UUID, String> companyNames = companyIds.isEmpty() ? Map.of()
                : companyRepository.findAllById(companyIds).stream()
                        .collect(Collectors.toMap(InsuranceCompany::getId, InsuranceCompany::getCompanyName));
        Map<UUID, String> userNames = new LinkedHashMap<>();
        if (!userIds.isEmpty()) {
            List<SysUser> users = userRepository.findAllById(userIds);
            if (users != null) {
                users.forEach(user -> userNames.put(user.getId(),
                        user.getDisplayName() == null || user.getDisplayName().isBlank()
                                ? user.getUsername() : user.getDisplayName()));
            }
        }
        return reconciliations.stream().map(reconciliation -> new InsuranceDtos.ReconciliationView(
                reconciliation.getId(),
                reconciliation.getCompanyId(),
                companyNames.get(reconciliation.getCompanyId()),
                reconciliation.getPeriod(),
                reconciliation.getCaseCount(),
                reconciliation.getReceivableAmount(),
                reconciliation.getReceivedAmount(),
                reconciliation.getDifferenceAmount(),
                reconciliation.getStatus(),
                reconciliation.getConfirmedBy(),
                reconciliation.getConfirmedBy() == null
                        ? null : userNames.get(reconciliation.getConfirmedBy()),
                reconciliation.getConfirmedAt(),
                reconciliation.getRemark(),
                reconciliation.getCreatedAt(),
                reconciliation.getUpdatedAt())).toList();
    }

    private BigDecimal zeroIfNull(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private UUID currentUserId() {
        try {
            return CurrentUser.id();
        } catch (RuntimeException ex) {
            return null;
        }
    }
}
