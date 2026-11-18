<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import { fetchUsers } from '@/api/users'
import { fetchCustomers } from '@/api/customers'
import {
  claimChatSession,
  closeChatSession,
  createChatSession,
  fetchChatQueue,
  fetchChatSessionDetail,
  fetchChatSessions,
  requestChatHandoff,
  sendChatMessage,
  transferChatSession
} from '@/api/chat'
import type { CustomerSummary } from '@/types/customer'
import type { UserResponse } from '@/types/system'
import type { ChatQueueItem, ChatSessionDetail, ChatSessionView, ChatSenderType } from '@/types/chat'

const auth = useAuthStore()
const canManage = computed(() => auth.has('crm:chat:manage'))
const canHandle = computed(() => auth.has('crm:chat:handle'))
const myId = computed(() => auth.user?.id)

const statusLabels: Record<string, string> = {
  AI: 'AI 接待',
  QUEUED: '排队中',
  ACTIVE: '人工接待',
  CLOSED: '已结束'
}

const statusTag = (status: string) => {
  if (status === 'ACTIVE') return 'success'
  if (status === 'QUEUED') return 'warning'
  if (status === 'AI') return 'primary'
  return 'info'
}

const priorityLabels: Record<string, string> = {
  low: '低',
  normal: '普通',
  high: '高',
  urgent: '紧急'
}

const reasonLabels: Record<string, string> = {
  explicit: '客户明确要求人工',
  complaint: '投诉',
  urgent: '紧急情况',
  sensitive: '敏感问题',
  low_confidence: 'AI 低置信度',
  transfer: '客服转接'
}

const modeLabels: Record<string, string> = {
  CONFIRM: '首次确认',
  SECOND: '二次请求自动排队',
  DIRECT: '直接转人工',
  TRANSFER: '客服转接'
}

const eventLabels: Record<string, string> = {
  CREATED: '创建会话',
  HANDOFF_REQUESTED: '请求人工',
  QUEUED: '进入人工队列',
  CLAIMED: '客服接管',
  TRANSFERRED: '客服转接',
  CLOSED: '结束会话'
}

const senderLabels: Record<string, string> = {
  CUSTOMER: '患者',
  AI: 'AI',
  STAFF: '客服',
  SYSTEM: '系统'
}

function formatTime(value?: string) {
  if (!value) return '—'
  return value.replace('T', ' ').slice(0, 16)
}

