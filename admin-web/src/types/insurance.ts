export type CompanySettlementType = 'api' | 'email' | 'manual'

export type CompanyStatus = 'active' | 'disabled'

export interface CompanyView {
  id: string
  companyCode: string
  companyName: string
  contactName?: string
  contactPhoneMasked?: string
  contactEmail?: string
  settlementType: CompanySettlementType
  status: CompanyStatus
  remark?: string
  createdAt: string
  updatedAt: string
}

export interface CreateCompanyRequest {
  companyCode: string
  companyName: string
  contactName?: string
  contactPhone?: string
  contactEmail?: string
  settlementType?: CompanySettlementType
  remark?: string
}

export interface UpdateCompanyRequest {
  companyName?: string
  contactName?: string
  contactPhone?: string
  contactEmail?: string
  settlementType?: CompanySettlementType
  remark?: string
}

export type PolicyRelation = 'self' | 'spouse' | 'child' | 'parent' | 'other'

export type PolicyStatus = 'active' | 'expired' | 'terminated'

export interface PolicyView {
  id: string
  policyNo: string
  companyId: string
  companyName: string
  customerId: string
  customerName: string
  customerNo: string
  holderName?: string
  insuredName?: string
  relation: PolicyRelation
  validFrom?: string
  validTo?: string
  coverageAmount?: number
  status: PolicyStatus
  remark?: string
  createdAt: string
  updatedAt: string
}

export interface CreatePolicyRequest {
  policyNo: string
  companyId: string
  customerId: string
  holderName?: string
  insuredName?: string
  relation?: PolicyRelation
  validFrom?: string
  validTo?: string
  coverageAmount?: number
  remark?: string
}

export type CaseType = 'outpatient' | 'inpatient' | 'physical_exam' | 'other'

export type CaseState =
  | 'registered'
  | 'eligibility_check'
  | 'quotation'
  | 'pre_authorization'
  | 'service_done'
  | 'claim_submitted'
  | 'supplement_required'
  | 'under_review'
  | 'approved'
  | 'partially_approved'
  | 'rejected'
  | 'reconciled'

export interface CaseView {
  id: string
  caseNo: string
  companyId: string
  companyName: string
  policyId: string
  policyNo: string
  customerId: string
  customerName: string
  customerNo: string
  caseType: CaseType
  serviceDesc?: string
  state: CaseState
  hospitalReceivable: number
  patientPay: number
  estimatedPayout: number
  actualReceived: number
  quotationAmount?: number
  quotationAt?: string
  preauthNo?: string
  preauthAmount?: number
  preauthAt?: string
  claimNo?: string
  claimSubmittedAt?: string
  claimDecision?: string
  approvedAmount?: number
  decisionReason?: string
  decidedAt?: string
  handlerUserId?: string
  handlerName?: string
  reconciledAt?: string
  reconciliationDiff?: number
  remark?: string
  createdBy?: string
  createdAt: string
  updatedAt: string
}

export interface CaseDetailView {
  case: CaseView
  materials: MaterialView[]
  emails: EmailView[]
  events: EventView[]
}

export interface CreateCaseRequest {
  policyId: string
  caseType?: CaseType
  serviceDesc?: string
  hospitalReceivable?: number
  patientPay?: number
  remark?: string
}

export interface TransitionRequest {
  targetState: CaseState
  note?: string
  quotationAmount?: number
  preauthNo?: string
  preauthAmount?: number
  claimNo?: string
  hospitalReceivable?: number
  patientPay?: number
  approvedAmount?: number
  actualReceived?: number
}

export interface AssignRequest {
  handlerUserId: string
}

export type MaterialType =
  | 'diagnosis'
  | 'invoice'
  | 'detail'
  | 'prescription'
  | 'report'
  | 'other'

export type MaterialSource = 'upload' | 'email' | 'manual'

export type MaterialStatus = 'pending' | 'valid' | 'invalid'

export interface MaterialView {
  id: string
  caseId: string
  materialType: MaterialType
  fileName: string
  fileKey: string
  fileHash: string
  fileSize?: number
  source: MaterialSource
  status: MaterialStatus
  note?: string
  uploadedBy?: string
  uploadedByName?: string
  createdAt: string
}

export interface MaterialCreateRequest {
  materialType: MaterialType
  fileName: string
  fileKey: string
  fileHash: string
  fileSize?: number
  note?: string
}

export type EmailDirection = 'outbound' | 'inbound'

export type EmailStatus = 'sent' | 'received' | 'failed'

export interface AttachmentView {
  id: string
  fileName: string
  fileHash: string
  fileSize?: number
  materialId?: string
  createdAt: string
}

export interface EmailView {
  id: string
  caseId: string
  direction: EmailDirection
  messageId?: string
  subject: string
  fromAddress?: string
  toAddress?: string
  bodyExcerpt?: string
  sentAt?: string
  receivedAt?: string
  attachmentCount: number
  operatorUserId?: string
  operatorName?: string
  status: EmailStatus
  createdAt: string
  attachments: AttachmentView[]
}

export interface EmailCreateRequest {
  direction: EmailDirection
  messageId?: string
  subject: string
  fromAddress?: string
  toAddress?: string
  bodyExcerpt?: string
  sentAt?: string
  receivedAt?: string
  attachments: { fileName: string; fileHash: string; fileSize?: number }[]
}

export interface EventView {
  id: string
  eventType: string
  fromState?: string
  toState?: string
  operatorUserId?: string
  operatorName?: string
  detail?: Record<string, unknown>
  createdAt: string
}

export type ReconciliationStatus = 'pending' | 'matched' | 'mismatch'

export interface ReconciliationView {
  id: string
  companyId: string
  companyName: string
  period: string
  caseCount: number
  receivableAmount: number
  receivedAmount: number
  differenceAmount: number
  status: ReconciliationStatus
  confirmedBy?: string
  confirmedByName?: string
  confirmedAt?: string
  remark?: string
  createdAt: string
  updatedAt: string
}

export interface ReconciliationCreateRequest {
  companyId: string
  period: string
  remark?: string
}
