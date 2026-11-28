<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import {
  addFamilyMember,
  checkDelegation,
  createAuthorization,
  createFamily,
  endFamilyMembership,
  fetchAuthorizations,
  fetchFamilies,
  fetchFamily,
  fetchFamilyGraph,
  fetchFamilyMembers,
  fetchMemberRelations,
  revokeAuthorization,
  updateFamilyMember
} from '@/api/families'
import { fetchCustomers } from '@/api/customers'
import type { CustomerSummary } from '@/types/customer'
import type {
  FamilyAuthorizationItem,
  FamilyDetail,
  FamilyGraph,
  FamilyGraphNode,
  FamilyMemberItem,
  FamilyMemberRelations,
  FamilySummary
} from '@/types/family'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
const canWrite = computed(() => auth.has('crm:family:write'))
const canAuthorize = computed(() => auth.has('crm:family:authorize'))

const relationshipOptions = [
  { value: 'spouse', label: '配偶' },
  { value: 'parent', label: '父母' },
  { value: 'child', label: '子女' },
  { value: 'sibling', label: '兄弟姐妹' },
  { value: 'grandparent', label: '祖父母/外祖父母' },
  { value: 'grandchild', label: '孙子女/外孙子女' },
  { value: 'other', label: '其他亲属' }
]
const scopeOptions = [
  { value: 'appointment', label: '代预约' },
  { value: 'payment', label: '代支付' },
  { value: 'package_share', label: '共享套餐' },
  { value: 'profile_update', label: '资料维护' }
]