function formatDuration(seconds: number) {
  if (!seconds) return '0 秒'
  if (seconds < 60) return `${seconds} 秒`
  const minutes = Math.floor(seconds / 60)
  const rest = seconds % 60
  return rest ? `${minutes} 分 ${rest} 秒` : `${minutes} 分`
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

// ------------------------------------------------------------------
// 队列与会话列表
// ------------------------------------------------------------------
const sideTab = ref<'queue' | 'mine' | 'all'>('queue')
const queueLoading = ref(false)
const queue = ref<ChatQueueItem[]>([])
const sessions = ref<ChatSessionView[]>([])
const sessionLoading = ref(false)
const sessionQuery = reactive({ keyword: '', status: '', page: 1, pageSize: 30 })
const sessionTotal = ref(0)

async function loadQueue() {
  queueLoading.value = true
  try {
    queue.value = await fetchChatQueue()
  } finally {
    queueLoading.value = false
  }
}

async function loadSessions() {
  sessionLoading.value = true
  try {
    const result = await fetchChatSessions({
      keyword: sessionQuery.keyword || undefined,
      status: sessionQuery.status || undefined,
      assigneeId: sideTab.value === 'mine' ? myId.value : undefined,
      page: sessionQuery.page,
      pageSize: sessionQuery.pageSize
    })
    sessions.value = result.items
    sessionTotal.value = result.total
  } finally {
    sessionLoading.value = false
  }
}

async function refreshAll() {
  await Promise.all([loadQueue(), loadSessions()])
}

let timer: ReturnType<typeof setInterval> | undefined

function switchTab(tab: string | number) {
  sideTab.value = tab as 'queue' | 'mine' | 'all'
  sessionQuery.page = 1
  if (sideTab.value === 'queue') {
    void loadQueue()
  } else {
    void loadSessions()
  }
}

// ------------------------------------------------------------------
// 会话详情
// ------------------------------------------------------------------
const detail = ref<ChatSessionDetail>()
const detailLoading = ref(false)
const composer = reactive({ senderType: 'STAFF' as ChatSenderType, content: '' })

async function openSession(sessionId: string) {
  detailLoading.value = true
  try {
    detail.value = await fetchChatSessionDetail(sessionId)
  } catch (error) {
    ElMessage.error((error as Error).message)
  } finally {
    detailLoading.value = false
  }
}

async function refreshDetail() {
  if (!detail.value) return
  await openSession(detail.value.session.id)
}

async function afterMutation(sessionId: string) {
  await openSession(sessionId)
  await refreshAll()
}

const isMine = computed(
  () => detail.value?.session.assignedStaffId && detail.value.session.assignedStaffId === myId.value
)
const canOperate = computed(() => canManage.value || Boolean(isMine.value))
const canCompose = computed(
  () => canHandle.value && detail.value?.session.status === 'ACTIVE' && canOperate.value
)
const canClaim = computed(() => detail.value?.session.status === 'QUEUED')
const canHandoff = computed(
  () => canHandle.value && (detail.value?.session.status === 'AI' || detail.value?.session.status === 'QUEUED')
)

async function handleClaim(sessionId: string) {
  try {
    await claimChatSession(sessionId)
    ElMessage.success('已接管会话，AI 上下文与摘要已同步')
    await afterMutation(sessionId)
  } catch (error) {
    ElMessage.error((error as Error).message)
  }
}

async function handleSend() {
  if (!detail.value) return
  if (!composer.content.trim()) {
    ElMessage.warning('请输入消息内容')
    return
  }
  try {
    await sendChatMessage(detail.value.session.id, {
      senderType: composer.senderType,
      content: composer.content.trim()
    })
    composer.content = ''
    await afterMutation(detail.value.session.id)
  } catch (error) {
    ElMessage.error((error as Error).message)
  }
}

async function handleClose() {
  if (!detail.value) return
  try {
    const { value } = await ElMessageBox.prompt('请输入结束原因（可选）', '结束会话', {
      inputPlaceholder: '例如：患者问题已解决',
      confirmButtonText: '结束会话',
      cancelButtonText: '取消'
    })
    await closeChatSession(detail.value.session.id, value || undefined)
    ElMessage.success('会话已结束')
    await afterMutation(detail.value.session.id)
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') {
      ElMessage.error((error as Error).message)
    }
  }
}

// ------------------------------------------------------------------
// 转人工 / 转接
// ------------------------------------------------------------------
const handoffVisible = ref(false)
const handoffForm = reactive({ reason: 'explicit', note: '' })
const handoffResult = ref('')

function openHandoff() {
  handoffForm.reason = 'explicit'
  handoffForm.note = ''
  handoffResult.value = ''
  handoffVisible.value = true
}

async function submitHandoff() {
  if (!detail.value) return
  try {
    const result = await requestChatHandoff(detail.value.session.id, {
      reason: handoffForm.reason,
      note: handoffForm.note || undefined
    })
    handoffResult.value = result.needsConfirmation
      ? result.message
      : `${result.message}（队列位置：${result.queuePosition ?? '—'}）`
    await afterMutation(detail.value.session.id)
  } catch (error) {
    ElMessage.error((error as Error).message)
  }
}

const transferVisible = ref(false)
const transferForm = reactive({ toStaffId: '', note: '' })

function openTransfer() {
  transferForm.toStaffId = ''
  transferForm.note = ''
  transferVisible.value = true
}

async function submitTransfer() {
  if (!detail.value) return
  if (!transferForm.toStaffId) {
    ElMessage.warning('请选择目标客服')
    return
  }
  try {
    await transferChatSession(detail.value.session.id, {
      toStaffId: transferForm.toStaffId,
      note: transferForm.note || undefined
    })
    ElMessage.success('会话已转接，历史记录完整保留')
    transferVisible.value = false
    await afterMutation(detail.value.session.id)
  } catch (error) {
    ElMessage.error((error as Error).message)
  }
}

