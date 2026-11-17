import { api } from './http'
import type { PageResponse } from '@/types/system'
import type {
  ExceptionCreatePayload,
  ExecutionCreatePayload,
  HomeAddress,
  HomeAddressCreatePayload,
  HomeAddressUpdatePayload,
  HomeServiceCreatePayload,
  HomeServiceItem,
  HomeServiceReviewPayload,
  HomeServiceUpdatePayload,
  HomeStaffQualification,
  HomeVisitException,
  HomeVisitExceptionBoardItem,
  HomeVisitExecution,
  HomeVisitOrderCreatePayload,
  HomeVisitOrderDetail,
  HomeVisitOrderDetailResponse,
  HomeVisitOrderReviewPayload,
  HomeVisitOrderSummary,
  StaffQualificationCreatePayload
} from '@/types/homevisit'

export interface HomeServiceQuery {
  keyword?: string
  category?: string
  status?: string
  page?: number
  pageSize?: number
}

export interface HomeStaffQualQuery {
  userId?: string
  qualType?: string
  page?: number
  pageSize?: number
}

export interface HomeVisitOrderQuery {
  keyword?: string
  status?: string
  serviceCode?: string
  customerId?: string
  staffUserId?: string
  dateFrom?: string
  dateTo?: string
  page?: number
  pageSize?: number
}

export interface HomeVisitExceptionQuery {
  status?: string
  level?: string
  page?: number
  pageSize?: number
}

// 后端订单详情接口返回 { summary, ... } 嵌套结构，这里统一拍平为视图模型
function flattenOrderDetail(resp: HomeVisitOrderDetailResponse): HomeVisitOrderDetail {
  return {
    ...resp.summary,
    remark: resp.remark,
    reviewReason: resp.reviewReason,
    cancelReason: resp.cancelReason,
    assignedAt: resp.assignedAt,
    acceptedAt: resp.acceptedAt,
    address: resp.address,
    executions: resp.executions ?? [],
    exceptions: resp.exceptions ?? [],
    price: resp.price,
    requiredQualType: resp.requiredQualType
  }
}

// ------------------------------------------------------------------
// 服务目录
// ------------------------------------------------------------------
export function fetchHomeServices(query: HomeServiceQuery = {}): Promise<PageResponse<HomeServiceItem>> {
  return api.get<PageResponse<HomeServiceItem>>('/home-services', query)
}

export function createHomeService(payload: HomeServiceCreatePayload): Promise<HomeServiceItem> {
  return api.post<HomeServiceItem>('/home-services', payload)
}

export function updateHomeService(
  serviceCode: string,
  payload: HomeServiceUpdatePayload
): Promise<HomeServiceItem> {
  return api.put<HomeServiceItem>(`/home-services/${serviceCode}`, payload)
}

export function submitHomeService(serviceCode: string): Promise<HomeServiceItem> {
  return api.put<HomeServiceItem>(`/home-services/${serviceCode}/submit`)
}

export function reviewHomeService(
  serviceCode: string,
  payload: HomeServiceReviewPayload
): Promise<HomeServiceItem> {
  return api.put<HomeServiceItem>(`/home-services/${serviceCode}/review`, payload)
}

export function changeHomeServiceStatus(
  serviceCode: string,
  status: 'published' | 'disabled'
): Promise<HomeServiceItem> {
  return api.put<HomeServiceItem>(`/home-services/${serviceCode}/status`, { status })
}

// ------------------------------------------------------------------
// 客户地址
// ------------------------------------------------------------------
export function fetchHomeAddresses(customerId: string): Promise<HomeAddress[]> {
  return api.get<HomeAddress[]>('/home-addresses', { customerId }).then(r => r ?? [])
}

export function createHomeAddress(payload: HomeAddressCreatePayload): Promise<HomeAddress> {
  return api.post<HomeAddress>('/home-addresses', payload)
}

export function updateHomeAddress(addressId: string, payload: HomeAddressUpdatePayload): Promise<HomeAddress> {
  return api.put<HomeAddress>(`/home-addresses/${addressId}`, payload)
}

export function changeHomeAddressStatus(addressId: string, status: 'active' | 'disabled'): Promise<HomeAddress> {
  return api.put<HomeAddress>(`/home-addresses/${addressId}/status`, { status })
}

