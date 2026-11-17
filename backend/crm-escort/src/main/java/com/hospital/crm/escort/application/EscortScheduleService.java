package com.hospital.crm.escort.application;

import com.hospital.crm.common.api.ErrorCode;
import com.hospital.crm.common.api.PageResponse;
import com.hospital.crm.common.exception.BusinessException;
import com.hospital.crm.escort.api.EscortDtos;
import com.hospital.crm.escort.domain.Escort;
import com.hospital.crm.escort.domain.EscortRepository;
import com.hospital.crm.escort.domain.EscortSchedule;
import com.hospital.crm.escort.domain.EscortScheduleRepository;
import com.hospital.crm.system.audit.AuditService;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 陪诊师排班服务：同一陪诊师同日班次时间段不得重叠（start < 已有end 且 end > 已有start）；
 * 排班仅允许安排今天及以后，删除仅限未来日期，避免影响已发生的派单。
 */
@Service
public class EscortScheduleService {

    private static final String RESOURCE = "ESCORT_SCHEDULE";

    private final EscortScheduleRepository scheduleRepository;
    private final EscortRepository escortRepository;
    private final AuditService auditService;

    public EscortScheduleService(EscortScheduleRepository scheduleRepository,
                                 EscortRepository escortRepository,
                                 AuditService auditService) {
        this.scheduleRepository = scheduleRepository;
        this.escortRepository = escortRepository;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public PageResponse<EscortDtos.ScheduleView> list(UUID escortId,
                                                      LocalDate dateFrom,
                                                      LocalDate dateTo,
                                                      int page,
                                                      int pageSize) {
        int safePage = Math.max(page, 1);
        int safePageSize = Math.min(Math.max(pageSize, 1), 100);
        Pageable pageable = PageRequest.of(safePage - 1, safePageSize,
                Sort.by(Sort.Order.asc("workDate"), Sort.Order.asc("startTime")));
        Page<EscortSchedule> result = scheduleRepository.findAll(
                buildSpecification(escortId, dateFrom, dateTo), pageable);
        Map<UUID, Escort> escorts = escortNames(result.getContent().stream()
                .map(EscortSchedule::getEscortId).collect(Collectors.toSet()));
        List<EscortDtos.ScheduleView> items = result.getContent().stream()
                .map(schedule -> toView(schedule, escorts.get(schedule.getEscortId())))
                .toList();
        return PageResponse.of(items, result.getTotalElements(), safePage, safePageSize);
    }

    @Transactional
    public EscortDtos.ScheduleView create(EscortDtos.ScheduleCreateRequest request) {
        Escort escort = loadEscort(request.escortId());
        requireEndTimeAfterStart(request.startTime(), request.endTime());
        if (request.workDate().isBefore(LocalDate.now())) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_FAILED, "排班日期不能早于今天");
        }
        requireNoOverlap(request.escortId(), request.workDate(),
                request.startTime(), request.endTime(), null);

        EscortSchedule schedule = new EscortSchedule();
        schedule.setEscortId(request.escortId());
        schedule.setWorkDate(request.workDate());
        schedule.setStartTime(request.startTime());
        schedule.setEndTime(request.endTime());
        schedule = scheduleRepository.save(schedule);

        Map<String, Object> audit = new LinkedHashMap<>();
        audit.put("escortNo", escort.getEscortNo());
        audit.put("workDate", String.valueOf(schedule.getWorkDate()));
        auditService.record("SCHEDULE_CREATE", RESOURCE, schedule.getId().toString(), audit);

