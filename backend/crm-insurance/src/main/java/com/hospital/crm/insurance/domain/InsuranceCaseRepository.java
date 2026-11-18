package com.hospital.crm.insurance.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public interface InsuranceCaseRepository
        extends JpaRepository<InsuranceCase, UUID>, JpaSpecificationExecutor<InsuranceCase> {

    /** 案件编号序列：IC + 序列值。 */
    @Query(value = "select nextval('crm_insurance_case_no_seq')", nativeQuery = true)
    long nextCaseNo();

    long countByCompanyIdAndDecidedAtGreaterThanEqualAndDecidedAtLessThan(
            UUID companyId, Instant from, Instant to);

    @Query("select coalesce(sum(c.hospitalReceivable), 0) from InsuranceCase c "
            + "where c.companyId = :companyId and c.decidedAt >= :from and c.decidedAt < :to")
    BigDecimal sumReceivable(@Param("companyId") UUID companyId,
                             @Param("from") Instant from,
                             @Param("to") Instant to);

    @Query("select coalesce(sum(c.actualReceived), 0) from InsuranceCase c "
            + "where c.companyId = :companyId and c.decidedAt >= :from and c.decidedAt < :to")
    BigDecimal sumReceived(@Param("companyId") UUID companyId,
                           @Param("from") Instant from,
                           @Param("to") Instant to);
}
