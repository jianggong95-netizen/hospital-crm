<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import {
  addEmail,
  addMaterial,
  assignCase,
  confirmReconciliation,
  createCase,
  createCompany,
  createPolicy,
  createReconciliation,
  fetchCaseDetail,
  fetchCases,
  fetchCompanies,
  fetchPolicies,
  fetchReconciliations,
  transitionCase,
  updateCompany,
  updateCompanyStatus,
  updateMaterialStatus,
  updatePolicyStatus
} from '@/api/insurance'
import type {
  CaseDetailView,
  CaseState,
  CaseType,
  CaseView,
  CompanySettlementType,
  CompanyStatus,
  CompanyView,
  EmailDirection,
  EmailView,
  MaterialStatus,
  MaterialType,
  MaterialView,
  PolicyRelation,
  PolicyStatus,
  PolicyView,
  ReconciliationStatus,
  ReconciliationView
} from '@/types/insurance'

const auth = useAuthStore()
const canManage = computed(() => auth.has('crm:insurance:manage'))
const canCaseWrite = computed(() => auth.has('crm:insurance:case:write'))
const canMaterialWrite = computed(() => auth.has('crm:insurance:material:write'))
const canEmailWrite = computed(() => auth.has('crm:insurance:email:write'))
const canReconcile = computed(() => auth.has('crm:insurance:reconcile'))

const activeTab = ref<'companies' | 'policies' | 'cases' | 'reconciliations'>('companies')

// ------------------------------------------------------------------
// 文案与格式
// ------------------------------------------------------------------
const settlementLabels: Record<CompanySettlementType, string> = {
  api: '接口直连',
  email: '邮件对账',
  manual: '手工处理'
}

const companyStatusLabels: Record<CompanyStatus, string> = { active: '启用', disabled: '停用' }

const relationLabels: Record<PolicyRelation, string> = {
  self: '本人',
  spouse: '配偶',
  child: '子女',
  parent: '父母',
  other: '其他'
}

const policyStatusLabels: Record<PolicyStatus, string> = {
  active: '生效中',
  expired: '已过期',
  terminated: '已终止'
}

const caseTypeLabels: Record<CaseType, string> = {
  outpatient: '门诊',
  inpatient: '住院',
  physical_exam: '体检',
  other: '其他'
}

const caseStateLabels: Record<CaseState, string> = {
  registered: '已登记',
  eligibility_check: '保障核验',
  quotation: '询价',
  pre_authorization: '预授权',
  service_done: '服务完成',
  claim_submitted: '已提交理赔',
  supplement_required: '待补件',
  under_review: '审核中',
  approved: '审核通过',
  partially_approved: '部分通过',
  rejected: '拒赔',
  reconciled: '已对账'
}

const caseNextStates: Record<CaseState, CaseState[]> = {
  registered: ['eligibility_check'],
  eligibility_check: ['quotation'],
  quotation: ['pre_authorization'],
  pre_authorization: ['service_done'],
  service_done: ['claim_submitted'],
  claim_submitted: ['supplement_required', 'under_review'],
  supplement_required: ['under_review'],
  under_review: ['approved', 'partially_approved', 'rejected'],
  approved: ['reconciled'],
  partially_approved: ['reconciled'],
  rejected: ['reconciled'],
  reconciled: []
}

const materialTypeLabels: Record<MaterialType, string> = {
  diagnosis: '诊断证明',
  invoice: '发票',
  detail: '费用明细',
  prescription: '处方',
  report: '检查报告',
  other: '其他'
}

const materialSourceLabels: Record<string, string> = {
  upload: '上传',
  email: '邮件',
  manual: '手工'
}

const materialStatusLabels: Record<MaterialStatus, string> = {
  pending: '待校验',
  valid: '有效',
  invalid: '无效'
}

const emailStatusLabels: Record<string, string> = {
  sent: '已发送',
  received: '已接收',
  failed: '失败'
}

const reconciliationStatusLabels: Record<ReconciliationStatus, string> = {
  pending: '待确认',
  matched: '已匹配',
  mismatch: '有差异'
}

function caseStateTag(state: CaseState) {
  if (state === 'approved' || state === 'partially_approved' || state === 'reconciled') return 'success'
  if (state === 'rejected') return 'danger'
  if (state === 'under_review' || state === 'supplement_required' || state === 'claim_submitted') {
    return 'warning'
  }
  if (state === 'registered' || state === 'service_done') return 'info'
  return 'primary'
}

function policyStatusTag(status: PolicyStatus) {
  if (status === 'active') return 'success'
  if (status === 'expired') return 'warning'
  return 'info'
}

function materialStatusTag(status: MaterialStatus) {
  if (status === 'valid') return 'success'
  if (status === 'invalid') return 'danger'
  return 'warning'
}

function reconciliationStatusTag(status: ReconciliationStatus) {
  if (status === 'matched') return 'success'
  if (status === 'mismatch') return 'danger'
  return 'warning'
}

function formatTime(value?: string) {
  if (!value) return '—'
  return value.replace('T', ' ').slice(0, 16)
}

function formatAmount(value?: number) {
  if (value === undefined || value === null) return '—'
  return `¥${value.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`
}

