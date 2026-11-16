<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { createRole, fetchPermissions, fetchRoles, updateRole } from '@/api/roles'
import { fetchOrgTree } from '@/api/orgs'
import type { OrgNode, PermissionGroup, RoleResponse } from '@/types/system'
import type { OrgTreeOption } from '@/utils/orgTree'
import { toOrgOptions } from '@/utils/orgTree'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
const canWrite = computed(() => auth.has('system:role:write'))

const loading = ref(false)
const roles = ref<RoleResponse[]>([])
const permissionGroups = ref<PermissionGroup[]>([])
const orgTree = ref<OrgNode[]>([])
const orgOptions = computed<OrgTreeOption[]>(() => toOrgOptions(orgTree.value))

const scopeLabels: Record<string, string> = {
  all: '全院数据',
  org_and_children: '本组织及下级',
  org: '本组织',
  self: '仅本人',
  custom: '自定义组织'
}

const scopeOptions = Object.entries(scopeLabels).map(([value, label]) => ({ value, label }))

const permissionCount = computed(() =>
  permissionGroups.value.reduce((sum, group) => sum + group.items.length, 0)
)

const dialogVisible = ref(false)
const dialogMode = ref<'create' | 'edit'>('create')
const submitting = ref(false)
const formRef = ref<FormInstance>()
const editingId = ref<string>()
const editingBuiltin = ref(false)
const form = reactive({
  roleCode: '',
  roleName: '',
  description: '',
  dataScope: 'self',
  permissionCodes: [] as string[],
  orgIds: [] as string[],
  status: 'active' as 'active' | 'disabled'
})

const formRules: FormRules = {
  roleCode: [
    { required: true, message: '请输入角色编码', trigger: 'blur' },
    { pattern: /^[A-Za-z0-9_]{2,64}$/, message: '2-64 位字母、数字或下划线', trigger: 'blur' }
  ],
  roleName: [{ required: true, message: '请输入角色名称', trigger: 'blur' }],
  dataScope: [{ required: true, message: '请选择数据范围', trigger: 'change' }],
  permissionCodes: [{ required: true, message: '至少选择一个权限', trigger: 'change' }]
}

async function loadAll() {
  loading.value = true
  try {
    const [roleList, groups, orgs] = await Promise.all([
      fetchRoles(),
      fetchPermissions(),
      fetchOrgTree()
    ])
    roles.value = roleList
    permissionGroups.value = groups
    orgTree.value = orgs
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '加载角色与权限失败')
  } finally {
    loading.value = false
  }
}

function openCreate() {
  dialogMode.value = 'create'
  editingId.value = undefined
  editingBuiltin.value = false
  Object.assign(form, {
    roleCode: '',
    roleName: '',
    description: '',
    dataScope: 'self',
    permissionCodes: [],
    orgIds: [],
    status: 'active'
  })
  dialogVisible.value = true
  formRef.value?.clearValidate()
}

function openEdit(row: RoleResponse) {
  dialogMode.value = 'edit'
  editingId.value = row.id
  editingBuiltin.value = row.roleCode === 'SUPER_ADMIN'
  Object.assign(form, {
    roleCode: row.roleCode,
    roleName: row.roleName,
    description: row.description ?? '',
    dataScope: row.dataScope,
    permissionCodes: [...row.permissionCodes],
    orgIds: [...row.orgIds],
    status: row.status
  })
  dialogVisible.value = true
  formRef.value?.clearValidate()
}

async function submitForm() {
  if (!formRef.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  if (form.dataScope === 'custom' && form.orgIds.length === 0) {
    ElMessage.warning('自定义数据范围必须选择至少一个组织')
    return
  }

  submitting.value = true
  try {
    const payload = {
      roleName: form.roleName.trim(),
      description: form.description || undefined,
      dataScope: form.dataScope,
      permissionCodes: form.permissionCodes,
      orgIds: form.dataScope === 'custom' ? form.orgIds : []
    }
    if (dialogMode.value === 'create') {
      await createRole({ roleCode: form.roleCode.trim(), ...payload })
      ElMessage.success('角色已创建')
    } else if (editingId.value) {
      await updateRole(editingId.value, { ...payload, status: form.status })
      ElMessage.success('角色已更新')
    }
    dialogVisible.value = false
    await loadAll()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '保存角色失败')
  } finally {
    submitting.value = false
  }
}

function formatTime(value?: string) {
  return value ? new Date(value).toLocaleString('zh-CN', { hour12: false }) : '—'
}

onMounted(loadAll)
</script>

