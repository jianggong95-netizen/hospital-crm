<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import { fetchCustomers } from '@/api/customers'
import {
  addLeadContact,
  assignLead,
  changeActivityStatus,
  changeInvitationStatus,
  changeLeadStatus,
  changeParticipantStatus,
  convertLead,
  createActivity,
  createInvitation,
  createLead,
  fetchActivities,
  fetchAssignees,
  fetchChannels,
  fetchInvitations,
  fetchLead,
  fetchLeads,
  fetchParticipants,
  mergeLead,
  registerParticipant
} from '@/api/operation'
import type { CustomerSummary } from '@/types/customer'
import type {
  ActivityView,
  Assignee,
  Channel,
  InvitationView,
  LeadDetail,
  LeadSummary,
  ParticipantView
} from '@/types/operation'

const auth = useAuthStore()
const canWriteLead = computed(() => auth.has('crm:lead:write'))
const canConvertLead = computed(() => auth.has('crm:lead:convert'))
const canAssignLead = computed(() => auth.has('crm:lead:assign'))
const canWriteInvitation = computed(() => auth.has('crm:invitation:write'))
const canWriteActivity = computed(() => auth.has('crm:activity:write'))

const activeTab = ref<'leads' | 'invitations' | 'activities'>('leads')
const channels = ref<Channel[]>([])

const leadStatusLabels: Record<string, string> = {
  NEW: '新线索',
  CONTACTING: '跟进中',
  QUALIFIED: '已确认意向',
  INVITED: '已邀约',
  CONVERTED: '已转客户',
  INVALID: '已作废',
  MERGED: '已合并'
}
const invitationStatusLabels: Record<string, string> = {
  PENDING: '待联系',
  CONTACTED: '已联系',
  ACCEPTED: '已接受',
  DECLINED: '已拒绝',
  CANCELLED: '已取消',
  EXPIRED: '已过期'
}
const activityStatusLabels: Record<string, string> = {
  DRAFT: '草稿',
  PUBLISHED: '已发布',
  CLOSED: '已结束',
  CANCELLED: '已取消'
}
const participantStatusLabels: Record<string, string> = {
  REGISTERED: '已报名',
  ATTENDED: '已签到',
  CANCELLED: '已取消',
  NO_SHOW: '未到场'
}
const activityTypeLabels: Record<string, string> = {
  health_lecture: '健康讲座',
  screening: '筛查活动',
  charity: '公益活动',
  marketing: '市场活动',
  other: '其他'
}
const contactTypeLabels: Record<string, string> = {
  phone: '电话',
  wechat: '微信',
  sms: '短信',
  visit: '到院',
  other: '其他'
}
const contactResultLabels: Record<string, string> = {
  CONNECTED: '已接通',
  NO_ANSWER: '未接通',
  REFUSED: '拒绝',
  INTERESTED: '有意向',
  CONVERTED: '已转化',
  OTHER: '其他'
}
const genderLabels: Record<string, string> = { unknown: '未知', male: '男', female: '女' }

function statusLabel(status?: string, map?: Record<string, string>) {
  return status ? map?.[status] ?? status : '—'
}

function statusBadge(status?: string) {
  switch (status) {
    case 'NEW':
    case 'PENDING':
    case 'DRAFT':
    case 'REGISTERED':
      return 'badge-neutral'
    case 'CONTACTING':
    case 'QUALIFIED':
    case 'CONTACTED':
    case 'PUBLISHED':
      return 'badge-warning'
    case 'INVITED':
    case 'ACCEPTED':
    case 'ATTENDED':
      return 'badge-success'
    case 'CONVERTED':
      return 'badge-success'
    case 'INVALID':
    case 'MERGED':
    case 'DECLINED':
    case 'CANCELLED':
    case 'EXPIRED':
    case 'NO_SHOW':
      return 'badge-danger'
    default:
      return 'badge-neutral'
  }
}

function formatDateTime(value?: string) {
  if (!value) return '—'
  return new Date(value).toLocaleString('zh-CN', { hour12: false })
}

function formatDate(value?: string) {
  return value || '—'
}

// ------------------------------------------------------------------
// 线索
// ------------------------------------------------------------------
const leadQuery = reactive({ keyword: '', status: '', channelCode: '', page: 1, pageSize: 20 })
const leadRows = ref<LeadSummary[]>([])
const leadTotal = ref(0)
const leadLoading = ref(false)

async function loadLeads() {
  leadLoading.value = true
  try {
    const result = await fetchLeads({
      keyword: leadQuery.keyword || undefined,
      status: leadQuery.status || undefined,
      channelCode: leadQuery.channelCode || undefined,
      page: leadQuery.page,
      pageSize: leadQuery.pageSize
    })
    leadRows.value = result.items
    leadTotal.value = result.total
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '加载线索失败')
  } finally {
    leadLoading.value = false
  }
}

function searchLeads() {
  leadQuery.page = 1
  loadLeads()
}

function resetLeadFilters() {
  leadQuery.keyword = ''
  leadQuery.status = ''
  leadQuery.channelCode = ''
  leadQuery.page = 1
  loadLeads()
}

const leadDialogVisible = ref(false)
const leadForm = reactive({
  name: '',
  gender: 'unknown',
  birthDate: '',
  phone: '',
  idNo: '',
  sourceChannelCode: '',
  remark: ''
})

function openLeadDialog() {
  Object.assign(leadForm, {
    name: '', gender: 'unknown', birthDate: '', phone: '', idNo: '', sourceChannelCode: '', remark: ''
  })
  leadDialogVisible.value = true
}

