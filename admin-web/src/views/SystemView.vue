<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { fetchHealth } from '@/api/health'
import { fetchSystemInfo } from '@/api/system'
import type { HealthStatus, SystemInfo } from '@/types/api'

const loading = ref(false)
const health = ref<HealthStatus | null>(null)
const info = ref<SystemInfo | null>(null)
const errorMessage = ref('')

const healthJson = computed(() => (health.value ? JSON.stringify(health.value, null, 2) : '—'))
const profiles = computed(() => info.value?.profiles?.join(', ') || '—')

const endpoints = [
  { method: 'GET', path: '/actuator/health', desc: '综合健康状态' },
  { method: 'GET', path: '/actuator/health/readiness', desc: '就绪探针（含数据库）' },
  { method: 'GET', path: '/actuator/health/liveness', desc: '存活探针' },
  { method: 'GET', path: '/api/v1/system/info', desc: '运行信息与版本' },
  { method: 'GET', path: '/v3/api-docs', desc: 'OpenAPI 文档（开发环境）' },
  { method: 'GET', path: '/swagger-ui.html', desc: 'Swagger UI（开发环境）' }
]

async function load() {
  loading.value = true
  errorMessage.value = ''
  const [healthResult, infoResult] = await Promise.allSettled([fetchHealth(), fetchSystemInfo()])
  health.value = healthResult.status === 'fulfilled' ? healthResult.value : null
  info.value = infoResult.status === 'fulfilled' ? infoResult.value : null
  if (healthResult.status === 'rejected') {
    errorMessage.value = '健康检查不可用，请先启动后端服务。'
  }
  loading.value = false
}

onMounted(load)
</script>

<template>
  <div class="system">
    <section class="card panel">
      <div class="section-head">
        <div>
          <h3 class="section-title">运行信息</h3>
          <p class="section-subtitle">数据来自 GET /api/v1/system/info</p>
        </div>
        <el-button :loading="loading" @click="load">刷新</el-button>
      </div>

      <div v-if="errorMessage" class="inline-alert">{{ errorMessage }}</div>

      <dl class="info-grid">
        <div>
          <dt>应用名称</dt>
          <dd>{{ info?.application ?? '—' }}</dd>
        </div>
        <div>
          <dt>应用版本</dt>
          <dd>{{ info?.version ?? '—' }}</dd>
        </div>
        <div>
          <dt>激活配置</dt>
          <dd>{{ profiles }}</dd>
        </div>
        <div>
          <dt>Java 版本</dt>
          <dd>{{ info?.javaVersion ?? '—' }}</dd>
        </div>
        <div>
          <dt>操作系统</dt>
          <dd>{{ info?.os ?? '—' }}</dd>
        </div>
        <div>
          <dt>服务时区</dt>
          <dd>{{ info?.timezone ?? '—' }}</dd>
        </div>
        <div>
          <dt>服务器时间</dt>
          <dd>{{ info?.serverTime ? new Date(info.serverTime).toLocaleString('zh-CN', { hour12: false }) : '—' }}</dd>
        </div>
        <div>
          <dt>健康状态</dt>
          <dd>
            <span class="badge" :class="health?.status === 'UP' ? 'badge-success' : 'badge-danger'">
              {{ health?.status ?? '未知' }}
            </span>
          </dd>
        </div>
      </dl>
    </section>

    <section class="card panel">
      <div class="section-head">
        <div>
          <h3 class="section-title">健康检查明细</h3>
          <p class="section-subtitle">生产环境建议将 show-details 调整为 never</p>
        </div>
      </div>
      <pre class="code-block health-json">{{ healthJson }}</pre>
    </section>

    <section class="card panel">
      <div class="section-head">
        <div>
          <h3 class="section-title">可用端点</h3>
          <p class="section-subtitle">本地开发默认地址 http://localhost:8080</p>
        </div>
      </div>
      <ul class="endpoint-list">
        <li v-for="endpoint in endpoints" :key="endpoint.path">
          <span class="method">{{ endpoint.method }}</span>
          <code>{{ endpoint.path }}</code>
          <span class="desc">{{ endpoint.desc }}</span>
        </li>
      </ul>
    </section>

    <section class="card panel">
      <div class="section-head">
        <div>
          <h3 class="section-title">本地启动命令</h3>
          <p class="section-subtitle">Windows PowerShell 示例</p>
        </div>
      </div>
      <pre class="code-block"># 1. 启动基础依赖
docker compose up -d postgres redis rabbitmq minio

# 2. 启动后端
cd backend
.\mvnw.cmd spring-boot:run

# 3. 启动管理端
cd ..\admin-web
npm install
npm run dev

# 4. 启动患者端 H5
cd ..\patient-mobile
npm install
npm run dev:h5</pre>
    </section>
  </div>
</template>

<style scoped lang="scss">
.system {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.panel {
  padding: 24px 26px;
}

.inline-alert {
  margin-bottom: 16px;
  padding: 10px 14px;
  border-radius: 10px;
  background: var(--crm-danger-soft);
  color: var(--crm-danger);
  font-size: 13px;
}

.info-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 14px;
  margin: 0;
}

.info-grid div {
  padding: 14px 16px;
  border-radius: var(--crm-radius-md);
  background: #f8fafc;
  border: 1px solid var(--crm-border);
}

.info-grid dt {
  color: var(--crm-muted);
  font-size: 12px;
}

.info-grid dd {
  margin: 4px 0 0;
  font-size: 14px;
  font-weight: 600;
  word-break: break-all;
}

.health-json {
  max-height: 360px;
  overflow: auto;
}

.endpoint-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin: 0;
  padding: 0;
  list-style: none;
}

.endpoint-list li {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 14px;
  border-radius: 10px;
  background: #f8fafc;
  border: 1px solid var(--crm-border);
  font-size: 13px;
}

.method {
  flex-shrink: 0;
  padding: 2px 8px;
  border-radius: 6px;
  background: var(--crm-primary-soft);
  color: var(--crm-primary);
  font-size: 11px;
  font-weight: 700;
}

.endpoint-list code {
  font-family: 'Cascadia Code', Consolas, monospace;
  color: var(--crm-ink);
}

.desc {
  margin-left: auto;
  color: var(--crm-muted);
  font-size: 12px;
}

@media (max-width: 1080px) {
  .info-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 640px) {
  .info-grid {
    grid-template-columns: 1fr;
  }

  .endpoint-list li {
    flex-wrap: wrap;
  }

  .desc {
    width: 100%;
    margin-left: 0;
  }
}
</style>
