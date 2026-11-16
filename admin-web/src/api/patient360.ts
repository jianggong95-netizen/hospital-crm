import { api } from './http'
import type { Patient360View } from '@/types/patient360'

export interface Patient360Query {
  include?: string
  visitPage?: number
  visitPageSize?: number
  admissionPage?: number
  admissionPageSize?: number
  refresh?: boolean
}

export function fetchPatient360(customerId: string, query: Patient360Query = {}): Promise<Patient360View> {
  return api.get<Patient360View>(`/customers/${customerId}/360`, query)
}
