export interface CustomerBrief {
  id: string
  customerNo: string
  name: string
  phoneMasked?: string
  status: 'active' | 'disabled'
}

export interface FamilySummary {
  id: string
  familyNo: string
  familyName: string
  status: 'active' | 'disabled'
  memberCount: number
  adminName?: string
  createdAt: string
  updatedAt: string
}

export interface FamilyMemberItem {
  id: string
  familyId: string
  customer: CustomerBrief | null
  relationshipCode: string
  relationshipLabel: string
  admin: boolean
  status: 'active' | 'ended'
  validFrom: string
  validTo?: string
}

export interface FamilyDetail {
  id: string
  familyNo: string
  familyName: string
  status: 'active' | 'disabled'
  members: FamilyMemberItem[]
  createdAt: string
  updatedAt: string
}

export interface FamilyAuthorizationItem {
  id: string
  memberId: string
  grantee: CustomerBrief | null
  scopeCode: string
  scopeLabel: string
  status: 'active' | 'revoked' | 'expired'
  validFrom: string
  validTo?: string
  revokedAt?: string
  createdAt: string
}

export interface DelegationCheckResult {
  allowed: boolean
  memberId: string
  granteeCustomerId: string
  scopeCode: string
  authorizationId?: string
  validTo?: string
}

export interface FamilyCreatePayload {
  familyName: string
  ownerCustomerId: string
}

export interface FamilyMemberAddPayload {
  customerId: string
  relationshipCode: string
}

export interface FamilyMemberUpdatePayload {
  relationshipCode: string
  admin?: boolean
}

export interface FamilyAuthorizationCreatePayload {
  granteeCustomerId: string
  scopeCode: string
  validFrom?: string
  validTo?: string
}

export interface DelegationCheckPayload {
  memberId: string
  granteeCustomerId: string
  scopeCode: string
}

export interface FamilyGraphNode {
  customerId: string
  name?: string
  customerNo?: string
  phoneMasked?: string
  gender?: string
  birthDate?: string
  relationshipCode: string
  relationshipLabel: string
  admin: boolean
}

export interface FamilyGraphEdge {
  fromCustomerId: string
  toCustomerId: string
  label: string
}

export interface FamilyGraph {
  nodes: FamilyGraphNode[]
  edges: FamilyGraphEdge[]
}

export interface FamilyMemberBrief {
  memberId: string
  customerId: string
  customerNo: string
  name: string
  gender?: string
  birthDate?: string
  phoneMasked?: string
  relationshipCode: string
  relationshipLabel: string
  admin: boolean
}

export interface FamilyMemberRelationItem {
  member: FamilyMemberBrief
  relationToSelf: string
  relationFromSelf: string
}

export interface FamilyPeerRelationItem {
  fromMemberId: string
  fromName: string
  toMemberId: string
  toName: string
  label: string
}

export interface FamilyMemberRelations {
  self: FamilyMemberBrief
  relations: FamilyMemberRelationItem[]
  peerRelations: FamilyPeerRelationItem[]
}
