import axios, { AxiosError } from 'axios'
import type { AxiosRequestConfig } from 'axios'
import type { ApiEnvelope } from '@/types/api'
import type { TokenResponse } from '@/types/auth'
import { clearTokens, loadTokens, saveTokens } from '@/auth/tokenStore'

const baseURL = import.meta.env.VITE_API_BASE_URL || '/api/v1'

const http = axios.create({
  baseURL,
  timeout: 15000,
  headers: { Accept: 'application/json' }
})

http.interceptors.request.use((config) => {
  const tokens = loadTokens()
  if (tokens?.accessToken) {
    config.headers.Authorization = `Bearer ${tokens.accessToken}`
  }
  return config
})

let refreshing: Promise<boolean> | null = null

async function refreshTokens(): Promise<boolean> {
  const tokens = loadTokens()
  if (!tokens?.refreshToken) return false
  try {
    const response = await axios.post<ApiEnvelope<TokenResponse>>(
      `${baseURL}/auth/refresh`,
      { refreshToken: tokens.refreshToken },
      { timeout: 15000 }
    )
    if (response.data.code !== 'OK' || !response.data.data) return false
    saveTokens({
      accessToken: response.data.data.accessToken,
      refreshToken: response.data.data.refreshToken
    })
    return true
  } catch {
    return false
  }
}

function redirectToLogin() {
  clearTokens()
  if (window.location.pathname.startsWith('/login')) return
  const redirect = encodeURIComponent(window.location.pathname + window.location.search)
  window.location.assign(`/login?redirect=${redirect}`)
}

http.interceptors.response.use(
  (response) => response,
  async (error: AxiosError<ApiEnvelope<unknown>>) => {
    const config = error.config as (AxiosRequestConfig & { __retried?: boolean }) | undefined
    const status = error.response?.status
    const url = config?.url ?? ''

    if (status === 401 && config && !config.__retried && !url.startsWith('/auth/')) {
      config.__retried = true
      refreshing = refreshing ?? refreshTokens().finally(() => {
        refreshing = null
      })
      if (await refreshing) {
        return http.request(config)
      }
      redirectToLogin()
    }

    const body = error.response?.data
    const detail = body?.details?.map((item) => `${item.field}: ${item.reason}`).join('；')
    const message = detail || body?.message || error.message || '网络请求失败'
    return Promise.reject(new Error(message))
  }
)

function unwrap<T>(body: ApiEnvelope<T>): T {
  if (body.code !== 'OK') {
    throw new Error(body.message || '请求失败')
  }
  return body.data
}

export const api = {
  async get<T>(url: string, params?: object): Promise<T> {
    const response = await http.get<ApiEnvelope<T>>(url, { params })
    return unwrap(response.data)
  },
  async getBlob(url: string, params?: object): Promise<Blob> {
    const response = await http.get<Blob>(url, { params, responseType: 'blob' })
    return response.data
  },
  async post<T>(url: string, payload?: unknown): Promise<T> {
    const response = await http.post<ApiEnvelope<T>>(url, payload)
    return unwrap(response.data)
  },
  async put<T>(url: string, payload?: unknown): Promise<T> {
    const response = await http.put<ApiEnvelope<T>>(url, payload)
    return unwrap(response.data)
  },
  async patch<T>(url: string, payload?: unknown): Promise<T> {
    const response = await http.patch<ApiEnvelope<T>>(url, payload)
    return unwrap(response.data)
  },
  async upload<T>(url: string, form: FormData, params?: object): Promise<T> {
    const response = await http.post<ApiEnvelope<T>>(url, form, { params })
    return unwrap(response.data)
  },
  async del<T>(url: string): Promise<T> {
    const response = await http.delete<ApiEnvelope<T>>(url)
    return unwrap(response.data)
  }
}
