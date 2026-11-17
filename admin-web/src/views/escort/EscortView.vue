<script setup lang="ts">
import { computed, nextTick, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { InputInstance } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import { fetchCustomers } from '@/api/customers'
import {
  acceptEscortOrder,
  addQualification,
  cancelEscortOrder,
  changeEscortStatus,
  createEscort,
  createEscortOrder,
  createSchedule,
  deleteSchedule,
  dispatchEscortOrder,
  fetchEscort,
  fetchEscortOrder,
  fetchEscortOrders,
  fetchEscortReviews,
  fetchEscorts,
  fetchSchedules,
  finishEscortOrder,
  rejectEscortOrder,
  reviewEscortOrder,
  revokeQualification,
  startEscortOrder,
  updateEscort,
  updateSchedule
} from '@/api/escort'
import type { CustomerSummary } from '@/types/customer'
import type {
  EscortDetail,
  EscortOrderDetail,
  EscortOrderSummary,
  EscortReview,
  EscortSchedule,
  EscortSummary
} from '@/types/escort'

const auth = useAuthStore()
const canReadEscort = computed(() => auth.has('crm:escort:read'))
const canWriteEscort = computed(() => auth.has('crm:escort:write'))
const canReadOrder = computed(() => auth.has('crm:escortorder:read'))
const canWriteOrder = computed(() => auth.has('crm:escortorder:write'))
const canManageOrder = computed(() => auth.has('crm:escortorder:manage'))

const activeTab = ref('orders')

const genderLabels: Record<string, string> = { unknown: '未知', male: '男', female: '女' }

const orderStatusLabels: Record<string, string> = {
  PENDING: '待派单',
  ASSIGNED: '已派单',
  ACCEPTED: '已接单',
  IN_PROGRESS: '服务中',
  COMPLETED: '已完成',
  CANCELLED: '已取消'
}

function formatDateTime(value?: string) {
  if (!value) return '—'
  return new Date(value).toLocaleString('zh-CN', { hour12: false })
}

function formatTime(value?: string) {
  return value ? value.slice(0, 5) : '—'
}

function orderStatusLabel(status: string) {
  return orderStatusLabels[status] || status
}

function orderBadge(status: string) {
  switch (status) {
    case 'PENDING': return 'badge-primary'
    case 'ASSIGNED': return 'badge-warning'
    case 'ACCEPTED': return 'badge-primary'
    case 'IN_PROGRESS': return 'badge-warning'
    case 'COMPLETED': return 'badge-success'
    case 'CANCELLED': return 'badge-neutral'
    default: return 'badge-neutral'
  }
}

// ------------------------------------------------------------------
// 陪诊师下拉选项（订单筛选 / 排班 / 派单共用）
// ------------------------------------------------------------------
const escortOptions = ref<EscortSummary[]>([])

async function loadEscortOptions() {
  const result = await fetchEscorts({ page: 1, pageSize: 100 })
  escortOptions.value = result.items
}

// ------------------------------------------------------------------
// 陪诊订单列表
// ------------------------------------------------------------------
const orderQuery = reactive({ keyword: '', status: '', escortId: '', dateRange: [] as string[], page: 1, pageSize: 20 })
const orderRows = ref<EscortOrderSummary[]>([])
const orderTotal = ref(0)
const orderLoading = ref(false)

async function loadOrders() {
  orderLoading.value = true
  try {
    const result = await fetchEscortOrders({
      keyword: orderQuery.keyword || undefined,
      status: orderQuery.status || undefined,
      escortId: orderQuery.escortId || undefined,
      dateFrom: orderQuery.dateRange?.[0] || undefined,
      dateTo: orderQuery.dateRange?.[1] || undefined,
      page: orderQuery.page,
      pageSize: orderQuery.pageSize
    })
    orderRows.value = result.items
    orderTotal.value = result.total
  } finally {
    orderLoading.value = false
  }
}

function resetOrderQuery() {
  orderQuery.keyword = ''
  orderQuery.status = ''
  orderQuery.escortId = ''
  orderQuery.dateRange = []
  orderQuery.page = 1
  loadOrders()
}

// ------------------------------------------------------------------
// 创建陪诊订单
// ------------------------------------------------------------------
const createVisible = ref(false)
const createLoading = ref(false)
const createForm = reactive({
  customerId: '',
  appointmentId: '',
  serviceDate: '',
  startTime: '',
  endTime: '',
  area: '',
  contactPhone: '',
  remark: ''
})
const customerOptions = ref<CustomerSummary[]>([])

async function searchCustomers(keyword: string) {
  try {
    const result = await fetchCustomers({ keyword: keyword || undefined, page: 1, pageSize: 10 })
    customerOptions.value = result.items
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '搜索客户失败')
  }
}

function openCreate() {
  createForm.customerId = ''
  createForm.appointmentId = ''
  createForm.serviceDate = ''
  createForm.startTime = ''
  createForm.endTime = ''
  createForm.area = ''
  createForm.contactPhone = ''
  createForm.remark = ''
  createVisible.value = true
  searchCustomers('')
}