function formatSize(bytes?: number) {
  if (bytes === undefined || bytes === null) return '—'
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / 1024 / 1024).toFixed(1)} MB`
}

function differenceClass(value?: number) {
  if (value === undefined || value === null || value === 0) return 'diff-zero'
  return value > 0 ? 'diff-positive' : 'diff-negative'
}

function stateLabel(state?: string) {
  if (!state) return '—'
  return caseStateLabels[state as CaseState] ?? state
}

function toInstant(value?: string) {
  if (!value) return undefined
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? undefined : date.toISOString()
}

const HASH_PATTERN = /^[a-fA-F0-9]{64}$/

function isHashInvalid(value: string) {
  return !HASH_PATTERN.test(value.trim())
}

// ------------------------------------------------------------------
// 保险公司
// ------------------------------------------------------------------
const companies = ref<CompanyView[]>([])
const companiesLoading = ref(false)
const companiesTotal = ref(0)
const companiesQuery = reactive({ keyword: '', status: '', page: 1, pageSize: 20 })
const companyOptions = ref<CompanyView[]>([])

async function loadCompanies() {
  companiesLoading.value = true
  try {
    const result = await fetchCompanies({
      keyword: companiesQuery.keyword || undefined,
      status: companiesQuery.status || undefined,
      page: companiesQuery.page,
      pageSize: companiesQuery.pageSize
    })
    companies.value = result.items
    companiesTotal.value = result.total
  } catch (error) {
    ElMessage.error((error as Error).message)
  } finally {
    companiesLoading.value = false
  }
}

function searchCompanies() {
  companiesQuery.page = 1
  void loadCompanies()
}

async function loadCompanyOptions() {
  try {
    const result = await fetchCompanies({ page: 1, pageSize: 200 })
    companyOptions.value = result.items
  } catch (error) {
    ElMessage.error((error as Error).message)
  }
}

const companyDialog = reactive({
  visible: false,
  editingId: '',
  companyCode: '',
  companyName: '',
  contactName: '',
  contactPhone: '',
  contactEmail: '',
  settlementType: 'api' as CompanySettlementType,
  remark: ''
})

function openCompanyDialog(row?: CompanyView) {
  companyDialog.editingId = row?.id ?? ''
  companyDialog.companyCode = row?.companyCode ?? ''
  companyDialog.companyName = row?.companyName ?? ''
  companyDialog.contactName = row?.contactName ?? ''
  companyDialog.contactPhone = ''
  companyDialog.contactEmail = row?.contactEmail ?? ''
  companyDialog.settlementType = row?.settlementType ?? 'api'
  companyDialog.remark = row?.remark ?? ''
  companyDialog.visible = true
}

async function submitCompany() {
  if (!companyDialog.companyName.trim()) {
    ElMessage.warning('请填写保险公司名称')
    return
  }
  if (!companyDialog.editingId && !companyDialog.companyCode.trim()) {
    ElMessage.warning('请填写公司编码')
    return
  }
  try {
    if (companyDialog.editingId) {
      await updateCompany(companyDialog.editingId, {
        companyName: companyDialog.companyName.trim(),
        contactName: companyDialog.contactName.trim() || undefined,
        contactPhone: companyDialog.contactPhone.trim() || undefined,
        contactEmail: companyDialog.contactEmail.trim() || undefined,
        settlementType: companyDialog.settlementType,
        remark: companyDialog.remark.trim() || undefined
      })
      ElMessage.success('保险公司已更新')
    } else {
      await createCompany({
        companyCode: companyDialog.companyCode.trim(),
        companyName: companyDialog.companyName.trim(),
        contactName: companyDialog.contactName.trim() || undefined,
        contactPhone: companyDialog.contactPhone.trim() || undefined,
        contactEmail: companyDialog.contactEmail.trim() || undefined,
        settlementType: companyDialog.settlementType,
        remark: companyDialog.remark.trim() || undefined
      })
      ElMessage.success('保险公司已创建')
    }
    companyDialog.visible = false
    await Promise.all([loadCompanies(), loadCompanyOptions()])
  } catch (error) {
    ElMessage.error((error as Error).message)
  }
}

async function toggleCompanyStatus(row: CompanyView) {
  const next: CompanyStatus = row.status === 'active' ? 'disabled' : 'active'
  try {
    await ElMessageBox.confirm(
      `确认${next === 'active' ? '启用' : '停用'}「${row.companyName}」？`,
      next === 'active' ? '启用保险公司' : '停用保险公司',
      { confirmButtonText: '确认', cancelButtonText: '取消', type: 'warning' }
    )
  } catch {
    return
  }
  try {
    await updateCompanyStatus(row.id, next)
    ElMessage.success(next === 'active' ? '已启用' : '已停用')
    await Promise.all([loadCompanies(), loadCompanyOptions()])
  } catch (error) {
    ElMessage.error((error as Error).message)
  }
}

// ------------------------------------------------------------------
// 保单
// ------------------------------------------------------------------
const policies = ref<PolicyView[]>([])
const policiesLoading = ref(false)
const policiesTotal = ref(0)
const policiesQuery = reactive({
  keyword: '',
  companyId: '',
  status: '',
  page: 1,
  pageSize: 20
})
const policyOptions = ref<PolicyView[]>([])

async function loadPolicies() {
  policiesLoading.value = true
  try {
    const result = await fetchPolicies({
      keyword: policiesQuery.keyword || undefined,
      companyId: policiesQuery.companyId || undefined,
      status: policiesQuery.status || undefined,
      page: policiesQuery.page,
      pageSize: policiesQuery.pageSize
    })
    policies.value = result.items
    policiesTotal.value = result.total
  } catch (error) {
    ElMessage.error((error as Error).message)
  } finally {
    policiesLoading.value = false
  }
}

function searchPolicies() {
  policiesQuery.page = 1
  void loadPolicies()
}

async function loadPolicyOptions() {
  try {
    const result = await fetchPolicies({ status: 'active', page: 1, pageSize: 200 })
    policyOptions.value = result.items
  } catch (error) {
    ElMessage.error((error as Error).message)
  }
}

const policyDialog = reactive({
  visible: false,
  companyId: '',
  customerId: '',
  policyNo: '',
  holderName: '',
  insuredName: '',
  relation: 'self' as PolicyRelation,
  validFrom: '',
  validTo: '',
  coverageAmount: undefined as number | undefined,
  remark: ''
})

function openPolicyDialog() {
  policyDialog.companyId = ''
  policyDialog.customerId = ''
  policyDialog.policyNo = ''
  policyDialog.holderName = ''
  policyDialog.insuredName = ''
  policyDialog.relation = 'self'
  policyDialog.validFrom = ''
  policyDialog.validTo = ''
  policyDialog.coverageAmount = undefined
  policyDialog.remark = ''
  policyDialog.visible = true
}

async function submitPolicy() {
  if (!policyDialog.companyId) {
    ElMessage.warning('请选择保险公司')
    return
  }
  if (!policyDialog.customerId.trim()) {
    ElMessage.warning('请填写客户 ID')
    return
  }
  if (!policyDialog.policyNo.trim()) {
    ElMessage.warning('请填写保单号')
    return
  }
  try {
    await createPolicy({
      policyNo: policyDialog.policyNo.trim(),
      companyId: policyDialog.companyId,
      customerId: policyDialog.customerId.trim(),
      holderName: policyDialog.holderName.trim() || undefined,
      insuredName: policyDialog.insuredName.trim() || undefined,
      relation: policyDialog.relation,
      validFrom: policyDialog.validFrom || undefined,
      validTo: policyDialog.validTo || undefined,
      coverageAmount: policyDialog.coverageAmount,
      remark: policyDialog.remark.trim() || undefined
    })
    ElMessage.success('保单已创建')
    policyDialog.visible = false
    await Promise.all([loadPolicies(), loadPolicyOptions()])
  } catch (error) {
    ElMessage.error((error as Error).message)
  }
}

const policyStatusDialog = reactive({
  visible: false,
  policyId: '',
  policyNo: '',
  status: 'active' as PolicyStatus
})

function openPolicyStatusDialog(row: PolicyView) {
  policyStatusDialog.policyId = row.id
  policyStatusDialog.policyNo = row.policyNo
  policyStatusDialog.status = row.status
  policyStatusDialog.visible = true
}

async function submitPolicyStatus() {
  try {
    await updatePolicyStatus(policyStatusDialog.policyId, policyStatusDialog.status)
    ElMessage.success('保单状态已更新')
    policyStatusDialog.visible = false
    await Promise.all([loadPolicies(), loadPolicyOptions()])
  } catch (error) {
    ElMessage.error((error as Error).message)
  }
}

// ------------------------------------------------------------------
// 商保案件
// ------------------------------------------------------------------
const cases = ref<CaseView[]>([])
const casesLoading = ref(false)
const casesTotal = ref(0)
const casesQuery = reactive({
  keyword: '',
  companyId: '',
  state: '',
  dateRange: null as [string, string] | null,
  page: 1,
  pageSize: 20
})

async function loadCases() {
  casesLoading.value = true
  try {
    const result = await fetchCases({
      keyword: casesQuery.keyword || undefined,
      companyId: casesQuery.companyId || undefined,
      state: casesQuery.state || undefined,
      dateFrom: casesQuery.dateRange?.[0],
      dateTo: casesQuery.dateRange?.[1],
      page: casesQuery.page,
      pageSize: casesQuery.pageSize
    })
    cases.value = result.items
    casesTotal.value = result.total
  } catch (error) {
    ElMessage.error((error as Error).message)
  } finally {
    casesLoading.value = false
  }
}

function searchCases() {
  casesQuery.page = 1
  void loadCases()
}

const caseDialog = reactive({
  visible: false,
  policyId: '',
  caseType: 'outpatient' as CaseType,
  serviceDesc: '',
  hospitalReceivable: undefined as number | undefined,
  patientPay: undefined as number | undefined,
  remark: ''
})

async function openCaseDialog() {
  caseDialog.policyId = ''
  caseDialog.caseType = 'outpatient'
  caseDialog.serviceDesc = ''
  caseDialog.hospitalReceivable = undefined
  caseDialog.patientPay = undefined
  caseDialog.remark = ''
  caseDialog.visible = true
  if (!policyOptions.value.length) {
    await loadPolicyOptions()
  }
}

async function submitCase() {
  if (!caseDialog.policyId) {
    ElMessage.warning('请选择保单')
    return
  }
  try {
    await createCase({
      policyId: caseDialog.policyId,
      caseType: caseDialog.caseType,
      serviceDesc: caseDialog.serviceDesc.trim() || undefined,
      hospitalReceivable: caseDialog.hospitalReceivable,
      patientPay: caseDialog.patientPay,
      remark: caseDialog.remark.trim() || undefined
    })
    ElMessage.success('商保案件已登记')
    caseDialog.visible = false
    await loadCases()
  } catch (error) {
    ElMessage.error((error as Error).message)
  }
}

// ------------------------------------------------------------------
// 案件详情抽屉
// ------------------------------------------------------------------
const detail = ref<CaseDetailView>()
const detailVisible = ref(false)
const detailLoading = ref(false)

const currentCase = computed(() => detail.value?.case)

const transitionTargets = computed<CaseState[]>(() => {
  const state = currentCase.value?.state
  return state ? caseNextStates[state] ?? [] : []
})

const sortedEvents = computed(() =>
  (detail.value?.events ?? []).slice().sort((a, b) => b.createdAt.localeCompare(a.createdAt))
)

async function openCaseDetail(caseId: string) {
  detailVisible.value = true
  detailLoading.value = true
  try {
    detail.value = await fetchCaseDetail(caseId)
  } catch (error) {
    ElMessage.error((error as Error).message)
    detailVisible.value = false
  } finally {
    detailLoading.value = false
  }
}

async function reloadDetail() {
  const caseId = detail.value?.case.id
  if (!caseId) return
  try {
    detail.value = await fetchCaseDetail(caseId)
  } catch (error) {
    ElMessage.error((error as Error).message)
  }
}

// 状态流转
const transitionDialog = reactive({
  visible: false,
  targetState: 'quotation' as CaseState,
  note: '',
  quotationAmount: undefined as number | undefined,
  preauthNo: '',
  preauthAmount: undefined as number | undefined,
  claimNo: '',
  hospitalReceivable: undefined as number | undefined,
  patientPay: undefined as number | undefined,
  approvedAmount: undefined as number | undefined,
  decisionReason: '',
  actualReceived: undefined as number | undefined
})

function openTransition(target: CaseState) {
  transitionDialog.targetState = target
  transitionDialog.note = ''
  transitionDialog.quotationAmount = undefined
  transitionDialog.preauthNo = ''
  transitionDialog.preauthAmount = undefined
  transitionDialog.claimNo = ''
  transitionDialog.hospitalReceivable = currentCase.value?.hospitalReceivable
  transitionDialog.patientPay = currentCase.value?.patientPay
  transitionDialog.approvedAmount = undefined
  transitionDialog.decisionReason = ''
  transitionDialog.actualReceived = undefined
  transitionDialog.visible = true
}

function isAmountMissing(value?: number) {
  return value === undefined || value === null
}

async function submitTransition() {
  const caseId = detail.value?.case.id
  if (!caseId) return
  const target = transitionDialog.targetState

  if (target === 'quotation' && isAmountMissing(transitionDialog.quotationAmount)) {
    ElMessage.warning('请填写询价金额')
    return
  }
  if (target === 'pre_authorization') {
    if (!transitionDialog.preauthNo.trim()) {
      ElMessage.warning('请填写预授权号')
      return
    }
    if (isAmountMissing(transitionDialog.preauthAmount)) {
      ElMessage.warning('请填写预授权金额')
      return
    }
  }
  if (target === 'claim_submitted') {
    if (!transitionDialog.claimNo.trim()) {
      ElMessage.warning('请填写理赔号')
      return
    }
    if (isAmountMissing(transitionDialog.hospitalReceivable)) {
      ElMessage.warning('请填写医院应收金额')
      return
    }
    if (isAmountMissing(transitionDialog.patientPay)) {
      ElMessage.warning('请填写患者自付金额')
      return
    }
  }
  if (
    (target === 'approved' || target === 'partially_approved') &&
    isAmountMissing(transitionDialog.approvedAmount)
  ) {
    ElMessage.warning('请填写核定金额')
    return
  }
  if (target === 'rejected' && !transitionDialog.decisionReason.trim()) {
    ElMessage.warning('请填写拒赔原因')
    return
  }
  if (target === 'reconciled' && isAmountMissing(transitionDialog.actualReceived)) {
    ElMessage.warning('请填写实际到账金额')
    return
  }

  let note = transitionDialog.note.trim()
  if (target === 'rejected') {
    const reason = transitionDialog.decisionReason.trim()
    note = `拒赔原因：${reason}${note ? `；备注：${note}` : ''}`
  }

  try {
    await transitionCase(caseId, {
      targetState: target,
      note: note || undefined,
      quotationAmount: target === 'quotation' ? transitionDialog.quotationAmount : undefined,
      preauthNo:
        target === 'pre_authorization' ? transitionDialog.preauthNo.trim() : undefined,
      preauthAmount: target === 'pre_authorization' ? transitionDialog.preauthAmount : undefined,
      claimNo: target === 'claim_submitted' ? transitionDialog.claimNo.trim() : undefined,
      hospitalReceivable:
        target === 'claim_submitted' ? transitionDialog.hospitalReceivable : undefined,
      patientPay: target === 'claim_submitted' ? transitionDialog.patientPay : undefined,
      approvedAmount:
        target === 'approved' || target === 'partially_approved'
          ? transitionDialog.approvedAmount
          : undefined,
      actualReceived: target === 'reconciled' ? transitionDialog.actualReceived : undefined
    })
    ElMessage.success(`已流转至「${caseStateLabels[target]}」`)
    transitionDialog.visible = false
    await Promise.all([loadCases(), reloadDetail()])
  } catch (error) {
    ElMessage.error((error as Error).message)
  }
}

// 分派处理人
const assignDialog = reactive({ visible: false, handlerUserId: '' })

function openAssignDialog() {
  assignDialog.handlerUserId = currentCase.value?.handlerUserId ?? ''
  assignDialog.visible = true
}

async function submitAssign() {
  const caseId = detail.value?.case.id
  if (!caseId) return
  if (!assignDialog.handlerUserId.trim()) {
    ElMessage.warning('请填写处理人用户 ID')
    return
  }
  try {
    await assignCase(caseId, { handlerUserId: assignDialog.handlerUserId.trim() })
    ElMessage.success('处理人已分派')
    assignDialog.visible = false
    await Promise.all([loadCases(), reloadDetail()])
  } catch (error) {
    ElMessage.error((error as Error).message)
  }
}

// ------------------------------------------------------------------
// 材料
// ------------------------------------------------------------------
const materialDialog = reactive({
  visible: false,
  materialType: 'invoice' as MaterialType,
  fileName: '',
  fileKey: '',
  fileHash: '',
  fileSize: undefined as number | undefined,
  note: ''
})

function openMaterialDialog() {
  materialDialog.materialType = 'invoice'
  materialDialog.fileName = ''
  materialDialog.fileKey = ''
  materialDialog.fileHash = ''
  materialDialog.fileSize = undefined
  materialDialog.note = ''
  materialDialog.visible = true
}

async function submitMaterial() {
  const caseId = detail.value?.case.id
  if (!caseId) return
  if (!materialDialog.fileName.trim()) {
    ElMessage.warning('请填写文件名')
    return
  }
  if (!materialDialog.fileKey.trim()) {
    ElMessage.warning('请填写文件 Key')
    return
  }
  if (isHashInvalid(materialDialog.fileHash)) {
    ElMessage.warning('文件哈希需为 64 位十六进制（SHA-256）')
    return
  }
  try {
    await addMaterial(caseId, {
      materialType: materialDialog.materialType,
      fileName: materialDialog.fileName.trim(),
      fileKey: materialDialog.fileKey.trim(),
      fileHash: materialDialog.fileHash.trim(),
      fileSize: materialDialog.fileSize,
      note: materialDialog.note.trim() || undefined
    })
    ElMessage.success('材料已登记')
    materialDialog.visible = false
    await reloadDetail()
  } catch (error) {
    ElMessage.error((error as Error).message)
  }
}

async function changeMaterialStatus(row: MaterialView, status: MaterialStatus) {
  try {
    await updateMaterialStatus(row.id, status)
    ElMessage.success(`材料已标记为${materialStatusLabels[status]}`)
    await reloadDetail()
  } catch (error) {
    ElMessage.error((error as Error).message)
  }
}

// ------------------------------------------------------------------
// 邮件
// ------------------------------------------------------------------
interface AttachmentDraft {
  fileName: string
  fileHash: string
  fileSize?: number
}

const emailDialog = reactive({
  visible: false,
  direction: 'outbound' as EmailDirection,
  subject: '',
  messageId: '',
  fromAddress: '',
  toAddress: '',
  bodyExcerpt: '',
  sentAt: '',
  receivedAt: '',
  attachments: [] as AttachmentDraft[]
})

function openEmailDialog() {
  emailDialog.direction = 'outbound'
  emailDialog.subject = ''
  emailDialog.messageId = ''
  emailDialog.fromAddress = ''
  emailDialog.toAddress = ''
  emailDialog.bodyExcerpt = ''
  emailDialog.sentAt = ''
  emailDialog.receivedAt = ''
  emailDialog.attachments = []
  emailDialog.visible = true
}

function addAttachmentRow() {
  emailDialog.attachments.push({ fileName: '', fileHash: '', fileSize: undefined })
}

function removeAttachmentRow(index: number) {
  emailDialog.attachments.splice(index, 1)
}

async function submitEmail() {
  const caseId = detail.value?.case.id
  if (!caseId) return
  if (!emailDialog.subject.trim()) {
    ElMessage.warning('请填写邮件主题')
    return
  }
  for (const [index, item] of emailDialog.attachments.entries()) {
    if (!item.fileName.trim()) {
      ElMessage.warning(`第 ${index + 1} 个附件缺少文件名`)
      return
    }
    if (isHashInvalid(item.fileHash)) {
      ElMessage.warning(`第 ${index + 1} 个附件哈希需为 64 位十六进制`)
      return
    }
  }
  try {
    await addEmail(caseId, {
      direction: emailDialog.direction,
      messageId: emailDialog.messageId.trim() || undefined,
      subject: emailDialog.subject.trim(),
      fromAddress: emailDialog.fromAddress.trim() || undefined,
      toAddress: emailDialog.toAddress.trim() || undefined,
      bodyExcerpt: emailDialog.bodyExcerpt.trim() || undefined,
      sentAt: emailDialog.direction === 'outbound' ? toInstant(emailDialog.sentAt) : undefined,
      receivedAt: emailDialog.direction === 'inbound' ? toInstant(emailDialog.receivedAt) : undefined,
      attachments: emailDialog.attachments.map((item) => ({
        fileName: item.fileName.trim(),
        fileHash: item.fileHash.trim(),
        fileSize: item.fileSize
      }))
    })
    ElMessage.success('邮件记录已保存')
    emailDialog.visible = false
    await reloadDetail()
  } catch (error) {
    ElMessage.error((error as Error).message)
  }
}

function emailTime(row: EmailView) {
  return row.direction === 'inbound' ? row.receivedAt ?? row.sentAt : row.sentAt
}

// ------------------------------------------------------------------
// 对账中心
// ------------------------------------------------------------------
const reconciliations = ref<ReconciliationView[]>([])
const reconciliationsLoading = ref(false)
const reconciliationsTotal = ref(0)
const reconciliationsQuery = reactive({
  companyId: '',
  period: '',
  status: '',
  page: 1,
  pageSize: 20
})

async function loadReconciliations() {
  reconciliationsLoading.value = true
  try {
    const result = await fetchReconciliations({
      companyId: reconciliationsQuery.companyId || undefined,
      period: reconciliationsQuery.period || undefined,
      status: reconciliationsQuery.status || undefined,
      page: reconciliationsQuery.page,
      pageSize: reconciliationsQuery.pageSize
    })
    reconciliations.value = result.items
    reconciliationsTotal.value = result.total
  } catch (error) {
    ElMessage.error((error as Error).message)
  } finally {
    reconciliationsLoading.value = false
  }
}

function searchReconciliations() {
  reconciliationsQuery.page = 1
  void loadReconciliations()
}

const reconciliationDialog = reactive({
  visible: false,
  companyId: '',
  period: '',
  remark: ''
})

function openReconciliationDialog() {
  reconciliationDialog.companyId = ''
  reconciliationDialog.period = ''
  reconciliationDialog.remark = ''
  reconciliationDialog.visible = true
}

async function submitReconciliation() {
  if (!reconciliationDialog.companyId) {
    ElMessage.warning('请选择保险公司')
    return
  }
  if (!reconciliationDialog.period) {
    ElMessage.warning('请选择账期')
    return
  }
  try {
    await createReconciliation({
      companyId: reconciliationDialog.companyId,
      period: reconciliationDialog.period,
      remark: reconciliationDialog.remark.trim() || undefined
    })
    ElMessage.success('对账单已生成')
    reconciliationDialog.visible = false
    await loadReconciliations()
  } catch (error) {
    ElMessage.error((error as Error).message)
  }
}

async function handleConfirmReconciliation(row: ReconciliationView) {
  try {
    await ElMessageBox.confirm(
      `确认「${row.companyName} · ${row.period}」对账单？确认后不可撤销。`,
      '确认对账',
      { confirmButtonText: '确认对账', cancelButtonText: '取消', type: 'warning' }
    )
  } catch {
    return
  }
  try {
    await confirmReconciliation(row.id)
    ElMessage.success('对账单已确认')
    await loadReconciliations()
  } catch (error) {
    ElMessage.error((error as Error).message)
  }
}

// ------------------------------------------------------------------
// 标签页切换与初始化
// ------------------------------------------------------------------
function onTabChange(name: string | number) {
  if (name === 'policies' && !policies.value.length) {
    void loadPolicies()
  }
  if (name === 'cases' && !cases.value.length) {
    void loadCases()
  }
  if (name === 'reconciliations' && !reconciliations.value.length) {
    void loadReconciliations()
  }
}

onMounted(async () => {
  await Promise.all([loadCompanies(), loadCompanyOptions()])
})
</script>

<template>
  <section class="ins-page">
    <header class="page-head">
      <div>
        <h2>商保中心</h2>
        <p class="page-desc">
          统一维护合作保险公司、客户保单与商保案件，覆盖保障核验、询价、预授权、理赔提交与对账全流程；
          案件材料、往来邮件与状态流转全程留痕，预计赔付与实收金额分列核对，差额一目了然。
        </p>
      </div>
    </header>

    <el-tabs v-model="activeTab" class="ins-tabs" @tab-change="onTabChange">
      <!-- ============================================================ -->
      <el-tab-pane label="保险公司" name="companies">
        <div class="toolbar">
          <el-input
            v-model="companiesQuery.keyword"
            placeholder="编码 / 名称 / 联系人"
            clearable
            size="small"
            style="width: 200px"
            @keyup.enter="searchCompanies"
          />
          <el-select v-model="companiesQuery.status" placeholder="全部状态" clearable size="small" style="width: 120px">
            <el-option label="启用" value="active" />
            <el-option label="停用" value="disabled" />
          </el-select>
          <el-button size="small" type="primary" @click="searchCompanies">查询</el-button>
          <span class="spacer" />
          <el-button v-if="canManage" size="small" type="primary" @click="openCompanyDialog()">
            新建保险公司
          </el-button>
        </div>

        <div class="panel-card">
          <el-table v-loading="companiesLoading" :data="companies" size="small">
            <el-table-column prop="companyCode" label="编码" width="130" />
            <el-table-column prop="companyName" label="公司名称" min-width="180" show-overflow-tooltip />
            <el-table-column label="联系人" width="100">
              <template #default="{ row }">{{ row.contactName || '—' }}</template>
            </el-table-column>
            <el-table-column label="联系电话（脱敏）" width="140">
              <template #default="{ row }">{{ row.contactPhoneMasked || '—' }}</template>
            </el-table-column>
            <el-table-column label="邮箱" min-width="180" show-overflow-tooltip>
              <template #default="{ row }">{{ row.contactEmail || '—' }}</template>
            </el-table-column>
            <el-table-column label="结算方式" width="110">
              <template #default="{ row }">
                {{ settlementLabels[row.settlementType as CompanySettlementType] ?? row.settlementType }}
              </template>
            </el-table-column>
            <el-table-column label="状态" width="90">
              <template #default="{ row }">
                <el-tag :type="row.status === 'active' ? 'success' : 'info'" size="small">
                  {{ companyStatusLabels[row.status as CompanyStatus] }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="更新时间" width="140">
              <template #default="{ row }">{{ formatTime(row.updatedAt) }}</template>
            </el-table-column>
            <el-table-column v-if="canManage" label="操作" width="130" fixed="right">
              <template #default="{ row }">
                <el-button size="small" link type="primary" @click="openCompanyDialog(row)">编辑</el-button>
                <el-button
                  size="small"
                  link
                  :type="row.status === 'active' ? 'danger' : 'success'"
                  @click="toggleCompanyStatus(row)"
                >
                  {{ row.status === 'active' ? '停用' : '启用' }}
                </el-button>
              </template>
            </el-table-column>
          </el-table>
          <el-pagination
            v-if="companiesTotal > companiesQuery.pageSize"
            v-model:current-page="companiesQuery.page"
            :page-size="companiesQuery.pageSize"
            :total="companiesTotal"
            layout="prev, pager, next"
            small
            @current-change="loadCompanies"
          />
        </div>
      </el-tab-pane>

      <!-- ============================================================ -->
      <el-tab-pane label="保单" name="policies">
        <div class="toolbar">
          <el-select
            v-model="policiesQuery.companyId"
            placeholder="全部保险公司"
            clearable
            filterable
            size="small"
            style="width: 180px"
          >
            <el-option v-for="item in companyOptions" :key="item.id" :label="item.companyName" :value="item.id" />
          </el-select>
          <el-input
            v-model="policiesQuery.keyword"
            placeholder="保单号 / 客户"
            clearable
            size="small"
            style="width: 180px"
            @keyup.enter="searchPolicies"
          />
          <el-select v-model="policiesQuery.status" placeholder="全部状态" clearable size="small" style="width: 120px">
            <el-option label="生效中" value="active" />
            <el-option label="已过期" value="expired" />
            <el-option label="已终止" value="terminated" />
          </el-select>
          <el-button size="small" type="primary" @click="searchPolicies">查询</el-button>
          <span class="spacer" />
          <el-button v-if="canManage" size="small" type="primary" @click="openPolicyDialog">
            新建保单
          </el-button>
        </div>

        <div class="panel-card">
          <el-table v-loading="policiesLoading" :data="policies" size="small">
            <el-table-column prop="policyNo" label="保单号" width="150" />
            <el-table-column prop="companyName" label="保险公司" min-width="150" show-overflow-tooltip />
            <el-table-column label="客户" min-width="150">
              <template #default="{ row }">
                <div>{{ row.customerName }}</div>
                <div class="hint">{{ row.customerNo }}</div>
              </template>
            </el-table-column>
            <el-table-column label="被保险人" width="110">
              <template #default="{ row }">{{ row.insuredName || row.holderName || '—' }}</template>
            </el-table-column>
            <el-table-column label="关系" width="80">
              <template #default="{ row }">{{ relationLabels[row.relation as PolicyRelation] ?? row.relation }}</template>
            </el-table-column>
            <el-table-column label="有效期" width="190">
              <template #default="{ row }">
                <span v-if="row.validFrom || row.validTo">
                  {{ row.validFrom || '—' }} ~ {{ row.validTo || '—' }}
                </span>
                <span v-else class="hint">—</span>
              </template>
            </el-table-column>
            <el-table-column label="保额" width="120" align="right">
              <template #default="{ row }">{{ formatAmount(row.coverageAmount) }}</template>
            </el-table-column>
            <el-table-column label="状态" width="90">
              <template #default="{ row }">
                <el-tag :type="policyStatusTag(row.status)" size="small">
                  {{ policyStatusLabels[row.status as PolicyStatus] }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column v-if="canManage" label="操作" width="100" fixed="right">
              <template #default="{ row }">
                <el-button size="small" link type="primary" @click="openPolicyStatusDialog(row)">
                  状态变更
                </el-button>
              </template>
            </el-table-column>
          </el-table>
          <el-pagination
            v-if="policiesTotal > policiesQuery.pageSize"
            v-model:current-page="policiesQuery.page"
            :page-size="policiesQuery.pageSize"
            :total="policiesTotal"
            layout="prev, pager, next"
            small
            @current-change="loadPolicies"
          />
        </div>
      </el-tab-pane>

      <!-- ============================================================ -->
      <el-tab-pane label="商保案件" name="cases">
        <div class="toolbar">
          <el-select
            v-model="casesQuery.companyId"
            placeholder="全部保险公司"
            clearable
            filterable
            size="small"
            style="width: 170px"
          >
            <el-option v-for="item in companyOptions" :key="item.id" :label="item.companyName" :value="item.id" />
          </el-select>
          <el-select v-model="casesQuery.state" placeholder="全部状态" clearable size="small" style="width: 140px">
            <el-option
              v-for="(label, key) in caseStateLabels"
              :key="key"
              :label="label"
              :value="key"
            />
          </el-select>
          <el-input
            v-model="casesQuery.keyword"
            placeholder="案件号 / 客户 / 保单"
            clearable
            size="small"
            style="width: 180px"
            @keyup.enter="searchCases"
          />
          <el-date-picker
            v-model="casesQuery.dateRange"
            type="daterange"
            size="small"
            style="width: 240px"
            value-format="YYYY-MM-DD"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
          />
          <el-button size="small" type="primary" @click="searchCases">查询</el-button>
          <span class="spacer" />
          <el-button v-if="canCaseWrite" size="small" type="primary" @click="openCaseDialog">
            新建案件
          </el-button>
        </div>

        <div class="panel-card">
          <el-table v-loading="casesLoading" :data="cases" size="small">
            <el-table-column prop="caseNo" label="案件号" width="140" />
            <el-table-column label="客户" min-width="120">
              <template #default="{ row }">
                <div>{{ row.customerName }}</div>
                <div class="hint">{{ row.customerNo }}</div>
              </template>
            </el-table-column>
            <el-table-column prop="companyName" label="保险公司" min-width="140" show-overflow-tooltip />
            <el-table-column label="类型" width="76">
              <template #default="{ row }">{{ caseTypeLabels[row.caseType as CaseType] ?? row.caseType }}</template>
            </el-table-column>
            <el-table-column label="状态" width="106">
              <template #default="{ row }">
                <el-tag :type="caseStateTag(row.state)" size="small">
                  {{ caseStateLabels[row.state as CaseState] ?? row.state }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="医院应收" width="118" align="right">
              <template #default="{ row }">{{ formatAmount(row.hospitalReceivable) }}</template>
            </el-table-column>
            <el-table-column label="患者自付" width="110" align="right">
              <template #default="{ row }">{{ formatAmount(row.patientPay) }}</template>
            </el-table-column>
            <el-table-column label="预计赔付" width="118" align="right">
              <template #default="{ row }">
                <span class="amount-estimated">{{ formatAmount(row.estimatedPayout) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="实际到账" width="118" align="right">
              <template #default="{ row }">
                <span class="amount-actual">{{ formatAmount(row.actualReceived) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="处理人" width="100">
              <template #default="{ row }">{{ row.handlerName || '—' }}</template>
            </el-table-column>
            <el-table-column label="创建时间" width="140">
              <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
            </el-table-column>
            <el-table-column label="操作" width="80" fixed="right">
              <template #default="{ row }">
                <el-button size="small" link type="primary" @click="openCaseDetail(row.id)">详情</el-button>
              </template>
            </el-table-column>
          </el-table>
          <el-pagination
            v-if="casesTotal > casesQuery.pageSize"
            v-model:current-page="casesQuery.page"
            :page-size="casesQuery.pageSize"
            :total="casesTotal"
            layout="prev, pager, next"
            small
            @current-change="loadCases"
          />
        </div>
      </el-tab-pane>

      <!-- ============================================================ -->
      <el-tab-pane label="对账中心" name="reconciliations">
        <div class="toolbar">
          <el-select
            v-model="reconciliationsQuery.companyId"
            placeholder="全部保险公司"
            clearable
            filterable
            size="small"
            style="width: 180px"
          >
            <el-option v-for="item in companyOptions" :key="item.id" :label="item.companyName" :value="item.id" />
          </el-select>
          <el-date-picker
            v-model="reconciliationsQuery.period"
            type="month"
            size="small"
            style="width: 140px"
            value-format="YYYY-MM"
            placeholder="账期"
          />
          <el-select v-model="reconciliationsQuery.status" placeholder="全部状态" clearable size="small" style="width: 120px">
            <el-option label="待确认" value="pending" />
            <el-option label="已匹配" value="matched" />
            <el-option label="有差异" value="mismatch" />
          </el-select>
          <el-button size="small" type="primary" @click="searchReconciliations">查询</el-button>
          <span class="spacer" />
          <el-button v-if="canReconcile" size="small" type="primary" @click="openReconciliationDialog">
            新建对账
          </el-button>
        </div>

        <div class="panel-card">
          <el-table v-loading="reconciliationsLoading" :data="reconciliations" size="small">
            <el-table-column prop="companyName" label="保险公司" min-width="170" show-overflow-tooltip />
            <el-table-column prop="period" label="账期" width="90" />
            <el-table-column prop="caseCount" label="案件数" width="80" align="right" />
            <el-table-column label="应收" width="120" align="right">
              <template #default="{ row }">{{ formatAmount(row.receivableAmount) }}</template>
            </el-table-column>
            <el-table-column label="实收" width="120" align="right">
              <template #default="{ row }">{{ formatAmount(row.receivedAmount) }}</template>
            </el-table-column>
            <el-table-column label="差额" width="120" align="right">
              <template #default="{ row }">
                <span :class="differenceClass(row.differenceAmount)">
                  {{ formatAmount(row.differenceAmount) }}
                </span>
              </template>
            </el-table-column>
            <el-table-column label="状态" width="90">
              <template #default="{ row }">
                <el-tag :type="reconciliationStatusTag(row.status)" size="small">
                  {{ reconciliationStatusLabels[row.status as ReconciliationStatus] }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="确认人 / 时间" width="160">
              <template #default="{ row }">
                <div v-if="row.confirmedByName">{{ row.confirmedByName }}</div>
                <div class="hint">{{ row.confirmedAt ? formatTime(row.confirmedAt) : '未确认' }}</div>
              </template>
            </el-table-column>
            <el-table-column label="备注" min-width="140" show-overflow-tooltip>
              <template #default="{ row }">{{ row.remark || '—' }}</template>
            </el-table-column>
            <el-table-column v-if="canReconcile" label="操作" width="100" fixed="right">
              <template #default="{ row }">
                <el-button
                  size="small"
                  link
                  type="primary"
                  :disabled="row.status !== 'pending'"
                  @click="handleConfirmReconciliation(row)"
                >
                  {{ row.status === 'pending' ? '确认' : '已确认' }}
                </el-button>
              </template>
            </el-table-column>
          </el-table>
          <el-pagination
            v-if="reconciliationsTotal > reconciliationsQuery.pageSize"
            v-model:current-page="reconciliationsQuery.page"
            :page-size="reconciliationsQuery.pageSize"
            :total="reconciliationsTotal"
            layout="prev, pager, next"
            small
            @current-change="loadReconciliations"
          />
        </div>
      </el-tab-pane>
    </el-tabs>

    <!-- 保险公司新建 / 编辑 -->
    <el-dialog
      v-model="companyDialog.visible"
      :title="companyDialog.editingId ? '编辑保险公司' : '新建保险公司'"
      width="560px"
    >
      <el-form label-width="100px">
        <el-form-item label="公司编码" required>
          <el-input
            v-model="companyDialog.companyCode"
            :disabled="!!companyDialog.editingId"
            maxlength="64"
            placeholder="如 INS-TAIPING"
          />
        </el-form-item>
        <el-form-item label="公司名称" required>
          <el-input v-model="companyDialog.companyName" maxlength="120" />
        </el-form-item>
        <el-form-item label="联系人">
          <el-input v-model="companyDialog.contactName" maxlength="64" />
        </el-form-item>
        <el-form-item label="联系电话">
          <el-input
            v-model="companyDialog.contactPhone"
            maxlength="32"
            :placeholder="companyDialog.editingId ? '留空则不修改（仅展示脱敏号码）' : '联系手机号'"
          />
        </el-form-item>
        <el-form-item label="联系邮箱">
          <el-input v-model="companyDialog.contactEmail" maxlength="120" placeholder="用于邮件对账" />
        </el-form-item>
        <el-form-item label="结算方式">
          <el-select v-model="companyDialog.settlementType" style="width: 100%">
            <el-option label="接口直连" value="api" />
            <el-option label="邮件对账" value="email" />
            <el-option label="手工处理" value="manual" />
          </el-select>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="companyDialog.remark" type="textarea" :rows="2" maxlength="500" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="companyDialog.visible = false">取消</el-button>
        <el-button type="primary" @click="submitCompany">保存</el-button>
      </template>
    </el-dialog>

    <!-- 保单新建 -->
    <el-dialog v-model="policyDialog.visible" title="新建保单" width="600px">
      <el-form label-width="100px">
        <el-form-item label="保险公司" required>
          <el-select v-model="policyDialog.companyId" filterable placeholder="选择保险公司" style="width: 100%">
            <el-option
              v-for="item in companyOptions"
              :key="item.id"
              :label="item.companyName"
              :value="item.id"
              :disabled="item.status === 'disabled'"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="客户 ID" required>
          <el-input v-model="policyDialog.customerId" maxlength="64" placeholder="客户档案 ID" />
        </el-form-item>
        <el-form-item label="保单号" required>
          <el-input v-model="policyDialog.policyNo" maxlength="64" />
        </el-form-item>
        <el-form-item label="投保人">
          <el-input v-model="policyDialog.holderName" maxlength="64" placeholder="留空默认客户本人" />
        </el-form-item>
        <el-form-item label="被保险人">
          <el-input v-model="policyDialog.insuredName" maxlength="64" placeholder="留空默认客户本人" />
        </el-form-item>
        <el-form-item label="与客户关系">
          <el-select v-model="policyDialog.relation" style="width: 100%">
            <el-option label="本人" value="self" />
            <el-option label="配偶" value="spouse" />
            <el-option label="子女" value="child" />
            <el-option label="父母" value="parent" />
            <el-option label="其他" value="other" />
          </el-select>
        </el-form-item>
        <el-form-item label="有效期">
          <div class="inline-group">
            <el-date-picker
              v-model="policyDialog.validFrom"
              type="date"
              value-format="YYYY-MM-DD"
              placeholder="生效日期"
              style="flex: 1"
            />
            <el-date-picker
              v-model="policyDialog.validTo"
              type="date"
              value-format="YYYY-MM-DD"
              placeholder="到期日期"
              style="flex: 1"
            />
          </div>
        </el-form-item>
        <el-form-item label="保额">
          <el-input-number
            v-model="policyDialog.coverageAmount"
            :min="0"
            :precision="2"
            :step="1000"
            controls-position="right"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="policyDialog.remark" type="textarea" :rows="2" maxlength="500" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="policyDialog.visible = false">取消</el-button>
        <el-button type="primary" @click="submitPolicy">保存</el-button>
      </template>
    </el-dialog>

    <!-- 保单状态变更 -->
    <el-dialog v-model="policyStatusDialog.visible" title="保单状态变更" width="420px">
      <el-form label-width="90px">
        <el-form-item label="保单号">
          <span class="hint">{{ policyStatusDialog.policyNo }}</span>
        </el-form-item>
        <el-form-item label="目标状态" required>
          <el-select v-model="policyStatusDialog.status" style="width: 100%">
            <el-option label="生效中" value="active" />
            <el-option label="已过期" value="expired" />
            <el-option label="已终止" value="terminated" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="policyStatusDialog.visible = false">取消</el-button>
        <el-button type="primary" @click="submitPolicyStatus">确认变更</el-button>
      </template>
    </el-dialog>

    <!-- 新建案件 -->
    <el-dialog v-model="caseDialog.visible" title="新建商保案件" width="600px">
      <el-form label-width="100px">
        <el-form-item label="保单" required>
          <el-select
            v-model="caseDialog.policyId"
            filterable
            placeholder="选择客户保单"
            style="width: 100%"
          >
            <el-option
              v-for="item in policyOptions"
              :key="item.id"
              :label="`${item.policyNo} · ${item.customerName}（${item.companyName}）`"
              :value="item.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="案件类型">
          <el-select v-model="caseDialog.caseType" style="width: 100%">
            <el-option label="门诊" value="outpatient" />
            <el-option label="住院" value="inpatient" />
            <el-option label="体检" value="physical_exam" />
            <el-option label="其他" value="other" />
          </el-select>
        </el-form-item>
        <el-form-item label="服务说明">
          <el-input
            v-model="caseDialog.serviceDesc"
            type="textarea"
            :rows="2"
            maxlength="500"
            placeholder="如 门诊陪诊 + 材料代取"
          />
        </el-form-item>
        <el-form-item label="医院应收">
          <el-input-number
            v-model="caseDialog.hospitalReceivable"
            :min="0"
            :precision="2"
            :step="100"
            controls-position="right"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="患者自付">
          <el-input-number
            v-model="caseDialog.patientPay"
            :min="0"
            :precision="2"
            :step="100"
            controls-position="right"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="caseDialog.remark" type="textarea" :rows="2" maxlength="500" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="caseDialog.visible = false">取消</el-button>
        <el-button type="primary" @click="submitCase">登记案件</el-button>
      </template>
    </el-dialog>

    <!-- 案件详情抽屉 -->
    <el-drawer
      v-model="detailVisible"
      :title="currentCase ? `商保案件 · ${currentCase.caseNo}` : '商保案件详情'"
      size="840px"
    >
      <div v-loading="detailLoading">
        <template v-if="detail && currentCase">
          <div class="amount-grid">
            <div class="amount-card amount-card-receivable">
              <span class="amount-label">医院应收</span>
              <span class="amount-value">{{ formatAmount(currentCase.hospitalReceivable) }}</span>
            </div>
            <div class="amount-card amount-card-patient">
              <span class="amount-label">患者自付</span>
              <span class="amount-value">{{ formatAmount(currentCase.patientPay) }}</span>
            </div>
            <div class="amount-card amount-card-estimated">
              <span class="amount-label">预计赔付 <em>预估</em></span>
              <span class="amount-value">{{ formatAmount(currentCase.estimatedPayout) }}</span>
            </div>
            <div class="amount-card amount-card-actual">
              <span class="amount-label">实际到账 <em>已收</em></span>
              <span class="amount-value">{{ formatAmount(currentCase.actualReceived) }}</span>
            </div>
          </div>

          <el-descriptions :column="2" border size="small" title="基础信息" class="drawer-section">
            <el-descriptions-item label="当前状态">
              <el-tag :type="caseStateTag(currentCase.state)" size="small">
                {{ caseStateLabels[currentCase.state] }}
              </el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="案件类型">
              {{ caseTypeLabels[currentCase.caseType] ?? currentCase.caseType }}
            </el-descriptions-item>
            <el-descriptions-item label="保险公司">{{ currentCase.companyName }}</el-descriptions-item>
            <el-descriptions-item label="保单号">{{ currentCase.policyNo }}</el-descriptions-item>
            <el-descriptions-item label="客户">
              {{ currentCase.customerName }} · {{ currentCase.customerNo }}
            </el-descriptions-item>
            <el-descriptions-item label="处理人">{{ currentCase.handlerName || '未分派' }}</el-descriptions-item>
            <el-descriptions-item label="询价金额">
              {{ formatAmount(currentCase.quotationAmount) }}
            </el-descriptions-item>
            <el-descriptions-item label="询价时间">{{ formatTime(currentCase.quotationAt) }}</el-descriptions-item>
            <el-descriptions-item label="预授权号">{{ currentCase.preauthNo || '—' }}</el-descriptions-item>
            <el-descriptions-item label="预授权金额">{{ formatAmount(currentCase.preauthAmount) }}</el-descriptions-item>
            <el-descriptions-item label="理赔号">{{ currentCase.claimNo || '—' }}</el-descriptions-item>
            <el-descriptions-item label="核定金额">{{ formatAmount(currentCase.approvedAmount) }}</el-descriptions-item>
            <el-descriptions-item v-if="currentCase.decisionReason" label="理赔结论" :span="2">
              {{ currentCase.decisionReason }}
            </el-descriptions-item>
            <el-descriptions-item v-if="currentCase.reconciliationDiff !== undefined" label="对账差额">
              <span :class="differenceClass(currentCase.reconciliationDiff)">
                {{ formatAmount(currentCase.reconciliationDiff) }}
              </span>
            </el-descriptions-item>
            <el-descriptions-item label="对账时间">{{ formatTime(currentCase.reconciledAt) }}</el-descriptions-item>
            <el-descriptions-item label="服务说明" :span="2">
              {{ currentCase.serviceDesc || '—' }}
            </el-descriptions-item>
            <el-descriptions-item label="备注" :span="2">{{ currentCase.remark || '—' }}</el-descriptions-item>
            <el-descriptions-item label="创建时间">{{ formatTime(currentCase.createdAt) }}</el-descriptions-item>
            <el-descriptions-item label="更新时间">{{ formatTime(currentCase.updatedAt) }}</el-descriptions-item>
          </el-descriptions>

          <div class="drawer-section state-bar">
            <div class="state-actions">
              <template v-if="canCaseWrite">
                <el-button
                  v-for="target in transitionTargets"
                  :key="target"
                  size="small"
                  type="primary"
                  plain
                  @click="openTransition(target)"
                >
                  流转至{{ caseStateLabels[target] }}
                </el-button>
                <span v-if="!transitionTargets.length" class="hint">案件已对账，流程结束</span>
              </template>
              <el-button v-if="canCaseWrite" size="small" @click="openAssignDialog">分派处理人</el-button>
            </div>
          </div>

          <div class="drawer-section">
            <div class="section-head-row">
              <span class="block-title">材料清单<em>{{ detail.materials.length }} 份</em></span>
              <el-button v-if="canMaterialWrite" size="small" type="primary" plain @click="openMaterialDialog">
                新增材料
              </el-button>
            </div>
            <el-table :data="detail.materials" size="small">
              <el-table-column label="类型" width="84">
                <template #default="{ row }">
                  {{ materialTypeLabels[row.materialType as MaterialType] ?? row.materialType }}
                </template>
              </el-table-column>
              <el-table-column label="文件" min-width="150">
                <template #default="{ row }">
                  <div class="file-name">{{ row.fileName }}</div>
                  <div class="hint">SHA-256：{{ row.fileHash ? `${row.fileHash.slice(0, 16)}…` : '—' }}</div>
                </template>
              </el-table-column>
              <el-table-column label="来源" width="64">
                <template #default="{ row }">{{ materialSourceLabels[row.source] ?? row.source }}</template>
              </el-table-column>
              <el-table-column label="大小" width="72">
                <template #default="{ row }">{{ formatSize(row.fileSize) }}</template>
              </el-table-column>
              <el-table-column label="状态" width="76">
                <template #default="{ row }">
                  <el-tag :type="materialStatusTag(row.status)" size="small">
                    {{ materialStatusLabels[row.status as MaterialStatus] }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column label="登记人" width="80">
                <template #default="{ row }">{{ row.uploadedByName || '—' }}</template>
              </el-table-column>
              <el-table-column label="时间" width="110">
                <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
              </el-table-column>
              <el-table-column v-if="canMaterialWrite" label="操作" width="104" fixed="right">
                <template #default="{ row }">
                  <el-button
                    v-if="row.status !== 'valid'"
                    size="small"
                    link
                    type="success"
                    @click="changeMaterialStatus(row, 'valid')"
                  >
                    有效
                  </el-button>
                  <el-button
                    v-if="row.status !== 'invalid'"
                    size="small"
                    link
                    type="danger"
                    @click="changeMaterialStatus(row, 'invalid')"
                  >
                    无效
                  </el-button>
                </template>
              </el-table-column>
            </el-table>
            <p v-if="!detail.materials.length" class="empty">暂无材料，请登记诊断证明、发票与费用明细</p>
          </div>

          <div class="drawer-section">
            <div class="section-head-row">
              <span class="block-title">往来邮件<em>{{ detail.emails.length }} 封</em></span>
              <el-button v-if="canEmailWrite" size="small" type="primary" plain @click="openEmailDialog">
                新增邮件
              </el-button>
            </div>
            <el-table :data="detail.emails" size="small">
              <el-table-column label="方向" width="70">
                <template #default="{ row }">
                  <el-tag :type="row.direction === 'outbound' ? 'primary' : 'info'" size="small">
                    {{ row.direction === 'outbound' ? '发出' : '收到' }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column label="主题" min-width="180" show-overflow-tooltip>
                <template #default="{ row }">
                  <div>{{ row.subject }}</div>
                  <div class="hint">
                    {{ row.fromAddress || '—' }} → {{ row.toAddress || '—' }}
                  </div>
                </template>
              </el-table-column>
              <el-table-column label="Message-ID" min-width="150" show-overflow-tooltip>
                <template #default="{ row }">{{ row.messageId || '—' }}</template>
              </el-table-column>
              <el-table-column label="附件" width="64">
                <template #default="{ row }">{{ row.attachmentCount }} 个</template>
              </el-table-column>
              <el-table-column label="状态" width="80">
                <template #default="{ row }">
                  <el-tag :type="row.status === 'failed' ? 'danger' : 'success'" size="small">
                    {{ emailStatusLabels[row.status] ?? row.status }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column label="时间" width="120">
                <template #default="{ row }">{{ formatTime(emailTime(row)) }}</template>
              </el-table-column>
            </el-table>
            <p v-if="!detail.emails.length" class="empty">暂无邮件往来记录</p>
          </div>

          <div class="drawer-section">
            <div class="section-head-row">
              <span class="block-title">状态变更时间线<em>{{ detail.events.length }} 条</em></span>
            </div>
            <el-timeline v-if="sortedEvents.length" class="event-timeline">
              <el-timeline-item
                v-for="event in sortedEvents"
                :key="event.id"
                :timestamp="formatTime(event.createdAt)"
                placement="top"
              >
                <span class="event-title">
                  <template v-if="event.fromState || event.toState">
                    {{ stateLabel(event.fromState) }} → {{ stateLabel(event.toState) }}
                  </template>
                  <template v-else>{{ event.eventType }}</template>
                </span>
                <span class="hint"> · {{ event.operatorName || '系统' }} · {{ event.eventType }}</span>
              </el-timeline-item>
            </el-timeline>
            <p v-else class="empty">暂无流转记录</p>
          </div>
        </template>
      </div>
    </el-drawer>

    <!-- 状态流转 -->
    <el-dialog
      v-model="transitionDialog.visible"
      :title="`状态流转 · ${caseStateLabels[transitionDialog.targetState]}`"
      width="560px"
    >
      <el-form label-width="110px">
        <el-form-item v-if="transitionDialog.targetState === 'quotation'" label="询价金额" required>
          <el-input-number
            v-model="transitionDialog.quotationAmount"
            :min="0"
            :precision="2"
            :step="100"
            controls-position="right"
            style="width: 100%"
          />
        </el-form-item>
        <template v-if="transitionDialog.targetState === 'pre_authorization'">
          <el-form-item label="预授权号" required>
            <el-input v-model="transitionDialog.preauthNo" maxlength="64" />
          </el-form-item>
          <el-form-item label="预授权金额" required>
            <el-input-number
              v-model="transitionDialog.preauthAmount"
              :min="0"
              :precision="2"
              :step="100"
              controls-position="right"
              style="width: 100%"
            />
          </el-form-item>
        </template>
        <template v-if="transitionDialog.targetState === 'claim_submitted'">
          <el-form-item label="理赔号" required>
            <el-input v-model="transitionDialog.claimNo" maxlength="64" />
          </el-form-item>
          <el-form-item label="医院应收" required>
            <el-input-number
              v-model="transitionDialog.hospitalReceivable"
              :min="0"
              :precision="2"
              :step="100"
              controls-position="right"
              style="width: 100%"
            />
          </el-form-item>
          <el-form-item label="患者自付" required>
            <el-input-number
              v-model="transitionDialog.patientPay"
              :min="0"
              :precision="2"
              :step="100"
              controls-position="right"
              style="width: 100%"
            />
          </el-form-item>
        </template>
        <el-form-item
          v-if="
            transitionDialog.targetState === 'approved' ||
            transitionDialog.targetState === 'partially_approved'
          "
          label="核定金额"
          required
        >
          <el-input-number
            v-model="transitionDialog.approvedAmount"
            :min="0"
            :precision="2"
            :step="100"
            controls-position="right"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item v-if="transitionDialog.targetState === 'rejected'" label="拒赔原因" required>
          <el-input
            v-model="transitionDialog.decisionReason"
            type="textarea"
            :rows="2"
            maxlength="500"
            placeholder="必填，随流转备注一并提交"
          />
        </el-form-item>
        <el-form-item v-if="transitionDialog.targetState === 'reconciled'" label="实际到账金额" required>
          <el-input-number
            v-model="transitionDialog.actualReceived"
            :min="0"
            :precision="2"
            :step="100"
            controls-position="right"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="备注">
          <el-input
            v-model="transitionDialog.note"
            type="textarea"
            :rows="2"
            maxlength="500"
            placeholder="流转说明（可选）"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="transitionDialog.visible = false">取消</el-button>
        <el-button type="primary" @click="submitTransition">确认流转</el-button>
      </template>
    </el-dialog>

    <!-- 分派处理人 -->
    <el-dialog v-model="assignDialog.visible" title="分派处理人" width="420px">
      <el-form label-width="100px">
        <el-form-item label="处理人 ID" required>
          <el-input v-model="assignDialog.handlerUserId" maxlength="64" placeholder="处理人用户 ID" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="assignDialog.visible = false">取消</el-button>
        <el-button type="primary" @click="submitAssign">确认分派</el-button>
      </template>
    </el-dialog>

    <!-- 新增材料 -->
    <el-dialog v-model="materialDialog.visible" title="新增材料" width="560px">
      <el-form label-width="100px">
        <el-form-item label="材料类型" required>
          <el-select v-model="materialDialog.materialType" style="width: 100%">
            <el-option label="诊断证明" value="diagnosis" />
            <el-option label="发票" value="invoice" />
            <el-option label="费用明细" value="detail" />
            <el-option label="处方" value="prescription" />
            <el-option label="检查报告" value="report" />
            <el-option label="其他" value="other" />
          </el-select>
        </el-form-item>
        <el-form-item label="文件名" required>
          <el-input v-model="materialDialog.fileName" maxlength="200" placeholder="如 住院发票.pdf" />
        </el-form-item>
        <el-form-item label="文件 Key" required>
          <el-input v-model="materialDialog.fileKey" maxlength="200" placeholder="对象存储文件标识" />
        </el-form-item>
        <el-form-item label="SHA-256" required>
          <el-input
            v-model="materialDialog.fileHash"
            maxlength="64"
            placeholder="64 位十六进制哈希"
          />
          <div v-if="materialDialog.fileHash && isHashInvalid(materialDialog.fileHash)" class="hash-error">
            哈希格式不正确，需为 64 位十六进制字符
          </div>
        </el-form-item>
        <el-form-item label="文件大小">
          <el-input-number
            v-model="materialDialog.fileSize"
            :min="0"
            :precision="0"
            controls-position="right"
            style="width: 100%"
          />
          <span class="hint">单位：字节</span>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="materialDialog.note" type="textarea" :rows="2" maxlength="500" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="materialDialog.visible = false">取消</el-button>
        <el-button type="primary" @click="submitMaterial">保存</el-button>
      </template>
    </el-dialog>

    <!-- 新增邮件 -->
    <el-dialog v-model="emailDialog.visible" title="新增邮件记录" width="680px">
      <el-form label-width="100px">
        <el-form-item label="收发方向" required>
          <el-radio-group v-model="emailDialog.direction">
            <el-radio value="outbound">发出</el-radio>
            <el-radio value="inbound">收到</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="主题" required>
          <el-input v-model="emailDialog.subject" maxlength="200" />
        </el-form-item>
        <el-form-item label="Message-ID">
          <el-input v-model="emailDialog.messageId" maxlength="200" placeholder="邮件唯一标识（可选）" />
        </el-form-item>
        <el-form-item label="发件人">
          <el-input v-model="emailDialog.fromAddress" maxlength="200" />
        </el-form-item>
        <el-form-item label="收件人">
          <el-input v-model="emailDialog.toAddress" maxlength="200" />
        </el-form-item>
        <el-form-item label="正文摘要">
          <el-input v-model="emailDialog.bodyExcerpt" type="textarea" :rows="2" maxlength="1000" />
        </el-form-item>
        <el-form-item v-if="emailDialog.direction === 'outbound'" label="发送时间">
          <el-date-picker
            v-model="emailDialog.sentAt"
            type="datetime"
            value-format="YYYY-MM-DDTHH:mm:ss"
            placeholder="发送时间"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item v-else label="接收时间">
          <el-date-picker
            v-model="emailDialog.receivedAt"
            type="datetime"
            value-format="YYYY-MM-DDTHH:mm:ss"
            placeholder="接收时间"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="附件">
          <div class="attachment-list">
            <div v-for="(item, index) in emailDialog.attachments" :key="index" class="attachment-row">
              <el-input v-model="item.fileName" maxlength="200" placeholder="附件文件名" />
              <el-input v-model="item.fileHash" maxlength="64" placeholder="SHA-256（64 位十六进制）" />
              <el-input-number
                v-model="item.fileSize"
                :min="0"
                :precision="0"
                controls-position="right"
                placeholder="字节"
                style="width: 130px"
              />
              <el-button link type="danger" @click="removeAttachmentRow(index)">移除</el-button>
            </div>
            <div>
              <el-button size="small" plain @click="addAttachmentRow">添加附件</el-button>
              <span class="hint">附件哈希需为 64 位十六进制，避免重复提交</span>
            </div>
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="emailDialog.visible = false">取消</el-button>
        <el-button type="primary" @click="submitEmail">保存</el-button>
      </template>
    </el-dialog>

    <!-- 新建对账 -->
    <el-dialog v-model="reconciliationDialog.visible" title="新建对账单" width="480px">
      <el-form label-width="100px">
        <el-form-item label="保险公司" required>
          <el-select v-model="reconciliationDialog.companyId" filterable placeholder="选择保险公司" style="width: 100%">
            <el-option v-for="item in companyOptions" :key="item.id" :label="item.companyName" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="账期" required>
          <el-date-picker
            v-model="reconciliationDialog.period"
            type="month"
            value-format="YYYY-MM"
            placeholder="选择账期"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="reconciliationDialog.remark" type="textarea" :rows="2" maxlength="500" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="reconciliationDialog.visible = false">取消</el-button>
        <el-button type="primary" @click="submitReconciliation">生成对账单</el-button>
      </template>
    </el-dialog>
  </section>
</template>

<style scoped>
.ins-page {
  padding: 20px 24px 32px;
}

.page-head h2 {
  margin: 0 0 6px;
  font-size: 20px;
}

.page-desc {
  margin: 0 0 8px;
  color: var(--el-text-color-secondary);
  font-size: 13px;
  line-height: 1.6;
  max-width: 860px;
}

.toolbar {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
  flex-wrap: wrap;
}

.spacer {
  flex: 1;
}

.hint {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.empty {
  text-align: center;
  color: var(--el-text-color-secondary);
  font-size: 12px;
  margin: 16px 0;
}

.panel-card {
  background: var(--crm-card);
  border: 1px solid var(--crm-border);
  border-radius: 14px;
  padding: 14px 16px 10px;
  box-shadow: 0 4px 16px rgba(31, 79, 134, 0.05);
}

.panel-card :deep(.el-table th.el-table__cell) {
  background: #f7f9fc;
  color: var(--crm-muted);
  font-weight: 600;
}

.el-pagination {
  margin-top: 10px;
  justify-content: center;
}

.inline-group {
  display: flex;
  gap: 8px;
  width: 100%;
}

/* ---------------- 金额 ---------------- */
.amount-estimated {
  color: #c9892b;
  font-weight: 600;
}

.amount-actual {
  color: #2f9e73;
  font-weight: 600;
}

.amount-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 10px;
}

.amount-card {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 12px 14px;
  border-radius: 12px;
  border: 1px solid transparent;
}

.amount-label {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  display: flex;
  align-items: center;
  gap: 6px;
}

.amount-label em {
  font-style: normal;
  font-size: 10px;
  padding: 0 5px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.7);
}

.amount-value {
  font-size: 17px;
  font-weight: 700;
  letter-spacing: 0.01em;
}

.amount-card-receivable {
  background: #e8f1fb;
  border-color: #d3e3f6;
  color: #1f4f86;
}

.amount-card-patient {
  background: #f2f4f8;
  border-color: #e5e9f0;
  color: #3f4e66;
}

.amount-card-estimated {
  background: #fdf5e6;
  border: 1px dashed #e6c68a;
  color: #9a6a17;
}

.amount-card-estimated .amount-label em {
  background: #f6e5c4;
  color: #9a6a17;
}

.amount-card-actual {
  background: #e9f7f0;
  border-color: #cbe9d9;
  color: #1f7a56;
}

.amount-card-actual .amount-label em {
  background: #d4efe2;
  color: #1f7a56;
}

/* ---------------- 抽屉 ---------------- */
.drawer-section {
  margin-top: 16px;
}

.state-bar {
  background: #f7f9fc;
  border-radius: 10px;
  padding: 10px 12px;
}

.state-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.section-head-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
}

.block-title {
  font-size: 14px;
  font-weight: 700;
  color: var(--crm-ink);
}

.block-title em {
  font-style: normal;
  font-weight: 400;
  font-size: 12px;
  color: var(--crm-muted);
  margin-left: 8px;
}

.file-name {
  font-size: 12px;
  line-height: 1.5;
  word-break: break-all;
}

.hash-error {
  color: var(--el-color-danger);
  font-size: 12px;
  line-height: 1.6;
}

.event-timeline {
  padding-left: 4px;
  margin-top: 4px;
}

.event-title {
  font-weight: 600;
  font-size: 13px;
}

.attachment-list {
  width: 100%;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.attachment-row {
  display: flex;
  align-items: center;
  gap: 8px;
}

.attachment-row .el-input:first-child {
  flex: 1.1;
}

.attachment-row .el-input:nth-child(2) {
  flex: 1.6;
}

.diff-zero {
  color: var(--crm-muted);
}

.diff-positive {
  color: #2f9e73;
  font-weight: 600;
}

.diff-negative {
  color: #c75450;
  font-weight: 600;
}
</style>