// ------------------------------------------------------------------
// 新建会话
// ------------------------------------------------------------------
const createVisible = ref(false)
const createForm = reactive({
  customerId: '',
  channel: 'h5',
  authorizationScope: 'basic'
})

function openCreate() {
  createForm.customerId = ''
  createForm.channel = 'h5'
  createForm.authorizationScope = 'basic'
  createVisible.value = true
  void searchCustomers('')
}

async function submitCreate() {
  if (!createForm.customerId) {
    ElMessage.warning('请选择客户')
    return
  }
  try {
    const created = await createChatSession({
      customerId: createForm.customerId,
      channel: createForm.channel,
      authorizationScope: createForm.authorizationScope || undefined
    })
    ElMessage.success('会话已创建')
    createVisible.value = false
    detail.value = created
    sideTab.value = 'mine'
    await loadSessions()
  } catch (error) {
    ElMessage.error((error as Error).message)
  }
}

onMounted(async () => {
  await Promise.all([loadBase(), loadQueue(), loadSessions()])
  timer = setInterval(() => {
    void loadQueue()
  }, 20000)
})

onBeforeUnmount(() => {
  if (timer) clearInterval(timer)
})
</script>

<template>
  <section class="chat-page">
    <header class="page-head">
      <div>
        <h2>客服工作台</h2>
        <p class="page-desc">
          默认 AI 接待；首次要求人工仅提示确认，再次请求自动进入人工队列，投诉、紧急、敏感与低置信度可直接转人工。
          人工接管保留完整会话、AI 摘要与患者授权范围，会话按接待人隔离。
        </p>
      </div>
      <el-button v-if="canHandle" type="primary" @click="openCreate">新建会话</el-button>
    </header>

    <div class="workspace">
      <aside class="side">
        <el-tabs :model-value="sideTab" stretch @tab-change="switchTab">
          <el-tab-pane label="人工队列" name="queue" />
          <el-tab-pane label="我的会话" name="mine" />
          <el-tab-pane v-if="canManage" label="全部会话" name="all" />
        </el-tabs>

        <div class="side-toolbar">
          <template v-if="sideTab === 'queue'">
            <span class="hint">等待 {{ queue.length }} 个会话</span>
            <el-button size="small" @click="loadQueue">刷新</el-button>
          </template>
          <template v-else>
            <el-input v-model="sessionQuery.keyword" placeholder="会话号" clearable size="small" style="width: 120px" />
            <el-select v-model="sessionQuery.status" placeholder="全部状态" clearable size="small" style="width: 110px">
              <el-option v-for="(label, key) in statusLabels" :key="key" :label="label" :value="key" />
            </el-select>
            <el-button size="small" @click="sessionQuery.page = 1; loadSessions()">查询</el-button>
          </template>
        </div>

        <div v-if="sideTab === 'queue'" v-loading="queueLoading" class="session-list">
          <div
            v-for="item in queue"
            :key="item.sessionId"
            class="session-card"
            :class="{ active: detail?.session.id === item.sessionId }"
            @click="openSession(item.sessionId)"
          >
            <div class="card-top">
              <span class="session-no">{{ item.sessionNo }}</span>
              <el-tag :type="item.priority === 'urgent' ? 'danger' : item.priority === 'high' ? 'warning' : 'info'" size="small">
                {{ priorityLabels[item.priority] || item.priority }}
              </el-tag>
            </div>
            <div class="card-line">
              {{ item.customerName || '未关联客户' }} · {{ item.phoneMasked || '—' }}
            </div>
            <div class="card-line preview">{{ item.lastMessage || '暂无消息' }}</div>
            <div class="card-bottom">
              <span class="hint">排队 {{ formatDuration(item.waitingSeconds) }} · 第 {{ item.queuePosition }} 位</span>
              <el-button v-if="canHandle" size="small" type="primary" link @click.stop="handleClaim(item.sessionId)">
                接管
              </el-button>
            </div>
          </div>
          <p v-if="!queue.length" class="empty">当前无排队会话</p>
        </div>

        <div v-else v-loading="sessionLoading" class="session-list">
          <div
            v-for="item in sessions"
            :key="item.id"
            class="session-card"
            :class="{ active: detail?.session.id === item.id }"
            @click="openSession(item.id)"
          >
            <div class="card-top">
              <span class="session-no">{{ item.sessionNo }}</span>
              <el-tag :type="statusTag(item.status)" size="small">{{ statusLabels[item.status] }}</el-tag>
            </div>
            <div class="card-line">{{ item.customerName || '未关联客户' }} · {{ item.channel }}</div>
            <div class="card-line preview">{{ item.lastMessagePreview || '暂无消息' }}</div>
            <div class="card-bottom">
              <span class="hint">{{ item.assignedStaffName || '未分配' }} · {{ formatTime(item.lastMessageAt) }}</span>
            </div>
          </div>
          <p v-if="!sessions.length" class="empty">暂无会话</p>
          <el-pagination
            v-if="sessionTotal > sessionQuery.pageSize"
            v-model:current-page="sessionQuery.page"
            :page-size="sessionQuery.pageSize"
            :total="sessionTotal"
            layout="prev, pager, next"
            small
            @current-change="loadSessions"
          />
        </div>
      </aside>

      <main class="panel">
        <div v-if="!detail" class="panel-empty">
          <p>从左侧选择队列会话或历史会话查看详情</p>
        </div>
        <template v-else>
          <div class="panel-head">
            <div>
              <div class="panel-title">
                {{ detail.session.customerName || '未关联客户' }}
                <el-tag :type="statusTag(detail.session.status)" size="small">
                  {{ statusLabels[detail.session.status] }}
                </el-tag>
                <el-tag v-if="detail.session.priority !== 'normal'" type="warning" size="small">
                  {{ priorityLabels[detail.session.priority] }}
                </el-tag>
              </div>
              <div class="hint">
                {{ detail.session.sessionNo }} · {{ detail.session.channel }} ·
                {{ detail.session.phoneMasked || '—' }} · 授权范围 {{ detail.session.authorizationScope }}
              </div>
            </div>
            <div class="panel-actions">
              <el-button v-if="canHandle && canHandoff" size="small" @click="openHandoff">请求转人工</el-button>
              <el-button v-if="canClaim" size="small" type="primary" @click="handleClaim(detail.session.id)">接管</el-button>
              <el-button v-if="canCompose" size="small" @click="openTransfer">转接</el-button>
              <el-button v-if="canHandle && detail.session.status !== 'CLOSED'" size="small" type="danger" plain @click="handleClose">
                结束会话
              </el-button>
              <el-button size="small" @click="refreshDetail">刷新</el-button>
            </div>
          </div>

          <el-alert
            v-if="detail.session.aiSummary"
            class="summary"
            type="info"
            :closable="false"
            :title="'AI 交接摘要'"
            :description="detail.session.aiSummary"
            show-icon
          />

          <div v-loading="detailLoading" class="message-list">
            <div
              v-for="message in detail.messages"
              :key="message.id"
              class="message-row"
              :class="{
                mine: message.senderType === 'STAFF',
                system: message.senderType === 'SYSTEM'
              }"
            >
              <div v-if="message.senderType !== 'SYSTEM'" class="bubble-wrap">
                <div class="bubble-meta">
                  {{ message.senderName || senderLabels[message.senderType] }} · {{ formatTime(message.createdAt) }}
                  <span v-if="message.aiConfidence !== undefined && message.aiConfidence !== null">
                    · 置信度 {{ (Number(message.aiConfidence) * 100).toFixed(0) }}%
                  </span>
                </div>
                <div class="bubble">{{ message.content }}</div>
                <div v-if="message.knowledgeRefs && message.knowledgeRefs.length" class="refs">
                  引用：{{ message.knowledgeRefs.join('、') }}
                </div>
              </div>
              <div v-else class="system-line">{{ message.content }}</div>
            </div>
            <p v-if="!detail.messages.length" class="empty">暂无消息</p>
          </div>

          <div v-if="canCompose" class="composer">
            <el-select v-model="composer.senderType" size="small" style="width: 100px">
              <el-option label="客服" value="STAFF" />
              <el-option label="患者" value="CUSTOMER" />
              <el-option label="AI" value="AI" />
              <el-option label="系统" value="SYSTEM" />
            </el-select>
            <el-input
              v-model="composer.content"
              type="textarea"
              :rows="2"
              maxlength="4000"
              placeholder="输入回复内容，Enter 发送"
              @keydown.enter.exact.prevent="handleSend"
            />
            <el-button type="primary" @click="handleSend">发送</el-button>
          </div>
          <p v-else-if="detail.session.status === 'ACTIVE'" class="hint operate-hint">
            当前会话由 {{ detail.session.assignedStaffName || '其他客服' }} 接待，仅可查看。
          </p>

          <el-collapse class="trail">
            <el-collapse-item title="转人工记录" name="handoff">
              <el-table v-if="detail.handoffs.length" :data="detail.handoffs" size="small" border>
                <el-table-column label="方式" width="140">
                  <template #default="{ row }">{{ modeLabels[row.mode] || row.mode }}</template>
                </el-table-column>
                <el-table-column label="原因" width="130">
                  <template #default="{ row }">{{ reasonLabels[row.reason] || row.reason }}</template>
                </el-table-column>
                <el-table-column prop="status" label="状态" width="130" />
                <el-table-column label="转接" min-width="150">
                  <template #default="{ row }">
                    {{ row.fromStaffName || '—' }} → {{ row.toStaffName || '—' }}
                  </template>
                </el-table-column>
                <el-table-column label="时间" width="150">
                  <template #default="{ row }">{{ formatTime(row.requestedAt) }}</template>
                </el-table-column>
              </el-table>
              <p v-else class="empty">暂无转人工记录</p>
            </el-collapse-item>
            <el-collapse-item title="会话轨迹" name="events">
              <el-timeline v-if="detail.events.length">
                <el-timeline-item
                  v-for="event in detail.events"
                  :key="event.id"
                  :timestamp="formatTime(event.createdAt)"
                  placement="top"
                >
                  <span class="event-title">{{ eventLabels[event.eventType] || event.eventType }}</span>
                  <span class="hint"> {{ event.operatorName || '系统' }} {{ event.note || '' }}</span>
                </el-timeline-item>
              </el-timeline>
              <p v-else class="empty">暂无轨迹</p>
            </el-collapse-item>
          </el-collapse>
        </template>
      </main>
    </div>

    <el-dialog v-model="createVisible" title="新建客服会话" width="500px">
      <el-form label-width="96px">
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
              :label="`${item.name || item.customerNo}（${item.customerNo}）`"
              :value="item.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="渠道" required>
          <el-select v-model="createForm.channel" style="width: 100%">
            <el-option label="H5" value="h5" />
            <el-option label="网页" value="web" />
            <el-option label="小程序" value="mini_program" />
            <el-option label="App" value="app" />
            <el-option label="电话" value="phone" />
            <el-option label="微信" value="wechat" />
          </el-select>
        </el-form-item>
        <el-form-item label="授权范围">
          <el-select v-model="createForm.authorizationScope" style="width: 100%">
            <el-option label="基础信息（basic）" value="basic" />
            <el-option label="预约信息（appointment）" value="appointment" />
            <el-option label="家庭账户（family）" value="family" />
          </el-select>
        </el-form-item>
      </el-form>
      <p class="hint">会话创建后由 AI 接待，客户明确要求人工时按一次确认、二次排队规则转人工。</p>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" @click="submitCreate">创建</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="handoffVisible" title="请求转人工" width="460px">
      <el-form label-width="90px">
        <el-form-item label="转人工原因">
          <el-select v-model="handoffForm.reason" style="width: 100%">
            <el-option
              v-for="(label, key) in reasonLabels"
              :key="key"
              :label="label"
              :value="key"
              :disabled="key === 'transfer'"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="handoffForm.note" type="textarea" :rows="2" maxlength="500" />
        </el-form-item>
      </el-form>
      <el-alert v-if="handoffResult" :title="handoffResult" type="success" :closable="false" show-icon />
      <template #footer>
        <el-button @click="handoffVisible = false">关闭</el-button>
        <el-button type="primary" @click="submitHandoff">提交请求</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="transferVisible" title="转接会话" width="440px">
      <el-form label-width="90px">
        <el-form-item label="目标客服" required>
          <el-select v-model="transferForm.toStaffId" filterable style="width: 100%">
            <el-option
              v-for="item in users"
              :key="item.id"
              :label="item.displayName || item.username"
              :value="item.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="转接说明">
          <el-input v-model="transferForm.note" type="textarea" :rows="2" maxlength="500" />
        </el-form-item>
      </el-form>
      <p class="hint">转接后会话、消息、AI 摘要和患者授权范围保持不变。</p>
      <template #footer>
        <el-button @click="transferVisible = false">取消</el-button>
        <el-button type="primary" @click="submitTransfer">确认转接</el-button>
      </template>
    </el-dialog>
  </section>
