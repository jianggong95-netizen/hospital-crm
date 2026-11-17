<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import { fetchCustomers } from '@/api/customers'
import { fetchUsers } from '@/api/users'
import {
  acceptHomeVisitOrder,
  addOrderExecution,
  cancelHomeVisitOrder,
  changeHomeAddressStatus,
  changeHomeServiceStatus,
  createHomeAddress,
  createHomeService,
  createHomeVisitOrder,
  createStaffQualification,
  dispatchHomeVisitOrder,
  escalateHomeVisitException,
  fetchHomeAddresses,
  fetchHomeServices,
  fetchHomeVisitExceptions,
  fetchHomeVisitOrder,
  fetchHomeVisitOrders,
  fetchStaffQualifications,
  finishHomeVisitOrder,
  rejectHomeVisitOrder,
  reportOrderException,
  resolveHomeVisitException,
  reviewHomeService,
  reviewHomeVisitOrder,
  revokeStaffQualification,
  startHomeVisitOrder,
  submitHomeService,
  updateHomeAddress,
  updateHomeService
} from '@/api/homevisit'
import type { CustomerSummary } from '@/types/customer'
import type { UserResponse } from '@/types/system'
import type {
  HomeAddress,
  HomeServiceItem,
  HomeStaffQualification,
  HomeVisitException,
  HomeVisitExceptionBoardItem,
  HomeVisitOrderDetail,
  HomeVisitOrderSummary
} from '@/types/homevisit'

const auth = useAuthStore()
const canRead = computed(() => auth.has('crm:homevisit:read'))
const canWrite = computed(() => auth.has('crm:homevisit:write'))
const canManage = computed(() => auth.has('crm:homevisit:manage'))

const activeTab = ref('orders')

// ------------------------------------------------------------------
// 标签与格式化
// ------------------------------------------------------------------
const orderStatusLabels: Record<string, string> = {
  PENDING: '待审核',
  APPROVED: '已审核',
  REJECTED: '已驳回',
  ASSIGNED: '已派单',
  ACCEPTED: '已接单',
  IN_PROGRESS: '服务中',
  COMPLETED: '已完成',
  CANCELLED: '已取消'
}

function orderStatusLabel(status: string) {
  return orderStatusLabels[status] || status
}

function orderBadge(status: string) {
  switch (status) {
    case 'PENDING': return 'badge-primary'
    case 'APPROVED': return 'badge-primary'
    case 'REJECTED': return 'badge-danger'
    case 'ASSIGNED': return 'badge-warning'
    case 'ACCEPTED': return 'badge-primary'
    case 'IN_PROGRESS': return 'badge-warning'
    case 'COMPLETED': return 'badge-success'
    case 'CANCELLED': return 'badge-neutral'
    default: return 'badge-neutral'
  }
}

const serviceStatusLabels: Record<string, string> = {
  draft: '草稿',
  pending_review: '待审核',
  published: '已发布',
  rejected: '已驳回',
  disabled: '已停用'
}

function serviceBadge(status: string) {
  switch (status) {
    case 'draft': return 'badge-neutral'
    case 'pending_review': return 'badge-primary'
    case 'published': return 'badge-success'
    case 'rejected': return 'badge-danger'
    case 'disabled': return 'badge-neutral'
    default: return 'badge-neutral'
  }
}

const categoryLabels: Record<string, string> = { medical: '医疗类', non_medical: '非医疗类' }

const qualTypeLabels: Record<string, string> = {
  nurse: '护士',
  caregiver: '照护师',
  health_manager: '健康管理师',
  doctor: '医生',
  other: '其他'
}

const exceptionLevelLabels: Record<string, string> = {
  low: '低',
  medium: '中',
  high: '高',
  critical: '严重'
}

function exceptionLevelBadge(level: string) {
  switch (level) {
    case 'low': return 'badge-neutral'
    case 'medium': return 'badge-warning'
    case 'high': return 'badge-danger'
    case 'critical': return 'badge-danger'
    default: return 'badge-neutral'
  }
}

const exceptionStatusLabels: Record<string, string> = {
  OPEN: '待处理',
  ESCALATED: '已升级',
  RESOLVED: '已解决'
}

function exceptionStatusBadge(status: string) {
  switch (status) {
    case 'OPEN': return 'badge-warning'
    case 'ESCALATED': return 'badge-danger'
    case 'RESOLVED': return 'badge-success'
    default: return 'badge-neutral'
  }
}

function formatDateTime(value?: string) {
  if (!value) return '—'
  return new Date(value).toLocaleString('zh-CN', { hour12: false })
}

function formatTime(value?: string) {
  return value ? value.slice(0, 5) : '—'
}

function formatPrice(value?: string) {
  if (!value) return '—'
  const n = Number(value)
  return Number.isNaN(n) ? value : `¥${n.toFixed(2)}`
}

// ------------------------------------------------------------------
// 服务目录选项（订单筛选 / 创建订单 / 派单要求资质共用）
// ------------------------------------------------------------------
const serviceOptions = ref<HomeServiceItem[]>([])

async function loadServiceOptions() {
  try {
    const result = await fetchHomeServices({ page: 1, pageSize: 100 })
    serviceOptions.value = result.items
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '加载服务目录失败')
  }
}

const publishedServiceOptions = computed(() => serviceOptions.value.filter(s => s.status === 'published'))

// ------------------------------------------------------------------
// 上门订单列表
// ------------------------------------------------------------------
const orderQuery = reactive({
  keyword: '',
  status: '',
  serviceCode: '',
  dateRange: [] as string[],
  page: 1,
  pageSize: 20
})
const orderRows = ref<HomeVisitOrderSummary[]>([])
const orderTotal = ref(0)
const orderLoading = ref(false)

async function loadOrders() {
  orderLoading.value = true
  try {
    const result = await fetchHomeVisitOrders({
      keyword: orderQuery.keyword || undefined,
      status: orderQuery.status || undefined,
      serviceCode: orderQuery.serviceCode || undefined,
      dateFrom: orderQuery.dateRange?.[0] || undefined,
      dateTo: orderQuery.dateRange?.[1] || undefined,
      page: orderQuery.page,
      pageSize: orderQuery.pageSize
    })
    orderRows.value = result.items
    orderTotal.value = result.total
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '加载上门订单失败')
  } finally {
    orderLoading.value = false
  }
}

function resetOrderQuery() {
  orderQuery.keyword = ''
  orderQuery.status = ''
  orderQuery.serviceCode = ''
  orderQuery.dateRange = []
  orderQuery.page = 1
  loadOrders()
}

// ------------------------------------------------------------------
// 创建上门订单（服务 published + 客户远程搜索 + 客户 active 地址）
// ------------------------------------------------------------------
const createVisible = ref(false)
const createLoading = ref(false)
const createForm = reactive({
  serviceCode: '',
  customerId: '',
  addressId: '',
  scheduledDate: '',
  startTime: '',
  endTime: '',
  remark: ''
})
const createCustomerOptions = ref<CustomerSummary[]>([])
const createAddressOptions = ref<HomeAddress[]>([])
const createAddressLoading = ref(false)

const selectedCreateService = computed(
  () => serviceOptions.value.find(s => s.serviceCode === createForm.serviceCode) || null
)

async function searchCreateCustomers(keyword: string) {
  try {
    const result = await fetchCustomers({ keyword: keyword || undefined, page: 1, pageSize: 10 })
    createCustomerOptions.value = result.items
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '搜索客户失败')
  }
}

watch(() => createForm.customerId, async (customerId) => {
  createForm.addressId = ''
  createAddressOptions.value = []
  if (!customerId) return
  createAddressLoading.value = true
  try {
    const items = await fetchHomeAddresses(customerId)
    createAddressOptions.value = items.filter(a => a.status === 'active')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '加载客户地址失败')
  } finally {
    createAddressLoading.value = false
  }
})

function openCreate() {
  createForm.serviceCode = ''
  createForm.customerId = ''
  createForm.addressId = ''
  createForm.scheduledDate = ''
  createForm.startTime = ''
  createForm.endTime = ''
  createForm.remark = ''
  createAddressOptions.value = []
  createVisible.value = true
  searchCreateCustomers('')
}

