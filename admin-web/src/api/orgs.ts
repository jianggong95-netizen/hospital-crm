import { api } from './http'
import type { OrgCreatePayload, OrgNode, OrgUpdatePayload } from '@/types/system'

export function fetchOrgTree(): Promise<OrgNode[]> {
  return api.get<OrgNode[]>('/system/orgs/tree')
}

export function createOrg(payload: OrgCreatePayload): Promise<OrgNode> {
  return api.post<OrgNode>('/system/orgs', payload)
}

export function updateOrg(orgId: string, payload: OrgUpdatePayload): Promise<OrgNode> {
  return api.put<OrgNode>(`/system/orgs/${orgId}`, payload)
}