</template>

<style scoped>
.chat-page {
  padding: 20px 24px 32px;
}

.page-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
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
  max-width: 760px;
}

.workspace {
  display: flex;
  gap: 16px;
  min-height: 620px;
}

.side {
  width: 360px;
  flex-shrink: 0;
  background: #fff;
  border: 1px solid var(--el-border-color-light);
  border-radius: 8px;
  padding: 8px 12px 12px;
  display: flex;
  flex-direction: column;
}

.side-toolbar {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 4px 0 10px;
  flex-wrap: wrap;
}

.session-list {
  flex: 1;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.session-card {
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
  padding: 10px 12px;
  cursor: pointer;
  transition: border-color 0.15s, box-shadow 0.15s;
  background: #fbfcfe;
}

.session-card:hover {
  border-color: var(--el-color-primary-light-5);
}

.session-card.active {
  border-color: var(--el-color-primary);
  box-shadow: 0 0 0 1px var(--el-color-primary-light-7);
  background: #fff;
}

.card-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 6px;
}

.session-no {
  font-size: 13px;
  font-weight: 600;
}

.card-line {
  font-size: 12px;
  color: var(--el-text-color-regular);
  margin-bottom: 4px;
}

.card-line.preview {
  color: var(--el-text-color-secondary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.card-bottom {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 4px;
}

.panel {
  flex: 1;
  background: #fff;
  border: 1px solid var(--el-border-color-light);
  border-radius: 8px;
  padding: 16px 18px;
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.panel-empty {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--el-text-color-secondary);
}

.panel-head {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: flex-start;
}

.panel-title {
  font-size: 16px;
  font-weight: 600;
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 4px;
}

.panel-actions {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
  justify-content: flex-end;
}

.summary {
  margin: 12px 0 4px;
}

.message-list {
  flex: 1;
  overflow-y: auto;
  padding: 12px 4px;
  display: flex;
  flex-direction: column;
  gap: 10px;
  max-height: 420px;
}

.message-row {
  display: flex;
  justify-content: flex-start;
}

.message-row.mine {
  justify-content: flex-end;
}

.message-row.system {
  justify-content: center;
}

.bubble-wrap {
  max-width: 70%;
}

.bubble-meta {
  font-size: 11px;
  color: var(--el-text-color-secondary);
  margin-bottom: 3px;
}

.bubble {
  padding: 8px 12px;
  border-radius: 10px;
  background: #f2f5fa;
  font-size: 13px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-word;
}

.message-row.mine .bubble {
  background: #e3efff;
}

.message-row.mine .bubble-meta {
  text-align: right;
}

.refs {
  margin-top: 4px;
  font-size: 11px;
  color: var(--el-text-color-secondary);
}

.system-line {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  background: #f7f8fa;
  border-radius: 12px;
  padding: 4px 12px;
}

.composer {
  display: flex;
  gap: 10px;
  align-items: flex-start;
  border-top: 1px solid var(--el-border-color-lighter);
  padding-top: 12px;
}

.composer .el-textarea {
  flex: 1;
}

.operate-hint {
  margin-top: 12px;
}

.trail {
  margin-top: 12px;
}

.event-title {
  font-weight: 600;
  font-size: 13px;
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
  margin-top: 8px;
  justify-content: center;
}
</style>
