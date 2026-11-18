import { api } from './http'
import type { PageResponse } from '@/types/system'
import type {
  CallRecordDetailView,
  CallRecordView,
  CreateCallRequest,
  DispatchResult,
  LinkCallRequest,
  MessageView,
  PopupView,
  RecordingAccessView,
  SendMessageRequest,
  SmsTemplateCreateRequest,
  SmsTemplateUpdateRequest,
  SmsTemplateView
} from '@/types/callcenter'

export interface CallRecordQuery {
  keyword?: string
  direction?: string
  status?: string
  customerId?: string
  linkType?: string
  dateFrom?: string
  dateTo?: string
  page?: number
  pageSize?: number
}

export interface SmsTemplateQuery {
  keyword?: string
  status?: string
  page?: number
  pageSize?: number
}

export interface MessageQuery {
  status?: string
  channel?: string
  scene?: string
  bizKey?: string
  keyword?: string
  page?: number
  pageSize?: number
}

export function fetchIncomingPopup(phone: string): Promise<PopupView> {
  return api.get<PopupView>('/callcenter/incoming-popup', { phone })
}

export function fetchCallRecords(query: CallRecordQuery): Promise<PageResponse<CallRecordView>> {
  return api.get<PageResponse<CallRecordView>>('/callcenter/calls', query)
}

export function createCallRecord(payload: CreateCallRequest): Promise<CallRecordDetailView> {
  return api.post<CallRecordDetailView>('/callcenter/calls', payload)
}

export function fetchCallRecordDetail(callId: string): Promise<CallRecordDetailView> {
  return api.get<CallRecordDetailView>(`/callcenter/calls/${callId}`)
}

export function linkCallRecord(callId: string, payload: LinkCallRequest): Promise<CallRecordDetailView> {
  return api.post<CallRecordDetailView>(`/callcenter/calls/${callId}/link`, payload)
}

export function fetchRecordingAccess(callId: string): Promise<RecordingAccessView> {
  return api.get<RecordingAccessView>(`/callcenter/calls/${callId}/recording`)
}

export function fetchSmsTemplates(query: SmsTemplateQuery): Promise<PageResponse<SmsTemplateView>> {
  return api.get<PageResponse<SmsTemplateView>>('/callcenter/sms/templates', query)
}

export function createSmsTemplate(payload: SmsTemplateCreateRequest): Promise<SmsTemplateView> {
  return api.post<SmsTemplateView>('/callcenter/sms/templates', payload)
}

export function updateSmsTemplate(
  templateId: string,
  payload: SmsTemplateUpdateRequest
): Promise<SmsTemplateView> {
  return api.put<SmsTemplateView>(`/callcenter/sms/templates/${templateId}`, payload)
}

export function updateSmsTemplateStatus(
  templateId: string,
  status: 'active' | 'disabled'
): Promise<SmsTemplateView> {
  return api.post<SmsTemplateView>(`/callcenter/sms/templates/${templateId}/status`, { status })
}

export function sendMessage(payload: SendMessageRequest): Promise<MessageView> {
  return api.post<MessageView>('/callcenter/messages', payload)
}

export function fetchMessages(query: MessageQuery): Promise<PageResponse<MessageView>> {
  return api.get<PageResponse<MessageView>>('/callcenter/messages', query)
}

export function retryMessage(messageId: string): Promise<MessageView> {
  return api.post<MessageView>(`/callcenter/messages/${messageId}/retry`)
}

export function dispatchMessages(): Promise<DispatchResult> {
  return api.post<DispatchResult>('/callcenter/messages/dispatch')
}