<template>
  <section class="card table-card">
    <div class="section-head">
      <div>
        <h3 class="section-title">角色与权限</h3>
        <p class="section-subtitle">
          角色决定可执行的操作（权限项）与可见数据范围；内置超级管理员角色不允许修改。
        </p>
      </div>
      <el-button v-if="canWrite" type="primary" @click="openCreate">新建角色</el-button>
    </div>

    <el-table v-loading="loading" :data="roles" row-key="id" stripe>
      <el-table-column prop="roleCode" label="角色编码" min-width="150">
        <template #default="{ row }">
          <span class="cell-mono">{{ row.roleCode }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="roleName" label="角色名称" min-width="140" />
      <el-table-column prop="description" label="说明" min-width="200">
        <template #default="{ row }">{{ row.description || '—' }}</template>
      </el-table-column>
      <el-table-column label="数据范围" width="150">
        <template #default="{ row }">
          <el-tag size="small" effect="plain" :type="row.dataScope === 'all' ? 'warning' : 'info'">
            {{ scopeLabels[row.dataScope] || row.dataScope }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="权限项" width="110">
        <template #default="{ row }">{{ row.permissionCodes.length }} / {{ permissionCount }}</template>
      </el-table-column>
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <span class="badge" :class="row.status === 'active' ? 'badge-success' : 'badge-danger'">
            {{ row.status === 'active' ? '启用' : '停用' }}
          </span>
        </template>
      </el-table-column>
      <el-table-column label="创建时间" width="170">
        <template #default="{ row }">
          <span class="cell-muted">{{ formatTime(row.createdAt) }}</span>
        </template>
      </el-table-column>
      <el-table-column v-if="canWrite" label="操作" width="120" fixed="right">
        <template #default="{ row }">
          <el-tooltip v-if="row.roleCode === 'SUPER_ADMIN'" content="内置角色不允许修改" placement="top">
            <span><el-button link type="primary" disabled>编辑</el-button></span>
          </el-tooltip>
          <el-button v-else link type="primary" @click="openEdit(row)">编辑</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog
      v-model="dialogVisible"
      :title="dialogMode === 'create' ? '新建角色' : '编辑角色'"
      width="720px"
      top="6vh"
    >
      <el-form ref="formRef" :model="form" :rules="formRules" label-width="92px">
        <el-form-item label="角色编码" prop="roleCode">
          <el-input
            v-model="form.roleCode"
            :disabled="dialogMode === 'edit'"
            placeholder="如 DEPT_MANAGER，创建后不可修改"
          />
        </el-form-item>
        <el-form-item label="角色名称" prop="roleName">
          <el-input v-model="form.roleName" />
        </el-form-item>
        <el-form-item label="说明">
          <el-input v-model="form.description" type="textarea" :rows="2" maxlength="500" show-word-limit />
        </el-form-item>
        <el-form-item label="数据范围" prop="dataScope">
          <el-radio-group v-model="form.dataScope" :disabled="editingBuiltin">
            <el-radio-button v-for="scope in scopeOptions" :key="scope.value" :value="scope.value">
              {{ scope.label }}
            </el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="form.dataScope === 'custom'" label="自定义组织">
          <el-tree-select
            v-model="form.orgIds"
            :data="orgOptions"
            multiple
            check-strictly
            filterable
            placeholder="选择该角色可见的组织"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item v-if="dialogMode === 'edit'" label="状态">
          <el-radio-group v-model="form.status" :disabled="editingBuiltin">
            <el-radio value="active">启用</el-radio>
            <el-radio value="disabled">停用</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="权限项" prop="permissionCodes">
          <div class="permission-box">
            <div v-for="group in permissionGroups" :key="group.module" class="permission-group">
              <h4>{{ group.module }}</h4>
              <el-checkbox-group v-model="form.permissionCodes" :disabled="editingBuiltin">
                <el-checkbox
                  v-for="item in group.items"
                  :key="item.permissionCode"
                  :value="item.permissionCode"
                  :title="item.description || item.permissionName"
                >
                  {{ item.permissionName }}
                  <span class="perm-code">{{ item.permissionCode }}</span>
                </el-checkbox>
              </el-checkbox-group>
            </div>
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" :disabled="editingBuiltin" @click="submitForm">
          保存
        </el-button>
      </template>
    </el-dialog>
  </section>
</template>

<style scoped lang="scss">
.permission-box {
  width: 100%;
  max-height: 320px;
  overflow: auto;
  padding: 14px 16px;
  border: 1px solid var(--crm-border);
  border-radius: var(--crm-radius-md);
  background: #f8fafc;
}

.permission-group + .permission-group {
  margin-top: 14px;
}

.permission-group h4 {
  margin-bottom: 8px;
  color: var(--crm-ink-soft);
  font-size: 13px;
}

.permission-group :deep(.el-checkbox) {
  min-width: 210px;
  margin-right: 12px;
}

.perm-code {
  margin-left: 6px;
  color: var(--crm-muted);
  font-family: 'Cascadia Code', Consolas, monospace;
  font-size: 11px;
}
</style>
