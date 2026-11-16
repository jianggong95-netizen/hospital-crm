<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { createOrg, fetchOrgTree, updateOrg } from '@/api/orgs'
import type { OrgNode } from '@/types/system'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
const canWrite = computed(() => auth.has('system:org:write'))

const loading = ref(false)
const tree = ref<OrgNode[]>([])

const typeLabels: Record<string, string> = {
  hospital: '医院',
  department: '科室',
  team: '班组'
}

const dialogVisible = ref(false)
const dialogMode = ref<'create' | 'edit'>('create')
const submitting = ref(false)
const formRef = ref<FormInstance>()
const editingId = ref<string>()
const parentNode = ref<OrgNode>()
const form = reactive({
  orgCode: '',
  orgName: '',
  parentName: '',
  orgType: 'department',
  sortOrder: 0,
  status: 'active' as 'active' | 'disabled'
})

const formRules: FormRules = {
  orgCode: [
    { required: true, message: '请输入组织编码', trigger: 'blur' },
    { pattern: /^[A-Za-z0-9_]{2,64}$/, message: '2-64 位字母、数字或下划线', trigger: 'blur' }
  ],
  orgName: [{ required: true, message: '请输入组织名称', trigger: 'blur' }]
}

async function loadTree() {
  loading.value = true
  try {
    tree.value = await fetchOrgTree()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '加载组织架构失败')
  } finally {
    loading.value = false
  }
}

function openCreate(parent: OrgNode) {
  dialogMode.value = 'create'
  editingId.value = undefined
  parentNode.value = parent
  Object.assign(form, {
    orgCode: '',
    orgName: '',
    parentName: parent.orgName,
    orgType: 'department',
    sortOrder: 0,
    status: 'active'
  })
  dialogVisible.value = true
  formRef.value?.clearValidate()
}

function openEdit(node: OrgNode) {
  dialogMode.value = 'edit'
  editingId.value = node.id
  parentNode.value = undefined
  Object.assign(form, {
    orgCode: node.orgCode,
    orgName: node.orgName,
    parentName: node.parentId ? '上级组织' : '根组织',
    orgType: node.orgType,
    sortOrder: node.sortOrder,
    status: node.status
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
    if (dialogMode.value === 'create' && parentNode.value) {
      await createOrg({
        orgCode: form.orgCode.trim(),
        orgName: form.orgName.trim(),
        parentId: parentNode.value.id,
        orgType: form.orgType,
        sortOrder: form.sortOrder
      })
      ElMessage.success('组织已创建')
    } else if (dialogMode.value === 'edit' && editingId.value) {
      await updateOrg(editingId.value, {
        orgName: form.orgName.trim(),
        orgType: form.orgType,
        sortOrder: form.sortOrder,
        status: form.status
      })
      ElMessage.success('组织已更新')
    }
    dialogVisible.value = false
    await loadTree()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '保存组织失败')
  } finally {
    submitting.value = false
  }
}

async function disableNode(node: OrgNode) {
  try {
    await ElMessageBox.confirm(`确认停用组织 ${node.orgName}？存在下级组织或关联用户时会被拒绝。`, '停用组织', {
      type: 'warning',
      confirmButtonText: '确认停用',
      cancelButtonText: '取消'
    })
  } catch {
    return
  }
  try {
    await updateOrg(node.id, {
      orgName: node.orgName,
      orgType: node.orgType,
      sortOrder: node.sortOrder,
      status: 'disabled'
    })
    ElMessage.success('组织已停用')
    await loadTree()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '停用失败')
  }
}

onMounted(loadTree)
</script>

<template>
  <section class="card table-card">
    <div class="section-head">
      <div>
        <h3 class="section-title">组织架构</h3>
        <p class="section-subtitle">组织层级用于归属与数据范围计算；停用前需先处理下级组织与关联用户。</p>
      </div>
      <el-button :loading="loading" @click="loadTree">刷新</el-button>
    </div>

    <el-tree
      v-loading="loading"
      :data="tree"
      node-key="id"
      default-expand-all
      :expand-on-click-node="false"
      class="org-tree"
    >
      <template #default="{ data }">
        <div class="org-node">
          <div class="org-main">
            <strong>{{ data.orgName }}</strong>
            <span class="cell-mono">{{ data.orgCode }}</span>
            <el-tag size="small" effect="plain">{{ typeLabels[data.orgType] || data.orgType }}</el-tag>
            <span class="badge" :class="data.status === 'active' ? 'badge-success' : 'badge-danger'">
              {{ data.status === 'active' ? '启用' : '停用' }}
            </span>
            <span class="cell-muted">排序 {{ data.sortOrder }}</span>
          </div>
          <div v-if="canWrite" class="org-actions">
            <el-button link type="primary" @click.stop="openCreate(data)">新增下级</el-button>
            <el-button link type="primary" @click.stop="openEdit(data)">编辑</el-button>
            <el-button
              v-if="data.status === 'active'"
              link
              type="danger"
              @click.stop="disableNode(data)"
            >
              停用
            </el-button>
          </div>
        </div>
      </template>
    </el-tree>

    <el-dialog
      v-model="dialogVisible"
      :title="dialogMode === 'create' ? '新增下级组织' : '编辑组织'"
      width="500px"
    >
      <el-form ref="formRef" :model="form" :rules="formRules" label-width="92px">
        <el-form-item v-if="dialogMode === 'create'" label="上级组织">
          <el-input :model-value="form.parentName" disabled />
        </el-form-item>
        <el-form-item label="组织编码" prop="orgCode">
          <el-input v-model="form.orgCode" :disabled="dialogMode === 'edit'" placeholder="如 OPD_INTERNAL" />
        </el-form-item>
        <el-form-item label="组织名称" prop="orgName">
          <el-input v-model="form.orgName" />
        </el-form-item>
        <el-form-item label="组织类型">
          <el-select v-model="form.orgType" style="width: 100%">
            <el-option v-for="(label, value) in typeLabels" :key="value" :label="label" :value="value" />
          </el-select>
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="form.sortOrder" :min="0" :max="9999" />
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
  </section>
</template>

<style scoped lang="scss">
.org-tree {
  padding: 6px 4px;
}

.org-node {
  display: flex;
  flex: 1;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  min-width: 0;
  padding: 7px 6px;
  border-bottom: 1px dashed #eef2f7;
}

.org-main {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px;
}

.org-actions {
  flex-shrink: 0;
}

.org-tree :deep(.el-tree-node__content) {
  height: auto;
  padding: 2px 0;
}
</style>