async function submitLead(forceCreate = false) {
  try {
    await createLead({
      name: leadForm.name,
      gender: leadForm.gender,
      birthDate: leadForm.birthDate || undefined,
      phone: leadForm.phone || undefined,
      idNo: leadForm.idNo || undefined,
      sourceChannelCode: leadForm.sourceChannelCode,
      remark: leadForm.remark || undefined,
      forceCreate
    })
    ElMessage.success('线索创建成功')
    leadDialogVisible.value = false
    searchLeads()
  } catch (error) {
    const message = error instanceof Error ? error.message : '创建线索失败'
    if (!forceCreate && message.includes('疑似重复线索')) {
      try {
        await ElMessageBox.confirm(`${message}。是否确认强制创建？`, '重复线索核验', {
          type: 'warning',
          confirmButtonText: '强制创建',
          cancelButtonText: '返回核验'
        })
        await submitLead(true)
      } catch {
        // 用户取消强制创建，保留弹窗以便核验
      }
      return
    }
    ElMessage.error(message)
  }
}

const detailVisible = ref(false)
const detailLoading = ref(false)
const detail = ref<LeadDetail>()

async function openLeadDetail(row: LeadSummary) {
  detailVisible.value = true
  detailLoading.value = true
  detail.value = undefined
  try {
    detail.value = await fetchLead(row.id)
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '加载线索详情失败')
  } finally {
    detailLoading.value = false
  }
}

async function reloadDetail() {
  if (!detail.value) return
  await openLeadDetail(detail.value.summary)
  await loadLeads()
}

const statusActions = computed(() => {
  if (!detail.value) return []
  const status = detail.value.summary.status
  const actions: { label: string; status: string }[] = []
  if (status === 'NEW') actions.push({ label: '开始跟进', status: 'CONTACTING' })
  if (status === 'CONTACTING') actions.push({ label: '确认意向', status: 'QUALIFIED' })
  if (status === 'QUALIFIED') actions.push({ label: '标记已邀约', status: 'INVITED' })
  if (status === 'INVITED') actions.push({ label: '退回确认', status: 'QUALIFIED' })
  if (['NEW', 'CONTACTING', 'QUALIFIED', 'INVITED'].includes(status)) {
    actions.push({ label: '作废', status: 'INVALID' })
  }
  return actions
})

async function changeDetailStatus(status: string) {
  if (!detail.value) return
  try {
    await ElMessageBox.confirm(
      `确认将线索状态变更为「${statusLabel(status, leadStatusLabels)}」？`,
      '状态变更',
      { type: 'warning' }
    )
    await changeLeadStatus(detail.value.summary.id, status)
    ElMessage.success('线索状态已更新')
    await reloadDetail()
  } catch (error) {
    if (error instanceof Error) ElMessage.error(error.message)
  }
}

const contactDialogVisible = ref(false)
const contactForm = reactive({
  contactType: 'phone',
  contactedAt: '',
  content: '',
  resultCode: 'CONNECTED',
  nextFollowUpAt: ''
})

function openContactDialog() {
  Object.assign(contactForm, {
    contactType: 'phone', contactedAt: '', content: '', resultCode: 'CONNECTED', nextFollowUpAt: ''
  })
  contactDialogVisible.value = true
}

async function submitContact() {
  if (!detail.value) return
  try {
    await addLeadContact(detail.value.summary.id, {
      contactType: contactForm.contactType,
      contactedAt: contactForm.contactedAt || undefined,
      content: contactForm.content,
      resultCode: contactForm.resultCode,
      nextFollowUpAt: contactForm.nextFollowUpAt || undefined
    })
    ElMessage.success('联系记录已保存')
    contactDialogVisible.value = false
    await reloadDetail()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '保存联系记录失败')
  }
}

const convertDialogVisible = ref(false)
const convertMode = ref<'NEW' | 'EXISTING'>('NEW')
const convertRemark = ref('')
const convertCustomerId = ref('')
const customerOptions = ref<CustomerSummary[]>([])

function openConvertDialog() {
  convertMode.value = 'NEW'
  convertRemark.value = ''
  convertCustomerId.value = ''
  customerOptions.value = []
  convertDialogVisible.value = true
}

async function searchConvertCustomers(keyword: string) {
  try {
    const result = await fetchCustomers({ keyword: keyword || undefined, page: 1, pageSize: 10 })
    customerOptions.value = result.items
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '搜索客户失败')
  }
}

async function submitConvert() {
  if (!detail.value) return
  try {
    const conversion = await convertLead(detail.value.summary.id, {
      customerId: convertMode.value === 'EXISTING' ? convertCustomerId.value : undefined,
      createCustomer: convertMode.value === 'NEW',
      remark: convertRemark.value || undefined
    })
    ElMessage.success(`已转化为客户 ${conversion.customerNo}`)
    convertDialogVisible.value = false
    await reloadDetail()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '线索转化失败')
  }
}

const mergeDialogVisible = ref(false)
const mergeKeyword = ref('')
const mergeTargetId = ref('')
const mergeReason = ref('')
const mergeOptions = ref<LeadSummary[]>([])

function openMergeDialog() {
  mergeKeyword.value = ''
  mergeTargetId.value = ''
  mergeReason.value = ''
  mergeOptions.value = []
  mergeDialogVisible.value = true
  searchMergeTargets('')
}

async function searchMergeTargets(keyword: string) {
  try {
    const result = await fetchLeads({ keyword: keyword || undefined, page: 1, pageSize: 10 })
    mergeOptions.value = result.items.filter(
      (item) => item.id !== detail.value?.summary.id && !['MERGED', 'INVALID'].includes(item.status)
    )
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '搜索线索失败')
  }
}

async function submitMerge() {
  if (!detail.value || !mergeTargetId.value) {
    ElMessage.warning('请选择目标线索')
    return
  }
  try {
    await mergeLead(detail.value.summary.id, mergeTargetId.value, mergeReason.value || undefined)
    ElMessage.success('线索已合并')
    mergeDialogVisible.value = false
    await reloadDetail()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '线索合并失败')
  }
}

const assignDialogVisible = ref(false)
const assignKeyword = ref('')
const assignToUserId = ref('')
const assignReason = ref('')
const assigneeOptions = ref<Assignee[]>([])

