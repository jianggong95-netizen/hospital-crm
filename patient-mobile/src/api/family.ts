import { http, type PageResult } from './request'

export interface FamilySummary {
  id: string
  familyNo: string
  familyName: string
  status: 'active' | 'disabled'
  memberCount: number
  adminName?: string
  createdAt: string
}

export interface FamilyMember {
  id: string
  relationshipCode: string
  relationshipLabel: string
  admin: boolean
  status: 'active' | 'ended'
  validFrom: string
  customer: { id: string; customerNo: string; name: string; gender: string } | null
}

export function fetchFamilies(): Promise<PageResult<FamilySummary>> {
  return http.get<PageResult<FamilySummary>>('/families', { page: 1, pageSize: 30 })
}

export function createFamily(familyName: string, ownerCustomerId: string): Promise<FamilySummary> {
  return http.post<FamilySummary>('/families', { familyName, ownerCustomerId })
}

export function fetchFamilyMembers(familyId: string): Promise<FamilyMember[]> {
  return http.get<FamilyMember[]>(`/families/${familyId}/members`, { includeEnded: false })
}

export function addFamilyMember(familyId: string, customerId: string, relationshipCode: string): Promise<FamilyMember> {
  return http.post<FamilyMember>(`/families/${familyId}/members`, { customerId, relationshipCode })
}
