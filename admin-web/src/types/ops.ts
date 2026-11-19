export type JobRunStatus = 'SUCCESS' | 'FAILED'

export type AlertSeverity = 'CRITICAL' | 'HIGH' | 'MEDIUM' | 'LOW'

export type AlertStatus = 'OPEN' | 'ACKED' | 'RESOLVED'

export type BackupType = 'FULL' | 'WAL' | 'OBJECT'

export type BackupStatus = 'SUCCESS' | 'FAILED'

export interface OpsDatabaseStatus {
  status: 'UP' | 'DOWN'
  roundTripMs: number
}

export interface OpsApiMetrics {
  windowMinutes: number
  requestCount: number
  errorCount: number
  errorRate: number
  avgLatencyMs: number
  maxLatencyMs: number
}

export interface OpsJobSummary {
  total: number
  stale: number
  failing: number
}

export interface OpsBackupSummary {
  lastSuccessAt: string | null
  lastBackupCode: string | null
  lastStatus: string | null
  ageHours: number | null
  restoreVerified: boolean
  lastVerifiedAt: string | null
}

export interface OpsAlertSummary {
  open: number
  critical: number
  high: number
  medium: number
}

export interface OpsCapabilities {
  hisEnabled: boolean
  callCenterEnabled: boolean
  smsProvider: string
}

export interface OpsOverview {
  appName: string
  appVersion: string
  profile: string
  startedAt: string
  uptimeSeconds: number
  database: OpsDatabaseStatus
  api: OpsApiMetrics
  jobs: OpsJobSummary
  backups: OpsBackupSummary
  alerts: OpsAlertSummary
  capabilities: OpsCapabilities
  generatedAt: string
}

export interface JobStatusView {
  jobCode: string
  jobName: string
  expectedIntervalSeconds: number
  lastRunAt: string | null
  lastStatus: JobRunStatus | null
  lastDurationMs: number | null
  lastMessage: string | null
  consecutiveFailures: number
  stale: boolean
  neverRun: boolean
}

export interface JobRunView {
  id: string
  jobCode: string
  status: JobRunStatus
  startedAt: string
  finishedAt: string
  durationMs: number
  message: string | null
}

export interface AlertView {
  id: string
  alertCode: string
  severity: AlertSeverity
  status: AlertStatus
  title: string
  detail: string | null
  dedupKey: string
  source: string
  firstSeenAt: string
  lastSeenAt: string
  occurrences: number
  ackedBy: string | null
  ackedByName: string | null
  ackedAt: string | null
  resolvedAt: string | null
  createdAt: string
}

export interface RaiseAlertRequest {
  alertCode: string
  severity: AlertSeverity
  title: string
  detail?: string
  dedupKey?: string
  source?: string
}

export interface AlertRuleView {
  alertCode: string
  name: string
  severity: AlertSeverity
  condition: string
  threshold: number | null
  windowSeconds: number | null
  enabled: boolean
}

export interface BackupView {
  id: string
  backupCode: string
  backupType: BackupType
  status: BackupStatus
  sizeBytes: number
  fileHash: string | null
  target: string | null
  startedAt: string
  finishedAt: string
  durationSeconds: number | null
  message: string | null
  reportedBy: string | null
  restoreVerified: boolean
  restoreVerifiedAt: string | null
  restoreMessage: string | null
  createdAt: string
}

export interface ReportBackupRequest {
  backupCode: string
  backupType: BackupType
  status: BackupStatus
  sizeBytes: number
  fileHash?: string
  target?: string
  startedAt: string
  finishedAt: string
  message?: string
}

export interface VerifyBackupRequest {
  restoreVerified: boolean
  message?: string
}
