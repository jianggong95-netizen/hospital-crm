<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import {
  changeCustomerStatus,
  createCustomer,
  fetchCustomer,
  fetchCustomers,
  updateCustomer
} from '@/api/customers'
import { fetchTags } from '@/api/tags'
import type { CustomerDetail, CustomerSummary, TagItem } from '@/types/customer'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
const router = useRouter()
const canWrite = computed(() => auth.has('crm:customer:write'))
const canView360 = computed(() => auth.has('crm:patient360:read'))

const genderLabels: Record<string, string> = { unknown: '未知', male: '男', female: '女' }
const sourceOptions = [
  { value: 'manual', label: '手工建档' },
  { value: 'outpatient', label: '门诊' },
  { value: 'inpatient', label: '住院' },
  { value: 'physical', label: '体检' },
  { value: 'online', label: '线上渠道' },
  { value: 'referral', label: '转介绍' },
  { value: 'activity', label: '活动' }
]

const loading = ref(false)
const rows = ref<CustomerSummary[]>([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(20)
const keyword = ref('')
const tagFilter = ref<string>()
const sourceFilter = ref<string>()
const statusFilter = ref<string>()
const tags = ref<TagItem[]>([])

const dialogVisible = ref(false)
const dialogMode = ref<'create' | 'edit'>('create')
const submitting = ref(false)
const formRef = ref<FormInstance>()
const editingId = ref<string>()
const form = reactive({
  name: '',
  gender: 'unknown',
  birthDate: '',
  phone: '',
  idNo: '',
  sourceCode: 'manual',
  notes: '',
  tagIds: [] as string[],
  forceCreate: false
})

const detailVisible = ref(false)
const detailLoading = ref(false)
const detail = ref<CustomerDetail>()

const formRules: FormRules = {
  name: [
    { required: true, message: '请输入姓名', trigger: 'blur' },
    { max: 100, message: '姓名长度不能超过 100', trigger: 'blur' }
  ],
  phone: [
    {
      validator: (_rule, value: string, callback) => {
        const digits = (value || '').replace(/\D/g, '')
        if (value && !/^1\d{10}$/.test(digits)) {
          callback(new Error('手机号需为 11 位且以 1 开头'))
          return
        }
        callback()
      },
      trigger: 'blur'
    }
  ]
}

function sourceLabel(code?: string) {
  return sourceOptions.find((item) => item.value === code)?.label ?? code ?? '—'
}

async function loadTags() {
  try {
    tags.value = await fetchTags()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '加载标签失败')
  }
}

async function load() {
  loading.value = true
  try {
    const result = await fetchCustomers({
      keyword: keyword.value || undefined,
      tagId: tagFilter.value || undefined,
      sourceCode: sourceFilter.value || undefined,
      status: statusFilter.value || undefined,
      page: page.value,
      pageSize: pageSize.value
    })
    rows.value = result.items
    total.value = result.total
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '加载客户列表失败')
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
  tagFilter.value = undefined
  sourceFilter.value = undefined
  statusFilter.value = undefined
  search()
}

function openCreate() {
  dialogMode.value = 'create'
  editingId.value = undefined
  Object.assign(form, {
    name: '',
    gender: 'unknown',
    birthDate: '',
    phone: '',
    idNo: '',
    sourceCode: 'manual',
    notes: '',
    tagIds: [],
    forceCreate: false
  })
  dialogVisible.value = true
  formRef.value?.clearValidate()
}

async function openEdit(row: CustomerSummary) {
  try {
    const data = await fetchCustomer(row.id)
    dialogMode.value = 'edit'
    editingId.value = row.id
    Object.assign(form, {
      name: data.name,
      gender: data.gender,
      birthDate: data.birthDate ?? '',
      phone: '',
      idNo: '',
      sourceCode: data.sourceCode ?? '',
      notes: data.notes ?? '',
      tagIds: data.tags.map((tag) => tag.id),
      forceCreate: false
    })
    dialogVisible.value = true
    formRef.value?.clearValidate()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '加载客户详情失败')
  }
}

async function submitForm() {
  if (!formRef.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  submitting.value = true
  try {
    if (dialogMode.value === 'create') {
      await createCustomer({
        name: form.name.trim(),
        gender: form.gender,
        birthDate: form.birthDate || undefined,
        phone: form.phone || undefined,
        idNo: form.idNo || undefined,
        sourceCode: form.sourceCode || undefined,
        notes: form.notes || undefined,
        tagIds: form.tagIds,
        forceCreate: form.forceCreate
      })
      ElMessage.success('客户档案已创建')
    } else if (editingId.value) {
      await updateCustomer(editingId.value, {
        name: form.name.trim(),
        gender: form.gender,
        birthDate: form.birthDate || undefined,
        phone: form.phone || undefined,
        idNo: form.idNo || undefined,
        sourceCode: form.sourceCode || undefined,
        notes: form.notes || undefined,
        tagIds: form.tagIds
      })
      ElMessage.success('客户档案已更新')
    }
    dialogVisible.value = false
    await load()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '保存失败')
  } finally {
    submitting.value = false
  }
}

