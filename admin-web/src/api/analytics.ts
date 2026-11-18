import { api } from './http'
import type { PageResponse } from '@/types/system'
import type {
  CreateMetricRequest,
  CreateReportRequest,
  DashboardView,
  ExportCreateRequest,
  ExportStatus,
  ExportView,
  MetricStatus,
  MetricView,
  ReportDataView,
  ReportView,
  UpdateMetricRequest
} from '@/types/analytics'

export interface MetricQuery {
  page?: number
  pageSize?: number
  keyword?: string
  status?: string
  category?: string
}

export interface ReportQuery {
  page?: number
  pageSize?: number
  keyword?: string
  status?: string
}

export interface ExportQuery {
  page?: number
  pageSize?: number
  status?: string
}

// ------------------------------------------------------------------
// 指标口径
// ------------------------------------------------------------------
export function getMetrics(query: MetricQuery): Promise<PageResponse<MetricView>> {
  return api.get<PageResponse<MetricView>>('/analytics/metrics', query)
}

export function createMetric(payload: CreateMetricRequest): Promise<MetricView> {
  return api.post<MetricView>('/analytics/metrics', payload)
}

export function updateMetric(metricId: string, payload: UpdateMetricRequest): Promise<MetricView> {
  return api.put<MetricView>(`/analytics/metrics/${metricId}`, payload)
}

export function updateMetricStatus(metricId: string, status: MetricStatus): Promise<MetricView> {
  return api.put<MetricView>(`/analytics/metrics/${metricId}/status`, { status })
}

// ------------------------------------------------------------------
// 指标看板
// ------------------------------------------------------------------
export function getDashboard(from: string, to: string): Promise<DashboardView> {
  return api.get<DashboardView>('/analytics/dashboard', { from, to })
}

// ------------------------------------------------------------------
// 报表
// ------------------------------------------------------------------
export function getReports(query: ReportQuery): Promise<PageResponse<ReportView>> {
  return api.get<PageResponse<ReportView>>('/analytics/reports', query)
}

export function createReport(payload: CreateReportRequest): Promise<ReportView> {
  return api.post<ReportView>('/analytics/reports', payload)
}

export function updateReportStatus(reportId: string, status: string): Promise<ReportView> {
  return api.put<ReportView>(`/analytics/reports/${reportId}/status`, { status })
}

export function getReportData(
  reportCode: string,
  from: string,
  to: string
): Promise<ReportDataView> {
  return api.get<ReportDataView>(`/analytics/reports/${reportCode}/data`, { from, to })
}

// ------------------------------------------------------------------
// 导出审批
// ------------------------------------------------------------------
export function createExport(payload: ExportCreateRequest): Promise<ExportView> {
  return api.post<ExportView>('/analytics/exports', payload)
}

export function getExports(query: ExportQuery): Promise<PageResponse<ExportView>> {
  return api.get<PageResponse<ExportView>>('/analytics/exports', query)
}

export function approveExport(exportId: string): Promise<ExportView> {
  return api.put<ExportView>(`/analytics/exports/${exportId}/approve`)
}

export function rejectExport(exportId: string, reason: string): Promise<ExportView> {
  return api.put<ExportView>(`/analytics/exports/${exportId}/reject`, { reason })
}

export async function downloadExport(exportId: string, reportCode: string): Promise<void> {
  const blob = await api.getBlob(`/analytics/exports/${exportId}/download`)
  const url = window.URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = `export-${reportCode}-${exportId.slice(0, 8)}.csv`
  document.body.appendChild(link)
  link.click()
  link.remove()
  window.URL.revokeObjectURL(url)
}

export type { ExportStatus }
