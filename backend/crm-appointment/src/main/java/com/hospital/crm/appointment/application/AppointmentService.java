package com.hospital.crm.appointment.application;

import com.hospital.crm.appointment.api.AppointmentDtos;
import com.hospital.crm.appointment.domain.Appointment;
import com.hospital.crm.appointment.domain.AppointmentReminder;
import com.hospital.crm.appointment.domain.AppointmentRepository;
import com.hospital.crm.appointment.domain.AppointmentType;
import com.hospital.crm.appointment.domain.AppointmentTypeRepository;
import com.hospital.crm.appointment.domain.NoShowRecord;
import com.hospital.crm.appointment.domain.NoShowRecordRepository;
import com.hospital.crm.appointment.domain.ServiceSlot;
import com.hospital.crm.appointment.domain.ServiceSlotRepository;
import com.hospital.crm.common.api.ErrorCode;
import com.hospital.crm.common.api.PageResponse;
import com.hospital.crm.common.exception.BusinessException;
import com.hospital.crm.customer.api.CustomerDtos;
import com.hospital.crm.customer.application.CustomerService;
import com.hospital.crm.customer.crypto.SensitiveDataCipher;
import com.hospital.crm.customer.crypto.SensitiveDataHasher;
import com.hospital.crm.customer.support.CustomerMasking;
import com.hospital.crm.system.audit.AuditService;
import com.hospital.crm.system.security.CurrentUser;
import com.hospital.crm.system.user.SysUserRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 预约服务：创建/改期通过时段条件更新占用名额，并发下不超卖；
 * 取消与改期按预约类型的提前小时数校验；爽约生成记录并保留容量占用。
 */
@Service
public class AppointmentService {

    private static final String RESOURCE = "APPOINTMENT";
    private static final Set<String> ACTIVE_STATUSES = Set.of("BOOKED", "CONFIRMED");

    private static final Map<String, Set<String>> STATUS_TRANSITIONS = Map.of(
            "BOOKED", Set.of("CONFIRMED", "COMPLETED", "CANCELLED", "NO_SHOW"),
            "CONFIRMED", Set.of("COMPLETED", "CANCELLED", "NO_SHOW")
    );

    private final AppointmentRepository appointmentRepository;
    private final ServiceSlotRepository slotRepository;
    private final AppointmentTypeRepository typeRepository;
    private final NoShowRecordRepository noShowRepository;
    private final ServiceSlotService slotService;
    private final AppointmentTypeService typeService;
    private final ReminderService reminderService;
    private final CustomerService customerService;
    private final SysUserRepository userRepository;
    private final SensitiveDataCipher cipher;
    private final SensitiveDataHasher hasher;
    private final AuditService auditService;

