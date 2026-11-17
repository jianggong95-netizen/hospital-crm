// ------------------------------------------------------------------
// 服务目录
// ------------------------------------------------------------------
export type HomeServiceCategory = 'medical' | 'non_medical'

export type HomeServiceStatus = 'draft' | 'pending_review' | 'published' | 'rejected' | 'disabled'

export interface HomeServiceItem {
  id: string
  serviceCode: string
  serviceName: string
  category: HomeServiceCategory
  requiredQualType?: string
  durationMinutes: number
  /** 十进制字符串，如 "280.00" */
  price: string
  status: HomeServiceStatus
  description?: string
  reviewReason?: string
  reviewedBy?: string
  reviewedAt?: string
}

export interface HomeServiceCreatePayload {
  serviceCode: string
  serviceName: string
  category: HomeServiceCategory
  requiredQualType?: string
  durationMinutes: number
  price: string
  description?: string
}

export interface HomeServiceUpdatePayload {
  serviceName?: string
  category?: HomeServiceCategory
  requiredQualType?: string
  durationMinutes?: number
  price?: string
  description?: string
}

export interface HomeServiceReviewPayload {
  approved: boolean
  reason?: string
}

// ------------------------------------------------------------------
// 客户地址
// ------------------------------------------------------------------
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

export interface HomeAddressCreatePayload {
  customerId: string
  address: string
  contactName: string
  contactPhone?: string
  isDefault?: boolean
  remark?: string
}

export interface HomeAddressUpdatePayload {
  address?: string
  contactName?: string
  contactPhone?: string
  isDefault?: boolean
  remark?: string
}

// ------------------------------------------------------------------
// 人员资质
// ------------------------------------------------------------------
export type HomeQualType = 'nurse' | 'caregiver' | 'health_manager' | 'doctor' | 'other'

export interface HomeStaffQualification {
  id: string
  userId: string
  username: string
  displayName: string
  qualType: HomeQualType
  certNo: string
  issuedAt: string
  expiresAt?: string
  status: string
  expired: boolean
}

export interface StaffQualificationCreatePayload {
  userId: string
  qualType: HomeQualType
  certNo: string
  issuedAt: string
  expiresAt?: string
}

// ------------------------------------------------------------------
// 上门订单
// ------------------------------------------------------------------
export type HomeVisitOrderStatus =
  | 'PENDING'
  | 'APPROVED'
  | 'REJECTED'
  | 'ASSIGNED'
  | 'ACCEPTED'
  | 'IN_PROGRESS'
  | 'COMPLETED'
  | 'CANCELLED'

export interface HomeVisitOrderSummary {
  id: string
  orderNo: string
  serviceCode: string
  serviceName: string
  customerId: string
  customerName: string
  addressId: string
  scheduledDate: string
  startTime: string
  endTime: string
  status: HomeVisitOrderStatus
  staffUserId?: string
  staffName?: string
  actualStartAt?: string
  actualEndAt?: string
  durationMinutes?: number
  createdAt: string
  updatedAt: string
}

export interface HomeVisitAddressSnapshot {
  address: string
  contactName: string
  contactPhoneMasked?: string
}

export interface HomeVisitExecution {
  id: string
  visitAt: string
  content: string
  recordedBy?: string
}

export type HomeVisitExceptionLevel = 'low' | 'medium' | 'high' | 'critical'

export type HomeVisitExceptionStatus = 'OPEN' | 'ESCALATED' | 'RESOLVED'

export interface HomeVisitException {
  id: string
  level: HomeVisitExceptionLevel
  category: string
  description: string
  status: HomeVisitExceptionStatus
  reportedBy?: string
  escalatedAt?: string
  resolvedAt?: string
}

export interface HomeVisitOrderDetail extends HomeVisitOrderSummary {
  remark?: string
  reviewReason?: string
  cancelReason?: string
  assignedAt?: string
  acceptedAt?: string
  address?: HomeVisitAddressSnapshot
  executions: HomeVisitExecution[]
  exceptions: HomeVisitException[]
  price?: string
  requiredQualType?: string
}

/** 后端 GET /home-visit-orders/{orderId} 返回的嵌套详情结构 */
export interface HomeVisitOrderDetailResponse {
  summary: HomeVisitOrderSummary
  remark?: string
  reviewReason?: string
  cancelReason?: string
  assignedAt?: string
  acceptedAt?: string
  address?: HomeVisitAddressSnapshot
  executions?: HomeVisitExecution[]
  exceptions?: HomeVisitException[]
  price?: string
  serviceName?: string
  requiredQualType?: string
}

export interface HomeVisitOrderCreatePayload {
  serviceCode: string
  customerId: string
  addressId: string
  scheduledDate: string
  startTime: string
  endTime: string
  remark?: string
}

export interface HomeVisitOrderReviewPayload {
  approved: boolean
  reason?: string
}

export interface ExecutionCreatePayload {
  content: string
  visitAt?: string
}

export interface ExceptionCreatePayload {
  level: HomeVisitExceptionLevel
  category: string
  description: string
}

// ------------------------------------------------------------------
// 异常看板
// ------------------------------------------------------------------
export interface HomeVisitExceptionBoardItem extends HomeVisitException {
  orderId: string
  orderNo: string
}
