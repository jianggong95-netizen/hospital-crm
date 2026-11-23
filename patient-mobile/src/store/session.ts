export interface CrmUser {
  id: string
  username: string
  displayName: string
  roles: string[]
  permissions: string[]
}

export interface PatientBrief {
  id: string
  customerNo: string
  name: string
  gender: string
  phoneMasked?: string
}

export interface Tokens {
  accessToken: string
  refreshToken: string
}

const TOKEN_KEY = 'crm.mobile.tokens'
const USER_KEY = 'crm.mobile.user'
const PATIENT_KEY = 'crm.mobile.patient'

export function getTokens(): Tokens | null {
  const v = uni.getStorageSync(TOKEN_KEY)
  return v ? (v as Tokens) : null
}

export function saveSession(tokens: Tokens, user: CrmUser): void {
  uni.setStorageSync(TOKEN_KEY, tokens)
  uni.setStorageSync(USER_KEY, user)
}

export function getUser(): CrmUser | null {
  const v = uni.getStorageSync(USER_KEY)
  return v ? (v as CrmUser) : null
}

export function getPatient(): PatientBrief | null {
  const v = uni.getStorageSync(PATIENT_KEY)
  return v ? (v as PatientBrief) : null
}

export function setPatient(patient: PatientBrief): void {
  uni.setStorageSync(PATIENT_KEY, patient)
}

export function clearPatient(): void {
  uni.removeStorageSync(PATIENT_KEY)
}

const PENDING_NAV_KEY = 'crm.mobile.pendingNav'

/** 选择就诊人后要跳转的业务页（避免在 URL 中传递编码后的路径） */
export function setPendingNav(url: string): void {
  uni.setStorageSync(PENDING_NAV_KEY, url)
}

export function takePendingNav(): string {
  const value = uni.getStorageSync(PENDING_NAV_KEY)
  uni.removeStorageSync(PENDING_NAV_KEY)
  return (value as string) || ''
}

export function clearSession(): void {
  uni.removeStorageSync(TOKEN_KEY)
  uni.removeStorageSync(USER_KEY)
  uni.removeStorageSync(PATIENT_KEY)
}

export function isLoggedIn(): boolean {
  const tokens = getTokens()
  return !!(tokens && tokens.accessToken)
}

/** 未登录时跳转登录页；返回是否已登录 */
export function ensureLogin(): boolean {
  if (isLoggedIn()) {
    return true
  }
  uni.reLaunch({ url: '/pages/login/login' })
  return false
}