async function submitCreate() {
  if (!createForm.serviceCode || !createForm.customerId || !createForm.addressId ||
      !createForm.scheduledDate || !createForm.startTime || !createForm.endTime) {
    ElMessage.warning('请填写服务、客户、地址、日期与时间')
    return
  }
  createLoading.value = true
  try {
    await createHomeVisitOrder({
      serviceCode: createForm.serviceCode,
      customerId: createForm.customerId,
      addressId: createForm.addressId,
      scheduledDate: createForm.scheduledDate,
      startTime: createForm.startTime,
      endTime: createForm.endTime,
      remark: createForm.remark || undefined
    })
    ElMessage.success('上门订单创建成功')
    createVisible.value = false
    loadOrders()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '创建订单失败')
  } finally {
    createLoading.value = false
  }
}

// ------------------------------------------------------------------
// 订单详情抽屉与状态流转操作
// ------------------------------------------------------------------
const orderDetailVisible = ref(false)
const orderDetail = ref<HomeVisitOrderDetail | null>(null)

async function openOrderDetail(row: HomeVisitOrderSummary) {
  try {
    orderDetail.value = await fetchHomeVisitOrder(row.id)
    orderDetailVisible.value = true
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '加载订单详情失败')
  }
}

async function refreshOrderDetail() {
  if (!orderDetail.value) return
  try {
    orderDetail.value = await fetchHomeVisitOrder(orderDetail.value.id)
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '刷新订单详情失败')
  }
}

const canReviewOrder = computed(() => orderDetail.value?.status === 'PENDING' && canManage.value)
const canDispatchOrder = computed(() => orderDetail.value?.status === 'APPROVED' && canWrite.value)
const canAcceptOrder = computed(() => orderDetail.value?.status === 'ASSIGNED' && canWrite.value)
const canRejectOrder = computed(() => orderDetail.value?.status === 'ASSIGNED' && canManage.value)
const canStartOrder = computed(() => orderDetail.value?.status === 'ACCEPTED' && canWrite.value)
const canRecordExecution = computed(() => orderDetail.value?.status === 'IN_PROGRESS' && canWrite.value)
const canReportException = computed(() => orderDetail.value?.status === 'IN_PROGRESS' && canWrite.value)
const canFinishOrder = computed(() => orderDetail.value?.status === 'IN_PROGRESS' && canManage.value)
const canCancelOrder = computed(() =>
  orderDetail.value !== null &&
  ['PENDING', 'APPROVED', 'ASSIGNED', 'ACCEPTED'].includes(orderDetail.value.status) &&
  canManage.value)

const unresolvedExceptionCount = computed(
  () => (orderDetail.value?.exceptions ?? []).filter(e => e.status !== 'RESOLVED').length
)

const orderTimeline = computed(() => {
  const detail = orderDetail.value
  if (!detail) return []
  const items: { label: string; time?: string; note?: string; type: 'primary' | 'success' | 'warning' | 'danger' | 'info' }[] = [
    { label: '订单创建', time: detail.createdAt, type: 'primary' }
  ]
  if (detail.status === 'REJECTED') {
    items.push({ label: '审核驳回', note: detail.reviewReason, type: 'danger' })
  } else if (['APPROVED', 'ASSIGNED', 'ACCEPTED', 'IN_PROGRESS', 'COMPLETED'].includes(detail.status)) {
    items.push({ label: '审核通过', type: 'success' })
  }
  if (detail.assignedAt) items.push({ label: `已派单给 ${detail.staffName || '—'}`, time: detail.assignedAt, type: 'primary' })
  if (detail.acceptedAt) items.push({ label: '服务人员已接单', time: detail.acceptedAt, type: 'success' })
  if (detail.actualStartAt) items.push({ label: '服务开始', time: detail.actualStartAt, type: 'success' })
  if (detail.actualEndAt) {
    items.push({
      label: '服务结束',
      time: detail.actualEndAt,
      note: detail.durationMinutes != null ? `实际时长 ${detail.durationMinutes} 分钟` : undefined,
      type: 'success'
    })
  }
  if (detail.cancelReason) items.push({ label: '订单取消', note: detail.cancelReason, type: 'warning' })
  return items
})

async function runOrderAction(label: string, action: () => Promise<unknown>) {
  try {
    await action()
    ElMessage.success(`${label}成功`)
    await refreshOrderDetail()
    loadOrders()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : `${label}失败`)
  }
}

async function promptValue(title: string, message: string, required = true): Promise<string | null> {
  try {
    const input = await ElMessageBox.prompt(message, title, {
      inputValidator: v => (required ? ((v && v.trim().length > 0) || '请填写内容') : true)
    })
    return input.value.trim()
  } catch {
    return null
  }
}

// 订单审核对话框
const orderReviewVisible = ref(false)
const orderReviewForm = reactive({ approved: true, reason: '' })

function openOrderReview() {
  orderReviewForm.approved = true
  orderReviewForm.reason = ''
  orderReviewVisible.value = true
}

async function submitOrderReview() {
  if (!orderDetail.value) return
  if (!orderReviewForm.approved && !orderReviewForm.reason.trim()) {
    ElMessage.warning('驳回时必须填写原因')
    return
  }
  const orderId = orderDetail.value.id
  const approved = orderReviewForm.approved
  await runOrderAction(approved ? '审核通过' : '审核驳回', () =>
    reviewHomeVisitOrder(orderId, {
      approved,
      reason: orderReviewForm.reason.trim() || undefined
    }))
  orderReviewVisible.value = false
}

async function acceptOrder() {
  if (!orderDetail.value) return
  const orderId = orderDetail.value.id
  await runOrderAction('接单', () => acceptHomeVisitOrder(orderId))
}

async function rejectOrder() {
  if (!orderDetail.value) return
  const reason = await promptValue('拒绝接单', '请填写拒绝原因')
  if (reason === null) return
  const orderId = orderDetail.value.id
  await runOrderAction('拒单', () => rejectHomeVisitOrder(orderId, reason))
}

async function startOrder() {
  if (!orderDetail.value) return
  const orderId = orderDetail.value.id
  await runOrderAction('开始服务', () => startHomeVisitOrder(orderId))
}

async function finishOrder() {
  if (!orderDetail.value) return
  const remark = await promptValue('完成服务', '可填写服务完成备注（选填）', false)
  if (remark === null) return
  const orderId = orderDetail.value.id
  // 存在未解决异常时后端返回 422，原样展示后端 message
  await runOrderAction('完成服务', () => finishHomeVisitOrder(orderId, remark || undefined))
}

async function cancelOrder() {
  if (!orderDetail.value) return
  const reason = await promptValue('取消订单', '请填写取消原因')
  if (reason === null) return
  const orderId = orderDetail.value.id
  await runOrderAction('取消订单', () => cancelHomeVisitOrder(orderId, reason))
}

// ------------------------------------------------------------------
// 派单对话框（从人员资质候选中选人，展示资质与到期状态）
// ------------------------------------------------------------------
const dispatchVisible = ref(false)
const dispatchLoading = ref(false)
const dispatchForm = reactive({ staffUserId: '' })
const dispatchQuals = ref<HomeStaffQualification[]>([])

const dispatchCandidates = computed(() => {
  const seen = new Set<string>()
  return dispatchQuals.value
    .slice()
    .sort((a, b) => Number(a.expired) - Number(b.expired))
    .filter(q => {
      if (seen.has(q.userId)) return false
      seen.add(q.userId)
      return true
    })
})

async function openDispatch() {
  dispatchForm.staffUserId = ''
  dispatchQuals.value = []
  dispatchVisible.value = true
  dispatchLoading.value = true
  try {
    const result = await fetchStaffQualifications({ page: 1, pageSize: 100 })
    dispatchQuals.value = result.items
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '加载人员资质候选失败')
  } finally {
    dispatchLoading.value = false
  }
}