        return toView(schedule, escort);
    }

    @Transactional
    public EscortDtos.ScheduleView update(UUID scheduleId, EscortDtos.ScheduleUpdateRequest request) {
        EscortSchedule schedule = load(scheduleId);
        Escort escort = loadEscort(schedule.getEscortId());
        requireEndTimeAfterStart(request.startTime(), request.endTime());
        if (request.workDate().isBefore(LocalDate.now())) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_FAILED, "排班日期不能早于今天");
        }
        requireNoOverlap(schedule.getEscortId(), request.workDate(),
                request.startTime(), request.endTime(), scheduleId);

        schedule.setWorkDate(request.workDate());
        schedule.setStartTime(request.startTime());
        schedule.setEndTime(request.endTime());
        schedule = scheduleRepository.save(schedule);

        Map<String, Object> audit = new LinkedHashMap<>();
        audit.put("escortNo", escort.getEscortNo());
        audit.put("workDate", String.valueOf(schedule.getWorkDate()));
        auditService.record("SCHEDULE_UPDATE", RESOURCE, scheduleId.toString(), audit);

        return toView(schedule, escort);
    }

    @Transactional
    public void delete(UUID scheduleId) {
        EscortSchedule schedule = load(scheduleId);
        if (!schedule.getWorkDate().isAfter(LocalDate.now())) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_FAILED, "仅可删除今天之后的排班");
        }
        Escort escort = escortRepository.findById(schedule.getEscortId()).orElse(null);
        scheduleRepository.deleteById(scheduleId);

        Map<String, Object> audit = new LinkedHashMap<>();
        audit.put("escortNo", escort == null ? null : escort.getEscortNo());
        audit.put("workDate", String.valueOf(schedule.getWorkDate()));
        auditService.record("SCHEDULE_DELETE", RESOURCE, scheduleId.toString(), audit);
    }

    @Transactional(readOnly = true)
    public EscortSchedule load(UUID scheduleId) {
        return scheduleRepository.findById(scheduleId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "排班不存在"));
    }

    private Escort loadEscort(UUID escortId) {
        return escortRepository.findById(escortId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "陪诊师不存在"));
    }

    private void requireEndTimeAfterStart(LocalTime startTime, LocalTime endTime) {
        if (endTime == null || startTime == null || !endTime.isAfter(startTime)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "结束时间必须晚于开始时间");
        }
    }

    /**
     * 时间段重叠校验：start < 已有end 且 end > 已有start；excludeScheduleId 非空时排除自身（更新场景）。
     */
    private void requireNoOverlap(UUID escortId, LocalDate workDate,
                                  LocalTime startTime, LocalTime endTime,
                                  UUID excludeScheduleId) {
        List<EscortSchedule> existing = scheduleRepository.findByEscortIdAndWorkDate(escortId, workDate);
        boolean overlapped = existing.stream()
                .filter(schedule -> excludeScheduleId == null
                        || !schedule.getId().equals(excludeScheduleId))
                .anyMatch(schedule -> startTime.isBefore(schedule.getEndTime())
                        && endTime.isAfter(schedule.getStartTime()));
        if (overlapped) {
            throw new BusinessException(ErrorCode.BUSINESS_CONFLICT, "排班与已有班次重叠");
        }
    }

    private Map<UUID, Escort> escortNames(Set<UUID> escortIds) {
        Map<UUID, Escort> names = new LinkedHashMap<>();
        if (escortIds.isEmpty()) {
            return names;
        }
        return escortRepository.findAllById(escortIds).stream()
                .collect(Collectors.toMap(Escort::getId, Function.identity(),
                        (a, b) -> a, LinkedHashMap::new));
    }

    private EscortDtos.ScheduleView toView(EscortSchedule schedule, Escort escort) {
        return new EscortDtos.ScheduleView(
                schedule.getId(),
                schedule.getEscortId(),
                escort == null ? null : escort.getName(),
                schedule.getWorkDate(),
                schedule.getStartTime(),
                schedule.getEndTime());
    }

    private Specification<EscortSchedule> buildSpecification(UUID escortId,
                                                             LocalDate dateFrom,
                                                             LocalDate dateTo) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (escortId != null) {
                predicates.add(cb.equal(root.get("escortId"), escortId));
            }
            if (dateFrom != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("workDate"), dateFrom));
            }
            if (dateTo != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("workDate"), dateTo));
            }
            return predicates.isEmpty() ? cb.conjunction()
                    : cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
