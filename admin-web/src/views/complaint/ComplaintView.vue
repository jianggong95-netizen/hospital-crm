<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import { fetchUsers } from '@/api/users'
import { fetchCustomers } from '@/api/customers'
import {
  addComplaintAction,
  addComplaintFeedback,
  addComplaintRectification,
  assignComplaintTicket,
  cancelComplaintTicket,
  createComplaintTicket,
  escalateComplaintTicket,
  fetchComplaintDetail,
  fetchComplaintTickets,
  reviewComplaintTicket,
  scanComplaintEscalations,
  startComplaintTicket,
  submitComplaintForReview,
  submitComplaintRectification
} from '@/api/complaint'
import type { CustomerSummary } from '@/types/customer'
import type { UserResponse } from '@/types/system'
import type {
  ComplaintSeverity,
  ComplaintSource,
  ComplaintStatus,
  ComplaintTicket,
  ComplaintTicketDetail,
  ComplaintTicketType
} from '@/types/complaint'

const auth = useAuthStore()
const canWrite = computed(() => auth.has('crm:complaint:write'))
const canAssign = computed(() => auth.has('crm:complaint:assign'))
const canReview = computed(() => auth.has('crm:complaint:review'))
const canManage = computed(() => auth.has('crm:complaint:manage'))

const loading = ref(false)

const typeLabels: Record<string, string> = {
  complaint: '投诉',
  praise: '表扬',
  suggestion: '建议'
}

const severityLabels: Record<string, string> = {
  low: '低',
  normal: '一般',
  high: '较高',
  urgent: '紧急'
}

const statusLabels: Record<string, string> = {
  OPEN: '待受理',
  ASSIGNED: '已分派',
  PROCESSING: '处理中',
  RECTIFYING: '整改中',
  PENDING_REVIEW: '待复核',
  CLOSED: '已结案',
  CANCELLED: '已取消'
}

const sourceLabels: Record<string, string> = {
  manual: '手工录入',
  phone: '电话',
  online: '在线',
  h5: 'H5',
  mini_program: '小程序',
  his: 'HIS',
  letter: '来信'
}

const actionTypeLabels: Record<string, string> = {
  CREATE: '建单',
  ASSIGN: '分派',
  START: '开始处理',
  CONTACT: '联系患者',
  INVESTIGATE: '调查核实',
  REPLY: '回复患者',
  NOTE: '处理备注',
  RECTIFY: '整改',
  FEEDBACK: '患者反馈',
  ESCALATE: '升级',
  REVIEW: '复核',
  CANCEL: '取消',
  OTHER: '其他'
}

const rectificationStatusLabels: Record<string, string> = {
  PENDING: '待提交',
  SUBMITTED: '已提交',
  VERIFIED: '已核验'
}

const terminalStatuses = ['CLOSED', 'CANCELLED']

function isTerminal(status: string) {
  return terminalStatuses.includes(status)
}

function statusTag(status: string) {
  if (status === 'CLOSED') return 'success'
  if (status === 'CANCELLED') return 'info'
  if (status === 'PENDING_REVIEW') return 'warning'
  if (status === 'PROCESSING' || status === 'RECTIFYING') return 'primary'
  if (status === 'ASSIGNED') return 'warning'
  return 'danger'
}

function severityTag(severity: string) {
  if (severity === 'urgent') return 'danger'
  if (severity === 'high') return 'warning'
  if (severity === 'low') return 'info'
  return 'primary'
}

function formatTime(value?: string) {
  if (!value) return '—'
  return value.replace('T', ' ').slice(0, 16)
}

// ------------------------------------------------------------------
// 基础数据
// ------------------------------------------------------------------
const users = ref<UserResponse[]>([])
const customerOptions = ref<CustomerSummary[]>([])

async function loadBase() {
  const result = await fetchUsers({ page: 1, pageSize: 100 })
  users.value = result.items
}

async function searchCustomers(keyword: string) {
  const result = await fetchCustomers({ keyword: keyword || undefined, page: 1, pageSize: 10 })
  customerOptions.value = result.items
}

