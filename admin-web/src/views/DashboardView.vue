<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { fetchHealth } from '@/api/health'
import { fetchSystemInfo } from '@/api/system'
import type { HealthStatus, SystemInfo } from '@/types/api'
import { phaseStatusLabels, phases } from '@/data/roadmap'

type Tone = 'success' | 'warning' | 'danger' | 'neutral'

const router = useRouter()
const loading = ref(false)
const health = ref<HealthStatus | null>(null)
const info = ref<SystemInfo | null>(null)
const errorMessage = ref('')

const healthState = computed<{ label: string; tone: Tone }>(() => {
  if (loading.value && !health.value) {
    return { label: '检测中', tone: 'neutral' }
  }
  if (!health.value) {
    return { label: '不可用', tone: 'danger' }
  }
  return health.value.status === 'UP'
    ? { label: '运行正常', tone: 'success' }
    : { label: health.value.status, tone: 'warning' }
})

const databaseState = computed<{ label: string; tone: Tone }>(() => {
  const status = health.value?.components?.db?.status
  if (!status) {
    return { label: health.value ? '未暴露明细' : '未知', tone: 'neutral' }
  }
  return status === 'UP'
    ? { label: '已连接', tone: 'success' }
    : { label: status, tone: 'danger' }
})

const profileText = computed(() => info.value?.profiles?.join(' / ') || 'dev')
const serverTimeText = computed(() => {
  if (!info.value?.serverTime) return '—'
  return new Date(info.value.serverTime).toLocaleString('zh-CN', { hour12: false })
})

const metrics = [
  { label: '客户总数', value: '—', hint: 'CRM-003 接入' },
  { label: '今日预约', value: '—', hint: 'CRM-008 接入' },
  { label: '待办随访', value: '—', hint: 'CRM-013 接入' },
  { label: '今日收入', value: '—', hint: 'CRM-011 接入' }
]

const scopeItems = [
  '统一响应结构与 traceId 链路追踪已启用',
  'Flyway V1 核心数据库基线已纳入版本管理',
  'Actuator 健康检查、就绪探针与 Prometheus 指标已开放',
  '认证、RBAC、数据范围与客户业务接口将在 CRM-002 / CRM-003 接入'
]

async function loadStatus() {
  loading.value = true
  errorMessage.value = ''
  const [healthResult, infoResult] = await Promise.allSettled([fetchHealth(), fetchSystemInfo()])

  health.value = healthResult.status === 'fulfilled' ? healthResult.value : null
  info.value = infoResult.status === 'fulfilled' ? infoResult.value : null

  if (healthResult.status === 'rejected' && infoResult.status === 'rejected') {
    errorMessage.value = '无法连接后端服务，请确认 crm-api 已启动且 PostgreSQL 可用。'
  }
  loading.value = false
}

onMounted(loadStatus)
</script>

<template>
  <div class="dashboard">
    <section class="hero card">
      <div class="hero-main">
        <span class="hero-kicker">CRM-001 · 项目初始化</span>
        <h2>医院客户服务与运营平台</h2>
        <p class="hero-desc">
          模块化单体骨架已就绪：Spring Boot 3 + PostgreSQL + Vue 3 + uni-app。
          统一响应、链路追踪、数据库迁移、健康检查与 CI 基线已建立。
        </p>
        <div class="hero-tags">
          <span class="badge badge-primary">Java 21</span>
          <span class="badge badge-primary">Spring Boot 3.5</span>
          <span class="badge badge-primary">Vue 3 + TS</span>
          <span class="badge badge-primary">uni-app</span>
        </div>
        <div class="hero-actions">
          <el-button type="primary" @click="router.push('/roadmap')">查看交付路线</el-button>
          <el-button :loading="loading" @click="loadStatus">刷新运行状态</el-button>
        </div>
      </div>
      <div class="hero-side">
        <div class="hero-stat">
          <span>当前阶段</span>
          <strong>P0 项目底座</strong>
        </div>
        <div class="hero-stat">
          <span>后端健康</span>
          <strong :class="`text-${healthState.tone}`">{{ healthState.label }}</strong>
        </div>
      </div>
    </section>

    <div v-if="errorMessage" class="alert card">
      <div>
        <strong>后端服务未连接</strong>
        <p>{{ errorMessage }}</p>
      </div>
      <pre class="code-block">docker compose up -d postgres
