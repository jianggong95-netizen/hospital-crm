package com.hospital.crm.system.user;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface SysUserRepository extends JpaRepository<SysUser, UUID>, JpaSpecificationExecutor<SysUser> {

    @EntityGraph(attributePaths = {"roles", "roles.permissions", "roles.customOrgIds"})
    Optional<SysUser> findByUsername(String username);

    @EntityGraph(attributePaths = {"roles", "roles.permissions", "roles.customOrgIds"})
    @Query("select u from SysUser u where u.id = :id")
    Optional<SysUser> findWithRolesById(@Param("id") UUID id);

    boolean existsByUsernameIgnoreCase(String username);

    long countByOrgId(UUID orgId);
}
