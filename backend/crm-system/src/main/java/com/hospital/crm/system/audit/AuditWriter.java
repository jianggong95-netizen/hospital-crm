package com.hospital.crm.system.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Component
public class AuditWriter {

    private static final Logger log = LoggerFactory.getLogger(AuditWriter.class);

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public AuditWriter(NamedParameterJdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void write(AuditEvent event) {
        try {
            String detailJson;
            try {
                detailJson = objectMapper.writeValueAsString(event.detail() == null ? Map.of() : event.detail());
            } catch (JsonProcessingException exception) {
                detailJson = "{}";
                log.warn("审计明细序列化失败: action={}", event.action(), exception);
            }

            MapSqlParameterSource parameters = new MapSqlParameterSource()
                    .addValue("actorUserId", event.actorUserId())
                    .addValue("action", truncate(event.action(), 80))
                    .addValue("resourceType", truncate(event.resourceType(), 80))
                    .addValue("resourceId", truncate(event.resourceId(), 100))
                    .addValue("ip", event.ipAddress())
                    .addValue("userAgent", truncate(event.userAgent(), 500))
                    .addValue("traceId", truncate(event.traceId(), 80))
                    .addValue("detail", detailJson);

            jdbcTemplate.update("""
                    insert into sys_audit_log
                        (actor_user_id, action, resource_type, resource_id, ip_address, user_agent, detail, trace_id, created_at)
                    values
                        (:actorUserId, :action, :resourceType, :resourceId, cast(:ip as inet), :userAgent,
                         cast(:detail as jsonb), :traceId, :now)
                    """, parameters.addValue("now",
                    java.time.OffsetDateTime.ofInstant(event.occurredAt(), java.time.ZoneOffset.UTC)));
        } catch (Exception exception) {
            log.error("写入审计日志失败: action={}, resourceType={}, resourceId={}",
                    event.action(), event.resourceType(), event.resourceId(), exception);
        }
    }

    private String truncate(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
