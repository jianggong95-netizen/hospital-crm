export type LeadStatus = 'NEW' | 'CONTACTING' | 'QUALIFIED' | 'INVITED' | 'CONVERTED' | 'INVALID' | 'MERGED'
export type InvitationStatus = 'PENDING' | 'CONTACTED' | 'ACCEPTED' | 'DECLINED' | 'CANCELLED' | 'EXPIRED'
export type ActivityStatus = 'DRAFT' | 'PUBLISHED' | 'CLOSED' | 'CANCELLED'
export type ParticipantStatus = 'REGISTERED' | 'ATTENDED' | 'CANCELLED' | 'NO_SHOW'

export interface Channel {
  id: string
  channelCode: string
  channelName: string
  channelType: string
  status: string
  remark?: string
}

export interface LeadSummary {
  id: string
  leadNo: string
  name: string
  gender: string
  birthDate?: string
  phoneMasked?: string
  idNoMasked?: string
  sourceChannelCode: string
  sourceChannelName: string
  status: LeadStatus
  ownerUserId?: string
  ownerName?: string
  orgId?: string
  convertedCustomerId?: string
  mergedIntoLeadId?: string
  createdAt: string
  updatedAt: string
}

export interface DuplicateLead {
  id: string
  leadNo: string
  name: string
  phoneMasked?: string
  status: LeadStatus
  createdAt: string
}

export interface Assignee {
  id: string
  username: string
  displayName: string
  orgId?: string
}

export interface ContactRecord {
  id: string
  contactType: string
  contactedAt: string
  contactedBy?: string
  contactedByName?: string
  content: string
  resultCode: string
  nextFollowUpAt?: string
}

export interface LeadAssignment {
  id: string
  fromUserId?: string
  fromUserName?: string
  toUserId: string
  toUserName?: string
  reason?: string
  assignedAt: string
}

export interface Conversion {
  id: string
  leadId: string
  customerId: string
  customerNo: string
  customerName: string
  conversionMode: string
  remark?: string
  convertedBy?: string
  convertedAt: string
}

export interface LeadDetail {
  summary: LeadSummary
  remark?: string
  convertedAt?: string
  recentContacts: ContactRecord[]
  assignments: LeadAssignment[]
  conversion?: Conversion
}

export interface LeadCreatePayload {
  name: string
  gender?: string
  birthDate?: string
  phone?: string
  idNo?: string
  sourceChannelCode: string
  ownerUserId?: string
  remark?: string
  forceCreate?: boolean
}

export interface InvitationView {
  id: string
  invitationNo: string
  leadId?: string
  leadNo?: string
  customerId?: string
  customerNo?: string
  customerName?: string
  channelCode: string
  channelName: string
  title: string
  plannedAt: string
  ownerUserId?: string
  ownerName?: string
  status: InvitationStatus
  result?: string
  remark?: string
  createdAt: string
  updatedAt: string
}

export interface ActivityView {
  id: string
  activityNo: string
  activityName: string
  activityType: string
  startAt: string
  endAt: string
  location?: string
  capacity?: number
  status: ActivityStatus
  description?: string
  participantCount: number
  createdAt: string
  updatedAt: string
}

export interface ParticipantView {
  id: string
  activityId: string
  leadId?: string
  customerId?: string
  participantName: string
  phoneMasked?: string
  status: ParticipantStatus
  registeredAt: string
  checkedInAt?: string
  remark?: string
}
