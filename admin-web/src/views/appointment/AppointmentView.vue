<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import { fetchCustomers } from '@/api/customers'
import {
  cancelAppointment,
  changeAppointmentStatus,
  changeAppointmentTypeStatus,
  changeSlotStatus,
  createAppointment,
  createAppointmentType,
  createSlot,
  dispatchReminders,
  fetchAppointment,
  fetchAppointmentTypes,
  fetchAppointments,
  fetchNoShows,
  fetchSlots,
  rescheduleAppointment,
  retryReminder,
  updateAppointmentType,
  updateSlot
} from '@/api/appointment'
import type { CustomerSummary } from '@/types/customer'
import type {
  AppointmentDetail,
  AppointmentReminder,
  AppointmentSummary,
  AppointmentType,
  NoShowRecord,
  ServiceSlot
} from '@/types/appointment'

const auth = useAuthStore()
const canReadAppointment = computed(() => auth.has('crm:appointment:read'))
const canWriteAppointment = computed(() => auth.has('crm:appointment:write'))
const canManageAppointment = computed(() => auth.has('crm:appointment:manage'))
const canWriteSlot = computed(() => auth.has('crm:appttype:write'))

const activeTab = ref('appointments')

function formatDateTime(value?: string) {
  if (!value) return '—'
  return new Date(value).toLocaleString('zh-CN', { hour12: false })
}

function formatTime(value?: string) {
  return value ? value.slice(0, 5) : '—'
}

function slotLabel(slot: ServiceSlot) {
  return `${slot.slotDate} ${formatTime(slot.startTime)}-${formatTime(slot.endTime)}（余 ${slot.remaining}）`
}

// ------------------------------------------------------------------
// 预约类型与时段选项
// ------------------------------------------------------------------
const types = ref<AppointmentType[]>([])
const openSlots = ref<ServiceSlot[]>([])

async function loadTypes() {
  types.value = await fetchAppointmentTypes(canWriteSlot.value)
}

async function loadOpenSlots(typeCode: string) {
  if (!typeCode) {
    openSlots.value = []
    return
  }
  const today = new Date().toISOString().slice(0, 10)
  const result = await fetchSlots({ typeCode, dateFrom: today, status: 'OPEN', page: 1, pageSize: 100 })
  openSlots.value = result.items
}

// ------------------------------------------------------------------
// 预约列表
// ------------------------------------------------------------------
const apptQuery = reactive({ keyword: '', status: '', typeCode: '', dateRange: [] as string[], page: 1, pageSize: 20 })
const apptRows = ref<AppointmentSummary[]>([])
const apptTotal = ref(0)
const apptLoading = ref(false)

async function loadAppointments() {
  apptLoading.value = true
  try {
    const result = await fetchAppointments({
      keyword: apptQuery.keyword || undefined,
      status: apptQuery.status || undefined,
      typeCode: apptQuery.typeCode || undefined,
      dateFrom: apptQuery.dateRange?.[0] || undefined,
      dateTo: apptQuery.dateRange?.[1] || undefined,
      page: apptQuery.page,
      pageSize: apptQuery.pageSize
    })
    apptRows.value = result.items
    apptTotal.value = result.total
  } finally {
    apptLoading.value = false
  }
}

function resetApptQuery() {
  apptQuery.keyword = ''
  apptQuery.status = ''
  apptQuery.typeCode = ''
  apptQuery.dateRange = []
  apptQuery.page = 1
  loadAppointments()
}

function appointmentBadge(status: string) {
  switch (status) {
    case 'BOOKED': return 'badge-primary'
    case 'CONFIRMED': return 'badge-warning'
    case 'COMPLETED': return 'badge-success'
    case 'CANCELLED': return 'badge-neutral'
    case 'NO_SHOW': return 'badge-danger'
    default: return 'badge-neutral'
  }
}

async function dispatchNow() {
  const result = await dispatchReminders()
  ElMessage.success(`扫描 ${result.scanned} 条：成功 ${result.sent}，失败 ${result.failed}`)
}

// ------------------------------------------------------------------
// 创建预约
// ------------------------------------------------------------------
const createVisible = ref(false)
const createLoading = ref(false)
const createForm = reactive({ customerId: '', typeCode: '', slotId: '', contactPhone: '', remark: '' })
const customerOptions = ref<CustomerSummary[]>([])

