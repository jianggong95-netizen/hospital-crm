<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import {
  changeUserStatus,
  createUser,
  fetchUsers,
  resetUserPassword,
  updateUser
} from '@/api/users'
import { fetchRoles } from '@/api/roles'
import { fetchOrgTree } from '@/api/orgs'
import type { OrgNode, RoleResponse, UserResponse } from '@/types/system'
import type { OrgTreeOption } from '@/utils/orgTree'
import { toOrgOptions } from '@/utils/orgTree'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
const canWrite = computed(() => auth.has('system:user:write'))

const loading = ref(false)
const rows = ref<UserResponse[]>([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(10)
const keyword = ref('')
const orgFilter = ref<string>()
const statusFilter = ref<string>()

const orgTree = ref<OrgNode[]>([])
const orgOptions = computed<OrgTreeOption[]>(() => toOrgOptions(orgTree.value))
const roles = ref<RoleResponse[]>([])

const dialogVisible = ref(false)
const dialogMode = ref<'create' | 'edit'>('create')
const submitting = ref(false)
const formRef = ref<FormInstance>()
const editingId = ref<string>()
const form = reactive({
  username: '',
  password: '',
  displayName: '',
  email: '',
  mobile: '',
  orgId: '',
  roleIds: [] as string[],
  status: 'active' as 'active' | 'disabled'
})

const passwordVisible = ref(false)
const passwordSubmitting = ref(false)
const passwordTarget = ref<UserResponse>()
const newPassword = ref('')

const formRules: FormRules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { pattern: /^[A-Za-z0-9._-]{4,64}$/, message: '4-64 位字母、数字或 . _ -', trigger: 'blur' }
  ],
  password: [{ required: true, message: '请输入初始密码', trigger: 'blur' }],
  displayName: [{ required: true, message: '请输入姓名', trigger: 'blur' }],
  orgId: [{ required: true, message: '请选择所属组织', trigger: 'change' }],
  roleIds: [{ required: true, message: '至少选择一个角色', trigger: 'change' }]
}

async function loadUsers() {
  loading.value = true
  try {
    const result = await fetchUsers({
      page: page.value,
      pageSize: pageSize.value,
      keyword: keyword.value || undefined,
      orgId: orgFilter.value || undefined,
      status: statusFilter.value || undefined
    })
    rows.value = result.items
    total.value = result.total
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '加载用户失败')
  } finally {
    loading.value = false
  }
}

async function loadOptions() {
  try {
    const [orgs, roleList] = await Promise.all([fetchOrgTree(), fetchRoles()])
    orgTree.value = orgs
    roles.value = roleList
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '加载基础数据失败')
  }
}

function search() {
  page.value = 1
  loadUsers()
}

function resetFilters() {
  keyword.value = ''
  orgFilter.value = undefined
  statusFilter.value = undefined
  search()
}

function openCreate() {
  dialogMode.value = 'create'
  editingId.value = undefined
  Object.assign(form, {
    username: '',
    password: '',
    displayName: '',
    email: '',
    mobile: '',
    orgId: '',
    roleIds: [],
    status: 'active'
  })
  dialogVisible.value = true
  formRef.value?.clearValidate()
}

function openEdit(row: UserResponse) {
  dialogMode.value = 'edit'
  editingId.value = row.id
  Object.assign(form, {
    username: row.username,
    password: '',
    displayName: row.displayName,
    email: row.email ?? '',
    mobile: '',
    orgId: row.orgId,
    roleIds: row.roles.map((role) => role.id),
    status: row.status
  })
  dialogVisible.value = true
  formRef.value?.clearValidate()
}

async function submitForm() {
  if (!formRef.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  submitting.value = true
  try {
    if (dialogMode.value === 'create') {
      await createUser({
        username: form.username.trim(),
        password: form.password,
        displayName: form.displayName.trim(),
        email: form.email || undefined,
        mobile: form.mobile || undefined,
        orgId: form.orgId,
        roleIds: form.roleIds
      })
      ElMessage.success('用户已创建')
    } else if (editingId.value) {
      await updateUser(editingId.value, {
        displayName: form.displayName.trim(),
        email: form.email || undefined,
        mobile: form.mobile || undefined,
        orgId: form.orgId,
        roleIds: form.roleIds,
        status: form.status
      })
      ElMessage.success('用户已更新')
    }
    dialogVisible.value = false
    await loadUsers()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '保存失败')
  } finally {
    submitting.value = false
  }
}

async function toggleStatus(row: UserResponse) {
  const next = row.status === 'active' ? 'disabled' : 'active'
  const actionText = next === 'active' ? '启用' : '停用'
  try {
    await ElMessageBox.confirm(`确认${actionText}用户 ${row.displayName}（${row.username}）？`, '状态变更', {
      type: 'warning',
      confirmButtonText: `确认${actionText}`,
      cancelButtonText: '取消'
    })
  } catch {
    return
  }
  try {
    await changeUserStatus(row.id, next)
    ElMessage.success(`已${actionText}`)
    await loadUsers()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '状态变更失败')
  }
}

function openResetPassword(row: UserResponse) {
  passwordTarget.value = row
  newPassword.value = ''
  passwordVisible.value = true
}

async function submitResetPassword() {
  if (!passwordTarget.value) return
  if (newPassword.value.length < 8) {
    ElMessage.warning('密码长度至少 8 位')
    return
  }
  passwordSubmitting.value = true
  try {
    await resetUserPassword(passwordTarget.value.id, newPassword.value)
    ElMessage.success('密码已重置，该用户所有会话已失效')
    passwordVisible.value = false
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '重置密码失败')
  } finally {
    passwordSubmitting.value = false
  }
}

