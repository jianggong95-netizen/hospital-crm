import { api } from './http'
import type { PageResponse } from '@/types/system'
import type {
  AssignRequest,
  CaseDetailView,
  CaseView,
  CompanyStatus,
  CompanyView,
  CreateCaseRequest,
  CreateCompanyRequest,
  CreatePolicyRequest,
  EmailCreateRequest,
  EmailView,
  EventView,
  MaterialCreateRequest,
  MaterialStatus,
  MaterialView,
  PolicyStatus,
  PolicyView,
  ReconciliationCreateRequest,
  ReconciliationView,
  TransitionRequest,
  UpdateCompanyRequest
} from '@/types/insurance'

export interface CompanyQuery {
  keyword?: string
  status?: string
  page?: number
  pageSize?: number
}

export interface PolicyQuery {
  keyword?: string
  companyId?: string
  customerId?: string
  status?: string
  page?: number
  pageSize?: number
}

export interface CaseQuery {
  keyword?: string
  companyId?: string
  policyId?: string
  state?: string
  customerId?: string
  handlerUserId?: string
  dateFrom?: string
  dateTo?: string
  page?: number
  pageSize?: number
}

export interface ReconciliationQuery {
  companyId?: string
  period?: string
  status?: string
  page?: number
  pageSize?: number
}

// ------------------------------------------------------------------
// 保险公司
// ------------------------------------------------------------------
export function fetchCompanies(query: CompanyQuery): Promise<PageResponse<CompanyView>> {
  return api.get<PageResponse<CompanyView>>('/insurance/companies', query)
}

export function fetchCompany(companyId: string): Promise<CompanyView> {
  return api.get<CompanyView>(`/insurance/companies/${companyId}`)
}

export function createCompany(payload: CreateCompanyRequest): Promise<CompanyView> {
  return api.post<CompanyView>('/insurance/companies', payload)
}

export function updateCompany(companyId: string, payload: UpdateCompanyRequest): Promise<CompanyView> {
  return api.put<CompanyView>(`/insurance/companies/${companyId}`, payload)
}

export function updateCompanyStatus(
  companyId: string,
  status: CompanyStatus
): Promise<CompanyView> {
  return api.post<CompanyView>(`/insurance/companies/${companyId}/status`, { status })
}

// ------------------------------------------------------------------
// 保单
// ------------------------------------------------------------------
export function fetchPolicies(query: PolicyQuery): Promise<PageResponse<PolicyView>> {
  return api.get<PageResponse<PolicyView>>('/insurance/policies', query)
}

export function fetchPolicy(policyId: string): Promise<PolicyView> {
  return api.get<PolicyView>(`/insurance/policies/${policyId}`)
}

export function createPolicy(payload: CreatePolicyRequest): Promise<PolicyView> {
  return api.post<PolicyView>('/insurance/policies', payload)
}

export function updatePolicyStatus(policyId: string, status: PolicyStatus): Promise<PolicyView> {
  return api.post<PolicyView>(`/insurance/policies/${policyId}/status`, { status })
}

// ------------------------------------------------------------------
// 商保案件
// ------------------------------------------------------------------
export function fetchCases(query: CaseQuery): Promise<PageResponse<CaseView>> {
  return api.get<PageResponse<CaseView>>('/insurance/cases', query)
}

export function createCase(payload: CreateCaseRequest): Promise<CaseView> {
  return api.post<CaseView>('/insurance/cases', payload)
}

export function fetchCaseDetail(caseId: string): Promise<CaseDetailView> {
  return api.get<CaseDetailView>(`/insurance/cases/${caseId}`)
}

export function assignCase(caseId: string, payload: AssignRequest): Promise<CaseView> {
  return api.post<CaseView>(`/insurance/cases/${caseId}/assign`, payload)
}

export function transitionCase(caseId: string, payload: TransitionRequest): Promise<CaseView> {
  return api.post<CaseView>(`/insurance/cases/${caseId}/transition`, payload)
}

export function fetchCaseEvents(caseId: string): Promise<EventView[]> {
  return api.get<EventView[]>(`/insurance/cases/${caseId}/events`)
}

// ------------------------------------------------------------------
// 材料
// ------------------------------------------------------------------
export function fetchMaterials(caseId: string): Promise<MaterialView[]> {
  return api.get<MaterialView[]>(`/insurance/cases/${caseId}/materials`)
}

export function addMaterial(caseId: string, payload: MaterialCreateRequest): Promise<MaterialView> {
  return api.post<MaterialView>(`/insurance/cases/${caseId}/materials`, payload)
}

export function updateMaterialStatus(
  materialId: string,
  status: MaterialStatus
): Promise<MaterialView> {
  return api.post<MaterialView>(`/insurance/materials/${materialId}/status`, { status })
}

// ------------------------------------------------------------------
// 邮件
// ------------------------------------------------------------------
export function fetchEmails(caseId: string): Promise<EmailView[]> {
  return api.get<EmailView[]>(`/insurance/cases/${caseId}/emails`)
}

export function addEmail(caseId: string, payload: EmailCreateRequest): Promise<EmailView> {
  return api.post<EmailView>(`/insurance/cases/${caseId}/emails`, payload)
}

// ------------------------------------------------------------------
// 对账中心
// ------------------------------------------------------------------
export function fetchReconciliations(
  query: ReconciliationQuery
): Promise<PageResponse<ReconciliationView>> {
  return api.get<PageResponse<ReconciliationView>>('/insurance/reconciliations', query)
}

export function fetchReconciliation(reconciliationId: string): Promise<ReconciliationView> {
  return api.get<ReconciliationView>(`/insurance/reconciliations/${reconciliationId}`)
}

export function createReconciliation(
  payload: ReconciliationCreateRequest
): Promise<ReconciliationView> {
  return api.post<ReconciliationView>('/insurance/reconciliations', payload)
}

export function confirmReconciliation(reconciliationId: string): Promise<ReconciliationView> {
  return api.post<ReconciliationView>(`/insurance/reconciliations/${reconciliationId}/confirm`)
}
