export interface PageResponse<T> {
  items: T[]
  total: number
  page: number
  pageSize: number
  totalPages: number
}

export interface RoleSummary {
  id: string
  roleCode: string
  roleName: string
}

export interface UserResponse {
  id: string
  username: string
  displayName: string
  email?: string
  mobileMasked?: string
  orgId: string
  orgName?: string
  status: 'active' | 'disabled'
  roles: RoleSummary[]
  lastLoginAt?: string
  createdAt: string
}

export interface UserCreatePayload {
  username: string
  password: string
  displayName: string
  email?: string
  mobile?: string
  orgId: string
  roleIds: string[]
}

export interface UserUpdatePayload {
  displayName: string
  email?: string
  mobile?: string
  orgId: string
  roleIds: string[]
  status: 'active' | 'disabled'
}

export interface RoleResponse {
  id: string
  roleCode: string
  roleName: string
  description?: string
  dataScope: string
  status: 'active' | 'disabled'
  permissionCodes: string[]
  orgIds: string[]
  createdAt: string
}

export interface RoleCreatePayload {
  roleCode: string
  roleName: string
  description?: string
  dataScope: string
  permissionCodes: string[]
  orgIds: string[]
}

export interface RoleUpdatePayload {
  roleName: string
  description?: string
  dataScope: string
  permissionCodes: string[]
  orgIds: string[]
  status: 'active' | 'disabled'
}

export interface PermissionItem {
  id: string
  permissionCode: string
  permissionName: string
  module: string
  permissionType: string
  description?: string
  sortOrder: number
}

export interface PermissionGroup {
  module: string
  items: PermissionItem[]
}

export interface OrgNode {
  id: string
  orgCode: string
  orgName: string
  parentId?: string
  orgType: string
  sortOrder: number
  status: 'active' | 'disabled'
  children: OrgNode[]
}

export interface OrgCreatePayload {
  orgCode: string
  orgName: string
  parentId: string
  orgType?: string
  sortOrder?: number
}

export interface OrgUpdatePayload {
  orgName: string
  orgType?: string
  sortOrder?: number
  status: 'active' | 'disabled'
}

export interface AuditLogItem {
  id: string
  actorUserId?: string
  actorName?: string
  action: string
  resourceType: string
  resourceId?: string
  ipAddress?: string
  traceId?: string
  detail?: string
  createdAt: string
}