function customerLabel(customer?: CustomerSummary) {
  if (!customer) return ''
  return `${customer.name || customer.customerNo}（${customer.customerNo}）`
}

// ------------------------------------------------------------------
// 工单列表
// ------------------------------------------------------------------
const query = reactive({
  keyword: '',
  ticketType: '',
  severity: '',
  status: '' as ComplaintStatus | '',
  overdue: false,
  escalated: false,
  page: 1,
  pageSize: 20
})
const rows = ref<ComplaintTicket[]>([])
const total = ref(0)

async function loadTickets() {
  loading.value = true
  try {
    const result = await fetchComplaintTickets({
      keyword: query.keyword || undefined,
      ticketType: query.ticketType || undefined,
      severity: query.severity || undefined,
      status: (query.status || undefined) as ComplaintStatus | undefined,
      overdue: query.overdue || undefined,
      escalated: query.escalated || undefined,
      page: query.page,
      pageSize: query.pageSize
    })
    rows.value = result.items
    total.value = result.total
  } finally {
    loading.value = false
  }
}

async function handleScan() {
  try {
    const result = await scanComplaintEscalations()
    ElMessage.success(`超时扫描完成，本次升级 ${result.escalated} 个工单`)
    await loadTickets()
  } catch (error) {
    ElMessage.error((error as Error).message)
  }
}

// ------------------------------------------------------------------
// 新建工单
// ------------------------------------------------------------------
const createVisible = ref(false)
const createForm = reactive({
  customerId: '',
  ticketType: 'complaint' as ComplaintTicketType,
  severity: 'normal' as ComplaintSeverity,
  subject: '',
  description: '',
  source: 'manual' as ComplaintSource,
  assigneeId: '',
  dueAt: ''
})

function openCreate() {
  createForm.customerId = ''
  createForm.ticketType = 'complaint'
  createForm.severity = 'normal'
  createForm.subject = ''
  createForm.description = ''
  createForm.source = 'manual'
  createForm.assigneeId = ''
  createForm.dueAt = ''
  createVisible.value = true
  void searchCustomers('')
}

async function submitCreate() {
  if (!createForm.customerId || !createForm.subject || !createForm.description) {
    ElMessage.warning('请选择客户并填写标题与详细描述')
    return
  }
  try {
    await createComplaintTicket({
      customerId: createForm.customerId,
      ticketType: createForm.ticketType,
      severity: createForm.severity,
      subject: createForm.subject,
      description: createForm.description,
      source: createForm.source,
      assigneeId: createForm.assigneeId || undefined,
      dueAt: createForm.dueAt ? new Date(createForm.dueAt).toISOString() : undefined
    })
    ElMessage.success('工单已创建')
    createVisible.value = false
    query.page = 1
    await loadTickets()
  } catch (error) {
    ElMessage.error((error as Error).message)
  }
}

// ------------------------------------------------------------------
// 分派
// ------------------------------------------------------------------
const assignVisible = ref(false)
const assignForm = reactive({ ticketId: '', assigneeId: '', dueAt: '' })

function openAssign(ticket: ComplaintTicket) {
  assignForm.ticketId = ticket.id
  assignForm.assigneeId = ticket.assigneeId || ''
  assignForm.dueAt = ''
  assignVisible.value = true
}

async function submitAssign() {
  if (!assignForm.assigneeId) {
    ElMessage.warning('请选择处理人')
    return
  }
  try {
    await assignComplaintTicket(assignForm.ticketId, {
      assigneeId: assignForm.assigneeId,
      dueAt: assignForm.dueAt ? new Date(assignForm.dueAt).toISOString() : undefined
    })
    ElMessage.success('分派成功')
    assignVisible.value = false
    await afterMutation(assignForm.ticketId)
  } catch (error) {
    ElMessage.error((error as Error).message)
  }
}

// ------------------------------------------------------------------
// 详情
// ------------------------------------------------------------------
const detailVisible = ref(false)
const detail = ref<ComplaintTicketDetail>()
const detailLoading = ref(false)
const actionForm = reactive({ actionType: 'CONTACT', content: '' })

