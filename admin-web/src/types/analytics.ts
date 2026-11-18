export type MetricUnit = 'count' | 'yuan' | 'percent' | 'minute'

export type MetricStatus = 'enabled' | 'disabled'

export interface MetricView {
  id: string
  metricCode: string
  metricName: string
  category: string
  caliberDescription: string
  dataSource: string
  unit: MetricUnit | null
  decimalPlaces: number
  status: MetricStatus
  remark?: string
  createdAt: string
  updatedAt: string
}

export interface CreateMetricRequest {
  metricCode: string
  metricName: string
  category: string
  caliberDescription: string
  dataSource: string
  unit?: MetricUnit
  decimalPlaces?: number
  remark?: string
}

export interface UpdateMetricRequest {
  metricName: string
  category: string
  caliberDescription: string
  dataSource: string
  unit?: MetricUnit
  decimalPlaces?: number
  remark?: string
}

export interface DashboardCard {
  metricCode: string
  metricName: string
  unit: MetricUnit | null
  value: number | null
  caliberDescription: string
  scopeNote: string | null
}

export interface DashboardView {
  dataFrom: string
  dataTo: string
  generatedAt: string
  scopeAll: boolean
  cards: DashboardCard[]
}

export type ReportType = 'metric_summary' | 'customer_detail'

export interface ReportView {
  id: string
  reportCode: string
  reportName: string
  description: string
  reportType: ReportType
  metricCodes: string[]
  status: string
  createdAt: string
  updatedAt: string
}

export interface CreateReportRequest {
  reportCode: string
  reportName: string
  description?: string
  reportType: ReportType
  metricCodes?: string[]
}

export interface ReportColumn {
  key: string
  label: string
}

export interface ReportDataView {
  reportCode: string
  reportName: string
  dataFrom: string
  dataTo: string
  generatedAt: string
  scopeAll: boolean
  columns: ReportColumn[]
  rows: Record<string, unknown>[]
  rowCount: number
}

export type ExportStatus = 'pending' | 'approved' | 'rejected'

export interface ExportView {
  id: string
  reportCode: string
  reportName: string
  requesterUserId: string
  requesterName: string
  dataFrom: string
  dataTo: string
  reason: string
  status: ExportStatus
  approverName?: string
  approvedAt?: string
  rejectReason?: string
  rowCount?: number
  fileHash?: string
  createdAt: string
}

export interface ExportCreateRequest {
  reportCode: string
  dataFrom: string
  dataTo: string
  reason: string
}
