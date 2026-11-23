import { http, type PageResult } from './request'

export interface EscortOrderSummary {
  id: string
  orderNo: string
  customerId: string
  customerName: string
  escortId?: string
  escortName?: string
  serviceDate: string
  startTime: string
  endTime: string
  area: string
  status: string
  createdAt: string
  updatedAt: string
}

export function fetchMyEscortOrders(customerId: string): Promise<PageResult<EscortOrderSummary>> {
  return http.get<PageResult<EscortOrderSummary>>('/escort-orders', {
    customerId,
    page: 1,
    pageSize: 20
  })
}

export function createEscortOrder(payload: {
  customerId: string
  serviceDate: string
  startTime: string
  endTime: string
  area: string
  contactPhone?: string
  remark?: string
}): Promise<EscortOrderSummary> {
  return http.post<EscortOrderSummary>('/escort-orders', payload)
}

export function cancelEscortOrder(orderId: string, reason: string): Promise<EscortOrderSummary> {
  return http.put<EscortOrderSummary>(`/escort-orders/${orderId}/cancel`, { reason })
}
