package com.hospital.crm.appointment.application;

import com.hospital.crm.appointment.api.AppointmentDtos;
import com.hospital.crm.appointment.domain.Appointment;
import com.hospital.crm.appointment.domain.AppointmentReminder;
import com.hospital.crm.appointment.domain.AppointmentReminderRepository;
import com.hospital.crm.appointment.domain.AppointmentRepository;
import com.hospital.crm.common.api.ErrorCode;
import com.hospital.crm.common.exception.BusinessException;
import com.hospital.crm.system.audit.AuditService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 预约提醒服务：创建预约时按“开始前 24 小时”生成提醒；
 * 到期提醒由调度或手工触发发送，发送失败记录 retry_count 并按 5/10/20 分钟指数退避重试，
 * 超过 max_retries 后停止自动重试，仍可人工重试。
 */
@Service
public class ReminderService {

    private static final String RESOURCE = "APPT_REMINDER";
    private static final int BATCH_LIMIT = 50;
    private static final long BACKOFF_BASE_SECONDS = 300;
    private static final List<String> TERMINAL_APPT_STATUSES = List.of("CANCELLED", "COMPLETED", "NO_SHOW");

    private static final Logger log = LoggerFactory.getLogger(ReminderService.class);

    private final AppointmentReminderRepository reminderRepository;
    private final AppointmentRepository appointmentRepository;
    private final AppointmentNotifier notifier;
    private final AuditService auditService;

    public ReminderService(AppointmentReminderRepository reminderRepository,
                           AppointmentRepository appointmentRepository,
                           AppointmentNotifier notifier,
                           AuditService auditService) {
        this.reminderRepository = reminderRepository;
        this.appointmentRepository = appointmentRepository;
        this.notifier = notifier;
        this.auditService = auditService;
    }

    /**
     * 预约创建后生成提醒；开始前 24 小时若已过则立即到期。
     */
    @Transactional
    public AppointmentReminder scheduleFor(Appointment appointment, Instant slotStartAt) {
        AppointmentReminder reminder = new AppointmentReminder();
        reminder.setAppointmentId(appointment.getId());
        reminder.setChannel("sms");
        Instant planned = slotStartAt.minus(24, ChronoUnit.HOURS);
        reminder.setPlannedAt(planned.isBefore(Instant.now()) ? Instant.now() : planned);
        reminder.setStatus("PENDING");
        return reminderRepository.save(reminder);
    }

    /**
     * 改期后同步未发送提醒的计划时间。
     */
    @Transactional
    public void reschedulePlan(UUID appointmentId, Instant newSlotStartAt) {
        Instant planned = newSlotStartAt.minus(24, ChronoUnit.HOURS);
        Instant floor = Instant.now();
        for (AppointmentReminder reminder : reminderRepository
                .findByAppointmentIdAndStatusIn(appointmentId, List.of("PENDING"))) {
            reminder.setPlannedAt(planned.isBefore(floor) ? floor : planned);
            reminderRepository.save(reminder);
        }
    }

    /**
     * 取消预约时撤销未发送的提醒。
     */
    @Transactional
    public void cancelPending(UUID appointmentId) {
        for (AppointmentReminder reminder : reminderRepository
                .findByAppointmentIdAndStatusIn(appointmentId, List.of("PENDING"))) {
            reminder.setStatus("CANCELLED");
            reminderRepository.save(reminder);
        }
    }

    @Transactional(readOnly = true)
    public List<AppointmentReminder> listForAppointment(UUID appointmentId) {
        return reminderRepository.findByAppointmentIdOrderByPlannedAtAsc(appointmentId);
    }