function openAssignDialog() {
  assignKeyword.value = ''
  assignToUserId.value = ''
  assignReason.value = ''
  assigneeOptions.value = []
  assignDialogVisible.value = true
  searchAssignees('')
}

async function searchAssignees(keyword: string) {
  try {
    assigneeOptions.value = await fetchAssignees(keyword || undefined)
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '加载员工列表失败')
  }
}

async function submitAssign() {
  if (!detail.value || !assignToUserId.value) {
    ElMessage.warning('请选择目标员工')
    return
  }
  try {
    await assignLead(detail.value.summary.id, assignToUserId.value, assignReason.value || undefined)
    ElMessage.success('线索已分派')
    assignDialogVisible.value = false
    await reloadDetail()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '线索分派失败')
  }
}

// ------------------------------------------------------------------
// 邀约
// ------------------------------------------------------------------
const invitationQuery = reactive({ keyword: '', status: '', page: 1, pageSize: 20 })
const invitationRows = ref<InvitationView[]>([])
const invitationTotal = ref(0)
const invitationLoading = ref(false)

async function loadInvitations() {
  invitationLoading.value = true
  try {
    const result = await fetchInvitations({
      keyword: invitationQuery.keyword || undefined,
      status: invitationQuery.status || undefined,
      page: invitationQuery.page,
      pageSize: invitationQuery.pageSize
    })
    invitationRows.value = result.items
    invitationTotal.value = result.total
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '加载邀约失败')
  } finally {
    invitationLoading.value = false
  }
}

function searchInvitations() {
  invitationQuery.page = 1
  loadInvitations()
}

const invitationDialogVisible = ref(false)
const invitationForm = reactive({
  targetType: 'LEAD' as 'LEAD' | 'CUSTOMER',
  targetId: '',
  channelCode: 'PHONE',
  title: '',
  plannedAt: '',
  remark: ''
})
const targetOptions = ref<{ id: string; label: string }[]>([])

function openInvitationDialog() {
  Object.assign(invitationForm, {
    targetType: 'LEAD', targetId: '', channelCode: 'PHONE', title: '', plannedAt: '', remark: ''
  })
  targetOptions.value = []
  invitationDialogVisible.value = true
  searchInvitationTargets('')
}

async function searchInvitationTargets(keyword: string) {
  try {
    if (invitationForm.targetType === 'LEAD') {
      const result = await fetchLeads({ keyword: keyword || undefined, page: 1, pageSize: 10 })
      targetOptions.value = result.items.map((lead) => ({
        id: lead.id,
        label: `${lead.leadNo} ${lead.name}（${statusLabel(lead.status, leadStatusLabels)}）`
      }))
    } else {
      const result = await fetchCustomers({ keyword: keyword || undefined, page: 1, pageSize: 10 })
      targetOptions.value = result.items.map((customer) => ({
        id: customer.id,
        label: `${customer.customerNo} ${customer.name}`
      }))
    }
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '搜索邀约对象失败')
  }
}

function onInvitationTargetTypeChange() {
  invitationForm.targetId = ''
  targetOptions.value = []
  searchInvitationTargets('')
}

async function submitInvitation() {
  if (!invitationForm.targetId) {
    ElMessage.warning('请选择邀约对象')
    return
  }
  try {
    await createInvitation({
      leadId: invitationForm.targetType === 'LEAD' ? invitationForm.targetId : undefined,
      customerId: invitationForm.targetType === 'CUSTOMER' ? invitationForm.targetId : undefined,
      channelCode: invitationForm.channelCode,
      title: invitationForm.title,
      plannedAt: invitationForm.plannedAt,
      remark: invitationForm.remark || undefined
    })
    ElMessage.success('邀约任务已创建')
    invitationDialogVisible.value = false
    searchInvitations()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '创建邀约失败')
  }
}

async function updateInvitationStatus(row: InvitationView, status: string) {
  try {
    const result = status === 'DECLINED' ? '客户拒绝' : status === 'ACCEPTED' ? '客户接受' : undefined
    await changeInvitationStatus(row.id, status, result)
    ElMessage.success('邀约状态已更新')
    loadInvitations()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '更新邀约状态失败')
  }
}

// ------------------------------------------------------------------
// 活动
// ------------------------------------------------------------------
const activityQuery = reactive({ keyword: '', status: '', page: 1, pageSize: 20 })
const activityRows = ref<ActivityView[]>([])
const activityTotal = ref(0)
const activityLoading = ref(false)

async function loadActivities() {
  activityLoading.value = true
  try {
    const result = await fetchActivities({
      keyword: activityQuery.keyword || undefined,
      status: activityQuery.status || undefined,
      page: activityQuery.page,
      pageSize: activityQuery.pageSize
    })
    activityRows.value = result.items
    activityTotal.value = result.total
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '加载活动失败')
  } finally {
    activityLoading.value = false
  }
}

function searchActivities() {
  activityQuery.page = 1
  loadActivities()
}

const activityDialogVisible = ref(false)
const activityForm = reactive({
  activityName: '',
  activityType: 'health_lecture',
  startAt: '',
  endAt: '',
  location: '',
  capacity: undefined as number | undefined,
  description: ''
})

function openActivityDialog() {
  Object.assign(activityForm, {
    activityName: '', activityType: 'health_lecture', startAt: '', endAt: '',
    location: '', capacity: undefined, description: ''
  })
  activityDialogVisible.value = true
}

async function submitActivity() {
  try {
    await createActivity({
      activityName: activityForm.activityName,
      activityType: activityForm.activityType,
      startAt: activityForm.startAt,
      endAt: activityForm.endAt,
      location: activityForm.location || undefined,
      capacity: activityForm.capacity,
      description: activityForm.description || undefined
    })
    ElMessage.success('活动已创建')
    activityDialogVisible.value = false
    searchActivities()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '创建活动失败')
  }
}

