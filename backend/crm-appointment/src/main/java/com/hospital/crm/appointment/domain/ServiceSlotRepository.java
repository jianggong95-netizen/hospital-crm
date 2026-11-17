package com.hospital.crm.appointment.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

public interface ServiceSlotRepository extends JpaRepository<ServiceSlot, UUID>, JpaSpecificationExecutor<ServiceSlot> {

    boolean existsByTypeCodeAndSlotDateAndStartTime(String typeCode, LocalDate slotDate, LocalTime startTime);

    /**
     * 原子占用一个名额：仅当时段开放且未满时 booked_count+1。
     * 返回 0 表示并发下已满或已关闭，由调用方转换为业务冲突，保证不超卖。
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update ServiceSlot s set s.bookedCount = s.bookedCount + 1, s.updatedAt = :now "
            + "where s.id = :slotId and s.status = 'OPEN' and s.bookedCount < s.capacity")
    int occupy(@Param("slotId") UUID slotId, @Param("now") Instant now);

    /**
     * 释放一个名额（取消/改期时归还容量）。
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update ServiceSlot s set s.bookedCount = case when s.bookedCount > 0 then s.bookedCount - 1 else 0 end, "
            + "s.updatedAt = :now where s.id = :slotId")
    int release(@Param("slotId") UUID slotId, @Param("now") Instant now);
}
