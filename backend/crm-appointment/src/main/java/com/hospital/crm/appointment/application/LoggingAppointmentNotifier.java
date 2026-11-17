package com.hospital.crm.appointment.application;

import com.hospital.crm.appointment.domain.Appointment;
import com.hospital.crm.appointment.domain.AppointmentReminder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 默认提醒通道：仅记录日志，用于尚未接入短信/微信服务商的环境。
 * 接入真实通道后替换为该通道的 AppointmentNotifier 实现即可，重试机制不变。
 */
@Component
public class LoggingAppointmentNotifier implements AppointmentNotifier {

    private static final Logger log = LoggerFactory.getLogger(LoggingAppointmentNotifier.class);

    @Override
    public void send(AppointmentReminder reminder, Appointment appointment) {
        log.info("发送预约提醒 channel={} appointmentNo={} plannedAt={}",
                reminder.getChannel(), appointment.getAppointmentNo(), reminder.getPlannedAt());
    }
}
