package com.hospital.crm.appointment.application;

import com.hospital.crm.appointment.api.AppointmentDtos;
import com.hospital.crm.appointment.domain.AppointmentType;
import com.hospital.crm.appointment.domain.AppointmentTypeRepository;
import com.hospital.crm.appointment.domain.ServiceSlot;
import com.hospital.crm.appointment.domain.ServiceSlotRepository;
import com.hospital.crm.common.api.ErrorCode;
import com.hospital.crm.common.api.PageResponse;
import com.hospital.crm.common.exception.BusinessException;
import com.hospital.crm.system.audit.AuditService;
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
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 服务时段管理：同一类型同一日期同一开始时间唯一；
 * 已有预约的时段不允许改期或缩容到低于已约人数，关闭后不可再预约。
 */
@Service
public class ServiceSlotService {

    private static final String RESOURCE = "SERVICE_SLOT";

    private final ServiceSlotRepository slotRepository;
    private final AppointmentTypeRepository typeRepository;
    private final AuditService auditService;

    public ServiceSlotService(ServiceSlotRepository slotRepository,
                              AppointmentTypeRepository typeRepository,
                              AuditService auditService) {
        this.slotRepository = slotRepository;
        this.typeRepository = typeRepository;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public PageResponse<AppointmentDtos.SlotView> list(String typeCode,
                                                       LocalDate dateFrom,
                                                       LocalDate dateTo,
                                                       String status,
                                                       int page,
                                                       int pageSize) {
        int safePage = Math.max(page, 1);
        int safePageSize = Math.min(Math.max(pageSize, 1), 100);
        Pageable pageable = PageRequest.of(safePage - 1, safePageSize,
                Sort.by(Sort.Direction.ASC, "slotDate").and(Sort.by(Sort.Direction.ASC, "startTime")));
        Page<ServiceSlot> result = slotRepository.findAll(
                buildSpecification(typeCode, dateFrom, dateTo, status), pageable);
        Map<String, String> typeNames = typeNames();
        List<AppointmentDtos.SlotView> items = result.getContent().stream()
                .map(slot -> toView(slot, typeNames))
                .toList();
        return PageResponse.of(items, result.getTotalElements(), safePage, safePageSize);
    }

    @Transactional
    public AppointmentDtos.SlotView create(AppointmentDtos.SlotCreateRequest request) {
        AppointmentType type = requireActiveType(request.typeCode());
        validateTimes(request.slotDate(), request.startTime(), request.endTime());
        validateCapacity(request.capacity());
        if (slotRepository.existsByTypeCodeAndSlotDateAndStartTime(
                request.typeCode(), request.slotDate(), request.startTime())) {
            throw new BusinessException(ErrorCode.BUSINESS_CONFLICT,
                    "同一类型同一日期同一开始时间的时段已存在");
        }

        ServiceSlot slot = new ServiceSlot();
        slot.setTypeCode(type.getTypeCode());
        slot.setSlotDate(request.slotDate());
        slot.setStartTime(request.startTime());
        slot.setEndTime(request.endTime());
        slot.setCapacity(request.capacity());
        slot.setRemark(trimToNull(request.remark()));
        slot = slotRepository.save(slot);

        Map<String, Object> audit = new LinkedHashMap<>();
        audit.put("typeCode", slot.getTypeCode());
        audit.put("slotDate", String.valueOf(slot.getSlotDate()));
        audit.put("startTime", String.valueOf(slot.getStartTime()));
        audit.put("capacity", slot.getCapacity());
        auditService.record("SLOT_CREATE", RESOURCE, slot.getId().toString(), audit);
        return toView(slot, typeNames());
    }

    @Transactional
    public AppointmentDtos.SlotView update(UUID slotId, AppointmentDtos.SlotUpdateRequest request) {
        ServiceSlot slot = load(slotId);
        boolean timeChanged = !slot.getSlotDate().equals(request.slotDate())
                || !slot.getStartTime().equals(request.startTime())
                || !slot.getEndTime().equals(request.endTime());
        if (timeChanged && slot.getBookedCount() > 0) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_FAILED,
                    "时段已有 " + slot.getBookedCount() + " 个预约，不能修改日期或时间，请先处理存量预约");
        }
        if (request.capacity() < slot.getBookedCount()) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_FAILED,
                    "容量不能小于已预约人数 " + slot.getBookedCount());
        }
        validateTimes(request.slotDate(), request.startTime(), request.endTime());
        validateCapacity(request.capacity());

        slot.setSlotDate(request.slotDate());
        slot.setStartTime(request.startTime());
        slot.setEndTime(request.endTime());
        slot.setCapacity(request.capacity());
        slot.setRemark(trimToNull(request.remark()));
        slot = slotRepository.save(slot);

        Map<String, Object> audit = new LinkedHashMap<>();
        audit.put("typeCode", slot.getTypeCode());
        audit.put("slotDate", String.valueOf(slot.getSlotDate()));
        audit.put("capacity", slot.getCapacity());
        auditService.record("SLOT_UPDATE", RESOURCE, slot.getId().toString(), audit);
        return toView(slot, typeNames());
    }

    @Transactional
    public AppointmentDtos.SlotView changeStatus(UUID slotId, String status) {
        ServiceSlot slot = load(slotId);
        slot.setStatus(status);
        slot = slotRepository.save(slot);

        Map<String, Object> audit = new LinkedHashMap<>();
        audit.put("typeCode", slot.getTypeCode());
        audit.put("slotDate", String.valueOf(slot.getSlotDate()));
        audit.put("status", status);
        auditService.record("SLOT_STATUS", RESOURCE, slot.getId().toString(), audit);
        return toView(slot, typeNames());
    }

    @Transactional(readOnly = true)
    public ServiceSlot load(UUID slotId) {
        return slotRepository.findById(slotId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "服务时段不存在"));
    }

    private AppointmentType requireActiveType(String typeCode) {
        AppointmentType type = typeRepository.findByTypeCode(typeCode)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND,
                        "预约类型不存在：" + typeCode));
        if (!"active".equals(type.getStatus())) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_FAILED,
                    "预约类型已停用：" + type.getTypeName());
        }
        return type;
    }

    private void validateTimes(LocalDate slotDate, LocalTime startTime, LocalTime endTime) {
        if (slotDate.isBefore(LocalDate.now())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "服务日期不能早于今天");
        }
        if (!endTime.isAfter(startTime)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "结束时间必须晚于开始时间");
        }
    }

    private void validateCapacity(int capacity) {
        if (capacity < 1 || capacity > 500) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "时段容量必须在 1 到 500 之间");
        }
    }

    private Specification<ServiceSlot> buildSpecification(String typeCode,
                                                          LocalDate dateFrom,
                                                          LocalDate dateTo,
                                                          String status) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (typeCode != null && !typeCode.isBlank()) {
                predicates.add(cb.equal(root.get("typeCode"), typeCode.trim()));
            }
            if (dateFrom != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("slotDate"), dateFrom));
            }
            if (dateTo != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("slotDate"), dateTo));
            }
            if (status != null && !status.isBlank()) {
                predicates.add(cb.equal(root.get("status"), status.trim()));
            }
            return predicates.isEmpty() ? cb.conjunction()
                    : cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private Map<String, String> typeNames() {
        Map<String, String> names = new LinkedHashMap<>();
        typeRepository.findAllByOrderByTypeCodeAsc()
                .forEach(type -> names.put(type.getTypeCode(), type.getTypeName()));
        return names;
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private AppointmentDtos.SlotView toView(ServiceSlot slot, Map<String, String> typeNames) {
        return new AppointmentDtos.SlotView(
                slot.getId(),
                slot.getTypeCode(),
                typeNames.getOrDefault(slot.getTypeCode(), slot.getTypeCode()),
                slot.getSlotDate(),
                slot.getStartTime(),
                slot.getEndTime(),
                slot.getCapacity(),
                slot.getBookedCount(),
                slot.getCapacity() - slot.getBookedCount(),
                slot.getStatus(),
                slot.getRemark(),
                slot.getUpdatedAt());
    }

    /**
     * 时段开始时间（按服务器时区换算为 Instant），供取消规则与提醒计划使用。
     */
    public static Instant slotStartAt(ServiceSlot slot) {
        return slot.getSlotDate().atTime(slot.getStartTime())
                .atZone(java.time.ZoneId.systemDefault()).toInstant();
    }
}
