import { http, type PageResult } from './request'

export interface ChatSessionView {
  id: string
  sessionNo: string
  customerId: string
  customerName?: string
  channel: string
  status: 'AI' | 'QUEUED' | 'ACTIVE' | 'CLOSED'
  currentHandlerType: 'ai' | 'human'
  priority: string
  assignedStaffName?: string
  lastMessageAt?: string
  lastMessagePreview?: string
}

export interface ChatMessageView {
  id: string
  senderType: 'CUSTOMER' | 'AI' | 'STAFF' | 'SYSTEM'
  senderName?: string
  content: string
  contentType: string
  createdAt: string
}

export interface ChatSessionDetail {
  session: ChatSessionView
  messages: ChatMessageView[]
  handoffs: unknown[]
  events: unknown[]
}

export function fetchMySessions(customerId: string): Promise<PageResult<ChatSessionView>> {
  return http.get<PageResult<ChatSessionView>>('/chat/sessions', {
    customerId,
    page: 1,
    pageSize: 20
  })
}

export function createSession(customerId: string, channel = 'h5'): Promise<ChatSessionDetail> {
  return http.post<ChatSessionDetail>('/chat/sessions', { customerId, channel })
}

export function fetchMessages(sessionId: string): Promise<PageResult<ChatMessageView>> {
  return http.get<PageResult<ChatMessageView>>(`/chat/sessions/${sessionId}/messages`, {
    page: 1,
    pageSize: 200
  })
}

export function sendMessage(sessionId: string, content: string): Promise<unknown> {
  return http.post(`/chat/sessions/${sessionId}/messages`, {
    senderType: 'CUSTOMER',
    content,
    contentType: 'text'
  })
}

export interface HandoffResult {
  accepted: boolean
  message: string
  queuePosition?: number
  session?: ChatSessionView
}

export function requestHandoff(sessionId: string, reason = 'explicit'): Promise<HandoffResult> {
  return http.post<HandoffResult>(`/chat/sessions/${sessionId}/handoff`, { reason })
}
