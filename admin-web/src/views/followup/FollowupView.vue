<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import { fetchUsers } from '@/api/users'
import { fetchCustomers } from '@/api/customers'
import {
  assignFollowupTask,
  cancelFollowupTask,
  changeFollowupPlanStatus,
  changeFollowupTemplateStatus,
  createFollowupCategory,
  createFollowupPlan,
  createFollowupTask,
  createFollowupTemplate,
  escalateFollowupTask,
  fetchFollowupCategories,
  fetchFollowupPlans,
  fetchFollowupTaskDetail,
  fetchFollowupTasks,
  fetchFollowupTemplates,
  resolveFollowupTask,
  startFollowupTask,
  submitFollowupRecord,
  triggerFollowup,
  updateFollowupCategory,
  updateFollowupPlan,
  updateFollowupTemplate
} from '@/api/followup'
import type { CustomerSummary } from '@/types/customer'
import type { UserResponse } from '@/types/system'
import type {
  FollowupCategory,
  FollowupChannel,
  FollowupPlan,
  FollowupTask,
  FollowupTaskDetail,
  FollowupTemplate,
  FollowupTriggerType
} from '@/types/followup'

const auth = useAuthStore()
const canWrite = computed(() => auth.has('crm:followup:write'))
const canManage = computed(() => auth.has('crm:followup:manage'))

const activeTab = ref('tasks')
const loading = ref(false)

const statusLabels: Record<string, string> = {
  PENDING: '待执行',
  IN_PROGRESS: '执行中',
  COMPLETED: '已完成',
  ESCALATED: '人工队列',
  CANCELLED: '已取消'
}

const riskLabels: Record<string, string> = { LOW: '低', MEDIUM: '中', HIGH: '高' }

const channelLabels: Record<string, string> = {
  phone: '电话',
  sms: '短信',
  mini_program: '小程序',
  h5: 'H5',
  ai: 'AI 外呼',
  manual: '手工'
}

const triggerLabels: Record<string, string> = {
  APPOINTMENT_COMPLETED: '预约完成',
  SERVICE_COMPLETED: '服务完成',
  ORDER_PAID: '订单支付',
  MANUAL: '手工'
}

function statusTag(status: string) {
  if (status === 'COMPLETED') return 'success'
  if (status === 'ESCALATED') return 'danger'
  if (status === 'CANCELLED') return 'info'
  if (status === 'IN_PROGRESS') return 'warning'
  return 'primary'
}

function riskTag(level?: string) {
  if (level === 'HIGH') return 'danger'
  if (level === 'MEDIUM') return 'warning'
  return 'info'
}

function formatTime(value?: string) {
  if (!value) return '—'
  return value.replace('T', ' ').slice(0, 16)
}

// ------------------------------------------------------------------
// 基础数据
// ------------------------------------------------------------------
const categories = ref<FollowupCategory[]>([])
const templates = ref<FollowupTemplate[]>([])
const plans = ref<FollowupPlan[]>([])
const users = ref<UserResponse[]>([])
const customerOptions = ref<CustomerSummary[]>([])

async function loadBase() {
  categories.value = await fetchFollowupCategories()
  templates.value = await fetchFollowupTemplates({})
  plans.value = await fetchFollowupPlans()
  const result = await fetchUsers({ page: 1, pageSize: 100 })
  users.value = result.items
}

async function reloadPlans() {
  plans.value = await fetchFollowupPlans()
}

async function reloadTemplates() {
  templates.value = await fetchFollowupTemplates({})
}

