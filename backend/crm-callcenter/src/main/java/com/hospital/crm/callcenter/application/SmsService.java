package com.hospital.crm.callcenter.application;

import com.hospital.crm.callcenter.adapter.SmsProvider;
import com.hospital.crm.callcenter.api.CallCenterDtos;
import com.hospital.crm.callcenter.domain.MessageOutbox;
import com.hospital.crm.callcenter.domain.MessageOutboxRepository;
import com.hospital.crm.callcenter.domain.SmsTemplate;
import com.hospital.crm.callcenter.domain.SmsTemplateRepository;
import com.hospital.crm.common.api.ErrorCode;
import com.hospital.crm.common.api.PageResponse;
import com.hospital.crm.common.exception.BusinessException;
import com.hospital.crm.customer.api.CustomerDtos;
import com.hospital.crm.customer.application.CustomerService;
import com.hospital.crm.customer.crypto.SensitiveDataHasher;
import com.hospital.crm.customer.support.CustomerMasking;
import com.hospital.crm.system.audit.AuditService;
import jakarta.persistence.criteria.Predicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 短信模板与消息发送队列服务。
 *
 * 发送走 Outbox 模式：入队即返回，由调度任务扫描到期消息统一投递；
 * 收件号码仅保存 HMAC 哈希与脱敏值，失败按 300×2^(n-1) 秒指数退避，
 * 超过 max_retries 停止自动重试（next_retry_at 置空），仍可人工重试。
 */
@Service
public class SmsService {

    private static final String MESSAGE_RESOURCE = "MESSAGE_OUTBOX";
    private static final String TEMPLATE_RESOURCE = "SMS_TEMPLATE";
    private static final Pattern VARIABLE_PATTERN = Pattern.compile("\\{\\{(\\w+)\\}\\}");
    private static final int BATCH_LIMIT = 50;
    private static final long BACKOFF_BASE_SECONDS = 300;
    private static final Set<String> CHANNELS = Set.of("sms", "wechat", "voice");

    private static final Logger log = LoggerFactory.getLogger(SmsService.class);

    private final MessageOutboxRepository messageRepository;
    private final SmsTemplateRepository templateRepository;
    private final CustomerService customerService;
    private final SensitiveDataHasher hasher;
    private final SmsProvider smsProvider;
    private final AuditService auditService;
    private final String providerName;

    public SmsService(MessageOutboxRepository messageRepository,
                      SmsTemplateRepository templateRepository,
                      CustomerService customerService,
                      SensitiveDataHasher hasher,
                      SmsProvider smsProvider,
                      AuditService auditService,
                      @Value("${crm.sms.provider:simulated}") String providerName) {
        this.messageRepository = messageRepository;
        this.templateRepository = templateRepository;
        this.customerService = customerService;
        this.hasher = hasher;
        this.smsProvider = smsProvider;
        this.auditService = auditService;
        this.providerName = providerName;
    }

    // ---------- 模板 ----------