async function loadDetail(ticketId: string) {
  detailLoading.value = true
  try {
    detail.value = await fetchComplaintDetail(ticketId)
  } finally {
    detailLoading.value = false
  }
}

async function openDetail(ticket: ComplaintTicket) {
  detailVisible.value = true
  actionForm.actionType = 'CONTACT'
  actionForm.content = ''
  await loadDetail(ticket.id)
}

async function afterMutation(ticketId: string) {
  await loadTickets()
  if (detailVisible.value && detail.value?.ticket.id === ticketId) {
    await loadDetail(ticketId)
  }
}

async function handleStart(ticket: ComplaintTicket) {
  try {
    await startComplaintTicket(ticket.id)
    ElMessage.success('已开始处理')
    await afterMutation(ticket.id)
  } catch (error) {
    ElMessage.error((error as Error).message)
  }
}

async function submitAction() {
  if (!detail.value) return
  if (!actionForm.content.trim()) {
    ElMessage.warning('请填写处理内容')
    return
  }
  try {
    await addComplaintAction(detail.value.ticket.id, {
      actionType: actionForm.actionType,
      content: actionForm.content.trim()
    })
    actionForm.content = ''
    ElMessage.success('处理动作已登记')
    await afterMutation(detail.value.ticket.id)
  } catch (error) {
    ElMessage.error((error as Error).message)
  }
}

async function handleSubmitReview() {
  if (!detail.value) return
  try {
    await submitComplaintForReview(detail.value.ticket.id)
    ElMessage.success('已提交结案复核')
    await afterMutation(detail.value.ticket.id)
  } catch (error) {
    ElMessage.error((error as Error).message)
  }
}

// ------------------------------------------------------------------
// 整改
// ------------------------------------------------------------------
const rectificationVisible = ref(false)
const rectificationForm = reactive({ ticketId: '', content: '', dueAt: '' })

function openRectification(ticketId: string) {
  rectificationForm.ticketId = ticketId
  rectificationForm.content = ''
  rectificationForm.dueAt = ''
  rectificationVisible.value = true
}

async function submitRectification() {
  if (!rectificationForm.content.trim()) {
    ElMessage.warning('请填写整改措施')
    return
  }
  try {
    await addComplaintRectification(rectificationForm.ticketId, {
      content: rectificationForm.content.trim(),
      dueAt: rectificationForm.dueAt ? new Date(rectificationForm.dueAt).toISOString() : undefined
    })
    ElMessage.success('整改任务已登记')
    rectificationVisible.value = false
    await afterMutation(rectificationForm.ticketId)
  } catch (error) {
    ElMessage.error((error as Error).message)
  }
}

async function submitRectificationItem(rectificationId: string) {
  if (!detail.value) return
  try {
    await submitComplaintRectification(detail.value.ticket.id, rectificationId)
    ElMessage.success('整改措施已提交')
    await afterMutation(detail.value.ticket.id)
  } catch (error) {
    ElMessage.error((error as Error).message)
  }
}

// ------------------------------------------------------------------
// 患者反馈
// ------------------------------------------------------------------
const feedbackVisible = ref(false)
const feedbackForm = reactive({
  ticketId: '',
  rating: 5,
  satisfied: true,
  comment: '',
  channel: 'manual'
})

function openFeedback(ticketId: string) {
  feedbackForm.ticketId = ticketId
  feedbackForm.rating = 5
  feedbackForm.satisfied = true
  feedbackForm.comment = ''
  feedbackForm.channel = 'manual'
  feedbackVisible.value = true
}

async function submitFeedback() {
  try {
    await addComplaintFeedback(feedbackForm.ticketId, {
      rating: feedbackForm.rating,
      satisfied: feedbackForm.satisfied,
      comment: feedbackForm.comment || undefined,
      channel: feedbackForm.channel
    })
    ElMessage.success('患者反馈已登记')
    feedbackVisible.value = false
    await afterMutation(feedbackForm.ticketId)
  } catch (error) {
    ElMessage.error((error as Error).message)
  }
}