cd backend
./mvnw spring-boot:run</pre>
    </div>

    <section class="status-grid">
      <article class="card status-card">
        <header>
          <span>后端服务</span>
          <span class="badge" :class="`badge-${healthState.tone}`">{{ healthState.label }}</span>
        </header>
        <strong class="status-value">{{ health?.status ?? '—' }}</strong>
        <p class="status-foot">GET /actuator/health</p>
      </article>

      <article class="card status-card">
        <header>
          <span>PostgreSQL</span>
          <span class="badge" :class="`badge-${databaseState.tone}`">{{ databaseState.label }}</span>
        </header>
        <strong class="status-value">Flyway V1</strong>
        <p class="status-foot">核心表基线迁移</p>
      </article>

      <article class="card status-card">
        <header>
          <span>运行环境</span>
          <span class="badge badge-neutral">{{ profileText }}</span>
        </header>
        <strong class="status-value">{{ info?.javaVersion ?? '—' }}</strong>
        <p class="status-foot">{{ serverTimeText }}</p>
      </article>
    </section>

    <section>
      <div class="section-head">
        <div>
          <h3 class="section-title">运营指标</h3>
          <p class="section-subtitle">指标口径与数据权限将在 CRM-019 统一落地</p>
        </div>
        <span class="badge badge-warning">待接入</span>
      </div>
      <div class="metric-grid">
        <article v-for="metric in metrics" :key="metric.label" class="card metric-card">
          <span>{{ metric.label }}</span>
          <strong>{{ metric.value }}</strong>
          <small>{{ metric.hint }}</small>
        </article>
      </div>
    </section>

    <section class="lower-grid">
      <article class="card panel">
        <div class="section-head">
          <div>
            <h3 class="section-title">交付路线</h3>
            <p class="section-subtitle">按任务编号逐项交付，不一次性生成全部模块</p>
          </div>
          <el-button text type="primary" @click="router.push('/roadmap')">全部阶段</el-button>
        </div>
        <ul class="phase-list">
          <li v-for="phase in phases.slice(0, 4)" :key="phase.key">
            <span class="phase-key">{{ phase.key }}</span>
            <div class="phase-body">
              <strong>{{ phase.name }}</strong>
              <p>{{ phase.focus }}</p>
            </div>
            <span class="badge" :class="phase.status === 'active' ? 'badge-success' : 'badge-neutral'">
              {{ phaseStatusLabels[phase.status] }}
            </span>
          </li>
        </ul>
      </article>

      <article class="card panel">
        <div class="section-head">
          <div>
            <h3 class="section-title">骨架能力清单</h3>
            <p class="section-subtitle">CRM-001 验收范围</p>
          </div>
        </div>
        <ul class="check-list">
          <li v-for="item in scopeItems" :key="item">
            <span class="check-dot"></span>
            <p>{{ item }}</p>
          </li>
        </ul>
        <div class="notice">
          <strong>安全边界</strong>
          <p>当前未接入认证与授权，仅允许本地开发联调；生产部署前必须完成 CRM-002 并启用 HTTPS。</p>
        </div>
      </article>
    </section>
  </div>
</template>

<style scoped lang="scss">
.dashboard {
  display: flex;
  flex-direction: column;
  gap: 24px;
}

.hero {
  display: flex;
  gap: 36px;
  justify-content: space-between;
  padding: 32px;
  background:
    radial-gradient(120% 180% at 100% 0%, rgba(76, 143, 216, 0.16) 0%, rgba(76, 143, 216, 0) 55%),
    linear-gradient(135deg, #ffffff 0%, #f6faff 100%);
}

.hero-main {
  max-width: 680px;
}

.hero-kicker {
  display: inline-block;
  padding: 4px 10px;
  border-radius: 999px;
  background: var(--crm-primary-soft);
  color: var(--crm-primary);
  font-size: 12px;
  font-weight: 700;
}

.hero h2 {
  margin-top: 12px;
  font-size: 28px;
  letter-spacing: 0.01em;
}

.hero-desc {
  margin-top: 10px;
  color: var(--crm-ink-soft);
  font-size: 14px;
  line-height: 1.8;
}

.hero-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 18px;
}

