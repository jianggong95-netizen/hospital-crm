package com.hospital.crm.homevisit.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface HomeVisitExceptionRepository extends JpaRepository<HomeVisitException, UUID>,
        JpaSpecificationExecutor<HomeVisitException> {

    List<HomeVisitException> findByOrderIdOrderByCreatedAtDesc(UUID orderId);

    /**
     * 统计订单指定状态的异常数量，用于完成订单前校验未解决异常。
     */
    long countByOrderIdAndStatusIn(UUID orderId, Collection<String> statuses);
}
