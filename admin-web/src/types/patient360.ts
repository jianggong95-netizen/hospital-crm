import type { CustomerDetail } from '@/types/customer'
import type { PageResponse } from '@/types/system'

export type SectionStatus =
  | 'LIVE'
  | 'STALE'
  | 'UNAVAILABLE'
  | 'NOT_LINKED'
  | 'RESTRICTED'
  | 'NOT_FOUND'
  | 'NOT_REQUESTED'

export interface Patient360DataSource {
  section: string
  sourceSystem: string
  dataStatus: SectionStatus
  fetchedAt?: string
  validUntil?: string
  cached?: boolean
  message?: string
}

export interface Patient360Visit {
  visitId: string
  visitTime?: string
  department?: string
  doctor?: string
  diagnosis?: string
}

export interface Patient360Admission {
  admissionId: string
  admittedAt?: string
  dischargedAt?: string
  ward?: string
  diagnosis?: string
}

export interface Patient360Family {
  familyId: string
  familyNo: string
  familyName: string
  familyStatus: string
  relationshipCode: string
  relationshipLabel: string
  admin: boolean
  memberCount: number
  validFrom?: string
}

export interface Patient360FamilySection {
  dataStatus: SectionStatus
  message?: string
  family?: Patient360Family
}

export interface Patient360HisSection {
  dataStatus: SectionStatus
  message?: string
  sourceSystem?: string
  externalPatientIdMasked?: string
  patientNameMasked?: string
  mrn?: string
  gender?: string
  birthDate?: string
  mobileMasked?: string
  fetchedAt?: string
  validUntil?: string
  stale: boolean
  cached: boolean
  matchConfidence?: number
  requiresManualVerification: boolean
  visits?: PageResponse<Patient360Visit>
  admissions?: PageResponse<Patient360Admission>
  errorCode?: string
}

export interface Patient360View {
  customer: CustomerDetail
  sensitiveVisible: boolean
  family: Patient360FamilySection
  his: Patient360HisSection
  dataSources: Patient360DataSource[]
  unavailableSections: string[]
}