async function submitDispatch() {
  if (!orderDetail.value || !dispatchForm.staffUserId) {
    ElMessage.warning('请选择服务人员')
    return
  }
  const orderId = orderDetail.value.id
  dispatchLoading.value = true
  try {
    await dispatchHomeVisitOrder(orderId, dispatchForm.staffUserId)
    ElMessage.success('派单成功')
    dispatchVisible.value = false
    await refreshOrderDetail()
    loadOrders()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '派单失败')
  } finally {
    dispatchLoading.value = false
  }
}

// ------------------------------------------------------------------
// 执行记录追加对话框
// ------------------------------------------------------------------
const execVisible = ref(false)
const execLoading = ref(false)
const execForm = reactive({ content: '', visitAt: '' })

function openExecDialog() {
  execForm.content = ''
  execForm.visitAt = ''
  execVisible.value = true
}

async function submitExecution() {
  if (!orderDetail.value) return
  if (!execForm.content.trim()) {
    ElMessage.warning('请填写执行记录内容')
    return
  }
  const orderId = orderDetail.value.id
  execLoading.value = true
  try {
    await addOrderExecution(orderId, {
      content: execForm.content.trim(),
      visitAt: execForm.visitAt || undefined
    })
    ElMessage.success('执行记录已追加')
    execVisible.value = false
    await refreshOrderDetail()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '追加执行记录失败')
  } finally {
    execLoading.value = false
  }
}

// ------------------------------------------------------------------
// 异常上报对话框
// ------------------------------------------------------------------
const excReportVisible = ref(false)
const excReportLoading = ref(false)
const excReportForm = reactive({ level: 'low', category: '', description: '' })

function openExcReport() {
  excReportForm.level = 'low'
  excReportForm.category = ''
  excReportForm.description = ''
  excReportVisible.value = true
}

async function submitExceptionReport() {
  if (!orderDetail.value) return
  if (!excReportForm.category.trim() || !excReportForm.description.trim()) {
    ElMessage.warning('请填写异常类别与描述')
    return
  }
  const orderId = orderDetail.value.id
  excReportLoading.value = true
  try {
    await reportOrderException(orderId, {
      level: excReportForm.level as 'low' | 'medium' | 'high' | 'critical',
      category: excReportForm.category.trim(),
      description: excReportForm.description.trim()
    })
    ElMessage.success('异常已上报')
    excReportVisible.value = false
    await refreshOrderDetail()
    loadExceptions()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '上报异常失败')
  } finally {
    excReportLoading.value = false
  }
}

// ------------------------------------------------------------------
// 异常升级 / 解决（详情抽屉与看板共用）
// ------------------------------------------------------------------
async function escalateException(item: HomeVisitException, after: () => void | Promise<void>) {
  const reason = await promptValue('升级异常', '请填写升级原因（如：需医务部介入）')
  if (reason === null) return
  try {
    await escalateHomeVisitException(item.id, reason)
    ElMessage.success('异常已升级')
    await after()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '升级异常失败')
  }
}

async function resolveException(item: HomeVisitException, after: () => void | Promise<void>) {
  const resolution = await promptValue('处置异常', '请填写处置结果')
  if (resolution === null) return
  try {
    await resolveHomeVisitException(item.id, resolution)
    ElMessage.success('异常已解决')
    await after()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '处置异常失败')
  }
}

function refreshAfterDetailException() {
  return async () => {
    await refreshOrderDetail()
    loadOrders()
    loadExceptions()
  }
}

// ------------------------------------------------------------------
// 服务目录
// ------------------------------------------------------------------
const serviceQuery = reactive({ keyword: '', category: '', status: '', page: 1, pageSize: 20 })
const serviceRows = ref<HomeServiceItem[]>([])
const serviceTotal = ref(0)
const serviceLoading = ref(false)

async function loadServices() {
  serviceLoading.value = true
  try {
    const result = await fetchHomeServices({
      keyword: serviceQuery.keyword || undefined,
      category: serviceQuery.category || undefined,
      status: serviceQuery.status || undefined,
      page: serviceQuery.page,
      pageSize: serviceQuery.pageSize
    })
    serviceRows.value = result.items
    serviceTotal.value = result.total
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '加载服务目录失败')
  } finally {
    serviceLoading.value = false
  }
}

function resetServiceQuery() {
  serviceQuery.keyword = ''
  serviceQuery.category = ''
  serviceQuery.status = ''
  serviceQuery.page = 1
  loadServices()
}

const serviceDialogVisible = ref(false)
const serviceEditing = ref<HomeServiceItem | null>(null)
const serviceForm = reactive({
  serviceCode: '',
  serviceName: '',
  category: 'medical',
  requiredQualType: '',
  durationMinutes: 60,
  price: '',
  description: ''
})

function openServiceCreate() {
  serviceEditing.value = null
  serviceForm.serviceCode = ''
  serviceForm.serviceName = ''
  serviceForm.category = 'medical'
  serviceForm.requiredQualType = ''
  serviceForm.durationMinutes = 60
  serviceForm.price = ''
  serviceForm.description = ''
  serviceDialogVisible.value = true
}

function openServiceEdit(item: HomeServiceItem) {
  serviceEditing.value = item
  serviceForm.serviceCode = item.serviceCode
  serviceForm.serviceName = item.serviceName
  serviceForm.category = item.category
  serviceForm.requiredQualType = item.requiredQualType ?? ''
  serviceForm.durationMinutes = item.durationMinutes
  serviceForm.price = item.price
  serviceForm.description = item.description ?? ''
  serviceDialogVisible.value = true
}

async function submitService() {
  if (!serviceEditing.value && !serviceForm.serviceCode.trim()) {
    ElMessage.warning('请填写服务编码')
    return
  }
  if (!serviceForm.serviceName.trim()) {
    ElMessage.warning('请填写服务名称')
    return
  }
  if (!serviceForm.durationMinutes || serviceForm.durationMinutes <= 0) {
    ElMessage.warning('请填写有效的服务时长')
    return
  }
  if (!/^\d+(\.\d{1,2})?$/.test(serviceForm.price.trim())) {
    ElMessage.warning('请填写正确的价格（如 280.00）')
    return
  }
  try {
    if (serviceEditing.value) {
      await updateHomeService(serviceEditing.value.serviceCode, {
        serviceName: serviceForm.serviceName.trim(),
        category: serviceForm.category as 'medical' | 'non_medical',
        requiredQualType: serviceForm.requiredQualType || undefined,
        durationMinutes: serviceForm.durationMinutes,
        price: serviceForm.price.trim(),
        description: serviceForm.description.trim() || undefined
      })
      ElMessage.success('服务已更新')
    } else {
      await createHomeService({
        serviceCode: serviceForm.serviceCode.trim(),
        serviceName: serviceForm.serviceName.trim(),
        category: serviceForm.category as 'medical' | 'non_medical',
        requiredQualType: serviceForm.requiredQualType || undefined,
        durationMinutes: serviceForm.durationMinutes,
        price: serviceForm.price.trim(),
        description: serviceForm.description.trim() || undefined
      })
      ElMessage.success('服务已创建')
    }
    serviceDialogVisible.value = false
    await Promise.all([loadServices(), loadServiceOptions()])
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '保存服务失败')
  }
}

async function submitForReview(item: HomeServiceItem) {
  await ElMessageBox.confirm(`确认将「${item.serviceName}」提交审核？`, '提示', { type: 'warning' })
  try {
    await submitHomeService(item.serviceCode)
    ElMessage.success('已提交审核')
    await Promise.all([loadServices(), loadServiceOptions()])
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '提交审核失败')
  }
}

const svcReviewVisible = ref(false)
const svcReviewTarget = ref<HomeServiceItem | null>(null)
const svcReviewForm = reactive({ approved: true, reason: '' })

function openServiceReview(item: HomeServiceItem) {
  svcReviewTarget.value = item
  svcReviewForm.approved = true
  svcReviewForm.reason = ''
  svcReviewVisible.value = true
}

async function submitServiceReview() {
  if (!svcReviewTarget.value) return
  if (!svcReviewForm.approved && !svcReviewForm.reason.trim()) {
    ElMessage.warning('驳回时必须填写原因')
    return
  }
  try {
    await reviewHomeService(svcReviewTarget.value.serviceCode, {
      approved: svcReviewForm.approved,
      reason: svcReviewForm.reason.trim() || undefined
    })
    ElMessage.success(svcReviewForm.approved ? '审核通过' : '已驳回')
    svcReviewVisible.value = false
    await Promise.all([loadServices(), loadServiceOptions()])
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '审核失败')
  }
}

