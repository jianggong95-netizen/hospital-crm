// ------------------------------------------------------------------
// 会员等级与账户
// ------------------------------------------------------------------
export interface MemberLevel {
  levelCode: string
  levelName: string
  minPoints: number
  /** 十进制折扣率字符串 */
  discountRate: string
  sortNo: number
}

export type MemberStatus = 'active' | 'disabled'

export interface MemberAccount {
  id: string
  accountNo: string
  customerId: string
  customerName?: string
  memberLevel: string
  levelName?: string
  discountRate?: string
  /** 可用余额（十进制字符串） */
  balance: string
  frozenBalance: string
  points: number
  status: MemberStatus
  createdAt: string
  updatedAt?: string
}

export type CardStatus = 'ACTIVE' | 'EXHAUSTED' | 'EXPIRED' | 'VOID'

export interface MemberCardSummary {
  id: string
  cardNo: string
  packageCode: string
  packageName?: string
  totalTimes: number
  usedTimes: number
  remainingTimes: number
  validFrom: string
  validTo: string
  status: CardStatus
}

export interface MemberAccountDetail {
  account: MemberAccount
  cards: MemberCardSummary[]
}

export interface CreateAccountPayload {
  customerId: string
}

export interface RechargePayload {
  accountId: string
  amount: string
  orderId?: string
  reason?: string
}

export interface ConsumePayload {
  customerId: string
  amount: string
  bizType?: string
  bizNo?: string
  allowShared?: boolean
}

export interface PointsAdjustPayload {
  accountId: string
  points: number
  reason?: string
}

export interface RefundReversalPayload {
  orderId: string
  refundAmount: string
}

export interface RefundReversalResult {
  orderId: string
  balanceReversed: string
  pointsReversed: number
  cardsVoided: number
  cardTimesReturned: number
}

// ------------------------------------------------------------------
// 账本
// ------------------------------------------------------------------
export interface MemberLedgerEntry {
  id: string
  ledgerType: string
  /** 余额变动，正增负减 */
  amount: string
  pointsDelta: number
  balanceAfter?: string
  pointsAfter?: number
  relatedOrderId?: string
  bizNo?: string
  reason?: string
  createdAt: string
}

// ------------------------------------------------------------------
// 套餐
// ------------------------------------------------------------------
export type PackageType = 'TIMES' | 'BALANCE' | 'POINTS'

export type PackageStatus = 'active' | 'disabled'

export interface MemberPackage {
  packageCode: string
  packageName: string
  packageType: PackageType
  price: string
  times?: number
  balanceAmount?: string
  pointsAmount?: number
  validDays?: number
  status: PackageStatus
  description?: string
}

export interface CreatePackagePayload {
  packageCode: string
  packageName: string
  packageType: PackageType
  price: string
  times?: number
  balanceAmount?: string
  pointsAmount?: number
  validDays?: number
  description?: string
}

export interface UpdatePackagePayload {
  packageName: string
  price: string
  times?: number
  balanceAmount?: string
  pointsAmount?: number
  validDays?: number
  description?: string
}

export interface PurchasePayload {
  customerId: string
  orderId?: string
}

export interface PurchaseResult {
  account: MemberAccount
  card?: MemberCardSummary
  balanceAdded: string
  pointsAdded: number
}

// ------------------------------------------------------------------
// 次卡
// ------------------------------------------------------------------
export interface MemberCard {
  id: string
  cardNo: string
  customerId: string
  customerName?: string
  packageCode: string
  packageName?: string
  totalTimes: number
  usedTimes: number
  remainingTimes: number
  validFrom: string
  validTo: string
  status: CardStatus
  sourceOrderId?: string
}

export type CardUsageStatus = 'CONSUMED' | 'REFUNDED'

export interface MemberCardUsage {
  id: string
  times: number
  bizType?: string
  bizNo?: string
  status: CardUsageStatus
  remark?: string
  createdAt: string
  refundedAt?: string
}

export interface MemberCardDetail {
  card: MemberCard
  usages: MemberCardUsage[]
}

export interface CardConsumePayload {
  times: number
  bizType?: string
  bizNo?: string
  remark?: string
}

export interface CardRefundPayload {
  times: number
  usageId?: string
  reason?: string
}

// ------------------------------------------------------------------
// 共享授权
// ------------------------------------------------------------------
export type ShareResourceType = 'BALANCE' | 'POINTS' | 'CARD'

export type ShareStatus = 'ACTIVE' | 'REVOKED' | 'EXPIRED'

export interface MemberShare {
  id: string
  ownerCustomerId: string
  ownerCustomerName?: string
  beneficiaryCustomerId: string
  beneficiaryCustomerName?: string
  resourceType: ShareResourceType
  resourceId?: string
  canConsume: boolean
  validFrom: string
  validTo?: string
  status: ShareStatus
  createdAt: string
}

export interface CreateSharePayload {
  ownerCustomerId: string
  beneficiaryCustomerId: string
  resourceType: ShareResourceType
  resourceId?: string
  validTo?: string
  canConsume?: boolean
}
