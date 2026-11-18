export type ChatSessionStatus = 'AI' | 'QUEUED' | 'ACTIVE' | 'CLOSED'

export type ChatPriority = 'low' | 'normal' | 'high' | 'urgent'

export type ChatSenderType = 'CUSTOMER' | 'AI' | 'STAFF' | 'SYSTEM'

export interface ChatSessionView {
  id: string
  sessionNo: string
  customerId: string
  customerName?: string
  phoneMasked?: string
  channel: string
  status: ChatSessionStatus
  currentHandlerType: 'ai' | 'human'
  priority: ChatPriority
  assignedStaffId?: string
  assignedStaffName?: string
  handoffRequestCount: number
  queueEnteredAt?: string
  claimedAt?: string
  lastMessageAt?: string
  lastMessagePreview?: string
  aiSummary?: string
  authorizationScope: string
  startedAt: string
  endedAt?: string
  closedBy?: string
  closedByName?: string
  waitingSeconds: number
}

export interface ChatMessageView {
  id: string
  senderType: ChatSenderType
  senderId?: string
  senderName?: string
  content: string
  contentType: string
  aiConfidence?: number
  knowledgeRefs?: string[]
  createdAt: string
}

export interface ChatHandoffView {
  id: string
  requestCount: number
  reason: string
  mode: string
  status: string
  fromStaffId?: string
  fromStaffName?: string
  toStaffId?: string
  toStaffName?: string
  note?: string
  requestedAt: string
  acceptedAt?: string
}

export interface ChatEventView {
  id: string
  eventType: string
  fromStatus?: string
  toStatus?: string
  operatorId?: string
  operatorName?: string
  note?: string
  createdAt: string
}

export interface ChatSessionDetail {
  session: ChatSessionView
  messages: ChatMessageView[]
  handoffs: ChatHandoffView[]
  events: ChatEventView[]
}

export interface ChatQueueItem {
  sessionId: string
  sessionNo: string
  customerName?: string
  phoneMasked?: string
  channel: string
  priority: ChatPriority
  waitingSeconds: number
  queuePosition: number
  lastMessage?: string
  aiSummary?: string
  reason?: string
}

export interface ChatHandoffResult {
  needsConfirmation: boolean
  message: string
  queuePosition?: number
  session: ChatSessionView
}
