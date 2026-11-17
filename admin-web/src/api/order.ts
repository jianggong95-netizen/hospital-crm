import { api } from './http'
import type { PageResponse } from '@/types/system'
import type {
  CreateOrderPayload,
  InitiatePaymentPayload,
  InvoiceApplyPayload,
  InvoiceItem,
  OrderDetail,
  OrderSummary,
  PaymentItem,
  ReconciliationDetail,
  ReconciliationRunPayload,
  ReconciliationBatch,
  RefundApplyPayload,
  RefundItem
} from '@/types/order'

export interface OrderQuery {
  keyword?: string
  status?: string
  customerId?: string
  dateFrom?: string
  dateTo?: string
  page?: number
  pageSize?: number
}

export interface PaymentQuery {
  keyword?: string
  status?: string
  channel?: string
  orderId?: string
  page?: number
  pageSize?: number
}

export interface RefundQuery {
  status?: string
  orderId?: string
  page?: number
  pageSize?: number
}

export interface ReconciliationQuery {
  channel?: string
  status?: string
  page?: number
  pageSize?: number
}

export interface InvoiceQuery {
  orderId?: string
  status?: string
  page?: number
  pageSize?: number
}

// ------------------------------------------------------------------
// 订单
// ------------------------------------------------------------------
export function fetchOrders(query: OrderQuery): Promise<PageResponse<OrderSummary>> {
  return api.get<PageResponse<OrderSummary>>('/orders', query)
}

export function fetchOrderDetail(orderId: string): Promise<OrderDetail> {
  return api.get<OrderDetail>(`/orders/${orderId}`)
}

export function createOrder(payload: CreateOrderPayload): Promise<OrderSummary> {
  return api.post<OrderSummary>('/orders', payload)
}

export function cancelOrder(orderId: string, reason: string): Promise<OrderSummary> {
  return api.put<OrderSummary>(`/orders/${orderId}/cancel`, { reason })
}

export function completeOrder(orderId: string): Promise<OrderSummary> {
  return api.put<OrderSummary>(`/orders/${orderId}/complete`)
}

// ------------------------------------------------------------------
// 支付
// ------------------------------------------------------------------
export function initiatePayment(orderId: string, payload: InitiatePaymentPayload): Promise<PaymentItem> {
  return api.post<PaymentItem>(`/orders/${orderId}/payments`, payload)
}

export function fetchPayments(query: PaymentQuery): Promise<PageResponse<PaymentItem>> {
  return api.get<PageResponse<PaymentItem>>('/payments', query)
}

export function fetchPayment(paymentId: string): Promise<PaymentItem> {
  return api.get<PaymentItem>(`/payments/${paymentId}`)
}

// ------------------------------------------------------------------
// 退款
// ------------------------------------------------------------------
export function applyRefund(paymentId: string, payload: RefundApplyPayload): Promise<RefundItem> {
  return api.post<RefundItem>(`/payments/${paymentId}/refunds`, payload)
}

export function fetchRefunds(query: RefundQuery): Promise<PageResponse<RefundItem>> {
  return api.get<PageResponse<RefundItem>>('/refunds', query)
}

export function fetchRefund(refundId: string): Promise<RefundItem> {
  return api.get<RefundItem>(`/refunds/${refundId}`)
}

export function approveRefund(refundId: string): Promise<RefundItem> {
  return api.put<RefundItem>(`/refunds/${refundId}/approve`)
}

export function rejectRefund(refundId: string, reason: string): Promise<RefundItem> {
  return api.put<RefundItem>(`/refunds/${refundId}/reject`, { reason })
}

// ------------------------------------------------------------------
// 对账
// ------------------------------------------------------------------
export function runReconciliation(payload: ReconciliationRunPayload): Promise<ReconciliationDetail> {
  return api.post<ReconciliationDetail>('/reconciliations', payload)
}

export function fetchReconciliations(query: ReconciliationQuery): Promise<PageResponse<ReconciliationBatch>> {
  return api.get<PageResponse<ReconciliationBatch>>('/reconciliations', query)
}

export function fetchReconciliationDiffs(batchId: string): Promise<ReconciliationDetail> {
  return api.get<ReconciliationDetail>(`/reconciliations/${batchId}/diffs`)
}

// ------------------------------------------------------------------
// 发票
// ------------------------------------------------------------------
export function applyInvoice(orderId: string, payload: InvoiceApplyPayload): Promise<InvoiceItem> {
  return api.post<InvoiceItem>(`/invoices/orders/${orderId}`, payload)
}

export function fetchInvoices(query: InvoiceQuery): Promise<PageResponse<InvoiceItem>> {
  return api.get<PageResponse<InvoiceItem>>('/invoices', query)
}

export function issueInvoice(invoiceId: string): Promise<InvoiceItem> {
  return api.put<InvoiceItem>(`/invoices/${invoiceId}/issue`)
}

export function voidInvoice(invoiceId: string, reason: string): Promise<InvoiceItem> {
  return api.put<InvoiceItem>(`/invoices/${invoiceId}/void`, { reason })
}
