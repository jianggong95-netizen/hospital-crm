export interface HealthStatus {
  status: string
}

const healthBaseUrl = import.meta.env.VITE_HEALTH_BASE_URL || 'http://localhost:8080/actuator'

export function fetchHealth(): Promise<HealthStatus> {
  return new Promise((resolve, reject) => {
    uni.request({
      url: `${healthBaseUrl}/health`,
      method: 'GET',
      timeout: 5000,
      success: (response) => {
        if (response.statusCode >= 200 && response.statusCode < 300) {
          resolve(response.data as HealthStatus)
          return
        }
        reject(new Error(`健康检查失败：${response.statusCode}`))
      },
      fail: (error) => {
        reject(new Error(error.errMsg || '网络异常'))
      }
    })
  })
}