async function updateActivityStatus(row: ActivityView, status: string) {
  try {
    await changeActivityStatus(row.id, status)
    ElMessage.success('活动状态已更新')
    loadActivities()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '更新活动状态失败')
  }
}

const participantVisible = ref(false)
const participantActivity = ref<ActivityView>()
const participantRows = ref<ParticipantView[]>([])
const participantTotal = ref(0)
const participantLoading = ref(false)
const participantPage = ref(1)
const participantPageSize = 10
const registerForm = reactive({ participantName: '', phone: '', remark: '' })

async function openParticipants(row: ActivityView) {
  participantActivity.value = row
  participantPage.value = 1
  Object.assign(registerForm, { participantName: '', phone: '', remark: '' })
  participantVisible.value = true
  await loadParticipants()
}

async function loadParticipants() {
  if (!participantActivity.value) return
  participantLoading.value = true
  try {
    const result = await fetchParticipants(
      participantActivity.value.id, participantPage.value, participantPageSize
    )
    participantRows.value = result.items
    participantTotal.value = result.total
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '加载参与人失败')
  } finally {
    participantLoading.value = false
  }
}

async function submitParticipant() {
  if (!participantActivity.value) return
  try {
    await registerParticipant(participantActivity.value.id, {
      participantName: registerForm.participantName,
      phone: registerForm.phone || undefined,
      remark: registerForm.remark || undefined
    })
    ElMessage.success('报名成功')
    Object.assign(registerForm, { participantName: '', phone: '', remark: '' })
    await loadParticipants()
    await loadActivities()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '报名失败')
  }
}

async function updateParticipantStatus(row: ParticipantView, status: string) {
  if (!participantActivity.value) return
  try {
    await changeParticipantStatus(participantActivity.value.id, row.id, status)
    ElMessage.success('参与状态已更新')
    await loadParticipants()
    await loadActivities()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '更新参与状态失败')
  }
}

watch(activeTab, (tab) => {
  if (tab === 'invitations' && invitationRows.value.length === 0) {
    loadInvitations()
  }
  if (tab === 'activities' && activityRows.value.length === 0) {
    loadActivities()
  }
})

onMounted(async () => {
  try {
    channels.value = await fetchChannels()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '加载来源渠道失败')
  }
  await loadLeads()
})
</script>

