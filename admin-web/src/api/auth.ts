import { api } from './http'
import type { TokenResponse, UserSession } from '@/types/auth'

export function login(username: string, password: string): Promise<TokenResponse> {
  return api.post<TokenResponse>('/auth/login', { username, password })
}

export function logout(refreshToken: string): Promise<void> {
  return api.post<void>('/auth/logout', { refreshToken })
}

export function fetchMe(): Promise<UserSession> {
  return api.get<UserSession>('/auth/me')
}
