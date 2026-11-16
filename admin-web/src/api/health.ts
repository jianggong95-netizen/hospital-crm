import axios from 'axios'
import type { HealthStatus } from '@/types/api'

const healthHttp = axios.create({
  baseURL: import.meta.env.VITE_HEALTH_BASE_URL || '/actuator',
  timeout: 5000
})

export async function fetchHealth(): Promise<HealthStatus> {
  const response = await healthHttp.get<HealthStatus>('/health')
  return response.data
}
