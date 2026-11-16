package com.hospital.crm.system.role;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface SysPermissionRepository extends JpaRepository<SysPermission, UUID> {

    List<SysPermission> findAllByOrderByModuleAscSortOrderAscPermissionCodeAsc();

    List<SysPermission> findByPermissionCodeIn(Collection<String> codes);
}
