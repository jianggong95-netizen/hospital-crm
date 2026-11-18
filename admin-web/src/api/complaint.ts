import { api } from './http'
import type { PageResponse } from '@/types/system'
import type {
  ComplaintAssignPayload,
  ComplaintCreatePayload,
  ComplaintStatus,
  ComplaintTicket,
  ComplaintTicketDetail
} from '@/types/complaint'

export interface ComplaintTicketQuery {
  keyword?: string
  ticketType?: string
  severity?: string
  status?: ComplaintStatus | ''
  assigneeId?: string
  customerId?: string
  escalated?: boolean
  overdue?: boolean
  page?: number
  pageSize?: number
}

export function fetchComplaintTickets(
  query: ComplaintTicketQuery
): Promise<PageResponse<ComplaintTicket>> {
  return api.get<PageResponse<ComplaintTicket>>('/complaints', query)
}

export function fetchComplaintDetail(ticketId: string): Promise<ComplaintTicketDetail> {
  return api.get<ComplaintTicketDetail>(`/complaints/${ticketId}`)
}

export function createComplaintTicket(payload: ComplaintCreatePayload): Promise<ComplaintTicket> {
  return api.post<ComplaintTicket>('/complaints', payload)
}

export function assignComplaintTicket(
  ticketId: string,
  payload: ComplaintAssignPayload
): Promise<ComplaintTicket> {
  return api.put<ComplaintTicket>(`/complaints/${ticketId}/assign`, payload)
}

export function startComplaintTicket(ticketId: string): Promise<ComplaintTicket> {
  return api.put<ComplaintTicket>(`/complaints/${ticketId}/start`)
}

export function addComplaintAction(
  ticketId: string,
  payload: { actionType: string; content: string }
): Promise<ComplaintTicketDetail> {
  return api.post<ComplaintTicketDetail>(`/complaints/${ticketId}/actions`, payload)
}

export function addComplaintRectification(
  ticketId: string,
  payload: { content: string; responsibleOrgId?: string; dueAt?: string }
): Promise<ComplaintTicketDetail> {
  return api.post<ComplaintTicketDetail>(`/complaints/${ticketId}/rectifications`, payload)
}

export function submitComplaintRectification(
  ticketId: string,
  rectificationId: string
): Promise<ComplaintTicketDetail> {
  return api.put<ComplaintTicketDetail>(
    `/complaints/${ticketId}/rectifications/${rectificationId}/submit`
  )
}

export function addComplaintFeedback(
  ticketId: string,
  payload: { rating: number; satisfied: boolean; comment?: string; channel?: string }
): Promise<ComplaintTicketDetail> {
  return api.post<ComplaintTicketDetail>(`/complaints/${ticketId}/feedback`, payload)
}

export function submitComplaintForReview(ticketId: string): Promise<ComplaintTicket> {
  return api.put<ComplaintTicket>(`/complaints/${ticketId}/submit-review`)
}

export function reviewComplaintTicket(
  ticketId: string,
  payload: { decision: 'PASS' | 'REJECT'; comment?: string }
): Promise<ComplaintTicketDetail> {
  return api.put<ComplaintTicketDetail>(`/complaints/${ticketId}/review`, payload)
}

export function escalateComplaintTicket(ticketId: string, reason: string): Promise<ComplaintTicket> {
  return api.put<ComplaintTicket>(`/complaints/${ticketId}/escalate`, { reason })
}

export function cancelComplaintTicket(ticketId: string, reason: string): Promise<ComplaintTicket> {
  return api.put<ComplaintTicket>(`/complaints/${ticketId}/cancel`, { reason })
}

export function scanComplaintEscalations(): Promise<{ escalated: number }> {
  return api.post<{ escalated: number }>('/complaints/escalation-scan')
}