// ------------------------------------------------------------------
// 结案审核 / 升级 / 取消
// ------------------------------------------------------------------
const reviewVisible = ref(false)
const reviewForm = reactive({ ticketId: '', decision: 'PASS' as 'PASS' | 'REJECT', comment: '' })

function openReview(ticketId: string) {
  reviewForm.ticketId = ticketId
  reviewForm.decision = 'PASS'
  reviewForm.comment = ''
  reviewVisible.value = true
}

async function submitReview() {
  try {
    await reviewComplaintTicket(reviewForm.ticketId, {
      decision: reviewForm.decision,
      comment: reviewForm.comment || undefined
    })
    ElMessage.success(reviewForm.decision === 'PASS' ? '复核通过，工单已结案' : '已退回处理人重新处理')
    reviewVisible.value = false
    await afterMutation(reviewForm.ticketId)
  } catch (error) {
    ElMessage.error((error as Error).message)
  }
}

async function handleEscalate(ticket: ComplaintTicket) {
  try {
    const { value } = await ElMessageBox.prompt('请输入升级原因', '升级工单', {
      inputPlaceholder: '例如：患者情绪激动，需主管介入',
      inputValidator: (input) => (input && input.trim() ? true : '升级原因不能为空')
    })
    await escalateComplaintTicket(ticket.id, value.trim())
    ElMessage.success('工单已升级')
    await afterMutation(ticket.id)
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') {
      ElMessage.error((error as Error).message)
    }
  }
}

async function handleCancel(ticket: ComplaintTicket) {
  try {
    const { value } = await ElMessageBox.prompt('请输入取消原因', '取消工单', {
      inputPlaceholder: '例如：重复投诉，已合并处理',
      inputValidator: (input) => (input && input.trim() ? true : '取消原因不能为空')
    })
    await cancelComplaintTicket(ticket.id, value.trim())
    ElMessage.success('工单已取消')
    await afterMutation(ticket.id)
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') {
      ElMessage.error((error as Error).message)
    }
  }
}

onMounted(async () => {
  await Promise.all([loadBase(), loadTickets()])
})
</script>

