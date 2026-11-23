import { http } from './request'
import type { CrmUser } from '@/store/session'

export interface LoginResult {
  accessToken: string
  refreshToken: string
  tokenType: string
  expiresIn: number
  user: CrmUser
}

export function login(username: string, password: string): Promise<LoginResult> {
  return http.post<LoginResult>('/auth/login', { username, password })
}

export function fetchMe(): Promise<CrmUser> {
  return http.get<CrmUser>('/auth/me')
}