<template>
  <section class="operation-page">
    <header class="page-head">
      <div>
        <h2>客户运营</h2>
        <p>线索获取、重复核验、邀约推进与活动参与；线索转化后保留完整追溯。</p>
      </div>
      <div class="head-metrics">
        <span class="metric"><strong>{{ leadTotal }}</strong> 线索</span>
        <span class="metric"><strong>{{ invitationTotal }}</strong> 邀约</span>
        <span class="metric"><strong>{{ activityTotal }}</strong> 活动</span>
      </div>
    </header>

    <el-tabs v-model="activeTab" class="operation-tabs">
      <el-tab-pane label="线索" name="leads">
        <div class="card tab-card">
          <div class="toolbar">
            <el-input
              v-model="leadQuery.keyword"
              placeholder="姓名 / 线索号 / 手机号"
              clearable
              class="toolbar-input"
              @keyup.enter="searchLeads"
            />
            <el-select v-model="leadQuery.status" placeholder="全部状态" clearable class="toolbar-select">
              <el-option v-for="(label, value) in leadStatusLabels" :key="value" :label="label" :value="value" />
            </el-select>
            <el-select v-model="leadQuery.channelCode" placeholder="全部渠道" clearable class="toolbar-select">
              <el-option v-for="channel in channels" :key="channel.channelCode"
                         :label="channel.channelName" :value="channel.channelCode" />
            </el-select>
            <el-button type="primary" plain @click="searchLeads">查询</el-button>
            <el-button @click="resetLeadFilters">重置</el-button>
            <div class="toolbar-spacer" />
            <el-button v-if="canWriteLead" type="primary" @click="openLeadDialog">新建线索</el-button>
          </div>

          <el-table v-loading="leadLoading" :data="leadRows" row-key="id" stripe>
            <el-table-column prop="leadNo" label="线索号" min-width="120">
              <template #default="{ row }"><span class="cell-mono">{{ row.leadNo }}</span></template>
            </el-table-column>
            <el-table-column prop="name" label="姓名" min-width="100" />
            <el-table-column prop="phoneMasked" label="手机号" min-width="120" />
            <el-table-column prop="sourceChannelName" label="来源渠道" min-width="110" />
            <el-table-column label="状态" min-width="110">
              <template #default="{ row }">
                <span class="badge" :class="statusBadge(row.status)">
                  {{ statusLabel(row.status, leadStatusLabels) }}
                </span>
              </template>
            </el-table-column>
            <el-table-column prop="ownerName" label="负责人" min-width="100" />
            <el-table-column label="创建时间" min-width="160">
              <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
            </el-table-column>
            <el-table-column label="操作" width="280" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" @click="openLeadDetail(row)">详情</el-button>
                <el-button v-if="canWriteLead && row.status !== 'MERGED'" link type="primary"
                           @click="openLeadDetail(row); openContactDialog()">联系</el-button>
                <el-button v-if="canConvertLead && row.status !== 'CONVERTED' && row.status !== 'MERGED'"
                           link type="primary" @click="openLeadDetail(row); openConvertDialog()">转客户</el-button>
                <el-button v-if="canWriteLead && row.status !== 'MERGED'" link type="primary"
                           @click="openLeadDetail(row); openMergeDialog()">合并</el-button>
                <el-button v-if="canAssignLead && row.status !== 'MERGED' && row.status !== 'INVALID'"
                           link type="primary" @click="openLeadDetail(row); openAssignDialog()">分派</el-button>
              </template>
            </el-table-column>
          </el-table>

          <el-pagination
            class="table-pagination"
            :current-page="leadQuery.page"
            :page-size="leadQuery.pageSize"
            :total="leadTotal"
            layout="total, prev, pager, next"
            @current-change="(page: number) => { leadQuery.page = page; loadLeads() }"
          />
        </div>
      </el-tab-pane>

      <el-tab-pane label="邀约" name="invitations">
        <div class="card tab-card">
          <div class="toolbar">
            <el-input v-model="invitationQuery.keyword" placeholder="邀约号 / 主题" clearable
                      class="toolbar-input" @keyup.enter="searchInvitations" />
            <el-select v-model="invitationQuery.status" placeholder="全部状态" clearable class="toolbar-select">
              <el-option v-for="(label, value) in invitationStatusLabels" :key="value"
                         :label="label" :value="value" />
            </el-select>
            <el-button type="primary" plain @click="searchInvitations">查询</el-button>
            <div class="toolbar-spacer" />
            <el-button v-if="canWriteInvitation" type="primary" @click="openInvitationDialog">新建邀约</el-button>
          </div>

          <el-table v-loading="invitationLoading" :data="invitationRows" row-key="id" stripe>
            <el-table-column prop="invitationNo" label="邀约号" min-width="120">
              <template #default="{ row }"><span class="cell-mono">{{ row.invitationNo }}</span></template>
            </el-table-column>
            <el-table-column prop="title" label="邀约主题" min-width="180" show-overflow-tooltip />
            <el-table-column label="邀约对象" min-width="170">
              <template #default="{ row }">
                {{ row.leadNo ? `线索 ${row.leadNo}` : `客户 ${row.customerNo || ''} ${row.customerName || ''}` }}
              </template>
            </el-table-column>
            <el-table-column prop="channelName" label="渠道" min-width="100" />
            <el-table-column label="计划时间" min-width="160">
              <template #default="{ row }">{{ formatDateTime(row.plannedAt) }}</template>
            </el-table-column>
            <el-table-column label="状态" min-width="100">
              <template #default="{ row }">
                <span class="badge" :class="statusBadge(row.status)">
                  {{ statusLabel(row.status, invitationStatusLabels) }}
                </span>
              </template>
            </el-table-column>
            <el-table-column prop="ownerName" label="负责人" min-width="100" />
            <el-table-column v-if="canWriteInvitation" label="操作" width="230" fixed="right">
              <template #default="{ row }">
                <el-button v-if="row.status === 'PENDING'" link type="primary"
                           @click="updateInvitationStatus(row, 'CONTACTED')">已联系</el-button>
                <el-button v-if="row.status === 'CONTACTED'" link type="success"
                           @click="updateInvitationStatus(row, 'ACCEPTED')">接受</el-button>
                <el-button v-if="row.status === 'CONTACTED'" link type="danger"
                           @click="updateInvitationStatus(row, 'DECLINED')">拒绝</el-button>
                <el-button v-if="['PENDING', 'CONTACTED'].includes(row.status)" link
                           @click="updateInvitationStatus(row, 'CANCELLED')">取消</el-button>
              </template>
            </el-table-column>
          </el-table>

          <el-pagination
            class="table-pagination"
            :current-page="invitationQuery.page"
            :page-size="invitationQuery.pageSize"
            :total="invitationTotal"
            layout="total, prev, pager, next"
            @current-change="(page: number) => { invitationQuery.page = page; loadInvitations() }"
          />
        </div>
      </el-tab-pane>

      <el-tab-pane label="活动" name="activities">
        <div class="card tab-card">
          <div class="toolbar">
            <el-input v-model="activityQuery.keyword" placeholder="活动号 / 活动名称" clearable
                      class="toolbar-input" @keyup.enter="searchActivities" />
            <el-select v-model="activityQuery.status" placeholder="全部状态" clearable class="toolbar-select">
              <el-option v-for="(label, value) in activityStatusLabels" :key="value"
                         :label="label" :value="value" />
            </el-select>
            <el-button type="primary" plain @click="searchActivities">查询</el-button>
            <div class="toolbar-spacer" />
            <el-button v-if="canWriteActivity" type="primary" @click="openActivityDialog">新建活动</el-button>
          </div>

          <el-table v-loading="activityLoading" :data="activityRows" row-key="id" stripe>
            <el-table-column prop="activityNo" label="活动号" min-width="120">
              <template #default="{ row }"><span class="cell-mono">{{ row.activityNo }}</span></template>
            </el-table-column>
            <el-table-column prop="activityName" label="活动名称" min-width="180" show-overflow-tooltip />
            <el-table-column label="类型" min-width="110">
              <template #default="{ row }">{{ activityTypeLabels[row.activityType] || row.activityType }}</template>
            </el-table-column>
            <el-table-column label="时间" min-width="210">
              <template #default="{ row }">
                {{ formatDateTime(row.startAt) }} ~ {{ formatDateTime(row.endAt) }}
              </template>
            </el-table-column>
            <el-table-column prop="location" label="地点" min-width="120" show-overflow-tooltip />
            <el-table-column label="参与人数" width="100">
              <template #default="{ row }">
                {{ row.participantCount }}<span v-if="row.capacity"> / {{ row.capacity }}</span>
              </template>
            </el-table-column>
            <el-table-column label="状态" min-width="100">
              <template #default="{ row }">
                <span class="badge" :class="statusBadge(row.status)">
                  {{ statusLabel(row.status, activityStatusLabels) }}
                </span>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="260" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" @click="openParticipants(row)">参与人</el-button>
                <el-button v-if="canWriteActivity && row.status === 'DRAFT'" link type="success"
                           @click="updateActivityStatus(row, 'PUBLISHED')">发布</el-button>
                <el-button v-if="canWriteActivity && row.status === 'PUBLISHED'" link type="warning"
                           @click="updateActivityStatus(row, 'CLOSED')">结束</el-button>
                <el-button v-if="canWriteActivity && ['DRAFT', 'PUBLISHED'].includes(row.status)" link
                           @click="updateActivityStatus(row, 'CANCELLED')">取消</el-button>
              </template>
            </el-table-column>
          </el-table>

          <el-pagination
            class="table-pagination"
            :current-page="activityQuery.page"
            :page-size="activityQuery.pageSize"
            :total="activityTotal"
            layout="total, prev, pager, next"
            @current-change="(page: number) => { activityQuery.page = page; loadActivities() }"
          />
        </div>
      </el-tab-pane>
    </el-tabs>

    <!-- 线索详情 -->
    <el-drawer v-model="detailVisible" title="线索详情" size="660px">
      <div v-loading="detailLoading" class="drawer-body">
        <template v-if="detail">
          <div class="detail-head">
            <div>
              <h3>{{ detail.summary.name }}</h3>
              <p class="cell-mono">{{ detail.summary.leadNo }}</p>
            </div>
            <span class="badge" :class="statusBadge(detail.summary.status)">
              {{ statusLabel(detail.summary.status, leadStatusLabels) }}
            </span>
          </div>

          <el-descriptions :column="2" border>
            <el-descriptions-item label="性别">
              {{ genderLabels[detail.summary.gender] || detail.summary.gender }}
            </el-descriptions-item>
            <el-descriptions-item label="出生日期">{{ formatDate(detail.summary.birthDate) }}</el-descriptions-item>
            <el-descriptions-item label="手机号">{{ detail.summary.phoneMasked || '—' }}</el-descriptions-item>
            <el-descriptions-item label="证件号">{{ detail.summary.idNoMasked || '—' }}</el-descriptions-item>
            <el-descriptions-item label="来源渠道">{{ detail.summary.sourceChannelName }}</el-descriptions-item>
            <el-descriptions-item label="负责人">{{ detail.summary.ownerName || '—' }}</el-descriptions-item>
            <el-descriptions-item label="创建时间">{{ formatDateTime(detail.summary.createdAt) }}</el-descriptions-item>
            <el-descriptions-item label="更新时间">{{ formatDateTime(detail.summary.updatedAt) }}</el-descriptions-item>
            <el-descriptions-item label="备注" :span="2">{{ detail.remark || '—' }}</el-descriptions-item>
          </el-descriptions>

          <div class="detail-actions">
            <el-button v-for="action in statusActions" :key="action.status" size="small"
                       :type="action.status === 'INVALID' ? 'danger' : 'primary'" plain
                       @click="changeDetailStatus(action.status)">
              {{ action.label }}
            </el-button>
            <el-button v-if="canWriteLead && detail.summary.status !== 'MERGED'" size="small" @click="openContactDialog">
              新增联系记录
            </el-button>
            <el-button v-if="canConvertLead && !['CONVERTED', 'MERGED', 'INVALID'].includes(detail.summary.status)"
                       size="small" type="success" plain @click="openConvertDialog">
              转客户
            </el-button>
            <el-button v-if="canWriteLead && detail.summary.status !== 'MERGED'" size="small" @click="openMergeDialog">
              合并
            </el-button>
            <el-button v-if="canAssignLead && !['MERGED', 'INVALID'].includes(detail.summary.status)"
                       size="small" @click="openAssignDialog">
              分派
            </el-button>
          </div>

          <el-alert v-if="detail.conversion" class="detail-alert" type="success" :closable="false" show-icon
                    :title="`已于 ${formatDateTime(detail.conversion.convertedAt)} 转化为客户 ${detail.conversion.customerNo} ${detail.conversion.customerName}`" />

          <h4 class="sub-title">联系记录</h4>
          <el-table :data="detail.recentContacts" row-key="id" size="small" stripe>
            <el-table-column label="方式" width="80">
              <template #default="{ row }">{{ contactTypeLabels[row.contactType] || row.contactType }}</template>
            </el-table-column>
            <el-table-column label="时间" min-width="150">
              <template #default="{ row }">{{ formatDateTime(row.contactedAt) }}</template>
            </el-table-column>
            <el-table-column label="结果" width="90">
              <template #default="{ row }">{{ contactResultLabels[row.resultCode] || row.resultCode }}</template>
            </el-table-column>
            <el-table-column prop="content" label="内容" min-width="180" show-overflow-tooltip />
            <el-table-column prop="contactedByName" label="记录人" width="90" />
          </el-table>

          <h4 class="sub-title">分派记录</h4>
          <el-table :data="detail.assignments" row-key="id" size="small" stripe>
            <el-table-column prop="fromUserName" label="原负责人" width="100" />
            <el-table-column prop="toUserName" label="新负责人" width="100" />
            <el-table-column prop="reason" label="原因" min-width="160" show-overflow-tooltip />
            <el-table-column label="分派时间" min-width="160">
              <template #default="{ row }">{{ formatDateTime(row.assignedAt) }}</template>
            </el-table-column>
          </el-table>
        </template>
      </div>
    </el-drawer>

    <!-- 新建线索 -->
    <el-dialog v-model="leadDialogVisible" title="新建线索" width="540px">
      <el-form label-width="92px">
        <el-form-item label="姓名" required>
          <el-input v-model="leadForm.name" maxlength="100" />
        </el-form-item>
        <el-form-item label="性别">
          <el-select v-model="leadForm.gender" class="full-width">
            <el-option v-for="(label, value) in genderLabels" :key="value" :label="label" :value="value" />
          </el-select>
        </el-form-item>
        <el-form-item label="出生日期">
          <el-date-picker v-model="leadForm.birthDate" type="date" value-format="YYYY-MM-DD"
                          placeholder="选择日期" class="full-width" />
        </el-form-item>
        <el-form-item label="手机号">
          <el-input v-model="leadForm.phone" maxlength="32" placeholder="11 位手机号" />
        </el-form-item>
        <el-form-item label="证件号">
          <el-input v-model="leadForm.idNo" maxlength="32" placeholder="15/18 位身份证号" />
        </el-form-item>
        <el-form-item label="来源渠道" required>
          <el-select v-model="leadForm.sourceChannelCode" class="full-width" placeholder="选择渠道">
            <el-option v-for="channel in channels" :key="channel.channelCode"
                       :label="channel.channelName" :value="channel.channelCode" />
          </el-select>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="leadForm.remark" type="textarea" :rows="3" maxlength="1000" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="leadDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitLead(false)">保存</el-button>
      </template>
    </el-dialog>

    <!-- 联系记录 -->
    <el-dialog v-model="contactDialogVisible" title="新增联系记录" width="520px">
      <el-form label-width="92px">
        <el-form-item label="联系类型">
          <el-select v-model="contactForm.contactType" class="full-width">
            <el-option v-for="(label, value) in contactTypeLabels" :key="value" :label="label" :value="value" />
          </el-select>
        </el-form-item>
        <el-form-item label="联系时间">
          <el-date-picker v-model="contactForm.contactedAt" type="datetime"
                          value-format="YYYY-MM-DDTHH:mm:ssZ" class="full-width" placeholder="默认当前时间" />
        </el-form-item>
        <el-form-item label="联系结果">
          <el-select v-model="contactForm.resultCode" class="full-width">
            <el-option v-for="(label, value) in contactResultLabels" :key="value" :label="label" :value="value" />
          </el-select>
        </el-form-item>
        <el-form-item label="联系内容" required>
          <el-input v-model="contactForm.content" type="textarea" :rows="4" maxlength="2000" />
        </el-form-item>
        <el-form-item label="下次跟进">
          <el-date-picker v-model="contactForm.nextFollowUpAt" type="datetime"
                          value-format="YYYY-MM-DDTHH:mm:ssZ" class="full-width" placeholder="可选" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="contactDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitContact">保存</el-button>
      </template>
    </el-dialog>

    <!-- 转客户 -->
    <el-dialog v-model="convertDialogVisible" title="线索转客户" width="560px">
      <el-alert class="dialog-alert" type="info" :closable="false" show-icon
                title="转化后将写入线索转化记录，并保留线索号与客户号的关联，便于追溯。" />
      <el-form label-width="100px">
        <el-form-item label="转化方式">
          <el-radio-group v-model="convertMode">
            <el-radio label="NEW">创建新客户</el-radio>
            <el-radio label="EXISTING">关联既有客户</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="convertMode === 'EXISTING'" label="选择客户" required>
          <el-select v-model="convertCustomerId" filterable remote class="full-width"
                     :remote-method="searchConvertCustomers" placeholder="输入姓名/客户号/手机号搜索">
            <el-option v-for="customer in customerOptions" :key="customer.id"
                       :label="`${customer.customerNo} ${customer.name} ${customer.phoneMasked || ''}`"
                       :value="customer.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="转化备注">
          <el-input v-model="convertRemark" type="textarea" :rows="3" maxlength="500" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="convertDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitConvert">确认转化</el-button>
      </template>
    </el-dialog>

    <!-- 合并 -->
    <el-dialog v-model="mergeDialogVisible" title="合并线索" width="560px">
      <el-alert class="dialog-alert" type="warning" :closable="false" show-icon
                title="合并后当前线索标记为已合并，并指向目标线索；请确认二者为同一人。" />
      <el-form label-width="92px">
        <el-form-item label="目标线索" required>
          <el-select v-model="mergeTargetId" filterable remote class="full-width"
                     :remote-method="searchMergeTargets" placeholder="输入线索号/姓名搜索">
            <el-option v-for="lead in mergeOptions" :key="lead.id"
                       :label="`${lead.leadNo} ${lead.name}（${statusLabel(lead.status, leadStatusLabels)}）`"
                       :value="lead.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="合并说明">
          <el-input v-model="mergeReason" type="textarea" :rows="3" maxlength="500" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="mergeDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitMerge">确认合并</el-button>
      </template>
    </el-dialog>

    <!-- 分派 -->
    <el-dialog v-model="assignDialogVisible" title="线索分派" width="520px">
      <el-form label-width="92px">
        <el-form-item label="目标员工" required>
          <el-select v-model="assignToUserId" filterable remote class="full-width"
                     :remote-method="searchAssignees" placeholder="输入姓名/账号搜索">
            <el-option v-for="user in assigneeOptions" :key="user.id"
                       :label="`${user.displayName}（${user.username}）`" :value="user.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="分派原因">
          <el-input v-model="assignReason" type="textarea" :rows="3" maxlength="500" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="assignDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitAssign">确认分派</el-button>
      </template>
    </el-dialog>

    <!-- 新建邀约 -->
    <el-dialog v-model="invitationDialogVisible" title="新建邀约" width="560px">
      <el-form label-width="96px">
        <el-form-item label="对象类型">
          <el-radio-group v-model="invitationForm.targetType" @change="onInvitationTargetTypeChange">
            <el-radio label="LEAD">线索</el-radio>
            <el-radio label="CUSTOMER">客户</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="邀约对象" required>
          <el-select v-model="invitationForm.targetId" filterable remote class="full-width"
                     :remote-method="searchInvitationTargets" placeholder="输入关键词搜索">
            <el-option v-for="target in targetOptions" :key="target.id"
                       :label="target.label" :value="target.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="邀约渠道">
          <el-select v-model="invitationForm.channelCode" class="full-width">
            <el-option v-for="channel in channels" :key="channel.channelCode"
                       :label="channel.channelName" :value="channel.channelCode" />
          </el-select>
        </el-form-item>
        <el-form-item label="邀约主题" required>
          <el-input v-model="invitationForm.title" maxlength="200" />
        </el-form-item>
        <el-form-item label="计划时间" required>
          <el-date-picker v-model="invitationForm.plannedAt" type="datetime"
                          value-format="YYYY-MM-DDTHH:mm:ssZ" class="full-width" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="invitationForm.remark" type="textarea" :rows="3" maxlength="1000" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="invitationDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitInvitation">创建邀约</el-button>
      </template>
    </el-dialog>

    <!-- 新建活动 -->
    <el-dialog v-model="activityDialogVisible" title="新建活动" width="560px">
      <el-form label-width="96px">
        <el-form-item label="活动名称" required>
          <el-input v-model="activityForm.activityName" maxlength="200" />
        </el-form-item>
        <el-form-item label="活动类型">
          <el-select v-model="activityForm.activityType" class="full-width">
            <el-option v-for="(label, value) in activityTypeLabels" :key="value" :label="label" :value="value" />
          </el-select>
        </el-form-item>
        <el-form-item label="开始时间" required>
          <el-date-picker v-model="activityForm.startAt" type="datetime"
                          value-format="YYYY-MM-DDTHH:mm:ssZ" class="full-width" />
        </el-form-item>
        <el-form-item label="结束时间" required>
          <el-date-picker v-model="activityForm.endAt" type="datetime"
                          value-format="YYYY-MM-DDTHH:mm:ssZ" class="full-width" />
        </el-form-item>
        <el-form-item label="活动地点">
          <el-input v-model="activityForm.location" maxlength="200" />
        </el-form-item>
        <el-form-item label="名额">
          <el-input-number v-model="activityForm.capacity" :min="0" :max="100000" />
        </el-form-item>
        <el-form-item label="活动说明">
          <el-input v-model="activityForm.description" type="textarea" :rows="3" maxlength="2000" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="activityDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitActivity">创建活动</el-button>
      </template>
    </el-dialog>

    <!-- 活动参与人 -->
    <el-drawer v-model="participantVisible" :title="`参与人 · ${participantActivity?.activityName || ''}`" size="620px">
      <div class="drawer-body">
        <div v-if="canWriteActivity" class="register-box">
          <el-input v-model="registerForm.participantName" placeholder="参与人姓名" class="register-name" />
          <el-input v-model="registerForm.phone" placeholder="手机号（可选）" class="register-phone" />
          <el-button type="primary" @click="submitParticipant">报名</el-button>
        </div>
        <el-table v-loading="participantLoading" :data="participantRows" row-key="id" size="small" stripe>
          <el-table-column prop="participantName" label="姓名" min-width="100" />
          <el-table-column prop="phoneMasked" label="手机号" min-width="120" />
          <el-table-column label="状态" min-width="90">
            <template #default="{ row }">
              <span class="badge" :class="statusBadge(row.status)">
                {{ statusLabel(row.status, participantStatusLabels) }}
              </span>
            </template>
          </el-table-column>
          <el-table-column label="报名时间" min-width="160">
            <template #default="{ row }">{{ formatDateTime(row.registeredAt) }}</template>
          </el-table-column>
          <el-table-column label="签到时间" min-width="160">
            <template #default="{ row }">{{ formatDateTime(row.checkedInAt) }}</template>
          </el-table-column>
          <el-table-column v-if="canWriteActivity" label="操作" width="160" fixed="right">
            <template #default="{ row }">
              <el-button v-if="['REGISTERED', 'NO_SHOW'].includes(row.status)" link type="success"
                         @click="updateParticipantStatus(row, 'ATTENDED')">签到</el-button>
              <el-button v-if="row.status === 'REGISTERED'" link type="warning"
                         @click="updateParticipantStatus(row, 'NO_SHOW')">爽约</el-button>
              <el-button v-if="['REGISTERED', 'NO_SHOW'].includes(row.status)" link
                         @click="updateParticipantStatus(row, 'CANCELLED')">取消</el-button>
            </template>
          </el-table-column>
        </el-table>
        <el-pagination
          class="table-pagination"
          :current-page="participantPage"
          :page-size="participantPageSize"
          :total="participantTotal"
          layout="total, prev, pager, next"
          @current-change="(page: number) => { participantPage = page; loadParticipants() }"
        />
      </div>
    </el-drawer>
  </section>