async function searchCustomers(keyword: string) {
  const result = await fetchCustomers({ keyword: keyword || undefined, page: 1, pageSize: 10 })
  customerOptions.value = result.items
}

function openCreate() {
  createForm.customerId = ''
  createForm.typeCode = ''
  createForm.slotId = ''
  createForm.contactPhone = ''
  createForm.remark = ''
  openSlots.value = []
  createVisible.value = true
  searchCustomers('')
}

watch(() => createForm.typeCode, (code) => {
  createForm.slotId = ''
  loadOpenSlots(code)
})

async function submitCreate() {
  if (!createForm.customerId || !createForm.typeCode || !createForm.slotId) {
    ElMessage.warning('请选择客户、预约类型与服务时段')
    return
  }
  createLoading.value = true
  try {
    await createAppointment({
      customerId: createForm.customerId,
      typeCode: createForm.typeCode,
      slotId: createForm.slotId,
      contactPhone: createForm.contactPhone || undefined,
      remark: createForm.remark || undefined
    })
    ElMessage.success('预约创建成功')
    createVisible.value = false
    loadAppointments()
  } finally {
    createLoading.value = false
  }
}

// ------------------------------------------------------------------
// 详情抽屉与履约操作
// ------------------------------------------------------------------
const detailVisible = ref(false)
const detail = ref<AppointmentDetail | null>(null)

async function openDetail(row: AppointmentSummary) {
  detail.value = await fetchAppointment(row.id)
  detailVisible.value = true
}

async function refreshDetail() {
  if (detail.value) {
    detail.value = await fetchAppointment(detail.value.summary.id)
  }
}

async function changeStatus(target: 'CONFIRMED' | 'COMPLETED' | 'NO_SHOW') {
  if (!detail.value) return
  const labels = { CONFIRMED: '确认到院', COMPLETED: '标记完成', NO_SHOW: '标记爽约' }
  let reason: string | undefined
  if (target === 'NO_SHOW') {
    const input = await ElMessageBox.prompt('请填写爽约说明', '标记爽约', { inputValidator: v => true })
    reason = input.value || undefined
  }
  await changeAppointmentStatus(detail.value.summary.id, target, reason)
  ElMessage.success(labels[target] + '成功')
  await refreshDetail()
  loadAppointments()
}

// ------------------------------------------------------------------
// 改期与取消
// ------------------------------------------------------------------
const rescheduleVisible = ref(false)
const rescheduleForm = reactive({ slotId: '', remark: '' })
const rescheduleSlots = ref<ServiceSlot[]>([])

async function openReschedule() {
  if (!detail.value) return
  rescheduleForm.slotId = ''
  rescheduleForm.remark = ''
  const today = new Date().toISOString().slice(0, 10)
  const result = await fetchSlots({
    typeCode: detail.value.summary.typeCode,
    dateFrom: today,
    status: 'OPEN',
    page: 1,
    pageSize: 100
  })
  rescheduleSlots.value = result.items.filter(slot => slot.id !== detail.value?.summary.slotId)
  rescheduleVisible.value = true
}

async function submitReschedule() {
  if (!detail.value || !rescheduleForm.slotId) {
    ElMessage.warning('请选择目标时段')
    return
  }
  await rescheduleAppointment(detail.value.summary.id, {
    slotId: rescheduleForm.slotId,
    remark: rescheduleForm.remark || undefined
  })
  ElMessage.success('改期成功')
  rescheduleVisible.value = false
  await refreshDetail()
  loadAppointments()
}

async function cancelAppointmentAction() {
  if (!detail.value) return
  const input = await ElMessageBox.prompt('请填写取消原因', '取消预约', { inputValidator: v => true })
  await cancelAppointment(detail.value.summary.id, input.value || undefined)
  ElMessage.success('预约已取消，容量已释放')
  await refreshDetail()
  loadAppointments()
}

const isActiveAppointment = computed(() =>
  detail.value !== null && ['BOOKED', 'CONFIRMED'].includes(detail.value.summary.status))

function reminderBadge(status: string) {
  switch (status) {
    case 'SENT': return 'badge-success'
    case 'PENDING': return 'badge-primary'
    case 'FAILED': return 'badge-danger'
    default: return 'badge-neutral'
  }
}

