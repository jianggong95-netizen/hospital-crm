<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import {
  approveKbDocument,
  askKnowledge,
  createKbCategory,
  createKbDocument,
  fetchKbCategories,
  fetchKbDocumentDetail,
  fetchKbDocuments,
  fetchKbQueryLogs,
  offlineKbDocument,
  rejectKbDocument,
  submitKbDocument,
  updateKbCategory,
  updateKbDocumentContent,
  updateKbDocumentMeta,
  uploadKbDocument
} from '@/api/knowledge'
import type {
  KbAskResult,
  KbCategoryView,
  KbDocumentDetail,
  KbDocumentView,
  KbQueryLogView
} from '@/types/knowledge'

const auth = useAuthStore()
const canWrite = computed(() => auth.has('crm:knowledge:write'))
const canReview = computed(() => auth.has('crm:knowledge:review'))

const activeTab = ref<'docs' | 'categories' | 'ask' | 'logs'>('docs')

const statusLabels: Record<string, string> = {
  DRAFT: '草稿',
  REVIEWING: '待审核',
  PUBLISHED: '已发布',
  OFFLINE: '已下架'
}

function statusTag(status: string) {
  if (status === 'PUBLISHED') return 'success'
  if (status === 'REVIEWING') return 'warning'
  if (status === 'OFFLINE') return 'danger'
  return 'info'
}

const actionLabels: Record<string, string> = {
  SUBMIT: '提交审核',
  APPROVE: '审核通过',
  REJECT: '审核驳回',
  OFFLINE: '下架'
}

function formatTime(value?: string) {
  if (!value) return '—'
  return value.replace('T', ' ').slice(0, 16)
}