function formatTime(value?: string) {
  return value ? new Date(value).toLocaleString('zh-CN', { hour12: false }) : '—'
}

onMounted(async () => {
  await loadOptions()
  await loadUsers()
})
</script>

<template>
  <section class="card table-card">
    <div class="section-head">
      <div>
        <h3 class="section-title">用户管理</h3>
        <p class="section-subtitle">维护院内账号、所属组织与角色授权，数据范围按当前管理员权限过滤</p>
      </div>
      <el-button v-if="canWrite" type="primary" @click="openCreate">新建用户</el-button>
    </div>

    <div class="toolbar">
      <el-input v-model="keyword" placeholder="用户名 / 姓名" clearable @keyup.enter="search" />
      <el-tree-select
        v-model="orgFilter"
        :data="orgOptions"
        placeholder="全部组织"
        clearable
        check-strictly
        filterable
        @change="search"
      />
      <el-select v-model="statusFilter" placeholder="全部状态" clearable @change="search">
        <el-option label="启用" value="active" />
        <el-option label="停用" value="disabled" />
      </el-select>
      <el-button type="primary" plain @click="search">查询</el-button>
      <el-button @click="resetFilters">重置</el-button>
    </div>

    <el-table v-loading="loading" :data="rows" row-key="id" stripe>
      <el-table-column prop="username" label="用户名" min-width="130">
        <template #default="{ row }">
          <span class="cell-mono">{{ row.username }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="displayName" label="姓名" min-width="120" />
      <el-table-column prop="orgName" label="所属组织" min-width="140">
        <template #default="{ row }">{{ row.orgName || '—' }}</template>
      </el-table-column>
      <el-table-column label="角色" min-width="200">
        <template #default="{ row }">
          <div class="role-tags">
            <el-tag v-for="role in row.roles" :key="role.id" size="small" effect="plain">
              {{ role.roleName }}
            </el-tag>
          </div>
        </template>
      </el-table-column>
      <el-table-column prop="mobileMasked" label="手机号" width="130">
        <template #default="{ row }">{{ row.mobileMasked || '—' }}</template>
      </el-table-column>
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <span class="badge" :class="row.status === 'active' ? 'badge-success' : 'badge-danger'">
            {{ row.status === 'active' ? '启用' : '停用' }}
          </span>
        </template>
      </el-table-column>
      <el-table-column label="最近登录" width="170">
        <template #default="{ row }">
          <span class="cell-muted">{{ formatTime(row.lastLoginAt) }}</span>
        </template>
      </el-table-column>
      <el-table-column v-if="canWrite" label="操作" width="230" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
          <el-button link type="primary" @click="openResetPassword(row)">重置密码</el-button>
          <el-button link :type="row.status === 'active' ? 'danger' : 'success'" @click="toggleStatus(row)">
            {{ row.status === 'active' ? '停用' : '启用' }}
          </el-button>
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
        @current-change="loadUsers"
        @size-change="search"
      />
    </div>

    <el-dialog v-model="dialogVisible" :title="dialogMode === 'create' ? '新建用户' : '编辑用户'" width="560px">
      <el-form ref="formRef" :model="form" :rules="formRules" label-width="96px">
        <el-form-item label="用户名" prop="username">
          <el-input v-model="form.username" :disabled="dialogMode === 'edit'" placeholder="用于登录，创建后不可修改" />
        </el-form-item>
        <el-form-item v-if="dialogMode === 'create'" label="初始密码" prop="password">
          <el-input v-model="form.password" type="password" show-password placeholder="8-64 位，须包含字母和数字" />
        </el-form-item>
        <el-form-item label="姓名" prop="displayName">
          <el-input v-model="form.displayName" />
        </el-form-item>
        <el-form-item label="所属组织" prop="orgId">
          <el-tree-select v-model="form.orgId" :data="orgOptions" check-strictly filterable style="width: 100%" />
        </el-form-item>
        <el-form-item label="角色" prop="roleIds">
          <el-select v-model="form.roleIds" multiple filterable placeholder="至少选择一个角色" style="width: 100%">
            <el-option
              v-for="role in roles"
              :key="role.id"
              :label="`${role.roleName}（${role.roleCode}）`"
              :value="role.id"
              :disabled="role.status !== 'active'"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="邮箱">
          <el-input v-model="form.email" placeholder="选填" />
        </el-form-item>
        <el-form-item label="手机号">
          <el-input v-model="form.mobile" placeholder="选填，仅保存脱敏结果" />
        </el-form-item>
        <el-form-item v-if="dialogMode === 'edit'" label="状态">
          <el-radio-group v-model="form.status">
            <el-radio value="active">启用</el-radio>
            <el-radio value="disabled">停用</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitForm">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="passwordVisible" title="重置密码" width="440px">
      <p class="form-tip">
        目标用户：{{ passwordTarget?.displayName }}（{{ passwordTarget?.username }}）。重置后该用户所有刷新令牌将失效。
      </p>
      <el-input
        v-model="newPassword"
        type="password"
        show-password
        placeholder="新密码：8-64 位，须包含字母和数字"
        style="margin-top: 12px"
      />
      <template #footer>
        <el-button @click="passwordVisible = false">取消</el-button>
        <el-button type="primary" :loading="passwordSubmitting" @click="submitResetPassword">确认重置</el-button>
      </template>
    </el-dialog>
  </section>
</template>

<style scoped lang="scss">
.role-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}
</style>
