package com.hospital.crm.system.audit;

import com.hospital.crm.common.api.PageResponse;
import com.hospital.crm.common.trace.TraceContext;
import com.hospital.crm.system.security.DataScope;
import com.hospital.crm.system.security.DataScopeService;
import com.hospital.crm.system.security.RequestInfo;
import com.hospital.crm.system.security.UserPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class AuditService {

    private final AuditWriter auditWriter;
    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final DataScopeService dataScopeService;

    public AuditService(AuditWriter auditWriter,
                        NamedParameterJdbcTemplate jdbcTemplate,
                        DataScopeService dataScopeService) {
        this.auditWriter = auditWriter;
        this.jdbcTemplate = jdbcTemplate;
        this.dataScopeService = dataScopeService;
    }

    /**
     * 在事务提交后写入审计；无事务时立即写入。
     */
    public void record(String action, String resourceType, String resourceId, Map<String, Object> detail) {
        AuditEvent event = buildEvent(action, resourceType, resourceId, detail);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    auditWriter.write(event);
                }
            });
        } else {
            auditWriter.write(event);
        }
    }

    /**
     * 立即写入审计，用于登录失败等需要随异常一起落库的场景。
     */
    public void recordNow(String action, String resourceType, String resourceId, Map<String, Object> detail) {
        auditWriter.write(buildEvent(action, resourceType, resourceId, detail));
    }

    public PageResponse<AuditDtos.AuditLogItem> page(int page, int pageSize, String action,
                                                     String resourceType, UUID actorUserId,
                                                     Instant from, Instant to) {
        int safePage = Math.max(page, 1);
        int safePageSize = Math.min(Math.max(pageSize, 1), 100);
        DataScope scope = dataScopeService.current();

        StringBuilder where = new StringBuilder(" where 1 = 1");
        MapSqlParameterSource parameters = new MapSqlParameterSource();

        if (action != null && !action.isBlank()) {
            where.append(" and a.action = :action");
            parameters.addValue("action", action.trim());
        }
        if (resourceType != null && !resourceType.isBlank()) {
            where.append(" and a.resource_type = :resourceType");
            parameters.addValue("resourceType", resourceType.trim());
        }
        if (actorUserId != null) {
            where.append(" and a.actor_user_id = :actorUserId");
            parameters.addValue("actorUserId", actorUserId);
        }
        if (from != null) {
            where.append(" and a.created_at >= :from");
            parameters.addValue("from", OffsetDateTime.ofInstant(from, ZoneOffset.UTC));
        }
        if (to != null) {
            where.append(" and a.created_at < :to");
            parameters.addValue("to", OffsetDateTime.ofInstant(to, ZoneOffset.UTC));
        }

        if (!scope.all()) {
            List<UUID> orgIds = new ArrayList<>(scope.orgIds());
            if (orgIds.isEmpty() && !scope.includeSelf()) {
                return PageResponse.of(List.of(), 0, safePage, safePageSize);
            }
            if (orgIds.isEmpty()) {
                where.append(" and a.actor_user_id = :selfId");
                parameters.addValue("selfId", scope.userId());
            } else if (scope.includeSelf()) {
                where.append(" and (a.actor_user_id = :selfId or u.org_id in (:orgIds))");
                parameters.addValue("selfId", scope.userId());
                parameters.addValue("orgIds", orgIds);
            } else {
                where.append(" and u.org_id in (:orgIds)");
                parameters.addValue("orgIds", orgIds);
            }
        }

        String baseFrom = " from sys_audit_log a left join sys_user u on u.id = a.actor_user_id";
        Long total = jdbcTemplate.queryForObject(
                "select count(*)" + baseFrom + where, parameters, Long.class);

        parameters.addValue("limit", safePageSize);
        parameters.addValue("offset", (safePage - 1) * safePageSize);

        List<AuditDtos.AuditLogItem> items = jdbcTemplate.query("""
                        select a.id, a.actor_user_id, u.display_name as actor_name, a.action, a.resource_type,
                               a.resource_id, a.ip_address::text as ip_address, a.trace_id,
                               a.detail::text as detail, a.created_at
                        """ + baseFrom + where + " order by a.created_at desc limit :limit offset :offset",
                parameters,
                (resultSet, rowNum) -> new AuditDtos.AuditLogItem(
                        resultSet.getObject("id", UUID.class),
                        resultSet.getObject("actor_user_id", UUID.class),
                        resultSet.getString("actor_name"),
                        resultSet.getString("action"),
                        resultSet.getString("resource_type"),
                        resultSet.getString("resource_id"),
                        resultSet.getString("ip_address"),
                        resultSet.getString("trace_id"),
                        resultSet.getString("detail"),
                        resultSet.getTimestamp("created_at").toInstant()
                ));

        long safeTotal = total == null ? 0 : total;
        return PageResponse.of(items, safeTotal, safePage, safePageSize);
    }

    private AuditEvent buildEvent(String action, String resourceType, String resourceId,
                                  Map<String, Object> detail) {
        HttpServletRequest request = currentRequest();
        Map<String, Object> safeDetail = detail == null ? new LinkedHashMap<>() : new LinkedHashMap<>(detail);
        return new AuditEvent(
                currentActorId(),
                action,
                resourceType,
                resourceId,
                RequestInfo.ip(request),
                RequestInfo.userAgent(request),
                TraceContext.currentTraceId(),
                safeDetail,
                Instant.now()
        );
    }

    private UUID currentActorId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof UserPrincipal principal) {
            return principal.userId();
        }
        return null;
    }

    private HttpServletRequest currentRequest() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            return attributes.getRequest();
        }
        return null;
    }
}
