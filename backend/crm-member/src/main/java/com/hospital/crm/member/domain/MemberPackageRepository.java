package com.hospital.crm.member.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MemberPackageRepository extends JpaRepository<MemberPackage, String> {

    List<MemberPackage> findAllByOrderByCreatedAtAsc();

    List<MemberPackage> findAllByStatusOrderByCreatedAtAsc(String status);
}
