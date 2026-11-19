import { api } from './http'
import type { PageResponse } from '@/types/system'
import type {
  AlertRuleView,
  AlertStatus,
  AlertView,
  BackupStatus,
  BackupView,
  JobRunView,
  JobStatusView,
  OpsOverview,
  RaiseAlertRequest,
  ReportBackupRequest,
  VerifyBackupRequest
} from '@/types/ops'

export interface AlertQuery {
  status?: AlertStatus
  page?: number
  pageSize?: number
}

export interface BackupQuery {
  status?: BackupStatus
  page?: number
  pageSize?: number
}

// ------------------------------------------------------------------
// 运行总览
// ------------------------------------------------------------------
export function getOpsOverview(): Promise<OpsOverview> {
  return api.get<OpsOverview>('/ops/overview')
}

// ------------------------------------------------------------------
// 定时任务
// ------------------------------------------------------------------
export function getJobStatuses(): Promise<JobStatusView[]> {
  return api.get<JobStatusView[]>('/ops/jobs')
}

export function getJobRuns(jobCode: string, limit = 20): Promise<JobRunView[]> {
  return api.get<JobRunView[]>(`/ops/jobs/${jobCode}/runs`, { limit })
}

// ------------------------------------------------------------------
// 告警中心
// ------------------------------------------------------------------
export function getAlerts(query: AlertQuery): Promise<PageResponse<AlertView>> {
  return api.get<PageResponse<AlertView>>('/ops/alerts', query)
}

export function ackAlert(alertId: string): Promise<AlertView> {
  return api.post<AlertView>(`/ops/alerts/${alertId}/ack`)
}

export function raiseAlert(payload: RaiseAlertRequest): Promise<AlertView> {
  return api.post<AlertView>('/ops/alerts/raise', payload)
}

export function getAlertRules(): Promise<AlertRuleView[]> {
  return api.get<AlertRuleView[]>('/ops/alerts/rules')
}

// ------------------------------------------------------------------
// 备份与演练
// ------------------------------------------------------------------
export function getBackups(query: BackupQuery): Promise<PageResponse<BackupView>> {
  return api.get<PageResponse<BackupView>>('/ops/backups', query)
}

export function reportBackup(payload: ReportBackupRequest): Promise<BackupView> {
  return api.post<BackupView>('/ops/backups', payload)
}

export function verifyBackup(backupId: string, payload: VerifyBackupRequest): Promise<BackupView> {
  return api.post<BackupView>(`/ops/backups/${backupId}/verify`, payload)
}
