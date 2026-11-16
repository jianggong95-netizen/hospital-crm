<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { fetchAuditLogs } from '@/api/audit'
import type { AuditLogItem } from '@/types/system'

const loading = ref(false)
const rows = ref<AuditLogItem[]>([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(20)
const action = ref<string>()
const resourceType = ref<string>()
const range = ref<[Date, Date] | null>(null)

const detailVisible = ref(false)
const current = ref<AuditLogItem>()

const actionLabels: Record<string, string> = {
  AUTH_LOGIN_SUCCESS: '登录成功',
  AUTH_LOGIN_FAILED: '登录失败',
  AUTH_REFRESH: '刷新令牌',
  AUTH_REFRESH_REUSE: '刷新令牌复用',
  AUTH_REFRESH_FAILED: '刷新失败',
  AUTH_LOGOUT: '退出登录',
  USER_CREATE: '创建用户',
  USER_UPDATE: '更新用户',
  USER_STATUS_CHANGED: '用户状态变更',
  USER_PASSWORD_RESET: '重置密码',
  ROLE_CREATE: '创建角色',
  ROLE_UPDATE: '更新角色',
  ORG_CREATE: '创建组织',
  ORG_UPDATE: '更新组织',
  BOOTSTRAP_ADMIN_CREATED: '初始化管理员'
}

const actionOptions = Object.entries(actionLabels)
  .filter(([value]) => value !== 'BOOTSTRAP_ADMIN_CREATED')
  .map(([value, label]) => ({ value, label }))

const detailJson = computed(() => {
  if (!current.value?.detail) return '{}'
  try {
    return JSON.stringify(JSON.parse(current.value.detail), null, 2)
  } catch {
    return current.value.detail
  }
})

async function load() {
  loading.value = true
  try {
    const result = await fetchAuditLogs({
      page: page.value,
      pageSize: pageSize.value,
      action: action.value || undefined,
      resourceType: resourceType.value || undefined,
      from: range.value?.[0] ? range.value[0].toISOString() : undefined,
      to: range.value?.[1] ? range.value[1].toISOString() : undefined
    })
    rows.value = result.items
    total.value = result.total
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '加载审计日志失败')
  } finally {
    loading.value = false
  }
}

function search() {
  page.value = 1
  load()
}

function resetFilters() {
  action.value = undefined
  resourceType.value = undefined
  range.value = null
  search()
}

function showDetail(row: AuditLogItem) {
  current.value = row
  detailVisible.value = true
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
        <h3 class="section-title">审计日志</h3>
        <p class="section-subtitle">记录登录、刷新令牌、用户、角色与组织的敏感操作，可按数据范围查看</p>
      </div>
      <el-button :loading="loading" @click="load">刷新</el-button>
    </div>

    <div class="toolbar">
      <el-select v-model="action" placeholder="全部动作" clearable @change="search">
        <el-option v-for="option in actionOptions" :key="option.value" :label="option.label" :value="option.value" />
      </el-select>
      <el-select v-model="resourceType" placeholder="全部资源" clearable @change="search">
        <el-option label="认证" value="AUTH" />
        <el-option label="用户" value="USER" />
        <el-option label="角色" value="ROLE" />
        <el-option label="组织" value="ORG" />
      </el-select>
      <el-date-picker
        v-model="range"
        type="datetimerange"
        start-placeholder="开始时间"
        end-placeholder="结束时间"
        range-separator="至"
        @change="search"
      />
      <el-button type="primary" plain @click="search">查询</el-button>
      <el-button @click="resetFilters">重置</el-button>
    </div>

    <el-table v-loading="loading" :data="rows" row-key="id" stripe>
      <el-table-column label="时间" width="170">
        <template #default="{ row }">
          <span class="cell-muted">{{ formatTime(row.createdAt) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="动作" min-width="150">
        <template #default="{ row }">
          <el-tag size="small" :type="row.action.endsWith('FAILED') || row.action.includes('REUSE') ? 'danger' : 'primary'" effect="plain">
            {{ actionLabels[row.action] || row.action }}
          </el-tag>
          <span class="cell-mono action-code">{{ row.action }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="resourceType" label="资源" width="100" />
      <el-table-column label="资源 ID" min-width="180">
        <template #default="{ row }">
          <span class="cell-mono">{{ row.resourceId || '—' }}</span>
        </template>
      </el-table-column>
      <el-table-column label="操作人" width="130">
        <template #default="{ row }">{{ row.actorName || '匿名/系统' }}</template>
      </el-table-column>
      <el-table-column label="来源 IP" width="140">
        <template #default="{ row }">
          <span class="cell-mono">{{ row.ipAddress || '—' }}</span>
        </template>
      </el-table-column>
      <el-table-column label="traceId" min-width="180">
        <template #default="{ row }">
          <span class="cell-mono">{{ row.traceId || '—' }}</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="90" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="showDetail(row)">详情</el-button>
        </template>
      </el-table-column>
    </el-table>

    <div class="table-pagination">
      <el-pagination
        v-model:current-page="page"
        v-model:page-size="pageSize"
        :total="total"
        :page-sizes="[20, 50, 100]"
        layout="total, sizes, prev, pager, next"
        @current-change="load"
        @size-change="search"
      />
    </div>

    <el-drawer v-model="detailVisible" title="审计详情" size="460px">
      <template v-if="current">
        <dl class="detail-list">
          <div><dt>动作</dt><dd>{{ actionLabels[current.action] || current.action }}（{{ current.action }}）</dd></div>
          <div><dt>资源</dt><dd>{{ current.resourceType }} / {{ current.resourceId || '—' }}</dd></div>
          <div><dt>操作人</dt><dd>{{ current.actorName || '匿名/系统' }}（{{ current.actorUserId || '—' }}）</dd></div>
          <div><dt>来源 IP</dt><dd>{{ current.ipAddress || '—' }}</dd></div>
          <div><dt>traceId</dt><dd class="cell-mono">{{ current.traceId || '—' }}</dd></div>
          <div><dt>发生时间</dt><dd>{{ formatTime(current.createdAt) }}</dd></div>
        </dl>
        <h4 class="detail-title">明细</h4>
        <pre class="code-block">{{ detailJson }}</pre>
      </template>
    </el-drawer>
  </section>
</template>

<style scoped lang="scss">
.action-code {
  display: block;
  margin-top: 2px;
  font-size: 11px;
}

.detail-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin: 0;
}

.detail-list div {
  display: flex;
  gap: 12px;
  padding: 10px 12px;
  border-radius: 10px;
  background: #f8fafc;
  border: 1px solid var(--crm-border);
}

.detail-list dt {
  flex-shrink: 0;
  width: 78px;
  color: var(--crm-muted);
  font-size: 12px;
}

.detail-list dd {
  margin: 0;
  font-size: 13px;
  word-break: break-all;
}

.detail-title {
  margin: 20px 0 10px;
  font-size: 14px;
}

.toolbar :deep(.el-date-editor) {
  width: 330px;
}
</style>
