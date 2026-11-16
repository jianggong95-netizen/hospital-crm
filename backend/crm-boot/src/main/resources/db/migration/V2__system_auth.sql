-- =====================================================================
-- CRM-002 认证、组织、角色、权限、数据范围与审计
-- 说明：本迁移只新增系统表与种子数据，不修改 V1 已应用内容。
-- =====================================================================

CREATE TABLE IF NOT EXISTS sys_org (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  org_code VARCHAR(64) NOT NULL UNIQUE,
  org_name VARCHAR(120) NOT NULL,
  parent_id UUID REFERENCES sys_org(id),
  org_path VARCHAR(1000) NOT NULL,
  org_type VARCHAR(32) NOT NULL DEFAULT 'department',
  sort_order INTEGER NOT NULL DEFAULT 0,
  status VARCHAR(20) NOT NULL DEFAULT 'active',
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT ck_org_status CHECK (status IN ('active','disabled'))
);
CREATE INDEX IF NOT EXISTS idx_org_parent ON sys_org(parent_id);
CREATE INDEX IF NOT EXISTS idx_org_path ON sys_org(org_path varchar_pattern_ops);

CREATE TABLE IF NOT EXISTS sys_user (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  username VARCHAR(64) NOT NULL,
  password_hash VARCHAR(100) NOT NULL,
  display_name VARCHAR(100) NOT NULL,
  email VARCHAR(120),
  mobile_masked VARCHAR(32),
  org_id UUID NOT NULL REFERENCES sys_org(id),
  status VARCHAR(20) NOT NULL DEFAULT 'active',
  failed_login_count INTEGER NOT NULL DEFAULT 0,
  locked_until TIMESTAMPTZ,
  last_login_at TIMESTAMPTZ,
  password_updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT ck_user_status CHECK (status IN ('active','disabled','locked'))
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_user_username_lower ON sys_user(lower(username));
CREATE INDEX IF NOT EXISTS idx_user_org ON sys_user(org_id);
CREATE INDEX IF NOT EXISTS idx_user_status ON sys_user(status);

CREATE TABLE IF NOT EXISTS sys_role (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  role_code VARCHAR(64) NOT NULL UNIQUE,
  role_name VARCHAR(120) NOT NULL,
  description VARCHAR(500),
  data_scope VARCHAR(32) NOT NULL DEFAULT 'self',
  status VARCHAR(20) NOT NULL DEFAULT 'active',
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT ck_role_scope CHECK (data_scope IN ('all','org_and_children','org','self','custom')),
  CONSTRAINT ck_role_status CHECK (status IN ('active','disabled'))
);

CREATE TABLE IF NOT EXISTS sys_permission (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  permission_code VARCHAR(100) NOT NULL UNIQUE,
  permission_name VARCHAR(120) NOT NULL,
  module VARCHAR(64) NOT NULL,
  permission_type VARCHAR(20) NOT NULL DEFAULT 'api',
  description VARCHAR(500),
  sort_order INTEGER NOT NULL DEFAULT 0,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT ck_permission_type CHECK (permission_type IN ('menu','action','api'))
);

CREATE TABLE IF NOT EXISTS sys_user_role (
  user_id UUID NOT NULL REFERENCES sys_user(id) ON DELETE CASCADE,
  role_id UUID NOT NULL REFERENCES sys_role(id) ON DELETE CASCADE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY (user_id, role_id)
);

CREATE TABLE IF NOT EXISTS sys_role_permission (
  role_id UUID NOT NULL REFERENCES sys_role(id) ON DELETE CASCADE,
  permission_id UUID NOT NULL REFERENCES sys_permission(id) ON DELETE CASCADE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY (role_id, permission_id)
);

CREATE TABLE IF NOT EXISTS sys_role_org_scope (
  role_id UUID NOT NULL REFERENCES sys_role(id) ON DELETE CASCADE,
  org_id UUID NOT NULL REFERENCES sys_org(id) ON DELETE CASCADE,
  PRIMARY KEY (role_id, org_id)
);

CREATE TABLE IF NOT EXISTS sys_refresh_token (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id UUID NOT NULL REFERENCES sys_user(id) ON DELETE CASCADE,
  token_hash VARCHAR(64) NOT NULL UNIQUE,
  expires_at TIMESTAMPTZ NOT NULL,
  revoked_at TIMESTAMPTZ,
  replaced_by_hash VARCHAR(64),
  ip_address VARCHAR(64),
  user_agent VARCHAR(500),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_refresh_user ON sys_refresh_token(user_id, expires_at);
CREATE INDEX IF NOT EXISTS idx_refresh_expiry ON sys_refresh_token(expires_at);

ALTER TABLE sys_audit_log ADD COLUMN IF NOT EXISTS trace_id VARCHAR(80);
CREATE INDEX IF NOT EXISTS idx_audit_action_time ON sys_audit_log(action, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_audit_trace ON sys_audit_log(trace_id);

-- 根组织：医院总部
INSERT INTO sys_org (id, org_code, org_name, parent_id, org_path, org_type, sort_order, status)
VALUES ('00000000-0000-0000-0000-000000000001', 'ROOT', '医院总部', NULL,
        '/00000000-0000-0000-0000-000000000001/', 'hospital', 0, 'active')
ON CONFLICT (org_code) DO NOTHING;

-- 权限基线
INSERT INTO sys_permission (permission_code, permission_name, module, permission_type, description, sort_order)
VALUES
  ('system:user:read', '查看用户', '系统管理', 'api', '查看用户列表与详情', 10),
  ('system:user:write', '管理用户', '系统管理', 'action', '创建、编辑、启停用户与重置密码', 20),
  ('system:role:read', '查看角色', '系统管理', 'api', '查看角色与权限配置', 30),
  ('system:role:write', '管理角色', '系统管理', 'action', '创建、编辑角色与数据范围', 40),
  ('system:permission:read', '查看权限项', '系统管理', 'api', '查看权限字典', 50),
  ('system:org:read', '查看组织', '系统管理', 'api', '查看组织架构树', 60),
  ('system:org:write', '管理组织', '系统管理', 'action', '创建、编辑、启停组织', 70),
  ('system:audit:read', '查看审计日志', '系统管理', 'api', '查看敏感操作审计日志', 80)
ON CONFLICT (permission_code) DO NOTHING;

-- 内置超级管理员角色，拥有全部权限与全院数据范围
INSERT INTO sys_role (id, role_code, role_name, description, data_scope, status)
VALUES ('00000000-0000-0000-0000-000000000101', 'SUPER_ADMIN', '超级管理员',
        '内置角色，拥有全部权限与全院数据范围，不允许通过接口修改', 'all', 'active')
ON CONFLICT (role_code) DO NOTHING;

INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r
CROSS JOIN sys_permission p
WHERE r.role_code = 'SUPER_ADMIN'
ON CONFLICT DO NOTHING;
