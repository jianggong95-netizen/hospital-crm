<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import {
  approveExport,
  createExport,
  createMetric,
  createReport,
  downloadExport,
  getDashboard,
  getExports,
  getMetrics,
  getReportData,
  getReports,
  rejectExport,
  updateMetric,
  updateMetricStatus,
  updateReportStatus
} from '@/api/analytics'
import type {
  DashboardView,
  ExportStatus,
  ExportView,
  MetricStatus,
  MetricUnit,
  MetricView,
  ReportDataView,
  ReportType,
  ReportView
} from '@/types/analytics'

const auth = useAuthStore()
const canMetricManage = computed(() => auth.has('crm:analytics:metric:manage'))
const canReportManage = computed(() => auth.has('crm:analytics:report:manage'))
const canExportRequest = computed(() => auth.has('crm:analytics:export:request'))
const canExportApprove = computed(() => auth.has('crm:analytics:export:approve'))
const canExportDownload = computed(() => auth.has('crm:analytics:export:download'))

const activeTab = ref<'dashboard' | 'metrics' | 'reports' | 'exports'>('dashboard')

// ------------------------------------------------------------------
// 文案与格式
// ------------------------------------------------------------------
const categoryLabels: Record<string, string> = {
  customer: '客户',
  operation: '运营',
  appointment: '预约',
  revenue: '收入',
  followup: '随访',
  complaint: '投诉',
  callcenter: '呼叫中心',
  insurance: '商保'
}

const unitLabels: Record<MetricUnit, string> = {
  count: '数量',
  yuan: '金额（元）',
  percent: '百分比',
  minute: '时长（分钟）'
}

const reportTypeLabels: Record<ReportType, string> = {
  metric_summary: '指标汇总',
  customer_detail: '客户明细'
}

const exportStatusLabels: Record<ExportStatus, string> = {
  pending: '待审批',
  approved: '已批准',
  rejected: '已驳回'
}

function exportStatusTag(status: ExportStatus) {
  if (status === 'approved') return 'success'
  if (status === 'rejected') return 'danger'
  return 'warning'
}

function pad2(value: number) {
  return String(value).padStart(2, '0')
}

function formatDate(date: Date) {
  return `${date.getFullYear()}-${pad2(date.getMonth() + 1)}-${pad2(date.getDate())}`
}

function defaultRange(): [string, string] {
  const to = new Date()
  const from = new Date()
  from.setDate(from.getDate() - 29)
  return [formatDate(from), formatDate(to)]
}

function formatDateTime(value?: string | null) {
  if (!value) return '—'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value.replace('T', ' ').slice(0, 19)
  return date.toLocaleString('zh-CN', { hour12: false })
}

function defaultDecimals(unit: MetricUnit | null) {
  if (unit === 'yuan') return 2
  if (unit === 'percent' || unit === 'minute') return 1
  return 0
}

function formatMetricValue(value: number, unit: MetricUnit | null) {
  const dp = defaultDecimals(unit)
  if (unit === 'percent') return `${value.toFixed(dp)}%`
  const numeric = value.toLocaleString('zh-CN', {
    minimumFractionDigits: dp,
    maximumFractionDigits: dp
  })
  if (unit === 'yuan') return `¥${numeric}`
  if (unit === 'minute') return `${numeric} 分钟`
  return numeric
}

function formatCell(value: unknown) {
  if (value === null || value === undefined || value === '') return '—'
  if (typeof value === 'number') return value.toLocaleString('zh-CN')
  if (typeof value === 'boolean') return value ? '是' : '否'
  return String(value)
}

// ------------------------------------------------------------------
// 指标概览（看板）
// ------------------------------------------------------------------
const dashboard = ref<DashboardView>()
const dashboardLoading = ref(false)
const dashboardRange = ref<[string, string]>(defaultRange())

async function loadDashboard() {
  dashboardLoading.value = true
  try {
    dashboard.value = await getDashboard(dashboardRange.value[0], dashboardRange.value[1])
  } catch (error) {
    ElMessage.error((error as Error).message)
  } finally {
    dashboardLoading.value = false
  }
}

// ------------------------------------------------------------------
// 指标口径
// ------------------------------------------------------------------
const metrics = ref<MetricView[]>([])
const metricsLoading = ref(false)
const metricsTotal = ref(0)
const metricsQuery = reactive({ keyword: '', status: '', category: '', page: 1, pageSize: 20 })

