package com.hospital.crm.appointment.application;

import com.hospital.crm.appointment.domain.Appointment;
import com.hospital.crm.appointment.domain.AppointmentReminder;

/**
 * 提醒发送通道抽象：短信/微信/电话由医院确认服务商后接入（CRM-017）。
 * 发送失败请抛出异常，由 ReminderService 记录重试次数与退避时间。
 */
public interface AppointmentNotifier {

    void send(AppointmentReminder reminder, Appointment appointment);
}
