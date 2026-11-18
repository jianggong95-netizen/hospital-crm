export type CallDirection = 'inbound' | 'outbound'

export type CallLinkType = 'customer' | 'complaint' | 'session' | 'appointment' | 'none'

export type CallRecordStatus = 'connected' | 'missed' | 'transferred'

export type CallSource = 'vendor' | 'manual'

export type RecordingStorageType = 'vendor' | 'oss' | 'local'

export interface CallRecordView {
  id: string
  callNo: string
  vendorCallId?: string
  direction: CallDirection
  callerMasked?: string
  calleeMasked?: string
  customerId?: string
  customerName?: string
  linkType: CallLinkType
  linkId?: string
  linkNo?: string
  queueName?: string
  agentUserId?: string
  agentUserName?: string
  status: CallRecordStatus
  startedAt: string
  endedAt?: string
  durationSeconds?: number
  recordingId?: string
  hasRecording: boolean
  source: CallSource
  createdAt: string
}

export interface RecordingView {
  id: string
  recordingKey: string
  storageType: RecordingStorageType
  durationSeconds?: number
  fileSize?: number
  accessCount: number
  createdAt: string
}

export interface CallRecordDetailView {
  call: CallRecordView
  recording?: RecordingView
}

export interface RecordingAccessView {
  recordingId: string
  callNo: string
  storageType: RecordingStorageType
  accessUrl?: string
  note?: string
}

export interface PopupCustomerView {
  id: string
  customerNo: string
  name: string
  gender?: string
  birthDate?: string
  phoneMasked?: string
  status?: string
}

export interface PopupView {
  matched: boolean
  phoneMasked?: string
  customer?: PopupCustomerView
  callHistoryCount: number
  lastCallAt?: string
}

export type SmsTemplateStatus = 'active' | 'disabled'

export interface SmsTemplateView {
  id: string
  code: string
  name: string
  channel: string
  content: string
  variables: string[]
  status: SmsTemplateStatus
  remark?: string
  createdAt: string
  updatedAt: string
}

export type MessageChannel = 'sms' | 'wechat' | 'voice'

export type MessageStatus = 'PENDING' | 'SENDING' | 'SENT' | 'FAILED' | 'CANCELLED'

export interface MessageView {
  id: string
  msgNo: string
  bizKey?: string
  templateCode?: string
  templateName?: string
  channel: MessageChannel
  scene?: string
  recipientMasked?: string
  customerId?: string
  customerName?: string
  content: string
  provider?: string
  status: MessageStatus
  retryCount: number
  maxRetries: number
  nextRetryAt?: string
  lastError?: string
  sentAt?: string
  createdAt: string
}

export interface DispatchResult {
  scanned: number
  sent: number
  failed: number
  at: string
}

export interface CreateCallRequest {
  vendorCallId?: string
  direction: CallDirection
  callerNumber?: string
  calleeNumber?: string
  customerId?: string
  linkType?: CallLinkType
  linkId?: string
  queueName?: string
  agentUserId?: string
  startedAt: string
  endedAt?: string
  durationSeconds?: number
  status: CallRecordStatus
  recordingKey?: string
  storageType?: RecordingStorageType
}

export interface LinkCallRequest {
  targetType: CallLinkType
  targetId: string
}

export interface SmsTemplateCreateRequest {
  code: string
  name: string
  content: string
  remark?: string
}

export interface SmsTemplateUpdateRequest {
  name?: string
  content?: string
  remark?: string
}

export interface SendMessageRequest {
  channel: MessageChannel
  scene?: string
  templateCode?: string
  variables?: Record<string, string>
  content?: string
  recipientPhone: string
  customerId?: string
  bizKey?: string
}
