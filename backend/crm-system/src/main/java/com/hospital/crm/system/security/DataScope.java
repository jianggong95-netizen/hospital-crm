package com.hospital.crm.system.security;

import java.util.Set;
import java.util.UUID;

public record DataScope(
        boolean all,
        Set<UUID> orgIds,
        boolean includeSelf,
        UUID userId
) {

    public boolean allowsOrg(UUID orgId) {
        return all || (orgId != null && orgIds.contains(orgId));
    }
}
