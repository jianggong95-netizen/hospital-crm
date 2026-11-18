export type ComplaintTicketType = 'complaint' | 'praise' | 'suggestion'

export type ComplaintSeverity = 'low' | 'normal' | 'high' | 'urgent'

export type ComplaintStatus =
  | 'OPEN'
  | 'ASSIGNED'
  | 'PROCESSING'
  | 'RECTIFYING'
  | 'PENDING_REVIEW'
  | 'CLOSED'
  | 'CANCELLED'

export type ComplaintSource = 'manual' | 'phone' | 'online' | 'h5' | 'mini_program' | 'his' | 'letter'

export type ComplaintRectificationStatus = 'PENDING' | 'SUBMITTED' | 'VERIFIED'

export interface ComplaintTicket {
  id: string
  ticketNo: string
  customerId: string
  customerName?: string
  phoneMasked?: string
  ticketType: ComplaintTicketType
  severity: ComplaintSeverity
  subject: string
  description?: string
  status: ComplaintStatus
  source: ComplaintSource
  responsibleOrgId?: string
  responsibleOrgName?: string
  assigneeId?: string
  assigneeName?: string
  dueAt?: string
  overdue: boolean
  escalated: boolean
  escalationLevel: number
  escalatedAt?: string
  escalationReason?: string
  createdAt: string
  updatedAt?: string
  closedAt?: string
  closedBy?: string
  closedByName?: string
}

export interface ComplaintActionView {
  id: string
  actionType: string
  fromStatus?: string
  toStatus?: string
  content?: string
  operatorId?: string
  operatorName?: string
  createdAt: string
}

export interface ComplaintRectification {
  id: string
  content: string
  responsibleOrgId?: string
  dueAt?: string
  status: ComplaintRectificationStatus
  submittedAt?: string
  verifiedAt?: string
  verifiedBy?: string
  createdAt: string
}

export interface ComplaintFeedback {
  id: string
  rating: number
  satisfied: boolean
  comment?: string
  channel: string
  createdAt: string
}

export interface ComplaintReview {
  id: string
  reviewerId: string
  reviewerName?: string
  decision: 'PASS' | 'REJECT'
  comment?: string
  createdAt: string
}

export interface ComplaintTicketDetail {
  ticket: ComplaintTicket
  actions: ComplaintActionView[]
  rectifications: ComplaintRectification[]
  feedbacks: ComplaintFeedback[]
  reviews: ComplaintReview[]
}

export interface ComplaintCreatePayload {
  customerId: string
  ticketType: ComplaintTicketType
  severity: ComplaintSeverity
  subject: string
  description: string
  source?: ComplaintSource
  responsibleOrgId?: string
  assigneeId?: string
  dueAt?: string
}

export interface ComplaintAssignPayload {
  assigneeId: string
  responsibleOrgId?: string
  dueAt?: string
}
