package com.hospital.crm.system.security;

import com.hospital.crm.system.org.SysOrgRepository;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Service
public class DataScopeService {

    private final SysOrgRepository orgRepository;

    public DataScopeService(SysOrgRepository orgRepository) {
        this.orgRepository = orgRepository;
    }

    public DataScope current() {
        return resolve(CurrentUser.principal());
    }

    public DataScope resolve(UserPrincipal principal) {
        boolean all = principal.roleScopes().stream()
                .anyMatch(scope -> scope.type() == DataScopeType.ALL);
        if (all) {
            return new DataScope(true, Set.of(), false, principal.userId());
        }

        Set<UUID> orgIds = new HashSet<>();
        boolean includeSelf = false;
        for (RoleScope roleScope : principal.roleScopes()) {
            switch (roleScope.type()) {
                case ALL -> {
                    return new DataScope(true, Set.of(), false, principal.userId());
                }
                case ORG -> {
                    if (principal.orgId() != null) {
                        orgIds.add(principal.orgId());
                    }
                }
                case ORG_AND_CHILDREN -> {
                    if (principal.orgPath() != null && !principal.orgPath().isBlank()) {
                        orgIds.addAll(orgRepository.findIdsByOrgPathPrefix(principal.orgPath()));
                    } else if (principal.orgId() != null) {
                        orgIds.add(principal.orgId());
                    }
                }
                case CUSTOM -> orgIds.addAll(roleScope.orgIds());
                case SELF -> includeSelf = true;
            }
        }
        return new DataScope(false, Set.copyOf(orgIds), includeSelf, principal.userId());
    }
}
