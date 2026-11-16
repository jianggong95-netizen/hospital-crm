import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { fetchMe, login as loginApi, logout as logoutApi } from '@/api/auth'
import { clearTokens, loadTokens, saveTokens } from '@/auth/tokenStore'
import type { StoredTokens } from '@/auth/tokenStore'
import type { UserSession } from '@/types/auth'

export const useAuthStore = defineStore('auth', () => {
  const tokens = ref<StoredTokens | null>(loadTokens())
  const user = ref<UserSession | null>(null)
  const permissions = computed(() => new Set(user.value?.permissions ?? []))
  const isAuthenticated = computed(() => Boolean(tokens.value?.accessToken))

  function applyTokens(next: StoredTokens) {
    saveTokens(next)
    tokens.value = next
  }

  function clearSession() {
    clearTokens()
    tokens.value = null
    user.value = null
  }

  function has(permission?: string): boolean {
    if (!permission) return true
    return permissions.value.has(permission)
  }

  async function login(username: string, password: string): Promise<void> {
    const result = await loginApi(username, password)
    applyTokens({ accessToken: result.accessToken, refreshToken: result.refreshToken })
    user.value = result.user
  }

  async function restore(): Promise<boolean> {
    if (!isAuthenticated.value) return false
    try {
      user.value = await fetchMe()
      return true
    } catch {
      clearSession()
      return false
    }
  }

  async function logout(): Promise<void> {
    const current = tokens.value
    try {
      if (current?.refreshToken) {
        await logoutApi(current.refreshToken)
      }
    } catch {
      // 忽略登出接口异常，本地会话必须清理
    } finally {
      clearSession()
    }
  }

  return { user, permissions, isAuthenticated, has, login, restore, logout }
})
