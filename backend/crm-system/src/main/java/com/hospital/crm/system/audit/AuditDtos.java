package com.hospital.crm.system.audit;

import java.time.Instant;
import java.util.UUID;

public final class AuditDtos {

    private AuditDtos() {
    }

    public record AuditLogItem(
            UUID id,
            UUID actorUserId,
            String actorName,
            String action,
            String resourceType,
            String resourceId,
            String ipAddress,
            String traceId,
            String detail,
            Instant createdAt
    ) {
    }
}