</template>

<style scoped lang="scss">
.operation-page {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.page-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 20px;
  padding: 24px 26px;
  border-radius: 18px;
  background: linear-gradient(120deg, #1f5f8b, #2d6dad 58%, #4b93c6);
  color: #f6fbff;
  box-shadow: 0 18px 36px rgba(24, 78, 120, 0.18);
}

.page-head h2 {
  margin: 0 0 6px;
  font-size: 24px;
}

.page-head p {
  margin: 0;
  color: rgba(244, 250, 255, 0.78);
  font-size: 13px;
}

.head-metrics {
  display: flex;
  gap: 10px;
}

.metric {
  padding: 10px 14px;
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.12);
  font-size: 12px;
  white-space: nowrap;
}

.metric strong {
  font-size: 18px;
  margin-right: 4px;
}

.operation-tabs :deep(.el-tabs__header) {
  margin-bottom: 14px;
}

.tab-card {
  padding: 20px 22px;
}

.toolbar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px;
  margin-bottom: 16px;
}

.toolbar-input {
  width: 240px;
}

.toolbar-select {
  width: 150px;
}

.toolbar-spacer {
  flex: 1;
}

.full-width {
  width: 100%;
}

.detail-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 16px;
}

.detail-head h3 {
  margin: 0 0 4px;
  font-size: 20px;
}

.detail-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin: 16px 0;
}

.detail-alert,
.dialog-alert {
  margin: 12px 0;
}

.sub-title {
  margin: 22px 0 10px;
  font-size: 14px;
}

.drawer-body {
  padding: 0 4px 12px;
}

.register-box {
  display: flex;
  gap: 10px;
  margin-bottom: 14px;
}

.register-name {
  width: 160px;
}

.register-phone {
  width: 180px;
}

@media (max-width: 900px) {
  .toolbar-input,
  .toolbar-select {
    width: 100%;
  }

  .page-head {
    flex-direction: column;
  }
}
</style>