    public AppointmentService(AppointmentRepository appointmentRepository,
                              ServiceSlotRepository slotRepository,
                              AppointmentTypeRepository typeRepository,
                              NoShowRecordRepository noShowRepository,
                              ServiceSlotService slotService,
                              AppointmentTypeService typeService,
                              ReminderService reminderService,
                              CustomerService customerService,
                              SysUserRepository userRepository,
                              SensitiveDataCipher cipher,
                              SensitiveDataHasher hasher,
                              AuditService auditService) {
        this.appointmentRepository = appointmentRepository;
        this.slotRepository = slotRepository;
        this.typeRepository = typeRepository;
        this.noShowRepository = noShowRepository;
        this.slotService = slotService;
        this.typeService = typeService;
        this.reminderService = reminderService;
        this.customerService = customerService;
        this.userRepository = userRepository;
        this.cipher = cipher;
        this.hasher = hasher;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public PageResponse<AppointmentDtos.AppointmentSummary> list(String keyword,
                                                                 String status,
                                                                 String typeCode,
                                                                 UUID customerId,
                                                                 LocalDate dateFrom,
                                                                 LocalDate dateTo,
                                                                 int page,
                                                                 int pageSize) {
        List<UUID> slotIds = null;
        if (dateFrom != null || dateTo != null) {
            slotIds = slotRepository.findAll(dateRangeSpecification(dateFrom, dateTo)).stream()
                    .map(ServiceSlot::getId).toList();
            if (slotIds.isEmpty()) {
                return PageResponse.of(List.of(), 0, Math.max(page, 1), Math.min(Math.max(pageSize, 1), 100));
            }
        }
        int safePage = Math.max(page, 1);
        int safePageSize = Math.min(Math.max(pageSize, 1), 100);
        Pageable pageable = PageRequest.of(safePage - 1, safePageSize,
                Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Appointment> result = appointmentRepository.findAll(
                buildSpecification(keyword, status, typeCode, customerId, slotIds), pageable);

        Map<UUID, ServiceSlot> slots = loadSlots(result.getContent());
        Map<UUID, CustomerDtos.CustomerBrief> customers = customerService.briefs(
                result.getContent().stream().map(Appointment::getCustomerId).collect(Collectors.toSet()));
        Map<String, String> typeNames = typeNames();
        List<AppointmentDtos.AppointmentSummary> items = result.getContent().stream()
                .map(appointment -> toSummary(appointment, slots.get(appointment.getSlotId()),
                        typeNames, customers.get(appointment.getCustomerId())))
                .toList();
        return PageResponse.of(items, result.getTotalElements(), safePage, safePageSize);
    }

    @Transactional(readOnly = true)
    public AppointmentDtos.AppointmentDetail detail(UUID appointmentId) {
        Appointment appointment = load(appointmentId);
        ServiceSlot slot = slotService.load(appointment.getSlotId());
        CustomerDtos.CustomerBrief customer = customerService.brief(appointment.getCustomerId());
        Map<String, String> typeNames = typeNames();
        List<AppointmentDtos.ReminderView> reminders = reminderService.listForAppointment(appointmentId).stream()
                .map(reminder -> reminderService.toView(reminder, appointment.getAppointmentNo()))
                .toList();
        return new AppointmentDtos.AppointmentDetail(
                toSummary(appointment, slot, typeNames, customer),
                appointment.getRemark(),
                appointment.getCancelledAt(),
                appointment.getCancelReason(),
                appointment.getCompletedAt(),
                appointment.getNoShowAt(),
                noShowRepository.countByCustomerId(appointment.getCustomerId()),
                reminders);
    }

    @Transactional
    public AppointmentDtos.AppointmentSummary create(AppointmentDtos.AppointmentCreateRequest request) {
        AppointmentType type = typeService.load(request.typeCode());
        if (!"active".equals(type.getStatus())) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_FAILED,
                    "预约类型已停用：" + type.getTypeName());
        }
        ServiceSlot slot = slotService.load(request.slotId());
        validateSlotForType(slot, type.getTypeCode());
        Instant startAt = ServiceSlotService.slotStartAt(slot);
        if (startAt.isBefore(Instant.now())) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_FAILED, "不能预约已开始的时段");
        }
        CustomerDtos.CustomerBrief customer = customerService.brief(request.customerId());
        if (appointmentRepository.existsByCustomerIdAndSlotIdAndStatusIn(
                request.customerId(), slot.getId(), ACTIVE_STATUSES)) {
            throw new BusinessException(ErrorCode.BUSINESS_CONFLICT, "该客户在此时段已有预约，请勿重复登记");
        }
        if (slotRepository.occupy(slot.getId(), Instant.now()) == 0) {
            throw new BusinessException(ErrorCode.BUSINESS_CONFLICT,
                    "该服务时段名额已满或已关闭，请选择其他时段");
        }

        Appointment appointment = new Appointment();
        appointment.setAppointmentNo("A" + appointmentRepository.nextAppointmentNo());
        appointment.setCustomerId(request.customerId());
        appointment.setTypeCode(type.getTypeCode());
        appointment.setSlotId(slot.getId());
        appointment.setStatus("BOOKED");
        applyContactPhone(appointment, request.contactPhone());
        appointment.setRemark(trimToNull(request.remark()));
        appointment.setOrgId(CurrentUser.principal().orgId());
        appointment.setCreatedBy(CurrentUser.id());
        appointment.setUpdatedBy(CurrentUser.id());
        appointment = appointmentRepository.save(appointment);
        reminderService.scheduleFor(appointment, startAt);

        Map<String, Object> audit = new LinkedHashMap<>();
        audit.put("appointmentNo", appointment.getAppointmentNo());
        audit.put("customerNo", customer.customerNo());
        audit.put("typeCode", type.getTypeCode());
        audit.put("slotDate", String.valueOf(slot.getSlotDate()));
        audit.put("startTime", String.valueOf(slot.getStartTime()));
        auditService.record("APPT_CREATE", RESOURCE, appointment.getId().toString(), audit);

        return toSummary(appointment, slot, typeNames(), customer);
    }

    @Transactional
    public AppointmentDtos.AppointmentSummary reschedule(UUID appointmentId,
                                                         AppointmentDtos.RescheduleRequest request) {
        Appointment appointment = load(appointmentId);
        requireActive(appointment, "改期");
        ServiceSlot oldSlot = slotService.load(appointment.getSlotId());
        AppointmentType type = typeService.load(appointment.getTypeCode());
        checkCutoff(type, oldSlot, "改期");

        ServiceSlot newSlot = slotService.load(request.slotId());
        if (newSlot.getId().equals(oldSlot.getId())) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_FAILED, "目标时段与当前时段相同");
        }
        validateSlotForType(newSlot, type.getTypeCode());
        Instant newStartAt = ServiceSlotService.slotStartAt(newSlot);
        if (newStartAt.isBefore(Instant.now())) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_FAILED, "不能改期到已开始的时段");
        }
        if (appointmentRepository.existsByCustomerIdAndSlotIdAndStatusIn(
                appointment.getCustomerId(), newSlot.getId(), ACTIVE_STATUSES)) {
            throw new BusinessException(ErrorCode.BUSINESS_CONFLICT, "该客户在目标时段已有预约");
        }
        if (slotRepository.occupy(newSlot.getId(), Instant.now()) == 0) {
            throw new BusinessException(ErrorCode.BUSINESS_CONFLICT,
                    "目标时段名额已满或已关闭，请选择其他时段");
        }
        slotRepository.release(oldSlot.getId(), Instant.now());

        appointment.setSlotId(newSlot.getId());
        appointment.setUpdatedBy(CurrentUser.id());
        if (trimToNull(request.remark()) != null) {
            appointment.setRemark(trimToNull(request.remark()));
        }
        appointment = appointmentRepository.save(appointment);
        reminderService.reschedulePlan(appointment.getId(), newStartAt);

        Map<String, Object> audit = new LinkedHashMap<>();
        audit.put("appointmentNo", appointment.getAppointmentNo());
        audit.put("fromSlot", oldSlot.getSlotDate() + " " + oldSlot.getStartTime());
        audit.put("toSlot", newSlot.getSlotDate() + " " + newSlot.getStartTime());
        auditService.record("APPT_RESCHEDULE", RESOURCE, appointmentId.toString(), audit);

        return toSummary(appointment, newSlot, typeNames(),
                customerService.brief(appointment.getCustomerId()));
    }

    @Transactional
    public AppointmentDtos.AppointmentSummary cancel(UUID appointmentId, AppointmentDtos.CancelRequest request) {
        Appointment appointment = load(appointmentId);
        requireActive(appointment, "取消");
        ServiceSlot slot = slotService.load(appointment.getSlotId());
        AppointmentType type = typeService.load(appointment.getTypeCode());
        checkCutoff(type, slot, "取消");

        appointment.setStatus("CANCELLED");
        appointment.setCancelledAt(Instant.now());
        appointment.setCancelReason(trimToNull(request.reason()));
        appointment.setUpdatedBy(CurrentUser.id());
        appointment = appointmentRepository.save(appointment);
        slotRepository.release(slot.getId(), Instant.now());
        reminderService.cancelPending(appointmentId);

        Map<String, Object> audit = new LinkedHashMap<>();
        audit.put("appointmentNo", appointment.getAppointmentNo());
        audit.put("reason", appointment.getCancelReason());
        auditService.record("APPT_CANCEL", RESOURCE, appointmentId.toString(), audit);

        return toSummary(appointment, slot, typeNames(),
                customerService.brief(appointment.getCustomerId()));
    }

    @Transactional
    public AppointmentDtos.AppointmentSummary changeStatus(UUID appointmentId,
                                                           AppointmentDtos.StatusChangeRequest request) {
        Appointment appointment = load(appointmentId);
        String target = request.status();
        Set<String> allowed = STATUS_TRANSITIONS.getOrDefault(appointment.getStatus(), Set.of());
        if (!allowed.contains(target)) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_FAILED,
                    "预约状态不能从 " + appointment.getStatus() + " 变更为 " + target);
        }
        ServiceSlot slot = slotService.load(appointment.getSlotId());
        Instant startAt = ServiceSlotService.slotStartAt(slot);
        Instant now = Instant.now();

        switch (target) {
            case "CONFIRMED" -> appointment.setStatus("CONFIRMED");
            case "COMPLETED" -> {
                if (startAt.isAfter(now)) {
                    throw new BusinessException(ErrorCode.BUSINESS_RULE_FAILED, "预约尚未开始，不能标记完成");
                }
                appointment.setStatus("COMPLETED");
                appointment.setCompletedAt(now);
            }
            case "NO_SHOW" -> {
                if (startAt.isAfter(now)) {
                    throw new BusinessException(ErrorCode.BUSINESS_RULE_FAILED, "预约尚未开始，不能标记爽约");
                }
                appointment.setStatus("NO_SHOW");
                appointment.setNoShowAt(now);
                recordNoShow(appointment, startAt, request.reason(), now);
            }
            default -> throw new BusinessException(ErrorCode.VALIDATION_ERROR, "不支持的状态：" + target);
        }
        appointment.setUpdatedBy(CurrentUser.id());
        appointment = appointmentRepository.save(appointment);

        Map<String, Object> audit = new LinkedHashMap<>();
        audit.put("appointmentNo", appointment.getAppointmentNo());
        audit.put("status", target);
        audit.put("reason", trimToNull(request.reason()));
        auditService.record("APPT_STATUS", RESOURCE, appointmentId.toString(), audit);

        return toSummary(appointment, slot, typeNames(),
                customerService.brief(appointment.getCustomerId()));
    }

    @Transactional(readOnly = true)
    public PageResponse<AppointmentDtos.NoShowView> noShows(UUID customerId,
                                                            LocalDate dateFrom,
                                                            LocalDate dateTo,
                                                            int page,
                                                            int pageSize) {
        int safePage = Math.max(page, 1);
        int safePageSize = Math.min(Math.max(pageSize, 1), 100);
        Pageable pageable = PageRequest.of(safePage - 1, safePageSize,
                Sort.by(Sort.Direction.DESC, "appointmentAt"));
        Page<NoShowRecord> result = noShowRepository.findAll(
                buildNoShowSpecification(customerId, dateFrom, dateTo), pageable);

        Map<UUID, CustomerDtos.CustomerBrief> customers = customerService.briefs(
                result.getContent().stream().map(NoShowRecord::getCustomerId).collect(Collectors.toSet()));
        Map<UUID, Appointment> appointments = appointmentRepository.findAllById(
                result.getContent().stream().map(NoShowRecord::getAppointmentId).toList()).stream()
                .collect(Collectors.toMap(Appointment::getId, Function.identity()));
        Map<UUID, String> recorderNames = userNames(result.getContent().stream()
                .map(NoShowRecord::getRecordedBy).filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet()));

        List<AppointmentDtos.NoShowView> items = result.getContent().stream()
                .map(record -> {
                    CustomerDtos.CustomerBrief customer = customers.get(record.getCustomerId());
                    Appointment appointment = appointments.get(record.getAppointmentId());
                    return new AppointmentDtos.NoShowView(
                            record.getId(),
                            record.getAppointmentId(),
                            appointment == null ? null : appointment.getAppointmentNo(),
                            record.getCustomerId(),
                            customer == null ? null : customer.customerNo(),
                            customer == null ? null : customer.name(),
                            record.getAppointmentAt(),
                            record.getReason(),
                            record.getRecordedBy() == null ? null : recorderNames.get(record.getRecordedBy()),
                            record.getCreatedAt());
                })
                .toList();
        return PageResponse.of(items, result.getTotalElements(), safePage, safePageSize);
    }

    @Transactional(readOnly = true)
    public Appointment load(UUID appointmentId) {
        return appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "预约不存在"));
    }

    private void recordNoShow(Appointment appointment, Instant startAt, String reason, Instant now) {
        if (noShowRepository.findByAppointmentId(appointment.getId()).isPresent()) {
            return;
        }
        NoShowRecord record = new NoShowRecord();
        record.setAppointmentId(appointment.getId());
        record.setCustomerId(appointment.getCustomerId());
        record.setAppointmentAt(startAt);
        record.setReason(trimToNull(reason));
        record.setRecordedBy(CurrentUser.id());
        noShowRepository.save(record);
    }

    private void requireActive(Appointment appointment, String operation) {
        if (!ACTIVE_STATUSES.contains(appointment.getStatus())) {
            throw new BusinessException(ErrorCode.BUSINESS_CONFLICT,
                    "当前状态 " + appointment.getStatus() + " 不能" + operation);
        }
    }

    /**
     * 取消/改期规则：必须在“时段开始 - 提前小时数”之前操作。
     */
    private void checkCutoff(AppointmentType type, ServiceSlot slot, String operation) {
        Instant deadline = ServiceSlotService.slotStartAt(slot).minusSeconds(type.getCancelCutoffHours() * 3600L);
        if (Instant.now().isAfter(deadline)) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_FAILED,
                    "距预约开始不足 " + type.getCancelCutoffHours() + " 小时，不能" + operation + "，请联系前台处理");
        }
    }

    private void validateSlotForType(ServiceSlot slot, String typeCode) {
        if (!slot.getTypeCode().equals(typeCode)) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_FAILED, "服务时段的类型与预约类型不一致");
        }
        if (!"OPEN".equals(slot.getStatus())) {
            throw new BusinessException(ErrorCode.BUSINESS_CONFLICT, "该服务时段已关闭");
        }
    }

    private void applyContactPhone(Appointment appointment, String phone) {
        String normalized = CustomerMasking.normalizePhone(phone);
        if (normalized == null) {
            return;
        }
        appointment.setContactPhoneEncrypted(cipher.encrypt(normalized));
        appointment.setContactPhoneHash(hasher.hash(normalized));
        appointment.setContactPhoneMasked(CustomerMasking.maskPhone(normalized));
    }

    private Map<UUID, ServiceSlot> loadSlots(List<Appointment> appointments) {
        Set<UUID> slotIds = appointments.stream().map(Appointment::getSlotId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (slotIds.isEmpty()) {
            return Map.of();
        }
        return slotRepository.findAllById(slotIds).stream()
                .collect(Collectors.toMap(ServiceSlot::getId, Function.identity()));
    }

    private Map<String, String> typeNames() {
        Map<String, String> names = new LinkedHashMap<>();
        typeRepository.findAllByOrderByTypeCodeAsc()
                .forEach(type -> names.put(type.getTypeCode(), type.getTypeName()));
        return names;
    }

    private Map<UUID, String> userNames(Set<UUID> userIds) {
        Map<UUID, String> names = new LinkedHashMap<>();
        if (userIds.isEmpty()) {
            return names;
        }
        userRepository.findAllById(userIds)
                .forEach(user -> names.put(user.getId(), user.getDisplayName()));
        return names;
    }

    private Specification<Appointment> buildSpecification(String keyword,
                                                          String status,
                                                          String typeCode,
                                                          UUID customerId,
                                                          List<UUID> slotIds) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (keyword != null && !keyword.isBlank()) {
                String like = "%" + keyword.trim().toLowerCase() + "%";
                predicates.add(cb.like(cb.lower(root.get("appointmentNo")), like));
            }
            if (status != null && !status.isBlank()) {
                predicates.add(cb.equal(root.get("status"), status.trim()));
            }
            if (typeCode != null && !typeCode.isBlank()) {
                predicates.add(cb.equal(root.get("typeCode"), typeCode.trim()));
            }
            if (customerId != null) {
                predicates.add(cb.equal(root.get("customerId"), customerId));
            }
            if (slotIds != null) {
                predicates.add(root.get("slotId").in(slotIds));
            }
            return predicates.isEmpty() ? cb.conjunction()
                    : cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private Specification<ServiceSlot> dateRangeSpecification(LocalDate dateFrom, LocalDate dateTo) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (dateFrom != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("slotDate"), dateFrom));
            }
            if (dateTo != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("slotDate"), dateTo));
            }
            return predicates.isEmpty() ? cb.conjunction()
                    : cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private Specification<NoShowRecord> buildNoShowSpecification(UUID customerId,
                                                                 LocalDate dateFrom,
                                                                 LocalDate dateTo) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (customerId != null) {
                predicates.add(cb.equal(root.get("customerId"), customerId));
            }
            if (dateFrom != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("appointmentAt"),
                        dateFrom.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant()));
            }
            if (dateTo != null) {
                predicates.add(cb.lessThan(root.get("appointmentAt"),
                        dateTo.plusDays(1).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant()));
            }
            return predicates.isEmpty() ? cb.conjunction()
                    : cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private AppointmentDtos.AppointmentSummary toSummary(Appointment appointment,
                                                         ServiceSlot slot,
                                                         Map<String, String> typeNames,
                                                         CustomerDtos.CustomerBrief customer) {
        return new AppointmentDtos.AppointmentSummary(
                appointment.getId(),
                appointment.getAppointmentNo(),
                appointment.getCustomerId(),
                customer == null ? null : customer.customerNo(),
                customer == null ? null : customer.name(),
                appointment.getTypeCode(),
                typeNames.getOrDefault(appointment.getTypeCode(), appointment.getTypeCode()),
                appointment.getSlotId(),
                slot == null ? null : slot.getSlotDate(),
                slot == null ? null : slot.getStartTime(),
                slot == null ? null : slot.getEndTime(),
                appointment.getStatus(),
                appointment.getContactPhoneMasked(),
                appointment.getCreatedAt());
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