async function retryOne(reminder: AppointmentReminder) {
  await retryReminder(reminder.id)
  ElMessage.success('已执行重试')
  await refreshDetail()
}

// ------------------------------------------------------------------
// 服务时段
// ------------------------------------------------------------------
const slotQuery = reactive({ typeCode: '', dateRange: [] as string[], status: '', page: 1, pageSize: 20 })
const slotRows = ref<ServiceSlot[]>([])
const slotTotal = ref(0)
const slotLoading = ref(false)

async function loadSlots() {
  slotLoading.value = true
  try {
    const result = await fetchSlots({
      typeCode: slotQuery.typeCode || undefined,
      dateFrom: slotQuery.dateRange?.[0] || undefined,
      dateTo: slotQuery.dateRange?.[1] || undefined,
      status: slotQuery.status || undefined,
      page: slotQuery.page,
      pageSize: slotQuery.pageSize
    })
    slotRows.value = result.items
    slotTotal.value = result.total
  } finally {
    slotLoading.value = false
  }
}

const slotDialogVisible = ref(false)
const slotEditing = ref<ServiceSlot | null>(null)
const slotForm = reactive({ typeCode: '', slotDate: '', startTime: '', endTime: '', capacity: 5, remark: '' })

function openSlotCreate() {
  slotEditing.value = null
  slotForm.typeCode = types.value[0]?.typeCode ?? ''
  slotForm.slotDate = ''
  slotForm.startTime = ''
  slotForm.endTime = ''
  slotForm.capacity = 5
  slotForm.remark = ''
  slotDialogVisible.value = true
}

function openSlotEdit(slot: ServiceSlot) {
  slotEditing.value = slot
  slotForm.typeCode = slot.typeCode
  slotForm.slotDate = slot.slotDate
  slotForm.startTime = slot.startTime
  slotForm.endTime = slot.endTime
  slotForm.capacity = slot.capacity
  slotForm.remark = slot.remark ?? ''
  slotDialogVisible.value = true
}

async function submitSlot() {
  if (!slotForm.typeCode || !slotForm.slotDate || !slotForm.startTime || !slotForm.endTime) {
    ElMessage.warning('请完整填写时段信息')
    return
  }
  const payload = {
    typeCode: slotForm.typeCode,
    slotDate: slotForm.slotDate,
    startTime: slotForm.startTime,
    endTime: slotForm.endTime,
    capacity: slotForm.capacity,
    remark: slotForm.remark || undefined
  }
  if (slotEditing.value) {
    await updateSlot(slotEditing.value.id, {
      slotDate: payload.slotDate,
      startTime: payload.startTime,
      endTime: payload.endTime,
      capacity: payload.capacity,
      remark: payload.remark
    })
    ElMessage.success('时段已更新')
  } else {
    await createSlot(payload)
    ElMessage.success('时段已发布')
  }
  slotDialogVisible.value = false
  loadSlots()
}

async function toggleSlot(slot: ServiceSlot) {
  const next = slot.status === 'OPEN' ? 'CLOSED' : 'OPEN'
  await ElMessageBox.confirm(`确认将时段 ${slot.slotDate} ${formatTime(slot.startTime)} 设为 ${next}？`, '提示', { type: 'warning' })
  await changeSlotStatus(slot.id, next)
  ElMessage.success('状态已更新')
  loadSlots()
}

// ------------------------------------------------------------------
// 预约类型
// ------------------------------------------------------------------
const typeRows = ref<AppointmentType[]>([])
const typeDialogVisible = ref(false)
const typeEditing = ref<AppointmentType | null>(null)
const typeForm = reactive({ typeCode: '', typeName: '', durationMinutes: 30, cancelCutoffHours: 2, remark: '' })

async function loadTypeRows() {
  typeRows.value = await fetchAppointmentTypes(true)
}

function openTypeCreate() {
  typeEditing.value = null
  typeForm.typeCode = ''
  typeForm.typeName = ''
  typeForm.durationMinutes = 30
  typeForm.cancelCutoffHours = 2
  typeForm.remark = ''
  typeDialogVisible.value = true
}

