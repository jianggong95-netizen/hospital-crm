export interface DataScopeView {
  all: boolean
  orgIds: string[]
  includeSelf: boolean
}

export interface UserSession {
  id: string
  username: string
  displayName: string
  orgId: string | null
  orgName: string | null
  roles: string[]
  permissions: string[]
  dataScope: DataScopeView
}

export interface TokenResponse {
  accessToken: string
  refreshToken: string
  tokenType: string
  expiresIn: number
  user: UserSession
}