const loading = ref(false)
const rows = ref<FamilySummary[]>([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(20)
const keyword = ref('')
const statusFilter = ref<string>()

const createVisible = ref(false)
const createSubmitting = ref(false)
const createFormRef = ref<FormInstance>()
const createForm = reactive({ familyName: '', ownerCustomerId: '' })
const ownerOptions = ref<CustomerSummary[]>([])
const ownerLoading = ref(false)

const createRules: FormRules = {
  familyName: [
    { required: true, message: '请输入家庭名称', trigger: 'blur' },
    { max: 100, message: '家庭名称长度不能超过 100', trigger: 'blur' }
  ],
  ownerCustomerId: [{ required: true, message: '请选择家庭管理员客户', trigger: 'change' }]
}

const detailVisible = ref(false)
const activeTab = ref<'members' | 'authorizations'>('members')
const detailLoading = ref(false)
const detail = ref<FamilyDetail>()
const members = ref<FamilyMemberItem[]>([])
const includeEnded = ref(false)
const activeMembers = computed(() => members.value.filter((member) => member.status === 'active'))

const memberVisible = ref(false)
const memberSubmitting = ref(false)
const memberFormRef = ref<FormInstance>()
const memberForm = reactive({ customerId: '', relationshipCode: 'spouse' })
const memberOptions = ref<CustomerSummary[]>([])
const memberLoading = ref(false)
const memberRules: FormRules = {
  customerId: [{ required: true, message: '请选择客户', trigger: 'change' }],
  relationshipCode: [{ required: true, message: '请选择家庭关系', trigger: 'change' }]
}

const editVisible = ref(false)
const editSubmitting = ref(false)
const editForm = reactive({
  memberId: '',
  customerName: '',
  relationshipCode: 'spouse',
  admin: false
})

// 家庭关系图谱（演示级称谓推导，数据来自 /families/{id}/graph）
const graphVisible = ref(false)
const graphLoading = ref(false)
const graph = ref<FamilyGraph>()
const graphSelected = ref<FamilyGraphNode>()

const graphRows = computed(() => {
  const selected = graphSelected.value
  const data = graph.value
  if (!selected || !data) return []
  return data.nodes
    .filter((node) => node.customerId !== selected.customerId)
    .map((node) => ({
      ...node,
      relationToSelected:
        data.edges.find(
          (edge) => edge.fromCustomerId === node.customerId && edge.toCustomerId === selected.customerId
        )?.label ?? '亲属'
    }))
})

async function openGraph(member: FamilyMemberItem) {
  const family = detail.value
  if (!family || !member.customer) return
  graphVisible.value = true
  graphLoading.value = true
  graph.value = undefined
  graphSelected.value = undefined
  try {
    const data = await fetchFamilyGraph(family.id)
    const selected = data.nodes.find((node) => node.customerId === member.customer?.id)
    if (!selected) {
      ElMessage.warning('该成员不在当前有效家庭成员中，暂无关系图谱')
      graphVisible.value = false
      return
    }
    graph.value = data
    graphSelected.value = selected
  } catch (error) {
    graphVisible.value = false
    ElMessage.error(error instanceof Error ? error.message : '加载关系图谱失败')
  } finally {
    graphLoading.value = false
  }
}

// 成员关联视图（数据来自 /families/{id}/members/{id}/relations，锚点为所选成员）
const relationsVisible = ref(false)
const relationsLoading = ref(false)
const relations = ref<FamilyMemberRelations>()

async function openRelations(member: FamilyMemberItem) {
  const family = detail.value
  if (!family) return
  relationsVisible.value = true
  relationsLoading.value = true
  relations.value = undefined
  try {
    relations.value = await fetchMemberRelations(family.id, member.id)
  } catch (error) {
    relationsVisible.value = false
    ElMessage.error(error instanceof Error ? error.message : '加载成员关联失败')
  } finally {
    relationsLoading.value = false
  }
}

// peerRelations 每对成员双向两条，合并成一条“互称”摘要展示
const peerPairs = computed(() => {
  const data = relations.value
  if (!data) return []
  const map = new Map<string, { key: string; lines: string[] }>()
  for (const item of data.peerRelations) {
    const key = item.fromMemberId < item.toMemberId
      ? `${item.fromMemberId}|${item.toMemberId}`
      : `${item.toMemberId}|${item.fromMemberId}`
    const line = `${item.fromName} 是 ${item.toName} 的${item.label}`
    const pair = map.get(key)
    if (pair) {
      pair.lines.push(line)
    } else {
      map.set(key, { key, lines: [line] })
    }
  }
  return Array.from(map.values())
})

const authMemberId = ref('')
const authorizations = ref<FamilyAuthorizationItem[]>([])
const authLoading = ref(false)
const authVisible = ref(false)
const authSubmitting = ref(false)
const authForm = reactive({ granteeCustomerId: '', scopeCode: 'appointment' })

const checkForm = reactive({ memberId: '', granteeCustomerId: '', scopeCode: 'appointment' })
const checkResult = ref<{ allowed: boolean; message: string }>()
const checking = ref(false)

const granteeOptions = computed(() =>
  activeMembers.value.filter((member) => member.customer && member.id !== authMemberId.value)
)
const checkGranteeOptions = computed(() =>
  activeMembers.value.filter((member) => member.customer && member.id !== checkForm.memberId)
)

async function load() {
  loading.value = true
  try {
    const result = await fetchFamilies({
      keyword: keyword.value || undefined,
      status: statusFilter.value || undefined,
      page: page.value,
      pageSize: pageSize.value
    })
    rows.value = result.items
    total.value = result.total
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '加载家庭账户失败')
  } finally {
    loading.value = false
  }
}

function search() {
  page.value = 1
  load()
}

function resetFilters() {
  keyword.value = ''
  statusFilter.value = undefined
  search()
}

async function searchCustomers(keywordText: string, target: 'owner' | 'member') {
  if (target === 'owner') {
    ownerLoading.value = true
  } else {
    memberLoading.value = true
  }
  try {
    const result = await fetchCustomers({ keyword: keywordText || undefined, page: 1, pageSize: 20 })
    if (target === 'owner') {
      ownerOptions.value = result.items
    } else {
      memberOptions.value = result.items
    }
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '加载客户列表失败')
  } finally {
    ownerLoading.value = false
    memberLoading.value = false
  }
}

function searchOwners(value: string) {
  return searchCustomers(value, 'owner')
}

function searchMembers(value: string) {
  return searchCustomers(value, 'member')
}

function openCreate() {
  Object.assign(createForm, { familyName: '', ownerCustomerId: '' })
  ownerOptions.value = []
  createVisible.value = true
  createFormRef.value?.clearValidate()
  searchCustomers('', 'owner')
}

