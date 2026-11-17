import { api } from './http'
import type { PageResponse } from '@/types/system'
import type {
  AppointmentCreatePayload,
  AppointmentDetail,
  AppointmentReminder,
  AppointmentSummary,
  AppointmentType,
  DispatchResult,
  NoShowRecord,
  ServiceSlot
} from '@/types/appointment'

export interface AppointmentQuery {
  keyword?: string
  status?: string
  typeCode?: string
  customerId?: string
  dateFrom?: string
  dateTo?: string
  page?: number
  pageSize?: number
}

export interface SlotQuery {
  typeCode?: string
  dateFrom?: string
  dateTo?: string
  status?: string
  page?: number
  pageSize?: number
}

export interface NoShowQuery {
  customerId?: string
  dateFrom?: string
  dateTo?: string
  page?: number
  pageSize?: number
}

export function fetchAppointmentTypes(includeDisabled = false): Promise<AppointmentType[]> {
  return api.get<AppointmentType[]>('/appointment-types', { includeDisabled })
}

export function createAppointmentType(payload: {
  typeCode: string
  typeName: string
  durationMinutes?: number
  cancelCutoffHours?: number
  remark?: string
}): Promise<AppointmentType> {
  return api.post<AppointmentType>('/appointment-types', payload)
}

export function updateAppointmentType(
  typeCode: string,
  payload: { typeName: string; durationMinutes?: number; cancelCutoffHours?: number; remark?: string }
): Promise<AppointmentType> {
  return api.put<AppointmentType>(`/appointment-types/${typeCode}`, payload)
}

export function changeAppointmentTypeStatus(typeCode: string, status: string): Promise<AppointmentType> {
  return api.put<AppointmentType>(`/appointment-types/${typeCode}/status`, { status })
}

export function fetchSlots(query: SlotQuery = {}): Promise<PageResponse<ServiceSlot>> {
  return api.get<PageResponse<ServiceSlot>>('/service-slots', query)
}

export function createSlot(payload: {
  typeCode: string
  slotDate: string
  startTime: string
  endTime: string
  capacity: number
  remark?: string
}): Promise<ServiceSlot> {
  return api.post<ServiceSlot>('/service-slots', payload)
}

export function updateSlot(
  slotId: string,
  payload: { slotDate: string; startTime: string; endTime: string; capacity: number; remark?: string }
): Promise<ServiceSlot> {
  return api.put<ServiceSlot>(`/service-slots/${slotId}`, payload)
}

export function changeSlotStatus(slotId: string, status: string): Promise<ServiceSlot> {
  return api.put<ServiceSlot>(`/service-slots/${slotId}/status`, { status })
}

export function fetchAppointments(query: AppointmentQuery = {}): Promise<PageResponse<AppointmentSummary>> {
  return api.get<PageResponse<AppointmentSummary>>('/appointments', query)
}

export function fetchAppointment(appointmentId: string): Promise<AppointmentDetail> {
  return api.get<AppointmentDetail>(`/appointments/${appointmentId}`)
}

export function createAppointment(payload: AppointmentCreatePayload): Promise<AppointmentSummary> {
  return api.post<AppointmentSummary>('/appointments', payload)
}

export function rescheduleAppointment(
  appointmentId: string,
  payload: { slotId: string; remark?: string }
): Promise<AppointmentSummary> {
  return api.put<AppointmentSummary>(`/appointments/${appointmentId}/reschedule`, payload)
}

export function cancelAppointment(appointmentId: string, reason?: string): Promise<AppointmentSummary> {
  return api.put<AppointmentSummary>(`/appointments/${appointmentId}/cancel`, { reason })
}

export function changeAppointmentStatus(
  appointmentId: string,
  status: string,
  reason?: string
): Promise<AppointmentSummary> {
  return api.put<AppointmentSummary>(`/appointments/${appointmentId}/status`, { status, reason })
}

export function dispatchReminders(): Promise<DispatchResult> {
  return api.post<DispatchResult>('/appointment-reminders/dispatch')
}

export function retryReminder(reminderId: string): Promise<AppointmentReminder> {
  return api.post<AppointmentReminder>(`/appointment-reminders/${reminderId}/retry`)
}

export function fetchNoShows(query: NoShowQuery = {}): Promise<PageResponse<NoShowRecord>> {
  return api.get<PageResponse<NoShowRecord>>('/no-shows', query)
}
