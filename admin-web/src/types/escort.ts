export type EscortStatus = 'active' | 'disabled'

export type EscortOrderStatus =
  | 'PENDING'
  | 'ASSIGNED'
  | 'ACCEPTED'
  | 'IN_PROGRESS'
  | 'COMPLETED'
  | 'CANCELLED'

export interface EscortQualification {
  id: string
  qualType: string
  certNo: string
  issuedAt: string
  expiresAt?: string
  status: string
  expired: boolean
}

export interface EscortSummary {
  id: string
  escortNo: string
  name: string
  gender: string
  phoneMasked: string
  serviceAreas: string[]
  status: EscortStatus
  avgRating?: number
  reviewCount: number
  createdAt: string
  updatedAt: string
}

export interface EscortDetail extends EscortSummary {
  remark?: string
  qualifications: EscortQualification[]
}

/** 后端 GET/POST /escorts 返回的嵌套详情结构 */
export interface EscortDetailResponse {
  summary: EscortSummary
  remark?: string
  qualifications: EscortQualification[]
}

export interface EscortCreatePayload {
  name: string
  gender: string
  phone?: string
  serviceAreas: string[]
  remark?: string
}

export interface EscortUpdatePayload {
  name?: string
  gender?: string
  phone?: string
  serviceAreas?: string[]
  remark?: string
}

export interface QualificationCreatePayload {
  qualType: string
  certNo: string
  issuedAt: string
  expiresAt?: string
}

export interface EscortSchedule {
  id: string
  escortId: string
  escortName: string
  workDate: string
  startTime: string
  endTime: string
}

export interface ScheduleCreatePayload {
  escortId: string
  workDate: string
  startTime: string
  endTime: string
}

export interface ScheduleUpdatePayload {
  workDate: string
  startTime: string
  endTime: string
}

export interface EscortOrderReview {
  id: string
  rating: number
  content: string
  createdBy?: string
  createdAt: string
}

export interface EscortOrderSummary {
  id: string
  orderNo: string
  customerId: string
  customerName: string
  appointmentId?: string
  escortId?: string
  escortName?: string
  serviceDate: string
  startTime: string
  endTime: string
  area: string
  status: EscortOrderStatus
  actualStartAt?: string
  actualEndAt?: string
  durationMinutes?: number
  createdAt: string
  updatedAt: string
}

export interface EscortOrderDetail extends EscortOrderSummary {
  contactPhoneMasked?: string
  remark?: string
  rejectReason?: string
  cancelReason?: string
  assignedAt?: string
  acceptedAt?: string
  review?: EscortOrderReview
}

/** 后端 GET /escort-orders/{id} 返回的嵌套详情结构 */
export interface EscortOrderDetailResponse {
  summary: EscortOrderSummary
  contactPhoneMasked?: string
  remark?: string
  rejectReason?: string
  cancelReason?: string
  assignedAt?: string
  acceptedAt?: string
  review?: EscortOrderReview
}

export interface EscortOrderCreatePayload {
  customerId: string
  appointmentId?: string
  serviceDate: string
  startTime: string
  endTime: string
  area: string
  contactPhone?: string
  remark?: string
}

export interface EscortReview {
  id: string
  orderNo: string
  escortId: string
  escortName: string
  rating: number
  content: string
  createdBy?: string
  createdAt: string
}

export interface ReviewCreatePayload {
  rating: number
  content?: string
}