async function submitCreate() {
  if (!createFormRef.value) return
  const valid = await createFormRef.value.validate().catch(() => false)
  if (!valid) return
  createSubmitting.value = true
  try {
    await createFamily({
      familyName: createForm.familyName.trim(),
      ownerCustomerId: createForm.ownerCustomerId
    })
    ElMessage.success('家庭账户已创建')
    createVisible.value = false
    await load()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '创建家庭失败')
  } finally {
    createSubmitting.value = false
  }
}

async function openDetail(row: FamilySummary) {
  detailVisible.value = true
  detailLoading.value = true
  detail.value = undefined
  members.value = []
  includeEnded.value = false
  authMemberId.value = ''
  authorizations.value = []
  checkResult.value = undefined
  try {
    detail.value = await fetchFamily(row.id)
    await loadMembers()
  } catch (error) {
    detailVisible.value = false
    ElMessage.error(error instanceof Error ? error.message : '加载家庭详情失败')
  } finally {
    detailLoading.value = false
  }
}

async function refreshDetail() {
  const family = detail.value
  if (!family) return
  detail.value = await fetchFamily(family.id)
  await loadMembers()
}

async function loadMembers() {
  const family = detail.value
  if (!family) return
  members.value = await fetchFamilyMembers(family.id, includeEnded.value)
  if (!authMemberId.value || !members.value.some((member) => member.id === authMemberId.value)) {
    authMemberId.value = ''
    authorizations.value = []
  }
  if (!checkForm.memberId || !members.value.some((member) => member.id === checkForm.memberId)) {
    checkForm.memberId = ''
    checkForm.granteeCustomerId = ''
  }
}

function openAddMember() {
  Object.assign(memberForm, { customerId: '', relationshipCode: 'spouse' })
  memberOptions.value = []
  memberVisible.value = true
  memberFormRef.value?.clearValidate()
  searchCustomers('', 'member')
}

async function submitAddMember() {
  const family = detail.value
  if (!memberFormRef.value || !family) return
  const valid = await memberFormRef.value.validate().catch(() => false)
  if (!valid) return
  memberSubmitting.value = true
  try {
    await addFamilyMember(family.id, {
      customerId: memberForm.customerId,
      relationshipCode: memberForm.relationshipCode
    })
    ElMessage.success('家庭成员已添加')
    memberVisible.value = false
    await refreshDetail()
    await load()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '添加成员失败')
  } finally {
    memberSubmitting.value = false
  }
}

function openEditMember(member: FamilyMemberItem) {
  Object.assign(editForm, {
    memberId: member.id,
    customerName: member.customer?.name ?? '—',
    relationshipCode: member.relationshipCode,
    admin: member.admin
  })
  editVisible.value = true
}

async function submitEditMember() {
  const family = detail.value
  if (!family) return
  editSubmitting.value = true
  try {
    await updateFamilyMember(family.id, editForm.memberId, {
      relationshipCode: editForm.relationshipCode,
      admin: editForm.admin
    })
    ElMessage.success('家庭成员已更新')
    editVisible.value = false
    await refreshDetail()
    await load()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '更新成员失败')
  } finally {
    editSubmitting.value = false
  }
}

async function setAdmin(member: FamilyMemberItem) {
  const family = detail.value
  if (!family) return
  try {
    await ElMessageBox.confirm(`确认将 ${member.customer?.name ?? '该成员'} 设为家庭管理员？原管理员将自动取消。`, '设置管理员', {
      type: 'warning',
      confirmButtonText: '确认设置',
      cancelButtonText: '取消'
    })
  } catch {
    return
  }
  try {
    await updateFamilyMember(family.id, member.id, {
      relationshipCode: member.relationshipCode,
      admin: true
    })
    ElMessage.success('家庭管理员已更新')
    await refreshDetail()
    await load()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '设置管理员失败')
  }
}

