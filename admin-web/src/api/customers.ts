import { api } from './http'
import type {
  CustomerCreatePayload,
  CustomerDetail,
  CustomerSummary,
  CustomerUpdatePayload
} from '@/types/customer'
import type { PageResponse } from '@/types/system'

export interface CustomerQuery {
  keyword?: string
  tagId?: string
  sourceCode?: string
  status?: string
  gender?: string
  page?: number
  pageSize?: number
}

export function fetchCustomers(query: CustomerQuery): Promise<PageResponse<CustomerSummary>> {
  return api.get<PageResponse<CustomerSummary>>('/customers', query)
}

export function fetchCustomer(customerId: string): Promise<CustomerDetail> {
  return api.get<CustomerDetail>(`/customers/${customerId}`)
}

export function createCustomer(payload: CustomerCreatePayload): Promise<CustomerSummary> {
  return api.post<CustomerSummary>('/customers', payload)
}

export function updateCustomer(customerId: string, payload: CustomerUpdatePayload): Promise<CustomerSummary> {
  return api.put<CustomerSummary>(`/customers/${customerId}`, payload)
}

export function changeCustomerStatus(customerId: string, status: 'active' | 'disabled'): Promise<void> {
  return api.patch<void>(`/customers/${customerId}/status`, { status })
}