function openTypeEdit(item: AppointmentType) {
  typeEditing.value = item
  typeForm.typeCode = item.typeCode
  typeForm.typeName = item.typeName
  typeForm.durationMinutes = item.durationMinutes
  typeForm.cancelCutoffHours = item.cancelCutoffHours
  typeForm.remark = item.remark ?? ''
  typeDialogVisible.value = true
}

async function submitType() {
  if (!typeForm.typeName) {
    ElMessage.warning('请填写类型名称')
    return
  }
  if (typeEditing.value) {
    await updateAppointmentType(typeEditing.value.typeCode, {
      typeName: typeForm.typeName,
      durationMinutes: typeForm.durationMinutes,
      cancelCutoffHours: typeForm.cancelCutoffHours,
      remark: typeForm.remark || undefined
    })
    ElMessage.success('类型已更新')
  } else {
    if (!typeForm.typeCode) {
      ElMessage.warning('请填写类型编码')
      return
    }
    await createAppointmentType({
      typeCode: typeForm.typeCode,
      typeName: typeForm.typeName,
      durationMinutes: typeForm.durationMinutes,
      cancelCutoffHours: typeForm.cancelCutoffHours,
      remark: typeForm.remark || undefined
    })
    ElMessage.success('类型已创建')
  }
  typeDialogVisible.value = false
  await Promise.all([loadTypeRows(), loadTypes()])
}

async function toggleType(item: AppointmentType) {
  const next = item.status === 'active' ? 'disabled' : 'active'
  await ElMessageBox.confirm(`确认将「${item.typeName}」设为${next === 'active' ? '启用' : '停用'}？`, '提示', { type: 'warning' })
  await changeAppointmentTypeStatus(item.typeCode, next)
  ElMessage.success('状态已更新')
  await Promise.all([loadTypeRows(), loadTypes()])
}

// ------------------------------------------------------------------
// 爽约记录
// ------------------------------------------------------------------
const noShowQuery = reactive({ dateRange: [] as string[], page: 1, pageSize: 20 })
const noShowRows = ref<NoShowRecord[]>([])
const noShowTotal = ref(0)
const noShowLoading = ref(false)

async function loadNoShows() {
  noShowLoading.value = true
  try {
    const result = await fetchNoShows({
      dateFrom: noShowQuery.dateRange?.[0] || undefined,
      dateTo: noShowQuery.dateRange?.[1] || undefined,
      page: noShowQuery.page,
      pageSize: noShowQuery.pageSize
    })
    noShowRows.value = result.items
    noShowTotal.value = result.total
  } finally {
    noShowLoading.value = false
  }
}

watch(activeTab, (tab) => {
  if (tab === 'slots' && slotRows.value.length === 0) loadSlots()
  if (tab === 'types' && typeRows.value.length === 0) loadTypeRows()
  if (tab === 'noshow' && noShowRows.value.length === 0) loadNoShows()
})

onMounted(() => {
  loadTypes()
  if (canReadAppointment.value) {
    loadAppointments()
  }
})
</script>

