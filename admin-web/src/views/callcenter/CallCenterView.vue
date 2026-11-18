<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import {
  createCallRecord,
  createSmsTemplate,
  dispatchMessages,
  fetchCallRecordDetail,
  fetchCallRecords,
  fetchIncomingPopup,
  fetchMessages,
  fetchRecordingAccess,
  fetchSmsTemplates,
  linkCallRecord,
  retryMessage,
  sendMessage,
  updateSmsTemplate,
  updateSmsTemplateStatus
} from '@/api/callcenter'
import type {
  CallDirection,
  CallLinkType,
  CallRecordDetailView,
  CallRecordStatus,
  CallRecordView,
  MessageChannel,
  MessageView,
  PopupView,
  RecordingStorageType,
  SendMessageRequest,
  SmsTemplateView
} from '@/types/callcenter'

const auth = useAuthStore()
const canCallWrite = computed(() => auth.has('crm:call:write'))
const canRecording = computed(() => auth.has('crm:call:recording:read'))
const canSmsRead = computed(() => auth.has('crm:sms:read'))
const canSmsSend = computed(() => auth.has('crm:sms:send'))
const canTemplateManage = computed(() => auth.has('crm:sms:template:manage'))

const activeTab = ref<'calls' | 'popup' | 'templates' | 'messages'>('calls')

// ------------------------------------------------------------------
// 文案与格式
// ------------------------------------------------------------------
const directionLabels: Record<CallDirection, string> = { inbound: '呼入', outbound: '呼出' }
const callStatusLabels: Record<CallRecordStatus, string> = {
  connected: '已接通',
  missed: '未接听',
  transferred: '已转接'
}
const linkTypeLabels: Record<CallLinkType, string> = {
  customer: '客户',
  complaint: '投诉工单',
  session: '客服会话',
  appointment: '预约',
  none: '未关联'
}
const sourceLabels: Record<string, string> = { vendor: '话务平台', manual: '手工补录' }
const storageLabels: Record<RecordingStorageType, string> = {
  vendor: '厂商存储',
  oss: '对象存储',
  local: '本地存储'
}
const channelLabels: Record<MessageChannel, string> = { sms: '短信', wechat: '微信', voice: '语音' }
const msgStatusLabels: Record<string, string> = {
  PENDING: '待发送',
  SENDING: '发送中',
  SENT: '已发送',
  FAILED: '发送失败',
  CANCELLED: '已取消'
}

function callStatusTag(status: CallRecordStatus) {
  if (status === 'connected') return 'success'
  if (status === 'missed') return 'danger'
  return 'warning'
}

function msgStatusTag(status: string) {
  if (status === 'SENT') return 'success'
  if (status === 'SENDING') return 'primary'
  if (status === 'FAILED') return 'danger'
  return 'info'
}

function formatTime(value?: string) {
  if (!value) return '—'
  return value.replace('T', ' ').slice(0, 16)
}

function formatDuration(seconds?: number) {
  if (!seconds) return '—'
  if (seconds < 60) return `${seconds} 秒`
  const minutes = Math.floor(seconds / 60)
  const rest = seconds % 60
  return rest ? `${minutes} 分 ${rest} 秒` : `${minutes} 分`
}

