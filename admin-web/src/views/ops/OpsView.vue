<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import {
  ackAlert,
  getAlertRules,
  getAlerts,
  getBackups,
  getJobRuns,
  getJobStatuses,
  getOpsOverview,
  reportBackup,
  verifyBackup
} from '@/api/ops'
import type {
  AlertRuleView,
  AlertSeverity,
  AlertStatus,
  AlertView,
  BackupStatus,
  BackupType,
  BackupView,
  JobRunView,
  JobStatusView,
  OpsOverview
} from '@/types/ops'

const auth = useAuthStore()
const canOpsManage = computed(() => auth.has('crm:ops:manage'))

type TabName = 'overview' | 'jobs' | 'alerts' | 'backups' | 'rules'

const activeTab = ref<TabName>('overview')

// ------------------------------------------------------------------
// 文案与格式
// ------------------------------------------------------------------
const severityLabels: Record<AlertSeverity, string> = {
  CRITICAL: '严重',
  HIGH: '高',
  MEDIUM: '中',
  LOW: '低'
}

const alertStatusLabels: Record<AlertStatus, string> = {
  OPEN: '待处理',
  ACKED: '已确认',
  RESOLVED: '已解决'
}

const backupTypeLabels: Record<BackupType, string> = {
  FULL: '全量',
  WAL: 'WAL 增量',
  OBJECT: '对象存储'
}

const backupStatusLabels: Record<BackupStatus, string> = {
  SUCCESS: '成功',
  FAILED: '失败'
}

function severityClass(severity: AlertSeverity) {
  return `sev-${severity.toLowerCase()}`
}

function alertStatusTag(status: AlertStatus) {
  if (status === 'OPEN') return 'danger'
  if (status === 'ACKED') return 'warning'
  return 'success'
}

function backupStatusTag(status: BackupStatus) {
  return status === 'SUCCESS' ? 'success' : 'danger'
}

function formatDateTime(value?: string | null) {
  if (!value) return '—'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value.replace('T', ' ').slice(0, 19)
  return date.toLocaleString('zh-CN', { hour12: false })
}

function formatNumber(value?: number | null) {
  if (value === null || value === undefined) return '—'
  return value.toLocaleString('zh-CN')
}

function formatRate(value?: number | null) {
  if (value === null || value === undefined) return '—'
  return `${(value * 100).toFixed(2)}%`
}

function formatUptime(seconds?: number | null) {
  if (seconds === null || seconds === undefined) return '—'
  const total = Math.max(0, Math.floor(seconds))
  const days = Math.floor(total / 86400)
  const hours = Math.floor((total % 86400) / 3600)
  const minutes = Math.floor((total % 3600) / 60)
  const parts: string[] = []
  if (days > 0) parts.push(`${days} 天`)
  if (days > 0 || hours > 0) parts.push(`${hours} 小时`)
  parts.push(`${minutes} 分`)
  return parts.join(' ')
}

function formatInterval(seconds?: number | null) {
  if (seconds === null || seconds === undefined || seconds <= 0) return '—'
  if (seconds % 3600 === 0) return `${seconds / 3600} 小时`
  if (seconds % 60 === 0) return `${seconds / 60} 分钟`
  return `${seconds} 秒`
}

function formatMs(value?: number | null) {
  if (value === null || value === undefined) return '—'
  return `${value.toLocaleString('zh-CN')} ms`
}

function formatSize(bytes?: number | null) {
  if (bytes === null || bytes === undefined) return '—'
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  if (bytes < 1024 * 1024 * 1024) return `${(bytes / 1024 / 1024).toFixed(1)} MB`
  return `${(bytes / 1024 / 1024 / 1024).toFixed(2)} GB`
}

// ------------------------------------------------------------------
// 运行总览
// ------------------------------------------------------------------
const overview = ref<OpsOverview>()
const overviewLoading = ref(false)

async function loadOverview() {
  overviewLoading.value = true
  try {
    overview.value = await getOpsOverview()
  } catch (error) {
    ElMessage.error((error as Error).message)
  } finally {
    overviewLoading.value = false
  }
}

// ------------------------------------------------------------------
// 定时任务
// ------------------------------------------------------------------
const jobs = ref<JobStatusView[]>([])
const jobsLoading = ref(false)

async function loadJobs() {
  jobsLoading.value = true
  try {
    jobs.value = await getJobStatuses()
  } catch (error) {
    ElMessage.error((error as Error).message)
  } finally {
    jobsLoading.value = false
  }
}

