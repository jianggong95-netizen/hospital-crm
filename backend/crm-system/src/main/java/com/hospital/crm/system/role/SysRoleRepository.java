package com.hospital.crm.system.role;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SysRoleRepository extends JpaRepository<SysRole, UUID> {

    Optional<SysRole> findByRoleCode(String roleCode);

    boolean existsByRoleCode(String roleCode);

    @EntityGraph(attributePaths = {"permissions", "customOrgIds"})
    @Query("select distinct r from SysRole r order by r.roleCode")
    List<SysRole> findAllWithPermissions();
}
