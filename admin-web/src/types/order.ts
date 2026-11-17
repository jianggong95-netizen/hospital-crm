// ------------------------------------------------------------------
// 统一订单
// ------------------------------------------------------------------
export type OrderStatus =
  | 'CREATED'
  | 'PAID'
  | 'COMPLETED'
  | 'CANCELLED'
  | 'PARTIAL_REFUNDED'
  | 'REFUNDED'

export interface OrderItemLine {
  id: string
  itemCode: string
  itemName: string
  /** 十进制字符串 */
  quantity: string
  unitPrice: string
  amount: string
}

export interface OrderSummary {
  id: string
  orderNo: string
  customerId: string
  customerName?: string
  orderType: string
  status: OrderStatus
  currency: string
  totalAmount: string
  discountAmount: string
  payableAmount: string
  paidAmount: string
  refundedAmount: string
  sourceChannel?: string
  completedAt?: string
  createdAt: string
  updatedAt?: string
}

export interface OrderDetail {
  summary: OrderSummary
  cancelReason?: string
  items: OrderItemLine[]
  payments: PaymentItem[]
  refunds: RefundItem[]
}

export interface OrderItemPayload {
  itemCode: string
  itemName: string
  quantity: string
  unitPrice: string
}

export interface CreateOrderPayload {
  customerId: string
  orderType: string
  items: OrderItemPayload[]
  discountAmount?: string
  idempotencyKey?: string
  sourceChannel?: string
}

// ------------------------------------------------------------------
// 支付
// ------------------------------------------------------------------
export type PaymentStatus = 'PROCESSING' | 'SUCCESS' | 'FAILED' | 'CLOSED'

export type PaymentChannel = 'wechat' | 'alipay' | 'bank' | 'cash' | 'other'

export interface PaymentItem {
  id: string
  paymentNo: string
  orderId: string
  orderNo?: string
  channel: PaymentChannel
  channelTradeNo?: string
  amount: string
  refundedAmount: string
  status: PaymentStatus
  paidAt?: string
  createdAt: string
}

export interface InitiatePaymentPayload {
  channel: PaymentChannel
  amount: string
  idempotencyKey: string
}

// ------------------------------------------------------------------
// 退款
// ------------------------------------------------------------------
export type RefundStatus = 'PENDING' | 'APPROVED' | 'REJECTED' | 'SUCCESS' | 'FAILED'

export interface RefundItem {
  id: string
  refundNo: string
  paymentId: string
  orderId: string
  amount: string
  reason: string
  status: RefundStatus
  channelRefundNo?: string
  reviewedBy?: string
  reviewedAt?: string
  rejectReason?: string
  createdAt: string
  completedAt?: string
}

export interface RefundApplyPayload {
  amount: string
  reason: string
}

// ------------------------------------------------------------------
// 对账
// ------------------------------------------------------------------
export type ReconciliationStatus = 'RUNNING' | 'MATCHED' | 'DIFF' | 'FAILED'

export type ReconciliationDiffType = 'LOCAL_ONLY' | 'CHANNEL_ONLY' | 'AMOUNT_MISMATCH'

export interface ReconciliationBatch {
  id: string
  batchNo: string
  channel: string
  reconcileDate: string
  localCount: number
  channelCount: number
  localAmount: string
  channelAmount: string
  diffCount: number
  status: ReconciliationStatus
  runBy?: string
  createdAt: string
}

export interface ReconciliationDiff {
  id: string
  diffType: ReconciliationDiffType
  paymentNo?: string
  channelTradeNo?: string
  localAmount?: string
  channelAmount?: string
  handled: boolean
}

export interface ReconciliationDetail {
  batch: ReconciliationBatch
  diffs: ReconciliationDiff[]
}

// ------------------------------------------------------------------
// 发票
// ------------------------------------------------------------------
export type InvoiceStatus = 'APPLIED' | 'ISSUED' | 'VOID'

export type InvoiceType = 'general' | 'special'

export interface InvoiceItem {
  id: string
  invoiceNo: string
  orderId: string
  orderNo?: string
  customerId: string
  title: string
  taxNo?: string
  amount: string
  invoiceType: InvoiceType
  status: InvoiceStatus
  provider?: string
  fileRef?: string
  appliedBy?: string
  appliedAt: string
  issuedAt?: string
  voidReason?: string
}

export interface InvoiceApplyPayload {
  title: string
  taxNo?: string
  amount: string
  invoiceType?: InvoiceType
}

export interface ReconciliationRunPayload {
  channel: string
  reconcileDate: string
}