async function submitCreate() {
  if (!createForm.customerId || !createForm.serviceDate || !createForm.startTime || !createForm.endTime || !createForm.area) {
    ElMessage.warning('请填写客户、服务日期、时间与区域')
    return
  }
  createLoading.value = true
  try {
    await createEscortOrder({
      customerId: createForm.customerId,
      appointmentId: createForm.appointmentId || undefined,
      serviceDate: createForm.serviceDate,
      startTime: createForm.startTime,
      endTime: createForm.endTime,
      area: createForm.area,
      contactPhone: createForm.contactPhone || undefined,
      remark: createForm.remark || undefined
    })
    ElMessage.success('陪诊订单创建成功')
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
const orderDetail = ref<EscortOrderDetail | null>(null)

async function openOrderDetail(row: EscortOrderSummary) {
  try {
    orderDetail.value = await fetchEscortOrder(row.id)
    orderDetailVisible.value = true
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '加载订单详情失败')
  }
}

async function refreshOrderDetail() {
  if (!orderDetail.value) return
  try {
    orderDetail.value = await fetchEscortOrder(orderDetail.value.id)
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '刷新订单详情失败')
  }
}

const canDispatch = computed(() =>
  orderDetail.value?.status === 'PENDING' && canWriteOrder.value)
const canAccept = computed(() =>
  orderDetail.value?.status === 'ASSIGNED' && canWriteOrder.value)
const canStart = computed(() =>
  orderDetail.value?.status === 'ACCEPTED' && canManageOrder.value)
const canFinish = computed(() =>
  orderDetail.value?.status === 'IN_PROGRESS' && canManageOrder.value)
const canCancel = computed(() =>
  orderDetail.value !== null &&
  ['PENDING', 'ASSIGNED', 'ACCEPTED'].includes(orderDetail.value.status) &&
  canManageOrder.value)
const canReview = computed(() =>
  orderDetail.value?.status === 'COMPLETED' && !orderDetail.value.review && canManageOrder.value)

const orderTimeline = computed(() => {
  const detail = orderDetail.value
  if (!detail) return []
  const items: { label: string; time?: string; note?: string; type: 'primary' | 'success' | 'warning' | 'danger' | 'info' }[] = [
    { label: '订单创建', time: detail.createdAt, type: 'primary' }
  ]
  if (detail.assignedAt) items.push({ label: `已派单给 ${detail.escortName || '—'}`, time: detail.assignedAt, type: 'primary' })
  if (detail.acceptedAt) items.push({ label: '陪诊师已接单', time: detail.acceptedAt, type: 'success' })
  if (detail.rejectReason) items.push({ label: '陪诊师拒单', note: detail.rejectReason, type: 'danger' })
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

async function acceptOrder() {
  if (!orderDetail.value) return
  await runOrderAction('接单', () => acceptEscortOrder(orderDetail.value!.id))
}

async function rejectOrder() {
  if (!orderDetail.value) return
  const orderId = orderDetail.value.id
  const input = await ElMessageBox.prompt('请填写拒绝原因', '拒绝接单', {
    inputValidator: v => (v && v.trim().length > 0) || '请填写拒绝原因'
  })
  await runOrderAction('拒单', () => rejectEscortOrder(orderId, input.value.trim()))
}

async function startOrder() {
  if (!orderDetail.value) return
  await runOrderAction('开始服务', () => startEscortOrder(orderDetail.value!.id))
}

async function finishOrder() {
  if (!orderDetail.value) return
  const orderId = orderDetail.value.id
  const input = await ElMessageBox.prompt('可填写服务完成备注（选填）', '完成服务', {
    inputValidator: v => true
  })
  await runOrderAction('完成服务', () => finishEscortOrder(orderId, input.value?.trim() || undefined))
}

async function cancelOrder() {
  if (!orderDetail.value) return
  const orderId = orderDetail.value.id
  const input = await ElMessageBox.prompt('请填写取消原因', '取消订单', {
    inputValidator: v => (v && v.trim().length > 0) || '请填写取消原因'
  })
  await runOrderAction('取消订单', () => cancelEscortOrder(orderId, input.value.trim()))
}

// ------------------------------------------------------------------
// 派单对话框（搜索陪诊师 + 资质/排班简要）
// ------------------------------------------------------------------
const dispatchVisible = ref(false)
const dispatchLoading = ref(false)
const dispatchForm = reactive({ escortId: '' })
const dispatchCandidates = ref<EscortSummary[]>([])
const dispatchEscortInfo = ref<EscortDetail | null>(null)
const dispatchDaySchedules = ref<EscortSchedule[]>([])

async function searchEscorts(keyword: string) {
  try {
    const result = await fetchEscorts({ keyword: keyword || undefined, status: 'active', page: 1, pageSize: 10 })
    dispatchCandidates.value = result.items
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '搜索陪诊师失败')
  }
}

function openDispatch() {
  dispatchForm.escortId = ''
  dispatchEscortInfo.value = null
  dispatchDaySchedules.value = []
  dispatchVisible.value = true
  searchEscorts('')
}

watch(() => dispatchForm.escortId, async (escortId) => {
  dispatchEscortInfo.value = null
  dispatchDaySchedules.value = []
  if (!escortId || !orderDetail.value) return
  try {
    const [detail, schedules] = await Promise.all([
      fetchEscort(escortId),
      fetchSchedules({
        escortId,
        dateFrom: orderDetail.value.serviceDate,
        dateTo: orderDetail.value.serviceDate,
        page: 1,
        pageSize: 20
      })
    ])
    dispatchEscortInfo.value = detail
    dispatchDaySchedules.value = schedules.items
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '加载陪诊师信息失败')
  }
})