async function loadMetrics() {
  metricsLoading.value = true
  try {
    const result = await getMetrics({
      keyword: metricsQuery.keyword || undefined,
      status: metricsQuery.status || undefined,
      category: metricsQuery.category || undefined,
      page: metricsQuery.page,
      pageSize: metricsQuery.pageSize
    })
    metrics.value = result.items
    metricsTotal.value = result.total
  } catch (error) {
    ElMessage.error((error as Error).message)
  } finally {
    metricsLoading.value = false
  }
}

function searchMetrics() {
  metricsQuery.page = 1
  void loadMetrics()
}

const metricFormRef = ref<FormInstance>()
const metricDialog = reactive({
  visible: false,
  editingId: '',
  metricCode: '',
  metricName: '',
  category: 'customer',
  caliberDescription: '',
  dataSource: '',
  unit: 'count' as MetricUnit | '',
  decimalPlaces: 0,
  remark: ''
})

const metricRules: FormRules = {
  metricCode: [{ required: true, message: '请填写指标编码', trigger: 'blur' }],
  metricName: [{ required: true, message: '请填写指标名称', trigger: 'blur' }],
  category: [{ required: true, message: '请选择指标分类', trigger: 'change' }],
  caliberDescription: [{ required: true, message: '请填写指标口径说明', trigger: 'blur' }],
  dataSource: [{ required: true, message: '请填写数据来源', trigger: 'blur' }]
}

function openMetricDialog(row?: MetricView) {
  metricDialog.editingId = row?.id ?? ''
  metricDialog.metricCode = row?.metricCode ?? ''
  metricDialog.metricName = row?.metricName ?? ''
  metricDialog.category = row?.category ?? 'customer'
  metricDialog.caliberDescription = row?.caliberDescription ?? ''
  metricDialog.dataSource = row?.dataSource ?? ''
  metricDialog.unit = row?.unit ?? ''
  metricDialog.decimalPlaces = row?.decimalPlaces ?? 0
  metricDialog.remark = row?.remark ?? ''
  metricDialog.visible = true
  metricFormRef.value?.clearValidate()
}

async function submitMetric() {
  if (!metricFormRef.value) return
  const valid = await metricFormRef.value.validate().catch(() => false)
  if (!valid) return
  const unit = metricDialog.unit || undefined
  const base = {
    metricName: metricDialog.metricName.trim(),
    category: metricDialog.category,
    caliberDescription: metricDialog.caliberDescription.trim(),
    dataSource: metricDialog.dataSource.trim(),
    unit,
    decimalPlaces: metricDialog.decimalPlaces,
    remark: metricDialog.remark.trim() || undefined
  }
  try {
    if (metricDialog.editingId) {
      await updateMetric(metricDialog.editingId, base)
      ElMessage.success('指标已更新')
    } else {
      await createMetric({ metricCode: metricDialog.metricCode.trim(), ...base })
      ElMessage.success('指标已创建')
    }
    metricDialog.visible = false
    await loadMetrics()
  } catch (error) {
    ElMessage.error((error as Error).message)
  }
}

async function toggleMetricStatus(row: MetricView) {
  const next: MetricStatus = row.status === 'enabled' ? 'disabled' : 'enabled'
  try {
    await ElMessageBox.confirm(
      `确认${next === 'enabled' ? '启用' : '停用'}指标「${row.metricName}」？`,
      next === 'enabled' ? '启用指标' : '停用指标',
      { confirmButtonText: '确认', cancelButtonText: '取消', type: 'warning' }
    )
  } catch {
    return
  }
  try {
    await updateMetricStatus(row.id, next)
    ElMessage.success(next === 'enabled' ? '已启用' : '已停用')
    await loadMetrics()
  } catch (error) {
    ElMessage.error((error as Error).message)
  }
}

// ------------------------------------------------------------------
// 报表查询
// ------------------------------------------------------------------
const reports = ref<ReportView[]>([])
const reportsLoading = ref(false)
const reportsTotal = ref(0)
const reportsQuery = reactive({ keyword: '', page: 1, pageSize: 20 })

async function loadReports() {
  reportsLoading.value = true
  try {
    const result = await getReports({
      keyword: reportsQuery.keyword || undefined,
      page: reportsQuery.page,
      pageSize: reportsQuery.pageSize
    })
    reports.value = result.items
    reportsTotal.value = result.total
  } catch (error) {
    ElMessage.error((error as Error).message)
  } finally {
    reportsLoading.value = false
  }
}

function searchReports() {
  reportsQuery.page = 1
  void loadReports()
}