async function endMembership(member: FamilyMemberItem) {
  const family = detail.value
  if (!family) return
  try {
    await ElMessageBox.confirm(
      `确认解除 ${member.customer?.name ?? '该成员'} 的家庭成员关系？解除后该成员全部代办授权将同时撤销。`,
      '解除关系',
      { type: 'warning', confirmButtonText: '确认解除', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  try {
    await endFamilyMembership(family.id, member.id)
    ElMessage.success('成员关系已解除')
    await refreshDetail()
    await load()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '解除关系失败')
  }
}

async function loadAuthorizations() {
  const family = detail.value
  if (!family || !authMemberId.value) {
    authorizations.value = []
    return
  }
  authLoading.value = true
  try {
    authorizations.value = await fetchAuthorizations(family.id, authMemberId.value)
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '加载代办授权失败')
  } finally {
    authLoading.value = false
  }
}

function openCreateAuthorization() {
  if (!authMemberId.value) {
    ElMessage.warning('请先选择家庭成员')
    return
  }
  Object.assign(authForm, { granteeCustomerId: '', scopeCode: 'appointment' })
  authVisible.value = true
}

async function submitAuthorization() {
  const family = detail.value
  if (!family || !authMemberId.value) return
  if (!authForm.granteeCustomerId) {
    ElMessage.warning('请选择被授权成员')
    return
  }
  authSubmitting.value = true
  try {
    await createAuthorization(family.id, authMemberId.value, {
      granteeCustomerId: authForm.granteeCustomerId,
      scopeCode: authForm.scopeCode
    })
    ElMessage.success('代办授权已创建')
    authVisible.value = false
    await loadAuthorizations()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '创建授权失败')
  } finally {
    authSubmitting.value = false
  }
}

