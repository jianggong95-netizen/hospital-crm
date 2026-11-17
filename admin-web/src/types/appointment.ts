export type AppointmentStatus = 'BOOKED' | 'CONFIRMED' | 'COMPLETED' | 'CANCELLED' | 'NO_SHOW'
export type ReminderStatus = 'PENDING' | 'SENT' | 'FAILED' | 'CANCELLED'

export interface AppointmentType {
  id: string
  typeCode: string
  typeName: string
  durationMinutes: number
  cancelCutoffHours: number
  status: string
  remark?: string
  createdAt: string
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
  remark?: string
  updatedAt: string
}

export interface AppointmentSummary {
  id: string
  appointmentNo: string
  customerId: string
  customerNo?: string
  customerName?: string
  typeCode: string
  typeName: string
  slotId: string
  slotDate?: string
  startTime?: string
  endTime?: string
  status: AppointmentStatus
  contactPhoneMasked?: string
  createdAt: string
}

export interface AppointmentReminder {
  id: string
  appointmentId: string
  appointmentNo?: string
  channel: string
  plannedAt: string
  status: ReminderStatus
  retryCount: number
  maxRetries: number
  nextRetryAt?: string
  lastError?: string
  sentAt?: string
}

export interface AppointmentDetail {
  summary: AppointmentSummary
  remark?: string
  cancelledAt?: string
  cancelReason?: string
  completedAt?: string
  noShowAt?: string
  noShowCount: number
  reminders: AppointmentReminder[]
}

export interface NoShowRecord {
  id: string
  appointmentId: string
  appointmentNo?: string
  customerId: string
  customerNo?: string
  customerName?: string
  appointmentAt: string
  reason?: string
  recordedByName?: string
  createdAt: string
}

export interface DispatchResult {
  scanned: number
  sent: number
  failed: number
  dispatchedAt: string
}

export interface AppointmentCreatePayload {
  customerId: string
  typeCode: string
  slotId: string
  contactPhone?: string
  remark?: string
}
