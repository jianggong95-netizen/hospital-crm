import { clearSession, getTokens } from '@/store/session'

const baseURL = import.meta.env.VITE_API_BASE_URL || '/api/v1'

type Method = 'GET' | 'POST' | 'PUT' | 'DELETE'

interface Envelope<T> {
  code: string
  message?: string
  data?: T
  details?: Array<{ field: string; reason: string }>
}

let redirecting = false

function handleUnauthorized(): void {
  clearSession()
  if (redirecting) {
    return
  }
  redirecting = true
  uni.reLaunch({
    url: '/pages/login/login',
    complete: () => {
      redirecting = false
    }
  })
}

export function request<T>(method: Method, url: string, data?: unknown): Promise<T> {
  const tokens = getTokens()
  return new Promise<T>((resolve, reject) => {
    uni.request({
      url: baseURL + url,
      method,
      data: data as never,
      timeout: 15000,
      header: {
        Accept: 'application/json',
        ...(tokens?.accessToken ? { Authorization: 'Bearer ' + tokens.accessToken } : {})
      },
      success: (res) => {
        if (res.statusCode === 401) {
          handleUnauthorized()
          reject(new Error('登录已过期，请重新登录'))
          return
        }
        const body = res.data as Envelope<T>
        if (res.statusCode >= 200 && res.statusCode < 300 && body && body.code === 'OK') {
          resolve(body.data as T)
          return
        }
        const detail = body?.details?.map((d) => `${d.field}: ${d.reason}`).join('；')
        reject(new Error(detail || body?.message || `请求失败（${res.statusCode}）`))
      },
      fail: (err) => {
        reject(new Error(err.errMsg || '网络异常，请检查服务是否启动'))
      }
    })
  })
}

export const http = {
  get: <T>(url: string, params?: object) => request<T>('GET', url, params),
  post: <T>(url: string, payload?: unknown) => request<T>('POST', url, payload),
  put: <T>(url: string, payload?: unknown) => request<T>('PUT', url, payload),
  del: <T>(url: string) => request<T>('DELETE', url)
}

export interface PageResult<T> {
  items: T[]
  total: number
  page: number
  pageSize: number
  totalPages: number
}
