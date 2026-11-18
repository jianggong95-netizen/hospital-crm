export type KbDocumentStatus = 'DRAFT' | 'REVIEWING' | 'PUBLISHED' | 'OFFLINE'

export interface KbCategoryView {
  id: string
  code: string
  name: string
  sortOrder: number
  status: string
  createdAt: string
}

export interface KbCategoryRequest {
  code: string
  name: string
  sortOrder?: number
  status?: string
}

export interface KbDocumentView {
  id: string
  docNo: string
  title: string
  categoryId?: string
  categoryName?: string
  summary?: string
  sourceType: 'manual' | 'file'
  fileName?: string
  fileSize?: number
  fileType?: string
  status: KbDocumentStatus
  currentVersion: number
  draftChunkCount: number
  ownerId?: string
  ownerName?: string
  publishedAt?: string
  offlineAt?: string
  offlineReason?: string
  createdAt: string
  updatedAt: string
}

export interface KbChunkView {
  id: string
  docVersion: number
  chunkIndex: number
  content: string
  charCount: number
  createdAt: string
}

export interface KbVersionView {
  id: string
  versionNo: number
  title: string
  summary?: string
  chunkCount: number
  changeNote?: string
  createdBy?: string
  createdByName?: string
  createdAt: string
}

export interface KbReviewView {
  id: string
  versionNo: number
  action: 'SUBMIT' | 'APPROVE' | 'REJECT' | 'OFFLINE'
  reviewerId?: string
  reviewerName?: string
  comment?: string
  createdAt: string
}

export interface KbDocumentDetail {
  document: KbDocumentView
  chunks: KbChunkView[]
  versions: KbVersionView[]
  reviews: KbReviewView[]
}

export interface KbAskRef {
  docId: string
  docNo: string
  title: string
  version: number
  chunkId: string
  chunkIndex: number
  score: number
}

export interface KbAskResult {
  logId: string
  answer: string
  confidence?: number
  refs: KbAskRef[]
  needsHandoff: boolean
  handoffMessage?: string
}

export interface KbQueryLogView {
  id: string
  question: string
  answer: string
  confidence?: number
  refs: string[]
  scene: 'chat' | 'followup'
  sessionId?: string
  needsHandoff: boolean
  askedBy?: string
  askedByName?: string
  createdAt: string
}