.hero-actions {
  display: flex;
  gap: 12px;
  margin-top: 24px;
}

.hero-side {
  display: flex;
  flex-direction: column;
  gap: 12px;
  min-width: 196px;
}

.hero-stat {
  padding: 14px 16px;
  border-radius: var(--crm-radius-md);
  background: rgba(255, 255, 255, 0.82);
  border: 1px solid var(--crm-border);
}

.hero-stat span {
  color: var(--crm-muted);
  font-size: 12px;
}

.hero-stat strong {
  display: block;
  margin-top: 4px;
  font-size: 18px;
}

.text-success {
  color: var(--crm-success);
}

.text-warning {
  color: var(--crm-warning);
}

.text-danger {
  color: var(--crm-danger);
}

.text-neutral {
  color: var(--crm-muted);
}

.alert {
  display: flex;
  align-items: center;
  gap: 24px;
  justify-content: space-between;
  padding: 20px 24px;
  border-color: #f0d5d3;
  background: #fff9f8;
}

.alert strong {
  color: var(--crm-danger);
}

.alert p {
  margin-top: 4px;
  color: var(--crm-ink-soft);
  font-size: 13px;
}

.alert .code-block {
  min-width: 340px;
}

.status-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 16px;
}

.status-card {
  padding: 20px 22px;
}

.status-card header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  color: var(--crm-muted);
  font-size: 13px;
}

.status-value {
  display: block;
  margin-top: 14px;
  font-size: 22px;
  letter-spacing: 0.01em;
}

.status-foot {
  margin-top: 6px;
  color: var(--crm-muted);
  font-size: 12px;
}

.metric-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 16px;
}

.metric-card {
  padding: 18px 20px;
}

.metric-card span {
  color: var(--crm-muted);
  font-size: 13px;
}

.metric-card strong {
  display: block;
  margin: 10px 0 4px;
  font-size: 28px;
  letter-spacing: 0.02em;
}

.metric-card small {
  color: var(--crm-muted);
  font-size: 12px;
}

.lower-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.25fr) minmax(0, 1fr);
  gap: 16px;
}

.panel {
  padding: 24px;
}

.phase-list {
  display: flex;
  flex-direction: column;
  gap: 4px;
  margin: 0;
  padding: 0;
  list-style: none;
}

.phase-list li {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 12px 10px;
  border-radius: 12px;
  transition: background 0.2s ease;
}

.phase-list li:hover {
  background: #f7fafd;
}

.phase-key {
  display: grid;
  place-items: center;
  width: 42px;
  height: 42px;
  flex-shrink: 0;
  border-radius: 11px;
  background: var(--crm-primary-soft);
  color: var(--crm-primary);
  font-weight: 700;
  font-size: 13px;
}

.phase-body {
  flex: 1;
  min-width: 0;
}

.phase-body strong {
  font-size: 14px;
}

.phase-body p {
  color: var(--crm-muted);
  font-size: 12.5px;
}

.check-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin: 0;
  padding: 0;
  list-style: none;
}

.check-list li {
  display: flex;
  align-items: flex-start;
  gap: 10px;
}

.check-list p {
  color: var(--crm-ink-soft);
  font-size: 13px;
}

.check-dot {
  width: 8px;
  height: 8px;
  flex-shrink: 0;
  margin-top: 7px;
  border-radius: 50%;
  background: var(--crm-success);
  box-shadow: 0 0 0 4px var(--crm-success-soft);
}

.notice {
  margin-top: 20px;
  padding: 14px 16px;
  border-radius: var(--crm-radius-md);
  background: var(--crm-warning-soft);
  border: 1px solid #f3e3c4;
}

.notice strong {
  color: var(--crm-warning);
  font-size: 13px;
}

.notice p {
  margin-top: 4px;
  color: #7a5a1f;
  font-size: 12.5px;
  line-height: 1.7;
}

@media (max-width: 1180px) {
  .metric-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .lower-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 860px) {
  .hero {
    flex-direction: column;
  }

  .hero-side {
    flex-direction: row;
    min-width: 0;
  }

  .status-grid {
    grid-template-columns: 1fr;
  }

  .alert {
    flex-direction: column;
    align-items: stretch;
  }

  .alert .code-block {
    min-width: 0;
  }
}
</style>
