import { api } from './http'
import type {
  PermissionGroup,
  RoleCreatePayload,
  RoleResponse,
  RoleUpdatePayload
} from '@/types/system'

export function fetchRoles(): Promise<RoleResponse[]> {
  return api.get<RoleResponse[]>('/system/roles')
}

export function fetchPermissions(): Promise<PermissionGroup[]> {
  return api.get<PermissionGroup[]>('/system/permissions')
}

export function createRole(payload: RoleCreatePayload): Promise<RoleResponse> {
  return api.post<RoleResponse>('/system/roles', payload)
}

export function updateRole(roleId: string, payload: RoleUpdatePayload): Promise<RoleResponse> {
  return api.put<RoleResponse>(`/system/roles/${roleId}`, payload)
}