const runDrawerVisible = ref(false)
const currentJob = ref<JobStatusView>()
const jobRuns = ref<JobRunView[]>([])
const jobRunsLoading = ref(false)

function openJobRuns(job: JobStatusView) {
  currentJob.value = job
  jobRuns.value = []
  runDrawerVisible.value = true
  void loadJobRuns(job.jobCode)
}

async function loadJobRuns(jobCode: string) {
  jobRunsLoading.value = true
  try {
    jobRuns.value = await getJobRuns(jobCode, 20)
  } catch (error) {
    ElMessage.error((error as Error).message)
  } finally {
    jobRunsLoading.value = false
  }
}

// ------------------------------------------------------------------
// 告警中心
// ------------------------------------------------------------------
const alerts = ref<AlertView[]>([])
const alertsLoading = ref(false)
const alertsTotal = ref(0)
const alertsQuery = reactive({
  status: '' as AlertStatus | '',
  page: 1,
  pageSize: 20
})

async function loadAlerts() {
  alertsLoading.value = true
  try {
    const result = await getAlerts({
      status: alertsQuery.status || undefined,
      page: alertsQuery.page,
      pageSize: alertsQuery.pageSize
    })
    alerts.value = result.items
    alertsTotal.value = result.total
  } catch (error) {
    ElMessage.error((error as Error).message)
  } finally {
    alertsLoading.value = false
  }
}

function searchAlerts() {
  alertsQuery.page = 1
  void loadAlerts()
}

async function handleAck(row: AlertView) {
  try {
    await ElMessageBox.confirm(
      `确认已知晓告警「${row.title}」？确认后将记录确认人与时间。`,
      '确认告警',
      { confirmButtonText: '确认', cancelButtonText: '取消', type: 'warning' }
    )
  } catch {
    return
  }
  try {
    await ackAlert(row.id)
    ElMessage.success('告警已确认')
    await loadAlerts()
    void loadOverview()
  } catch (error) {
    ElMessage.error((error as Error).message)
  }
}

// ------------------------------------------------------------------
// 备份与演练
// ------------------------------------------------------------------
const backups = ref<BackupView[]>([])
const backupsLoading = ref(false)
const backupsTotal = ref(0)
const backupsQuery = reactive({
  status: '' as BackupStatus | '',
  page: 1,
  pageSize: 20
})

async function loadBackups() {
  backupsLoading.value = true
  try {
    const result = await getBackups({
      status: backupsQuery.status || undefined,
      page: backupsQuery.page,
      pageSize: backupsQuery.pageSize
    })
    backups.value = result.items
    backupsTotal.value = result.total
  } catch (error) {
    ElMessage.error((error as Error).message)
  } finally {
    backupsLoading.value = false
  }
}

function searchBackups() {
  backupsQuery.page = 1
  void loadBackups()
}

const backupFormRef = ref<FormInstance>()
const backupDialog = ref({
  visible: false,
  backupCode: '',
  backupType: 'FULL' as BackupType,
  status: 'SUCCESS' as BackupStatus,
  sizeBytes: 0,
  fileHash: '',
  target: '',
  startedAt: null as Date | null,
  finishedAt: null as Date | null,
  message: ''
})

const backupRules: FormRules = {
  backupCode: [{ required: true, message: '请填写备份号', trigger: 'blur' }],
  backupType: [{ required: true, message: '请选择备份类型', trigger: 'change' }],
  status: [{ required: true, message: '请选择备份状态', trigger: 'change' }],
  sizeBytes: [{ required: true, message: '请填写备份大小（字节）', trigger: 'blur' }],
  startedAt: [{ required: true, message: '请选择开始时间', trigger: 'change' }],
  finishedAt: [{ required: true, message: '请选择结束时间', trigger: 'change' }]
}

function openBackupDialog() {
  backupDialog.value.backupCode = ''
  backupDialog.value.backupType = 'FULL'
  backupDialog.value.status = 'SUCCESS'
  backupDialog.value.sizeBytes = 0
  backupDialog.value.fileHash = ''
  backupDialog.value.target = ''
  backupDialog.value.startedAt = null
  backupDialog.value.finishedAt = null
  backupDialog.value.message = ''
  backupDialog.value.visible = true
  backupFormRef.value?.clearValidate()
}