    @Transactional(readOnly = true)
    public PageResponse<CallCenterDtos.SmsTemplateView> listTemplates(String keyword, String status,
                                                                      int page, int pageSize) {
        int safePage = Math.max(page, 1);
        int safePageSize = Math.min(Math.max(pageSize, 1), 100);
        Pageable pageable = PageRequest.of(safePage - 1, safePageSize,
                Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<SmsTemplate> result = templateRepository.findAll(buildTemplateSpecification(keyword, status), pageable);
        return PageResponse.of(result.getContent().stream().map(this::toTemplateView).toList(),
                result.getTotalElements(), safePage, safePageSize);
    }

    @Transactional(readOnly = true)
    public CallCenterDtos.SmsTemplateView templateDetail(UUID templateId) {
        return toTemplateView(requireTemplate(templateId));
    }

    @Transactional
    public CallCenterDtos.SmsTemplateView createTemplate(CallCenterDtos.TemplateCreateRequest request) {
        String code = request.code().trim();
        if (templateRepository.existsByCode(code)) {
            throw new BusinessException(ErrorCode.BUSINESS_CONFLICT, "模板编码已存在：" + code);
        }
        SmsTemplate template = new SmsTemplate();
        template.setCode(code);
        template.setName(request.name().trim());
        template.setContent(request.content());
        template.setVariables(extractVariables(request.content()));
        template.setStatus("active");
        template.setRemark(blankToNull(request.remark()));
        SmsTemplate saved = templateRepository.save(template);

        Map<String, Object> audit = new LinkedHashMap<>();
        audit.put("code", saved.getCode());
        audit.put("name", saved.getName());
        auditService.record("SMS_TEMPLATE_CREATE", TEMPLATE_RESOURCE, saved.getId().toString(), audit);
        return toTemplateView(saved);
    }

    @Transactional
    public CallCenterDtos.SmsTemplateView updateTemplate(UUID templateId,
                                                         CallCenterDtos.TemplateUpdateRequest request) {
        SmsTemplate template = requireTemplate(templateId);
        if (request.name() != null && !request.name().isBlank()) {
            template.setName(request.name().trim());
        }
        if (request.content() != null && !request.content().isBlank()
                && !request.content().equals(template.getContent())) {
            template.setContent(request.content());
            template.setVariables(extractVariables(request.content()));
        }
        if (request.remark() != null) {
            template.setRemark(blankToNull(request.remark()));
        }
        SmsTemplate saved = templateRepository.save(template);

        Map<String, Object> audit = new LinkedHashMap<>();
        audit.put("code", saved.getCode());
        auditService.record("SMS_TEMPLATE_UPDATE", TEMPLATE_RESOURCE, templateId.toString(), audit);
        return toTemplateView(saved);
    }

    @Transactional
    public CallCenterDtos.SmsTemplateView updateTemplateStatus(UUID templateId, String status) {
        SmsTemplate template = requireTemplate(templateId);
        if (!"active".equals(status) && !"disabled".equals(status)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "状态取值不合法");
        }
        template.setStatus(status);
        SmsTemplate saved = templateRepository.save(template);

        Map<String, Object> audit = new LinkedHashMap<>();
        audit.put("code", saved.getCode());
        audit.put("status", status);
        auditService.record("SMS_TEMPLATE_STATUS", TEMPLATE_RESOURCE, templateId.toString(), audit);
        return toTemplateView(saved);
    }

    // ---------- 消息队列 ----------

