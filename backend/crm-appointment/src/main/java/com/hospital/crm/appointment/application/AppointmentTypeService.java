package com.hospital.crm.appointment.application;

import com.hospital.crm.appointment.api.AppointmentDtos;
import com.hospital.crm.appointment.domain.AppointmentType;
import com.hospital.crm.appointment.domain.AppointmentTypeRepository;
import com.hospital.crm.common.api.ErrorCode;
import com.hospital.crm.common.exception.BusinessException;
import com.hospital.crm.system.audit.AuditService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 预约类型字典服务：类型编码唯一，取消/改期提前小时数在此配置。
 */
@Service
public class AppointmentTypeService {

    private static final String RESOURCE = "APPT_TYPE";

    private final AppointmentTypeRepository typeRepository;
    private final AuditService auditService;

    public AppointmentTypeService(AppointmentTypeRepository typeRepository, AuditService auditService) {
        this.typeRepository = typeRepository;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<AppointmentDtos.TypeView> list(boolean includeDisabled) {
        return typeRepository.findAllByOrderByTypeCodeAsc().stream()
                .filter(type -> includeDisabled || "active".equals(type.getStatus()))
                .map(this::toView)
                .toList();
    }

    @Transactional
    public AppointmentDtos.TypeView create(AppointmentDtos.TypeCreateRequest request) {
        if (typeRepository.existsByTypeCode(request.typeCode())) {
            throw new BusinessException(ErrorCode.BUSINESS_CONFLICT, "类型编码已存在：" + request.typeCode());
        }
        AppointmentType type = new AppointmentType();
        type.setTypeCode(request.typeCode());
        type.setTypeName(request.typeName().trim());
        type.setDurationMinutes(clampDuration(request.durationMinutes()));
        type.setCancelCutoffHours(clampCutoff(request.cancelCutoffHours()));
        type.setRemark(blankToNull(request.remark()));
        type = typeRepository.save(type);

        Map<String, Object> audit = new LinkedHashMap<>();
        audit.put("typeCode", type.getTypeCode());
        audit.put("typeName", type.getTypeName());
        auditService.record("APPTTYPE_CREATE", RESOURCE, type.getId().toString(), audit);
        return toView(type);
    }

    @Transactional
    public AppointmentDtos.TypeView update(String typeCode, AppointmentDtos.TypeUpdateRequest request) {
        AppointmentType type = load(typeCode);
        type.setTypeName(request.typeName().trim());
        type.setDurationMinutes(clampDuration(request.durationMinutes()));
        type.setCancelCutoffHours(clampCutoff(request.cancelCutoffHours()));
        type.setRemark(blankToNull(request.remark()));
        type = typeRepository.save(type);

        Map<String, Object> audit = new LinkedHashMap<>();
        audit.put("typeCode", type.getTypeCode());
        audit.put("cancelCutoffHours", type.getCancelCutoffHours());
        auditService.record("APPTTYPE_UPDATE", RESOURCE, type.getId().toString(), audit);
        return toView(type);
    }

    @Transactional
    public AppointmentDtos.TypeView changeStatus(String typeCode, String status) {
        AppointmentType type = load(typeCode);
        type.setStatus(status);
        type = typeRepository.save(type);

        Map<String, Object> audit = new LinkedHashMap<>();
        audit.put("typeCode", type.getTypeCode());
        audit.put("status", status);
        auditService.record("APPTTYPE_STATUS", RESOURCE, type.getId().toString(), audit);
        return toView(type);
    }

    @Transactional(readOnly = true)
    public AppointmentType load(String typeCode) {
        return typeRepository.findByTypeCode(typeCode)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "预约类型不存在：" + typeCode));
    }

    private int clampDuration(Integer durationMinutes) {
        int value = durationMinutes == null ? 30 : durationMinutes;
        if (value < 1 || value > 480) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "服务时长必须在 1 到 480 分钟之间");
        }
        return value;
    }

    private int clampCutoff(Integer cancelCutoffHours) {
        int value = cancelCutoffHours == null ? 2 : cancelCutoffHours;
        if (value < 0 || value > 720) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "取消提前小时数必须在 0 到 720 之间");
        }
        return value;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private AppointmentDtos.TypeView toView(AppointmentType type) {
        return new AppointmentDtos.TypeView(
                type.getId(),
                type.getTypeCode(),
                type.getTypeName(),
                type.getDurationMinutes(),
                type.getCancelCutoffHours(),
                type.getStatus(),
                type.getRemark(),
                type.getCreatedAt());
    }
}
