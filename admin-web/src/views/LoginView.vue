<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
const router = useRouter()
const route = useRoute()

const formRef = ref<FormInstance>()
const loading = ref(false)
const errorMessage = ref('')
const form = reactive({ username: '', password: '' })

const rules: FormRules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

const isDev = import.meta.env.DEV

async function submit() {
  if (!formRef.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  loading.value = true
  errorMessage.value = ''
  try {
    await auth.login(form.username.trim(), form.password)
    ElMessage.success(`欢迎回来，${auth.user?.displayName ?? form.username}`)
    const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : '/'
    await router.replace(redirect)
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '登录失败，请稍后重试'
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="login-page">
    <section class="brand-panel">
      <div class="brand-head">
        <span class="brand-mark">
          <svg viewBox="0 0 64 64" aria-hidden="true">
            <path d="M32 16v32M16 32h32" stroke="currentColor" stroke-width="8" stroke-linecap="round" />
          </svg>
        </span>
        <div>
          <strong>Hospital CRM</strong>
          <small>医院客户服务与运营平台</small>
        </div>
      </div>

      <div class="brand-body">
        <h1>以患者为中心<br />统一客户运营与随访管理</h1>
        <p>内网私有化部署 · 细粒度权限控制 · 全链路操作审计</p>
      </div>

      <ul class="brand-points">
        <li><span></span>JWT 无状态认证与刷新令牌轮换</li>
        <li><span></span>RBAC 角色权限与五级数据范围</li>
        <li><span></span>敏感操作审计留痕，可按 traceId 追溯</li>
      </ul>
    </section>

    <section class="form-panel">
      <div class="form-card">
        <header>
          <p class="eyebrow">管理端登录</p>
          <h2>账号密码登录</h2>
          <p class="subtitle">请使用医院内部账号登录，账号异常请联系系统管理员。</p>
        </header>

        <el-alert
          v-if="errorMessage"
          :title="errorMessage"
          type="error"
          :closable="false"
          show-icon
          class="login-alert"
        />

        <el-form ref="formRef" :model="form" :rules="rules" label-position="top" @submit.prevent="submit">
          <el-form-item label="用户名" prop="username">
            <el-input v-model="form.username" size="large" placeholder="请输入用户名" autocomplete="username" />
          </el-form-item>
          <el-form-item label="密码" prop="password">
            <el-input
              v-model="form.password"
              size="large"
              type="password"
              show-password
              placeholder="请输入密码"
              autocomplete="current-password"
              @keyup.enter="submit"
            />
          </el-form-item>
          <el-button type="primary" size="large" class="submit" :loading="loading" @click="submit">
            登录
          </el-button>
        </el-form>

        <p v-if="isDev" class="dev-hint">
          开发环境初始管理员：<code>admin / 见 .env 的 CRM_BOOTSTRAP_ADMIN_PASSWORD</code>（生产部署必须通过环境变量覆盖）
        </p>
      </div>
    </section>
  </div>
</template>

<style scoped lang="scss">
.login-page {
  display: grid;
  grid-template-columns: minmax(0, 1.05fr) minmax(420px, 0.95fr);
  min-height: 100vh;
  background: #f4f7fb;
}

.brand-panel {
  display: flex;
  flex-direction: column;
  padding: 48px 56px;
  background:
    radial-gradient(90% 120% at 100% 0%, rgba(95, 162, 230, 0.35) 0%, rgba(95, 162, 230, 0) 60%),
    linear-gradient(160deg, #1c4a7e 0%, #163a63 60%, #102c4c 100%);
  color: #eaf2fb;
}

.brand-head {
  display: flex;
  align-items: center;
  gap: 14px;
}

.brand-mark {
  display: grid;
  place-items: center;
  width: 46px;
  height: 46px;
  border-radius: 14px;
  background: linear-gradient(135deg, #5fa2e6, #2d6dad);
  box-shadow: 0 10px 22px rgba(8, 24, 45, 0.35);
}

.brand-mark svg {
  width: 28px;
  height: 28px;
}

.brand-head strong {
  display: block;
  font-size: 18px;
  letter-spacing: 0.02em;
}

.brand-head small {
  color: rgba(234, 242, 251, 0.66);
  font-size: 12px;
}

.brand-body {
  margin-top: auto;
  max-width: 520px;
}

.brand-body h1 {
  font-size: 34px;
  line-height: 1.4;
  letter-spacing: 0.01em;
}

.brand-body p {
  margin-top: 16px;
  color: rgba(234, 242, 251, 0.7);
  font-size: 14px;
}

.brand-points {
  display: flex;
  flex-direction: column;
  gap: 12px;
  margin: 32px 0 0;
  padding: 0;
  list-style: none;
  color: rgba(234, 242, 251, 0.8);
  font-size: 13.5px;
}

.brand-points li {
  display: flex;
  align-items: center;
  gap: 10px;
}

.brand-points span {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: #8fd3b5;
  box-shadow: 0 0 0 4px rgba(143, 211, 181, 0.16);
}

.form-panel {
  display: grid;
  place-items: center;
  padding: 40px 32px;
}

.form-card {
  width: min(420px, 100%);
  padding: 36px 34px;
  border-radius: 20px;
  background: #fff;
  border: 1px solid var(--crm-border);
  box-shadow: var(--crm-shadow);
}

.eyebrow {
  color: var(--crm-primary);
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.12em;
}

.form-card h2 {
  margin-top: 8px;
  font-size: 24px;
}

.subtitle {
  margin-top: 8px;
  color: var(--crm-muted);
  font-size: 13px;
  line-height: 1.7;
}

.login-alert {
  margin-top: 18px;
}

.el-form {
  margin-top: 22px;
}

.submit {
  width: 100%;
  margin-top: 6px;
  height: 44px;
  font-size: 15px;
}

.dev-hint {
  margin-top: 20px;
  padding: 10px 12px;
  border-radius: 10px;
  background: var(--crm-warning-soft);
  color: #7a5a1f;
  font-size: 12px;
  line-height: 1.7;
}

.dev-hint code {
  font-family: 'Cascadia Code', Consolas, monospace;
}

@media (max-width: 960px) {
  .login-page {
    grid-template-columns: 1fr;
  }

  .brand-panel {
    padding: 32px 28px;
  }

  .brand-body {
    margin-top: 40px;
  }

  .brand-body h1 {
    font-size: 26px;
  }

  .brand-points {
    display: none;
  }
}
</style>
