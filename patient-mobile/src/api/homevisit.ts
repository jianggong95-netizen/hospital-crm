import { http, type PageResult } from './request'

export interface HomeServiceItem {
  id: string
  serviceCode: string
  serviceName: string
  category: string
  requiredQualType?: string
  durationMinutes: number
  price: string
  status: string
  description?: string
}

export interface HomeAddress {
  id: string
  customerId: string
  address: string
  contactName: string
  contactPhoneMasked?: string
  isDefault: boolean
  status: string
  remark?: string
}

export interface HomeVisitOrderSummary {
  id: string
  orderNo: string
  serviceCode: string
  serviceName?: string
  customerId: string
  customerName?: string
  addressId: string
  scheduledDate: string
  startTime: string
  endTime: string
  status: string
  staffUserId?: string
  staffName?: string
  actualStartAt?: string
  actualEndAt?: string
  durationMinutes?: number
  createdAt: string
  updatedAt: string
}

export interface AddressSnapshot {
  address: string
  contactName: string
  contactPhoneMasked?: string
}

export interface ExecutionView {
  id: string
  visitAt: string
  content: string
  recordedBy?: string
}

export interface ExceptionView {
  id: string
  orderId: string
  orderNo?: string
  level: string
  category: string
  description: string
  status: string
  reportedBy?: string
  escalatedAt?: string
  resolvedAt?: string
}

/** 后端订单详情返回 { summary, ... } 嵌套结构，这里拍平为视图模型 */
export interface HomeVisitOrderDetail extends HomeVisitOrderSummary {
  remark?: string
  reviewReason?: string
  cancelReason?: string
  assignedAt?: string
  acceptedAt?: string
  address?: AddressSnapshot
  executions: ExecutionView[]
  exceptions: ExceptionView[]
  price?: string
  requiredQualType?: string
}

interface HomeVisitOrderDetailResponse {
  summary: HomeVisitOrderSummary
  remark?: string
  reviewReason?: string
  cancelReason?: string
  assignedAt?: string
  acceptedAt?: string
  address?: AddressSnapshot
  executions?: ExecutionView[]
  exceptions?: ExceptionView[]
  price?: string
  requiredQualType?: string
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

export function fetchPublishedHomeServices(): Promise<PageResult<HomeServiceItem>> {
  return http.get<PageResult<HomeServiceItem>>('/home-services', {
    status: 'published',
    page: 1,
    pageSize: 50
  })
}

export function fetchHomeAddresses(customerId: string): Promise<HomeAddress[]> {
  return http.get<HomeAddress[]>('/home-addresses', { customerId }).then((list) => list || [])
}

export function createHomeAddress(payload: {
  customerId: string
  address: string
  contactName: string
  contactPhone?: string
  isDefault?: boolean
  remark?: string
}): Promise<HomeAddress> {
  return http.post<HomeAddress>('/home-addresses', payload)
}

export function fetchHomeVisitOrders(query: HomeVisitOrderQuery): Promise<PageResult<HomeVisitOrderSummary>> {
  return http.get<PageResult<HomeVisitOrderSummary>>('/home-visit-orders', {
    page: 1,
    pageSize: 50,
    ...query
  })
}

export function createHomeVisitOrder(payload: {
  serviceCode: string
  customerId: string
  addressId: string
  scheduledDate: string
  startTime: string
  endTime: string
  remark?: string
}): Promise<HomeVisitOrderSummary> {
  return http.post<HomeVisitOrderSummary>('/home-visit-orders', payload)
}

export function fetchHomeVisitOrder(orderId: string): Promise<HomeVisitOrderDetail> {
  return http.get<HomeVisitOrderDetailResponse>(`/home-visit-orders/${orderId}`).then((resp) => ({
    ...resp.summary,
    remark: resp.remark,
    reviewReason: resp.reviewReason,
    cancelReason: resp.cancelReason,
    assignedAt: resp.assignedAt,
    acceptedAt: resp.acceptedAt,
    address: resp.address,
    executions: resp.executions || [],
    exceptions: resp.exceptions || [],
    price: resp.price,
    requiredQualType: resp.requiredQualType
  }))
}

export function acceptHomeVisitOrder(orderId: string): Promise<HomeVisitOrderSummary> {
  return http.put<HomeVisitOrderSummary>(`/home-visit-orders/${orderId}/accept`, {})
}

export function rejectHomeVisitOrder(orderId: string, reason: string): Promise<HomeVisitOrderSummary> {
  return http.put<HomeVisitOrderSummary>(`/home-visit-orders/${orderId}/reject`, { reason })
}

export function startHomeVisitOrder(orderId: string): Promise<HomeVisitOrderSummary> {
  return http.put<HomeVisitOrderSummary>(`/home-visit-orders/${orderId}/start`, {})
}

export function addHomeVisitExecution(orderId: string, content: string): Promise<ExecutionView> {
  return http.post<ExecutionView>(`/home-visit-orders/${orderId}/executions`, { content })
}

export function reportHomeVisitException(
  orderId: string,
  payload: { level: string; category: string; description: string }
): Promise<ExceptionView> {
  return http.post<ExceptionView>(`/home-visit-orders/${orderId}/exceptions`, payload)
}

export function finishHomeVisitOrder(orderId: string, remark?: string): Promise<HomeVisitOrderSummary> {
  return http.put<HomeVisitOrderSummary>(`/home-visit-orders/${orderId}/finish`, { remark })
}

export function cancelHomeVisitOrder(orderId: string, reason: string): Promise<HomeVisitOrderSummary> {
  return http.put<HomeVisitOrderSummary>(`/home-visit-orders/${orderId}/cancel`, { reason })
}