const dataDrawerVisible = ref(false)
const dataDrawerReport = ref<ReportView>()
const drawerRange = ref<[string, string]>(defaultRange())
const reportData = ref<ReportDataView>()
const reportDataLoading = ref(false)

function openReportData(row: ReportView) {
  dataDrawerReport.value = row
  drawerRange.value = defaultRange()
  reportData.value = undefined
  dataDrawerVisible.value = true
  void loadReportData()
}

async function loadReportData() {
  const report = dataDrawerReport.value
  if (!report) return
  reportDataLoading.value = true
  try {
    reportData.value = await getReportData(
      report.reportCode,
      drawerRange.value[0],
      drawerRange.value[1]
    )
  } catch (error) {
    ElMessage.error((error as Error).message)
  } finally {
    reportDataLoading.value = false
  }
}

const metricOptions = ref<MetricView[]>([])
const reportFormRef = ref<FormInstance>()
const reportDialog = reactive({
  visible: false,
  reportCode: '',
  reportName: '',
  description: '',
  reportType: 'metric_summary' as ReportType,
  metricCodes: [] as string[]
})

const reportRules: FormRules = {
  reportCode: [{ required: true, message: '请填写报表编码', trigger: 'blur' }],
  reportName: [{ required: true, message: '请填写报表名称', trigger: 'blur' }],
  reportType: [{ required: true, message: '请选择报表类型', trigger: 'change' }]
}

async function openReportDialog() {
  reportDialog.reportCode = ''
  reportDialog.reportName = ''
  reportDialog.description = ''
  reportDialog.reportType = 'metric_summary'
  reportDialog.metricCodes = []
  reportDialog.visible = true
  reportFormRef.value?.clearValidate()
  if (!metricOptions.value.length) {
    try {
      const result = await getMetrics({ page: 1, pageSize: 200 })
      metricOptions.value = result.items
    } catch (error) {
      ElMessage.error((error as Error).message)
    }
  }
}

async function submitReport() {
  if (!reportFormRef.value) return
  const valid = await reportFormRef.value.validate().catch(() => false)
  if (!valid) return
  try {
    await createReport({
      reportCode: reportDialog.reportCode.trim(),
      reportName: reportDialog.reportName.trim(),
      description: reportDialog.description.trim() || undefined,
      reportType: reportDialog.reportType,
      metricCodes: reportDialog.reportType === 'metric_summary' ? reportDialog.metricCodes : undefined
    })
    ElMessage.success('报表已创建')
    reportDialog.visible = false
    await loadReports()
  } catch (error) {
    ElMessage.error((error as Error).message)
  }
}

async function toggleReportStatus(row: ReportView) {
  const next = row.status === 'enabled' ? 'disabled' : 'enabled'
  try {
    await ElMessageBox.confirm(
      `确认${next === 'enabled' ? '启用' : '停用'}报表「${row.reportName}」？`,
      next === 'enabled' ? '启用报表' : '停用报表',
      { confirmButtonText: '确认', cancelButtonText: '取消', type: 'warning' }
    )
  } catch {
    return
  }
  try {
    await updateReportStatus(row.id, next)
    ElMessage.success(next === 'enabled' ? '已启用' : '已停用')
    await loadReports()
  } catch (error) {
    ElMessage.error((error as Error).message)
  }
}

// ------------------------------------------------------------------
// 导出审批
// ------------------------------------------------------------------
const exportsList = ref<ExportView[]>([])
const exportsLoading = ref(false)
const exportsTotal = ref(0)
const exportsQuery = reactive({ status: '', page: 1, pageSize: 20 })

async function loadExports() {
  exportsLoading.value = true
  try {
    const result = await getExports({
      status: exportsQuery.status || undefined,
      page: exportsQuery.page,
      pageSize: exportsQuery.pageSize
    })
    exportsList.value = result.items
    exportsTotal.value = result.total
  } catch (error) {
    ElMessage.error((error as Error).message)
  } finally {
    exportsLoading.value = false
  }
}

function searchExports() {
  exportsQuery.page = 1
  void loadExports()
}

const exportFormRef = ref<FormInstance>()
const exportDialog = reactive({
  visible: false,
  reportCode: '',
  dateRange: defaultRange() as [string, string],
  reason: ''
})

const exportRules: FormRules = {
  reportCode: [{ required: true, message: '请选择报表', trigger: 'change' }],
  reason: [{ required: true, message: '请填写导出原因', trigger: 'blur' }]
}

async function openExportDialog() {
  exportDialog.reportCode = ''
  exportDialog.dateRange = defaultRange()
  exportDialog.reason = ''
  exportDialog.visible = true
  exportFormRef.value?.clearValidate()
  if (!reports.value.length) {
    await loadReports()
  }
}