async function revoke(item: FamilyAuthorizationItem) {
  const family = detail.value
  if (!family) return
  try {
    await ElMessageBox.confirm(
      `确认撤销 ${item.grantee?.name ?? '该成员'} 的「${item.scopeLabel}」代办授权？撤销后代办接口将立即拒绝访问。`,
      '撤销授权',
      { type: 'warning', confirmButtonText: '确认撤销', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  try {
    const memberId = item.memberId
    await revokeAuthorization(family.id, memberId, item.id)
    ElMessage.success('代办授权已撤销')
    authMemberId.value = memberId
    await loadAuthorizations()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '撤销授权失败')
  }
}

async function runCheck() {
  const family = detail.value
  if (!family) return
  if (!checkForm.memberId || !checkForm.granteeCustomerId || !checkForm.scopeCode) {
    ElMessage.warning('请选择家庭成员、被授权人和代办范围')
    return
  }
  checking.value = true
  checkResult.value = undefined
  try {
    const result = await checkDelegation(family.id, { ...checkForm })
    checkResult.value = {
      allowed: result.allowed,
      message: result.validTo
        ? `授权有效，有效期至 ${formatTime(result.validTo)}`
        : '授权有效，长期有效'
    }
  } catch (error) {
    checkResult.value = {
      allowed: false,
      message: error instanceof Error ? error.message : '代办权限校验未通过'
    }
  } finally {
    checking.value = false
  }
}

function formatDate(value?: string) {
  return value ? value.slice(0, 10) : '—'
}

function formatTime(value?: string) {
  return value ? value.slice(0, 16).replace('T', ' ') : '—'
}

onMounted(load)
</script>

<template>
  <section class="card table-card">
    <div class="section-head">
      <div>
        <h3 class="section-title">家庭账户</h3>
        <p class="section-subtitle">
          家庭账户与个人客户分离管理；家庭关系不自动授予病历访问权限，代办授权可单独撤销。
        </p>
      </div>
      <el-button v-if="canWrite" type="primary" @click="openCreate">新建家庭</el-button>
    </div>

    <div class="toolbar">
      <el-input v-model="keyword" placeholder="家庭号 / 家庭名称" clearable @keyup.enter="search" />
      <el-select v-model="statusFilter" placeholder="全部状态" clearable @change="search">
        <el-option label="启用" value="active" />
        <el-option label="停用" value="disabled" />
      </el-select>
      <el-button type="primary" plain @click="search">查询</el-button>
      <el-button @click="resetFilters">重置</el-button>
    </div>

    <el-table v-loading="loading" :data="rows" row-key="id" stripe>
      <el-table-column prop="familyNo" label="家庭号" width="140">
        <template #default="{ row }">
          <span class="cell-mono">{{ row.familyNo }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="familyName" label="家庭名称" min-width="160" />
      <el-table-column prop="memberCount" label="成员数" width="90" />
      <el-table-column label="家庭管理员" width="120">
        <template #default="{ row }">{{ row.adminName || '—' }}</template>
      </el-table-column>
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <span class="badge" :class="row.status === 'active' ? 'badge-success' : 'badge-danger'">
            {{ row.status === 'active' ? '启用' : '停用' }}
          </span>
        </template>
      </el-table-column>
      <el-table-column label="创建日期" width="120">
        <template #default="{ row }">
          <span class="cell-muted">{{ formatDate(row.createdAt) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="120" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openDetail(row)">详情</el-button>
        </template>
      </el-table-column>
    </el-table>

    <div class="table-pagination">
      <el-pagination
        v-model:current-page="page"
        v-model:page-size="pageSize"
        :total="total"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next"
        @current-change="load"
        @size-change="search"
      />
    </div>

    <el-dialog v-model="createVisible" title="新建家庭账户" width="560px">
      <el-form ref="createFormRef" :model="createForm" :rules="createRules" label-width="110px">
        <el-form-item label="家庭名称" prop="familyName">
          <el-input v-model="createForm.familyName" maxlength="100" placeholder="如：张三家庭" />
        </el-form-item>
        <el-form-item label="家庭管理员" prop="ownerCustomerId">
          <el-select
            v-model="createForm.ownerCustomerId"
            filterable
            remote
            :remote-method="searchOwners"
            :loading="ownerLoading"
            placeholder="按姓名 / 客户号 / 手机号搜索客户"
            style="width: 100%"
          >
            <el-option
              v-for="customer in ownerOptions"
              :key="customer.id"
              :label="`${customer.name}（${customer.customerNo}）`"
              :value="customer.id"
            />
          </el-select>
        </el-form-item>
      </el-form>
      <p class="form-tip">家庭管理员仅管理家庭与代办授权，不会自动获得成员病历访问权限。</p>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" :loading="createSubmitting" @click="submitCreate">创建</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="memberVisible" title="添加家庭成员" width="520px">
      <el-form ref="memberFormRef" :model="memberForm" :rules="memberRules" label-width="96px">
        <el-form-item label="客户" prop="customerId">
          <el-select
            v-model="memberForm.customerId"
            filterable
            remote
            :remote-method="searchMembers"
            :loading="memberLoading"
            placeholder="按姓名 / 客户号 / 手机号搜索客户"
            style="width: 100%"
          >
            <el-option
              v-for="customer in memberOptions"
              :key="customer.id"
              :label="`${customer.name}（${customer.customerNo}）`"
              :value="customer.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="家庭关系" prop="relationshipCode">
          <el-select v-model="memberForm.relationshipCode" style="width: 100%">
            <el-option
              v-for="item in relationshipOptions"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="memberVisible = false">取消</el-button>
        <el-button type="primary" :loading="memberSubmitting" @click="submitAddMember">添加</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="editVisible" title="编辑家庭成员" width="480px">
      <el-form :model="editForm" label-width="96px">
        <el-form-item label="成员">{{ editForm.customerName }}</el-form-item>
        <el-form-item label="家庭关系">
          <el-select v-model="editForm.relationshipCode" style="width: 100%">
            <el-option
              v-for="item in relationshipOptions"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="家庭管理员">
          <el-switch v-model="editForm.admin" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editVisible = false">取消</el-button>
        <el-button type="primary" :loading="editSubmitting" @click="submitEditMember">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="authVisible" title="创建代办授权" width="520px">
      <el-form :model="authForm" label-width="96px">
        <el-form-item label="被授权人">
          <el-select v-model="authForm.granteeCustomerId" placeholder="选择同家庭其他成员" style="width: 100%">
            <el-option
              v-for="member in granteeOptions"
              :key="member.id"
              :label="`${member.customer?.name ?? '—'}（${member.customer?.customerNo ?? ''}）`"
              :value="member.customer?.id ?? ''"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="代办范围">
          <el-select v-model="authForm.scopeCode" style="width: 100%">
            <el-option
              v-for="item in scopeOptions"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
        </el-form-item>
      </el-form>
      <p class="form-tip">代办范围仅限非临床事务，病历、诊断、检验等临床数据不可授权。</p>
      <template #footer>
        <el-button @click="authVisible = false">取消</el-button>
        <el-button type="primary" :loading="authSubmitting" @click="submitAuthorization">创建授权</el-button>
      </template>
    </el-dialog>

    <el-drawer v-model="detailVisible" title="家庭账户详情" size="780px">
      <div v-loading="detailLoading">
        <template v-if="detail">
          <div class="detail-hero">
            <div>
              <h3>{{ detail.familyName }}</h3>
              <span class="cell-mono">{{ detail.familyNo }}</span>
            </div>
            <span class="badge" :class="detail.status === 'active' ? 'badge-success' : 'badge-danger'">
              {{ detail.status === 'active' ? '启用' : '停用' }}
            </span>
          </div>
          <p class="meta-line">创建时间 {{ formatTime(detail.createdAt) }} · 当前有效成员 {{ activeMembers.length }} 人</p>

          <el-tabs v-model="activeTab">
            <el-tab-pane label="成员与关系" name="members">
              <div class="pane-toolbar">
                <el-button v-if="canWrite" type="primary" plain size="small" @click="openAddMember">
                  添加成员
                </el-button>
                <el-checkbox v-model="includeEnded" @change="loadMembers">显示已解除关系</el-checkbox>
              </div>

              <el-table :data="members" row-key="id" size="small" stripe>
                <el-table-column label="成员" min-width="150">
                  <template #default="{ row }">
                    <div>{{ row.customer?.name || '—' }}</div>
                    <span class="cell-mono cell-muted">{{ row.customer?.customerNo || '—' }}</span>
                  </template>
                </el-table-column>
                <el-table-column label="手机号" width="120">
                  <template #default="{ row }">
                    <span class="cell-mono">{{ row.customer?.phoneMasked || '—' }}</span>
                  </template>
                </el-table-column>
                <el-table-column prop="relationshipLabel" label="关系" width="110" />
                <el-table-column label="管理员" width="80">
                  <template #default="{ row }">
                    <el-tag v-if="row.admin" size="small" type="warning" effect="plain">管理员</el-tag>
                    <span v-else class="cell-muted">—</span>
                  </template>
                </el-table-column>
                <el-table-column label="状态" width="80">
                  <template #default="{ row }">
                    <span class="badge" :class="row.status === 'active' ? 'badge-success' : 'badge-muted'">
                      {{ row.status === 'active' ? '有效' : '已解除' }}
                    </span>
                  </template>
                </el-table-column>
                <el-table-column label="加入时间" width="120">
                  <template #default="{ row }">
                    <span class="cell-muted">{{ formatDate(row.validFrom) }}</span>
                  </template>
                </el-table-column>
                <el-table-column label="解除时间" width="120">
                  <template #default="{ row }">
                    <span class="cell-muted">{{ formatDate(row.validTo) }}</span>
                  </template>
                </el-table-column>
                <el-table-column label="操作" width="280" fixed="right">
                  <template #default="{ row }">
                    <el-button link type="primary" @click="openGraph(row)">关系图</el-button>
                    <el-button
                      v-if="row.status === 'active' && row.customer"
                      link
                      type="primary"
                      @click="openRelations(row)"
                    >
                      查看关联
                    </el-button>
                    <template v-if="canWrite && row.status === 'active'">
                      <el-button link type="primary" @click="openEditMember(row)">编辑</el-button>
                      <el-button
                        v-if="!row.admin"
                        link
                        type="warning"
                        @click="setAdmin(row)"
                      >
                        设为管理员
                      </el-button>
                      <el-button v-if="!row.admin" link type="danger" @click="endMembership(row)">
                        解除
                      </el-button>
                    </template>
                    <span v-else-if="row.status !== 'active'" class="cell-muted">历史关系</span>
                  </template>
                </el-table-column>
              </el-table>
            </el-tab-pane>

            <el-tab-pane label="代办授权" name="authorizations">
              <div class="pane-toolbar">
                <el-select v-model="authMemberId" placeholder="选择家庭成员" clearable style="width: 220px" @change="loadAuthorizations">
                  <el-option
                    v-for="member in members"
                    :key="member.id"
                    :label="`${member.customer?.name ?? '—'}（${member.relationshipLabel}）`"
                    :value="member.id"
                  />
                </el-select>
                <el-button v-if="canAuthorize" type="primary" plain size="small" @click="openCreateAuthorization">
                  创建授权
                </el-button>
              </div>

              <el-table v-loading="authLoading" :data="authorizations" row-key="id" size="small" stripe>
                <el-table-column label="被授权人" min-width="140">
                  <template #default="{ row }">{{ row.grantee?.name || '—' }}</template>
                </el-table-column>
                <el-table-column prop="scopeLabel" label="代办范围" width="110" />
                <el-table-column label="状态" width="90">
                  <template #default="{ row }">
                    <span class="badge" :class="row.status === 'active' ? 'badge-success' : 'badge-danger'">
                      {{ row.status === 'active' ? '有效' : row.status === 'revoked' ? '已撤销' : '已过期' }}
                    </span>
                  </template>
                </el-table-column>
                <el-table-column label="生效时间" width="120">
                  <template #default="{ row }">
                    <span class="cell-muted">{{ formatDate(row.validFrom) }}</span>
                  </template>
                </el-table-column>
                <el-table-column label="截止时间" width="120">
                  <template #default="{ row }">
                    <span class="cell-muted">{{ row.validTo ? formatDate(row.validTo) : '长期' }}</span>
                  </template>
                </el-table-column>
                <el-table-column label="撤销时间" width="130">
                  <template #default="{ row }">
                    <span class="cell-muted">{{ formatTime(row.revokedAt) }}</span>
                  </template>
                </el-table-column>
                <el-table-column v-if="canAuthorize" label="操作" width="90" fixed="right">
                  <template #default="{ row }">
                    <el-button v-if="row.status === 'active'" link type="danger" @click="revoke(row)">
                      撤销
                    </el-button>
                  </template>
                </el-table-column>
              </el-table>

              <h4 class="detail-title">代办权限校验</h4>
              <div class="check-form">
                <el-select v-model="checkForm.memberId" placeholder="家庭成员" style="width: 170px">
                  <el-option
                    v-for="member in members"
                    :key="member.id"
                    :label="member.customer?.name ?? '—'"
                    :value="member.id"
                  />
                </el-select>
                <el-select v-model="checkForm.granteeCustomerId" placeholder="被授权人" style="width: 170px">
                  <el-option
                    v-for="member in checkGranteeOptions"
                    :key="member.id"
                    :label="member.customer?.name ?? '—'"
                    :value="member.customer?.id ?? ''"
                  />
                </el-select>
                <el-select v-model="checkForm.scopeCode" placeholder="代办范围" style="width: 140px">
                  <el-option
                    v-for="item in scopeOptions"
                    :key="item.value"
                    :label="item.label"
                    :value="item.value"
                  />
                </el-select>
                <el-button type="primary" :loading="checking" @click="runCheck">校验</el-button>
              </div>
              <el-alert
                v-if="checkResult"
                :title="checkResult.message"
                :type="checkResult.allowed ? 'success' : 'error'"
                :closable="false"
                show-icon
                class="check-result"
              />
            </el-tab-pane>
          </el-tabs>
        </template>
      </div>
    </el-drawer>

    <el-dialog v-model="graphVisible" title="家庭关系图谱" width="640px" append-to-body>
      <div v-loading="graphLoading">
        <template v-if="graphSelected">
          <div class="graph-hero">
            <div>
              <h4>
                {{ graphSelected.name || '—' }}
                <el-tag v-if="graphSelected.admin" size="small" type="warning" effect="plain">
                  家庭管理员 / 户主
                </el-tag>
              </h4>
              <p class="meta-line">
                <span class="cell-mono">{{ graphSelected.customerNo || '—' }}</span>
                · 与户主关系：{{ graphSelected.relationshipLabel }}
                · <span class="cell-mono">{{ graphSelected.phoneMasked || '—' }}</span>
              </p>
            </div>
          </div>
          <el-table :data="graphRows" row-key="customerId" size="small" stripe>
            <el-table-column label="姓名" min-width="150">
              <template #default="{ row }">
                <div>{{ row.name || '—' }}</div>
                <span class="cell-mono cell-muted">{{ row.customerNo || '—' }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="relationshipLabel" label="与户主关系" width="130" />
            <el-table-column label="与该成员关系" min-width="140">
              <template #default="{ row }">
                <el-tag size="small" effect="plain">{{ row.relationToSelected }}</el-tag>
              </template>
            </el-table-column>
          </el-table>
          <p class="form-tip">称谓由户主关系演示级推导，仅供参考，不代表医学或法律亲属关系。</p>
        </template>
      </div>
    </el-dialog>

    <el-drawer v-model="relationsVisible" title="成员关联称谓" size="560px" append-to-body>
      <div v-loading="relationsLoading">
        <template v-if="relations">
          <div class="rel-self-card">
            <div class="rel-self-head">
              <h4>
                {{ relations.self.name }}
                <el-tag v-if="relations.self.admin" size="small" type="warning" effect="plain">
                  家庭管理员 / 户主
                </el-tag>
              </h4>
              <span class="badge" :class="relations.self.gender === 'female' ? 'badge-neutral' : 'badge-success'">
                {{ relations.self.gender === 'male' ? '男' : relations.self.gender === 'female' ? '女' : '未知' }}
              </span>
            </div>
            <p class="meta-line">
              <span class="cell-mono">{{ relations.self.customerNo }}</span>
              · 生日 {{ relations.self.birthDate || '—' }}
              · 与户主关系：{{ relations.self.relationshipLabel }}
              · <span class="cell-mono">{{ relations.self.phoneMasked || '—' }}</span>
            </p>
          </div>

          <h4 class="detail-title">其他成员与所选成员的关联</h4>
          <el-table :data="relations.relations" size="small" stripe empty-text="暂无其他有效成员">
            <el-table-column label="姓名" min-width="110">
              <template #default="{ row }">
                <div>{{ row.member.name || '—' }}</div>
                <span class="cell-mono cell-muted">{{ row.member.customerNo || '—' }}</span>
              </template>
            </el-table-column>
            <el-table-column label="与所选成员关系" min-width="120">
              <template #default="{ row }">
                <el-tag size="small" effect="plain">{{ row.relationToSelf }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="反向称谓" min-width="120">
              <template #default="{ row }">
                <el-tag size="small" type="info" effect="plain">{{ row.relationFromSelf }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="手机号" width="120">
              <template #default="{ row }">
                <span class="cell-mono">{{ row.member.phoneMasked || '—' }}</span>
              </template>
            </el-table-column>
          </el-table>

          <h4 class="detail-title">成员间关系</h4>
          <p v-if="!peerPairs.length" class="cell-muted">暂无其他成员间关系</p>
          <ul v-else class="peer-list">
            <li v-for="pair in peerPairs" :key="pair.key">{{ pair.lines.join('；') }}</li>
          </ul>
          <p class="form-tip">称谓由户主关系演示级推导，选中任意成员即可查看其与其他成员的互称。</p>
        </template>
      </div>
    </el-drawer>
  </section>
</template>

<style scoped lang="scss">
.detail-hero {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  padding-bottom: 16px;
  border-bottom: 1px solid var(--crm-border);
}

.detail-hero h3 {
  font-size: 20px;
}

.meta-line {
  margin: 12px 0 4px;
  color: var(--crm-ink-soft);
  font-size: 13px;
}

.pane-toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin: 12px 0;
}

.detail-title {
  margin: 24px 0 12px;
  font-size: 14px;
}

.check-form {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.check-result {
  margin-top: 14px;
}

.badge-muted {
  background: #eef1f5;
  color: #6b7a90;
}

.rel-self-card {
  padding: 14px 16px;
  border: 1px solid var(--crm-border);
  border-radius: 10px;
  background: #f8fafc;
}

.rel-self-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.rel-self-head h4 {
  margin: 0;
  font-size: 16px;
  display: flex;
  align-items: center;
  gap: 8px;
}

.rel-self-card .meta-line {
  margin: 8px 0 0;
}

.peer-list {
  margin: 0;
  padding-left: 18px;
  color: var(--crm-ink-soft);
  font-size: 13px;
  line-height: 2;
}
</style>
