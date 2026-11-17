package com.hospital.crm.order.application;

import com.hospital.crm.common.api.ErrorCode;
import com.hospital.crm.common.api.PageResponse;
import com.hospital.crm.common.exception.BusinessException;
import com.hospital.crm.order.api.OrderDtos;
import com.hospital.crm.order.application.gateway.ChannelStatementProvider;
import com.hospital.crm.order.domain.Payment;
import com.hospital.crm.order.domain.PaymentRepository;
import com.hospital.crm.order.domain.ReconciliationBatch;
import com.hospital.crm.order.domain.ReconciliationBatchRepository;
import com.hospital.crm.order.domain.ReconciliationDiff;
import com.hospital.crm.order.domain.ReconciliationDiffRepository;
import com.hospital.crm.system.audit.AuditService;
import com.hospital.crm.system.security.CurrentUser;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 渠道对账服务：按渠道与账期比对本地成功支付与渠道账单，
 * 本地有渠道无 LOCAL_ONLY、渠道有本地无 CHANNEL_ONLY、金额不等 AMOUNT_MISMATCH；
 * 全部一致批次 MATCHED，否则 DIFF。资金数据全部来自 CRM 主库，不读 HIS。
 */
@Service
public class ReconciliationService {

    private static final String RESOURCE = "CRM_RECONCILIATION";

    private final PaymentRepository paymentRepository;
    private final ReconciliationBatchRepository batchRepository;
    private final ReconciliationDiffRepository diffRepository;
    private final ChannelStatementProvider statementProvider;
    private final AuditService auditService;

    public ReconciliationService(PaymentRepository paymentRepository,
                                 ReconciliationBatchRepository batchRepository,
                                 ReconciliationDiffRepository diffRepository,
                                 ChannelStatementProvider statementProvider,
                                 AuditService auditService) {
        this.paymentRepository = paymentRepository;
        this.batchRepository = batchRepository;
        this.diffRepository = diffRepository;
        this.statementProvider = statementProvider;
        this.auditService = auditService;
    }