async function reloadCategories() {
  categories.value = await fetchFollowupCategories()
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
// 任务
// ------------------------------------------------------------------
const taskQuery = reactive({
  keyword: '',
  status: '',
  categoryCode: '',
  riskLevel: '',
  escalationRequired: false as boolean | undefined,
  page: 1,
  pageSize: 20
})
const taskRows = ref<FollowupTask[]>([])
const taskTotal = ref(0)

async function loadTasks() {
  loading.value = true
  try {
    const result = await fetchFollowupTasks({
      keyword: taskQuery.keyword || undefined,
      status: (taskQuery.status || undefined) as never,
      categoryCode: taskQuery.categoryCode || undefined,
      riskLevel: taskQuery.riskLevel || undefined,
      escalationRequired: taskQuery.escalationRequired,
      page: taskQuery.page,
      pageSize: taskQuery.pageSize
    })
    taskRows.value = result.items
    taskTotal.value = result.total
  } finally {
    loading.value = false
  }
}

const escalationQuery = reactive({ page: 1, pageSize: 20 })
const escalationRows = ref<FollowupTask[]>([])
const escalationTotal = ref(0)

async function loadEscalations() {
  const result = await fetchFollowupTasks({
    status: 'ESCALATED',
    escalationRequired: true,
    page: escalationQuery.page,
    pageSize: escalationQuery.pageSize
  })
  escalationRows.value = result.items
  escalationTotal.value = result.total
}

// 新建任务
const createVisible = ref(false)
const createForm = reactive({
  customerId: '',
  categoryCode: '',
  templateCode: '',
  channel: 'phone',
  plannedAt: '',
  assigneeId: ''
})

function openCreateTask() {
  createForm.customerId = ''
  createForm.categoryCode = ''
  createForm.templateCode = ''
  createForm.channel = 'phone'
  createForm.plannedAt = ''
  createForm.assigneeId = ''
  createVisible.value = true
  void searchCustomers('')
}

const templateOptions = computed(() =>
  templates.value.filter(item => !createForm.categoryCode || item.categoryCode === createForm.categoryCode)
)

async function submitCreateTask() {
  if (!createForm.customerId || !createForm.categoryCode) {
    ElMessage.warning('请选择客户与随访分类')
    return
  }
  try {
    await createFollowupTask({
      customerId: createForm.customerId,
      categoryCode: createForm.categoryCode,
      templateCode: createForm.templateCode || undefined,
      channel: createForm.channel as never,
      plannedAt: new Date(createForm.plannedAt || Date.now()).toISOString(),
      assigneeId: createForm.assigneeId || undefined
    })
    ElMessage.success('随访任务已创建')
    createVisible.value = false
    await loadTasks()
  } catch (error) {
    ElMessage.error((error as Error).message || '创建失败')
  }
}

// 事件触发
const triggerVisible = ref(false)
const triggerForm = reactive({
  triggerType: 'SERVICE_COMPLETED' as FollowupTriggerType,
  eventKey: '',
  customerId: ''
})

function openTrigger() {
  triggerForm.triggerType = 'SERVICE_COMPLETED'
  triggerForm.eventKey = ''
  triggerForm.customerId = ''
  triggerVisible.value = true
  void searchCustomers('')
}

async function submitTrigger() {
  if (!triggerForm.customerId || !triggerForm.eventKey) {
    ElMessage.warning('请选择客户并填写事件键')
    return
  }
  try {
    const result = await triggerFollowup({ ...triggerForm })
    ElMessage.success(`触发完成：新建 ${result.created} 个，跳过 ${result.skipped} 个`)
    triggerVisible.value = false
    await loadTasks()
  } catch (error) {
    ElMessage.error((error as Error).message || '触发失败')
  }
}

// 分配
const assignVisible = ref(false)
const assignForm = reactive({ taskId: '', assigneeId: '' })

function openAssign(row: FollowupTask) {
  assignForm.taskId = row.id
  assignForm.assigneeId = row.assigneeId || ''
  assignVisible.value = true
}

async function submitAssign() {
  if (!assignForm.assigneeId) {
    ElMessage.warning('请选择随访人')
    return
  }
  try {
    await assignFollowupTask(assignForm.taskId, assignForm.assigneeId)
    ElMessage.success('已分配')
    assignVisible.value = false
    await loadTasks()
  } catch (error) {
    ElMessage.error((error as Error).message || '分配失败')
  }
}

// 详情与结果提交
const detailVisible = ref(false)
const detail = ref<FollowupTaskDetail | null>(null)
const answerForm = reactive<Record<string, string>>({})
const recordForm = reactive({ resultCode: 'DONE', remark: '' })

async function openDetail(row: FollowupTask) {
  const result = await fetchFollowupTaskDetail(row.id)
  detail.value = result
  Object.keys(answerForm).forEach(key => delete answerForm[key])
  recordForm.resultCode = 'DONE'
  recordForm.remark = ''
  detailVisible.value = true
}

function questionOptions(question: Record<string, unknown>): string[] {
  const options = question.options
  return Array.isArray(options) ? options.map(item => String(item)) : []
}

async function submitRecord() {
  if (!detail.value) return
  try {
    await submitFollowupRecord(detail.value.task.id, {
      answers: { ...answerForm },
      resultCode: recordForm.resultCode,
      remark: recordForm.remark || undefined
    })
    ElMessage.success('随访结果已提交')
    detailVisible.value = false
    await Promise.all([loadTasks(), loadEscalations()])
  } catch (error) {
    ElMessage.error((error as Error).message || '提交失败')
  }
}

async function handleStart(row: FollowupTask) {
  try {
    await startFollowupTask(row.id)
    ElMessage.success('已开始随访')
    await loadTasks()
  } catch (error) {
    ElMessage.error((error as Error).message || '操作失败')
  }
}

async function handleEscalate(row: FollowupTask) {
  const { value } = await ElMessageBox.prompt('请输入升级原因', `升级 ${row.taskNo}`)
  try {
    await escalateFollowupTask(row.id, value)
    ElMessage.success('已进入人工队列')
    await Promise.all([loadTasks(), loadEscalations()])
  } catch (error) {
    ElMessage.error((error as Error).message || '升级失败')
  }
}

const resolveVisible = ref(false)
const resolveForm = reactive({ taskId: '', remark: '' })

function openResolve(row: FollowupTask) {
  resolveForm.taskId = row.id
  resolveForm.remark = ''
  resolveVisible.value = true
}

async function submitResolve() {
  try {
    await resolveFollowupTask(resolveForm.taskId, resolveForm.remark || undefined)
    ElMessage.success('已处置')
    resolveVisible.value = false
    await Promise.all([loadTasks(), loadEscalations()])
  } catch (error) {
    ElMessage.error((error as Error).message || '处置失败')
  }
}

async function handleCancel(row: FollowupTask) {
  const { value } = await ElMessageBox.prompt('请输入取消原因', `取消 ${row.taskNo}`)
  try {
    await cancelFollowupTask(row.id, value)
    ElMessage.success('已取消')
    await Promise.all([loadTasks(), loadEscalations()])
  } catch (error) {
    ElMessage.error((error as Error).message || '取消失败')
  }
}

// ------------------------------------------------------------------
// 计划
// ------------------------------------------------------------------
const planVisible = ref(false)
const planForm = reactive({
  planNo: '',
  planName: '',
  categoryCode: '',
  templateCode: '',
  triggerType: 'SERVICE_COMPLETED' as FollowupTriggerType,
  offsetDays: 1,
  channel: 'phone',
  defaultAssigneeId: '',
  description: ''
})

function openPlanDialog(row?: FollowupPlan) {
  planForm.planNo = row?.planNo || ''
  planForm.planName = row?.planName || ''
  planForm.categoryCode = row?.categoryCode || ''
  planForm.templateCode = row?.templateCode || ''
  planForm.triggerType = row?.triggerType || 'SERVICE_COMPLETED'
  planForm.offsetDays = row?.offsetDays ?? 1
  planForm.channel = row?.channel || 'phone'
  planForm.defaultAssigneeId = row?.defaultAssigneeId || ''
  planForm.description = row?.description || ''
  planVisible.value = true
}

async function submitPlan() {
  const payload = {
    planName: planForm.planName,
    categoryCode: planForm.categoryCode,
    templateCode: planForm.templateCode,
    triggerType: planForm.triggerType,
    offsetDays: planForm.offsetDays,
    channel: planForm.channel as FollowupChannel,
    defaultAssigneeId: planForm.defaultAssigneeId || undefined,
    description: planForm.description || undefined
  }
  try {
    if (planForm.planNo) {
      await updateFollowupPlan(planForm.planNo, payload)
    } else {
      await createFollowupPlan(payload)
    }
    ElMessage.success('随访计划已保存')
    planVisible.value = false
    await reloadPlans()
  } catch (error) {
    ElMessage.error((error as Error).message || '保存失败')
  }
}

async function togglePlan(row: FollowupPlan) {
  await changeFollowupPlanStatus(row.planNo, row.status === 'active' ? 'disabled' : 'active')
  ElMessage.success('计划状态已更新')
  await reloadPlans()
}

// ------------------------------------------------------------------
// 模板
// ------------------------------------------------------------------
const templateVisible = ref(false)
const templateForm = reactive({
  templateCode: '',
  categoryCode: '',
  templateName: '',
  channel: 'phone',
  questionsJson: '[]',
  riskRulesJson: '{}',
  description: ''
})

function openTemplateDialog(row?: FollowupTemplate) {
  templateForm.templateCode = row?.templateCode || ''
  templateForm.categoryCode = row?.categoryCode || ''
  templateForm.templateName = row?.templateName || ''
  templateForm.channel = row?.channel || 'phone'
  templateForm.questionsJson = row ? JSON.stringify(row.questions, null, 2) : '[]'
  templateForm.riskRulesJson = row ? JSON.stringify(row.riskRules, null, 2) : '{}'
  templateForm.description = row?.description || ''
  templateVisible.value = true
}

async function submitTemplate() {
  let questions: Array<Record<string, unknown>>
  let riskRules: Record<string, unknown>
  try {
    questions = JSON.parse(templateForm.questionsJson)
    riskRules = JSON.parse(templateForm.riskRulesJson)
  } catch {
    ElMessage.error('问题或风险规则不是合法的 JSON')
    return
  }
  const payload = {
    templateName: templateForm.templateName,
    channel: templateForm.channel as FollowupChannel,
    questions,
    riskRules,
    description: templateForm.description || undefined
  }
  try {
    if (templateForm.templateCode) {
      await updateFollowupTemplate(templateForm.templateCode, payload)
    } else {
      await createFollowupTemplate({
        templateCode: templateForm.templateCode,
        categoryCode: templateForm.categoryCode,
        ...payload
      })
    }
    ElMessage.success('随访模板已保存')
    templateVisible.value = false
    await reloadTemplates()
  } catch (error) {
    ElMessage.error((error as Error).message || '保存失败')
  }
}

async function toggleTemplate(row: FollowupTemplate) {
  await changeFollowupTemplateStatus(row.templateCode, row.status === 'active' ? 'disabled' : 'active')
  ElMessage.success('模板状态已更新')
  await reloadTemplates()
}

// ------------------------------------------------------------------
// 分类
// ------------------------------------------------------------------
const categoryVisible = ref(false)
const categoryForm = reactive({ categoryCode: '', categoryName: '', description: '', sortNo: 0 })

function openCategoryDialog(row?: FollowupCategory) {
  categoryForm.categoryCode = row?.categoryCode || ''
  categoryForm.categoryName = row?.categoryName || ''
  categoryForm.description = row?.description || ''
  categoryForm.sortNo = row?.sortNo ?? 0
  categoryVisible.value = true
}

async function submitCategory() {
  const payload = {
    categoryName: categoryForm.categoryName,
    description: categoryForm.description || undefined,
    sortNo: categoryForm.sortNo
  }
  try {
    if (categoryForm.categoryCode) {
      await updateFollowupCategory(categoryForm.categoryCode, payload)
    } else {
      await createFollowupCategory({ categoryCode: categoryForm.categoryCode, ...payload })
    }
    ElMessage.success('随访分类已保存')
    categoryVisible.value = false
    await reloadCategories()
  } catch (error) {
    ElMessage.error((error as Error).message || '保存失败')
  }
}

// ------------------------------------------------------------------
// 初始化
// ------------------------------------------------------------------
function handleTabChange(name: string) {
  if (name === 'tasks') void loadTasks()
  if (name === 'escalations') void loadEscalations()
}

onMounted(async () => {
  await loadBase()
  await loadTasks()
})
</script>

<template>
  <section class="followup-page">
    <header class="page-head">
      <div>
        <h2>随访中心</h2>
        <p class="page-desc">
          按分类模板与计划生成随访任务，事件重复投递不会重复建单；随访答案按风险规则评估，高风险自动进入人工队列。
        </p>
      </div>
    </header>

    <el-tabs v-model="activeTab" @tab-change="handleTabChange">
      <!-- 任务 -->
      <el-tab-pane label="随访任务" name="tasks">
        <div class="toolbar">
          <el-input v-model="taskQuery.keyword" placeholder="任务号" clearable style="width: 150px" />
          <el-select v-model="taskQuery.status" placeholder="全部状态" clearable style="width: 130px">
            <el-option v-for="(label, key) in statusLabels" :key="key" :label="label" :value="key" />
          </el-select>
          <el-select v-model="taskQuery.categoryCode" placeholder="全部分类" clearable style="width: 140px">
            <el-option v-for="item in categories" :key="item.categoryCode" :label="item.categoryName" :value="item.categoryCode" />
          </el-select>
          <el-select v-model="taskQuery.riskLevel" placeholder="全部风险" clearable style="width: 120px">
            <el-option label="低风险" value="LOW" />
            <el-option label="中风险" value="MEDIUM" />
            <el-option label="高风险" value="HIGH" />
          </el-select>
          <el-button type="primary" @click="taskQuery.page = 1; loadTasks()">查询</el-button>
          <el-button v-if="canWrite" @click="openCreateTask">新建任务</el-button>
          <el-button v-if="canWrite" @click="openTrigger">事件触发</el-button>
        </div>

        <el-table v-loading="loading" :data="taskRows" border stripe>
          <el-table-column prop="taskNo" label="任务号" width="120" />
          <el-table-column prop="customerName" label="客户" min-width="110" />
          <el-table-column prop="phoneMasked" label="手机号" width="130" />
          <el-table-column prop="categoryName" label="分类" width="110" />
          <el-table-column label="渠道" width="90">
            <template #default="{ row }">{{ channelLabels[row.channel] || row.channel }}</template>
          </el-table-column>
          <el-table-column label="计划时间" width="150">
            <template #default="{ row }">{{ formatTime(row.plannedAt) }}</template>
          </el-table-column>
          <el-table-column label="状态" width="100">
            <template #default="{ row }">
              <el-tag :type="statusTag(row.status)">{{ statusLabels[row.status] || row.status }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="风险" width="80">
            <template #default="{ row }">
              <el-tag v-if="row.riskLevel" :type="riskTag(row.riskLevel)">{{ riskLabels[row.riskLevel] }}</el-tag>
              <span v-else>—</span>
            </template>
          </el-table-column>
          <el-table-column prop="assigneeName" label="随访人" width="100" />
          <el-table-column label="操作" width="290" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" @click="openDetail(row)">详情</el-button>
              <template v-if="canWrite && row.status === 'PENDING'">
                <el-button link type="success" @click="handleStart(row)">开始</el-button>
                <el-button link type="warning" @click="openAssign(row)">分配</el-button>
              </template>
              <el-button
                v-if="canWrite && ['PENDING', 'IN_PROGRESS'].includes(row.status)"
                link
                type="success"
                @click="openDetail(row)"
              >填结果</el-button>
              <el-button
                v-if="canManage && ['PENDING', 'IN_PROGRESS'].includes(row.status)"
                link
                type="danger"
                @click="handleEscalate(row)"
              >升级</el-button>
              <el-button
                v-if="canManage && ['PENDING', 'IN_PROGRESS', 'ESCALATED'].includes(row.status)"
                link
                type="info"
                @click="handleCancel(row)"
              >取消</el-button>
            </template>
          </el-table-column>
        </el-table>

        <el-pagination
          v-model:current-page="taskQuery.page"
          v-model:page-size="taskQuery.pageSize"
          :total="taskTotal"
          layout="total, prev, pager, next, sizes"
          @current-change="loadTasks"
          @size-change="taskQuery.page = 1; loadTasks()"
        />
      </el-tab-pane>

      <!-- 人工队列 -->
      <el-tab-pane label="人工队列" name="escalations">
        <div class="toolbar">
          <el-button type="primary" @click="loadEscalations">刷新</el-button>
          <span class="hint">高风险随访与手动升级的任务集中在此，由随访主管处置。</span>
        </div>
        <el-table :data="escalationRows" border stripe empty-text="暂无待处置任务">
          <el-table-column prop="taskNo" label="任务号" width="120" />
          <el-table-column prop="customerName" label="客户" min-width="110" />
          <el-table-column prop="phoneMasked" label="手机号" width="130" />
          <el-table-column prop="categoryName" label="分类" width="110" />
          <el-table-column label="风险" width="80">
            <template #default="{ row }">
              <el-tag :type="riskTag(row.riskLevel)">{{ riskLabels[row.riskLevel] || '—' }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="进入时间" width="150">
            <template #default="{ row }">{{ formatTime(row.completedAt || row.plannedAt) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="160" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" @click="openDetail(row)">详情</el-button>
              <el-button v-if="canManage" link type="success" @click="openResolve(row)">处置</el-button>
            </template>
          </el-table-column>
        </el-table>
        <el-pagination
          v-model:current-page="escalationQuery.page"
          v-model:page-size="escalationQuery.pageSize"
          :total="escalationTotal"
          layout="total, prev, pager, next"
          @current-change="loadEscalations"
        />
      </el-tab-pane>

      <!-- 计划 -->
      <el-tab-pane label="随访计划" name="plans">
        <div class="toolbar">
          <el-button type="primary" @click="reloadPlans">刷新</el-button>
          <el-button v-if="canManage" @click="openPlanDialog()">新建计划</el-button>
        </div>
        <el-table :data="plans" border stripe>
          <el-table-column prop="planNo" label="计划号" width="110" />
          <el-table-column prop="planName" label="计划名称" min-width="160" />
          <el-table-column prop="categoryName" label="分类" width="110" />
          <el-table-column label="触发事件" width="110">
            <template #default="{ row }">{{ triggerLabels[row.triggerType] || row.triggerType }}</template>
          </el-table-column>
          <el-table-column label="偏移" width="80">
            <template #default="{ row }">+{{ row.offsetDays }} 天</template>
          </el-table-column>
          <el-table-column label="渠道" width="90">
            <template #default="{ row }">{{ channelLabels[row.channel] || row.channel }}</template>
          </el-table-column>
          <el-table-column prop="defaultAssigneeName" label="默认随访人" width="110" />
          <el-table-column label="状态" width="90">
            <template #default="{ row }">
              <el-tag :type="row.status === 'active' ? 'success' : 'info'">
                {{ row.status === 'active' ? '启用' : '停用' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="150" fixed="right">
            <template #default="{ row }">
              <el-button v-if="canManage" link type="primary" @click="openPlanDialog(row)">编辑</el-button>
              <el-button v-if="canManage" link type="warning" @click="togglePlan(row)">
                {{ row.status === 'active' ? '停用' : '启用' }}
              </el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <!-- 模板 -->
      <el-tab-pane label="随访模板" name="templates">
        <div class="toolbar">
          <el-button type="primary" @click="reloadTemplates">刷新</el-button>
          <el-button v-if="canManage" @click="openTemplateDialog()">新建模板</el-button>
        </div>
        <el-table :data="templates" border stripe>
          <el-table-column prop="templateCode" label="模板编码" width="160" />
          <el-table-column prop="templateName" label="模板名称" min-width="160" />
          <el-table-column prop="categoryCode" label="分类" width="130" />
          <el-table-column label="渠道" width="90">
            <template #default="{ row }">{{ channelLabels[row.channel] || row.channel }}</template>
          </el-table-column>
          <el-table-column label="问题数" width="90">
            <template #default="{ row }">{{ row.questions?.length || 0 }}</template>
          </el-table-column>
          <el-table-column prop="version" label="版本" width="80" />
          <el-table-column label="状态" width="90">
            <template #default="{ row }">
              <el-tag :type="row.status === 'active' ? 'success' : 'info'">
                {{ row.status === 'active' ? '启用' : '停用' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="150" fixed="right">
            <template #default="{ row }">
              <el-button v-if="canManage" link type="primary" @click="openTemplateDialog(row)">编辑</el-button>
              <el-button v-if="canManage" link type="warning" @click="toggleTemplate(row)">
                {{ row.status === 'active' ? '停用' : '启用' }}
              </el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <!-- 分类 -->
      <el-tab-pane label="随访分类" name="categories">
        <div class="toolbar">
          <el-button type="primary" @click="reloadCategories">刷新</el-button>
          <el-button v-if="canManage" @click="openCategoryDialog()">新建分类</el-button>
        </div>
        <el-table :data="categories" border stripe>
          <el-table-column prop="categoryCode" label="分类编码" width="160" />
          <el-table-column prop="categoryName" label="分类名称" min-width="150" />
          <el-table-column prop="description" label="说明" min-width="220" />
          <el-table-column prop="sortNo" label="排序" width="80" />
          <el-table-column label="状态" width="90">
            <template #default="{ row }">
              <el-tag :type="row.status === 'active' ? 'success' : 'info'">
                {{ row.status === 'active' ? '启用' : '停用' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="90" fixed="right">
            <template #default="{ row }">
              <el-button v-if="canManage" link type="primary" @click="openCategoryDialog(row)">编辑</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>
    </el-tabs>

    <!-- 新建任务 -->
    <el-dialog v-model="createVisible" title="新建随访任务" width="500px">
      <el-form label-width="90px">
        <el-form-item label="客户" required>
          <el-select v-model="createForm.customerId" filterable remote :remote-method="searchCustomers" style="width: 100%">
            <el-option v-for="item in customerOptions" :key="item.id" :label="customerLabel(item)" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="随访分类" required>
          <el-select v-model="createForm.categoryCode" style="width: 100%">
            <el-option v-for="item in categories" :key="item.categoryCode" :label="item.categoryName" :value="item.categoryCode" />
          </el-select>
        </el-form-item>
        <el-form-item label="随访模板">
          <el-select v-model="createForm.templateCode" clearable style="width: 100%">
            <el-option v-for="item in templateOptions" :key="item.templateCode" :label="item.templateName" :value="item.templateCode" />
          </el-select>
        </el-form-item>
        <el-form-item label="随访渠道">
          <el-select v-model="createForm.channel" style="width: 100%">
            <el-option v-for="(label, key) in channelLabels" :key="key" :label="label" :value="key" />
          </el-select>
        </el-form-item>
        <el-form-item label="计划时间">
          <el-date-picker v-model="createForm.plannedAt" type="datetime" style="width: 100%" />
        </el-form-item>
        <el-form-item label="随访人">
          <el-select v-model="createForm.assigneeId" clearable style="width: 100%">
            <el-option v-for="item in users" :key="item.id" :label="item.displayName || item.username" :value="item.id" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" @click="submitCreateTask">创建</el-button>
      </template>
    </el-dialog>

    <!-- 事件触发 -->
    <el-dialog v-model="triggerVisible" title="事件触发随访" width="500px">
      <el-form label-width="90px">
        <el-form-item label="触发事件" required>
          <el-select v-model="triggerForm.triggerType" style="width: 100%">
            <el-option v-for="(label, key) in triggerLabels" :key="key" :label="label" :value="key" />
          </el-select>
        </el-form-item>
        <el-form-item label="事件键" required>
          <el-input v-model="triggerForm.eventKey" placeholder="同一事件重复提交不会重复建单" />
        </el-form-item>
        <el-form-item label="客户" required>
          <el-select v-model="triggerForm.customerId" filterable remote :remote-method="searchCustomers" style="width: 100%">
            <el-option v-for="item in customerOptions" :key="item.id" :label="customerLabel(item)" :value="item.id" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="triggerVisible = false">取消</el-button>
        <el-button type="primary" @click="submitTrigger">触发</el-button>
      </template>
    </el-dialog>

    <!-- 分配 -->
    <el-dialog v-model="assignVisible" title="分配随访人" width="420px">
      <el-form label-width="80px">
        <el-form-item label="随访人" required>
          <el-select v-model="assignForm.assigneeId" style="width: 100%">
            <el-option v-for="item in users" :key="item.id" :label="item.displayName || item.username" :value="item.id" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="assignVisible = false">取消</el-button>
        <el-button type="primary" @click="submitAssign">确认</el-button>
      </template>
    </el-dialog>

    <!-- 详情与结果 -->
    <el-dialog v-model="detailVisible" title="随访任务详情" width="640px">
      <template v-if="detail">
        <el-descriptions :column="3" border>
          <el-descriptions-item label="任务号">{{ detail.task.taskNo }}</el-descriptions-item>
          <el-descriptions-item label="客户">{{ detail.task.customerName || '—' }}</el-descriptions-item>
          <el-descriptions-item label="分类">{{ detail.task.categoryName || detail.task.categoryCode }}</el-descriptions-item>
          <el-descriptions-item label="状态">{{ statusLabels[detail.task.status] }}</el-descriptions-item>
          <el-descriptions-item label="风险">
            {{ detail.task.riskLevel ? riskLabels[detail.task.riskLevel] : '—' }}
          </el-descriptions-item>
          <el-descriptions-item label="随访人">{{ detail.task.assigneeName || '未分配' }}</el-descriptions-item>
        </el-descriptions>

        <div v-if="detail.questions.length" class="answer-block">
          <h4>随访问卷</h4>
          <el-form label-width="120px">
            <el-form-item v-for="question in detail.questions" :key="String(question.code)" :label="String(question.label || question.code)">
              <el-select v-model="answerForm[String(question.code)]" :disabled="detail.task.status !== 'IN_PROGRESS' && detail.task.status !== 'PENDING'">
                <el-option v-for="option in questionOptions(question)" :key="option" :label="option" :value="option" />
              </el-select>
            </el-form-item>
            <el-form-item label="结果备注">
              <el-input v-model="recordForm.remark" type="textarea" :rows="2" />
            </el-form-item>
          </el-form>
          <el-button
            v-if="canWrite && ['PENDING', 'IN_PROGRESS'].includes(detail.task.status)"
            type="primary"
            @click="submitRecord"
          >提交随访结果</el-button>
        </div>

        <div v-if="detail.records.length" class="record-block">
          <h4>随访记录</h4>
          <el-table :data="detail.records" border size="small">
            <el-table-column label="风险" width="80">
              <template #default="{ row }">
                <el-tag :type="riskTag(row.riskLevel)">{{ riskLabels[row.riskLevel] }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="答案" min-width="220">
              <template #default="{ row }">{{ Object.entries(row.answers || {}).map(([k, v]) => `${k}:${v}`).join('，') }}</template>
            </el-table-column>
            <el-table-column prop="remark" label="备注" min-width="160" />
            <el-table-column label="时间" width="150">
              <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
            </el-table-column>
          </el-table>
        </div>
      </template>
    </el-dialog>

    <!-- 处置 -->
    <el-dialog v-model="resolveVisible" title="处置人工队列任务" width="460px">
      <el-input v-model="resolveForm.remark" type="textarea" :rows="3" placeholder="处置说明" />
      <template #footer>
        <el-button @click="resolveVisible = false">取消</el-button>
        <el-button type="primary" @click="submitResolve">确认处置</el-button>
      </template>
    </el-dialog>

    <!-- 计划表单 -->
    <el-dialog v-model="planVisible" :title="planForm.planNo ? '编辑随访计划' : '新建随访计划'" width="520px">
      <el-form label-width="100px">
        <el-form-item label="计划名称" required>
          <el-input v-model="planForm.planName" />
        </el-form-item>
        <el-form-item label="随访分类" required>
          <el-select v-model="planForm.categoryCode" :disabled="!!planForm.planNo" style="width: 100%">
            <el-option v-for="item in categories" :key="item.categoryCode" :label="item.categoryName" :value="item.categoryCode" />
          </el-select>
        </el-form-item>
        <el-form-item label="随访模板" required>
          <el-select v-model="planForm.templateCode" :disabled="!!planForm.planNo" style="width: 100%">
            <el-option v-for="item in templates" :key="item.templateCode" :label="item.templateName" :value="item.templateCode" />
          </el-select>
        </el-form-item>
        <el-form-item label="触发事件" required>
          <el-select v-model="planForm.triggerType" style="width: 100%">
            <el-option v-for="(label, key) in triggerLabels" :key="key" :label="label" :value="key" />
          </el-select>
        </el-form-item>
        <el-form-item label="偏移天数" required>
          <el-input-number v-model="planForm.offsetDays" :min="0" :max="365" style="width: 100%" />
        </el-form-item>
        <el-form-item label="随访渠道">
          <el-select v-model="planForm.channel" style="width: 100%">
            <el-option v-for="(label, key) in channelLabels" :key="key" :label="label" :value="key" />
          </el-select>
        </el-form-item>
        <el-form-item label="默认随访人">
          <el-select v-model="planForm.defaultAssigneeId" clearable style="width: 100%">
            <el-option v-for="item in users" :key="item.id" :label="item.displayName || item.username" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="说明">
          <el-input v-model="planForm.description" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="planVisible = false">取消</el-button>
        <el-button type="primary" @click="submitPlan">保存</el-button>
      </template>
    </el-dialog>

    <!-- 模板表单 -->
    <el-dialog v-model="templateVisible" :title="templateForm.templateCode ? '编辑随访模板' : '新建随访模板'" width="640px">
      <el-form label-width="100px">
        <el-form-item label="模板编码" required>
          <el-input v-model="templateForm.templateCode" :disabled="!!templateForm.templateCode" />
        </el-form-item>
        <el-form-item label="随访分类" required>
          <el-select v-model="templateForm.categoryCode" :disabled="!!templateForm.templateCode" style="width: 100%">
            <el-option v-for="item in categories" :key="item.categoryCode" :label="item.categoryName" :value="item.categoryCode" />
          </el-select>
        </el-form-item>
        <el-form-item label="模板名称" required>
          <el-input v-model="templateForm.templateName" />
        </el-form-item>
        <el-form-item label="渠道">
          <el-select v-model="templateForm.channel" style="width: 100%">
            <el-option v-for="(label, key) in channelLabels" :key="key" :label="label" :value="key" />
          </el-select>
        </el-form-item>
        <el-form-item label="问题清单">
          <el-input v-model="templateForm.questionsJson" type="textarea" :rows="6" />
        </el-form-item>
        <el-form-item label="风险规则">
          <el-input v-model="templateForm.riskRulesJson" type="textarea" :rows="4" />
        </el-form-item>
        <el-form-item label="说明">
          <el-input v-model="templateForm.description" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="templateVisible = false">取消</el-button>
        <el-button type="primary" @click="submitTemplate">保存</el-button>
      </template>
    </el-dialog>

    <!-- 分类表单 -->
    <el-dialog v-model="categoryVisible" :title="categoryForm.categoryCode ? '编辑随访分类' : '新建随访分类'" width="460px">
      <el-form label-width="90px">
        <el-form-item label="分类编码" required>
          <el-input v-model="categoryForm.categoryCode" :disabled="!!categoryForm.categoryCode" />
        </el-form-item>
        <el-form-item label="分类名称" required>
          <el-input v-model="categoryForm.categoryName" />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="categoryForm.sortNo" :min="0" style="width: 100%" />
        </el-form-item>
        <el-form-item label="说明">
          <el-input v-model="categoryForm.description" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="categoryVisible = false">取消</el-button>
        <el-button type="primary" @click="submitCategory">保存</el-button>
      </template>
    </el-dialog>
  </section>
</template>

<style scoped>
.followup-page {
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

.el-pagination {
  margin-top: 14px;
  justify-content: flex-end;
}

h4 {
  margin: 18px 0 10px;
  font-size: 14px;
}

.answer-block,
.record-block {
  margin-top: 8px;
}
</style>
