package com.hospital.crm.homevisit.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;

public interface HomeVisitOrderRepository extends JpaRepository<HomeVisitOrder, UUID>,
        JpaSpecificationExecutor<HomeVisitOrder> {

    Optional<HomeVisitOrder> findByOrderNo(String orderNo);

    @Query(value = "select nextval('crm_home_visit_order_no_seq')", nativeQuery = true)
    Long nextOrderNo();
}
