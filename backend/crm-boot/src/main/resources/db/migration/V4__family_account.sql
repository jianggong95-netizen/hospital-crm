-- =====================================================================
-- CRM-004 家庭账户
-- 说明：家庭创建、成员关联与关系历史、家庭管理员、代办授权与撤销。
--       家庭关系不构成病历访问授权；代办范围仅限非临床业务动作。
-- =====================================================================

-- 家庭编号序列：对外展示的家庭号，避免暴露自增主键
CREATE SEQUENCE IF NOT EXISTS crm_family_no_seq START WITH 100000 INCREMENT BY 1;

-- 成员关系状态：active 有效、ended 已解除（保留记录作为关系历史）
ALTER TABLE crm_family_member ADD COLUMN IF NOT EXISTS status VARCHAR(20) NOT NULL DEFAULT 'active';

DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ck_family_member_status') THEN
    ALTER TABLE crm_family_member
      ADD CONSTRAINT ck_family_member_status CHECK (status IN ('active','ended'));
  END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_family_member_family_status ON crm_family_member(family_id, status);
CREATE INDEX IF NOT EXISTS idx_family_member_customer_status ON crm_family_member(customer_id, status);

-- 家庭代办授权：一条记录对应一个成员的一项代办范围，可单独撤销
CREATE TABLE IF NOT EXISTS crm_family_authorization (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  family_id UUID NOT NULL REFERENCES crm_family(id),
  member_id UUID NOT NULL REFERENCES crm_family_member(id),
  grantor_customer_id UUID NOT NULL REFERENCES crm_customer(id),
  grantee_customer_id UUID NOT NULL REFERENCES crm_customer(id),
  scope_code VARCHAR(32) NOT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'active',
  valid_from TIMESTAMPTZ NOT NULL DEFAULT now(),
  valid_to TIMESTAMPTZ,
  revoked_at TIMESTAMPTZ,
  revoked_by UUID,
  created_by UUID,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT ck_family_auth_status CHECK (status IN ('active','revoked','expired')),
  -- 白名单仅包含非临床代办动作，病历与临床数据不在可授权范围内
  CONSTRAINT ck_family_auth_scope CHECK (scope_code IN ('appointment','payment','package_share','profile_update'))
);

CREATE INDEX IF NOT EXISTS idx_family_auth_lookup
  ON crm_family_authorization(member_id, grantee_customer_id, scope_code, status);
CREATE INDEX IF NOT EXISTS idx_family_auth_family ON crm_family_authorization(family_id, status);
CREATE INDEX IF NOT EXISTS idx_family_auth_grantee ON crm_family_authorization(grantee_customer_id, status);

-- 家庭账户权限基线
INSERT INTO sys_permission (permission_code, permission_name, module, permission_type, description, sort_order)
VALUES
  ('crm:family:read', '查看家庭账户', '家庭账户', 'api', '查看家庭列表、成员、关系历史与代办授权', 210),
  ('crm:family:write', '管理家庭账户', '家庭账户', 'action', '创建家庭、维护成员关系与家庭管理员', 220),
  ('crm:family:authorize', '管理代办授权', '家庭账户', 'action', '创建与撤销家庭代办授权；代办范围不含病历与临床数据', 230)
ON CONFLICT (permission_code) DO NOTHING;

-- 内置超级管理员自动获得新增权限
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r
CROSS JOIN sys_permission p
WHERE r.role_code = 'SUPER_ADMIN'
  AND p.permission_code LIKE 'crm:%'
ON CONFLICT DO NOTHING;
