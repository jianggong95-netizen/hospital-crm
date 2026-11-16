export interface FieldViolation {
  field: string
  reason: string
}

export interface ApiEnvelope<T> {
  code: string
  message: string
  data: T
  details?: FieldViolation[]
  traceId: string
  timestamp: string
}

export interface SystemInfo {
  application: string
  version: string
  profiles: string[]
  javaVersion: string
  os: string
  timezone: string
  serverTime: string
}

export interface HealthComponent {
  status: string
  details?: Record<string, unknown>
}

export interface HealthStatus {
  status: string
  components?: Record<string, HealthComponent>
}