// ------------------------------------------------------------------
// 人员资质
// ------------------------------------------------------------------
export function fetchStaffQualifications(
  query: HomeStaffQualQuery = {}
): Promise<PageResponse<HomeStaffQualification>> {
  return api.get<PageResponse<HomeStaffQualification>>('/home-staff-qualifications', query)
}

export function createStaffQualification(payload: StaffQualificationCreatePayload): Promise<HomeStaffQualification> {
  return api.post<HomeStaffQualification>('/home-staff-qualifications', payload)
}

export function revokeStaffQualification(id: string): Promise<void> {
  return api.put<void>(`/home-staff-qualifications/${id}/revoke`)
}

// ------------------------------------------------------------------
// 上门订单
// ------------------------------------------------------------------
export function fetchHomeVisitOrders(query: HomeVisitOrderQuery = {}): Promise<PageResponse<HomeVisitOrderSummary>> {
  return api.get<PageResponse<HomeVisitOrderSummary>>('/home-visit-orders', query)
}

export function createHomeVisitOrder(payload: HomeVisitOrderCreatePayload): Promise<HomeVisitOrderSummary> {
  return api.post<HomeVisitOrderSummary>('/home-visit-orders', payload)
}

export function fetchHomeVisitOrder(orderId: string): Promise<HomeVisitOrderDetail> {
  return api.get<HomeVisitOrderDetailResponse>(`/home-visit-orders/${orderId}`).then(flattenOrderDetail)
}

export function reviewHomeVisitOrder(
  orderId: string,
  payload: HomeVisitOrderReviewPayload
): Promise<HomeVisitOrderSummary> {
  return api.put<HomeVisitOrderSummary>(`/home-visit-orders/${orderId}/review`, payload)
}

export function dispatchHomeVisitOrder(orderId: string, staffUserId: string): Promise<HomeVisitOrderSummary> {
  return api.put<HomeVisitOrderSummary>(`/home-visit-orders/${orderId}/dispatch`, { staffUserId })
}

export function acceptHomeVisitOrder(orderId: string): Promise<HomeVisitOrderSummary> {
  return api.put<HomeVisitOrderSummary>(`/home-visit-orders/${orderId}/accept`)
}

export function rejectHomeVisitOrder(orderId: string, reason: string): Promise<HomeVisitOrderSummary> {
  return api.put<HomeVisitOrderSummary>(`/home-visit-orders/${orderId}/reject`, { reason })
}

export function startHomeVisitOrder(orderId: string): Promise<HomeVisitOrderSummary> {
  return api.put<HomeVisitOrderSummary>(`/home-visit-orders/${orderId}/start`)
}

export function addOrderExecution(orderId: string, payload: ExecutionCreatePayload): Promise<HomeVisitExecution> {
  return api.post<HomeVisitExecution>(`/home-visit-orders/${orderId}/executions`, payload)
}

export function reportOrderException(orderId: string, payload: ExceptionCreatePayload): Promise<HomeVisitException> {
  return api.post<HomeVisitException>(`/home-visit-orders/${orderId}/exceptions`, payload)
}

export function finishHomeVisitOrder(orderId: string, remark?: string): Promise<HomeVisitOrderSummary> {
  return api.put<HomeVisitOrderSummary>(`/home-visit-orders/${orderId}/finish`, { remark })
}

export function cancelHomeVisitOrder(orderId: string, reason: string): Promise<HomeVisitOrderSummary> {
  return api.put<HomeVisitOrderSummary>(`/home-visit-orders/${orderId}/cancel`, { reason })
}

// ------------------------------------------------------------------
// 异常处置与看板
// ------------------------------------------------------------------
export function escalateHomeVisitException(exceptionId: string, reason: string): Promise<HomeVisitException> {
  return api.put<HomeVisitException>(`/home-visit-exceptions/${exceptionId}/escalate`, { reason })
}

export function resolveHomeVisitException(exceptionId: string, resolution: string): Promise<HomeVisitException> {
  return api.put<HomeVisitException>(`/home-visit-exceptions/${exceptionId}/resolve`, { resolution })
}

export function fetchHomeVisitExceptions(
  query: HomeVisitExceptionQuery = {}
): Promise<PageResponse<HomeVisitExceptionBoardItem>> {
  return api.get<PageResponse<HomeVisitExceptionBoardItem>>('/home-visit-exceptions', query)
}
