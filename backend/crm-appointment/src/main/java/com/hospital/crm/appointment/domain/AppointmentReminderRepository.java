package com.hospital.crm.appointment.domain;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface AppointmentReminderRepository extends JpaRepository<AppointmentReminder, UUID> {

    List<AppointmentReminder> findByAppointmentIdOrderByPlannedAtAsc(UUID appointmentId);

    List<AppointmentReminder> findByAppointmentIdAndStatusIn(UUID appointmentId, Collection<String> statuses);

    /**
     * 到期可发送的提醒：PENDING 到期，或 FAILED 且未超重试上限并到达重试时间。
     */
    @Query("select r from AppointmentReminder r where r.plannedAt <= :now and ("
            + "r.status = 'PENDING' "
            + "or (r.status = 'FAILED' and r.retryCount < r.maxRetries "
            + "and (r.nextRetryAt is null or r.nextRetryAt <= :now))) "
            + "order by r.plannedAt asc")
    List<AppointmentReminder> findDue(@Param("now") Instant now, Pageable pageable);
}
