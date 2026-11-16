import { api } from './http'
import type {
  PageResponse,
  UserCreatePayload,
  UserResponse,
  UserUpdatePayload
} from '@/types/system'

export interface UserQuery {
  page?: number
  pageSize?: number
  keyword?: string
  orgId?: string
  status?: string
}

export function fetchUsers(query: UserQuery): Promise<PageResponse<UserResponse>> {
  return api.get<PageResponse<UserResponse>>('/system/users', query)
}

export function createUser(payload: UserCreatePayload): Promise<UserResponse> {
  return api.post<UserResponse>('/system/users', payload)
}

export function updateUser(userId: string, payload: UserUpdatePayload): Promise<UserResponse> {
  return api.put<UserResponse>(`/system/users/${userId}`, payload)
}

export function changeUserStatus(userId: string, status: 'active' | 'disabled'): Promise<void> {
  return api.patch<void>(`/system/users/${userId}/status`, { status })
}

export function resetUserPassword(userId: string, password: string): Promise<void> {
  return api.post<void>(`/system/users/${userId}/reset-password`, { password })
}