    /**
     * 扫描并发送到期提醒；单条失败不影响其余提醒。
     */
    @Transactional
    public AppointmentDtos.DispatchResult dispatchDue() {
        Instant now = Instant.now();
        List<AppointmentReminder> due = reminderRepository.findDue(now, PageRequest.of(0, BATCH_LIMIT));
        int sent = 0;
        int failed = 0;
        for (AppointmentReminder reminder : due) {
            Appointment appointment = appointmentRepository.findById(reminder.getAppointmentId()).orElse(null);
            if (appointment == null) {
                reminder.setStatus("CANCELLED");
                reminderRepository.save(reminder);
                continue;
            }
            if (TERMINAL_APPT_STATUSES.contains(appointment.getStatus())) {
                reminder.setStatus("CANCELLED");
                reminderRepository.save(reminder);
                continue;
            }
            if (attempt(reminder, appointment, now)) {
                sent++;
            } else {
                failed++;
            }
        }
        if (!due.isEmpty()) {
            Map<String, Object> audit = new LinkedHashMap<>();
            audit.put("scanned", due.size());
            audit.put("sent", sent);
            audit.put("failed", failed);
            auditService.record("REMINDER_DISPATCH", RESOURCE, "batch", audit);
        }
        return new AppointmentDtos.DispatchResult(due.size(), sent, failed, now);
    }

    /**
     * 人工重试单条提醒（含已停止自动重试的提醒）。
     */
    @Transactional
    public AppointmentDtos.ReminderView retry(UUID reminderId) {
        AppointmentReminder reminder = reminderRepository.findById(reminderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "提醒不存在"));
        if ("SENT".equals(reminder.getStatus()) || "CANCELLED".equals(reminder.getStatus())) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_FAILED,
                    "仅待发送或发送失败的提醒可以重试，当前状态：" + reminder.getStatus());
        }
        Appointment appointment = appointmentRepository.findById(reminder.getAppointmentId())
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "预约不存在"));
        Instant now = Instant.now();
        boolean success = attempt(reminder, appointment, now);

        Map<String, Object> audit = new LinkedHashMap<>();
        audit.put("appointmentNo", appointment.getAppointmentNo());
        audit.put("result", success ? "SENT" : "FAILED");
        audit.put("retryCount", reminder.getRetryCount());
        auditService.record("REMINDER_RETRY", RESOURCE, reminderId.toString(), audit);

        return toView(reminder, appointment.getAppointmentNo());
    }

    /**
     * 发送一次并更新状态；返回是否成功。
     */
    private boolean attempt(AppointmentReminder reminder, Appointment appointment, Instant now) {
        try {
            notifier.send(reminder, appointment);
            reminder.setStatus("SENT");
            reminder.setSentAt(now);
            reminder.setLastError(null);
            reminder.setNextRetryAt(null);
            reminderRepository.save(reminder);
            return true;
        } catch (Exception ex) {
            log.warn("预约提醒发送失败 appointmentNo={} retryCount={}: {}",
                    appointment.getAppointmentNo(), reminder.getRetryCount(), ex.getMessage());
            int nextRetryCount = reminder.getRetryCount() + 1;
            reminder.setRetryCount(nextRetryCount);
            reminder.setStatus("FAILED");
            reminder.setLastError(truncate(ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage()));
            if (nextRetryCount >= reminder.getMaxRetries()) {
                reminder.setNextRetryAt(null);
            } else {
                long backoffSeconds = BACKOFF_BASE_SECONDS * (1L << (nextRetryCount - 1));
                reminder.setNextRetryAt(now.plusSeconds(backoffSeconds));
            }
            reminderRepository.save(reminder);
            return false;
        }
    }

    private String truncate(String value) {
        if (value == null) {
            return null;
        }
        return value.length() <= 500 ? value : value.substring(0, 500);
    }

    public AppointmentDtos.ReminderView toView(AppointmentReminder reminder, String appointmentNo) {
        return new AppointmentDtos.ReminderView(
                reminder.getId(),
                reminder.getAppointmentId(),
                appointmentNo,
                reminder.getChannel(),
                reminder.getPlannedAt(),
                reminder.getStatus(),
                reminder.getRetryCount(),
                reminder.getMaxRetries(),
                reminder.getNextRetryAt(),
                reminder.getLastError(),
                reminder.getSentAt());
    }
}