function formatSize(bytes?: number) {
  if (bytes === undefined || bytes === null) return '—'
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / 1024 / 1024).toFixed(1)} MB`
}

function genderLabel(gender?: string) {
  if (!gender) return '—'
  const map: Record<string, string> = { M: '男', F: '女', MALE: '男', FEMALE: '女' }
  return map[gender.toUpperCase()] ?? gender
}

function toInstant(value?: string) {
  if (!value) return undefined
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? undefined : date.toISOString()
}

// ------------------------------------------------------------------
// 通话记录
// ------------------------------------------------------------------
const calls = ref<CallRecordView[]>([])
const callsLoading = ref(false)
const callsTotal = ref(0)
const callsQuery = reactive({
  keyword: '',
  direction: '',
  status: '',
  dateRange: null as [string, string] | null,
  page: 1,
  pageSize: 20
})

async function loadCalls() {
  callsLoading.value = true
  try {
    const result = await fetchCallRecords({
      keyword: callsQuery.keyword || undefined,
      direction: callsQuery.direction || undefined,
      status: callsQuery.status || undefined,
      dateFrom: callsQuery.dateRange?.[0],
      dateTo: callsQuery.dateRange?.[1],
      page: callsQuery.page,
      pageSize: callsQuery.pageSize
    })
    calls.value = result.items
    callsTotal.value = result.total
  } catch (error) {
    ElMessage.error((error as Error).message)
  } finally {
    callsLoading.value = false
  }
}

function searchCalls() {
  callsQuery.page = 1
  void loadCalls()
}

// 详情抽屉
const detail = ref<CallRecordDetailView>()
const detailVisible = ref(false)
const detailLoading = ref(false)

async function openCallDetail(callId: string) {
  detailVisible.value = true
  detailLoading.value = true
  try {
    detail.value = await fetchCallRecordDetail(callId)
  } catch (error) {
    ElMessage.error((error as Error).message)
    detailVisible.value = false
  } finally {
    detailLoading.value = false
  }
}

// 关联工单 / 客户
const linkDialog = reactive({
  visible: false,
  callId: '',
  callNo: '',
  targetType: 'complaint' as CallLinkType,
  targetId: ''
})

function openLinkDialog(row: CallRecordView) {
  linkDialog.callId = row.id
  linkDialog.callNo = row.callNo
  linkDialog.targetType = 'complaint'
  linkDialog.targetId = ''
  linkDialog.visible = true
}

async function submitLink() {
  if (!linkDialog.targetId.trim()) {
    ElMessage.warning('请填写关联对象编号')
    return
  }
  try {
    await linkCallRecord(linkDialog.callId, {
      targetType: linkDialog.targetType,
      targetId: linkDialog.targetId.trim()
    })
    ElMessage.success('通话已关联')
    linkDialog.visible = false
    await loadCalls()
    if (detail.value?.call.id === linkDialog.callId) {
      await openCallDetail(linkDialog.callId)
    }
  } catch (error) {
    ElMessage.error((error as Error).message)
  }
}

// 获取录音
const recordingDialog = reactive({
  visible: false,
  callNo: '',
  storageType: '',
  accessUrl: '',
  note: ''
})

async function openRecording(row: CallRecordView) {
  try {
    const result = await fetchRecordingAccess(row.id)
    recordingDialog.callNo = result.callNo
    recordingDialog.storageType = storageLabels[result.storageType] ?? result.storageType
    recordingDialog.accessUrl = result.accessUrl ?? ''
    recordingDialog.note = result.note ?? ''
    recordingDialog.visible = true
  } catch (error) {
    ElMessage.error((error as Error).message)
  }
}

// 新建通话记录
const callForm = reactive({
  visible: false,
  direction: 'inbound' as CallDirection,
  status: 'connected' as CallRecordStatus,
  callerNumber: '',
  calleeNumber: '',
  startedAt: '',
  endedAt: '',
  customerId: '',
  queueName: '',
  vendorCallId: '',
  recordingKey: '',
  storageType: '' as '' | RecordingStorageType,
  linkType: '' as '' | CallLinkType,
  linkId: ''
})

function openCreateCall() {
  callForm.direction = 'inbound'
  callForm.status = 'connected'
  callForm.callerNumber = ''
  callForm.calleeNumber = ''
  callForm.startedAt = ''
  callForm.endedAt = ''
  callForm.customerId = ''
  callForm.queueName = ''
  callForm.vendorCallId = ''
  callForm.recordingKey = ''
  callForm.storageType = ''
  callForm.linkType = ''
  callForm.linkId = ''
  callForm.visible = true
}

async function submitCreateCall() {
  if (!callForm.startedAt) {
    ElMessage.warning('请选择通话开始时间')
    return
  }
  const startedInstant = toInstant(callForm.startedAt)
  if (!startedInstant) {
    ElMessage.warning('开始时间格式不正确')
    return
  }
  try {
    await createCallRecord({
      direction: callForm.direction,
      status: callForm.status,
      callerNumber: callForm.callerNumber.trim() || undefined,
      calleeNumber: callForm.calleeNumber.trim() || undefined,
      startedAt: startedInstant,
      endedAt: toInstant(callForm.endedAt),
      customerId: callForm.customerId.trim() || undefined,
      queueName: callForm.queueName.trim() || undefined,
      vendorCallId: callForm.vendorCallId.trim() || undefined,
      recordingKey: callForm.recordingKey.trim() || undefined,
      storageType: callForm.storageType || undefined,
      linkType: callForm.linkType || undefined,
      linkId: callForm.linkId.trim() || undefined
    })
    ElMessage.success('通话记录已补录')
    callForm.visible = false
    await loadCalls()
  } catch (error) {
    ElMessage.error((error as Error).message)
  }
}

// ------------------------------------------------------------------
// 来电弹屏
// ------------------------------------------------------------------
const popupPhone = ref('')
const popupLoading = ref(false)
const popupResult = ref<PopupView>()

async function queryPopup() {
  const phone = popupPhone.value.trim()
  if (!phone) {
    ElMessage.warning('请输入来电号码')
    return
  }
  popupLoading.value = true
  try {
    popupResult.value = await fetchIncomingPopup(phone)
  } catch (error) {
    ElMessage.error((error as Error).message)
    popupResult.value = undefined
  } finally {
    popupLoading.value = false
  }
}

// ------------------------------------------------------------------
// 短信模板
// ------------------------------------------------------------------
const templates = ref<SmsTemplateView[]>([])
const templatesLoading = ref(false)
const templatesTotal = ref(0)
const templatesQuery = reactive({ keyword: '', status: '', page: 1, pageSize: 20 })

async function loadTemplates() {
  templatesLoading.value = true
  try {
    const result = await fetchSmsTemplates({
      keyword: templatesQuery.keyword || undefined,
      status: templatesQuery.status || undefined,
      page: templatesQuery.page,
      pageSize: templatesQuery.pageSize
    })
    templates.value = result.items
    templatesTotal.value = result.total
  } catch (error) {
    ElMessage.error((error as Error).message)
  } finally {
    templatesLoading.value = false
  }
}

function searchTemplates() {
  templatesQuery.page = 1
  void loadTemplates()
}

const templateForm = reactive({
  visible: false,
  editingId: '',
  code: '',
  name: '',
  content: '',
  remark: ''
})

const extractedVars = computed(() => {
  const matches = templateForm.content.match(/\{([^{}]+)\}/g) ?? []
  return [...new Set(matches.map((item) => item.slice(1, -1)))]
})

function openTemplateDialog(row?: SmsTemplateView) {
  templateForm.editingId = row?.id ?? ''
  templateForm.code = row?.code ?? ''
  templateForm.name = row?.name ?? ''
  templateForm.content = row?.content ?? ''
  templateForm.remark = row?.remark ?? ''
  templateForm.visible = true
}

async function submitTemplate() {
  if (!templateForm.name.trim() || !templateForm.content.trim()) {
    ElMessage.warning('请填写模板名称与内容')
    return
  }
  try {
    if (templateForm.editingId) {
      await updateSmsTemplate(templateForm.editingId, {
        name: templateForm.name.trim(),
        content: templateForm.content,
        remark: templateForm.remark.trim() || undefined
      })
    } else {
      if (!templateForm.code.trim()) {
        ElMessage.warning('请填写模板编码')
        return
      }
      await createSmsTemplate({
        code: templateForm.code.trim(),
        name: templateForm.name.trim(),
        content: templateForm.content,
        remark: templateForm.remark.trim() || undefined
      })
    }
    ElMessage.success('模板已保存')
    templateForm.visible = false
    await loadTemplates()
  } catch (error) {
    ElMessage.error((error as Error).message)
  }
}

async function toggleTemplateStatus(row: SmsTemplateView) {
  const next = row.status === 'active' ? 'disabled' : 'active'
  try {
    await updateSmsTemplateStatus(row.id, next)
    ElMessage.success(next === 'active' ? '模板已启用' : '模板已停用')
    await loadTemplates()
  } catch (error) {
    ElMessage.error((error as Error).message)
  }
}

// ------------------------------------------------------------------
// 消息队列
// ------------------------------------------------------------------
const messages = ref<MessageView[]>([])
const messagesLoading = ref(false)
const messagesTotal = ref(0)
const messagesQuery = reactive({
  status: '',
  channel: '',
  scene: '',
  bizKey: '',
  keyword: '',
  page: 1,
  pageSize: 20
})

async function loadMessages() {
  messagesLoading.value = true
  try {
    const result = await fetchMessages({
      status: messagesQuery.status || undefined,
      channel: messagesQuery.channel || undefined,
      scene: messagesQuery.scene || undefined,
      bizKey: messagesQuery.bizKey || undefined,
      keyword: messagesQuery.keyword || undefined,
      page: messagesQuery.page,
      pageSize: messagesQuery.pageSize
    })
    messages.value = result.items
    messagesTotal.value = result.total
  } catch (error) {
    ElMessage.error((error as Error).message)
  } finally {
    messagesLoading.value = false
  }
}

function searchMessages() {
  messagesQuery.page = 1
  void loadMessages()
}

const dispatching = ref(false)

async function handleDispatch() {
  dispatching.value = true
  try {
    const result = await dispatchMessages()
    ElMessage.success(
      `派发完成：扫描 ${result.scanned} 条，发送 ${result.sent} 条，失败 ${result.failed} 条`
    )
    await loadMessages()
  } catch (error) {
    ElMessage.error((error as Error).message)
  } finally {
    dispatching.value = false
  }
}

async function handleRetry(row: MessageView) {
  try {
    await retryMessage(row.id)
    ElMessage.success('已提交重试')
    await loadMessages()
  } catch (error) {
    ElMessage.error((error as Error).message)
  }
}

// 新建消息
const messageForm = reactive({
  visible: false,
  channel: 'sms' as MessageChannel,
  templateCode: '',
  content: '',
  recipientPhone: '',
  scene: '',
  bizKey: '',
  customerId: ''
})
const templateOptions = ref<SmsTemplateView[]>([])
const messageVariables = ref<Record<string, string>>({})

const selectedTemplate = computed(() =>
  templateOptions.value.find((item) => item.code === messageForm.templateCode)
)

async function openMessageDialog() {
  messageForm.channel = 'sms'
  messageForm.templateCode = ''
  messageForm.content = ''
  messageForm.recipientPhone = ''
  messageForm.scene = ''
  messageForm.bizKey = ''
  messageForm.customerId = ''
  messageVariables.value = {}
  messageForm.visible = true
  if (!templateOptions.value.length) {
    try {
      const result = await fetchSmsTemplates({ status: 'active', page: 1, pageSize: 100 })
      templateOptions.value = result.items
    } catch (error) {
      ElMessage.error((error as Error).message)
    }
  }
}

function onTemplateChange() {
  messageVariables.value = {}
}

async function submitMessage() {
  if (!messageForm.recipientPhone.trim()) {
    ElMessage.warning('请填写接收号码')
    return
  }
  const template = selectedTemplate.value
  if (!template && !messageForm.content.trim()) {
    ElMessage.warning('请选择模板或填写自定义内容')
    return
  }
  const variables: Record<string, string> = {}
  for (const key of template?.variables ?? []) {
    const value = messageVariables.value[key]
    if (value !== undefined && value !== '') variables[key] = value
  }
  const payload: SendMessageRequest = {
    channel: messageForm.channel,
    scene: messageForm.scene.trim() || undefined,
    templateCode: template?.code,
    variables: template && Object.keys(variables).length ? variables : undefined,
    content: template ? undefined : messageForm.content.trim(),
    recipientPhone: messageForm.recipientPhone.trim(),
    customerId: messageForm.customerId.trim() || undefined,
    bizKey: messageForm.bizKey.trim() || undefined
  }
  try {
    const result = await sendMessage(payload)
    ElMessage.success(`消息已入队（${msgStatusLabels[result.status] ?? result.status}）`)
    messageForm.visible = false
    await loadMessages()
  } catch (error) {
    ElMessage.error((error as Error).message)
  }
}

// ------------------------------------------------------------------
// 标签页切换与初始化
// ------------------------------------------------------------------
function onTabChange(name: string | number) {
  if (name === 'templates' && !templates.value.length) {
    void loadTemplates()
  }
  if (name === 'messages' && !messages.value.length) {
    void loadMessages()
  }
}

onMounted(() => {
  void loadCalls()
})
</script>

<template>
  <section class="cc-page">
    <header class="page-head">
      <div>
        <h2>呼叫中心与短信</h2>
        <p class="page-desc">
          话务平台通话记录自动同步与手工补录，来电号码即时弹屏识别客户身份；短信模板统一管理变量，
          消息队列支持发送、失败重试与手动派发，录音按权限受控访问，全程号码脱敏、留痕可追溯。
        </p>
      </div>
    </header>

    <el-tabs v-model="activeTab" class="cc-tabs" @tab-change="onTabChange">
      <!-- ============================================================ -->
      <el-tab-pane label="通话记录" name="calls">
        <div class="toolbar">
          <el-input
            v-model="callsQuery.keyword"
            placeholder="编号 / 号码 / 客户"
            clearable
            size="small"
            style="width: 180px"
          />
          <el-select v-model="callsQuery.direction" placeholder="全部方向" clearable size="small" style="width: 110px">
            <el-option label="呼入" value="inbound" />
            <el-option label="呼出" value="outbound" />
          </el-select>
          <el-select v-model="callsQuery.status" placeholder="全部状态" clearable size="small" style="width: 110px">
            <el-option label="已接通" value="connected" />
            <el-option label="未接听" value="missed" />
            <el-option label="已转接" value="transferred" />
          </el-select>
          <el-date-picker
            v-model="callsQuery.dateRange"
            type="daterange"
            size="small"
            style="width: 240px"
            value-format="YYYY-MM-DD"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
          />
          <el-button size="small" type="primary" @click="searchCalls">查询</el-button>
          <span class="spacer" />
          <el-button
            v-if="canCallWrite"
            size="small"
            type="primary"
            @click="openCreateCall"
          >
            新建通话记录
          </el-button>
        </div>

        <el-table v-loading="callsLoading" :data="calls" size="small" border>
          <el-table-column prop="callNo" label="通话编号" width="130" />
          <el-table-column label="方向" width="76">
            <template #default="{ row }">
              <el-tag :type="row.direction === 'inbound' ? 'primary' : 'warning'" size="small">
                {{ directionLabels[row.direction as CallDirection] }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="号码（脱敏）" min-width="150">
            <template #default="{ row }">
              <span v-if="row.direction === 'inbound'">{{ row.callerMasked || '—' }} → 平台</span>
              <span v-else>平台 → {{ row.calleeMasked || '—' }}</span>
            </template>
          </el-table-column>
          <el-table-column label="客户" width="110">
            <template #default="{ row }">{{ row.customerName || '—' }}</template>
          </el-table-column>
          <el-table-column label="关联对象" width="130">
            <template #default="{ row }">
              <span v-if="row.linkType !== 'none'">
                {{ linkTypeLabels[row.linkType as CallLinkType] }}
                <span v-if="row.linkNo" class="hint"> · {{ row.linkNo }}</span>
              </span>
              <span v-else class="hint">未关联</span>
            </template>
          </el-table-column>
          <el-table-column label="坐席" width="100">
            <template #default="{ row }">{{ row.agentUserName || '—' }}</template>
          </el-table-column>
          <el-table-column label="状态" width="86">
            <template #default="{ row }">
              <el-tag :type="callStatusTag(row.status)" size="small">
                {{ callStatusLabels[row.status as CallRecordStatus] }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="时长" width="90">
            <template #default="{ row }">{{ formatDuration(row.durationSeconds) }}</template>
          </el-table-column>
          <el-table-column label="通话时间" width="140">
            <template #default="{ row }">{{ formatTime(row.startedAt) }}</template>
          </el-table-column>
          <el-table-column label="来源" width="86">
            <template #default="{ row }">{{ sourceLabels[row.source] ?? row.source }}</template>
          </el-table-column>
          <el-table-column label="操作" width="190" fixed="right">
            <template #default="{ row }">
              <el-button size="small" link type="primary" @click="openCallDetail(row.id)">详情</el-button>
              <el-button
                v-if="canCallWrite"
                size="small"
                link
                type="primary"
                @click="openLinkDialog(row)"
              >
                关联
              </el-button>
              <el-button
                v-if="canRecording && row.hasRecording"
                size="small"
                link
                type="success"
                @click="openRecording(row)"
              >
                录音
              </el-button>
            </template>
          </el-table-column>
        </el-table>
        <el-pagination
          v-if="callsTotal > callsQuery.pageSize"
          v-model:current-page="callsQuery.page"
          :page-size="callsQuery.pageSize"
          :total="callsTotal"
          layout="prev, pager, next"
          small
          @current-change="loadCalls"
        />
      </el-tab-pane>

      <!-- ============================================================ -->
      <el-tab-pane label="来电弹屏" name="popup">
        <div class="popup-panel">
          <div class="toolbar">
            <el-input
              v-model="popupPhone"
              placeholder="输入来电号码，如 13800001234"
              clearable
              size="small"
              style="width: 240px"
              @keyup.enter="queryPopup"
            />
            <el-button size="small" type="primary" :loading="popupLoading" @click="queryPopup">
              查询客户
            </el-button>
            <span class="hint">坐席接起来电后按主叫号码即时匹配客户档案与历史通话</span>
          </div>

          <div v-if="popupResult" class="popup-card">
            <template v-if="popupResult.matched && popupResult.customer">
              <div class="popup-head">
                <div class="popup-avatar">{{ popupResult.customer.name.slice(0, 1) }}</div>
                <div>
                  <div class="popup-name">
                    {{ popupResult.customer.name }}
                    <el-tag size="small" type="success">已匹配客户</el-tag>
                  </div>
                  <div class="hint">客户号 {{ popupResult.customer.customerNo }}</div>
                </div>
              </div>
              <div class="popup-fields">
                <div class="popup-field">
                  <span class="popup-label">性别</span>
                  <span>{{ genderLabel(popupResult.customer.gender) }}</span>
                </div>
                <div class="popup-field">
                  <span class="popup-label">生日</span>
                  <span>{{ popupResult.customer.birthDate || '—' }}</span>
                </div>
                <div class="popup-field">
                  <span class="popup-label">手机（脱敏）</span>
                  <span>{{ popupResult.customer.phoneMasked || '—' }}</span>
                </div>
                <div class="popup-field">
                  <span class="popup-label">客户状态</span>
                  <span>{{ popupResult.customer.status || '—' }}</span>
                </div>
              </div>
              <div class="popup-stats">
                <div class="stat-item">
                  <div class="stat-value">{{ popupResult.callHistoryCount }}</div>
                  <div class="hint">历史通话（次）</div>
                </div>
                <div class="stat-item">
                  <div class="stat-value">{{ formatTime(popupResult.lastCallAt) }}</div>
                  <div class="hint">最近一次通话</div>
                </div>
              </div>
            </template>
            <el-empty v-else description="未匹配到客户档案，可先登记线索或补录通话" />
          </div>
          <p v-else class="empty popup-empty">输入来电号码后此处展示客户弹屏卡片</p>
        </div>
      </el-tab-pane>

      <!-- ============================================================ -->
      <el-tab-pane v-if="canSmsRead" label="短信模板" name="templates">
        <div class="toolbar">
          <el-input
            v-model="templatesQuery.keyword"
            placeholder="编码 / 名称 / 内容"
            clearable
            size="small"
            style="width: 180px"
          />
          <el-select v-model="templatesQuery.status" placeholder="全部状态" clearable size="small" style="width: 110px">
            <el-option label="启用" value="active" />
            <el-option label="停用" value="disabled" />
          </el-select>
          <el-button size="small" type="primary" @click="searchTemplates">查询</el-button>
          <span class="spacer" />
          <el-button v-if="canTemplateManage" size="small" type="primary" @click="openTemplateDialog()">
            新建模板
          </el-button>
        </div>

        <el-table v-loading="templatesLoading" :data="templates" size="small" border>
          <el-table-column prop="code" label="编码" width="150" />
          <el-table-column prop="name" label="名称" min-width="140" show-overflow-tooltip />
          <el-table-column label="渠道" width="80">
            <template #default="{ row }">{{ channelLabels[row.channel as MessageChannel] ?? row.channel }}</template>
          </el-table-column>
          <el-table-column label="内容" min-width="220">
            <template #default="{ row }">
              <div class="tpl-content">{{ row.content }}</div>
            </template>
          </el-table-column>
          <el-table-column label="变量" min-width="150">
            <template #default="{ row }">
              <el-tag v-for="v in row.variables" :key="v" size="small" class="var-tag">{{ v }}</el-tag>
              <span v-if="!row.variables.length" class="hint">无</span>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="80">
            <template #default="{ row }">
              <el-tag :type="row.status === 'active' ? 'success' : 'info'" size="small">
                {{ row.status === 'active' ? '启用' : '停用' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="更新时间" width="140">
            <template #default="{ row }">{{ formatTime(row.updatedAt) }}</template>
          </el-table-column>
          <el-table-column v-if="canTemplateManage" label="操作" width="130" fixed="right">
            <template #default="{ row }">
              <el-button size="small" link type="primary" @click="openTemplateDialog(row)">编辑</el-button>
              <el-button
                size="small"
                link
                :type="row.status === 'active' ? 'danger' : 'success'"
                @click="toggleTemplateStatus(row)"
              >
                {{ row.status === 'active' ? '停用' : '启用' }}
              </el-button>
            </template>
          </el-table-column>
        </el-table>
        <el-pagination
          v-if="templatesTotal > templatesQuery.pageSize"
          v-model:current-page="templatesQuery.page"
          :page-size="templatesQuery.pageSize"
          :total="templatesTotal"
          layout="prev, pager, next"
          small
          @current-change="loadTemplates"
        />
      </el-tab-pane>

      <!-- ============================================================ -->
      <el-tab-pane v-if="canSmsRead" label="消息队列" name="messages">
        <div class="toolbar">
          <el-select v-model="messagesQuery.status" placeholder="全部状态" clearable size="small" style="width: 110px">
            <el-option v-for="(label, key) in msgStatusLabels" :key="key" :label="label" :value="key" />
          </el-select>
          <el-select v-model="messagesQuery.channel" placeholder="全部渠道" clearable size="small" style="width: 110px">
            <el-option label="短信" value="sms" />
            <el-option label="微信" value="wechat" />
            <el-option label="语音" value="voice" />
          </el-select>
          <el-input v-model="messagesQuery.scene" placeholder="场景" clearable size="small" style="width: 110px" />
          <el-input v-model="messagesQuery.bizKey" placeholder="业务键 bizKey" clearable size="small" style="width: 150px" />
          <el-input v-model="messagesQuery.keyword" placeholder="编号 / 客户" clearable size="small" style="width: 150px" />
          <el-button size="small" type="primary" @click="searchMessages">查询</el-button>
          <span class="spacer" />
          <template v-if="canSmsSend">
            <el-button size="small" :loading="dispatching" @click="handleDispatch">手动派发</el-button>
            <el-button size="small" type="primary" @click="openMessageDialog">新建消息</el-button>
          </template>
        </div>

        <el-table v-loading="messagesLoading" :data="messages" size="small" border>
          <el-table-column prop="msgNo" label="编号" width="130" />
          <el-table-column label="渠道" width="72">
            <template #default="{ row }">{{ channelLabels[row.channel as MessageChannel] }}</template>
          </el-table-column>
          <el-table-column label="收件（脱敏）" width="120">
            <template #default="{ row }">{{ row.recipientMasked || '—' }}</template>
          </el-table-column>
          <el-table-column label="内容摘要" min-width="220" show-overflow-tooltip>
            <template #default="{ row }">
              <div>{{ row.content }}</div>
              <div v-if="row.templateName" class="hint">模板：{{ row.templateName }}</div>
            </template>
          </el-table-column>
          <el-table-column label="场景" width="100">
            <template #default="{ row }">{{ row.scene || '—' }}</template>
          </el-table-column>
          <el-table-column label="状态" width="90">
            <template #default="{ row }">
              <el-tag :type="msgStatusTag(row.status)" size="small">{{ msgStatusLabels[row.status] }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="重试" width="80">
            <template #default="{ row }">{{ row.retryCount }} / {{ row.maxRetries }}</template>
          </el-table-column>
          <el-table-column label="下次重试" width="140">
            <template #default="{ row }">{{ formatTime(row.nextRetryAt) }}</template>
          </el-table-column>
          <el-table-column label="错误" min-width="120" show-overflow-tooltip>
            <template #default="{ row }">
              <span :class="row.lastError ? 'error-text' : 'hint'">{{ row.lastError || '—' }}</span>
            </template>
          </el-table-column>
          <el-table-column label="发送时间" width="140">
            <template #default="{ row }">{{ formatTime(row.sentAt) }}</template>
          </el-table-column>
          <el-table-column v-if="canSmsSend" label="操作" width="70" fixed="right">
            <template #default="{ row }">
              <el-button
                v-if="row.status === 'PENDING' || row.status === 'FAILED'"
                size="small"
                link
                type="primary"
                @click="handleRetry(row)"
              >
                重试
              </el-button>
            </template>
          </el-table-column>
        </el-table>
        <el-pagination
          v-if="messagesTotal > messagesQuery.pageSize"
          v-model:current-page="messagesQuery.page"
          :page-size="messagesQuery.pageSize"
          :total="messagesTotal"
          layout="prev, pager, next"
          small
          @current-change="loadMessages"
        />
      </el-tab-pane>
    </el-tabs>

    <!-- 新建通话记录 -->
    <el-dialog v-model="callForm.visible" title="新建通话记录" width="620px">
      <el-form label-width="96px">
        <el-form-item label="方向" required>
          <el-radio-group v-model="callForm.direction">
            <el-radio value="inbound">呼入</el-radio>
            <el-radio value="outbound">呼出</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="主叫号码">
          <el-input v-model="callForm.callerNumber" maxlength="32" placeholder="呼入时填客户来电号码" />
        </el-form-item>
        <el-form-item label="被叫号码">
          <el-input v-model="callForm.calleeNumber" maxlength="32" placeholder="呼出时填客户号码" />
        </el-form-item>
        <el-form-item label="开始时间" required>
          <el-date-picker
            v-model="callForm.startedAt"
            type="datetime"
            value-format="YYYY-MM-DDTHH:mm:ss"
            placeholder="通话开始时间"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="结束时间">
          <el-date-picker
            v-model="callForm.endedAt"
            type="datetime"
            value-format="YYYY-MM-DDTHH:mm:ss"
            placeholder="留空表示未接通"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="状态" required>
          <el-select v-model="callForm.status" style="width: 100%">
            <el-option label="已接通" value="connected" />
            <el-option label="未接听" value="missed" />
            <el-option label="已转接" value="transferred" />
          </el-select>
        </el-form-item>
        <el-form-item label="客户 ID">
          <el-input v-model="callForm.customerId" maxlength="64" placeholder="可选，关联客户档案" />
        </el-form-item>
        <el-form-item label="队列">
          <el-input v-model="callForm.queueName" maxlength="64" placeholder="如 客服一线队列" />
        </el-form-item>
        <el-form-item label="厂商通话号">
          <el-input v-model="callForm.vendorCallId" maxlength="64" placeholder="话务平台通话唯一标识（可选）" />
        </el-form-item>
        <el-form-item label="录音 Key">
          <el-input v-model="callForm.recordingKey" maxlength="128" placeholder="录音文件标识（可选）" />
        </el-form-item>
        <el-form-item label="录音存储">
          <el-select v-model="callForm.storageType" clearable placeholder="默认厂商存储" style="width: 100%">
            <el-option label="厂商存储" value="vendor" />
            <el-option label="对象存储" value="oss" />
            <el-option label="本地存储" value="local" />
          </el-select>
        </el-form-item>
        <el-form-item label="关联对象">
          <div class="inline-group">
            <el-select v-model="callForm.linkType" clearable placeholder="不关联" style="width: 140px">
              <el-option label="客户" value="customer" />
              <el-option label="投诉工单" value="complaint" />
              <el-option label="客服会话" value="session" />
              <el-option label="预约" value="appointment" />
            </el-select>
            <el-input v-model="callForm.linkId" maxlength="64" placeholder="对象 ID" style="flex: 1" />
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="callForm.visible = false">取消</el-button>
        <el-button type="primary" @click="submitCreateCall">保存</el-button>
      </template>
    </el-dialog>

    <!-- 关联工单 / 客户 -->
    <el-dialog v-model="linkDialog.visible" title="关联通话" width="440px">
      <el-form label-width="96px">
        <el-form-item label="通话编号">
          <span class="hint">{{ linkDialog.callNo }}</span>
        </el-form-item>
        <el-form-item label="关联类型" required>
          <el-select v-model="linkDialog.targetType" style="width: 100%">
            <el-option label="客户" value="customer" />
            <el-option label="投诉工单" value="complaint" />
            <el-option label="客服会话" value="session" />
            <el-option label="预约" value="appointment" />
          </el-select>
        </el-form-item>
        <el-form-item label="对象 ID" required>
          <el-input v-model="linkDialog.targetId" maxlength="64" placeholder="工单 / 客户 / 会话 / 预约编号" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="linkDialog.visible = false">取消</el-button>
        <el-button type="primary" @click="submitLink">确认关联</el-button>
      </template>
    </el-dialog>

    <!-- 录音访问 -->
    <el-dialog v-model="recordingDialog.visible" title="录音访问" width="480px">
      <el-descriptions :column="1" border size="small">
        <el-descriptions-item label="通话编号">{{ recordingDialog.callNo }}</el-descriptions-item>
        <el-descriptions-item label="存储方式">{{ recordingDialog.storageType }}</el-descriptions-item>
        <el-descriptions-item label="访问地址">
          <el-link
            v-if="recordingDialog.accessUrl"
            type="primary"
            :href="recordingDialog.accessUrl"
            target="_blank"
          >
            点击播放 / 下载录音
          </el-link>
          <span v-else class="hint">{{ recordingDialog.note || '暂无可用的录音访问地址' }}</span>
        </el-descriptions-item>
      </el-descriptions>
      <p class="hint recording-hint">录音访问按权限受控并计入访问次数，请勿外传访问链接。</p>
      <template #footer>
        <el-button @click="recordingDialog.visible = false">关闭</el-button>
      </template>
    </el-dialog>

    <!-- 通话详情抽屉 -->
    <el-drawer v-model="detailVisible" :title="`通话详情 · ${detail?.call.callNo || ''}`" size="560px">
      <div v-loading="detailLoading">
        <template v-if="detail">
          <el-descriptions :column="2" border size="small" title="通话信息">
            <el-descriptions-item label="方向">
              {{ directionLabels[detail.call.direction] }}
            </el-descriptions-item>
            <el-descriptions-item label="状态">
              {{ callStatusLabels[detail.call.status] }}
            </el-descriptions-item>
            <el-descriptions-item label="主叫（脱敏）">{{ detail.call.callerMasked || '—' }}</el-descriptions-item>
            <el-descriptions-item label="被叫（脱敏）">{{ detail.call.calleeMasked || '—' }}</el-descriptions-item>
            <el-descriptions-item label="开始时间">{{ formatTime(detail.call.startedAt) }}</el-descriptions-item>
            <el-descriptions-item label="结束时间">{{ formatTime(detail.call.endedAt) }}</el-descriptions-item>
            <el-descriptions-item label="时长">{{ formatDuration(detail.call.durationSeconds) }}</el-descriptions-item>
            <el-descriptions-item label="来源">{{ sourceLabels[detail.call.source] ?? detail.call.source }}</el-descriptions-item>
            <el-descriptions-item label="客户">
              {{ detail.call.customerName || detail.call.customerId || '—' }}
            </el-descriptions-item>
            <el-descriptions-item label="坐席">{{ detail.call.agentUserName || '—' }}</el-descriptions-item>
            <el-descriptions-item label="队列">{{ detail.call.queueName || '—' }}</el-descriptions-item>
            <el-descriptions-item label="厂商通话号">{{ detail.call.vendorCallId || '—' }}</el-descriptions-item>
          </el-descriptions>

          <el-descriptions :column="2" border size="small" title="关联信息" class="drawer-section">
            <el-descriptions-item label="关联类型">
              {{ linkTypeLabels[detail.call.linkType] }}
            </el-descriptions-item>
            <el-descriptions-item label="关联对象">
              {{ detail.call.linkNo || detail.call.linkId || '—' }}
            </el-descriptions-item>
          </el-descriptions>

          <el-descriptions
            v-if="detail.recording"
            :column="2"
            border
            size="small"
            title="录音信息"
            class="drawer-section"
          >
            <el-descriptions-item label="录音 Key" :span="2">
              {{ detail.recording.recordingKey }}
            </el-descriptions-item>
            <el-descriptions-item label="存储方式">
              {{ storageLabels[detail.recording.storageType] }}
            </el-descriptions-item>
            <el-descriptions-item label="时长">
              {{ formatDuration(detail.recording.durationSeconds) }}
            </el-descriptions-item>
            <el-descriptions-item label="文件大小">{{ formatSize(detail.recording.fileSize) }}</el-descriptions-item>
            <el-descriptions-item label="访问次数">{{ detail.recording.accessCount }}</el-descriptions-item>
          </el-descriptions>
          <p v-else class="empty drawer-section">本次通话无录音</p>
        </template>
      </div>
    </el-drawer>

    <!-- 模板编辑 -->
    <el-dialog
      v-model="templateForm.visible"
      :title="templateForm.editingId ? '编辑短信模板' : '新建短信模板'"
      width="560px"
    >
      <el-form label-width="88px">
        <el-form-item label="编码" required>
          <el-input
            v-model="templateForm.code"
            :disabled="!!templateForm.editingId"
            maxlength="64"
            placeholder="如 SMS-APPT-REMIND"
          />
        </el-form-item>
        <el-form-item label="名称" required>
          <el-input v-model="templateForm.name" maxlength="120" />
        </el-form-item>
        <el-form-item label="内容" required>
          <el-input
            v-model="templateForm.content"
            type="textarea"
            :rows="5"
            maxlength="1000"
            show-word-limit
            placeholder="变量用花括号表示，如：尊敬的{customerName}，您的预约时间为{appointTime}"
          />
          <div v-if="extractedVars.length" class="var-preview">
            <span class="hint">自动识别变量：</span>
            <el-tag v-for="v in extractedVars" :key="v" size="small" class="var-tag">{{ v }}</el-tag>
          </div>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="templateForm.remark" type="textarea" :rows="2" maxlength="500" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="templateForm.visible = false">取消</el-button>
        <el-button type="primary" @click="submitTemplate">保存</el-button>
      </template>
    </el-dialog>

    <!-- 新建消息 -->
    <el-dialog v-model="messageForm.visible" title="新建消息" width="560px">
      <el-form label-width="96px">
        <el-form-item label="渠道" required>
          <el-radio-group v-model="messageForm.channel">
            <el-radio value="sms">短信</el-radio>
            <el-radio value="wechat">微信</el-radio>
            <el-radio value="voice">语音</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="接收号码" required>
          <el-input v-model="messageForm.recipientPhone" maxlength="32" placeholder="客户手机号" />
        </el-form-item>
        <el-form-item label="短信模板">
          <el-select
            v-model="messageForm.templateCode"
            clearable
            placeholder="选模板；留空则自定义内容"
            style="width: 100%"
            @change="onTemplateChange"
          >
            <el-option
              v-for="item in templateOptions"
              :key="item.id"
              :label="`${item.name}（${item.code}）`"
              :value="item.code"
            />
          </el-select>
        </el-form-item>
        <template v-if="selectedTemplate">
          <el-form-item label="模板预览">
            <div class="tpl-preview">{{ selectedTemplate.content }}</div>
          </el-form-item>
          <el-form-item
            v-for="v in selectedTemplate.variables"
            :key="v"
            :label="v"
          >
            <el-input v-model="messageVariables[v]" :placeholder="`变量 ${v} 的值`" maxlength="200" />
          </el-form-item>
        </template>
        <el-form-item v-else label="消息内容" required>
          <el-input
            v-model="messageForm.content"
            type="textarea"
            :rows="4"
            maxlength="1000"
            show-word-limit
          />
        </el-form-item>
        <el-form-item label="场景">
          <el-input v-model="messageForm.scene" maxlength="64" placeholder="如 预约提醒 / 随访通知" />
        </el-form-item>
        <el-form-item label="业务键">
          <el-input v-model="messageForm.bizKey" maxlength="128" placeholder="幂等去重标识（可选）" />
        </el-form-item>
        <el-form-item label="客户 ID">
          <el-input v-model="messageForm.customerId" maxlength="64" placeholder="可选，关联客户档案" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="messageForm.visible = false">取消</el-button>
        <el-button type="primary" @click="submitMessage">提交发送</el-button>
      </template>
    </el-dialog>
  </section>
</template>

<style scoped>
.cc-page {
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
  max-width: 820px;
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

.el-pagination {
  margin-top: 10px;
  justify-content: center;
}

/* ---------------- 来电弹屏 ---------------- */
.popup-panel {
  max-width: 760px;
}

.popup-card {
  border: 1px solid var(--el-border-color-light);
  border-radius: 10px;
  background: #fff;
  padding: 16px 18px;
  box-shadow: 0 1px 4px rgba(45, 109, 173, 0.08);
}

.popup-head {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
}

.popup-avatar {
  width: 44px;
  height: 44px;
  border-radius: 50%;
  background: linear-gradient(135deg, #2d6dad, #5a8ec5);
  color: #fff;
  font-size: 18px;
  font-weight: 600;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.popup-name {
  font-size: 16px;
  font-weight: 600;
  display: flex;
  align-items: center;
  gap: 8px;
}

.popup-fields {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 8px 16px;
  background: #f7f9fc;
  border-radius: 8px;
  padding: 10px 12px;
  font-size: 13px;
}

.popup-field {
  display: flex;
  gap: 8px;
}

.popup-label {
  color: var(--el-text-color-secondary);
  flex-shrink: 0;
}

.popup-stats {
  display: flex;
  gap: 32px;
  margin-top: 12px;
}

.stat-item .stat-value {
  font-size: 16px;
  font-weight: 600;
  color: #2d6dad;
}

.popup-empty {
  margin-top: 30px;
}

/* ---------------- 模板与消息 ---------------- */
.tpl-content {
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  font-size: 12px;
  line-height: 1.5;
}

.var-tag {
  margin: 0 4px 2px 0;
}

.var-preview {
  margin-top: 6px;
}

.tpl-preview {
  width: 100%;
  background: #f7f9fc;
  border-radius: 6px;
  padding: 8px 10px;
  font-size: 12px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-word;
}

.error-text {
  color: var(--el-color-danger);
  font-size: 12px;
}

.inline-group {
  display: flex;
  gap: 8px;
  width: 100%;
}

.recording-hint {
  margin: 10px 0 0;
}

.drawer-section {
  margin-top: 16px;
}
</style>
