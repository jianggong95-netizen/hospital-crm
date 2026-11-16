import { api } from './http'
import type { SystemInfo } from '@/types/api'

export function fetchSystemInfo(): Promise<SystemInfo> {
  return api.get<SystemInfo>('/system/info')
}
