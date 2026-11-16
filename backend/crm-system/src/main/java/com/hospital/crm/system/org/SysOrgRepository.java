package com.hospital.crm.system.org;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SysOrgRepository extends JpaRepository<SysOrg, UUID> {

    Optional<SysOrg> findByOrgCode(String orgCode);

    boolean existsByOrgCode(String orgCode);

    boolean existsByParentId(UUID parentId);

    List<SysOrg> findAllByOrderBySortOrderAscOrgNameAsc();

    @Query("select o.id from SysOrg o where o.orgPath like concat(:prefix, '%')")
    List<UUID> findIdsByOrgPathPrefix(@Param("prefix") String prefix);
}