async function submitBackup() {
  if (!backupFormRef.value) return
  const valid = await backupFormRef.value.validate().catch(() => false)
  if (!valid) return
  const form = backupDialog.value
  if (!form.startedAt || !form.finishedAt) return
  try {
    await reportBackup({
      backupCode: form.backupCode.trim(),
      backupType: form.backupType,
      status: form.status,
      sizeBytes: form.sizeBytes,
      fileHash: form.fileHash.trim() || undefined,
      target: form.target.trim() || undefined,
      startedAt: form.startedAt.toISOString(),
      finishedAt: form.finishedAt.toISOString(),
      message: form.message.trim() || undefined
    })
    ElMessage.success('备份记录已登记')
    backupDialog.value.visible = false
    await loadBackups()
    void loadOverview()
  } catch (error) {
    ElMessage.error((error as Error).message)
  }
}

const verifyDialog = ref({
  visible: false,
  backupId: '',
  backupCode: '',
  restoreVerified: true,
  message: ''
})

function openVerifyDialog(row: BackupView) {
  verifyDialog.value.backupId = row.id
  verifyDialog.value.backupCode = row.backupCode
  verifyDialog.value.restoreVerified = true
  verifyDialog.value.message = ''
  verifyDialog.value.visible = true
}

async function submitVerify() {
  try {
    await verifyBackup(verifyDialog.value.backupId, {
      restoreVerified: verifyDialog.value.restoreVerified,
      message: verifyDialog.value.message.trim() || undefined
    })
    ElMessage.success('演练结果已登记')
    verifyDialog.value.visible = false
    await loadBackups()
    void loadOverview()
  } catch (error) {
    ElMessage.error((error as Error).message)
  }
}

// ------------------------------------------------------------------
// 告警规则
// ------------------------------------------------------------------
const rules = ref<AlertRuleView[]>([])
const rulesLoading = ref(false)

async function loadAlertRules() {
  rulesLoading.value = true
  try {
    rules.value = await getAlertRules()
  } catch (error) {
    ElMessage.error((error as Error).message)
  } finally {
    rulesLoading.value = false
  }
}

// ------------------------------------------------------------------
// 标签页切换与初始化
// ------------------------------------------------------------------
function onTabChange(name: string | number) {
  if (name === 'jobs' && !jobs.value.length) {
    void loadJobs()
  }
  if (name === 'alerts' && !alerts.value.length) {
    void loadAlerts()
  }
  if (name === 'backups' && !backups.value.length) {
    void loadBackups()
  }
  if (name === 'rules' && !rules.value.length) {
    void loadAlertRules()
  }
}

onMounted(() => {
  void loadOverview()
})
</script>