async function disableService(item: HomeServiceItem) {
  await ElMessageBox.confirm(`确认停用「${item.serviceName}」？`, '提示', { type: 'warning' })
  try {
    await changeHomeServiceStatus(item.serviceCode, 'disabled')
    ElMessage.success('服务已停用')
    await Promise.all([loadServices(), loadServiceOptions()])
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '停用服务失败')
  }
}

// ------------------------------------------------------------------
// 客户地址
// ------------------------------------------------------------------
const addrCustomerId = ref('')
const addrCustomerOptions = ref<CustomerSummary[]>([])
const addrRows = ref<HomeAddress[]>([])
const addrLoading = ref(false)

async function searchAddrCustomers(keyword: string) {
  try {
    const result = await fetchCustomers({ keyword: keyword || undefined, page: 1, pageSize: 10 })
    addrCustomerOptions.value = result.items
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '搜索客户失败')
  }
}

async function loadAddresses() {
  if (!addrCustomerId.value) {
    addrRows.value = []
    return
  }
  addrLoading.value = true
  try {
    addrRows.value = await fetchHomeAddresses(addrCustomerId.value)
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '加载客户地址失败')
  } finally {
    addrLoading.value = false
  }
}

watch(addrCustomerId, loadAddresses)

const addrDialogVisible = ref(false)
const addrEditing = ref<HomeAddress | null>(null)
const addrForm = reactive({ address: '', contactName: '', contactPhone: '', isDefault: false, remark: '' })

function openAddrCreate() {
  if (!addrCustomerId.value) {
    ElMessage.warning('请先选择客户')
    return
  }
  addrEditing.value = null
  addrForm.address = ''
  addrForm.contactName = ''
  addrForm.contactPhone = ''
  addrForm.isDefault = false
  addrForm.remark = ''
  addrDialogVisible.value = true
}

function openAddrEdit(item: HomeAddress) {
  addrEditing.value = item
  addrForm.address = item.address
  addrForm.contactName = item.contactName
  addrForm.contactPhone = ''
  addrForm.isDefault = item.isDefault
  addrForm.remark = item.remark ?? ''
  addrDialogVisible.value = true
}

async function submitAddress() {
  if (!addrForm.address.trim() || !addrForm.contactName.trim()) {
    ElMessage.warning('请填写详细地址与联系人')
    return
  }
  try {
    if (addrEditing.value) {
      await updateHomeAddress(addrEditing.value.id, {
        address: addrForm.address.trim(),
        contactName: addrForm.contactName.trim(),
        contactPhone: addrForm.contactPhone || undefined,
        isDefault: addrForm.isDefault,
        remark: addrForm.remark.trim() || undefined
      })
      ElMessage.success('地址已更新')
    } else {
      await createHomeAddress({
        customerId: addrCustomerId.value,
        address: addrForm.address.trim(),
        contactName: addrForm.contactName.trim(),
        contactPhone: addrForm.contactPhone || undefined,
        isDefault: addrForm.isDefault,
        remark: addrForm.remark.trim() || undefined
      })
      ElMessage.success('地址已创建')
    }
    addrDialogVisible.value = false
    loadAddresses()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '保存地址失败')
  }
}

async function toggleAddress(item: HomeAddress) {
  const next = item.status === 'active' ? 'disabled' : 'active'
  await ElMessageBox.confirm(`确认将该地址设为${next === 'active' ? '启用' : '停用'}？`, '提示', { type: 'warning' })
  try {
    await changeHomeAddressStatus(item.id, next)
    ElMessage.success('地址状态已更新')
    loadAddresses()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '地址状态变更失败')
  }
}

// ------------------------------------------------------------------
// 人员资质
// ------------------------------------------------------------------
const qualQuery = reactive({ userId: '', qualType: '', page: 1, pageSize: 20 })
const qualRows = ref<HomeStaffQualification[]>([])
const qualTotal = ref(0)
const qualLoading = ref(false)

async function loadQuals() {
  qualLoading.value = true
  try {
    const result = await fetchStaffQualifications({
      userId: qualQuery.userId || undefined,
      qualType: qualQuery.qualType || undefined,
      page: qualQuery.page,
      pageSize: qualQuery.pageSize
    })
    qualRows.value = result.items
    qualTotal.value = result.total
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '加载人员资质失败')
  } finally {
    qualLoading.value = false
  }
}

function resetQualQuery() {
  qualQuery.userId = ''
  qualQuery.qualType = ''
  qualQuery.page = 1
  loadQuals()
}

const qualDialogVisible = ref(false)
const qualForm = reactive({ userId: '', qualType: 'nurse', certNo: '', issuedAt: '', expiresAt: '' })
const userOptions = ref<UserResponse[]>([])

async function searchUsers(keyword: string) {
  try {
    const result = await fetchUsers({ keyword: keyword || undefined, page: 1, pageSize: 10 })
    userOptions.value = result.items
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '搜索系统用户失败')
  }
}

function openQualCreate() {
  qualForm.userId = ''
  qualForm.qualType = 'nurse'
  qualForm.certNo = ''
  qualForm.issuedAt = ''
  qualForm.expiresAt = ''
  qualDialogVisible.value = true
  searchUsers('')
}

async function submitQual() {
  if (!qualForm.userId.trim()) {
    ElMessage.warning('请选择或填写系统用户 ID')
    return
  }
  if (!qualForm.certNo.trim() || !qualForm.issuedAt) {
    ElMessage.warning('请填写证书编号与发证日期')
    return
  }
  try {
    await createStaffQualification({
      userId: qualForm.userId.trim(),
      qualType: qualForm.qualType as 'nurse' | 'caregiver' | 'health_manager' | 'doctor' | 'other',
      certNo: qualForm.certNo.trim(),
      issuedAt: qualForm.issuedAt,
      expiresAt: qualForm.expiresAt || undefined
    })
    ElMessage.success('资质已登记')
    qualDialogVisible.value = false
    loadQuals()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '登记资质失败')
  }
}

async function revokeQual(item: HomeStaffQualification) {
  await ElMessageBox.confirm(`确认停用「${item.displayName || item.username}」的资质？`, '提示', { type: 'warning' })
  try {
    await revokeStaffQualification(item.id)
    ElMessage.success('资质已停用')
    loadQuals()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '停用资质失败')
  }
}

// ------------------------------------------------------------------
// 异常看板
// ------------------------------------------------------------------
const excQuery = reactive({ status: '', level: '', page: 1, pageSize: 20 })
const excRows = ref<HomeVisitExceptionBoardItem[]>([])
const excTotal = ref(0)
const excLoading = ref(false)

async function loadExceptions() {
  excLoading.value = true
  try {
    const result = await fetchHomeVisitExceptions({
      status: excQuery.status || undefined,
      level: excQuery.level || undefined,
      page: excQuery.page,
      pageSize: excQuery.pageSize
    })
    excRows.value = result.items
    excTotal.value = result.total
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '加载异常看板失败')
  } finally {
    excLoading.value = false
  }
}

function resetExcQuery() {
  excQuery.status = ''
  excQuery.level = ''
  excQuery.page = 1
  loadExceptions()
}

watch(activeTab, (tab) => {
  if (tab === 'services' && serviceRows.value.length === 0) loadServices()
  if (tab === 'addresses') searchAddrCustomers('')
  if (tab === 'quals' && qualRows.value.length === 0) loadQuals()
  if (tab === 'exceptions' && excRows.value.length === 0) loadExceptions()
})

onMounted(() => {
  if (canRead.value) {
    loadOrders()
    loadServiceOptions()
  }
})
</script>

