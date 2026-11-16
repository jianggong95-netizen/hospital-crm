package com.hospital.crm.system.security;

import java.util.Set;
import java.util.UUID;

public record RoleScope(DataScopeType type, Set<UUID> orgIds) {
}
