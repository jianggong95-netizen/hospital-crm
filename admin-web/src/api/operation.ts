import { api } from './http'
import type { PageResponse } from '@/types/system'
import type {
  ActivityView,
  Assignee,
  Channel,
  ContactRecord,
  Conversion,
  DuplicateLead,
  InvitationView,
  LeadCreatePayload,
  LeadDetail,
  LeadSummary,
  ParticipantView
} from '@/types/operation'

export interface LeadQuery {
  keyword?: string
  status?: string
  channelCode?: string
  ownerUserId?: string
  page?: number
  pageSize?: number
}

export interface InvitationQuery {
  keyword?: string
  status?: string
  ownerUserId?: string
  page?: number
  pageSize?: number
}

export interface ActivityQuery {
  keyword?: string
  status?: string
  page?: number
  pageSize?: number
}

export function fetchChannels(includeDisabled = false): Promise<Channel[]> {
  return api.get<Channel[]>('/operation/channels', { includeDisabled })
}

export function fetchLeads(query: LeadQuery = {}): Promise<PageResponse<LeadSummary>> {
  return api.get<PageResponse<LeadSummary>>('/operation/leads', query)
}

export function fetchLead(leadId: string): Promise<LeadDetail> {
  return api.get<LeadDetail>(`/operation/leads/${leadId}`)
}

export function createLead(payload: LeadCreatePayload): Promise<LeadSummary> {
  return api.post<LeadSummary>('/operation/leads', payload)
}

export function updateLead(leadId: string, payload: Partial<LeadCreatePayload>): Promise<LeadSummary> {
  return api.put<LeadSummary>(`/operation/leads/${leadId}`, payload)
}

export function changeLeadStatus(leadId: string, status: string, remark?: string): Promise<LeadSummary> {
  return api.put<LeadSummary>(`/operation/leads/${leadId}/status`, { status, remark })
}

export function convertLead(
  leadId: string,
  payload: { customerId?: string; createCustomer?: boolean; remark?: string }
): Promise<Conversion> {
  return api.post<Conversion>(`/operation/leads/${leadId}/convert`, payload)
}

export function mergeLead(leadId: string, targetLeadId: string, reason?: string): Promise<LeadSummary> {
  return api.post<LeadSummary>(`/operation/leads/${leadId}/merge`, { targetLeadId, reason })
}

export function assignLead(leadId: string, toUserId: string, reason?: string): Promise<LeadSummary> {
  return api.post<LeadSummary>(`/operation/leads/${leadId}/assign`, { toUserId, reason })
}

export function fetchAssignees(keyword?: string): Promise<Assignee[]> {
  return api.get<Assignee[]>('/operation/leads/assignees', { keyword })
}

export function fetchLeadContacts(leadId: string, page = 1, pageSize = 20): Promise<PageResponse<ContactRecord>> {
  return api.get<PageResponse<ContactRecord>>(`/operation/leads/${leadId}/contacts`, { page, pageSize })
}

export function addLeadContact(
  leadId: string,
  payload: { contactType: string; contactedAt?: string; content: string; resultCode: string; nextFollowUpAt?: string }
): Promise<ContactRecord> {
  return api.post<ContactRecord>(`/operation/leads/${leadId}/contacts`, payload)
}

export function fetchDuplicateLeads(phone?: string, idNo?: string): Promise<DuplicateLead[]> {
  return api.get<DuplicateLead[]>('/operation/leads/duplicates', { phone, idNo })
}

export function fetchInvitations(query: InvitationQuery = {}): Promise<PageResponse<InvitationView>> {
  return api.get<PageResponse<InvitationView>>('/operation/invitations', query)
}

export function createInvitation(payload: {
  leadId?: string
  customerId?: string
  channelCode: string
  title: string
  plannedAt: string
  ownerUserId?: string
  remark?: string
}): Promise<InvitationView> {
  return api.post<InvitationView>('/operation/invitations', payload)
}

export function changeInvitationStatus(
  invitationId: string,
  status: string,
  result?: string,
  remark?: string
): Promise<InvitationView> {
  return api.put<InvitationView>(`/operation/invitations/${invitationId}/status`, { status, result, remark })
}

export function fetchActivities(query: ActivityQuery = {}): Promise<PageResponse<ActivityView>> {
  return api.get<PageResponse<ActivityView>>('/operation/activities', query)
}

export function createActivity(payload: {
  activityName: string
  activityType?: string
  startAt: string
  endAt: string
  location?: string
  capacity?: number
  description?: string
}): Promise<ActivityView> {
  return api.post<ActivityView>('/operation/activities', payload)
}

export function changeActivityStatus(activityId: string, status: string): Promise<ActivityView> {
  return api.put<ActivityView>(`/operation/activities/${activityId}/status`, { status })
}

export function fetchParticipants(
  activityId: string,
  page = 1,
  pageSize = 20
): Promise<PageResponse<ParticipantView>> {
  return api.get<PageResponse<ParticipantView>>(`/operation/activities/${activityId}/participants`, {
    page,
    pageSize
  })
}

export function registerParticipant(
  activityId: string,
  payload: { leadId?: string; customerId?: string; participantName: string; phone?: string; remark?: string }
): Promise<ParticipantView> {
  return api.post<ParticipantView>(`/operation/activities/${activityId}/participants`, payload)
}

export function changeParticipantStatus(
  activityId: string,
  participantId: string,
  status: string,
  remark?: string
): Promise<ParticipantView> {
  return api.put<ParticipantView>(
    `/operation/activities/${activityId}/participants/${participantId}/status`,
    { status, remark }
  )
}