async function submitDispatch() {
  if (!orderDetail.value || !dispatchForm.escortId) {
    ElMessage.warning('请选择陪诊师')
    return
  }
  const orderId = orderDetail.value.id
  dispatchLoading.value = true
  try {
    await dispatchEscortOrder(orderId, dispatchForm.escortId)
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
// 评价对话框
// ------------------------------------------------------------------
const reviewVisible = ref(false)
const reviewForm = reactive({ rating: 5, content: '' })

function openReview() {
  reviewForm.rating = 5
  reviewForm.content = ''
  reviewVisible.value = true
}

async function submitReview() {
  if (!orderDetail.value) return
  const orderId = orderDetail.value.id
  try {
    await reviewEscortOrder(orderId, { rating: reviewForm.rating, content: reviewForm.content || undefined })
    ElMessage.success('评价提交成功')
    reviewVisible.value = false
    await refreshOrderDetail()
    loadOrders()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '提交评价失败')
  }
}

// ------------------------------------------------------------------
// 陪诊师列表
// ------------------------------------------------------------------
const escortQuery = reactive({ keyword: '', status: '', page: 1, pageSize: 20 })
const escortRows = ref<EscortSummary[]>([])
const escortTotal = ref(0)
const escortLoading = ref(false)

async function loadEscorts() {
  escortLoading.value = true
  try {
    const result = await fetchEscorts({
      keyword: escortQuery.keyword || undefined,
      status: escortQuery.status || undefined,
      page: escortQuery.page,
      pageSize: escortQuery.pageSize
    })
    escortRows.value = result.items
    escortTotal.value = result.total
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '加载陪诊师失败')
  } finally {
    escortLoading.value = false
  }
}

function resetEscortQuery() {
  escortQuery.keyword = ''
  escortQuery.status = ''
  escortQuery.page = 1
  loadEscorts()
}

// 新建/编辑陪诊师（serviceAreas 用 el-tag + 输入框动态增删）
const escortDialogVisible = ref(false)
const escortEditing = ref<EscortSummary | null>(null)
const escortForm = reactive({ name: '', gender: 'unknown', phone: '', serviceAreas: [] as string[], remark: '' })
const areaInputRef = ref<InputInstance>()
const areaInputVisible = ref(false)
const areaInputValue = ref('')

function openEscortCreate() {
  escortEditing.value = null
  escortForm.name = ''
  escortForm.gender = 'unknown'
  escortForm.phone = ''
  escortForm.serviceAreas = []
  escortForm.remark = ''
  areaInputVisible.value = false
  escortDialogVisible.value = true
}

async function openEscortEdit(item: EscortSummary) {
  escortEditing.value = item
  escortForm.name = item.name
  escortForm.gender = item.gender
  escortForm.phone = ''
  escortForm.serviceAreas = [...item.serviceAreas]
  escortForm.remark = ''
  areaInputVisible.value = false
  escortDialogVisible.value = true
  try {
    const detail = await fetchEscort(item.id)
    if (escortEditing.value?.id === item.id) {
      escortForm.remark = detail.remark ?? ''
    }
  } catch {
    // 备注预填失败不阻塞编辑
  }
}

function showAreaInput() {
  areaInputVisible.value = true
  nextTick(() => areaInputRef.value?.focus())
}

function confirmAreaInput() {
  const value = areaInputValue.value.trim()
  if (value && !escortForm.serviceAreas.includes(value)) {
    escortForm.serviceAreas.push(value)
  }
  areaInputVisible.value = false
  areaInputValue.value = ''
}

function removeArea(area: string) {
  escortForm.serviceAreas = escortForm.serviceAreas.filter(a => a !== area)
}

async function submitEscort() {
  if (!escortForm.name) {
    ElMessage.warning('请填写陪诊师姓名')
    return
  }
  try {
    if (escortEditing.value) {
      await updateEscort(escortEditing.value.id, {
        name: escortForm.name,
        gender: escortForm.gender,
        phone: escortForm.phone || undefined,
        serviceAreas: escortForm.serviceAreas,
        remark: escortForm.remark || undefined
      })
      ElMessage.success('陪诊师信息已更新')
    } else {
      await createEscort({
        name: escortForm.name,
        gender: escortForm.gender,
        phone: escortForm.phone || undefined,
        serviceAreas: escortForm.serviceAreas,
        remark: escortForm.remark || undefined
      })
      ElMessage.success('陪诊师已创建')
    }
    escortDialogVisible.value = false
    await Promise.all([loadEscorts(), loadEscortOptions()])
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '保存陪诊师失败')
  }
}

async function toggleEscort(item: EscortSummary) {
  const next = item.status === 'active' ? 'disabled' : 'active'
  await ElMessageBox.confirm(`确认将「${item.name}」设为${next === 'active' ? '启用' : '停用'}？`, '提示', { type: 'warning' })
  try {
    await changeEscortStatus(item.id, next)
    ElMessage.success('状态已更新')
    await Promise.all([loadEscorts(), loadEscortOptions()])
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '状态变更失败')
  }
}

// 陪诊师详情抽屉（资质子表）
const escortDetailVisible = ref(false)
const escortDetail = ref<EscortDetail | null>(null)

async function openEscortDetail(row: EscortSummary) {
  try {
    escortDetail.value = await fetchEscort(row.id)
    escortDetailVisible.value = true
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '加载陪诊师详情失败')
  }
}

async function refreshEscortDetail() {
  if (!escortDetail.value) return
  try {
    escortDetail.value = await fetchEscort(escortDetail.value.id)
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '刷新陪诊师详情失败')
  }
}

const qualDialogVisible = ref(false)
const qualForm = reactive({ qualType: '', certNo: '', issuedAt: '', expiresAt: '' })

function openQualCreate() {
  qualForm.qualType = ''
  qualForm.certNo = ''
  qualForm.issuedAt = ''
  qualForm.expiresAt = ''
  qualDialogVisible.value = true
}

