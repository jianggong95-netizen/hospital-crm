import { api } from './http'
import type { PageResponse } from '@/types/system'
import type {
  ChatHandoffResult,
  ChatMessageView,
  ChatQueueItem,
  ChatSessionDetail,
  ChatSessionView,
  ChatSenderType
} from '@/types/chat'

export interface ChatSessionQuery {
  keyword?: string
  status?: string
  channel?: string
  customerId?: string
  assigneeId?: string
  priority?: string
  page?: number
  pageSize?: number
}

export function fetchChatQueue(): Promise<ChatQueueItem[]> {
  return api.get<ChatQueueItem[]>('/chat/queue')
}

export function fetchChatSessions(query: ChatSessionQuery): Promise<PageResponse<ChatSessionView>> {
  return api.get<PageResponse<ChatSessionView>>('/chat/sessions', query)
}

export function fetchChatSessionDetail(sessionId: string): Promise<ChatSessionDetail> {
  return api.get<ChatSessionDetail>(`/chat/sessions/${sessionId}`)
}

export function fetchChatMessages(
  sessionId: string,
  page = 1,
  pageSize = 200
): Promise<PageResponse<ChatMessageView>> {
  return api.get<PageResponse<ChatMessageView>>(`/chat/sessions/${sessionId}/messages`, {
    page,
    pageSize
  })
}

export function createChatSession(payload: {
  customerId: string
  channel: string
  authorizationScope?: string
}): Promise<ChatSessionDetail> {
  return api.post<ChatSessionDetail>('/chat/sessions', payload)
}

export function sendChatMessage(
  sessionId: string,
  payload: {
    senderType: ChatSenderType
    content: string
    contentType?: string
    aiConfidence?: number
    knowledgeRefs?: string[]
    clientMessageId?: string
  }
): Promise<ChatSessionDetail> {
  return api.post<ChatSessionDetail>(`/chat/sessions/${sessionId}/messages`, payload)
}

export function requestChatHandoff(
  sessionId: string,
  payload: { reason: string; note?: string }
): Promise<ChatHandoffResult> {
  return api.post<ChatHandoffResult>(`/chat/sessions/${sessionId}/handoff`, payload)
}

export function claimChatSession(sessionId: string): Promise<ChatSessionDetail> {
  return api.post<ChatSessionDetail>(`/chat/sessions/${sessionId}/claim`)
}

export function transferChatSession(
  sessionId: string,
  payload: { toStaffId: string; note?: string }
): Promise<ChatSessionDetail> {
  return api.post<ChatSessionDetail>(`/chat/sessions/${sessionId}/transfer`, payload)
}

export function closeChatSession(sessionId: string, reason?: string): Promise<ChatSessionView> {
  return api.post<ChatSessionView>(`/chat/sessions/${sessionId}/close`, { reason })
}