    /**
     * 消息入队：模板与自定义内容二选一；biz_key 命中已有消息时直接返回（上游重试幂等）。
     */
    @Transactional
    public CallCenterDtos.MessageView enqueue(CallCenterDtos.MessageSendRequest request) {
        if (!CHANNELS.contains(request.channel())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "发送渠道取值不合法");
        }
        boolean hasTemplate = request.templateCode() != null && !request.templateCode().isBlank();
        boolean hasContent = request.content() != null && !request.content().isBlank();
        if (hasTemplate == hasContent) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "templateCode 与 content 必须二选一");
        }

        String bizKey = blankToNull(request.bizKey());
        if (bizKey != null) {
            var existing = messageRepository.findByBizKey(bizKey);
            if (existing.isPresent()) {
                return buildMessageView(existing.get());
            }
        }

        SmsTemplate template = null;
        String content;
        if (hasTemplate) {
            template = templateRepository.findByCode(request.templateCode().trim())
                    .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "短信模板不存在"));
            if (!"active".equals(template.getStatus())) {
                throw new BusinessException(ErrorCode.BUSINESS_RULE_FAILED, "模板已停用，不能用于发送");
            }
            content = render(template, request.variables());
        } else {
            content = request.content();
        }

        String normalized = CustomerMasking.normalizePhone(request.recipientPhone());
        if (normalized == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "接收号码格式不合法");
        }

        MessageOutbox message = new MessageOutbox();
        message.setMsgNo("MS" + messageRepository.nextMsgNo());
        message.setBizKey(bizKey);
        message.setTemplateId(template == null ? null : template.getId());
        message.setChannel(request.channel());
        message.setScene(blankToNull(request.scene()));
        message.setRecipientHash(hasher.hash(normalized));
        message.setRecipientMasked(CustomerMasking.maskPhone(normalized));
        if (request.customerId() != null) {
            message.setCustomerId(customerService.brief(request.customerId()).id());
        }
        message.setContent(content);
        message.setProvider(providerName);
        message.setStatus("PENDING");
        message.setRetryCount(0);
        message.setMaxRetries(3);
        message.setNextRetryAt(Instant.now());
        MessageOutbox saved = messageRepository.save(message);

        Map<String, Object> audit = new LinkedHashMap<>();
        audit.put("msgNo", saved.getMsgNo());
        audit.put("channel", saved.getChannel());
        audit.put("scene", saved.getScene());
        audit.put("bizKey", saved.getBizKey());
        audit.put("content", truncate(saved.getContent(), 60));
        auditService.record("MESSAGE_ENQUEUE", MESSAGE_RESOURCE, saved.getId().toString(), audit);

        return buildMessageView(saved);
    }

    @Transactional(readOnly = true)
    public PageResponse<CallCenterDtos.MessageView> listMessages(String status, String channel, String scene,
                                                                 String bizKey, String keyword,
                                                                 int page, int pageSize) {
        int safePage = Math.max(page, 1);
        int safePageSize = Math.min(Math.max(pageSize, 1), 100);
        Pageable pageable = PageRequest.of(safePage - 1, safePageSize,
                Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<MessageOutbox> result = messageRepository.findAll(
                buildMessageSpecification(status, channel, scene, bizKey, keyword), pageable);
        return PageResponse.of(toMessageViews(result.getContent()),
                result.getTotalElements(), safePage, safePageSize);
    }

    /**
     * 人工重试：不受 max_retries 限制，立即尝试发送一次。
     */
    @Transactional
    public CallCenterDtos.MessageView retry(UUID messageId) {
        MessageOutbox message = requireMessage(messageId);
        if (!"PENDING".equals(message.getStatus()) && !"FAILED".equals(message.getStatus())) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_FAILED,
                    "仅待发送或发送失败的消息可以重试，当前状态：" + message.getStatus());
        }
        Instant now = Instant.now();
        int claimed = messageRepository.markSendingManual(messageId, now);
        if (claimed == 0) {
            throw new BusinessException(ErrorCode.BUSINESS_CONFLICT, "消息状态已变化，请刷新后重试");
        }
        boolean success = attempt(message, now);

        Map<String, Object> audit = new LinkedHashMap<>();
        audit.put("msgNo", message.getMsgNo());
        audit.put("result", success ? "SENT" : "FAILED");
        auditService.record("MESSAGE_RETRY", MESSAGE_RESOURCE, messageId.toString(), audit);

        return buildMessageView(requireMessage(messageId));
    }

    /**
     * 手工触发一次到期调度，语义与定时任务一致。
     */
    @Transactional
    public CallCenterDtos.DispatchResult dispatch() {
        return dispatchDue();
    }

    /**
     * 扫描并发送到期消息；条件抢占保证并发实例只有一个发送成功，单条失败不影响其余。
     */
    @Transactional
    public CallCenterDtos.DispatchResult dispatchDue() {
        Instant now = Instant.now();
        List<MessageOutbox> due = messageRepository.findDue(now, PageRequest.of(0, BATCH_LIMIT));
        int sent = 0;
        int failed = 0;
        for (MessageOutbox message : due) {
            if (messageRepository.markSending(message.getId(), now) == 0) {
                continue;
            }
            if (attempt(message, now)) {
                sent++;
            } else {
                failed++;
            }
        }
        if (!due.isEmpty()) {
            Map<String, Object> audit = new LinkedHashMap<>();
            audit.put("scanned", due.size());
            audit.put("sent", sent);
            audit.put("failed", failed);
            auditService.record("MESSAGE_DISPATCH", MESSAGE_RESOURCE, "batch", audit);
        }
        return new CallCenterDtos.DispatchResult(due.size(), sent, failed, now);
    }

    // ---------- 私有方法 ----------

    /** 发送一次并更新状态；返回是否成功。 */
    private boolean attempt(MessageOutbox message, Instant now) {
        try {
            smsProvider.send(message.getRecipientMasked(), message.getContent(), message);
            messageRepository.markSent(message.getId(), now);
            return true;
        } catch (Exception ex) {
            log.warn("消息发送失败 msgNo={} retryCount={}: {}",
                    message.getMsgNo(), message.getRetryCount(), ex.getMessage());
            int nextRetryCount = message.getRetryCount() + 1;
            Instant nextRetryAt = nextRetryCount >= message.getMaxRetries()
                    ? null
                    : now.plusSeconds(BACKOFF_BASE_SECONDS * (1L << (nextRetryCount - 1)));
            messageRepository.markFailed(message.getId(), now,
                    truncate(ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage(), 500),
                    nextRetryAt);
            return false;
        }
    }

    /** 渲染模板：缺变量直接拒绝并列出缺失变量名。 */
    private String render(SmsTemplate template, Map<String, String> variables) {
        Map<String, String> values = variables == null ? Map.of() : variables;
        List<String> missing = template.getVariables().stream()
                .filter(name -> values.get(name) == null || values.get(name).isBlank())
                .toList();
        if (!missing.isEmpty()) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_FAILED, "模板变量缺失：" + String.join("、", missing));
        }
        String content = template.getContent();
        for (String name : template.getVariables()) {
            content = content.replace("{{" + name + "}}", values.get(name));
        }
        return content;
    }

    /** 提取 {{var}} 占位符变量名，去重保序。 */
    static List<String> extractVariables(String content) {
        Set<String> names = new LinkedHashSet<>();
        Matcher matcher = VARIABLE_PATTERN.matcher(content);
        while (matcher.find()) {
            names.add(matcher.group(1));
        }
        return new ArrayList<>(names);
    }

    private MessageOutbox requireMessage(UUID messageId) {
        return messageRepository.findById(messageId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "消息不存在"));
    }

    private SmsTemplate requireTemplate(UUID templateId) {
        return templateRepository.findById(templateId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "短信模板不存在"));
    }

    private Specification<SmsTemplate> buildTemplateSpecification(String keyword, String status) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (keyword != null && !keyword.isBlank()) {
                String like = "%" + keyword.trim().toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("code")), like),
                        cb.like(cb.lower(root.get("name")), like)));
            }
            if (status != null && !status.isBlank()) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private Specification<MessageOutbox> buildMessageSpecification(String status, String channel, String scene,
                                                                   String bizKey, String keyword) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (status != null && !status.isBlank()) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (channel != null && !channel.isBlank()) {
                predicates.add(cb.equal(root.get("channel"), channel));
            }
            if (scene != null && !scene.isBlank()) {
                predicates.add(cb.equal(root.get("scene"), scene));
            }
            if (bizKey != null && !bizKey.isBlank()) {
                predicates.add(cb.equal(root.get("bizKey"), bizKey));
            }
            if (keyword != null && !keyword.isBlank()) {
                String like = "%" + keyword.trim().toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("msgNo")), like),
                        cb.like(cb.lower(root.get("content")), like)));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private CallCenterDtos.MessageView buildMessageView(MessageOutbox message) {
        return toMessageViews(List.of(message)).get(0);
    }

    private List<CallCenterDtos.MessageView> toMessageViews(List<MessageOutbox> messages) {
        Set<UUID> customerIds = messages.stream()
                .map(MessageOutbox::getCustomerId).filter(java.util.Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Set<UUID> templateIds = messages.stream()
                .map(MessageOutbox::getTemplateId).filter(java.util.Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<UUID, CustomerDtos.CustomerBrief> customers = customerIds.isEmpty()
                ? Map.of() : customerService.briefs(customerIds);
        Map<UUID, SmsTemplate> templates = templateIds.isEmpty()
                ? Map.of() : templateRepository.findAllById(templateIds).stream()
                        .collect(Collectors.toMap(SmsTemplate::getId, t -> t));

        return messages.stream().map(message -> {
            CustomerDtos.CustomerBrief brief = message.getCustomerId() == null
                    ? null : customers.get(message.getCustomerId());
            SmsTemplate template = message.getTemplateId() == null
                    ? null : templates.get(message.getTemplateId());
            return new CallCenterDtos.MessageView(
                    message.getId(),
                    message.getMsgNo(),
                    message.getBizKey(),
                    template == null ? null : template.getCode(),
                    template == null ? null : template.getName(),
                    message.getChannel(),
                    message.getScene(),
                    message.getRecipientMasked(),
                    message.getCustomerId(),
                    brief == null ? null : brief.name(),
                    message.getContent(),
                    message.getProvider(),
                    message.getStatus(),
                    message.getRetryCount(),
                    message.getMaxRetries(),
                    message.getNextRetryAt(),
                    message.getLastError(),
                    message.getSentAt(),
                    message.getCreatedAt());
        }).toList();
    }

    private CallCenterDtos.SmsTemplateView toTemplateView(SmsTemplate template) {
        return new CallCenterDtos.SmsTemplateView(
                template.getId(),
                template.getCode(),
                template.getName(),
                template.getChannel(),
                template.getContent(),
                template.getVariables(),
                template.getStatus(),
                template.getRemark(),
                template.getCreatedAt(),
                template.getUpdatedAt());
    }

    private String truncate(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