async function submitQualification() {
  if (!escortDetail.value) return
  if (!qualForm.qualType || !qualForm.certNo || !qualForm.issuedAt) {
    ElMessage.warning('请填写资质类型、证书编号与发证日期')
    return
  }
  try {
    await addQualification(escortDetail.value.id, {
      qualType: qualForm.qualType,
      certNo: qualForm.certNo,
      issuedAt: qualForm.issuedAt,
      expiresAt: qualForm.expiresAt || undefined
    })
    ElMessage.success('资质已登记')
    qualDialogVisible.value = false
    await refreshEscortDetail()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '登记资质失败')
  }
}

async function revokeQual(qualificationId: string) {
  if (!escortDetail.value) return
  await ElMessageBox.confirm('确认停用该资质？', '提示', { type: 'warning' })
  try {
    await revokeQualification(escortDetail.value.id, qualificationId)
    ElMessage.success('资质已停用')
    await refreshEscortDetail()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '停用资质失败')
  }
}

// ------------------------------------------------------------------
// 排班
// ------------------------------------------------------------------
const scheduleQuery = reactive({ escortId: '', dateRange: [] as string[], page: 1, pageSize: 20 })
const scheduleRows = ref<EscortSchedule[]>([])
const scheduleTotal = ref(0)
const scheduleLoading = ref(false)

async function loadSchedules() {
  scheduleLoading.value = true
  try {
    const result = await fetchSchedules({
      escortId: scheduleQuery.escortId || undefined,
      dateFrom: scheduleQuery.dateRange?.[0] || undefined,
      dateTo: scheduleQuery.dateRange?.[1] || undefined,
      page: scheduleQuery.page,
      pageSize: scheduleQuery.pageSize
    })
    scheduleRows.value = result.items
    scheduleTotal.value = result.total
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '加载排班失败')
  } finally {
    scheduleLoading.value = false
  }
}

const scheduleDialogVisible = ref(false)
const scheduleEditing = ref<EscortSchedule | null>(null)
const scheduleForm = reactive({ escortId: '', workDate: '', startTime: '', endTime: '' })

function openScheduleCreate() {
  scheduleEditing.value = null
  scheduleForm.escortId = scheduleQuery.escortId || escortOptions.value[0]?.id || ''
  scheduleForm.workDate = ''
  scheduleForm.startTime = ''
  scheduleForm.endTime = ''
  scheduleDialogVisible.value = true
}

function openScheduleEdit(item: EscortSchedule) {
  scheduleEditing.value = item
  scheduleForm.escortId = item.escortId
  scheduleForm.workDate = item.workDate
  scheduleForm.startTime = item.startTime
  scheduleForm.endTime = item.endTime
  scheduleDialogVisible.value = true
}

async function submitSchedule() {
  if (!scheduleForm.escortId || !scheduleForm.workDate || !scheduleForm.startTime || !scheduleForm.endTime) {
    ElMessage.warning('请完整填写排班信息')
    return
  }
  try {
    if (scheduleEditing.value) {
      await updateSchedule(scheduleEditing.value.id, {
        workDate: scheduleForm.workDate,
        startTime: scheduleForm.startTime,
        endTime: scheduleForm.endTime
      })
      ElMessage.success('排班已更新')
    } else {
      await createSchedule({
        escortId: scheduleForm.escortId,
        workDate: scheduleForm.workDate,
        startTime: scheduleForm.startTime,
        endTime: scheduleForm.endTime
      })
      ElMessage.success('排班已创建')
    }
    scheduleDialogVisible.value = false
    loadSchedules()
  } catch (error) {
    // 排班重叠返回 409，原样展示后端 message
    ElMessage.error(error instanceof Error ? error.message : '保存排班失败')
  }
}

async function removeSchedule(item: EscortSchedule) {
  await ElMessageBox.confirm(`确认删除 ${item.escortName} ${item.workDate} 的排班？`, '提示', { type: 'warning' })
  try {
    await deleteSchedule(item.id)
    ElMessage.success('排班已删除')
    loadSchedules()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '删除排班失败')
  }
}

// ------------------------------------------------------------------
// 评价列表
// ------------------------------------------------------------------
const reviewQuery = reactive({ escortId: '', page: 1, pageSize: 20 })
const reviewRows = ref<EscortReview[]>([])
const reviewTotal = ref(0)
const reviewLoading = ref(false)

async function loadReviews() {
  reviewLoading.value = true
  try {
    const result = await fetchEscortReviews({
      escortId: reviewQuery.escortId || undefined,
      page: reviewQuery.page,
      pageSize: reviewQuery.pageSize
    })
    reviewRows.value = result.items
    reviewTotal.value = result.total
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '加载评价失败')
  } finally {
    reviewLoading.value = false
  }
}

watch(activeTab, (tab) => {
  if (tab === 'escorts' && escortRows.value.length === 0) loadEscorts()
  if (tab === 'schedules' && scheduleRows.value.length === 0) loadSchedules()
  if (tab === 'reviews' && reviewRows.value.length === 0) loadReviews()
})

onMounted(() => {
  if (canReadEscort.value) {
    loadEscortOptions().catch(() => undefined)
  }
  if (canReadOrder.value) {
    loadOrders()
  }
})
</script>