<template>
  <div class="page">
    <header class="page-head">
      <div>
        <h1>上门服务</h1>
        <p>上门订单审核派单与履约、服务目录审核、客户地址、人员资质与异常处置</p>
      </div>
      <div class="head-metrics">
        <span class="metric">订单总数 <strong>{{ orderTotal }}</strong></span>
      </div>
    </header>

    <el-tabs v-model="activeTab" class="tab-card">
      <!-- ============ 上门订单 ============ -->
      <el-tab-pane label="上门订单" name="orders">
        <div class="card">
          <div class="toolbar">
            <el-input v-model="orderQuery.keyword" placeholder="订单号/客户/服务名称" clearable style="width: 190px"
                      @keyup.enter="orderQuery.page = 1; loadOrders()" />
            <el-select v-model="orderQuery.status" placeholder="状态" clearable style="width: 130px">
              <el-option label="待审核" value="PENDING" />
              <el-option label="已审核" value="APPROVED" />
              <el-option label="已驳回" value="REJECTED" />
              <el-option label="已派单" value="ASSIGNED" />
              <el-option label="已接单" value="ACCEPTED" />
              <el-option label="服务中" value="IN_PROGRESS" />
              <el-option label="已完成" value="COMPLETED" />
              <el-option label="已取消" value="CANCELLED" />
            </el-select>
            <el-select v-model="orderQuery.serviceCode" placeholder="服务" clearable filterable style="width: 160px">
              <el-option v-for="s in serviceOptions" :key="s.serviceCode"
                         :label="`${s.serviceName}（${s.serviceCode}）`" :value="s.serviceCode" />
            </el-select>
            <el-date-picker v-model="orderQuery.dateRange" type="daterange" value-format="YYYY-MM-DD"
                            start-placeholder="上门开始" end-placeholder="上门结束" style="width: 240px" />
            <el-button type="primary" @click="orderQuery.page = 1; loadOrders()">查询</el-button>
            <el-button @click="resetOrderQuery">重置</el-button>
            <span class="spacer" />
            <el-button v-if="canWrite" type="primary" @click="openCreate">创建订单</el-button>
          </div>

          <el-table v-loading="orderLoading" :data="orderRows" stripe>
            <el-table-column prop="orderNo" label="订单号" width="130">
              <template #default="{ row }"><span class="cell-mono">{{ row.orderNo }}</span></template>
            </el-table-column>
            <el-table-column label="服务" min-width="150">
              <template #default="{ row }">
                {{ row.serviceName || '—' }}
                <div class="sub">{{ row.serviceCode }}</div>
              </template>
            </el-table-column>
            <el-table-column label="客户" min-width="110">
              <template #default="{ row }">{{ row.customerName || '—' }}</template>
            </el-table-column>
            <el-table-column label="计划上门" min-width="160">
              <template #default="{ row }">
                {{ row.scheduledDate }} {{ formatTime(row.startTime) }}-{{ formatTime(row.endTime) }}
              </template>
            </el-table-column>
            <el-table-column label="服务人员" width="100">
              <template #default="{ row }">{{ row.staffName || '—' }}</template>
            </el-table-column>
            <el-table-column label="状态" width="90">
              <template #default="{ row }">
                <span :class="['badge', orderBadge(row.status)]">{{ orderStatusLabel(row.status) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="创建时间" width="170">
              <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
            </el-table-column>
            <el-table-column label="操作" width="90" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" @click="openOrderDetail(row)">详情</el-button>
              </template>
            </el-table-column>
          </el-table>

          <el-pagination v-model:current-page="orderQuery.page" v-model:page-size="orderQuery.pageSize"
                         :total="orderTotal" :page-sizes="[10, 20, 50]"
                         layout="total, sizes, prev, pager, next" class="table-pagination"
                         @change="loadOrders" />
        </div>
      </el-tab-pane>

      <!-- ============ 服务目录 ============ -->
      <el-tab-pane label="服务目录" name="services">
        <div class="card">
          <div class="toolbar">
            <el-input v-model="serviceQuery.keyword" placeholder="编码/名称" clearable style="width: 170px"
                      @keyup.enter="serviceQuery.page = 1; loadServices()" />
            <el-select v-model="serviceQuery.category" placeholder="类别" clearable style="width: 120px">
              <el-option label="医疗类" value="medical" />
              <el-option label="非医疗类" value="non_medical" />
            </el-select>
            <el-select v-model="serviceQuery.status" placeholder="状态" clearable style="width: 120px">
              <el-option label="草稿" value="draft" />
              <el-option label="待审核" value="pending_review" />
              <el-option label="已发布" value="published" />
              <el-option label="已驳回" value="rejected" />
              <el-option label="已停用" value="disabled" />
            </el-select>
            <el-button type="primary" @click="serviceQuery.page = 1; loadServices()">查询</el-button>
            <el-button @click="resetServiceQuery">重置</el-button>
            <span class="spacer" />
            <el-button v-if="canWrite" type="primary" @click="openServiceCreate">新建服务</el-button>
          </div>

          <el-table v-loading="serviceLoading" :data="serviceRows" stripe>
            <el-table-column prop="serviceCode" label="编码" width="130">
              <template #default="{ row }"><span class="cell-mono">{{ row.serviceCode }}</span></template>
            </el-table-column>
            <el-table-column prop="serviceName" label="名称" min-width="140" />
            <el-table-column label="类别" width="90">
              <template #default="{ row }">{{ categoryLabels[row.category] || row.category }}</template>
            </el-table-column>
            <el-table-column label="所需资质" width="110">
              <template #default="{ row }">
                <span v-if="row.requiredQualType">{{ qualTypeLabels[row.requiredQualType] || row.requiredQualType }}</span>
                <span v-else class="cell-muted">不限</span>
              </template>
            </el-table-column>
            <el-table-column label="时长" width="80">
              <template #default="{ row }">{{ row.durationMinutes }} 分</template>
            </el-table-column>
            <el-table-column label="价格" width="100">
              <template #default="{ row }">{{ formatPrice(row.price) }}</template>
            </el-table-column>
            <el-table-column label="状态" width="90">
              <template #default="{ row }">
                <span :class="['badge', serviceBadge(row.status)]">{{ serviceStatusLabels[row.status] || row.status }}</span>
              </template>
            </el-table-column>
            <el-table-column label="审核意见" min-width="140">
              <template #default="{ row }">
                <span v-if="row.status === 'rejected'" class="reject-reason">{{ row.reviewReason || '—' }}</span>
                <span v-else class="cell-muted">—</span>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="220" fixed="right">
              <template #default="{ row }">
                <el-button v-if="canWrite && ['draft', 'rejected'].includes(row.status)" link type="primary"
                           @click="openServiceEdit(row)">编辑</el-button>
                <el-button v-if="canWrite && ['draft', 'rejected'].includes(row.status)" link type="primary"
                           @click="submitForReview(row)">提交审核</el-button>
                <el-button v-if="canManage && row.status === 'pending_review'" link type="primary"
                           @click="openServiceReview(row)">审核</el-button>
                <el-button v-if="canManage && row.status === 'published'" link type="danger"
                           @click="disableService(row)">停用</el-button>
              </template>
            </el-table-column>
          </el-table>

          <el-pagination v-model:current-page="serviceQuery.page" v-model:page-size="serviceQuery.pageSize"
                         :total="serviceTotal" :page-sizes="[10, 20, 50]"
                         layout="total, sizes, prev, pager, next" class="table-pagination"
                         @change="loadServices" />
        </div>
      </el-tab-pane>

      <!-- ============ 客户地址 ============ -->
      <el-tab-pane label="客户地址" name="addresses">
        <div class="card">
          <div class="toolbar">
            <el-select v-model="addrCustomerId" filterable remote :remote-method="searchAddrCustomers"
                       placeholder="输入姓名/客户号/手机号搜索客户" style="width: 280px">
              <el-option v-for="c in addrCustomerOptions" :key="c.id"
                         :label="`${c.name}（${c.customerNo}）`" :value="c.id" />
            </el-select>
            <span v-if="!addrCustomerId" class="cell-muted">请先选择客户以查看其服务地址</span>
            <span class="spacer" />
            <el-button v-if="canWrite && addrCustomerId" type="primary" @click="openAddrCreate">新建地址</el-button>
          </div>

          <el-table v-loading="addrLoading" :data="addrRows" stripe>
            <el-table-column prop="address" label="详细地址" min-width="220" />
            <el-table-column prop="contactName" label="联系人" width="110" />
            <el-table-column label="联系电话" width="130">
              <template #default="{ row }">{{ row.contactPhoneMasked || '—' }}</template>
            </el-table-column>
            <el-table-column label="默认" width="80">
              <template #default="{ row }">
                <span v-if="row.isDefault" class="badge badge-success">默认</span>
                <span v-else class="cell-muted">—</span>
              </template>
            </el-table-column>
            <el-table-column label="状态" width="90">
              <template #default="{ row }">
                <span :class="['badge', row.status === 'active' ? 'badge-success' : 'badge-neutral']">
                  {{ row.status === 'active' ? '启用' : '停用' }}
                </span>
              </template>
            </el-table-column>
            <el-table-column prop="remark" label="备注" min-width="140">
              <template #default="{ row }">{{ row.remark || '—' }}</template>
            </el-table-column>
            <el-table-column v-if="canWrite" label="操作" width="130" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" @click="openAddrEdit(row)">编辑</el-button>
                <el-button link :type="row.status === 'active' ? 'danger' : 'primary'" @click="toggleAddress(row)">
                  {{ row.status === 'active' ? '停用' : '启用' }}
                </el-button>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </el-tab-pane>

      <!-- ============ 人员资质 ============ -->
      <el-tab-pane label="人员资质" name="quals">
        <div class="card">
          <div class="toolbar">
            <el-input v-model="qualQuery.userId" placeholder="用户 ID" clearable style="width: 180px"
                      @keyup.enter="qualQuery.page = 1; loadQuals()" />
            <el-select v-model="qualQuery.qualType" placeholder="资质类型" clearable style="width: 140px">
              <el-option label="护士" value="nurse" />
              <el-option label="照护师" value="caregiver" />
              <el-option label="健康管理师" value="health_manager" />
              <el-option label="医生" value="doctor" />
              <el-option label="其他" value="other" />
            </el-select>
            <el-button type="primary" @click="qualQuery.page = 1; loadQuals()">查询</el-button>
            <el-button @click="resetQualQuery">重置</el-button>
            <span class="spacer" />
            <el-button v-if="canWrite" type="primary" @click="openQualCreate">新建资质</el-button>
          </div>

          <el-table v-loading="qualLoading" :data="qualRows" stripe>
            <el-table-column label="人员" min-width="140">
              <template #default="{ row }">
                {{ row.displayName || '—' }}
                <div class="sub">{{ row.username }}</div>
              </template>
            </el-table-column>
            <el-table-column label="资质类型" width="120">
              <template #default="{ row }">{{ qualTypeLabels[row.qualType] || row.qualType }}</template>
            </el-table-column>
            <el-table-column prop="certNo" label="证书编号" min-width="140">
              <template #default="{ row }"><span class="cell-mono">{{ row.certNo }}</span></template>
            </el-table-column>
            <el-table-column prop="issuedAt" label="发证日期" width="110" />
            <el-table-column label="有效期至" width="110">
              <template #default="{ row }">{{ row.expiresAt || '长期' }}</template>
            </el-table-column>
            <el-table-column label="状态" width="100">
              <template #default="{ row }">
                <span :class="['badge', row.expired ? 'badge-danger' : 'badge-success']">
                  {{ row.expired ? '已过期' : row.status }}
                </span>
              </template>
            </el-table-column>
            <el-table-column v-if="canWrite" label="操作" width="80" fixed="right">
              <template #default="{ row }">
                <el-button v-if="row.status !== 'revoked' && row.status !== 'disabled'" link type="danger"
                           @click="revokeQual(row)">停用</el-button>
              </template>
            </el-table-column>
          </el-table>

          <el-pagination v-model:current-page="qualQuery.page" v-model:page-size="qualQuery.pageSize"
                         :total="qualTotal" :page-sizes="[10, 20, 50]"
                         layout="total, sizes, prev, pager, next" class="table-pagination"
                         @change="loadQuals" />
        </div>
      </el-tab-pane>

      <!-- ============ 异常看板 ============ -->
      <el-tab-pane label="异常看板" name="exceptions">
        <div class="card">
          <div class="toolbar">
            <el-select v-model="excQuery.status" placeholder="处置状态" clearable style="width: 130px">
              <el-option label="待处理" value="OPEN" />
              <el-option label="已升级" value="ESCALATED" />
              <el-option label="已解决" value="RESOLVED" />
            </el-select>
            <el-select v-model="excQuery.level" placeholder="等级" clearable style="width: 120px">
              <el-option label="低" value="low" />
              <el-option label="中" value="medium" />
              <el-option label="高" value="high" />
              <el-option label="严重" value="critical" />
            </el-select>
            <el-button type="primary" @click="excQuery.page = 1; loadExceptions()">查询</el-button>
            <el-button @click="resetExcQuery">重置</el-button>
          </div>

          <el-table v-loading="excLoading" :data="excRows" stripe>
            <el-table-column prop="orderNo" label="订单号" width="130">
              <template #default="{ row }"><span class="cell-mono">{{ row.orderNo }}</span></template>
            </el-table-column>
            <el-table-column label="等级" width="90">
              <template #default="{ row }">
                <span :class="['badge', exceptionLevelBadge(row.level)]">{{ exceptionLevelLabels[row.level] || row.level }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="category" label="类别" width="130" />
            <el-table-column prop="description" label="描述" min-width="200" />
            <el-table-column label="状态" width="90">
              <template #default="{ row }">
                <span :class="['badge', exceptionStatusBadge(row.status)]">{{ exceptionStatusLabels[row.status] || row.status }}</span>
              </template>
            </el-table-column>
            <el-table-column label="升级/解决时间" width="170">
              <template #default="{ row }">
                {{ row.resolvedAt ? formatDateTime(row.resolvedAt) : (row.escalatedAt ? formatDateTime(row.escalatedAt) : '—') }}
              </template>
            </el-table-column>
            <el-table-column v-if="canManage" label="操作" width="140" fixed="right">
              <template #default="{ row }">
                <el-button v-if="row.status === 'OPEN'" link type="warning"
                           @click="escalateException(row, () => loadExceptions())">升级</el-button>
                <el-button v-if="row.status === 'OPEN' || row.status === 'ESCALATED'" link type="primary"
                           @click="resolveException(row, () => loadExceptions())">解决</el-button>
              </template>
            </el-table-column>
          </el-table>

          <el-pagination v-model:current-page="excQuery.page" v-model:page-size="excQuery.pageSize"
                         :total="excTotal" :page-sizes="[10, 20, 50]"
                         layout="total, sizes, prev, pager, next" class="table-pagination"
                         @change="loadExceptions" />
        </div>
      </el-tab-pane>
    </el-tabs>

    <!-- 创建上门订单 -->
    <el-dialog v-model="createVisible" title="创建上门订单" width="540px">
      <el-form label-width="90px">
        <el-form-item label="服务" required>
          <el-select v-model="createForm.serviceCode" filterable placeholder="选择已发布的服务" style="width: 100%">
            <el-option v-for="s in publishedServiceOptions" :key="s.serviceCode"
                       :label="`${s.serviceName}（${s.serviceCode}）`" :value="s.serviceCode" />
          </el-select>
          <div v-if="selectedCreateService" class="sub service-hint">
            价格 {{ formatPrice(selectedCreateService.price) }} · 标准时长 {{ selectedCreateService.durationMinutes }} 分钟
            <template v-if="selectedCreateService.requiredQualType">
              · 要求资质：{{ qualTypeLabels[selectedCreateService.requiredQualType] || selectedCreateService.requiredQualType }}
            </template>
          </div>
        </el-form-item>
        <el-form-item label="客户" required>
          <el-select v-model="createForm.customerId" filterable remote :remote-method="searchCreateCustomers"
                     placeholder="输入姓名/客户号/手机号搜索" style="width: 100%">
            <el-option v-for="c in createCustomerOptions" :key="c.id"
                       :label="`${c.name}（${c.customerNo}）`" :value="c.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="服务地址" required>
          <el-select v-model="createForm.addressId" :loading="createAddressLoading" :disabled="!createForm.customerId"
                     placeholder="选择客户的启用地址" style="width: 100%">
            <el-option v-for="a in createAddressOptions" :key="a.id"
                       :label="`${a.address}（${a.contactName}）${a.isDefault ? ' · 默认' : ''}`" :value="a.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="上门日期" required>
          <el-date-picker v-model="createForm.scheduledDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
        <el-form-item label="开始时间" required>
          <el-time-select v-model="createForm.startTime" start="07:00" step="00:30" end="21:00" style="width: 100%" />
        </el-form-item>
        <el-form-item label="结束时间" required>
          <el-time-select v-model="createForm.endTime" :start="createForm.startTime || '07:30'" step="00:30" end="22:00"
                          style="width: 100%" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="createForm.remark" type="textarea" :rows="2" maxlength="1000" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" :loading="createLoading" @click="submitCreate">确定</el-button>
      </template>
    </el-dialog>

    <!-- 订单审核 -->
    <el-dialog v-model="orderReviewVisible" title="订单审核" width="440px">
      <el-form label-width="70px">
        <el-form-item label="结论" required>
          <el-radio-group v-model="orderReviewForm.approved">
            <el-radio :value="true">通过</el-radio>
            <el-radio :value="false">驳回</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="原因" :required="!orderReviewForm.approved">
          <el-input v-model="orderReviewForm.reason" type="textarea" :rows="3" maxlength="500"
                    :placeholder="orderReviewForm.approved ? '审核意见（选填）' : '驳回原因必填'" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="orderReviewVisible = false">取消</el-button>
        <el-button type="primary" @click="submitOrderReview">提交审核</el-button>
      </template>
    </el-dialog>

    <!-- 派单 -->
    <el-dialog v-model="dispatchVisible" title="派单：选择服务人员" width="560px">
      <el-form label-width="90px">
        <el-form-item label="服务人员" required>
          <el-select v-model="dispatchForm.staffUserId" filterable :loading="dispatchLoading"
                     placeholder="从已登记资质的人员中选择" style="width: 100%">
            <el-option v-for="q in dispatchCandidates" :key="q.id" :value="q.userId"
                       :label="`${q.displayName || q.username}（${q.username}）· ${qualTypeLabels[q.qualType] || q.qualType}${q.expired ? ' · 已过期' : ''}`" />
          </el-select>
        </el-form-item>
      </el-form>
      <div v-if="orderDetail?.requiredQualType" class="dispatch-info">
        <span class="dispatch-label">本单要求资质：</span>
        <el-tag size="small" :type="qualTypeLabels[orderDetail.requiredQualType] ? 'success' : 'info'">
          {{ qualTypeLabels[orderDetail.requiredQualType] || orderDetail.requiredQualType }}
        </el-tag>
        <span class="sub" style="margin-left: 8px">已过期人员派单可能被后端拒绝，原样提示。</span>
      </div>
      <template #footer>
        <el-button @click="dispatchVisible = false">取消</el-button>
        <el-button type="primary" :loading="dispatchLoading" @click="submitDispatch">确认派单</el-button>
      </template>
    </el-dialog>

    <!-- 追加执行记录 -->
    <el-dialog v-model="execVisible" title="追加执行记录" width="460px">
      <el-form label-width="90px">
        <el-form-item label="记录内容" required>
          <el-input v-model="execForm.content" type="textarea" :rows="3" maxlength="1000"
                    placeholder="如：已到达客户家，开始伤口换药" />
        </el-form-item>
        <el-form-item label="上门时间">
          <el-date-picker v-model="execForm.visitAt" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss"
                          placeholder="留空默认为当前时间" style="width: 100%" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="execVisible = false">取消</el-button>
        <el-button type="primary" :loading="execLoading" @click="submitExecution">保存</el-button>
      </template>
    </el-dialog>

    <!-- 异常上报 -->
    <el-dialog v-model="excReportVisible" title="上报异常" width="460px">
      <el-form label-width="90px">
        <el-form-item label="等级" required>
          <el-select v-model="excReportForm.level" style="width: 100%">
            <el-option label="低" value="low" />
            <el-option label="中" value="medium" />
            <el-option label="高" value="high" />
            <el-option label="严重" value="critical" />
          </el-select>
        </el-form-item>
        <el-form-item label="类别" required>
          <el-input v-model="excReportForm.category" maxlength="50" placeholder="如：客户不在家 / 生命体征异常" />
        </el-form-item>
        <el-form-item label="描述" required>
          <el-input v-model="excReportForm.description" type="textarea" :rows="3" maxlength="1000" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="excReportVisible = false">取消</el-button>
        <el-button type="primary" :loading="excReportLoading" @click="submitExceptionReport">上报</el-button>
      </template>
    </el-dialog>

    <!-- 新建/编辑服务 -->
    <el-dialog v-model="serviceDialogVisible" :title="serviceEditing ? '编辑服务' : '新建服务'" width="500px">
      <el-form label-width="90px">
        <el-form-item label="服务编码" required>
          <el-input v-model="serviceForm.serviceCode" maxlength="50" :disabled="serviceEditing !== null"
                    placeholder="如 HV-NURSE-01（创建后不可修改）" />
        </el-form-item>
        <el-form-item label="服务名称" required>
          <el-input v-model="serviceForm.serviceName" maxlength="100" />
        </el-form-item>
        <el-form-item label="类别" required>
          <el-radio-group v-model="serviceForm.category">
            <el-radio value="medical">医疗类</el-radio>
            <el-radio value="non_medical">非医疗类</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="所需资质">
          <el-select v-model="serviceForm.requiredQualType" clearable placeholder="不限" style="width: 100%">
            <el-option label="护士" value="nurse" />
            <el-option label="照护师" value="caregiver" />
            <el-option label="健康管理师" value="health_manager" />
            <el-option label="医生" value="doctor" />
            <el-option label="其他" value="other" />
          </el-select>
        </el-form-item>
        <el-form-item label="时长(分钟)" required>
          <el-input-number v-model="serviceForm.durationMinutes" :min="5" :max="1440" :step="15" style="width: 100%" />
        </el-form-item>
        <el-form-item label="价格" required>
          <el-input v-model="serviceForm.price" maxlength="20" placeholder="十进制字符串，如 280.00" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="serviceForm.description" type="textarea" :rows="3" maxlength="1000" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="serviceDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitService">保存</el-button>
      </template>
    </el-dialog>

    <!-- 服务审核 -->
    <el-dialog v-model="svcReviewVisible" :title="`审核服务：${svcReviewTarget?.serviceName ?? ''}`" width="440px">
      <el-form label-width="70px">
        <el-form-item label="结论" required>
          <el-radio-group v-model="svcReviewForm.approved">
            <el-radio :value="true">通过</el-radio>
            <el-radio :value="false">驳回</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="原因" :required="!svcReviewForm.approved">
          <el-input v-model="svcReviewForm.reason" type="textarea" :rows="3" maxlength="500"
                    :placeholder="svcReviewForm.approved ? '审核意见（选填）' : '驳回原因必填'" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="svcReviewVisible = false">取消</el-button>
        <el-button type="primary" @click="submitServiceReview">提交审核</el-button>
      </template>
    </el-dialog>

    <!-- 新建/编辑地址 -->
    <el-dialog v-model="addrDialogVisible" :title="addrEditing ? '编辑地址' : '新建地址'" width="480px">
      <el-form label-width="90px">
        <el-form-item label="详细地址" required>
          <el-input v-model="addrForm.address" maxlength="200" placeholder="省市区 + 小区门牌号" />
        </el-form-item>
        <el-form-item label="联系人" required>
          <el-input v-model="addrForm.contactName" maxlength="50" />
        </el-form-item>
        <el-form-item label="联系电话">
          <el-input v-model="addrForm.contactPhone" maxlength="20"
                    :placeholder="addrEditing ? '留空则不修改电话' : '联系人手机号（选填）'" />
        </el-form-item>
        <el-form-item label="默认地址">
          <el-switch v-model="addrForm.isDefault" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="addrForm.remark" type="textarea" :rows="2" maxlength="500" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="addrDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitAddress">保存</el-button>
      </template>
    </el-dialog>

    <!-- 新建资质 -->
    <el-dialog v-model="qualDialogVisible" title="登记人员资质" width="480px">
      <el-form label-width="90px">
        <el-form-item label="系统用户" required>
          <el-select v-model="qualForm.userId" filterable remote allow-create default-first-option
                     :remote-method="searchUsers" placeholder="搜索用户名/姓名，或直接输入用户 ID" style="width: 100%">
            <el-option v-for="u in userOptions" :key="u.id" :label="`${u.displayName}（${u.username}）`" :value="u.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="资质类型" required>
          <el-select v-model="qualForm.qualType" style="width: 100%">
            <el-option label="护士" value="nurse" />
            <el-option label="照护师" value="caregiver" />
            <el-option label="健康管理师" value="health_manager" />
            <el-option label="医生" value="doctor" />
            <el-option label="其他" value="other" />
          </el-select>
        </el-form-item>
        <el-form-item label="证书编号" required>
          <el-input v-model="qualForm.certNo" maxlength="64" />
        </el-form-item>
        <el-form-item label="发证日期" required>
          <el-date-picker v-model="qualForm.issuedAt" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
        <el-form-item label="有效期至">
          <el-date-picker v-model="qualForm.expiresAt" type="date" value-format="YYYY-MM-DD"
                          placeholder="长期有效可留空" style="width: 100%" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="qualDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitQual">保存</el-button>
      </template>
    </el-dialog>

    <!-- 订单详情抽屉 -->
    <el-drawer v-model="orderDetailVisible" title="上门订单详情" size="680px">
      <div v-if="orderDetail" class="drawer-body">
        <div class="detail-head">
          <h2>{{ orderDetail.orderNo }}</h2>
          <span :class="['badge', orderBadge(orderDetail.status)]">{{ orderStatusLabel(orderDetail.status) }}</span>
        </div>
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="客户">{{ orderDetail.customerName || '—' }}</el-descriptions-item>
          <el-descriptions-item label="服务">{{ orderDetail.serviceName }}（{{ orderDetail.serviceCode }}）</el-descriptions-item>
          <el-descriptions-item label="计划上门">
            {{ orderDetail.scheduledDate }} {{ formatTime(orderDetail.startTime) }}-{{ formatTime(orderDetail.endTime) }}
          </el-descriptions-item>
          <el-descriptions-item label="服务人员">{{ orderDetail.staffName || '—' }}</el-descriptions-item>
          <el-descriptions-item label="价格">{{ formatPrice(orderDetail.price) }}</el-descriptions-item>
          <el-descriptions-item label="要求资质">
            {{ orderDetail.requiredQualType ? (qualTypeLabels[orderDetail.requiredQualType] || orderDetail.requiredQualType) : '不限' }}
          </el-descriptions-item>
          <el-descriptions-item label="服务地址" :span="2">
            <template v-if="orderDetail.address">
              {{ orderDetail.address.address }}
              <span class="sub">（联系人：{{ orderDetail.address.contactName }}
                <template v-if="orderDetail.address.contactPhoneMasked"> · {{ orderDetail.address.contactPhoneMasked }}</template>）</span>
            </template>
            <template v-else>—</template>
          </el-descriptions-item>
          <el-descriptions-item label="备注" :span="2">{{ orderDetail.remark || '—' }}</el-descriptions-item>
        </el-descriptions>

        <h3 class="sub-title">订单进度</h3>
        <el-timeline>
          <el-timeline-item v-for="(item, idx) in orderTimeline" :key="idx"
                            :timestamp="item.time ? formatDateTime(item.time) : ''" :type="item.type">
            {{ item.label }}
            <span v-if="item.note" class="timeline-note">{{ item.note }}</span>
          </el-timeline-item>
        </el-timeline>

        <h3 class="sub-title">执行记录</h3>
        <el-table :data="orderDetail.executions" size="small" stripe>
          <el-table-column label="上门时间" width="170">
            <template #default="{ row }">{{ formatDateTime(row.visitAt) }}</template>
          </el-table-column>
          <el-table-column prop="content" label="记录内容" min-width="200" />
          <el-table-column prop="recordedBy" label="记录人" width="100">
            <template #default="{ row }">{{ row.recordedBy || '—' }}</template>
          </el-table-column>
        </el-table>
        <span v-if="!orderDetail.executions.length" class="cell-muted">暂无执行记录</span>

        <h3 class="sub-title">异常记录</h3>
        <el-table :data="orderDetail.exceptions" size="small" stripe>
          <el-table-column label="等级" width="80">
            <template #default="{ row }">
              <span :class="['badge', exceptionLevelBadge(row.level)]">{{ exceptionLevelLabels[row.level] || row.level }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="category" label="类别" width="120" />
          <el-table-column prop="description" label="描述" min-width="160" />
          <el-table-column label="状态" width="90">
            <template #default="{ row }">
              <span :class="['badge', exceptionStatusBadge(row.status)]">{{ exceptionStatusLabels[row.status] || row.status }}</span>
            </template>
          </el-table-column>
          <el-table-column v-if="canManage" label="操作" width="120" fixed="right">
            <template #default="{ row }">
              <el-button v-if="row.status === 'OPEN'" link type="warning"
                         @click="escalateException(row, refreshAfterDetailException())">升级</el-button>
              <el-button v-if="row.status === 'OPEN' || row.status === 'ESCALATED'" link type="primary"
                         @click="resolveException(row, refreshAfterDetailException())">解决</el-button>
            </template>
          </el-table-column>
        </el-table>
        <span v-if="!orderDetail.exceptions.length" class="cell-muted">暂无异常记录</span>
        <div v-if="unresolvedExceptionCount > 0" class="exc-warning">
          存在 {{ unresolvedExceptionCount }} 条未解决异常，完成服务可能被拒绝。
        </div>

        <div class="detail-actions">
          <el-button v-if="canReviewOrder" type="primary" @click="openOrderReview">审核</el-button>
          <el-button v-if="canDispatchOrder" type="primary" @click="openDispatch">派单</el-button>
          <el-button v-if="canAcceptOrder" type="primary" @click="acceptOrder">接单</el-button>
          <el-button v-if="canRejectOrder" type="danger" plain @click="rejectOrder">拒绝</el-button>
          <el-button v-if="canStartOrder" type="primary" @click="startOrder">开始服务</el-button>
          <el-button v-if="canRecordExecution" @click="openExecDialog">执行记录</el-button>
          <el-button v-if="canReportException" type="warning" plain @click="openExcReport">异常上报</el-button>
          <el-button v-if="canFinishOrder" type="success" @click="finishOrder">完成服务</el-button>
          <el-button v-if="canCancelOrder" type="warning" plain @click="cancelOrder">取消订单</el-button>
        </div>
      </div>
    </el-drawer>
  </div>
</template>

<style scoped>
.page-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-end;
  margin-bottom: 16px;
}

.page-head h1 {
  margin: 0 0 4px;
  font-size: 22px;
}

.page-head p {
  margin: 0;
  color: var(--el-text-color-secondary);
  font-size: 13px;
}

.head-metrics .metric {
  font-size: 13px;
  color: var(--el-text-color-regular);
}

.head-metrics .metric strong {
  margin-left: 6px;
  font-size: 16px;
}

.sub {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.detail-head {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
}

.detail-head h2 {
  margin: 0;
  font-size: 18px;
}

.sub-title {
  margin: 18px 0 8px;
  font-size: 14px;
  display: flex;
  align-items: center;
  gap: 8px;
}

.detail-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 18px;
}

.service-hint {
  margin-top: 4px;
}

.reject-reason {
  color: var(--crm-danger, #d4574e);
  font-size: 12px;
}

.dispatch-info {
  margin-top: 4px;
  padding: 10px 12px;
  border-radius: 6px;
  background: var(--el-fill-color-light);
  font-size: 13px;
}

.dispatch-label {
  color: var(--el-text-color-secondary);
  margin-right: 4px;
}

.timeline-note {
  display: block;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.exc-warning {
  margin-top: 8px;
  padding: 8px 12px;
  border-radius: 6px;
  background: var(--el-color-warning-light-9);
  color: var(--el-color-warning);
  font-size: 13px;
}
</style>