async function submitExport() {
  if (!exportFormRef.value) return
  const valid = await exportFormRef.value.validate().catch(() => false)
  if (!valid) return
  try {
    await createExport({
      reportCode: exportDialog.reportCode,
      dataFrom: exportDialog.dateRange[0],
      dataTo: exportDialog.dateRange[1],
      reason: exportDialog.reason.trim()
    })
    ElMessage.success('导出申请已提交，等待审批')
    exportDialog.visible = false
    await loadExports()
  } catch (error) {
    ElMessage.error((error as Error).message)
  }
}

async function handleApprove(row: ExportView) {
  try {
    await ElMessageBox.confirm(
      `批准将生成脱敏文件并留痕，确认批准「${row.reportName}」的导出申请？`,
      '批准导出申请',
      { confirmButtonText: '批准', cancelButtonText: '取消', type: 'warning' }
    )
  } catch {
    return
  }
  try {
    await approveExport(row.id)
    ElMessage.success('已批准，脱敏文件已生成')
    await loadExports()
  } catch (error) {
    ElMessage.error((error as Error).message)
  }
}

const rejectDialog = reactive({
  visible: false,
  exportId: '',
  reportName: '',
  reason: ''
})

function openRejectDialog(row: ExportView) {
  rejectDialog.exportId = row.id
  rejectDialog.reportName = row.reportName
  rejectDialog.reason = ''
  rejectDialog.visible = true
}

async function submitReject() {
  if (!rejectDialog.reason.trim()) {
    ElMessage.warning('请填写驳回原因')
    return
  }
  try {
    await rejectExport(rejectDialog.exportId, rejectDialog.reason.trim())
    ElMessage.success('已驳回该导出申请')
    rejectDialog.visible = false
    await loadExports()
  } catch (error) {
    ElMessage.error((error as Error).message)
  }
}

async function handleDownload(row: ExportView) {
  try {
    await downloadExport(row.id, row.reportCode)
  } catch (error) {
    ElMessage.error((error as Error).message)
  }
}

// ------------------------------------------------------------------
// 标签页切换与初始化
// ------------------------------------------------------------------
function onTabChange(name: string | number) {
  if (name === 'metrics' && !metrics.value.length) {
    void loadMetrics()
  }
  if (name === 'reports' && !reports.value.length) {
    void loadReports()
  }
  if (name === 'exports' && !exportsList.value.length) {
    void loadExports()
  }
}

onMounted(() => {
  void loadDashboard()
})
</script>