<template>
  <div class="page">
    <header class="page-head">
      <div>
        <h1>陪诊服务</h1>
        <p>陪诊订单派单与履约、陪诊师档案与资质、排班管理和服务评价</p>
      </div>
      <div class="head-metrics">
        <span class="metric">订单总数 <strong>{{ orderTotal }}</strong></span>
      </div>
    </header>

    <el-tabs v-model="activeTab" class="tab-card">
      <!-- ============ 陪诊订单 ============ -->
      <el-tab-pane label="陪诊订单" name="orders">
        <div class="card">
          <div class="toolbar">
            <el-input v-model="orderQuery.keyword" placeholder="订单号/客户姓名" clearable style="width: 180px"
                      @keyup.enter="orderQuery.page = 1; loadOrders()" />
            <el-select v-model="orderQuery.status" placeholder="状态" clearable style="width: 130px">
              <el-option label="待派单" value="PENDING" />
              <el-option label="已派单" value="ASSIGNED" />
              <el-option label="已接单" value="ACCEPTED" />
              <el-option label="服务中" value="IN_PROGRESS" />
              <el-option label="已完成" value="COMPLETED" />
              <el-option label="已取消" value="CANCELLED" />
            </el-select>
            <el-select v-model="orderQuery.escortId" placeholder="陪诊师" clearable filterable style="width: 150px">
              <el-option v-for="e in escortOptions" :key="e.id" :label="e.name" :value="e.id" />
            </el-select>
            <el-date-picker v-model="orderQuery.dateRange" type="daterange" value-format="YYYY-MM-DD"
                            start-placeholder="服务开始" end-placeholder="服务结束" style="width: 240px" />
            <el-button type="primary" @click="orderQuery.page = 1; loadOrders()">查询</el-button>
            <el-button @click="resetOrderQuery">重置</el-button>
            <span class="spacer" />
            <el-button v-if="canWriteOrder" type="primary" @click="openCreate">创建订单</el-button>
          </div>

          <el-table v-loading="orderLoading" :data="orderRows" stripe>
            <el-table-column prop="orderNo" label="订单号" width="130">
              <template #default="{ row }"><span class="cell-mono">{{ row.orderNo }}</span></template>
            </el-table-column>
            <el-table-column label="客户" min-width="120">
              <template #default="{ row }">{{ row.customerName || '—' }}</template>
            </el-table-column>
            <el-table-column label="陪诊师" width="110">
              <template #default="{ row }">{{ row.escortName || '—' }}</template>
            </el-table-column>
            <el-table-column label="服务时段" min-width="170">
              <template #default="{ row }">
                {{ row.serviceDate }} {{ formatTime(row.startTime) }}-{{ formatTime(row.endTime) }}
              </template>
            </el-table-column>
            <el-table-column prop="area" label="服务区域" min-width="120" />
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

      <!-- ============ 陪诊师 ============ -->
      <el-tab-pane label="陪诊师" name="escorts">
        <div class="card">
          <div class="toolbar">
            <el-input v-model="escortQuery.keyword" placeholder="姓名/编号/手机号" clearable style="width: 180px"
                      @keyup.enter="escortQuery.page = 1; loadEscorts()" />
            <el-select v-model="escortQuery.status" placeholder="状态" clearable style="width: 120px">
              <el-option label="启用" value="active" />
              <el-option label="停用" value="disabled" />
            </el-select>
            <el-button type="primary" @click="escortQuery.page = 1; loadEscorts()">查询</el-button>
            <el-button @click="resetEscortQuery">重置</el-button>
            <span class="spacer" />
            <el-button v-if="canWriteEscort" type="primary" @click="openEscortCreate">新建陪诊师</el-button>
          </div>

          <el-table v-loading="escortLoading" :data="escortRows" stripe>
            <el-table-column prop="escortNo" label="编号" width="120">
              <template #default="{ row }"><span class="cell-mono">{{ row.escortNo }}</span></template>
            </el-table-column>
            <el-table-column prop="name" label="姓名" width="110" />
            <el-table-column label="性别" width="70">
              <template #default="{ row }">{{ genderLabels[row.gender] || row.gender }}</template>
            </el-table-column>
            <el-table-column label="手机号" width="130">
              <template #default="{ row }">{{ row.phoneMasked || '—' }}</template>
            </el-table-column>
            <el-table-column label="服务区域" min-width="180">
              <template #default="{ row }">
                <el-tag v-for="area in row.serviceAreas" :key="area" size="small" class="area-tag">{{ area }}</el-tag>
                <span v-if="!row.serviceAreas.length" class="cell-muted">—</span>
              </template>
            </el-table-column>
            <el-table-column label="评分" width="110">
              <template #default="{ row }">
                <span v-if="row.avgRating != null">{{ Number(row.avgRating).toFixed(1) }} 分</span>
                <span v-else class="cell-muted">暂无</span>
                <span class="sub">（{{ row.reviewCount }}）</span>
              </template>
            </el-table-column>
            <el-table-column label="状态" width="90">
              <template #default="{ row }">
                <span :class="['badge', row.status === 'active' ? 'badge-success' : 'badge-neutral']">
                  {{ row.status === 'active' ? '启用' : '停用' }}
                </span>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="180" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" @click="openEscortDetail(row)">详情</el-button>
                <el-button v-if="canWriteEscort" link type="primary" @click="openEscortEdit(row)">编辑</el-button>
                <el-button v-if="canWriteEscort" link :type="row.status === 'active' ? 'danger' : 'primary'"
                           @click="toggleEscort(row)">
                  {{ row.status === 'active' ? '停用' : '启用' }}
                </el-button>
              </template>
            </el-table-column>
          </el-table>

          <el-pagination v-model:current-page="escortQuery.page" v-model:page-size="escortQuery.pageSize"
                         :total="escortTotal" :page-sizes="[10, 20, 50]"
                         layout="total, sizes, prev, pager, next" class="table-pagination"
                         @change="loadEscorts" />
        </div>
      </el-tab-pane>

      <!-- ============ 排班 ============ -->
      <el-tab-pane label="排班" name="schedules">
        <div class="card">
          <div class="toolbar">
            <el-select v-model="scheduleQuery.escortId" placeholder="陪诊师" clearable filterable style="width: 160px">
              <el-option v-for="e in escortOptions" :key="e.id" :label="e.name" :value="e.id" />
            </el-select>
            <el-date-picker v-model="scheduleQuery.dateRange" type="daterange" value-format="YYYY-MM-DD"
                            start-placeholder="开始日期" end-placeholder="结束日期" style="width: 240px" />
            <el-button type="primary" @click="scheduleQuery.page = 1; loadSchedules()">查询</el-button>
            <span class="spacer" />
            <el-button v-if="canWriteEscort" type="primary" @click="openScheduleCreate">新建排班</el-button>
          </div>

          <el-table v-loading="scheduleLoading" :data="scheduleRows" stripe>
            <el-table-column prop="escortName" label="陪诊师" width="120" />
            <el-table-column prop="workDate" label="排班日期" width="120" />
            <el-table-column label="班次时间" width="150">
              <template #default="{ row }">{{ formatTime(row.startTime) }} - {{ formatTime(row.endTime) }}</template>
            </el-table-column>
            <el-table-column v-if="canWriteEscort" label="操作" width="130" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" @click="openScheduleEdit(row)">编辑</el-button>
                <el-button link type="danger" @click="removeSchedule(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>

          <el-pagination v-model:current-page="scheduleQuery.page" v-model:page-size="scheduleQuery.pageSize"
                         :total="scheduleTotal" :page-sizes="[10, 20, 50]"
                         layout="total, sizes, prev, pager, next" class="table-pagination"
                         @change="loadSchedules" />
        </div>
      </el-tab-pane>

      <!-- ============ 评价 ============ -->
      <el-tab-pane label="评价" name="reviews">
        <div class="card">
          <div class="toolbar">
            <el-select v-model="reviewQuery.escortId" placeholder="陪诊师" clearable filterable style="width: 160px">
              <el-option v-for="e in escortOptions" :key="e.id" :label="e.name" :value="e.id" />
            </el-select>
            <el-button type="primary" @click="reviewQuery.page = 1; loadReviews()">查询</el-button>
          </div>

          <el-table v-loading="reviewLoading" :data="reviewRows" stripe>
            <el-table-column prop="orderNo" label="订单号" width="140">
              <template #default="{ row }"><span class="cell-mono">{{ row.orderNo }}</span></template>
            </el-table-column>
            <el-table-column prop="escortName" label="陪诊师" width="120" />
            <el-table-column label="星级" width="180">
              <template #default="{ row }"><el-rate :model-value="row.rating" disabled /></template>
            </el-table-column>
            <el-table-column prop="content" label="评价内容" min-width="200">
              <template #default="{ row }">{{ row.content || '—' }}</template>
            </el-table-column>
            <el-table-column prop="createdBy" label="评价人" width="110">
              <template #default="{ row }">{{ row.createdBy || '—' }}</template>
            </el-table-column>
            <el-table-column label="评价时间" width="170">
              <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
            </el-table-column>
          </el-table>

          <el-pagination v-model:current-page="reviewQuery.page" v-model:page-size="reviewQuery.pageSize"
                         :total="reviewTotal" :page-sizes="[10, 20, 50]"
                         layout="total, sizes, prev, pager, next" class="table-pagination"
                         @change="loadReviews" />
        </div>
      </el-tab-pane>
    </el-tabs>

    <!-- 创建陪诊订单 -->
    <el-dialog v-model="createVisible" title="创建陪诊订单" width="520px">
      <el-form label-width="90px">
        <el-form-item label="客户" required>
          <el-select v-model="createForm.customerId" filterable remote :remote-method="searchCustomers"
                     placeholder="输入姓名/客户号/手机号搜索" style="width: 100%">
            <el-option v-for="c in customerOptions" :key="c.id"
                       :label="`${c.name}（${c.customerNo}）`" :value="c.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="关联预约">
          <el-input v-model="createForm.appointmentId" maxlength="64" placeholder="预约 ID（选填）" />
        </el-form-item>
        <el-form-item label="服务日期" required>
          <el-date-picker v-model="createForm.serviceDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
        <el-form-item label="开始时间" required>
          <el-time-select v-model="createForm.startTime" start="07:00" step="00:30" end="21:00" style="width: 100%" />
        </el-form-item>
        <el-form-item label="结束时间" required>
          <el-time-select v-model="createForm.endTime" :start="createForm.startTime || '07:30'" step="00:30" end="22:00"
                          style="width: 100%" />
        </el-form-item>
        <el-form-item label="服务区域" required>
          <el-input v-model="createForm.area" maxlength="100" placeholder="如：门诊楼 / 住院部 A 区" />
        </el-form-item>
        <el-form-item label="联系电话">
          <el-input v-model="createForm.contactPhone" maxlength="20" placeholder="服务联系人手机号（选填）" />
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

    <!-- 派单 -->
    <el-dialog v-model="dispatchVisible" title="派单：选择陪诊师" width="560px">
      <el-form label-width="90px">
        <el-form-item label="陪诊师" required>
          <el-select v-model="dispatchForm.escortId" filterable remote :remote-method="searchEscorts"
                     placeholder="输入姓名/编号搜索启用中的陪诊师" style="width: 100%">
            <el-option v-for="e in dispatchCandidates" :key="e.id"
                       :label="`${e.name}（${e.escortNo}）`" :value="e.id" />
          </el-select>
        </el-form-item>
      </el-form>
      <div v-if="dispatchEscortInfo" class="dispatch-info">
        <div class="dispatch-block">
          <span class="dispatch-label">资质：</span>
          <template v-if="dispatchEscortInfo.qualifications.length">
            <el-tag v-for="q in dispatchEscortInfo.qualifications" :key="q.id" size="small"
                    :type="q.status === 'valid' && !q.expired ? 'success' : 'info'" class="area-tag">
              {{ q.qualType }}{{ q.expired ? '（已过期）' : '' }}
            </el-tag>
          </template>
          <span v-else class="cell-muted">未登记资质</span>
        </div>
        <div class="dispatch-block">
          <span class="dispatch-label">当日排班（{{ orderDetail?.serviceDate }}）：</span>
          <template v-if="dispatchDaySchedules.length">
            <el-tag v-for="s in dispatchDaySchedules" :key="s.id" size="small" class="area-tag">
              {{ formatTime(s.startTime) }}-{{ formatTime(s.endTime) }}
            </el-tag>
          </template>
          <span v-else class="cell-muted">当日暂无排班</span>
        </div>
      </div>
      <template #footer>
        <el-button @click="dispatchVisible = false">取消</el-button>
        <el-button type="primary" :loading="dispatchLoading" @click="submitDispatch">确认派单</el-button>
      </template>
    </el-dialog>

    <!-- 评价 -->
    <el-dialog v-model="reviewVisible" title="服务评价" width="440px">
      <el-form label-width="70px">
        <el-form-item label="星级" required>
          <el-rate v-model="reviewForm.rating" />
        </el-form-item>
        <el-form-item label="内容">
          <el-input v-model="reviewForm.content" type="textarea" :rows="3" maxlength="500" placeholder="评价内容（选填）" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="reviewVisible = false">取消</el-button>
        <el-button type="primary" @click="submitReview">提交评价</el-button>
      </template>
    </el-dialog>

    <!-- 新建/编辑陪诊师 -->
    <el-dialog v-model="escortDialogVisible" :title="escortEditing ? '编辑陪诊师' : '新建陪诊师'" width="480px">
      <el-form label-width="90px">
        <el-form-item label="姓名" required>
          <el-input v-model="escortForm.name" maxlength="50" />
        </el-form-item>
        <el-form-item label="性别">
          <el-radio-group v-model="escortForm.gender">
            <el-radio value="unknown">未知</el-radio>
            <el-radio value="male">男</el-radio>
            <el-radio value="female">女</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="手机号">
          <el-input v-model="escortForm.phone" maxlength="20"
                    :placeholder="escortEditing ? '留空则不修改手机号' : '陪诊师手机号（选填）'" />
        </el-form-item>
        <el-form-item label="服务区域">
          <div class="area-editor">
            <el-tag v-for="area in escortForm.serviceAreas" :key="area" closable size="default"
                    @close="removeArea(area)">{{ area }}</el-tag>
            <el-input v-if="areaInputVisible" ref="areaInputRef" v-model="areaInputValue" size="small"
                      style="width: 120px" @keyup.enter="confirmAreaInput" @blur="confirmAreaInput" />
            <el-button v-else size="small" @click="showAreaInput">+ 添加区域</el-button>
          </div>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="escortForm.remark" type="textarea" :rows="2" maxlength="500" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="escortDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitEscort">保存</el-button>
      </template>
    </el-dialog>

    <!-- 新增资质 -->
    <el-dialog v-model="qualDialogVisible" title="登记资质" width="440px">
      <el-form label-width="90px">
        <el-form-item label="资质类型" required>
          <el-input v-model="qualForm.qualType" maxlength="50" placeholder="如：陪诊师证 / 护理证" />
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
        <el-button type="primary" @click="submitQualification">保存</el-button>
      </template>
    </el-dialog>

    <!-- 排班编辑 -->
    <el-dialog v-model="scheduleDialogVisible" :title="scheduleEditing ? '编辑排班' : '新建排班'" width="440px">
      <el-form label-width="90px">
        <el-form-item label="陪诊师" required>
          <el-select v-model="scheduleForm.escortId" :disabled="scheduleEditing !== null" filterable
                     placeholder="选择陪诊师" style="width: 100%">
            <el-option v-for="e in escortOptions" :key="e.id" :label="`${e.name}（${e.escortNo}）`" :value="e.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="排班日期" required>
          <el-date-picker v-model="scheduleForm.workDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
        <el-form-item label="开始时间" required>
          <el-time-select v-model="scheduleForm.startTime" start="07:00" step="00:30" end="21:00" style="width: 100%" />
        </el-form-item>
        <el-form-item label="结束时间" required>
          <el-time-select v-model="scheduleForm.endTime" :start="scheduleForm.startTime || '07:30'" step="00:30"
                          end="22:00" style="width: 100%" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="scheduleDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitSchedule">保存</el-button>
      </template>
    </el-dialog>

    <!-- 订单详情抽屉 -->
    <el-drawer v-model="orderDetailVisible" title="陪诊订单详情" size="640px">
      <div v-if="orderDetail" class="drawer-body">
        <div class="detail-head">
          <h2>{{ orderDetail.orderNo }}</h2>
          <span :class="['badge', orderBadge(orderDetail.status)]">{{ orderStatusLabel(orderDetail.status) }}</span>
        </div>
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="客户">{{ orderDetail.customerName || '—' }}</el-descriptions-item>
          <el-descriptions-item label="陪诊师">{{ orderDetail.escortName || '—' }}</el-descriptions-item>
          <el-descriptions-item label="服务日期">{{ orderDetail.serviceDate }}</el-descriptions-item>
          <el-descriptions-item label="服务时间">
            {{ formatTime(orderDetail.startTime) }} - {{ formatTime(orderDetail.endTime) }}
          </el-descriptions-item>
          <el-descriptions-item label="服务区域">{{ orderDetail.area }}</el-descriptions-item>
          <el-descriptions-item label="联系电话">{{ orderDetail.contactPhoneMasked || '—' }}</el-descriptions-item>
          <el-descriptions-item label="关联预约">{{ orderDetail.appointmentId || '—' }}</el-descriptions-item>
          <el-descriptions-item label="服务时长">
            {{ orderDetail.durationMinutes != null ? `${orderDetail.durationMinutes} 分钟` : '—' }}
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

        <template v-if="orderDetail.review">
          <h3 class="sub-title">服务评价</h3>
          <div class="review-block">
            <el-rate :model-value="orderDetail.review.rating" disabled />
            <span class="sub">{{ orderDetail.review.createdBy || '—' }} · {{ formatDateTime(orderDetail.review.createdAt) }}</span>
            <p class="review-content">{{ orderDetail.review.content || '（未填写评价内容）' }}</p>
          </div>
        </template>

        <div class="detail-actions">
          <el-button v-if="canDispatch" type="primary" @click="openDispatch">派单</el-button>
          <el-button v-if="canAccept" type="primary" @click="acceptOrder">接单</el-button>
          <el-button v-if="canAccept" type="danger" plain @click="rejectOrder">拒绝</el-button>
          <el-button v-if="canStart" type="primary" @click="startOrder">开始服务</el-button>
          <el-button v-if="canFinish" type="success" @click="finishOrder">完成服务</el-button>
          <el-button v-if="canCancel" type="warning" plain @click="cancelOrder">取消订单</el-button>
          <el-button v-if="canReview" @click="openReview">评价服务</el-button>
        </div>
      </div>
    </el-drawer>

    <!-- 陪诊师详情抽屉 -->
    <el-drawer v-model="escortDetailVisible" title="陪诊师详情" size="640px">
      <div v-if="escortDetail" class="drawer-body">
        <div class="detail-head">
          <h2>{{ escortDetail.name }}</h2>
          <span :class="['badge', escortDetail.status === 'active' ? 'badge-success' : 'badge-neutral']">
            {{ escortDetail.status === 'active' ? '启用' : '停用' }}
          </span>
        </div>
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="编号">{{ escortDetail.escortNo }}</el-descriptions-item>
          <el-descriptions-item label="性别">{{ genderLabels[escortDetail.gender] || escortDetail.gender }}</el-descriptions-item>
          <el-descriptions-item label="手机号">{{ escortDetail.phoneMasked || '—' }}</el-descriptions-item>
          <el-descriptions-item label="评分">
            <template v-if="escortDetail.avgRating != null">
              {{ Number(escortDetail.avgRating).toFixed(1) }} 分（{{ escortDetail.reviewCount }} 条评价）
            </template>
            <template v-else>暂无评价</template>
          </el-descriptions-item>
          <el-descriptions-item label="服务区域" :span="2">
            <el-tag v-for="area in escortDetail.serviceAreas" :key="area" size="small" class="area-tag">{{ area }}</el-tag>
            <span v-if="!escortDetail.serviceAreas.length" class="cell-muted">—</span>
          </el-descriptions-item>
          <el-descriptions-item label="创建时间">{{ formatDateTime(escortDetail.createdAt) }}</el-descriptions-item>
          <el-descriptions-item label="更新时间">{{ formatDateTime(escortDetail.updatedAt) }}</el-descriptions-item>
          <el-descriptions-item label="备注" :span="2">{{ escortDetail.remark || '—' }}</el-descriptions-item>
        </el-descriptions>

        <h3 class="sub-title">
          资质证照
          <el-button v-if="canWriteEscort" link type="primary" @click="openQualCreate">+ 登记资质</el-button>
        </h3>
        <el-table :data="escortDetail.qualifications" size="small" stripe>
          <el-table-column prop="qualType" label="类型" width="110" />
          <el-table-column prop="certNo" label="证书编号" min-width="130">
            <template #default="{ row }"><span class="cell-mono">{{ row.certNo }}</span></template>
          </el-table-column>
          <el-table-column prop="issuedAt" label="发证日期" width="100" />
          <el-table-column prop="expiresAt" label="有效期至" width="100">
            <template #default="{ row }">{{ row.expiresAt || '长期' }}</template>
          </el-table-column>
          <el-table-column label="状态" width="100">
            <template #default="{ row }">
              <span :class="['badge', row.expired ? 'badge-danger' : 'badge-success']">
                {{ row.expired ? '已过期' : row.status }}
              </span>
            </template>
          </el-table-column>
          <el-table-column v-if="canWriteEscort" label="操作" width="70" fixed="right">
            <template #default="{ row }">
              <el-button v-if="row.status !== 'revoked'" link type="danger" @click="revokeQual(row.id)">停用</el-button>
            </template>
          </el-table-column>
        </el-table>
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

.area-tag {
  margin-right: 6px;
}

.area-editor {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
}

.dispatch-info {
  margin-top: 4px;
  padding: 10px 12px;
  border-radius: 6px;
  background: var(--el-fill-color-light);
  font-size: 13px;
}

.dispatch-block + .dispatch-block {
  margin-top: 8px;
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

.review-block {
  padding: 10px 12px;
  border-radius: 6px;
  background: var(--el-fill-color-light);
}

.review-content {
  margin: 6px 0 0;
  font-size: 13px;
  white-space: pre-wrap;
}
</style>
