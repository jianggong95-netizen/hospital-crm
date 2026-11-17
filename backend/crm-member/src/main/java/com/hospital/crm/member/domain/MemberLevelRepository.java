package com.hospital.crm.member.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MemberLevelRepository extends JpaRepository<MemberLevel, String> {

    List<MemberLevel> findAllByStatusOrderBySortNoAsc(String status);

    List<MemberLevel> findAllByOrderBySortNoAsc();

    Optional<MemberLevel> findFirstByStatusAndMinPointsLessThanEqualOrderByMinPointsDesc(String status, long points);
}