function formatSize(bytes?: number) {
  if (bytes === undefined || bytes === null) return '—'
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / 1024 / 1024).toFixed(1)} MB`
}

// ------------------------------------------------------------------
// 分类
// ------------------------------------------------------------------
const categories = ref<KbCategoryView[]>([])

async function loadCategories() {
  categories.value = await fetchKbCategories()
}

const categoryDialog = reactive({
  visible: false,
  editingId: '',
  code: '',
  name: '',
  sortOrder: 100,
  status: 'active'
})

function openCategoryDialog(row?: KbCategoryView) {
  categoryDialog.editingId = row?.id ?? ''
  categoryDialog.code = row?.code ?? ''
  categoryDialog.name = row?.name ?? ''
  categoryDialog.sortOrder = row?.sortOrder ?? 100
  categoryDialog.status = row?.status ?? 'active'
  categoryDialog.visible = true
}

async function submitCategory() {
  if (!categoryDialog.code || !categoryDialog.name) {
    ElMessage.warning('请填写分类编码与名称')
    return
  }
  const payload = {
    code: categoryDialog.code,
    name: categoryDialog.name,
    sortOrder: categoryDialog.sortOrder,
    status: categoryDialog.status
  }
  try {
    if (categoryDialog.editingId) {
      await updateKbCategory(categoryDialog.editingId, payload)
    } else {
      await createKbCategory(payload)
    }
    ElMessage.success('分类已保存')
    categoryDialog.visible = false
    await loadCategories()
  } catch (error) {
    ElMessage.error((error as Error).message)
  }
}

// ------------------------------------------------------------------
// 文档列表
// ------------------------------------------------------------------
const docs = ref<KbDocumentView[]>([])
const docsLoading = ref(false)
const docsTotal = ref(0)
const docsQuery = reactive({ keyword: '', categoryId: '', status: '', page: 1, pageSize: 20 })

async function loadDocs() {
  docsLoading.value = true
  try {
    const result = await fetchKbDocuments({
      keyword: docsQuery.keyword || undefined,
      categoryId: docsQuery.categoryId || undefined,
      status: docsQuery.status || undefined,
      page: docsQuery.page,
      pageSize: docsQuery.pageSize
    })
    docs.value = result.items
    docsTotal.value = result.total
  } finally {
    docsLoading.value = false
  }
}

// ------------------------------------------------------------------
// 新建 / 上传 / 编辑
// ------------------------------------------------------------------
const editDialog = reactive({
  visible: false,
  mode: 'create' as 'create' | 'meta' | 'content',
  documentId: '',
  title: '',
  categoryId: '',
  summary: '',
  content: '',
  changeNote: ''
})

function openCreate() {
  editDialog.mode = 'create'
  editDialog.documentId = ''
  editDialog.title = ''
  editDialog.categoryId = ''
  editDialog.summary = ''
  editDialog.content = ''
  editDialog.changeNote = ''
  editDialog.visible = true
}

function openMetaEdit(row: KbDocumentView) {
  editDialog.mode = 'meta'
  editDialog.documentId = row.id
  editDialog.title = row.title
  editDialog.categoryId = row.categoryId ?? ''
  editDialog.summary = row.summary ?? ''
  editDialog.visible = true
}

function openContentEdit(row: KbDocumentView) {
  editDialog.mode = 'content'
  editDialog.documentId = row.id
  editDialog.title = row.title
  editDialog.content = ''
  editDialog.changeNote = ''
  editDialog.visible = true
  void preloadContent(row.id)
}

async function preloadContent(documentId: string) {
  try {
    const detail = await fetchKbDocumentDetail(documentId)
    const draftVersion = detail.document.currentVersion + 1
    const target = detail.chunks.filter((chunk) =>
      detail.document.status === 'PUBLISHED' ? chunk.docVersion === draftVersion : true
    )
    editDialog.content = (target.length ? target : detail.chunks)
      .slice()
      .sort((a, b) => a.docVersion - b.docVersion || a.chunkIndex - b.chunkIndex)
      .map((chunk) => chunk.content)
      .join('\n')
  } catch (error) {
    ElMessage.error((error as Error).message)
  }
}

async function submitEdit() {
  try {
    if (editDialog.mode === 'create') {
      if (!editDialog.title.trim() || !editDialog.content.trim()) {
        ElMessage.warning('请填写标题与知识内容')
        return
      }
      await createKbDocument({
        title: editDialog.title.trim(),
        categoryId: editDialog.categoryId || undefined,
        summary: editDialog.summary || undefined,
        content: editDialog.content
      })
      ElMessage.success('知识文档已创建（草稿），送审前不会进入 AI 检索')
    } else if (editDialog.mode === 'meta') {
      if (!editDialog.title.trim()) {
        ElMessage.warning('请填写标题')
        return
      }
      await updateKbDocumentMeta(editDialog.documentId, {
        title: editDialog.title.trim(),
        categoryId: editDialog.categoryId || undefined,
        summary: editDialog.summary || undefined
      })
      ElMessage.success('文档信息已更新')
    } else {
      if (!editDialog.content.trim()) {
        ElMessage.warning('请填写知识内容')
        return
      }
      await updateKbDocumentContent(editDialog.documentId, {
        content: editDialog.content,
        changeNote: editDialog.changeNote || undefined
      })
      ElMessage.success('内容已更新为草稿切片')
    }
    editDialog.visible = false
    await loadDocs()
    if (detail.value?.document.id === editDialog.documentId) {
      await openDetail(editDialog.documentId)
    }
  } catch (error) {
    ElMessage.error((error as Error).message)
  }
}

const uploadDialog = reactive({
  visible: false,
  fileName: '',
  file: null as File | null,
  title: '',
  categoryId: '',
  summary: ''
})
const fileInput = ref<HTMLInputElement>()

function openUpload() {
  uploadDialog.fileName = ''
  uploadDialog.file = null
  uploadDialog.title = ''
  uploadDialog.categoryId = ''
  uploadDialog.summary = ''
  uploadDialog.visible = true
}

function onFileChange(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return
  if (!/\.(txt|md|markdown)$/i.test(file.name)) {
    ElMessage.warning('当前支持解析 .txt / .md 文本文件')
    input.value = ''
    return
  }
  if (file.size > 5 * 1024 * 1024) {
    ElMessage.warning('文件不能超过 5MB')
    input.value = ''
    return
  }
  uploadDialog.file = file
  uploadDialog.fileName = file.name
}

async function submitUpload() {
  if (!uploadDialog.file) {
    ElMessage.warning('请选择要上传的文件')
    return
  }
  try {
    await uploadKbDocument(uploadDialog.file, {
      title: uploadDialog.title.trim() || undefined,
      categoryId: uploadDialog.categoryId || undefined,
      summary: uploadDialog.summary || undefined
    })
    ElMessage.success('文件已解析为草稿切片，请核对后送审')
    uploadDialog.visible = false
    await loadDocs()
  } catch (error) {
    ElMessage.error((error as Error).message)
  }
}

// ------------------------------------------------------------------
// 状态流转操作
// ------------------------------------------------------------------
async function handleSubmit(row: KbDocumentView) {
  try {
    await submitKbDocument(row.id)
    ElMessage.success('已提交审核（含敏感信息扫描）')
    await afterAction(row.id)
  } catch (error) {
    ElMessage.error((error as Error).message)
  }
}

async function handleApprove(row: KbDocumentView) {
  try {
    const { value } = await ElMessageBox.prompt(
      `确认发布《${row.title}》？发布后版本号 +1，AI 检索将引用该版本`,
      '审核通过',
      { inputPlaceholder: '审核意见（可选）', confirmButtonText: '发布', cancelButtonText: '取消' }
    )
    await approveKbDocument(row.id, value || undefined)
    ElMessage.success('已发布，历史版本快照已保留')
    await afterAction(row.id)
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') {
      ElMessage.error((error as Error).message)
    }
  }
}

async function handleReject(row: KbDocumentView) {
  try {
    const { value } = await ElMessageBox.prompt(`驳回《${row.title}》？`, '审核驳回', {
      inputPlaceholder: '驳回原因（必填）',
      inputValidator: (text: string) => (text && text.trim() ? true : '请填写驳回原因'),
      confirmButtonText: '驳回',
      cancelButtonText: '取消'
    })
    await rejectKbDocument(row.id, (value as string).trim())
    ElMessage.success('已驳回，文档退回草稿')
    await afterAction(row.id)
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') {
      ElMessage.error((error as Error).message)
    }
  }
}

async function handleOffline(row: KbDocumentView) {
  try {
    const { value } = await ElMessageBox.prompt(
      `下架《${row.title}》后，AI 检索将立即不再引用该知识`,
      '知识下架',
      {
        inputPlaceholder: '下架原因（必填，例如：内容失效 / 政策变更）',
        inputValidator: (text: string) => (text && text.trim() ? true : '请填写下架原因'),
        confirmButtonText: '确认下架',
        confirmButtonClass: 'el-button--danger',
        cancelButtonText: '取消'
      }
    )
    await offlineKbDocument(row.id, (value as string).trim())
    ElMessage.success('知识已下架，检索不再引用')
    await afterAction(row.id)
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') {
      ElMessage.error((error as Error).message)
    }
  }
}

async function afterAction(documentId: string) {
  await loadDocs()
  if (detail.value?.document.id === documentId) {
    await openDetail(documentId)
  }
}

// ------------------------------------------------------------------
// 详情抽屉
// ------------------------------------------------------------------
const detail = ref<KbDocumentDetail>()
const detailVisible = ref(false)
const detailLoading = ref(false)
const detailTab = ref<'chunks' | 'versions' | 'reviews'>('chunks')

async function openDetail(documentId: string) {
  detailVisible.value = true
  detailLoading.value = true
  detailTab.value = 'chunks'
  try {
    detail.value = await fetchKbDocumentDetail(documentId)
  } catch (error) {
    ElMessage.error((error as Error).message)
    detailVisible.value = false
  } finally {
    detailLoading.value = false
  }
}

const sortedChunks = computed(() =>
  (detail.value?.chunks ?? [])
    .slice()
    .sort((a, b) => b.docVersion - a.docVersion || a.chunkIndex - b.chunkIndex)
)

// ------------------------------------------------------------------
// AI 问答测试
// ------------------------------------------------------------------
const askForm = reactive({ question: '', scene: 'chat', topK: 3 })
const askLoading = ref(false)
const askResult = ref<KbAskResult>()

async function submitAsk() {
  if (!askForm.question.trim()) {
    ElMessage.warning('请输入测试问题')
    return
  }
  askLoading.value = true
  try {
    askResult.value = await askKnowledge({
      question: askForm.question.trim(),
      scene: askForm.scene,
      topK: askForm.topK
    })
  } catch (error) {
    ElMessage.error((error as Error).message)
  } finally {
    askLoading.value = false
  }
}

// ------------------------------------------------------------------
// 问答记录
// ------------------------------------------------------------------
const logs = ref<KbQueryLogView[]>([])
const logsLoading = ref(false)
const logsTotal = ref(0)
const logsQuery = reactive({ page: 1, pageSize: 20 })

async function loadLogs() {
  logsLoading.value = true
  try {
    const result = await fetchKbQueryLogs(logsQuery.page, logsQuery.pageSize)
    logs.value = result.items
    logsTotal.value = result.total
  } finally {
    logsLoading.value = false
  }
}

function onTabChange(name: string | number) {
  if (name === 'categories' && !categories.value.length) {
    void loadCategories()
  }
  if (name === 'logs') {
    void loadLogs()
  }
}

onMounted(async () => {
  await Promise.all([loadCategories(), loadDocs()])
})
</script>

<template>
  <section class="kb-page">
    <header class="page-head">
      <div>
        <h2>AI 知识库与数字人</h2>
        <p class="page-desc">
          知识文档经上传解析、切片、敏感扫描与审核发布后，AI 才能引用回答；答案始终携带知识编号与版本引用，
          低置信度自动提示转人工，失效知识可一键下架并即时退出检索。未经授权的患者数据不会进入知识库。
        </p>
      </div>
    </header>

    <el-tabs v-model="activeTab" class="kb-tabs" @tab-change="onTabChange">
      <!-- ============================================================ -->
      <el-tab-pane label="知识文档" name="docs">
        <div class="toolbar">
          <el-input v-model="docsQuery.keyword" placeholder="标题 / 编号" clearable size="small" style="width: 180px" />
          <el-select v-model="docsQuery.categoryId" placeholder="全部分类" clearable size="small" style="width: 150px">
            <el-option v-for="item in categories" :key="item.id" :label="item.name" :value="item.id" />
          </el-select>
          <el-select v-model="docsQuery.status" placeholder="全部状态" clearable size="small" style="width: 120px">
            <el-option v-for="(label, key) in statusLabels" :key="key" :label="label" :value="key" />
          </el-select>
          <el-button size="small" type="primary" @click="docsQuery.page = 1; loadDocs()">查询</el-button>
          <span class="spacer" />
          <template v-if="canWrite">
            <el-button size="small" @click="openUpload">上传文件</el-button>
            <el-button size="small" type="primary" @click="openCreate">新建文档</el-button>
          </template>
        </div>

        <el-table v-loading="docsLoading" :data="docs" size="small" border>
          <el-table-column prop="docNo" label="编号" width="110" />
          <el-table-column label="标题" min-width="200">
            <template #default="{ row }">
              <el-link type="primary" :underline="false" @click="openDetail(row.id)">{{ row.title }}</el-link>
              <div class="hint">{{ row.summary || '—' }}</div>
            </template>
          </el-table-column>
          <el-table-column prop="categoryName" label="分类" width="120">
            <template #default="{ row }">{{ row.categoryName || '未分类' }}</template>
          </el-table-column>
          <el-table-column label="状态" width="94">
            <template #default="{ row }">
              <el-tag :type="statusTag(row.status)" size="small">{{ statusLabels[row.status] }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="版本" width="72">
            <template #default="{ row }">v{{ row.currentVersion }}</template>
          </el-table-column>
          <el-table-column label="来源" width="130">
            <template #default="{ row }">
              <span v-if="row.sourceType === 'file'">{{ row.fileName }}（{{ formatSize(row.fileSize) }}）</span>
              <span v-else>手工录入</span>
            </template>
          </el-table-column>
          <el-table-column label="更新时间" width="140">
            <template #default="{ row }">{{ formatTime(row.updatedAt) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="250" fixed="right">
            <template #default="{ row }">
              <el-button size="small" link type="primary" @click="openDetail(row.id)">详情</el-button>
              <template v-if="canWrite && (row.status === 'DRAFT' || row.status === 'OFFLINE')">
                <el-button size="small" link @click="openMetaEdit(row)">编辑</el-button>
                <el-button size="small" link @click="openContentEdit(row)">改内容</el-button>
              </template>
              <el-button v-if="canWrite && row.status === 'DRAFT'" size="small" link type="warning" @click="handleSubmit(row)">
                送审
              </el-button>
              <template v-if="canReview && row.status === 'REVIEWING'">
                <el-button size="small" link type="success" @click="handleApprove(row)">通过</el-button>
                <el-button size="small" link type="danger" @click="handleReject(row)">驳回</el-button>
              </template>
              <el-button v-if="canReview && row.status === 'PUBLISHED'" size="small" link type="danger" @click="handleOffline(row)">
                下架
              </el-button>
            </template>
          </el-table-column>
        </el-table>
        <el-pagination
          v-if="docsTotal > docsQuery.pageSize"
          v-model:current-page="docsQuery.page"
          :page-size="docsQuery.pageSize"
          :total="docsTotal"
          layout="prev, pager, next"
          small
          @current-change="loadDocs"
        />
      </el-tab-pane>

      <!-- ============================================================ -->
      <el-tab-pane label="分类管理" name="categories">
        <div class="toolbar">
          <span class="hint">分类用于组织知识文档，停用后不再出现在新建下拉中</span>
          <span class="spacer" />
          <el-button v-if="canWrite" size="small" type="primary" @click="openCategoryDialog()">新建分类</el-button>
        </div>
        <el-table :data="categories" size="small" border>
          <el-table-column prop="code" label="编码" width="160" />
          <el-table-column prop="name" label="名称" min-width="160" />
          <el-table-column prop="sortOrder" label="排序" width="80" />
          <el-table-column label="状态" width="90">
            <template #default="{ row }">
              <el-tag :type="row.status === 'active' ? 'success' : 'info'" size="small">
                {{ row.status === 'active' ? '启用' : '停用' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="创建时间" width="150">
            <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
          </el-table-column>
          <el-table-column v-if="canWrite" label="操作" width="90">
            <template #default="{ row }">
              <el-button size="small" link type="primary" @click="openCategoryDialog(row)">编辑</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <!-- ============================================================ -->
      <el-tab-pane label="AI 问答测试" name="ask">
        <div class="ask-panel">
          <div class="ask-form">
            <el-input
              v-model="askForm.question"
              type="textarea"
              :rows="3"
              maxlength="500"
              placeholder="输入患者常见问题，例如：陪诊服务怎么收费？"
            />
            <div class="ask-options">
              <el-select v-model="askForm.scene" size="small" style="width: 150px">
                <el-option label="在线客服问答" value="chat" />
                <el-option label="AI 随访话术" value="followup" />
              </el-select>
              <el-input-number v-model="askForm.topK" :min="1" :max="5" size="small" />
              <span class="hint">引用条数</span>
              <el-button size="small" type="primary" :loading="askLoading" @click="submitAsk">提问</el-button>
            </div>
          </div>

          <div v-if="askResult" class="ask-answer">
            <el-alert
              v-if="askResult.needsHandoff"
              class="handoff-alert"
              type="warning"
              :closable="false"
              show-icon
              :title="askResult.handoffMessage || '置信度较低，建议转人工确认'"
            />
            <div class="answer-meta">
              <el-tag size="small" :type="(askResult.confidence ?? 0) >= 0.35 ? 'success' : 'warning'">
                置信度 {{ askResult.confidence !== undefined ? (Number(askResult.confidence) * 100).toFixed(0) + '%' : '—' }}
              </el-tag>
              <span class="hint">仅检索「已发布」知识；下架或驳回的内容不会被引用</span>
            </div>
            <div class="answer-body">{{ askResult.answer }}</div>
            <div class="answer-refs">
              <div class="refs-title">引用来源（答案可追溯）</div>
              <el-table v-if="askResult.refs.length" :data="askResult.refs" size="small" border>
                <el-table-column prop="docNo" label="知识编号" width="110" />
                <el-table-column prop="title" label="标题" min-width="180" />
                <el-table-column label="版本 / 切片" width="110">
                  <template #default="{ row }">v{{ row.version }} · #{{ row.chunkIndex + 1 }}</template>
                </el-table-column>
                <el-table-column label="相关度" width="90">
                  <template #default="{ row }">{{ (Number(row.score) * 100).toFixed(0) }}%</template>
                </el-table-column>
              </el-table>
              <p v-else class="empty">知识库暂无相关知识，AI 已提示转人工</p>
            </div>
          </div>
          <p v-else class="empty ask-empty">提问后此处展示 AI 答案、置信度与引用来源</p>
        </div>
      </el-tab-pane>

      <!-- ============================================================ -->
      <el-tab-pane label="问答记录" name="logs">
        <div class="toolbar">
          <span class="hint">每次 AI 问答均留痕：问题、答案、置信度、引用来源与是否转人工</span>
          <span class="spacer" />
          <el-button size="small" @click="loadLogs">刷新</el-button>
        </div>
        <el-table v-loading="logsLoading" :data="logs" size="small" border>
          <el-table-column type="expand">
            <template #default="{ row }">
              <div class="log-detail">
                <div class="log-answer">{{ row.answer }}</div>
                <div v-if="row.refs && row.refs.length" class="hint">引用：{{ row.refs.join('、') }}</div>
                <div v-else class="hint">无引用来源</div>
              </div>
            </template>
          </el-table-column>
          <el-table-column prop="question" label="问题" min-width="220" show-overflow-tooltip />
          <el-table-column label="场景" width="100">
            <template #default="{ row }">{{ row.scene === 'followup' ? '随访话术' : '在线问答' }}</template>
          </el-table-column>
          <el-table-column label="置信度" width="90">
            <template #default="{ row }">
              {{ row.confidence !== undefined ? (Number(row.confidence) * 100).toFixed(0) + '%' : '—' }}
            </template>
          </el-table-column>
          <el-table-column label="引用" width="70">
            <template #default="{ row }">{{ row.refs ? row.refs.length : 0 }} 条</template>
          </el-table-column>
          <el-table-column label="转人工" width="80">
            <template #default="{ row }">
              <el-tag v-if="row.needsHandoff" type="warning" size="small">是</el-tag>
              <span v-else class="hint">否</span>
            </template>
          </el-table-column>
          <el-table-column prop="askedByName" label="提问人" width="110">
            <template #default="{ row }">{{ row.askedByName || '—' }}</template>
          </el-table-column>
          <el-table-column label="时间" width="150">
            <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
          </el-table-column>
        </el-table>
        <el-pagination
          v-if="logsTotal > logsQuery.pageSize"
          v-model:current-page="logsQuery.page"
          :page-size="logsQuery.pageSize"
          :total="logsTotal"
          layout="prev, pager, next"
          small
          @current-change="loadLogs"
        />
      </el-tab-pane>
    </el-tabs>

    <!-- 新建 / 编辑文档 -->
    <el-dialog
      v-model="editDialog.visible"
      :title="editDialog.mode === 'create' ? '新建知识文档' : editDialog.mode === 'meta' ? '编辑文档信息' : '编辑知识内容'"
      width="640px"
    >
      <el-form label-width="88px">
        <el-form-item v-if="editDialog.mode !== 'content'" label="标题" required>
          <el-input v-model="editDialog.title" maxlength="200" />
        </el-form-item>
        <el-form-item v-if="editDialog.mode !== 'content'" label="分类">
          <el-select v-model="editDialog.categoryId" clearable placeholder="未分类" style="width: 100%">
            <el-option
              v-for="item in categories.filter((c) => c.status === 'active')"
              :key="item.id"
              :label="item.name"
              :value="item.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item v-if="editDialog.mode !== 'content'" label="摘要">
          <el-input v-model="editDialog.summary" type="textarea" :rows="2" maxlength="500" show-word-limit />
        </el-form-item>
        <template v-if="editDialog.mode !== 'meta'">
          <el-form-item label="知识内容" required>
            <el-input
              v-model="editDialog.content"
              type="textarea"
              :rows="10"
              maxlength="200000"
              placeholder="纯文本知识内容，保存时自动按 ≤500 字合并切片；仅「已发布」版本参与 AI 检索"
            />
          </el-form-item>
          <el-form-item v-if="editDialog.mode === 'content'" label="变更说明">
            <el-input v-model="editDialog.changeNote" maxlength="500" placeholder="发布快照时记录（可选）" />
          </el-form-item>
        </template>
      </el-form>
      <template #footer>
        <el-button @click="editDialog.visible = false">取消</el-button>
        <el-button type="primary" @click="submitEdit">保存</el-button>
      </template>
    </el-dialog>

    <!-- 上传文件 -->
    <el-dialog v-model="uploadDialog.visible" title="上传知识文件" width="520px">
      <el-form label-width="88px">
        <el-form-item label="文件" required>
          <input ref="fileInput" type="file" accept=".txt,.md,.markdown" @change="onFileChange" />
          <div v-if="uploadDialog.fileName" class="hint">已选择：{{ uploadDialog.fileName }}</div>
        </el-form-item>
        <el-form-item label="标题">
          <el-input v-model="uploadDialog.title" maxlength="200" placeholder="留空则使用文件名" />
        </el-form-item>
        <el-form-item label="分类">
          <el-select v-model="uploadDialog.categoryId" clearable placeholder="未分类" style="width: 100%">
            <el-option
              v-for="item in categories.filter((c) => c.status === 'active')"
              :key="item.id"
              :label="item.name"
              :value="item.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="摘要">
          <el-input v-model="uploadDialog.summary" type="textarea" :rows="2" maxlength="500" />
        </el-form-item>
      </el-form>
      <p class="hint">支持 .txt / .md 文本（≤5MB），上传后自动解析切片为草稿，需送审发布后 AI 才会引用。</p>
      <template #footer>
        <el-button @click="uploadDialog.visible = false">取消</el-button>
        <el-button type="primary" @click="submitUpload">上传解析</el-button>
      </template>
    </el-dialog>

    <!-- 分类编辑 -->
    <el-dialog
      v-model="categoryDialog.visible"
      :title="categoryDialog.editingId ? '编辑分类' : '新建分类'"
      width="440px"
    >
      <el-form label-width="88px">
        <el-form-item label="编码" required>
          <el-input v-model="categoryDialog.code" :disabled="!!categoryDialog.editingId" maxlength="64" placeholder="如 service-price" />
        </el-form-item>
        <el-form-item label="名称" required>
          <el-input v-model="categoryDialog.name" maxlength="120" />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="categoryDialog.sortOrder" :min="1" :max="9999" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="categoryDialog.status" style="width: 100%">
            <el-option label="启用" value="active" />
            <el-option label="停用" value="disabled" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="categoryDialog.visible = false">取消</el-button>
        <el-button type="primary" @click="submitCategory">保存</el-button>
      </template>
    </el-dialog>

    <!-- 文档详情抽屉 -->
    <el-drawer v-model="detailVisible" :title="detail?.document.title || '文档详情'" size="620px">
      <div v-loading="detailLoading">
        <template v-if="detail">
          <div class="detail-meta">
            <div>
              <el-tag :type="statusTag(detail.document.status)" size="small">
                {{ statusLabels[detail.document.status] }}
              </el-tag>
              <span class="doc-no">{{ detail.document.docNo }} · v{{ detail.document.currentVersion }}</span>
            </div>
            <div class="hint">
              分类：{{ detail.document.categoryName || '未分类' }} ·
              来源：{{ detail.document.sourceType === 'file' ? detail.document.fileName : '手工录入' }} ·
              创建人：{{ detail.document.ownerName || '—' }}
            </div>
            <div class="hint">
              发布时间：{{ formatTime(detail.document.publishedAt) }}
              <template v-if="detail.document.status === 'OFFLINE'">
                · 下架时间：{{ formatTime(detail.document.offlineAt) }} · 原因：{{ detail.document.offlineReason }}
              </template>
            </div>
            <p v-if="detail.document.summary" class="detail-summary">{{ detail.document.summary }}</p>
          </div>

          <el-tabs v-model="detailTab">
            <el-tab-pane :label="`切片（${detail.chunks.length}）`" name="chunks">
              <div v-for="chunk in sortedChunks" :key="chunk.id" class="chunk-card">
                <div class="chunk-meta">
                  v{{ chunk.docVersion }} · 第 {{ chunk.chunkIndex + 1 }} 片 · {{ chunk.charCount }} 字
                  <el-tag
                    v-if="detail.document.status === 'PUBLISHED' && chunk.docVersion === detail.document.currentVersion"
                    size="small"
                    type="success"
                  >
                    检索中
                  </el-tag>
                  <el-tag v-else-if="chunk.docVersion === detail.document.currentVersion + 1" size="small" type="info">
                    草稿
                  </el-tag>
                </div>
                <div class="chunk-content">{{ chunk.content }}</div>
              </div>
              <p v-if="!detail.chunks.length" class="empty">暂无切片</p>
            </el-tab-pane>
            <el-tab-pane :label="`版本（${detail.versions.length}）`" name="versions">
              <el-table :data="detail.versions" size="small" border>
                <el-table-column label="版本" width="70">
                  <template #default="{ row }">v{{ row.versionNo }}</template>
                </el-table-column>
                <el-table-column prop="title" label="标题" min-width="150" show-overflow-tooltip />
                <el-table-column prop="chunkCount" label="切片" width="64" />
                <el-table-column prop="changeNote" label="变更说明" min-width="130" show-overflow-tooltip>
                  <template #default="{ row }">{{ row.changeNote || '—' }}</template>
                </el-table-column>
                <el-table-column label="发布人" width="100">
                  <template #default="{ row }">{{ row.createdByName || '—' }}</template>
                </el-table-column>
                <el-table-column label="时间" width="140">
                  <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
                </el-table-column>
              </el-table>
            </el-tab-pane>
            <el-tab-pane :label="`审核记录（${detail.reviews.length}）`" name="reviews">
              <el-timeline v-if="detail.reviews.length">
                <el-timeline-item
                  v-for="review in detail.reviews"
                  :key="review.id"
                  :timestamp="formatTime(review.createdAt)"
                  placement="top"
                >
                  <span class="event-title">{{ actionLabels[review.action] || review.action }} · v{{ review.versionNo }}</span>
                  <span class="hint"> {{ review.reviewerName || '系统' }}{{ review.comment ? `：${review.comment}` : '' }}</span>
                </el-timeline-item>
              </el-timeline>
              <p v-else class="empty">暂无审核记录</p>
            </el-tab-pane>
          </el-tabs>
        </template>
      </div>
    </el-drawer>
  </section>
</template>

<style scoped>
.kb-page {
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
  max-width: 820px;
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

.el-pagination {
  margin-top: 10px;
  justify-content: center;
}

/* ---------------- AI 问答测试 ---------------- */
.ask-panel {
  display: flex;
  flex-direction: column;
  gap: 14px;
  max-width: 860px;
}

.ask-options {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-top: 10px;
}

.ask-answer {
  border: 1px solid var(--el-border-color-light);
  border-radius: 8px;
  background: #fff;
  padding: 14px 16px;
}

.handoff-alert {
  margin-bottom: 10px;
}

.answer-meta {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 8px;
}

.answer-body {
  white-space: pre-wrap;
  word-break: break-word;
  font-size: 13px;
  line-height: 1.7;
  background: #f7f9fc;
  border-radius: 8px;
  padding: 10px 12px;
}

.answer-refs {
  margin-top: 12px;
}

.refs-title {
  font-size: 12px;
  font-weight: 600;
  margin-bottom: 6px;
}

.ask-empty {
  margin-top: 30px;
}

/* ---------------- 问答记录 ---------------- */
.log-detail {
  padding: 8px 16px;
}

.log-answer {
  white-space: pre-wrap;
  word-break: break-word;
  font-size: 12px;
  line-height: 1.6;
  margin-bottom: 6px;
}

/* ---------------- 详情抽屉 ---------------- */
.detail-meta {
  display: flex;
  flex-direction: column;
  gap: 6px;
  margin-bottom: 12px;
}

.doc-no {
  margin-left: 8px;
  font-size: 12px;
  font-weight: 600;
}

.detail-summary {
  margin: 0;
  font-size: 13px;
  color: var(--el-text-color-regular);
  background: #f7f9fc;
  border-radius: 6px;
  padding: 8px 10px;
}

.chunk-card {
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
  padding: 8px 10px;
  margin-bottom: 8px;
  background: #fbfcfe;
}

.chunk-meta {
  font-size: 11px;
  color: var(--el-text-color-secondary);
  margin-bottom: 4px;
  display: flex;
  align-items: center;
  gap: 6px;
}

.chunk-content {
  white-space: pre-wrap;
  word-break: break-word;
  font-size: 12px;
  line-height: 1.6;
}

.event-title {
  font-weight: 600;
  font-size: 13px;
}
</style>