    @Transactional
    public OrderDtos.ReconciliationDetail run(OrderDtos.ReconRunRequest request) {
        String channel = request.channel().trim().toLowerCase();
        if (!PaymentService.CHANNELS.contains(channel)) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_FAILED, "对账渠道取值不合法");
        }
        LocalDate date = request.reconcileDate();
        List<Payment> locals = paymentRepository.findSuccessInPeriod(channel,
                date.atStartOfDay(ZoneOffset.UTC).toInstant(),
                date.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant());
        List<ChannelStatementProvider.ChannelStatementEntry> statements =
                statementProvider.fetch(channel, date);

        Map<String, Payment> localByTradeNo = new LinkedHashMap<>();
        for (Payment payment : locals) {
            if (payment.getChannelTradeNo() != null) {
                localByTradeNo.put(payment.getChannelTradeNo(), payment);
            }
        }
        Set<String> matchedTradeNos = new HashSet<>();
        List<ReconciliationDiff> diffs = new ArrayList<>();

        for (ChannelStatementProvider.ChannelStatementEntry entry : statements) {
            Payment local = entry.channelTradeNo() == null ? null : localByTradeNo.get(entry.channelTradeNo());
            if (local == null) {
                diffs.add(buildDiff("CHANNEL_ONLY", entry.paymentNo(), entry.channelTradeNo(),
                        null, entry.amount()));
            } else {
                matchedTradeNos.add(entry.channelTradeNo());
                if (local.getAmount().compareTo(entry.amount()) != 0) {
                    diffs.add(buildDiff("AMOUNT_MISMATCH", local.getPaymentNo(),
                            entry.channelTradeNo(), local.getAmount(), entry.amount()));
                }
            }
        }
        for (Payment payment : locals) {
            if (payment.getChannelTradeNo() == null || !matchedTradeNos.contains(payment.getChannelTradeNo())) {
                diffs.add(buildDiff("LOCAL_ONLY", payment.getPaymentNo(),
                        payment.getChannelTradeNo(), payment.getAmount(), null));
            }
        }

        ReconciliationBatch batch = new ReconciliationBatch();
        batch.setBatchNo("RC" + date.toString().replace("-", "") + batchRepository.nextBatchNo());
        batch.setChannel(channel);
        batch.setReconcileDate(date);
        batch.setLocalCount(locals.size());
        batch.setChannelCount(statements.size());
        batch.setLocalAmount(locals.stream().map(Payment::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
        batch.setChannelAmount(statements.stream()
                .map(entry -> entry.amount() == null ? BigDecimal.ZERO : entry.amount())
                .reduce(BigDecimal.ZERO, BigDecimal::add));
        batch.setDiffCount(diffs.size());
        batch.setStatus(diffs.isEmpty() ? "MATCHED" : "DIFF");
        batch.setRunBy(CurrentUser.principal().username());
        batch = batchRepository.save(batch);

        for (ReconciliationDiff diff : diffs) {
            diff.setBatchId(batch.getId());
        }
        diffRepository.saveAll(diffs);

        Map<String, Object> audit = new LinkedHashMap<>();
        audit.put("batchNo", batch.getBatchNo());
        audit.put("channel", channel);
        audit.put("reconcileDate", date.toString());
        audit.put("diffCount", batch.getDiffCount());
        auditService.record("RECON_RUN", RESOURCE, batch.getId().toString(), audit);

        return new OrderDtos.ReconciliationDetail(toView(batch), diffs.stream()
                .map(ReconciliationService::toDiffView).toList());
    }

    @Transactional(readOnly = true)
    public PageResponse<OrderDtos.ReconciliationView> list(String channel, String status,
                                                            int page, int pageSize) {
        int safePage = Math.max(page, 1);
        int safePageSize = Math.min(Math.max(pageSize, 1), 100);
        Pageable pageable = PageRequest.of(safePage - 1, safePageSize,
                Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<ReconciliationBatch> result = batchRepository.findAll(
                buildSpecification(channel, status), pageable);
        List<OrderDtos.ReconciliationView> items = result.getContent().stream()
                .map(ReconciliationService::toView).toList();
        return PageResponse.of(items, result.getTotalElements(), safePage, safePageSize);
    }

    @Transactional(readOnly = true)
    public OrderDtos.ReconciliationDetail detail(UUID batchId) {
        ReconciliationBatch batch = batchRepository.findById(batchId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "对账批次不存在"));
        List<OrderDtos.ReconciliationDiffView> diffs = diffRepository.findByBatchIdOrderByCreatedAtAsc(batchId)
                .stream().map(ReconciliationService::toDiffView).toList();
        return new OrderDtos.ReconciliationDetail(toView(batch), diffs);
    }

    private ReconciliationDiff buildDiff(String diffType, String paymentNo,
                                         String channelTradeNo, BigDecimal localAmount, BigDecimal channelAmount) {
        ReconciliationDiff entity = new ReconciliationDiff();
        entity.setDiffType(diffType);
        entity.setPaymentNo(paymentNo);
        entity.setChannelTradeNo(channelTradeNo);
        entity.setLocalAmount(localAmount);
        entity.setChannelAmount(channelAmount);
        entity.setHandled(false);
        return entity;
    }

    private static OrderDtos.ReconciliationView toView(ReconciliationBatch batch) {
        return new OrderDtos.ReconciliationView(
                batch.getId(),
                batch.getBatchNo(),
                batch.getChannel(),
                batch.getReconcileDate(),
                batch.getLocalCount(),
                batch.getChannelCount(),
                batch.getLocalAmount(),
                batch.getChannelAmount(),
                batch.getDiffCount(),
                batch.getStatus(),
                batch.getRunBy(),
                batch.getCreatedAt());
    }

    private static OrderDtos.ReconciliationDiffView toDiffView(ReconciliationDiff diff) {
        return new OrderDtos.ReconciliationDiffView(
                diff.getId(),
                diff.getDiffType(),
                diff.getPaymentNo(),
                diff.getChannelTradeNo(),
                diff.getLocalAmount(),
                diff.getChannelAmount(),
                diff.isHandled());
    }

    private Specification<ReconciliationBatch> buildSpecification(String channel, String status) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (channel != null && !channel.isBlank()) {
                predicates.add(cb.equal(root.get("channel"), channel.trim()));
            }
            if (status != null && !status.isBlank()) {
                predicates.add(cb.equal(root.get("status"), status.trim()));
            }
            return predicates.isEmpty() ? cb.conjunction()
                    : cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
