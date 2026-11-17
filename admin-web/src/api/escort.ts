import { api } from './http'
import type { PageResponse } from '@/types/system'
import type {
  EscortCreatePayload,
  EscortDetail,
  EscortDetailResponse,
  EscortOrderCreatePayload,
  EscortOrderDetail,
  EscortOrderDetailResponse,
  EscortOrderReview,
  EscortOrderSummary,
  EscortQualification,
  EscortReview,
  EscortSchedule,
  EscortStatus,
  EscortSummary,
  EscortUpdatePayload,
  QualificationCreatePayload,
  ReviewCreatePayload,
  ScheduleCreatePayload,
  ScheduleUpdatePayload
} from '@/types/escort'

export interface EscortQuery {
  keyword?: string
  status?: string
  page?: number
  pageSize?: number
}

export interface EscortScheduleQuery {
  escortId?: string
  dateFrom?: string
  dateTo?: string
  page?: number
  pageSize?: number
}

export interface EscortOrderQuery {
  keyword?: string
  status?: string
  escortId?: string
  customerId?: string
  dateFrom?: string
  dateTo?: string
  page?: number
  pageSize?: number
}

export interface EscortReviewQuery {
  escortId?: string
  page?: number
  pageSize?: number
}

// 后端详情接口返回 { summary, ... } 嵌套结构，这里统一拍平为视图模型
function flattenEscortDetail(resp: EscortDetailResponse): EscortDetail {
  return { ...resp.summary, remark: resp.remark, qualifications: resp.qualifications ?? [] }
}

function flattenOrderDetail(resp: EscortOrderDetailResponse): EscortOrderDetail {
  return {
    ...resp.summary,
    contactPhoneMasked: resp.contactPhoneMasked,
    remark: resp.remark,
    rejectReason: resp.rejectReason,
    cancelReason: resp.cancelReason,
    assignedAt: resp.assignedAt,
    acceptedAt: resp.acceptedAt,
    review: resp.review
  }
}

// ------------------------------------------------------------------
// 陪诊师档案与资质
// ------------------------------------------------------------------
export function fetchEscorts(query: EscortQuery = {}): Promise<PageResponse<EscortSummary>> {
  return api.get<PageResponse<EscortSummary>>('/escorts', query)
}

export function createEscort(payload: EscortCreatePayload): Promise<EscortSummary> {
  return api.post<EscortDetailResponse>('/escorts', payload).then(r => r.summary)
}

export function fetchEscort(escortId: string): Promise<EscortDetail> {
  return api.get<EscortDetailResponse>(`/escorts/${escortId}`).then(flattenEscortDetail)
}

export function updateEscort(escortId: string, payload: EscortUpdatePayload): Promise<EscortSummary> {
  return api.put<EscortDetailResponse>(`/escorts/${escortId}`, payload).then(r => r.summary)
}

export function changeEscortStatus(escortId: string, status: EscortStatus): Promise<EscortSummary> {
  return api.put<EscortDetailResponse>(`/escorts/${escortId}/status`, { status }).then(r => r.summary)
}

export function addQualification(
  escortId: string,
  payload: QualificationCreatePayload
): Promise<EscortQualification> {
  return api.post<EscortQualification>(`/escorts/${escortId}/qualifications`, payload)
}

export function revokeQualification(escortId: string, qualificationId: string): Promise<void> {
  return api.put<void>(`/escorts/${escortId}/qualifications/${qualificationId}/revoke`)
}

// ------------------------------------------------------------------
// 排班
// ------------------------------------------------------------------
export function fetchSchedules(query: EscortScheduleQuery = {}): Promise<PageResponse<EscortSchedule>> {
  return api.get<PageResponse<EscortSchedule>>('/escort-schedules', query)
}

export function createSchedule(payload: ScheduleCreatePayload): Promise<EscortSchedule> {
  return api.post<EscortSchedule>('/escort-schedules', payload)
}

export function updateSchedule(scheduleId: string, payload: ScheduleUpdatePayload): Promise<EscortSchedule> {
  return api.put<EscortSchedule>(`/escort-schedules/${scheduleId}`, payload)
}

export function deleteSchedule(scheduleId: string): Promise<void> {
  return api.del<void>(`/escort-schedules/${scheduleId}`)
}

// ------------------------------------------------------------------
// 陪诊订单
// ------------------------------------------------------------------
export function fetchEscortOrders(query: EscortOrderQuery = {}): Promise<PageResponse<EscortOrderSummary>> {
  return api.get<PageResponse<EscortOrderSummary>>('/escort-orders', query)
}

export function createEscortOrder(payload: EscortOrderCreatePayload): Promise<EscortOrderSummary> {
  return api.post<EscortOrderSummary>('/escort-orders', payload)
}

export function fetchEscortOrder(orderId: string): Promise<EscortOrderDetail> {
  return api.get<EscortOrderDetailResponse>(`/escort-orders/${orderId}`).then(flattenOrderDetail)
}

export function dispatchEscortOrder(orderId: string, escortId: string): Promise<EscortOrderSummary> {
  return api.put<EscortOrderSummary>(`/escort-orders/${orderId}/dispatch`, { escortId })
}

export function acceptEscortOrder(orderId: string): Promise<EscortOrderSummary> {
  return api.put<EscortOrderSummary>(`/escort-orders/${orderId}/accept`)
}

export function rejectEscortOrder(orderId: string, reason: string): Promise<EscortOrderSummary> {
  return api.put<EscortOrderSummary>(`/escort-orders/${orderId}/reject`, { reason })
}

export function startEscortOrder(orderId: string): Promise<EscortOrderSummary> {
  return api.put<EscortOrderSummary>(`/escort-orders/${orderId}/start`)
}

export function finishEscortOrder(orderId: string, remark?: string): Promise<EscortOrderSummary> {
  return api.put<EscortOrderSummary>(`/escort-orders/${orderId}/finish`, { remark })
}

export function cancelEscortOrder(orderId: string, reason: string): Promise<EscortOrderSummary> {
  return api.put<EscortOrderSummary>(`/escort-orders/${orderId}/cancel`, { reason })
}

export function reviewEscortOrder(orderId: string, payload: ReviewCreatePayload): Promise<EscortOrderReview> {
  return api.post<EscortOrderReview>(`/escort-orders/${orderId}/review`, payload)
}

// ------------------------------------------------------------------
// 评价
// ------------------------------------------------------------------
export function fetchEscortReviews(query: EscortReviewQuery = {}): Promise<PageResponse<EscortReview>> {
  return api.get<PageResponse<EscortReview>>('/escort-reviews', query)
}