<template>
  <section class="an-page">
    <header class="page-head">
      <div>
        <h2>运营分析</h2>
        <p class="page-desc">
          运营分析——指标定义、看板、报表与导出审批；所有数据时间范围可追溯。
        </p>
      </div>
    </header>

    <el-tabs v-model="activeTab" class="an-tabs" @tab-change="onTabChange">
      <!-- ============================================================ -->
      <el-tab-pane label="指标概览" name="dashboard">
        <div class="toolbar">
          <el-date-picker
            v-model="dashboardRange"
            type="daterange"
            size="small"
            style="width: 240px"
            value-format="YYYY-MM-DD"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
          />
          <el-button size="small" type="primary" @click="loadDashboard">查询</el-button>
        </div>

        <div v-if="dashboard" class="trace-bar">
          数据时间范围 {{ dashboard.dataFrom }} ~ {{ dashboard.dataTo }} · 生成时间
          {{ formatDateTime(dashboard.generatedAt) }} · 数据范围：{{
            dashboard.scopeAll ? '全院' : '组织/个人'
          }}
        </div>

        <div v-loading="dashboardLoading" class="panel-card">
          <div class="metric-grid">
            <div v-for="card in dashboard?.cards ?? []" :key="card.metricCode" class="metric-card">
              <div class="metric-name">
                <span>{{ card.metricName }}</span>
                <el-tooltip
                  v-if="card.caliberDescription"
                  :content="card.caliberDescription"
                  placement="top"
                >
                  <span class="caliber-badge">口径</span>
                </el-tooltip>
              </div>
              <div v-if="card.value !== null" class="metric-value">
                {{ formatMetricValue(card.value, card.unit) }}
              </div>
              <div v-else class="metric-limited">{{ card.scopeNote || '受限' }}</div>
              <div class="metric-code hint">{{ card.metricCode }}</div>
            </div>
          </div>
          <p v-if="dashboard && !dashboard.cards.length && !dashboardLoading" class="empty">
            暂无指标数据，请先在「指标口径」中定义并启用指标
          </p>
        </div>
      </el-tab-pane>

      <!-- ============================================================ -->
      <el-tab-pane label="指标口径" name="metrics">
        <div class="toolbar">
          <el-input
            v-model="metricsQuery.keyword"
            placeholder="编码 / 名称"
            clearable
            size="small"
            style="width: 180px"
            @keyup.enter="searchMetrics"
          />
          <el-select
            v-model="metricsQuery.category"
            placeholder="全部分类"
            clearable
            size="small"
            style="width: 130px"
          >
            <el-option v-for="(label, key) in categoryLabels" :key="key" :label="label" :value="key" />
          </el-select>
          <el-select
            v-model="metricsQuery.status"
            placeholder="全部状态"
            clearable
            size="small"
            style="width: 110px"
          >
            <el-option label="启用" value="enabled" />
            <el-option label="停用" value="disabled" />
          </el-select>
          <el-button size="small" type="primary" @click="searchMetrics">查询</el-button>
          <span class="spacer" />
          <el-button v-if="canMetricManage" size="small" type="primary" @click="openMetricDialog()">
            新建指标
          </el-button>
        </div>

        <div class="panel-card">
          <el-table v-loading="metricsLoading" :data="metrics" size="small">
            <el-table-column prop="metricCode" label="编码" width="160" />
            <el-table-column prop="metricName" label="名称" min-width="140" show-overflow-tooltip />
            <el-table-column label="分类" width="100">
              <template #default="{ row }">{{ categoryLabels[row.category] ?? row.category }}</template>
            </el-table-column>
            <el-table-column
              prop="caliberDescription"
              label="口径说明"
              min-width="220"
              show-overflow-tooltip
            />
            <el-table-column prop="dataSource" label="来源" min-width="140" show-overflow-tooltip />
            <el-table-column label="单位" width="100">
              <template #default="{ row }">
                {{ row.unit ? unitLabels[row.unit as MetricUnit] ?? row.unit : '—' }}
              </template>
            </el-table-column>
            <el-table-column label="状态" width="80">
              <template #default="{ row }">
                <el-tag :type="row.status === 'enabled' ? 'success' : 'info'" size="small">
                  {{ row.status === 'enabled' ? '启用' : '停用' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column v-if="canMetricManage" label="操作" width="120" fixed="right">
              <template #default="{ row }">
                <el-button size="small" link type="primary" @click="openMetricDialog(row)">
                  编辑
                </el-button>
                <el-button
                  size="small"
                  link
                  :type="row.status === 'enabled' ? 'danger' : 'success'"
                  @click="toggleMetricStatus(row)"
                >
                  {{ row.status === 'enabled' ? '停用' : '启用' }}
                </el-button>
              </template>
            </el-table-column>
          </el-table>
          <el-pagination
            v-if="metricsTotal > metricsQuery.pageSize"
            v-model:current-page="metricsQuery.page"
            :page-size="metricsQuery.pageSize"
            :total="metricsTotal"
            layout="prev, pager, next"
            small
            @current-change="loadMetrics"
          />
        </div>
      </el-tab-pane>

      <!-- ============================================================ -->
      <el-tab-pane label="报表查询" name="reports">
        <div class="toolbar">
          <el-input
            v-model="reportsQuery.keyword"
            placeholder="编码 / 名称"
            clearable
            size="small"
            style="width: 200px"
            @keyup.enter="searchReports"
          />
          <el-button size="small" type="primary" @click="searchReports">查询</el-button>
          <span class="spacer" />
          <el-button v-if="canReportManage" size="small" type="primary" @click="openReportDialog">
            新建报表
          </el-button>
        </div>

        <div class="panel-card">
          <el-table v-loading="reportsLoading" :data="reports" size="small">
            <el-table-column prop="reportCode" label="编码" width="170" />
            <el-table-column prop="reportName" label="名称" min-width="160" show-overflow-tooltip />
            <el-table-column label="说明" min-width="200" show-overflow-tooltip>
              <template #default="{ row }">{{ row.description || '—' }}</template>
            </el-table-column>
            <el-table-column label="类型" width="100">
              <template #default="{ row }">
                {{ reportTypeLabels[row.reportType as ReportType] ?? row.reportType }}
              </template>
            </el-table-column>
            <el-table-column label="状态" width="80">
              <template #default="{ row }">
                <el-tag :type="row.status === 'enabled' ? 'success' : 'info'" size="small">
                  {{ row.status === 'enabled' ? '启用' : '停用' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="160" fixed="right">
              <template #default="{ row }">
                <el-button size="small" link type="primary" @click="openReportData(row)">
                  查询数据
                </el-button>
                <el-button
                  v-if="canReportManage"
                  size="small"
                  link
                  :type="row.status === 'enabled' ? 'danger' : 'success'"
                  @click="toggleReportStatus(row)"
                >
                  {{ row.status === 'enabled' ? '停用' : '启用' }}
                </el-button>
              </template>
            </el-table-column>
          </el-table>
          <el-pagination
            v-if="reportsTotal > reportsQuery.pageSize"
            v-model:current-page="reportsQuery.page"
            :page-size="reportsQuery.pageSize"
            :total="reportsTotal"
            layout="prev, pager, next"
            small
            @current-change="loadReports"
          />
        </div>
      </el-tab-pane>

      <!-- ============================================================ -->
      <el-tab-pane label="导出审批" name="exports">
        <div class="toolbar">
          <el-select
            v-model="exportsQuery.status"
            placeholder="全部状态"
            clearable
            size="small"
            style="width: 120px"
          >
            <el-option label="待审批" value="pending" />
            <el-option label="已批准" value="approved" />
            <el-option label="已驳回" value="rejected" />
          </el-select>
          <el-button size="small" type="primary" @click="searchExports">查询</el-button>
          <span class="spacer" />
          <el-button v-if="canExportRequest" size="small" type="primary" @click="openExportDialog">
            发起导出申请
          </el-button>
        </div>

        <div class="panel-card">
          <el-table v-loading="exportsLoading" :data="exportsList" size="small">
            <el-table-column label="报表" min-width="160">
              <template #default="{ row }">
                <div>{{ row.reportName }}</div>
                <div class="hint">{{ row.reportCode }}</div>
              </template>
            </el-table-column>
            <el-table-column label="申请人" width="100">
              <template #default="{ row }">{{ row.requesterName || '—' }}</template>
            </el-table-column>
            <el-table-column label="时间范围" width="180">
              <template #default="{ row }">{{ row.dataFrom }} ~ {{ row.dataTo }}</template>
            </el-table-column>
            <el-table-column prop="reason" label="原因" min-width="150" show-overflow-tooltip />
            <el-table-column label="状态" width="90">
              <template #default="{ row }">
                <el-tag :type="exportStatusTag(row.status as ExportStatus)" size="small">
                  {{ exportStatusLabels[row.status as ExportStatus] }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="审批人 / 时间" width="140">
              <template #default="{ row }">
                <div v-if="row.approverName">{{ row.approverName }}</div>
                <div class="hint">
                  {{ row.approvedAt ? formatDateTime(row.approvedAt) : row.status === 'pending' ? '待审批' : '—' }}
                </div>
              </template>
            </el-table-column>
            <el-table-column label="驳回原因" min-width="130" show-overflow-tooltip>
              <template #default="{ row }">{{ row.rejectReason || '—' }}</template>
            </el-table-column>
            <el-table-column label="行数" width="80" align="right">
              <template #default="{ row }">{{ row.rowCount ?? '—' }}</template>
            </el-table-column>
            <el-table-column label="文件哈希" width="120">
              <template #default="{ row }">
                <span class="hint">{{ row.fileHash ? row.fileHash.slice(0, 12) : '—' }}</span>
              </template>
            </el-table-column>
            <el-table-column label="申请时间" width="140">
              <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
            </el-table-column>
            <el-table-column label="操作" width="110" fixed="right">
              <template #default="{ row }">
                <template v-if="row.status === 'pending' && canExportApprove">
                  <el-button size="small" link type="success" @click="handleApprove(row)">
                    批准
                  </el-button>
                  <el-button size="small" link type="danger" @click="openRejectDialog(row)">
                    驳回
                  </el-button>
                </template>
                <el-button
                  v-else-if="row.status === 'approved' && canExportDownload"
                  size="small"
                  link
                  type="primary"
                  @click="handleDownload(row)"
                >
                  下载
                </el-button>
                <span v-else class="hint">—</span>
              </template>
            </el-table-column>
          </el-table>
          <el-pagination
            v-if="exportsTotal > exportsQuery.pageSize"
            v-model:current-page="exportsQuery.page"
            :page-size="exportsQuery.pageSize"
            :total="exportsTotal"
            layout="prev, pager, next"
            small
            @current-change="loadExports"
          />
        </div>
      </el-tab-pane>
    </el-tabs>

    <!-- 指标新建 / 编辑 -->
    <el-dialog
      v-model="metricDialog.visible"
      :title="metricDialog.editingId ? '编辑指标' : '新建指标'"
      width="560px"
    >
      <el-form ref="metricFormRef" :model="metricDialog" :rules="metricRules" label-width="100px">
        <el-form-item label="指标编码" prop="metricCode">
          <el-input
            v-model="metricDialog.metricCode"
            :disabled="!!metricDialog.editingId"
            maxlength="64"
            placeholder="如 APPT_CONFIRM_RATE"
          />
        </el-form-item>
        <el-form-item label="指标名称" prop="metricName">
          <el-input v-model="metricDialog.metricName" maxlength="120" />
        </el-form-item>
        <el-form-item label="分类" prop="category">
          <el-select v-model="metricDialog.category" style="width: 100%">
            <el-option
              v-for="(label, key) in categoryLabels"
              :key="key"
              :label="`${label}（${key}）`"
              :value="key"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="口径说明" prop="caliberDescription">
          <el-input
            v-model="metricDialog.caliberDescription"
            type="textarea"
            :rows="3"
            maxlength="500"
            show-word-limit
            placeholder="统计范围、计算方式、过滤条件等"
          />
        </el-form-item>
        <el-form-item label="数据来源" prop="dataSource">
          <el-input v-model="metricDialog.dataSource" maxlength="120" placeholder="如 crm_appointment" />
        </el-form-item>
        <el-form-item label="单位">
          <el-select v-model="metricDialog.unit" clearable placeholder="无单位" style="width: 100%">
            <el-option v-for="(label, key) in unitLabels" :key="key" :label="label" :value="key" />
          </el-select>
        </el-form-item>
        <el-form-item label="小数位数">
          <el-input-number
            v-model="metricDialog.decimalPlaces"
            :min="0"
            :max="4"
            controls-position="right"
          />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="metricDialog.remark" type="textarea" :rows="2" maxlength="500" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="metricDialog.visible = false">取消</el-button>
        <el-button type="primary" @click="submitMetric">
          {{ metricDialog.editingId ? '保存修改' : '创建指标' }}
        </el-button>
      </template>
    </el-dialog>

    <!-- 报表新建 -->
    <el-dialog v-model="reportDialog.visible" title="新建报表" width="560px">
      <el-form ref="reportFormRef" :model="reportDialog" :rules="reportRules" label-width="100px">
        <el-form-item label="报表编码" prop="reportCode">
          <el-input v-model="reportDialog.reportCode" maxlength="64" placeholder="如 RPT_WEEKLY_OPS" />
        </el-form-item>
        <el-form-item label="报表名称" prop="reportName">
          <el-input v-model="reportDialog.reportName" maxlength="120" />
        </el-form-item>
        <el-form-item label="说明">
          <el-input v-model="reportDialog.description" type="textarea" :rows="2" maxlength="500" />
        </el-form-item>
        <el-form-item label="类型" prop="reportType">
          <el-select v-model="reportDialog.reportType" style="width: 100%">
            <el-option
              v-for="(label, key) in reportTypeLabels"
              :key="key"
              :label="label"
              :value="key"
            />
          </el-select>
        </el-form-item>
        <el-form-item v-if="reportDialog.reportType === 'metric_summary'" label="包含指标">
          <el-select
            v-model="reportDialog.metricCodes"
            multiple
            filterable
            collapse-tags
            collapse-tags-tooltip
            placeholder="选择指标（可多选）"
            style="width: 100%"
          >
            <el-option
              v-for="item in metricOptions"
              :key="item.id"
              :label="`${item.metricName}（${item.metricCode}）`"
              :value="item.metricCode"
            />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="reportDialog.visible = false">取消</el-button>
        <el-button type="primary" @click="submitReport">创建报表</el-button>
      </template>
    </el-dialog>

    <!-- 发起导出申请 -->
    <el-dialog v-model="exportDialog.visible" title="发起导出申请" width="520px">
      <el-form ref="exportFormRef" :model="exportDialog" :rules="exportRules" label-width="100px">
        <el-form-item label="报表" prop="reportCode">
          <el-select v-model="exportDialog.reportCode" filterable placeholder="选择报表" style="width: 100%">
            <el-option
              v-for="item in reports"
              :key="item.reportCode"
              :label="`${item.reportName}（${item.reportCode}）`"
              :value="item.reportCode"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="时间范围" required>
          <el-date-picker
            v-model="exportDialog.dateRange"
            type="daterange"
            value-format="YYYY-MM-DD"
            style="width: 100%"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
          />
        </el-form-item>
        <el-form-item label="导出原因" prop="reason">
          <el-input
            v-model="exportDialog.reason"
            type="textarea"
            :rows="3"
            maxlength="300"
            show-word-limit
            placeholder="说明导出用途；审批通过后将生成脱敏文件并留痕"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="exportDialog.visible = false">取消</el-button>
        <el-button type="primary" @click="submitExport">提交申请</el-button>
      </template>
    </el-dialog>

    <!-- 驳回导出申请 -->
    <el-dialog v-model="rejectDialog.visible" title="驳回导出申请" width="460px">
      <el-form label-width="90px">
        <el-form-item label="申请报表">
          <span>{{ rejectDialog.reportName }}</span>
        </el-form-item>
        <el-form-item label="驳回原因" required>
          <el-input
            v-model="rejectDialog.reason"
            type="textarea"
            :rows="3"
            maxlength="300"
            placeholder="请填写驳回原因"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="rejectDialog.visible = false">取消</el-button>
        <el-button type="danger" @click="submitReject">确认驳回</el-button>
      </template>
    </el-dialog>

    <!-- 报表数据抽屉 -->
    <el-drawer
      v-model="dataDrawerVisible"
      :title="dataDrawerReport ? `报表数据 · ${dataDrawerReport.reportName}` : '报表数据'"
      size="900px"
    >
      <div class="toolbar">
        <el-date-picker
          v-model="drawerRange"
          type="daterange"
          size="small"
          style="width: 240px"
          value-format="YYYY-MM-DD"
          start-placeholder="开始日期"
          end-placeholder="结束日期"
        />
        <el-button size="small" type="primary" @click="loadReportData">查询</el-button>
      </div>

      <div v-if="reportData" class="trace-bar">
        数据时间范围 {{ reportData.dataFrom }} ~ {{ reportData.dataTo }} · 生成时间
        {{ formatDateTime(reportData.generatedAt) }} · 共 {{ reportData.rowCount }} 行 · 数据范围：{{
          reportData.scopeAll ? '全院' : '组织/个人'
        }}
      </div>
      <p v-if="dataDrawerReport?.reportType === 'customer_detail'" class="hint drawer-hint">
        客户明细报表中的手机号、证件号等敏感字段已由后端脱敏，此处直接展示脱敏值。
      </p>

      <div v-loading="reportDataLoading">
        <el-table v-if="reportData" :data="reportData.rows" size="small" border>
          <el-table-column
            v-for="col in reportData.columns"
            :key="col.key"
            :label="col.label"
            min-width="140"
            show-overflow-tooltip
          >
            <template #default="{ row }">{{ formatCell(row[col.key]) }}</template>
          </el-table-column>
        </el-table>
        <p v-else-if="!reportDataLoading" class="empty">请选择时间范围后点击查询</p>
        <p v-if="reportData && !reportData.rows.length && !reportDataLoading" class="empty">
          该时间范围内无数据
        </p>
      </div>
    </el-drawer>
  </section>
</template>

<style scoped>
.an-page {
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

/* ---------------- 追溯信息条 ---------------- */
.trace-bar {
  background: var(--crm-primary-soft);
  border: 1px solid #d3e3f6;
  color: var(--crm-primary-dark);
  border-radius: 10px;
  padding: 8px 12px;
  font-size: 12px;
  line-height: 1.6;
  margin-bottom: 12px;
}

.drawer-hint {
  margin: 0 0 10px;
}

/* ---------------- 指标卡片 ---------------- */
.metric-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(230px, 1fr));
  gap: 12px;
}

.metric-card {
  display: flex;
  flex-direction: column;
  gap: 8px;
  background: #f9fbfe;
  border: 1px solid var(--crm-border);
  border-radius: 12px;
  padding: 14px 16px;
}

.metric-name {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  color: var(--crm-ink-soft);
}

.caliber-badge {
  font-size: 10px;
  line-height: 16px;
  padding: 0 6px;
  border-radius: 999px;
  border: 1px solid #b0c8e4;
  background: #fff;
  color: var(--crm-primary);
  cursor: help;
}

.metric-value {
  font-size: 22px;
  font-weight: 700;
  color: var(--crm-primary-dark);
  letter-spacing: 0.01em;
}

.metric-limited {
  font-size: 13px;
  color: var(--crm-muted);
}

.metric-code {
  font-size: 11px;
  word-break: break-all;
}
</style>