async function openDetail(row: CustomerSummary) {
  detailVisible.value = true
  detailLoading.value = true
  detail.value = undefined
  try {
    detail.value = await fetchCustomer(row.id)
  } catch (error) {
    detailVisible.value = false
    ElMessage.error(error instanceof Error ? error.message : '加载客户详情失败')
  } finally {
    detailLoading.value = false
  }
}

async function toggleStatus(row: CustomerSummary) {
  const next = row.status === 'active' ? 'disabled' : 'active'
  const text = next === 'active' ? '启用' : '停用'
  try {
    await ElMessageBox.confirm(`确认${text}客户 ${row.name}（${row.customerNo}）？`, '状态变更', {
      type: 'warning',
      confirmButtonText: `确认${text}`,
      cancelButtonText: '取消'
    })
  } catch {
    return
  }
  try {
    await changeCustomerStatus(row.id, next)
    ElMessage.success(`已${text}`)
    await load()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '状态变更失败')
  }
}

function formatDate(value?: string) {
  return value ? value.slice(0, 10) : '—'
}

onMounted(async () => {
  await loadTags()
  await load()
})
</script>

<template>
  <section class="card table-card">
    <div class="section-head">
      <div>
        <h3 class="section-title">客户中心</h3>
        <p class="section-subtitle">
          客户档案台账；手机号与证件号加密存储，列表仅展示脱敏值，检索需精确匹配手机号或证件号。
        </p>
      </div>
      <el-button v-if="canWrite" type="primary" @click="openCreate">新建客户</el-button>
    </div>

    <div class="toolbar">
      <el-input v-model="keyword" placeholder="姓名 / 客户号 / 手机号 / 证件号" clearable @keyup.enter="search" />
      <el-select v-model="tagFilter" placeholder="全部标签" clearable @change="search">
        <el-option v-for="tag in tags" :key="tag.id" :label="tag.tagName" :value="tag.id" />
      </el-select>
      <el-select v-model="sourceFilter" placeholder="全部来源" clearable @change="search">
        <el-option v-for="item in sourceOptions" :key="item.value" :label="item.label" :value="item.value" />
      </el-select>
      <el-select v-model="statusFilter" placeholder="全部状态" clearable @change="search">
        <el-option label="启用" value="active" />
        <el-option label="停用" value="disabled" />
      </el-select>
      <el-button type="primary" plain @click="search">查询</el-button>
      <el-button @click="resetFilters">重置</el-button>
    </div>

    <el-table v-loading="loading" :data="rows" row-key="id" stripe>
      <el-table-column prop="customerNo" label="客户号" width="140">
        <template #default="{ row }">
          <span class="cell-mono">{{ row.customerNo }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="name" label="姓名" min-width="100" />
      <el-table-column label="性别" width="80">
        <template #default="{ row }">{{ genderLabels[row.gender] || row.gender }}</template>
      </el-table-column>
      <el-table-column label="手机号" width="130">
        <template #default="{ row }">
          <span class="cell-mono">{{ row.phoneMasked || '—' }}</span>
        </template>
      </el-table-column>
      <el-table-column label="证件号" width="170">
        <template #default="{ row }">
          <span class="cell-mono">{{ row.idNoMasked || '—' }}</span>
        </template>
      </el-table-column>
      <el-table-column label="标签" min-width="200">
        <template #default="{ row }">
          <div class="tag-list">
            <el-tag v-for="tag in row.tags" :key="tag.id" size="small" effect="plain">{{ tag.tagName }}</el-tag>
            <span v-if="!row.tags.length" class="cell-muted">—</span>
          </div>
        </template>
      </el-table-column>
      <el-table-column label="来源" width="110">
        <template #default="{ row }">{{ sourceLabel(row.sourceCode) }}</template>
      </el-table-column>
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <span class="badge" :class="row.status === 'active' ? 'badge-success' : 'badge-danger'">
            {{ row.status === 'active' ? '启用' : '停用' }}
          </span>
        </template>
      </el-table-column>
      <el-table-column label="建档日期" width="120">
        <template #default="{ row }">
          <span class="cell-muted">{{ formatDate(row.createdAt) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="240" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openDetail(row)">详情</el-button>
          <el-button
            v-if="canView360"
            link
            type="primary"
            @click="router.push({ name: 'patient360', query: { customerId: row.id } })"
          >
            360
          </el-button>
          <template v-if="canWrite">
            <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button link :type="row.status === 'active' ? 'danger' : 'success'" @click="toggleStatus(row)">
              {{ row.status === 'active' ? '停用' : '启用' }}
            </el-button>
          </template>
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

    <el-dialog
      v-model="dialogVisible"
      :title="dialogMode === 'create' ? '新建客户档案' : '编辑客户档案'"
      width="620px"
      top="6vh"
    >
      <el-form ref="formRef" :model="form" :rules="formRules" label-width="96px">
        <el-form-item label="姓名" prop="name">
          <el-input v-model="form.name" maxlength="100" />
        </el-form-item>
        <el-form-item label="性别">
          <el-radio-group v-model="form.gender">
            <el-radio value="unknown">未知</el-radio>
            <el-radio value="male">男</el-radio>
            <el-radio value="female">女</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="出生日期">
          <el-date-picker v-model="form.birthDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
        <el-form-item label="手机号" prop="phone">
          <el-input
            v-model="form.phone"
            :placeholder="dialogMode === 'edit' ? '留空则保持不变' : '11 位手机号'"
          />
        </el-form-item>
        <el-form-item label="证件号">
          <el-input
            v-model="form.idNo"
            :placeholder="dialogMode === 'edit' ? '留空则保持不变（15/18 位身份证）' : '15/18 位身份证号'"
          />
        </el-form-item>
        <el-form-item label="来源">
          <el-select v-model="form.sourceCode" style="width: 100%">
            <el-option v-for="item in sourceOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="标签">
          <el-select v-model="form.tagIds" multiple filterable placeholder="选择客户标签" style="width: 100%">
            <el-option v-for="tag in tags" :key="tag.id" :label="tag.tagName" :value="tag.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.notes" type="textarea" :rows="2" maxlength="1000" show-word-limit />
        </el-form-item>
        <el-form-item v-if="dialogMode === 'create'" label="重复校验">
          <el-checkbox v-model="form.forceCreate">手机号或证件号重复时仍强制创建</el-checkbox>
        </el-form-item>
      </el-form>
      <p class="form-tip">敏感字段仅保存密文与检索哈希，列表和接口响应不会返回明文。</p>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitForm">保存</el-button>
      </template>
    </el-dialog>

    <el-drawer v-model="detailVisible" title="客户档案" size="520px">
      <div v-loading="detailLoading">
        <template v-if="detail">
          <div class="detail-hero">
            <div>
              <h3>{{ detail.name }}</h3>
              <span class="cell-mono">{{ detail.customerNo }}</span>
            </div>
            <div class="hero-tags">
              <span class="badge" :class="detail.status === 'active' ? 'badge-success' : 'badge-danger'">
                {{ detail.status === 'active' ? '启用' : '停用' }}
              </span>
              <el-tag v-for="tag in detail.tags" :key="tag.id" size="small" effect="plain">
                {{ tag.tagName }}
              </el-tag>
            </div>
          </div>

          <el-descriptions :column="2" border class="detail-grid">
            <el-descriptions-item label="性别">{{ genderLabels[detail.gender] || detail.gender }}</el-descriptions-item>
            <el-descriptions-item label="出生日期">{{ detail.birthDate || '—' }}</el-descriptions-item>
            <el-descriptions-item label="来源">{{ sourceLabel(detail.sourceCode) }}</el-descriptions-item>
            <el-descriptions-item label="建档时间">{{ formatDate(detail.createdAt) }}</el-descriptions-item>
            <el-descriptions-item label="手机号">
              <span class="cell-mono">{{ detail.sensitiveVisible ? (detail.phone || '—') : (detail.phoneMasked || '—') }}</span>
            </el-descriptions-item>
            <el-descriptions-item label="证件号">
              <span class="cell-mono">{{ detail.sensitiveVisible ? (detail.idNo || '—') : (detail.idNoMasked || '—') }}</span>
            </el-descriptions-item>
          </el-descriptions>

          <el-alert
            v-if="!detail.sensitiveVisible"
            title="当前账号无敏感信息查看权限，仅展示脱敏值"
            type="info"
            :closable="false"
            show-icon
            class="sensitive-tip"
          />

          <h4 class="detail-title">备注</h4>
          <p class="notes">{{ detail.notes || '暂无备注' }}</p>
        </template>
      </div>
    </el-drawer>
  </section>
</template>

<style scoped lang="scss">
.tag-list {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

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

.hero-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  justify-content: flex-end;
}

.detail-grid {
  margin-top: 18px;
}

.sensitive-tip {
  margin-top: 16px;
}

.detail-title {
  margin: 22px 0 8px;
  font-size: 14px;
}

.notes {
  padding: 12px 14px;
  border-radius: 10px;
  background: #f8fafc;
  border: 1px solid var(--crm-border);
  color: var(--crm-ink-soft);
  font-size: 13px;
  line-height: 1.8;
  white-space: pre-wrap;
}
</style>