<template>
  <section class="complaint-page">
    <header class="page-head">
      <div>
        <h2>投诉表扬中心</h2>
        <p class="page-desc">
          投诉、表扬与建议统一建单，按严重程度设定处理时限；处理与结案审核分权，全部流转写入时间线，超时未结案自动升级。
        </p>
      </div>
    </header>

    <div class="toolbar">
      <el-input v-model="query.keyword" placeholder="工单号 / 标题" clearable style="width: 170px" />
      <el-select v-model="query.ticketType" placeholder="全部类型" clearable style="width: 110px">
        <el-option v-for="(label, key) in typeLabels" :key="key" :label="label" :value="key" />
      </el-select>
      <el-select v-model="query.severity" placeholder="全部等级" clearable style="width: 110px">
        <el-option v-for="(label, key) in severityLabels" :key="key" :label="label" :value="key" />
      </el-select>
      <el-select v-model="query.status" placeholder="全部状态" clearable style="width: 120px">
        <el-option v-for="(label, key) in statusLabels" :key="key" :label="label" :value="key" />
      </el-select>
      <el-checkbox v-model="query.overdue">仅看超期</el-checkbox>
      <el-checkbox v-model="query.escalated">仅看升级</el-checkbox>
      <el-button type="primary" @click="query.page = 1; loadTickets()">查询</el-button>
      <el-button v-if="canWrite" @click="openCreate">新建工单</el-button>
      <el-button v-if="canManage" @click="handleScan">超时扫描</el-button>
    </div>

    <el-table v-loading="loading" :data="rows" border stripe>
      <el-table-column prop="ticketNo" label="工单号" width="120" />
      <el-table-column prop="customerName" label="客户" min-width="100" />
      <el-table-column label="类型" width="80">
        <template #default="{ row }">{{ typeLabels[row.ticketType] || row.ticketType }}</template>
      </el-table-column>
      <el-table-column label="等级" width="80">
        <template #default="{ row }">
          <el-tag :type="severityTag(row.severity)" effect="plain">
            {{ severityLabels[row.severity] || row.severity }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="subject" label="标题" min-width="180" show-overflow-tooltip />
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="statusTag(row.status)">{{ statusLabels[row.status] || row.status }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="assigneeName" label="处理人" width="100">
        <template #default="{ row }">{{ row.assigneeName || '未分派' }}</template>
      </el-table-column>
      <el-table-column label="处理时限" width="150">
        <template #default="{ row }">
          <span :class="{ 'overdue-text': row.overdue }">{{ formatTime(row.dueAt) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="升级" width="80">
        <template #default="{ row }">
          <el-tag v-if="row.escalated" type="danger" effect="plain">L{{ row.escalationLevel }}</el-tag>
          <span v-else>—</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="250" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openDetail(row)">详情</el-button>
          <el-button
            v-if="canAssign && ['OPEN', 'ASSIGNED'].includes(row.status)"
            link
            type="warning"
            @click="openAssign(row)"
          >分派</el-button>
          <el-button
            v-if="canWrite && row.status === 'ASSIGNED'"
            link
            type="success"
            @click="handleStart(row)"
          >开始处理</el-button>
          <el-button
            v-if="canReview && row.status === 'PENDING_REVIEW'"
            link
            type="success"
            @click="openReview(row.id)"
          >结案审核</el-button>
          <el-button
            v-if="canManage && !isTerminal(row.status)"
            link
            type="danger"
            @click="handleEscalate(row)"
          >升级</el-button>
          <el-button
            v-if="canManage && !isTerminal(row.status)"
            link
            type="info"
            @click="handleCancel(row)"
          >取消</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-pagination
      v-model:current-page="query.page"
      v-model:page-size="query.pageSize"
      :total="total"
      layout="total, prev, pager, next, sizes"
      @current-change="loadTickets"
      @size-change="query.page = 1; loadTickets()"
    />

    <!-- 新建工单 -->
    <el-dialog v-model="createVisible" title="新建投诉表扬工单" width="560px">
      <el-form label-width="90px">
        <el-form-item label="客户" required>
          <el-select
            v-model="createForm.customerId"
            filterable
            remote
            :remote-method="searchCustomers"
            style="width: 100%"
          >
            <el-option
              v-for="item in customerOptions"
              :key="item.id"
              :label="customerLabel(item)"
              :value="item.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="工单类型" required>
          <el-select v-model="createForm.ticketType" style="width: 100%">
            <el-option v-for="(label, key) in typeLabels" :key="key" :label="label" :value="key" />
          </el-select>
        </el-form-item>
        <el-form-item label="严重程度" required>
          <el-select v-model="createForm.severity" style="width: 100%">
            <el-option v-for="(label, key) in severityLabels" :key="key" :label="label" :value="key" />
          </el-select>
        </el-form-item>
        <el-form-item label="来源">
          <el-select v-model="createForm.source" style="width: 100%">
            <el-option v-for="(label, key) in sourceLabels" :key="key" :label="label" :value="key" />
          </el-select>
        </el-form-item>
        <el-form-item label="标题" required>
          <el-input v-model="createForm.subject" maxlength="200" show-word-limit />
        </el-form-item>
        <el-form-item label="详细描述" required>
          <el-input v-model="createForm.description" type="textarea" :rows="4" />
        </el-form-item>
        <el-form-item label="处理人">
          <el-select v-model="createForm.assigneeId" clearable style="width: 100%">
            <el-option
              v-for="item in users"
              :key="item.id"
              :label="item.displayName || item.username"
              :value="item.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="处理时限">
          <el-date-picker v-model="createForm.dueAt" type="datetime" style="width: 100%" />
        </el-form-item>
      </el-form>
      <p class="hint">未指定处理时限时按严重程度自动套用 SLA：紧急 4 小时、较高 24 小时、一般 48 小时、低 72 小时。</p>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" @click="submitCreate">创建</el-button>
      </template>
    </el-dialog>

    <!-- 分派 -->
    <el-dialog v-model="assignVisible" title="分派工单" width="440px">
      <el-form label-width="80px">
        <el-form-item label="处理人" required>
          <el-select v-model="assignForm.assigneeId" style="width: 100%">
            <el-option
              v-for="item in users"
              :key="item.id"
              :label="item.displayName || item.username"
              :value="item.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="处理时限">
          <el-date-picker v-model="assignForm.dueAt" type="datetime" style="width: 100%" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="assignVisible = false">取消</el-button>
        <el-button type="primary" @click="submitAssign">确认</el-button>
      </template>
    </el-dialog>

    <!-- 详情 -->
    <el-dialog v-model="detailVisible" title="工单详情" width="780px">
      <div v-loading="detailLoading">
        <template v-if="detail">
          <el-descriptions :column="3" border>
            <el-descriptions-item label="工单号">{{ detail.ticket.ticketNo }}</el-descriptions-item>
            <el-descriptions-item label="客户">
              {{ detail.ticket.customerName || '—' }}
              <span v-if="detail.ticket.phoneMasked" class="hint">（{{ detail.ticket.phoneMasked }}）</span>
            </el-descriptions-item>
            <el-descriptions-item label="类型">
              {{ typeLabels[detail.ticket.ticketType] || detail.ticket.ticketType }}
            </el-descriptions-item>
            <el-descriptions-item label="等级">
              {{ severityLabels[detail.ticket.severity] || detail.ticket.severity }}
            </el-descriptions-item>
            <el-descriptions-item label="状态">
              <el-tag :type="statusTag(detail.ticket.status)">
                {{ statusLabels[detail.ticket.status] || detail.ticket.status }}
              </el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="来源">
              {{ sourceLabels[detail.ticket.source] || detail.ticket.source }}
            </el-descriptions-item>
            <el-descriptions-item label="处理人">{{ detail.ticket.assigneeName || '未分派' }}</el-descriptions-item>
            <el-descriptions-item label="责任部门">{{ detail.ticket.responsibleOrgName || '—' }}</el-descriptions-item>
            <el-descriptions-item label="处理时限">
              <span :class="{ 'overdue-text': detail.ticket.overdue }">{{ formatTime(detail.ticket.dueAt) }}</span>
            </el-descriptions-item>
            <el-descriptions-item label="升级">
              <el-tag v-if="detail.ticket.escalated" type="danger" effect="plain">
                已升级 L{{ detail.ticket.escalationLevel }}
              </el-tag>
              <span v-else>未升级</span>
            </el-descriptions-item>
            <el-descriptions-item label="结案时间">{{ formatTime(detail.ticket.closedAt) }}</el-descriptions-item>
            <el-descriptions-item label="结案人">{{ detail.ticket.closedByName || '—' }}</el-descriptions-item>
          </el-descriptions>

          <div class="block">
            <h4>工单内容</h4>
            <p class="subject">{{ detail.ticket.subject }}</p>
            <p class="description">{{ detail.ticket.description || '—' }}</p>
            <p v-if="detail.ticket.escalationReason" class="hint">升级原因：{{ detail.ticket.escalationReason }}</p>
          </div>

          <div class="block">
            <h4>处理时间线</h4>
            <el-timeline v-if="detail.actions.length">
              <el-timeline-item
                v-for="action in detail.actions"
                :key="action.id"
                :timestamp="formatTime(action.createdAt)"
                placement="top"
              >
                <div class="timeline-head">
                  <el-tag size="small" effect="plain">
                    {{ actionTypeLabels[action.actionType] || action.actionType }}
                  </el-tag>
                  <span class="hint">{{ action.operatorName || '系统' }}</span>
                  <span v-if="action.fromStatus && action.toStatus && action.fromStatus !== action.toStatus" class="hint">
                    {{ statusLabels[action.fromStatus] || action.fromStatus }} →
                    {{ statusLabels[action.toStatus] || action.toStatus }}
                  </span>
                </div>
                <p class="timeline-content">{{ action.content || '—' }}</p>
              </el-timeline-item>
            </el-timeline>
            <p v-else class="hint">暂无处理记录</p>
          </div>

          <div class="block">
            <div class="block-head">
              <h4>整改任务</h4>
              <el-button
                v-if="canWrite && ['PROCESSING', 'RECTIFYING'].includes(detail.ticket.status)"
                size="small"
                @click="openRectification(detail.ticket.id)"
              >登记整改</el-button>
            </div>
            <el-table v-if="detail.rectifications.length" :data="detail.rectifications" border size="small">
              <el-table-column prop="content" label="整改措施" min-width="220" />
              <el-table-column label="状态" width="90">
                <template #default="{ row }">
                  <el-tag :type="row.status === 'VERIFIED' ? 'success' : row.status === 'SUBMITTED' ? 'warning' : 'info'">
                    {{ rectificationStatusLabels[row.status] || row.status }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column label="提交时间" width="150">
                <template #default="{ row }">{{ formatTime(row.submittedAt) }}</template>
              </el-table-column>
              <el-table-column label="操作" width="90">
                <template #default="{ row }">
                  <el-button
                    v-if="canWrite && row.status === 'PENDING'"
                    link
                    type="primary"
                    @click="submitRectificationItem(row.id)"
                  >提交</el-button>
                </template>
              </el-table-column>
            </el-table>
            <p v-else class="hint">暂无整改任务</p>
          </div>

          <div class="block">
            <div class="block-head">
              <h4>患者反馈</h4>
              <el-button
                v-if="canWrite && ['PROCESSING', 'RECTIFYING', 'PENDING_REVIEW', 'CLOSED'].includes(detail.ticket.status)"
                size="small"
                @click="openFeedback(detail.ticket.id)"
              >登记反馈</el-button>
            </div>
            <el-table v-if="detail.feedbacks.length" :data="detail.feedbacks" border size="small">
              <el-table-column prop="rating" label="评分" width="70" />
              <el-table-column label="满意度" width="90">
                <template #default="{ row }">
                  <el-tag :type="row.satisfied ? 'success' : 'danger'">{{ row.satisfied ? '满意' : '不满意' }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="comment" label="意见" min-width="220" />
              <el-table-column label="渠道" width="100">
                <template #default="{ row }">{{ sourceLabels[row.channel] || row.channel }}</template>
              </el-table-column>
              <el-table-column label="时间" width="150">
                <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
              </el-table-column>
            </el-table>
            <p v-else class="hint">暂无患者反馈</p>
          </div>

          <div v-if="detail.reviews.length" class="block">
            <h4>复核记录</h4>
            <el-table :data="detail.reviews" border size="small">
              <el-table-column label="结论" width="90">
                <template #default="{ row }">
                  <el-tag :type="row.decision === 'PASS' ? 'success' : 'danger'">
                    {{ row.decision === 'PASS' ? '通过' : '退回' }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="reviewerName" label="审核人" width="110" />
              <el-table-column prop="comment" label="意见" min-width="220" />
              <el-table-column label="时间" width="150">
                <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
              </el-table-column>
            </el-table>
          </div>

          <div v-if="canWrite && !isTerminal(detail.ticket.status)" class="block">
            <h4>登记处理动作</h4>
            <div class="action-form">
              <el-select v-model="actionForm.actionType" style="width: 140px">
                <el-option
                  v-for="(label, key) in actionTypeLabels"
                  :key="key"
                  :label="label"
                  :value="key"
                />
              </el-select>
              <el-input v-model="actionForm.content" type="textarea" :rows="2" placeholder="记录联系、调查、回复或处理结果" />
              <el-button type="primary" @click="submitAction">登记</el-button>
            </div>
          </div>
        </template>
      </div>
      <template #footer>
        <el-button @click="detailVisible = false">关闭</el-button>
        <template v-if="detail">
          <el-button
            v-if="canWrite && detail.ticket.status === 'ASSIGNED'"
            type="success"
            @click="handleStart(detail.ticket)"
          >开始处理</el-button>
          <el-button
            v-if="canWrite && ['PROCESSING', 'RECTIFYING'].includes(detail.ticket.status)"
            type="warning"
            @click="handleSubmitReview"
          >提交结案复核</el-button>
          <el-button
            v-if="canReview && detail.ticket.status === 'PENDING_REVIEW'"
            type="primary"
            @click="openReview(detail.ticket.id)"
          >结案审核</el-button>
        </template>
      </template>
    </el-dialog>

    <!-- 整改 -->
    <el-dialog v-model="rectificationVisible" title="登记整改任务" width="480px">
      <el-form label-width="90px">
        <el-form-item label="整改措施" required>
          <el-input v-model="rectificationForm.content" type="textarea" :rows="4" />
        </el-form-item>
        <el-form-item label="完成时限">
          <el-date-picker v-model="rectificationForm.dueAt" type="datetime" style="width: 100%" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="rectificationVisible = false">取消</el-button>
        <el-button type="primary" @click="submitRectification">保存</el-button>
      </template>
    </el-dialog>

    <!-- 患者反馈 -->
    <el-dialog v-model="feedbackVisible" title="登记患者反馈" width="460px">
      <el-form label-width="90px">
        <el-form-item label="满意度评分" required>
          <el-rate v-model="feedbackForm.rating" />
        </el-form-item>
        <el-form-item label="是否满意">
          <el-radio-group v-model="feedbackForm.satisfied">
            <el-radio :value="true">满意</el-radio>
            <el-radio :value="false">不满意</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="反馈渠道">
          <el-select v-model="feedbackForm.channel" style="width: 100%">
            <el-option label="手工录入" value="manual" />
            <el-option label="电话" value="phone" />
            <el-option label="在线" value="online" />
            <el-option label="H5" value="h5" />
            <el-option label="小程序" value="mini_program" />
          </el-select>
        </el-form-item>
        <el-form-item label="患者意见">
          <el-input v-model="feedbackForm.comment" type="textarea" :rows="3" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="feedbackVisible = false">取消</el-button>
        <el-button type="primary" @click="submitFeedback">保存</el-button>
      </template>
    </el-dialog>

    <!-- 结案审核 -->
    <el-dialog v-model="reviewVisible" title="结案审核" width="480px">
      <el-form label-width="90px">
        <el-form-item label="审核结论" required>
          <el-radio-group v-model="reviewForm.decision">
            <el-radio value="PASS">通过并结案</el-radio>
            <el-radio value="REJECT">退回重新处理</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="审核意见">
          <el-input v-model="reviewForm.comment" type="textarea" :rows="3" />
        </el-form-item>
      </el-form>
      <p class="hint">审核人不得为当前处理人；通过后整改任务自动标记为已核验。</p>
      <template #footer>
        <el-button @click="reviewVisible = false">取消</el-button>
        <el-button type="primary" @click="submitReview">提交</el-button>
      </template>
    </el-dialog>
  </section>
</template>

<style scoped>
.complaint-page {
  padding: 20px 24px 32px;
}

.page-head h2 {
  margin: 0 0 6px;
  font-size: 20px;
}

.page-desc {
  margin: 0 0 16px;
  color: var(--el-text-color-secondary);
  font-size: 13px;
  line-height: 1.6;
}

.toolbar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px;
  margin-bottom: 14px;
}

.hint {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.overdue-text {
  color: var(--el-color-danger);
  font-weight: 600;
}

.el-pagination {
  margin-top: 14px;
  justify-content: flex-end;
}

h4 {
  margin: 0 0 10px;
  font-size: 14px;
}

.block {
  margin-top: 18px;
}

.block-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
}

.block-head h4 {
  margin: 0;
}

.subject {
  margin: 0 0 6px;
  font-weight: 600;
}

.description {
  margin: 0;
  color: var(--el-text-color-regular);
  font-size: 13px;
  line-height: 1.7;
  white-space: pre-wrap;
}

.timeline-head {
  display: flex;
  align-items: center;
  gap: 8px;
}

.timeline-content {
  margin: 6px 0 0;
  font-size: 13px;
  color: var(--el-text-color-regular);
}

.action-form {
  display: flex;
  align-items: flex-start;
  gap: 10px;
}

.action-form .el-textarea {
  flex: 1;
}
</style>
