import { api } from './http'
import type {
  DelegationCheckPayload,
  DelegationCheckResult,
  FamilyAuthorizationCreatePayload,
  FamilyAuthorizationItem,
  FamilyCreatePayload,
  FamilyDetail,
  FamilyGraph,
  FamilyMemberAddPayload,
  FamilyMemberItem,
  FamilyMemberRelations,
  FamilyMemberUpdatePayload,
  FamilySummary
} from '@/types/family'
import type { PageResponse } from '@/types/system'

export interface FamilyQuery {
  keyword?: string
  status?: string
  page?: number
  pageSize?: number
}

export function fetchFamilies(query: FamilyQuery): Promise<PageResponse<FamilySummary>> {
  return api.get<PageResponse<FamilySummary>>('/families', query)
}

export function createFamily(payload: FamilyCreatePayload): Promise<FamilyDetail> {
  return api.post<FamilyDetail>('/families', payload)
}

export function fetchFamily(familyId: string): Promise<FamilyDetail> {
  return api.get<FamilyDetail>(`/families/${familyId}`)
}

export function fetchFamilyMembers(familyId: string, includeEnded = false): Promise<FamilyMemberItem[]> {
  return api.get<FamilyMemberItem[]>(`/families/${familyId}/members`, { includeEnded })
}

export function fetchFamilyGraph(familyId: string): Promise<FamilyGraph> {
  return api.get<FamilyGraph>(`/families/${familyId}/graph`)
}

export function fetchMemberRelations(familyId: string, memberId: string): Promise<FamilyMemberRelations> {
  return api.get<FamilyMemberRelations>(`/families/${familyId}/members/${memberId}/relations`)
}

export function addFamilyMember(familyId: string, payload: FamilyMemberAddPayload): Promise<FamilyMemberItem> {
  return api.post<FamilyMemberItem>(`/families/${familyId}/members`, payload)
}

export function updateFamilyMember(
  familyId: string,
  memberId: string,
  payload: FamilyMemberUpdatePayload
): Promise<FamilyMemberItem> {
  return api.put<FamilyMemberItem>(`/families/${familyId}/members/${memberId}`, payload)
}

export function endFamilyMembership(familyId: string, memberId: string): Promise<void> {
  return api.del<void>(`/families/${familyId}/members/${memberId}`)
}

export function fetchAuthorizations(
  familyId: string,
  memberId: string
): Promise<FamilyAuthorizationItem[]> {
  return api.get<FamilyAuthorizationItem[]>(`/families/${familyId}/members/${memberId}/authorizations`)
}

export function createAuthorization(
  familyId: string,
  memberId: string,
  payload: FamilyAuthorizationCreatePayload
): Promise<FamilyAuthorizationItem> {
  return api.post<FamilyAuthorizationItem>(
    `/families/${familyId}/members/${memberId}/authorizations`,
    payload
  )
}

export function revokeAuthorization(
  familyId: string,
  memberId: string,
  authorizationId: string
): Promise<FamilyAuthorizationItem> {
  return api.del<FamilyAuthorizationItem>(
    `/families/${familyId}/members/${memberId}/authorizations/${authorizationId}`
  )
}

export function checkDelegation(
  familyId: string,
  payload: DelegationCheckPayload
): Promise<DelegationCheckResult> {
  return api.post<DelegationCheckResult>(`/families/${familyId}/delegation-checks`, payload)
}
