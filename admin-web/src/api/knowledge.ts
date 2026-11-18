import { api } from './http'
import type { PageResponse } from '@/types/system'
import type {
  KbAskResult,
  KbCategoryRequest,
  KbCategoryView,
  KbDocumentDetail,
  KbDocumentView,
  KbQueryLogView
} from '@/types/knowledge'

export interface KbDocumentQuery {
  keyword?: string
  categoryId?: string
  status?: string
  page?: number
  pageSize?: number
}

export function fetchKbCategories(): Promise<KbCategoryView[]> {
  return api.get<KbCategoryView[]>('/knowledge/categories')
}

export function createKbCategory(payload: KbCategoryRequest): Promise<KbCategoryView> {
  return api.post<KbCategoryView>('/knowledge/categories', payload)
}

export function updateKbCategory(categoryId: string, payload: KbCategoryRequest): Promise<KbCategoryView> {
  return api.put<KbCategoryView>(`/knowledge/categories/${categoryId}`, payload)
}

export function fetchKbDocuments(query: KbDocumentQuery): Promise<PageResponse<KbDocumentView>> {
  return api.get<PageResponse<KbDocumentView>>('/knowledge/documents', query)
}

export function fetchKbDocumentDetail(documentId: string): Promise<KbDocumentDetail> {
  return api.get<KbDocumentDetail>(`/knowledge/documents/${documentId}`)
}

export function createKbDocument(payload: {
  title: string
  categoryId?: string
  summary?: string
  content: string
}): Promise<KbDocumentView> {
  return api.post<KbDocumentView>('/knowledge/documents', payload)
}

export function uploadKbDocument(
  file: File,
  meta: { title?: string; categoryId?: string; summary?: string }
): Promise<KbDocumentView> {
  const form = new FormData()
  form.append('file', file)
  return api.upload<KbDocumentView>('/knowledge/documents/upload', form, {
    title: meta.title || undefined,
    categoryId: meta.categoryId || undefined,
    summary: meta.summary || undefined
  })
}

export function updateKbDocumentMeta(
  documentId: string,
  payload: { title: string; categoryId?: string; summary?: string }
): Promise<KbDocumentView> {
  return api.put<KbDocumentView>(`/knowledge/documents/${documentId}`, payload)
}

export function updateKbDocumentContent(
  documentId: string,
  payload: { content: string; changeNote?: string }
): Promise<KbDocumentView> {
  return api.put<KbDocumentView>(`/knowledge/documents/${documentId}/content`, payload)
}

export function submitKbDocument(documentId: string): Promise<KbDocumentView> {
  return api.post<KbDocumentView>(`/knowledge/documents/${documentId}/submit`)
}

export function approveKbDocument(documentId: string, comment?: string): Promise<KbDocumentView> {
  return api.post<KbDocumentView>(`/knowledge/documents/${documentId}/approve`, { comment })
}

export function rejectKbDocument(documentId: string, comment: string): Promise<KbDocumentView> {
  return api.post<KbDocumentView>(`/knowledge/documents/${documentId}/reject`, { comment })
}

export function offlineKbDocument(documentId: string, reason: string): Promise<KbDocumentView> {
  return api.post<KbDocumentView>(`/knowledge/documents/${documentId}/offline`, { reason })
}

export function askKnowledge(payload: {
  question: string
  topK?: number
  scene?: string
  sessionId?: string
}): Promise<KbAskResult> {
  return api.post<KbAskResult>('/knowledge/rag/ask', payload)
}

export function fetchKbQueryLogs(page = 1, pageSize = 20): Promise<PageResponse<KbQueryLogView>> {
  return api.get<PageResponse<KbQueryLogView>>('/knowledge/rag/logs', { page, pageSize })
}