<template>
  <div class="page">
    <header class="page-head">
      <div>
        <h1>预约中心</h1>
        <p>预约类型、服务时段、预约、改期、取消、提醒与爽约记录</p>
      </div>
      <div class="head-metrics">
        <span class="metric">预约总数 <strong>{{ apptTotal }}</strong></span>
      </div>
    </header>

    <el-tabs v-model="activeTab" class="tab-card">
      <el-tab-pane label="预约管理" name="appointments">
        <div class="card">
          <div class="toolbar">
            <el-input v-model="apptQuery.keyword" placeholder="预约单号" clearable style="width: 180px"
                      @keyup.enter="apptQuery.page = 1; loadAppointments()" />
            <el-select v-model="apptQuery.status" placeholder="状态" clearable style="width: 130px">
              <el-option label="已预约" value="BOOKED" />
              <el-option label="已确认" value="CONFIRMED" />
              <el-option label="已完成" value="COMPLETED" />
              <el-option label="已取消" value="CANCELLED" />
              <el-option label="爽约" value="NO_SHOW" />
            </el-select>
            <el-select v-model="apptQuery.typeCode" placeholder="预约类型" clearable style="width: 150px">
              <el-option v-for="t in types" :key="t.typeCode" :label="t.typeName" :value="t.typeCode" />
            </el-select>
            <el-date-picker v-model="apptQuery.dateRange" type="daterange" value-format="YYYY-MM-DD"
                            start-placeholder="开始日期" end-placeholder="结束日期" style="width: 240px" />
            <el-button type="primary" @click="apptQuery.page = 1; loadAppointments()">查询</el-button>
            <el-button @click="resetApptQuery">重置</el-button>
            <span class="spacer" />
            <el-button v-if="canManageAppointment" @click="dispatchNow">发送到期提醒</el-button>
            <el-button v-if="canWriteAppointment" type="primary" @click="openCreate">创建预约</el-button>
          </div>

          <el-table v-loading="apptLoading" :data="apptRows" stripe>
            <el-table-column prop="appointmentNo" label="预约单号" width="120">
              <template #default="{ row }"><span class="cell-mono">{{ row.appointmentNo }}</span></template>
            </el-table-column>
            <el-table-column label="客户" min-width="150">
              <template #default="{ row }">
                {{ row.customerName || '—' }}
                <span v-if="row.customerNo" class="cell-mono sub"> {{ row.customerNo }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="typeName" label="类型" width="110" />
            <el-table-column label="服务时段" min-width="170">
              <template #default="{ row }">
                {{ row.slotDate }} {{ formatTime(row.startTime) }}-{{ formatTime(row.endTime) }}
              </template>
            </el-table-column>
            <el-table-column label="状态" width="100">
              <template #default="{ row }">
                <span :class="['badge', appointmentBadge(row.status)]">{{ row.status }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="contactPhoneMasked" label="联系电话" width="120">
              <template #default="{ row }">{{ row.contactPhoneMasked || '—' }}</template>
            </el-table-column>
            <el-table-column label="创建时间" width="170">
              <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
            </el-table-column>
            <el-table-column label="操作" width="90" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" @click="openDetail(row)">详情</el-button>
              </template>
            </el-table-column>
          </el-table>

          <el-pagination v-model:current-page="apptQuery.page" v-model:page-size="apptQuery.pageSize"
                         :total="apptTotal" :page-sizes="[10, 20, 50]"
                         layout="total, sizes, prev, pager, next" class="table-pagination"
                         @change="loadAppointments" />
        </div>
      </el-tab-pane>

      <el-tab-pane label="服务时段" name="slots">
        <div class="card">
          <div class="toolbar">
            <el-select v-model="slotQuery.typeCode" placeholder="预约类型" clearable style="width: 150px">
              <el-option v-for="t in types" :key="t.typeCode" :label="t.typeName" :value="t.typeCode" />
            </el-select>
            <el-date-picker v-model="slotQuery.dateRange" type="daterange" value-format="YYYY-MM-DD"
                            start-placeholder="开始日期" end-placeholder="结束日期" style="width: 240px" />
            <el-select v-model="slotQuery.status" placeholder="状态" clearable style="width: 120px">
              <el-option label="开放" value="OPEN" />
              <el-option label="关闭" value="CLOSED" />
            </el-select>
            <el-button type="primary" @click="slotQuery.page = 1; loadSlots()">查询</el-button>
            <span class="spacer" />
            <el-button v-if="canWriteSlot" type="primary" @click="openSlotCreate">新建时段</el-button>
          </div>

          <el-table v-loading="slotLoading" :data="slotRows" stripe>
            <el-table-column prop="typeName" label="类型" width="120" />
            <el-table-column prop="slotDate" label="日期" width="120" />
            <el-table-column label="时间" width="140">
              <template #default="{ row }">{{ formatTime(row.startTime) }} - {{ formatTime(row.endTime) }}</template>
            </el-table-column>
            <el-table-column label="已约/容量" width="110">
              <template #default="{ row }">{{ row.bookedCount }} / {{ row.capacity }}</template>
            </el-table-column>
            <el-table-column prop="remaining" label="剩余" width="80" />
            <el-table-column label="状态" width="90">
              <template #default="{ row }">
                <span :class="['badge', row.status === 'OPEN' ? 'badge-success' : 'badge-neutral']">{{ row.status }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="remark" label="备注" min-width="140">
              <template #default="{ row }">{{ row.remark || '—' }}</template>
            </el-table-column>
            <el-table-column v-if="canWriteSlot" label="操作" width="150" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" @click="openSlotEdit(row)">编辑</el-button>
                <el-button link :type="row.status === 'OPEN' ? 'danger' : 'primary'" @click="toggleSlot(row)">
                  {{ row.status === 'OPEN' ? '关闭' : '开放' }}
                </el-button>
              </template>
            </el-table-column>
          </el-table>

          <el-pagination v-model:current-page="slotQuery.page" v-model:page-size="slotQuery.pageSize"
                         :total="slotTotal" :page-sizes="[10, 20, 50]"
                         layout="total, sizes, prev, pager, next" class="table-pagination"
                         @change="loadSlots" />
        </div>
      </el-tab-pane>

      <el-tab-pane label="预约类型" name="types">
        <div class="card">
          <div class="toolbar">
            <span class="hint">取消/改期截止：时段开始前须提前的最少小时数</span>
            <span class="spacer" />
            <el-button v-if="canWriteSlot" type="primary" @click="openTypeCreate">新建类型</el-button>
          </div>
          <el-table :data="typeRows" stripe>
            <el-table-column prop="typeCode" label="编码" width="150">
              <template #default="{ row }"><span class="cell-mono">{{ row.typeCode }}</span></template>
            </el-table-column>
            <el-table-column prop="typeName" label="名称" min-width="140" />
            <el-table-column prop="durationMinutes" label="时长(分钟)" width="110" />
            <el-table-column prop="cancelCutoffHours" label="取消提前(小时)" width="130" />
            <el-table-column label="状态" width="90">
              <template #default="{ row }">
                <span :class="['badge', row.status === 'active' ? 'badge-success' : 'badge-neutral']">
                  {{ row.status === 'active' ? '启用' : '停用' }}
                </span>
              </template>
            </el-table-column>
            <el-table-column prop="remark" label="备注" min-width="160">
              <template #default="{ row }">{{ row.remark || '—' }}</template>
            </el-table-column>
            <el-table-column v-if="canWriteSlot" label="操作" width="150" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" @click="openTypeEdit(row)">编辑</el-button>
                <el-button link :type="row.status === 'active' ? 'danger' : 'primary'" @click="toggleType(row)">
                  {{ row.status === 'active' ? '停用' : '启用' }}
                </el-button>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </el-tab-pane>

      <el-tab-pane label="爽约记录" name="noshow">
        <div class="card">
          <div class="toolbar">
            <el-date-picker v-model="noShowQuery.dateRange" type="daterange" value-format="YYYY-MM-DD"
                            start-placeholder="开始日期" end-placeholder="结束日期" style="width: 240px" />
            <el-button type="primary" @click="noShowQuery.page = 1; loadNoShows()">查询</el-button>
          </div>
          <el-table v-loading="noShowLoading" :data="noShowRows" stripe>
            <el-table-column prop="appointmentNo" label="预约单号" width="120">
              <template #default="{ row }"><span class="cell-mono">{{ row.appointmentNo }}</span></template>
            </el-table-column>
            <el-table-column label="客户" min-width="160">
              <template #default="{ row }">
                {{ row.customerName || '—' }}
                <span v-if="row.customerNo" class="cell-mono sub"> {{ row.customerNo }}</span>
              </template>
            </el-table-column>
            <el-table-column label="预约时间" width="180">
              <template #default="{ row }">{{ formatDateTime(row.appointmentAt) }}</template>
            </el-table-column>
            <el-table-column prop="reason" label="说明" min-width="160">
              <template #default="{ row }">{{ row.reason || '—' }}</template>
            </el-table-column>
            <el-table-column prop="recordedByName" label="记录人" width="110">
              <template #default="{ row }">{{ row.recordedByName || '—' }}</template>
            </el-table-column>
            <el-table-column label="记录时间" width="180">
              <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
            </el-table-column>
          </el-table>
          <el-pagination v-model:current-page="noShowQuery.page" v-model:page-size="noShowQuery.pageSize"
                         :total="noShowTotal" :page-sizes="[10, 20, 50]"
                         layout="total, sizes, prev, pager, next" class="table-pagination"
                         @change="loadNoShows" />
        </div>
      </el-tab-pane>
    </el-tabs>

    <!-- 创建预约 -->
    <el-dialog v-model="createVisible" title="创建预约" width="520px">
      <el-form label-width="90px">
        <el-form-item label="客户" required>
          <el-select v-model="createForm.customerId" filterable remote :remote-method="searchCustomers"
                     placeholder="输入姓名/客户号/手机号搜索" style="width: 100%">
            <el-option v-for="c in customerOptions" :key="c.id"
                       :label="`${c.name}（${c.customerNo}）`" :value="c.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="预约类型" required>
          <el-select v-model="createForm.typeCode" placeholder="选择类型" style="width: 100%">
            <el-option v-for="t in types.filter(x => x.status === 'active')" :key="t.typeCode"
                       :label="t.typeName" :value="t.typeCode" />
          </el-select>
        </el-form-item>
        <el-form-item label="服务时段" required>
          <el-select v-model="createForm.slotId" placeholder="先选择预约类型" style="width: 100%">
            <el-option v-for="s in openSlots" :key="s.id" :label="slotLabel(s)" :value="s.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="联系电话">
          <el-input v-model="createForm.contactPhone" maxlength="20" placeholder="到院联系手机号（选填）" />
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

    <!-- 改期 -->
    <el-dialog v-model="rescheduleVisible" title="预约改期" width="480px">
      <el-form label-width="90px">
        <el-form-item label="目标时段" required>
          <el-select v-model="rescheduleForm.slotId" placeholder="选择开放时段" style="width: 100%">
            <el-option v-for="s in rescheduleSlots" :key="s.id" :label="slotLabel(s)" :value="s.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="改期说明">
          <el-input v-model="rescheduleForm.remark" type="textarea" :rows="2" maxlength="500" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="rescheduleVisible = false">取消</el-button>
        <el-button type="primary" @click="submitReschedule">确认改期</el-button>
      </template>
    </el-dialog>

    <!-- 时段编辑 -->
    <el-dialog v-model="slotDialogVisible" :title="slotEditing ? '编辑服务时段' : '新建服务时段'" width="460px">
      <el-form label-width="90px">
        <el-form-item label="预约类型" required>
          <el-select v-model="slotForm.typeCode" :disabled="slotEditing !== null" style="width: 100%">
            <el-option v-for="t in types.filter(x => x.status === 'active')" :key="t.typeCode"
                       :label="t.typeName" :value="t.typeCode" />
          </el-select>
        </el-form-item>
        <el-form-item label="服务日期" required>
          <el-date-picker v-model="slotForm.slotDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
        <el-form-item label="开始时间" required>
          <el-time-select v-model="slotForm.startTime" start="07:00" step="00:30" end="21:00" style="width: 100%" />
        </el-form-item>
        <el-form-item label="结束时间" required>
          <el-time-select v-model="slotForm.endTime" :start="slotForm.startTime || '07:30'" step="00:30" end="22:00"
                          style="width: 100%" />
        </el-form-item>
        <el-form-item label="容量" required>
          <el-input-number v-model="slotForm.capacity" :min="1" :max="500"
                           :disabled="slotEditing !== null && slotEditing.bookedCount > 0" />
          <span v-if="slotEditing" class="hint">已约 {{ slotEditing.bookedCount }} 人</span>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="slotForm.remark" maxlength="500" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="slotDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitSlot">保存</el-button>
      </template>
    </el-dialog>

    <!-- 类型编辑 -->
    <el-dialog v-model="typeDialogVisible" :title="typeEditing ? '编辑预约类型' : '新建预约类型'" width="460px">
      <el-form label-width="110px">
        <el-form-item label="类型编码" required>
          <el-input v-model="typeForm.typeCode" :disabled="typeEditing !== null"
                    placeholder="大写字母/数字/下划线" maxlength="64" />
        </el-form-item>
        <el-form-item label="类型名称" required>
          <el-input v-model="typeForm.typeName" maxlength="100" />
        </el-form-item>
        <el-form-item label="服务时长(分)">
          <el-input-number v-model="typeForm.durationMinutes" :min="1" :max="480" />
        </el-form-item>
        <el-form-item label="取消提前(时)">
          <el-input-number v-model="typeForm.cancelCutoffHours" :min="0" :max="720" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="typeForm.remark" maxlength="500" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="typeDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitType">保存</el-button>
      </template>
    </el-dialog>

    <!-- 预约详情抽屉 -->
    <el-drawer v-model="detailVisible" title="预约详情" size="640px">
      <div v-if="detail" class="drawer-body">
        <div class="detail-head">
          <h2>{{ detail.summary.appointmentNo }}</h2>
          <span :class="['badge', appointmentBadge(detail.summary.status)]">{{ detail.summary.status }}</span>
        </div>
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="客户">{{ detail.summary.customerName || '—' }}</el-descriptions-item>
          <el-descriptions-item label="客户号">{{ detail.summary.customerNo || '—' }}</el-descriptions-item>
          <el-descriptions-item label="预约类型">{{ detail.summary.typeName }}</el-descriptions-item>
          <el-descriptions-item label="联系电话">{{ detail.summary.contactPhoneMasked || '—' }}</el-descriptions-item>
          <el-descriptions-item label="服务日期">{{ detail.summary.slotDate }}</el-descriptions-item>
          <el-descriptions-item label="服务时间">
            {{ formatTime(detail.summary.startTime) }} - {{ formatTime(detail.summary.endTime) }}
          </el-descriptions-item>
          <el-descriptions-item label="历史爽约">{{ detail.noShowCount }} 次</el-descriptions-item>
          <el-descriptions-item label="创建时间">{{ formatDateTime(detail.summary.createdAt) }}</el-descriptions-item>
          <el-descriptions-item v-if="detail.cancelledAt" label="取消时间">
            {{ formatDateTime(detail.cancelledAt) }}（{{ detail.cancelReason || '未填写原因' }}）
          </el-descriptions-item>
          <el-descriptions-item v-if="detail.completedAt" label="完成时间">{{ formatDateTime(detail.completedAt) }}</el-descriptions-item>
          <el-descriptions-item v-if="detail.noShowAt" label="爽约时间">{{ formatDateTime(detail.noShowAt) }}</el-descriptions-item>
          <el-descriptions-item label="备注" :span="2">{{ detail.remark || '—' }}</el-descriptions-item>
        </el-descriptions>

        <h3 class="sub-title">提醒记录</h3>
        <el-table :data="detail.reminders" size="small" stripe>
          <el-table-column prop="channel" label="通道" width="70" />
          <el-table-column label="计划时间" width="160">
            <template #default="{ row }">{{ formatDateTime(row.plannedAt) }}</template>
          </el-table-column>
          <el-table-column label="状态" width="90">
            <template #default="{ row }">
              <span :class="['badge', reminderBadge(row.status)]">{{ row.status }}</span>
            </template>
          </el-table-column>
          <el-table-column label="重试" width="80">
            <template #default="{ row }">{{ row.retryCount }} / {{ row.maxRetries }}</template>
          </el-table-column>
          <el-table-column prop="lastError" label="最近错误" min-width="130">
            <template #default="{ row }">{{ row.lastError || '—' }}</template>
          </el-table-column>
          <el-table-column v-if="canManageAppointment" label="操作" width="70" fixed="right">
            <template #default="{ row }">
              <el-button v-if="row.status === 'FAILED' || row.status === 'PENDING'"
                         link type="primary" @click="retryOne(row)">重试</el-button>
            </template>
          </el-table-column>
        </el-table>

        <div class="detail-actions">
          <el-button v-if="canManageAppointment && detail.summary.status === 'BOOKED'"
                    type="primary" @click="changeStatus('CONFIRMED')">确认到院</el-button>
          <el-button v-if="canManageAppointment && ['BOOKED','CONFIRMED'].includes(detail.summary.status)"
                    @click="changeStatus('COMPLETED')">标记完成</el-button>
          <el-button v-if="canManageAppointment && ['BOOKED','CONFIRMED'].includes(detail.summary.status)"
                    type="danger" plain @click="changeStatus('NO_SHOW')">标记爽约</el-button>
          <el-button v-if="canWriteAppointment && isActiveAppointment" @click="openReschedule">改期</el-button>
          <el-button v-if="canWriteAppointment && isActiveAppointment" type="warning" plain
                    @click="cancelAppointmentAction">取消预约</el-button>
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

.hint {
  color: var(--el-text-color-secondary);
  font-size: 12px;
  margin-left: 8px;
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
}

.detail-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 18px;
}
</style>
