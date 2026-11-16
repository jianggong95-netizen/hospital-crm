<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { navItems } from '@/router/nav'
import { useAuthStore } from '@/stores/auth'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

const pageTitle = computed(() => (route.meta.title as string) || '工作台')
const menuItems = computed(() => navItems.filter((item) => auth.has(item.permission)))
const user = computed(() => auth.user)
const roleText = computed(() => user.value?.roles.join(' · ') || '未分配角色')

async function handleLogout() {
  await auth.logout()
  await router.replace({ name: 'login' })
}
</script>

<template>
  <div class="app-shell">
    <aside class="sidebar">
      <RouterLink to="/" class="brand">
        <span class="brand-mark">
          <svg viewBox="0 0 64 64" aria-hidden="true">
            <path d="M32 16v32M16 32h32" stroke="currentColor" stroke-width="8" stroke-linecap="round" />
          </svg>
        </span>
        <span class="brand-text">
          <strong>Hospital CRM</strong>
          <small>医院客户运营平台</small>
        </span>
      </RouterLink>

      <nav class="nav">
        <RouterLink
          v-for="item in menuItems"
          :key="item.path"
          :to="item.path"
          class="nav-item"
          :class="{ active: route.path === item.path }"
        >
          <span class="nav-icon" :style="{ background: item.tint }">{{ item.short }}</span>
          <span class="nav-label">{{ item.label }}</span>
        </RouterLink>
      </nav>

      <div class="sidebar-foot">
        <span class="pulse"></span>
        <div>
          <strong>CRM-002</strong>
          <span>认证与权限已启用</span>
        </div>
      </div>
    </aside>

    <div class="main">
      <header class="topbar">
        <div>
          <p class="eyebrow">医院内网 · 私有化部署</p>
          <h1>{{ pageTitle }}</h1>
        </div>
        <div class="topbar-meta">
          <span class="badge badge-neutral">{{ user?.orgName || '—' }}</span>
          <span class="badge badge-primary">V1.0.0</span>
          <el-dropdown trigger="click" @command="handleLogout">
            <button type="button" class="user-chip">
              <span class="user-avatar">{{ (user?.displayName || 'U').slice(0, 1) }}</span>
              <span class="user-meta">
                <strong>{{ user?.displayName || '未登录' }}</strong>
                <small>{{ roleText }}</small>
              </span>
            </button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="logout">退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </header>

      <main class="content">
        <RouterView />
      </main>

      <footer class="footer">
        Hospital CRM V1.0 · 所有敏感操作均记录审计日志，禁止在非生产授权环境使用真实患者信息
      </footer>
    </div>
  </div>
</template>

<style scoped lang="scss">
.app-shell {
  display: flex;
  min-height: 100vh;
}

.sidebar {
  display: flex;
  flex-direction: column;
  width: 252px;
  flex-shrink: 0;
  padding: 26px 18px;
  background: linear-gradient(180deg, #1c4a7e 0%, #163a63 100%);
  color: #eaf2fb;
}

.brand {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 4px 8px 24px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.12);
}

.brand-mark {
  display: grid;
  place-items: center;
  width: 42px;
  height: 42px;
  border-radius: 13px;
  background: linear-gradient(135deg, #5fa2e6, #2d6dad);
  box-shadow: 0 8px 18px rgba(8, 24, 45, 0.35);
  color: #fff;
}

.brand-mark svg {
  width: 26px;
  height: 26px;
}

.brand-text {
  display: flex;
  flex-direction: column;
  line-height: 1.35;
}

.brand-text strong {
  font-size: 16px;
  letter-spacing: 0.02em;
}

.brand-text small {
  color: rgba(234, 242, 251, 0.66);
  font-size: 12px;
}

.nav {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-top: 24px;
}

.nav-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 12px;
  border-radius: 12px;
  color: rgba(234, 242, 251, 0.78);
  transition: background 0.2s ease, color 0.2s ease, transform 0.2s ease;
}

.nav-item:hover {
  background: rgba(255, 255, 255, 0.08);
  color: #fff;
  transform: translateX(2px);
}

.nav-item.active {
  background: rgba(255, 255, 255, 0.14);
  color: #fff;
  box-shadow: inset 0 0 0 1px rgba(255, 255, 255, 0.12);
}

.nav-icon {
  display: grid;
  place-items: center;
  width: 30px;
  height: 30px;
  border-radius: 9px;
  color: #fff;
  font-size: 13px;
  font-weight: 700;
}

.nav-label {
  font-size: 14px;
  font-weight: 600;
}

.sidebar-foot {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-top: auto;
  padding: 14px 12px;
  border-radius: 12px;
  background: rgba(8, 24, 45, 0.28);
}

.sidebar-foot strong,
.sidebar-foot span {
  display: block;
}

.sidebar-foot strong {
  font-size: 13px;
}

.sidebar-foot span {
  color: rgba(234, 242, 251, 0.6);
  font-size: 12px;
}

.pulse {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #8fd3b5;
  box-shadow: 0 0 0 5px rgba(143, 211, 181, 0.18);
}

.main {
  display: flex;
  flex-direction: column;
  flex: 1;
  min-width: 0;
}

.topbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  padding: 26px 36px 18px;
}

.eyebrow {
  color: var(--crm-muted);
  font-size: 12px;
  letter-spacing: 0.08em;
}

.topbar h1 {
  margin-top: 4px;
  font-size: 24px;
  font-weight: 700;
  letter-spacing: 0.01em;
}

.topbar-meta {
  display: flex;
  align-items: center;
  gap: 8px;
}

.user-chip {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 6px 12px 6px 6px;
  border: 1px solid var(--crm-border);
  border-radius: 999px;
  background: #fff;
  cursor: pointer;
  transition: border-color 0.2s ease, box-shadow 0.2s ease;
}

.user-chip:hover {
  border-color: var(--crm-primary-light, #b0c8e4);
  box-shadow: 0 6px 16px rgba(31, 79, 134, 0.1);
}

.user-avatar {
  display: grid;
  place-items: center;
  width: 32px;
  height: 32px;
  border-radius: 50%;
  background: linear-gradient(135deg, #5fa2e6, #2d6dad);
  color: #fff;
  font-size: 14px;
  font-weight: 700;
}

.user-meta {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  line-height: 1.3;
}

.user-meta strong {
  font-size: 13px;
}

.user-meta small {
  color: var(--crm-muted);
  font-size: 11px;
}

.content {
  flex: 1;
  padding: 8px 36px 28px;
}

.footer {
  padding: 0 36px 24px;
  color: var(--crm-muted);
  font-size: 12px;
}

@media (max-width: 900px) {
  .app-shell {
    flex-direction: column;
  }

  .sidebar {
    width: 100%;
    padding: 16px 18px;
  }

  .brand {
    padding-bottom: 14px;
  }

  .nav {
    flex-direction: row;
    flex-wrap: wrap;
    margin-top: 14px;
  }

  .nav-item {
    flex: 1;
    min-width: 120px;
  }

  .sidebar-foot {
    display: none;
  }

  .topbar {
    padding: 20px 20px 12px;
  }

  .content {
    padding: 4px 20px 24px;
  }

  .footer {
    padding: 0 20px 20px;
  }
}
</style>
