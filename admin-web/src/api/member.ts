import { api } from './http'
import type { PageResponse } from '@/types/system'
import type {
  CardConsumePayload,
  CardRefundPayload,
  ConsumePayload,
  CreateAccountPayload,
  CreatePackagePayload,
  CreateSharePayload,
  MemberAccount,
  MemberAccountDetail,
  MemberCard,
  MemberCardDetail,
  MemberLedgerEntry,
  MemberLevel,
  MemberPackage,
  MemberShare,
  PointsAdjustPayload,
  PurchasePayload,
  PurchaseResult,
  RechargePayload,
  RefundReversalPayload,
  RefundReversalResult,
  UpdatePackagePayload
} from '@/types/member'

export interface MemberAccountQuery {
  keyword?: string
  level?: string
  status?: string
  page?: number
  pageSize?: number
}

export interface MemberCardQuery {
  customerId?: string
  status?: string
  page?: number
  pageSize?: number
}

export interface LedgerQuery {
  page?: number
  pageSize?: number
}

// ------------------------------------------------------------------
// 等级与账户
// ------------------------------------------------------------------
export function fetchMemberLevels(): Promise<MemberLevel[]> {
  return api.get<MemberLevel[]>('/memberships/levels')
}

export function fetchMemberAccounts(query: MemberAccountQuery): Promise<PageResponse<MemberAccount>> {
  return api.get<PageResponse<MemberAccount>>('/memberships/accounts', query)
}

export function fetchMemberAccountDetail(accountId: string): Promise<MemberAccountDetail> {
  return api.get<MemberAccountDetail>(`/memberships/accounts/${accountId}`)
}

export function openMemberAccount(payload: CreateAccountPayload): Promise<MemberAccount> {
  return api.post<MemberAccount>('/memberships/accounts', payload)
}

export function rechargeMemberAccount(payload: RechargePayload): Promise<MemberAccount> {
  return api.post<MemberAccount>('/memberships/recharges', payload)
}

export function consumeMemberBalance(payload: ConsumePayload): Promise<MemberAccount> {
  return api.post<MemberAccount>('/memberships/consumptions', payload)
}

export function adjustMemberPoints(payload: PointsAdjustPayload): Promise<MemberAccount> {
  return api.post<MemberAccount>('/memberships/points/adjustments', payload)
}

export function reverseMemberBenefits(payload: RefundReversalPayload): Promise<RefundReversalResult> {
  return api.post<RefundReversalResult>('/memberships/refund-reversals', payload)
}

export function fetchMemberLedger(accountId: string, query: LedgerQuery): Promise<PageResponse<MemberLedgerEntry>> {
  return api.get<PageResponse<MemberLedgerEntry>>(`/memberships/accounts/${accountId}/ledger`, query)
}

// ------------------------------------------------------------------
// 套餐
// ------------------------------------------------------------------
export function fetchMemberPackages(status?: string): Promise<MemberPackage[]> {
  return api.get<MemberPackage[]>('/memberships/packages', { status })
}

export function createMemberPackage(payload: CreatePackagePayload): Promise<MemberPackage> {
  return api.post<MemberPackage>('/memberships/packages', payload)
}

export function updateMemberPackage(packageCode: string, payload: UpdatePackagePayload): Promise<MemberPackage> {
  return api.put<MemberPackage>(`/memberships/packages/${packageCode}`, payload)
}

export function changeMemberPackageStatus(packageCode: string, status: string): Promise<MemberPackage> {
  return api.put<MemberPackage>(`/memberships/packages/${packageCode}/status`, { status })
}

export function purchaseMemberPackage(packageCode: string, payload: PurchasePayload): Promise<PurchaseResult> {
  return api.post<PurchaseResult>(`/memberships/packages/${packageCode}/purchase`, payload)
}

// ------------------------------------------------------------------
// 次卡
// ------------------------------------------------------------------
export function fetchMemberCards(query: MemberCardQuery): Promise<PageResponse<MemberCard>> {
  return api.get<PageResponse<MemberCard>>('/memberships/cards', query)
}

export function fetchMemberCardDetail(cardId: string): Promise<MemberCardDetail> {
  return api.get<MemberCardDetail>(`/memberships/cards/${cardId}`)
}

export function consumeMemberCard(cardId: string, payload: CardConsumePayload): Promise<MemberCard> {
  return api.post<MemberCard>(`/memberships/cards/${cardId}/consumptions`, payload)
}

export function refundMemberCard(cardId: string, payload: CardRefundPayload): Promise<MemberCard> {
  return api.post<MemberCard>(`/memberships/cards/${cardId}/refunds`, payload)
}

// ------------------------------------------------------------------
// 共享授权
// ------------------------------------------------------------------
export function fetchMemberShares(params: {
  ownerCustomerId?: string
  beneficiaryCustomerId?: string
}): Promise<MemberShare[]> {
  return api.get<MemberShare[]>('/memberships/shares', params)
}

export function createMemberShare(payload: CreateSharePayload): Promise<MemberShare> {
  return api.post<MemberShare>('/memberships/shares', payload)
}

export function revokeMemberShare(shareId: string): Promise<MemberShare> {
  return api.put<MemberShare>(`/memberships/shares/${shareId}/revoke`)
}
