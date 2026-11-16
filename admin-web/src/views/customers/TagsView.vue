<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { createTag, fetchTags, updateTag } from '@/api/tags'
import type { TagItem } from '@/types/customer'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
const canWrite = computed(() => auth.has('crm:tag:write'))

const typeLabels: Record<string, string> = { customer: '客户标签', service: '服务标签', risk: '风险标签' }
const typeOptions = Object.entries(typeLabels).map(([value, label]) => ({ value, label }))

const loading = ref(false)
const includeDisabled = ref(true)
const rows = ref<TagItem[]>([])

const dialogVisible = ref(false)
const dialogMode = ref<'create' | 'edit'>('create')
const submitting = ref(false)
const formRef = ref<FormInstance>()
const editingId = ref<string>()
const form = reactive({
  tagCode: '',
  tagName: '',
  tagType: 'customer',
  sortOrder: 0,
  status: 'active' as 'active' | 'disabled'
})

const formRules: FormRules = {
  tagCode: [
    { required: true, message: '请输入标签编码', trigger: 'blur' },
    { pattern: /^[A-Za-z0-9_]{2,64}$/, message: '2-64 位字母、数字或下划线', trigger: 'blur' }
  ],
  tagName: [{ required: true, message: '请输入标签名称', trigger: 'blur' }]
}

async function load() {
  loading.value = true
  try {
    rows.value = await fetchTags(includeDisabled.value)
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '加载标签失败')
  } finally {
    loading.value = false
  }
}

function openCreate() {
  dialogMode.value = 'create'
  editingId.value = undefined
  Object.assign(form, { tagCode: '', tagName: '', tagType: 'customer', sortOrder: 0, status: 'active' })
  dialogVisible.value = true
  formRef.value?.clearValidate()
}

function openEdit(row: TagItem) {
  dialogMode.value = 'edit'
  editingId.value = row.id
  Object.assign(form, {
    tagCode: row.tagCode,
    tagName: row.tagName,
    tagType: row.tagType,
    sortOrder: row.sortOrder,
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
      await createTag({
        tagCode: form.tagCode.trim(),
        tagName: form.tagName.trim(),
        tagType: form.tagType,
        sortOrder: form.sortOrder
      })
      ElMessage.success('标签已创建')
    } else if (editingId.value) {
      await updateTag(editingId.value, {
        tagName: form.tagName.trim(),
        tagType: form.tagType,
        sortOrder: form.sortOrder,
        status: form.status
      })
      ElMessage.success('标签已更新')
    }
    dialogVisible.value = false
    await load()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '保存标签失败')
  } finally {
    submitting.value = false
  }
}

function formatTime(value?: string) {
  return value ? new Date(value).toLocaleString('zh-CN', { hour12: false }) : '—'
}

onMounted(load)
</script>

<template>
  <section class="card table-card">
    <div class="section-head">
      <div>
        <h3 class="section-title">标签管理</h3>
        <p class="section-subtitle">客户标签用于分群与检索；停用标签不再可用于新分配，历史关联仍保留。</p>
      </div>
      <div class="head-actions">
        <el-switch v-model="includeDisabled" active-text="含停用" @change="load" />
        <el-button v-if="canWrite" type="primary" @click="openCreate">新建标签</el-button>
      </div>
    </div>

    <el-table v-loading="loading" :data="rows" row-key="id" stripe>
      <el-table-column prop="tagCode" label="标签编码" min-width="150">
        <template #default="{ row }">
          <span class="cell-mono">{{ row.tagCode }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="tagName" label="标签名称" min-width="150" />
      <el-table-column label="类型" width="120">
        <template #default="{ row }">
          <el-tag size="small" effect="plain">{{ typeLabels[row.tagType] || row.tagType }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="sortOrder" label="排序" width="90" />
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
      <el-table-column v-if="canWrite" label="操作" width="100" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog
      v-model="dialogVisible"
      :title="dialogMode === 'create' ? '新建标签' : '编辑标签'"
      width="480px"
    >
      <el-form ref="formRef" :model="form" :rules="formRules" label-width="92px">
        <el-form-item label="标签编码" prop="tagCode">
          <el-input v-model="form.tagCode" :disabled="dialogMode === 'edit'" placeholder="如 VIP、POST_OP" />
        </el-form-item>
        <el-form-item label="标签名称" prop="tagName">
          <el-input v-model="form.tagName" maxlength="80" />
        </el-form-item>
        <el-form-item label="标签类型">
          <el-select v-model="form.tagType" style="width: 100%">
            <el-option v-for="item in typeOptions" :key="item.value" :label="item.label" :value="item.value" />
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
.head-actions {
  display: flex;
  align-items: center;
  gap: 16px;
}
</style>
