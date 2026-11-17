package com.hospital.crm.homevisit.application;

import com.hospital.crm.common.api.ErrorCode;
import com.hospital.crm.common.api.PageResponse;
import com.hospital.crm.common.exception.BusinessException;
import com.hospital.crm.homevisit.api.HomeVisitDtos;
import com.hospital.crm.homevisit.domain.HomeStaffQualification;
import com.hospital.crm.homevisit.domain.HomeStaffQualificationRepository;
import com.hospital.crm.system.audit.AuditService;
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

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 上门服务人员资质服务：按系统用户登记护士、护理员、健康管理师、医师等证书，
 * 同一用户同类型同编号唯一；停用或过期后不可参与派单与接单。
 */
@Service
public class HomeStaffQualificationService {

    private static final String RESOURCE = "STAFF_QUALIFICATION";

    private final HomeStaffQualificationRepository qualificationRepository;
    private final SysUserRepository userRepository;
    private final AuditService auditService;

    public HomeStaffQualificationService(HomeStaffQualificationRepository qualificationRepository,
                                         SysUserRepository userRepository,
                                         AuditService auditService) {
        this.qualificationRepository = qualificationRepository;
        this.userRepository = userRepository;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public PageResponse<HomeVisitDtos.StaffQualificationView> list(UUID userId,
                                                                    String qualType,
                                                                    int page,
                                                                    int pageSize) {
        int safePage = Math.max(page, 1);
        int safePageSize = Math.min(Math.max(pageSize, 1), 100);
        Pageable pageable = PageRequest.of(safePage - 1, safePageSize,
                Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<HomeStaffQualification> result = qualificationRepository.findAll(
                buildSpecification(userId, qualType), pageable);
        Map<UUID, SysUser> users = userMap(result.getContent().stream()
                .map(HomeStaffQualification::getUserId).collect(Collectors.toSet()));
        List<HomeVisitDtos.StaffQualificationView> items = result.getContent().stream()
                .map(qualification -> toView(qualification, users.get(qualification.getUserId())))
                .toList();
        return PageResponse.of(items, result.getTotalElements(), safePage, safePageSize);
    }

    @Transactional
    public HomeVisitDtos.StaffQualificationView create(HomeVisitDtos.StaffQualificationCreateRequest request) {
        SysUser user = userRepository.findById(request.userId())
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "服务人员账号不存在"));
        if (request.expiresAt() != null && request.expiresAt().isBefore(request.issuedAt())) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_FAILED, "资质到期日期不能早于发证日期");
        }
        if (qualificationRepository.existsByUserIdAndQualTypeAndCertNo(
                request.userId(), request.qualType(), request.certNo().trim())) {
            throw new BusinessException(ErrorCode.BUSINESS_CONFLICT, "该人员已登记相同类型与编号的资质");
        }

        HomeStaffQualification qualification = new HomeStaffQualification();
        qualification.setUserId(request.userId());
        qualification.setQualType(request.qualType());
        qualification.setCertNo(request.certNo().trim());
        qualification.setIssuedAt(request.issuedAt());
        qualification.setExpiresAt(request.expiresAt());
        qualification.setStatus("active");
        qualification = qualificationRepository.save(qualification);

        Map<String, Object> audit = new LinkedHashMap<>();
        audit.put("username", user.getUsername());
        audit.put("certNo", qualification.getCertNo());
        auditService.record("STAFFQUAL_CREATE", RESOURCE, qualification.getId().toString(), audit);

        return toView(qualification, user);
    }

    @Transactional
    public HomeVisitDtos.StaffQualificationView revoke(UUID qualificationId) {
        HomeStaffQualification qualification = load(qualificationId);
        if ("revoked".equals(qualification.getStatus())) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_FAILED, "该资质已停用，请勿重复操作");
        }
        qualification.setStatus("revoked");
        qualification = qualificationRepository.save(qualification);

        SysUser user = userRepository.findById(qualification.getUserId()).orElse(null);
        Map<String, Object> audit = new LinkedHashMap<>();
        audit.put("username", user == null ? null : user.getUsername());
        audit.put("certNo", qualification.getCertNo());
        auditService.record("STAFFQUAL_REVOKE", RESOURCE, qualificationId.toString(), audit);

        return toView(qualification, user);
    }

    @Transactional(readOnly = true)
    public HomeStaffQualification load(UUID qualificationId) {
        return qualificationRepository.findById(qualificationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "资质记录不存在"));
    }

    private Map<UUID, SysUser> userMap(Set<UUID> userIds) {
        if (userIds.isEmpty()) {
            return Map.of();
        }
        return userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(SysUser::getId, Function.identity()));
    }

    private HomeVisitDtos.StaffQualificationView toView(HomeStaffQualification qualification, SysUser user) {
        boolean expired = qualification.getExpiresAt() != null
                && qualification.getExpiresAt().isBefore(LocalDate.now());
        return new HomeVisitDtos.StaffQualificationView(
                qualification.getId(),
                qualification.getUserId(),
                user == null ? null : user.getUsername(),
                user == null ? null : user.getDisplayName(),
                qualification.getQualType(),
                qualification.getCertNo(),
                qualification.getIssuedAt(),
                qualification.getExpiresAt(),
                qualification.getStatus(),
                expired);
    }

    private Specification<HomeStaffQualification> buildSpecification(UUID userId, String qualType) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (userId != null) {
                predicates.add(cb.equal(root.get("userId"), userId));
            }
            if (qualType != null && !qualType.isBlank()) {
                predicates.add(cb.equal(root.get("qualType"), qualType.trim()));
            }
            return predicates.isEmpty() ? cb.conjunction()
                    : cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
