<script setup lang="ts">
import { computed, reactive, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { navTopItems, navGroups } from '@/router/nav'
import { useAuthStore } from '@/stores/auth'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

const pageTitle = computed(() => (route.meta.title as string) || '工作台')
const visibleTopItems = computed(() => navTopItems.filter((item) => auth.has(item.permission)))
const visibleGroups = computed(() =>
  navGroups
    .map((group) => ({ ...group, items: group.items.filter((item) => auth.has(item.permission)) }))
    .filter((group) => group.items.length > 0)
)
const user = computed(() => auth.user)
const roleText = computed(() => user.value?.roles.join(' · ') || '未分配角色')

const expanded = reactive<Record<string, boolean>>({})

function toggleGroup(key: string) {
  expanded[key] = !expanded[key]
}

function groupHasActive(group: { items: { path: string }[] }) {
  return group.items.some((item) => route.path === item.path)
}

// 进入某页面时自动展开其所属分组
watch(
  () => route.path,
  (path) => {
    const active = navGroups.find((g) => g.items.some((i) => i.path === path))
    if (active) expanded[active.key] = true
  },
  { immediate: true }
)

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
          v-for="item in visibleTopItems"
          :key="item.path"
          :to="item.path"
          class="nav-item"
          :class="{ active: route.path === item.path }"
        >
          <span class="nav-icon" :style="{ background: item.tint }">{{ item.short }}</span>
          <span class="nav-label">{{ item.label }}</span>
        </RouterLink>

        <div v-for="group in visibleGroups" :key="group.key" class="nav-group">
          <button
            type="button"
            class="group-header"
            :class="{ 'has-active': groupHasActive(group) }"
            @click="toggleGroup(group.key)"
          >
            <span class="group-label">{{ group.label }}</span>
            <span class="group-count">{{ group.items.length }}</span>
            <span class="group-arrow" :class="{ open: expanded[group.key] }">
              <svg viewBox="0 0 16 16" aria-hidden="true">
                <path d="M5 3l6 5-6 5" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" />
              </svg>
            </span>
          </button>

          <el-collapse-transition>
            <div v-show="expanded[group.key]" class="group-items">
              <RouterLink
                v-for="item in group.items"
                :key="item.path"
                :to="item.path"
                class="nav-item"
                :class="{ active: route.path === item.path }"
              >
                <span class="nav-icon" :style="{ background: item.tint }">{{ item.short }}</span>
                <span class="nav-label">{{ item.label }}</span>
              </RouterLink>
            </div>
          </el-collapse-transition>
        </div>
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
  height: 100vh;
  overflow: hidden;
}

.sidebar {
  display: flex;
  flex-direction: column;
  width: 252px;
  height: 100vh;
  flex-shrink: 0;
  padding: 26px 18px;
  overflow-y: auto;
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
  gap: 14px;
  margin-top: 24px;
}

.nav-group {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.group-header {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 10px;
  border: none;
  border-radius: 10px;
  background: transparent;
  color: rgba(234, 242, 251, 0.55);
  cursor: pointer;
  transition: background 0.2s ease, color 0.2s ease;
}

.group-header:hover {
  background: rgba(255, 255, 255, 0.06);
  color: rgba(234, 242, 251, 0.85);
}

.group-header.has-active {
  color: #fff;
}

.group-label {
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.08em;
}

.group-count {
  margin-left: auto;
  min-width: 18px;
  padding: 1px 6px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.1);
  color: rgba(234, 242, 251, 0.7);
  font-size: 11px;
  text-align: center;
}

.group-header.has-active .group-count {
  background: rgba(95, 162, 230, 0.35);
  color: #fff;
}

.group-arrow {
  display: grid;
  place-items: center;
  width: 16px;
  height: 16px;
  transition: transform 0.2s ease;
}

.group-arrow svg {
  width: 12px;
  height: 12px;
}

.group-arrow.open {
  transform: rotate(90deg);
}

.group-items {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding-left: 10px;
  border-left: 1px solid rgba(255, 255, 255, 0.1);
  margin-left: 10px;
  overflow: hidden;
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
  height: 100vh;
  overflow: hidden;
}

.topbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  flex-shrink: 0;
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
  min-height: 0;
  overflow-y: auto;
  padding: 8px 36px 28px;
}

.footer {
  flex-shrink: 0;
  padding: 0 36px 24px;
  color: var(--crm-muted);
  font-size: 12px;
}

@media (max-width: 900px) {
  .app-shell {
    flex-direction: column;
    height: auto;
    overflow: visible;
  }

  .sidebar {
    width: 100%;
    height: auto;
    overflow: visible;
    padding: 16px 18px;
  }

  .brand {
    padding-bottom: 14px;
  }

  .nav {
    flex-direction: column;
    gap: 10px;
    margin-top: 14px;
  }

  .group-items {
    flex-direction: row;
    flex-wrap: wrap;
    margin-left: 0;
    padding-left: 0;
    border-left: none;
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

  .main {
    height: auto;
    overflow: visible;
  }

  .content {
    overflow: visible;
    padding: 4px 20px 24px;
  }

  .footer {
    padding: 0 20px 20px;
  }
}
</style>
