import { http, type PageResult } from './request'

export interface AppointmentType {
  typeCode: string
  typeName: string
  durationMinutes: number
  cancelCutoffHours: number
  status: string
}

export interface ServiceSlot {
  id: string
  typeCode: string
  typeName: string
  slotDate: string
  startTime: string
  endTime: string
  capacity: number
  bookedCount: number
  remaining: number
  status: string
}

export interface AppointmentSummary {
  id: string
  appointmentNo: string
  customerId: string
  customerName?: string
  typeCode: string
  typeName: string
  slotId: string
  slotDate?: string
  startTime?: string
  endTime?: string
  status: 'BOOKED' | 'CONFIRMED' | 'COMPLETED' | 'CANCELLED' | 'NO_SHOW'
  contactPhoneMasked?: string
  createdAt: string
}

export function fetchAppointmentTypes(): Promise<AppointmentType[]> {
  return http.get<AppointmentType[]>('/appointment-types', { includeDisabled: false })
}

export function fetchSlots(params: {
  typeCode?: string
  dateFrom?: string
  dateTo?: string
  page?: number
  pageSize?: number
}): Promise<PageResult<ServiceSlot>> {
  return http.get<PageResult<ServiceSlot>>('/service-slots', { ...params })
}

export function fetchMyAppointments(customerId: string): Promise<PageResult<AppointmentSummary>> {
  return http.get<PageResult<AppointmentSummary>>('/appointments', {
    customerId,
    page: 1,
    pageSize: 20
  })
}

export function createAppointment(payload: {
  customerId: string
  typeCode: string
  slotId: string
  contactPhone?: string
  remark?: string
}): Promise<AppointmentSummary> {
  return http.post<AppointmentSummary>('/appointments', payload)
}

export function cancelAppointment(appointmentId: string, reason?: string): Promise<AppointmentSummary> {
  return http.put<AppointmentSummary>(`/appointments/${appointmentId}/cancel`, { reason })
}
