package com.hospital.crm.escort.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EscortOrderRepository extends JpaRepository<EscortOrder, UUID>,
        JpaSpecificationExecutor<EscortOrder> {

    Optional<EscortOrder> findByOrderNo(String orderNo);

    /**
     * 查询同一陪诊师同日处于进行中链路（ASSIGNED/ACCEPTED/IN_PROGRESS）且时间窗重叠的其他订单，
     * 排除本单；重叠条件为 startTime < 本单 endTime 且 endTime > 本单 startTime。
     */
    @Query("select o from EscortOrder o where o.escortId = :escortId and o.serviceDate = :serviceDate "
            + "and o.status in :statuses and o.id <> :excludeOrderId "
            + "and o.startTime < :endTime and o.endTime > :startTime")
    List<EscortOrder> findConflictingOrders(@Param("escortId") UUID escortId,
                                            @Param("serviceDate") LocalDate serviceDate,
                                            @Param("statuses") Collection<String> statuses,
                                            @Param("excludeOrderId") UUID excludeOrderId,
                                            @Param("startTime") LocalTime startTime,
                                            @Param("endTime") LocalTime endTime);

    @Query(value = "select nextval('crm_escort_order_no_seq')", nativeQuery = true)
    Long nextOrderNo();
}