<template>
  <section class="ops-page">
    <header class="page-head">
      <div>
        <h2>运维监控</h2>
        <p class="page-desc">
          运维监控——运行总览、定时任务、告警中心、备份与恢复演练、告警规则；用于系统集成与上线后的日常值守。
        </p>
      </div>
    </header>

    <el-tabs v-model="activeTab" class="ops-tabs" @tab-change="onTabChange">
      <!-- ============================================================ -->
      <el-tab-pane label="运行总览" name="overview">
        <div class="toolbar">
          <span class="hint">运行时健康状态，数据由后端实时采集。</span>
          <span class="spacer" />
          <span v-if="overview" class="hint">
            生成时间 {{ formatDateTime(overview.generatedAt) }}
          </span>
          <el-button size="small" type="primary" :loading="overviewLoading" @click="loadOverview">
            刷新
          </el-button>
        </div>

        <div v-loading="overviewLoading">
          <template v-if="overview">
            <div class="stat-grid">
              <div class="stat-card">
                <div class="stat-label">服务状态</div>
                <div class="stat-line">
                  <el-tag type="success" size="small">运行中</el-tag>
                  <span class="stat-value">{{ formatUptime(overview.uptimeSeconds) }}</span>
                </div>
                <div class="hint">启动于 {{ formatDateTime(overview.startedAt) }}</div>
              </div>

              <div class="stat-card">
                <div class="stat-label">数据库</div>
                <div class="stat-line">
                  <el-tag
                    :type="overview.database.status === 'UP' ? 'success' : 'danger'"
                    size="small"
                  >
                    {{ overview.database.status === 'UP' ? '正常' : '异常' }}
                  </el-tag>
                  <span class="stat-value">
                    {{ formatNumber(overview.database.roundTripMs) }} ms
                  </span>
                </div>
                <div class="hint">连通性探测往返耗时</div>
              </div>

              <div class="stat-card">
                <div class="stat-label">待处理告警</div>
                <div class="stat-value" :class="{ 'stat-danger': overview.alerts.open > 0 }">
                  {{ formatNumber(overview.alerts.open) }} 条
                </div>
                <div class="hint">
                  严重 {{ formatNumber(overview.alerts.critical) }} · 高
                  {{ formatNumber(overview.alerts.high) }} · 中
                  {{ formatNumber(overview.alerts.medium) }}
                </div>
              </div>

              <div class="stat-card">
                <div class="stat-label">接口请求数（近 {{ overview.api.windowMinutes }} 分钟）</div>
                <div class="stat-value">{{ formatNumber(overview.api.requestCount) }}</div>
                <div class="hint">错误 {{ formatNumber(overview.api.errorCount) }} 次</div>
              </div>

              <div class="stat-card">
                <div class="stat-label">接口错误率</div>
                <div class="stat-value error-rate">{{ formatRate(overview.api.errorRate) }}</div>
                <div class="hint">按近 {{ overview.api.windowMinutes }} 分钟窗口统计</div>
              </div>

              <div class="stat-card">
                <div class="stat-label">接口耗时</div>
                <div class="stat-value">{{ formatMs(overview.api.avgLatencyMs) }}</div>
                <div class="hint">平均耗时 · 最大 {{ formatMs(overview.api.maxLatencyMs) }}</div>
              </div>

              <div class="stat-card">
                <div class="stat-label">定时任务</div>
                <div class="stat-value">{{ formatNumber(overview.jobs.total) }} 个</div>
                <div class="hint">
                  停滞 {{ formatNumber(overview.jobs.stale) }} · 连续失败
                  {{ formatNumber(overview.jobs.failing) }}
                </div>
              </div>
            </div>

            <div class="panel-card section-block">
              <div class="block-title">服务信息</div>
              <el-descriptions :column="3" border size="small">
                <el-descriptions-item label="应用">{{ overview.appName }}</el-descriptions-item>
                <el-descriptions-item label="版本">{{ overview.appVersion }}</el-descriptions-item>
                <el-descriptions-item label="环境">{{ overview.profile }}</el-descriptions-item>
                <el-descriptions-item label="启动时间">
                  {{ formatDateTime(overview.startedAt) }}
                </el-descriptions-item>
                <el-descriptions-item label="运行时长">
                  {{ formatUptime(overview.uptimeSeconds) }}
                </el-descriptions-item>
                <el-descriptions-item label="生成时间">
                  {{ formatDateTime(overview.generatedAt) }}
                </el-descriptions-item>
              </el-descriptions>
            </div>

            <div class="panel-card section-block">
              <div class="block-title">备份状态</div>
              <el-descriptions :column="3" border size="small">
                <el-descriptions-item label="最近成功备份">
                  {{ formatDateTime(overview.backups.lastSuccessAt) }}
                </el-descriptions-item>
                <el-descriptions-item label="备份号">
                  {{ overview.backups.lastBackupCode || '—' }}
                </el-descriptions-item>
                <el-descriptions-item label="最近状态">
                  {{ overview.backups.lastStatus || '—' }}
                </el-descriptions-item>
                <el-descriptions-item label="距今（小时）">
                  {{
                    overview.backups.ageHours === null
                      ? '—'
                      : `${overview.backups.ageHours.toFixed(1)} 小时`
                  }}
                </el-descriptions-item>
                <el-descriptions-item label="是否已演练">
                  <el-tag
                    :type="overview.backups.restoreVerified ? 'success' : 'info'"
                    size="small"
                  >
                    {{ overview.backups.restoreVerified ? '已演练' : '未演练' }}
                  </el-tag>
                </el-descriptions-item>
                <el-descriptions-item label="最近演练时间">
                  {{ formatDateTime(overview.backups.lastVerifiedAt) }}
                </el-descriptions-item>
              </el-descriptions>
            </div>

            <div class="panel-card section-block">
              <div class="block-title">外部集成</div>
              <el-descriptions :column="3" border size="small">
                <el-descriptions-item label="HIS 接入">
                  <el-tag :type="overview.capabilities.hisEnabled ? 'success' : 'info'" size="small">
                    {{ overview.capabilities.hisEnabled ? '已启用' : '未启用' }}
                  </el-tag>
                </el-descriptions-item>
                <el-descriptions-item label="呼叫中心">
                  <el-tag
                    :type="overview.capabilities.callCenterEnabled ? 'success' : 'info'"
                    size="small"
                  >
                    {{ overview.capabilities.callCenterEnabled ? '已启用' : '未启用' }}
                  </el-tag>
                </el-descriptions-item>
                <el-descriptions-item label="短信通道">
                  {{ overview.capabilities.smsProvider || '—' }}
                </el-descriptions-item>
              </el-descriptions>
            </div>
          </template>
          <p v-else-if="!overviewLoading" class="empty">暂无运行数据</p>
        </div>
      </el-tab-pane>

      <!-- ============================================================ -->
      <el-tab-pane label="定时任务" name="jobs">
        <div class="toolbar">
          <span class="hint">停滞任务指超过期望执行间隔仍未运行的定时任务。</span>
          <span class="spacer" />
          <el-button size="small" type="primary" :loading="jobsLoading" @click="loadJobs">
            刷新
          </el-button>
        </div>

        <div class="panel-card">
          <el-table v-loading="jobsLoading" :data="jobs" size="small">
            <el-table-column prop="jobName" label="任务名" min-width="160" show-overflow-tooltip />
            <el-table-column prop="jobCode" label="任务编码" width="180" />
            <el-table-column label="期望间隔" width="110">
              <template #default="{ row }">
                {{ formatInterval(row.expectedIntervalSeconds) }}
              </template>
            </el-table-column>
            <el-table-column label="最近执行" width="160">
              <template #default="{ row }">
                {{ row.neverRun ? '从未执行' : formatDateTime(row.lastRunAt) }}
              </template>
            </el-table-column>
            <el-table-column label="状态" width="90">
              <template #default="{ row }">
                <el-tag
                  v-if="row.lastStatus"
                  :type="row.lastStatus === 'SUCCESS' ? 'success' : 'danger'"
                  size="small"
                >
                  {{ row.lastStatus === 'SUCCESS' ? '成功' : '失败' }}
                </el-tag>
                <el-tag v-else type="info" size="small">未运行</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="耗时" width="110">
              <template #default="{ row }">{{ formatMs(row.lastDurationMs) }}</template>
            </el-table-column>
            <el-table-column prop="consecutiveFailures" label="连续失败" width="90" align="center" />
            <el-table-column label="是否停滞" width="100">
              <template #default="{ row }">
                <el-tag :type="row.stale ? 'danger' : 'success'" size="small">
                  {{ row.stale ? '停滞' : '正常' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="消息" min-width="180" show-overflow-tooltip>
              <template #default="{ row }">{{ row.lastMessage || '—' }}</template>
            </el-table-column>
            <el-table-column label="操作" width="100" fixed="right">
              <template #default="{ row }">
                <el-button size="small" link type="primary" @click="openJobRuns(row)">
                  查看运行记录
                </el-button>
              </template>
            </el-table-column>
          </el-table>
          <p v-if="!jobs.length && !jobsLoading" class="empty">暂无定时任务</p>
        </div>
      </el-tab-pane>

      <!-- ============================================================ -->
      <el-tab-pane label="告警中心" name="alerts">
        <div class="toolbar">
          <el-select
            v-model="alertsQuery.status"
            placeholder="全部状态"
            clearable
            size="small"
            style="width: 130px"
          >
            <el-option label="待处理" value="OPEN" />
            <el-option label="已确认" value="ACKED" />
            <el-option label="已解决" value="RESOLVED" />
          </el-select>
          <el-button size="small" type="primary" @click="searchAlerts">查询</el-button>
          <span class="spacer" />
          <span class="hint">确认告警需要 crm:ops:manage 权限</span>
        </div>

        <div class="panel-card">
          <el-table v-loading="alertsLoading" :data="alerts" size="small">
            <el-table-column label="等级" width="80">
              <template #default="{ row }">
                <el-tag size="small" :class="severityClass(row.severity)">
                  {{ severityLabels[row.severity as AlertSeverity] ?? row.severity }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="标题" min-width="220">
              <template #default="{ row }">
                <div>{{ row.title }}</div>
                <el-tooltip
                  v-if="row.detail"
                  :content="row.detail"
                  placement="top"
                  popper-class="ops-tooltip"
                >
                  <span class="detail-link">详情</span>
                </el-tooltip>
              </template>
            </el-table-column>
            <el-table-column prop="alertCode" label="告警编码" width="170" show-overflow-tooltip />
            <el-table-column label="来源" width="110" show-overflow-tooltip>
              <template #default="{ row }">{{ row.source || '—' }}</template>
            </el-table-column>
            <el-table-column label="首次发生" width="160">
              <template #default="{ row }">{{ formatDateTime(row.firstSeenAt) }}</template>
            </el-table-column>
            <el-table-column label="最近发生" width="160">
              <template #default="{ row }">{{ formatDateTime(row.lastSeenAt) }}</template>
            </el-table-column>
            <el-table-column prop="occurrences" label="次数" width="70" align="center" />
            <el-table-column label="状态" width="90">
              <template #default="{ row }">
                <el-tag :type="alertStatusTag(row.status as AlertStatus)" size="small">
                  {{ alertStatusLabels[row.status as AlertStatus] ?? row.status }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="确认人 / 时间" width="160">
              <template #default="{ row }">
                <template v-if="row.ackedByName">
                  <div>{{ row.ackedByName }}</div>
                  <div class="hint">{{ formatDateTime(row.ackedAt) }}</div>
                </template>
                <span v-else class="hint">—</span>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="90" fixed="right">
              <template #default="{ row }">
                <el-button
                  v-if="row.status !== 'RESOLVED' && canOpsManage"
                  size="small"
                  link
                  type="primary"
                  @click="handleAck(row)"
                >
                  确认
                </el-button>
                <span v-else class="hint">—</span>
              </template>
            </el-table-column>
          </el-table>
          <p v-if="!alerts.length && !alertsLoading" class="empty">当前筛选条件下暂无告警</p>
          <el-pagination
            v-if="alertsTotal > alertsQuery.pageSize"
            v-model:current-page="alertsQuery.page"
            :page-size="alertsQuery.pageSize"
            :total="alertsTotal"
            layout="prev, pager, next"
            small
            @current-change="loadAlerts"
          />
        </div>
      </el-tab-pane>

      <!-- ============================================================ -->
      <el-tab-pane label="备份与演练" name="backups">
        <div class="toolbar">
          <el-select
            v-model="backupsQuery.status"
            placeholder="全部状态"
            clearable
            size="small"
            style="width: 120px"
          >
            <el-option label="成功" value="SUCCESS" />
            <el-option label="失败" value="FAILED" />
          </el-select>
          <el-button size="small" type="primary" @click="searchBackups">查询</el-button>
          <span class="spacer" />
          <el-button v-if="canOpsManage" size="small" type="primary" @click="openBackupDialog">
            登记备份
          </el-button>
        </div>

        <div class="panel-card">
          <el-table v-loading="backupsLoading" :data="backups" size="small">
            <el-table-column prop="backupCode" label="备份号" width="190" show-overflow-tooltip />
            <el-table-column label="类型" width="100">
              <template #default="{ row }">
                {{ backupTypeLabels[row.backupType as BackupType] ?? row.backupType }}
              </template>
            </el-table-column>
            <el-table-column label="状态" width="80">
              <template #default="{ row }">
                <el-tag :type="backupStatusTag(row.status as BackupStatus)" size="small">
                  {{ backupStatusLabels[row.status as BackupStatus] ?? row.status }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="大小" width="110" align="right">
              <template #default="{ row }">{{ formatSize(row.sizeBytes) }}</template>
            </el-table-column>
            <el-table-column label="哈希" width="120">
              <template #default="{ row }">
                <el-tooltip
                  v-if="row.fileHash"
                  :content="row.fileHash"
                  placement="top"
                  popper-class="ops-tooltip"
                >
                  <span class="cell-mono">{{ row.fileHash.slice(0, 12) }}</span>
                </el-tooltip>
                <span v-else class="hint">—</span>
              </template>
            </el-table-column>
            <el-table-column label="开始时间" width="160">
              <template #default="{ row }">{{ formatDateTime(row.startedAt) }}</template>
            </el-table-column>
            <el-table-column label="结束时间" width="160">
              <template #default="{ row }">{{ formatDateTime(row.finishedAt) }}</template>
            </el-table-column>
            <el-table-column label="耗时" width="90">
              <template #default="{ row }">
                {{ row.durationSeconds === null ? '—' : `${row.durationSeconds} 秒` }}
              </template>
            </el-table-column>
            <el-table-column label="是否演练" width="90">
              <template #default="{ row }">
                <el-tag :type="row.restoreVerified ? 'success' : 'info'" size="small">
                  {{ row.restoreVerified ? '已演练' : '未演练' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="回填时间" width="160">
              <template #default="{ row }">{{ formatDateTime(row.restoreVerifiedAt) }}</template>
            </el-table-column>
            <el-table-column label="目标" min-width="130" show-overflow-tooltip>
              <template #default="{ row }">{{ row.target || '—' }}</template>
            </el-table-column>
            <el-table-column label="操作" width="150" fixed="right">
              <template #default="{ row }">
                <el-button
                  v-if="canOpsManage"
                  size="small"
                  link
                  type="primary"
                  @click="openVerifyDialog(row)"
                >
                  登记恢复演练结果
                </el-button>
                <span v-else class="hint">—</span>
              </template>
            </el-table-column>
          </el-table>
          <p v-if="!backups.length && !backupsLoading" class="empty">暂无备份记录</p>
          <el-pagination
            v-if="backupsTotal > backupsQuery.pageSize"
            v-model:current-page="backupsQuery.page"
            :page-size="backupsQuery.pageSize"
            :total="backupsTotal"
            layout="prev, pager, next"
            small
            @current-change="loadBackups"
          />
        </div>
      </el-tab-pane>

      <!-- ============================================================ -->
      <el-tab-pane label="告警规则" name="rules">
        <div class="toolbar">
          <span class="hint">
            规则由后端定时评估，阈值与统计窗口可通过后端环境变量调整；此处展示当前生效配置，共
            {{ rules.length }} 条规则。
          </span>
          <span class="spacer" />
          <el-button size="small" type="primary" :loading="rulesLoading" @click="loadAlertRules">
            刷新
          </el-button>
        </div>

        <div class="panel-card">
          <el-table v-loading="rulesLoading" :data="rules" size="small">
            <el-table-column prop="alertCode" label="编码" width="220" show-overflow-tooltip />
            <el-table-column prop="name" label="名称" min-width="160" show-overflow-tooltip />
            <el-table-column label="等级" width="80">
              <template #default="{ row }">
                <el-tag size="small" :class="severityClass(row.severity)">
                  {{ severityLabels[row.severity as AlertSeverity] ?? row.severity }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="condition" label="触发条件" min-width="220" show-overflow-tooltip />
            <el-table-column label="阈值" width="100" align="right">
              <template #default="{ row }">
                {{ row.threshold === null ? '—' : row.threshold }}
              </template>
            </el-table-column>
            <el-table-column label="窗口" width="110">
              <template #default="{ row }">
                {{ formatInterval(row.windowSeconds) }}
              </template>
            </el-table-column>
            <el-table-column label="启用" width="80">
              <template #default="{ row }">
                <el-tag :type="row.enabled ? 'success' : 'info'" size="small">
                  {{ row.enabled ? '启用' : '停用' }}
                </el-tag>
              </template>
            </el-table-column>
          </el-table>
          <p v-if="!rules.length && !rulesLoading" class="empty">暂无告警规则</p>
        </div>
      </el-tab-pane>
    </el-tabs>

    <!-- 任务执行记录抽屉 -->
    <el-drawer
      v-model="runDrawerVisible"
      :title="currentJob ? `运行记录 · ${currentJob.jobName}` : '运行记录'"
      size="760px"
    >
      <p v-if="currentJob" class="hint drawer-hint">
        任务编码 {{ currentJob.jobCode }} · 期望间隔
        {{ formatInterval(currentJob.expectedIntervalSeconds) }} · 最近 20 次执行记录
      </p>
      <el-table v-loading="jobRunsLoading" :data="jobRuns" size="small" border>
        <el-table-column label="开始时间" width="160">
          <template #default="{ row }">{{ formatDateTime(row.startedAt) }}</template>
        </el-table-column>
        <el-table-column label="结束时间" width="160">
          <template #default="{ row }">{{ formatDateTime(row.finishedAt) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="80">
          <template #default="{ row }">
            <el-tag :type="row.status === 'SUCCESS' ? 'success' : 'danger'" size="small">
              {{ row.status === 'SUCCESS' ? '成功' : '失败' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="耗时" width="100">
          <template #default="{ row }">{{ formatMs(row.durationMs) }}</template>
        </el-table-column>
        <el-table-column label="消息" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">{{ row.message || '—' }}</template>
        </el-table-column>
      </el-table>
      <p v-if="!jobRuns.length && !jobRunsLoading" class="empty">暂无执行记录</p>
    </el-drawer>

    <!-- 登记备份 -->
    <el-dialog v-model="backupDialog.visible" title="登记备份" width="560px">
      <el-form ref="backupFormRef" :model="backupDialog" :rules="backupRules" label-width="110px">
        <el-form-item label="备份号" prop="backupCode">
          <el-input v-model="backupDialog.backupCode" maxlength="120" placeholder="如 BAK-20261118-0200" />
        </el-form-item>
        <el-form-item label="备份类型" prop="backupType">
          <el-select v-model="backupDialog.backupType" style="width: 100%">
            <el-option
              v-for="(label, key) in backupTypeLabels"
              :key="key"
              :label="`${label}（${key}）`"
              :value="key"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="备份状态" prop="status">
          <el-select v-model="backupDialog.status" style="width: 100%">
            <el-option
              v-for="(label, key) in backupStatusLabels"
              :key="key"
              :label="label"
              :value="key"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="大小（字节）" prop="sizeBytes">
          <el-input-number
            v-model="backupDialog.sizeBytes"
            :min="0"
            :step="1048576"
            controls-position="right"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="文件哈希（可选）">
          <el-input
            v-model="backupDialog.fileHash"
            maxlength="64"
            placeholder="SHA-256，64 位十六进制字符"
          />
        </el-form-item>
        <el-form-item label="目标位置（可选）">
          <el-input v-model="backupDialog.target" maxlength="120" placeholder="如 prod-db / oss://backup" />
        </el-form-item>
        <el-form-item label="开始时间" prop="startedAt">
          <el-date-picker
            v-model="backupDialog.startedAt"
            type="datetime"
            placeholder="选择开始时间"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="结束时间" prop="finishedAt">
          <el-date-picker
            v-model="backupDialog.finishedAt"
            type="datetime"
            placeholder="选择结束时间"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="备注">
          <el-input
            v-model="backupDialog.message"
            type="textarea"
            :rows="2"
            maxlength="300"
            show-word-limit
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="backupDialog.visible = false">取消</el-button>
        <el-button type="primary" @click="submitBackup">提交登记</el-button>
      </template>
    </el-dialog>

    <!-- 登记恢复演练结果 -->
    <el-dialog v-model="verifyDialog.visible" title="登记恢复演练结果" width="520px">
      <el-form label-width="110px">
        <el-form-item label="备份记录">
          <span class="cell-mono">{{ verifyDialog.backupCode }}</span>
        </el-form-item>
        <el-form-item label="演练结果">
          <el-switch
            v-model="verifyDialog.restoreVerified"
            active-text="通过"
            inactive-text="未通过"
            inline-prompt
          />
        </el-form-item>
        <el-form-item label="说明">
          <el-input
            v-model="verifyDialog.message"
            type="textarea"
            :rows="3"
            maxlength="300"
            show-word-limit
            placeholder="恢复耗时、校验结果等"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="verifyDialog.visible = false">取消</el-button>
        <el-button type="primary" @click="submitVerify">提交</el-button>
      </template>
    </el-dialog>
  </section>
</template>

<style scoped>
.ops-page {
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

.hint {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.cell-mono {
  font-family: Consolas, 'Courier New', monospace;
  font-size: 12px;
  word-break: break-all;
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

.section-block {
  margin-bottom: 12px;
}

.block-title {
  font-size: 14px;
  font-weight: 700;
  color: var(--crm-ink);
  margin-bottom: 10px;
}

.el-pagination {
  margin-top: 10px;
  justify-content: center;
}

/* ---------------- 总览指标卡 ---------------- */
.stat-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(230px, 1fr));
  gap: 12px;
  margin-bottom: 12px;
}

.stat-card {
  display: flex;
  flex-direction: column;
  gap: 8px;
  background: var(--crm-card);
  border: 1px solid var(--crm-border);
  border-radius: 12px;
  padding: 14px 16px;
  box-shadow: 0 4px 16px rgba(31, 79, 134, 0.05);
}

.stat-label {
  font-size: 12px;
  color: var(--crm-muted);
}

.stat-line {
  display: flex;
  align-items: center;
  gap: 10px;
}

.stat-value {
  font-size: 20px;
  font-weight: 700;
  color: var(--crm-primary-dark);
  letter-spacing: 0.01em;
}

.stat-danger {
  color: var(--crm-danger);
}

.error-rate {
  font-size: 22px;
}

/* ---------------- 告警等级配色 ---------------- */
.sev-critical {
  --el-tag-bg-color: #fbeeed;
  --el-tag-border-color: #f0c7c5;
  --el-tag-text-color: #b8413d;
}

.sev-high {
  --el-tag-bg-color: #fdf1e6;
  --el-tag-border-color: #f3d3b6;
  --el-tag-text-color: #c96a2b;
}

.sev-medium {
  --el-tag-bg-color: #fdf8e2;
  --el-tag-border-color: #f0e1ac;
  --el-tag-text-color: #a8801f;
}

.sev-low {
  --el-tag-bg-color: #eef2f7;
  --el-tag-border-color: #dbe3ee;
  --el-tag-text-color: #6b7a90;
}

.detail-link {
  display: inline-block;
  margin-top: 2px;
  font-size: 12px;
  color: var(--crm-primary);
  cursor: help;
}

.drawer-hint {
  margin: 0 0 10px;
}
</style>
