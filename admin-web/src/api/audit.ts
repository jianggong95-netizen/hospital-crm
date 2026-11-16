import { api } from './http'
import type { AuditLogItem, PageResponse } from '@/types/system'

export interface AuditQuery {
  page?: number
  pageSize?: number
  action?: string
  resourceType?: string
  actorUserId?: string
  from?: string
  to?: string
}

export function fetchAuditLogs(query: AuditQuery): Promise<PageResponse<AuditLogItem>> {
  return api.get<PageResponse<AuditLogItem>>('/system/audit-logs', query)
}
