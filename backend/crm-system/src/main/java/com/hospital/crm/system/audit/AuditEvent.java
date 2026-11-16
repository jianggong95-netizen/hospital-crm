package com.hospital.crm.system.audit;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record AuditEvent(
        UUID actorUserId,
        String action,
        String resourceType,
        String resourceId,
        String ipAddress,
        String userAgent,
        String traceId,
        Map<String, Object> detail,
        Instant occurredAt
) {
}
