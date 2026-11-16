-- =====================================================================
-- CRM-003 客户中心
-- 说明：新增客户标签字典与客户标签关联，并补充客户中心权限项。
--       客户主表沿用 V1 的 crm_customer（手机号/证件号仅保存密文、hash 与脱敏值）。
-- =====================================================================

-- 客户编号序列：对外展示的客户号，避免暴露自增主键
CREATE SEQUENCE IF NOT EXISTS crm_customer_no_seq START WITH 100000 INCREMENT BY 1;

CREATE TABLE IF NOT EXISTS crm_tag (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  tag_code VARCHAR(64) NOT NULL UNIQUE,
  tag_name VARCHAR(80) NOT NULL,
  tag_type VARCHAR(32) NOT NULL DEFAULT 'customer',
  status VARCHAR(20) NOT NULL DEFAULT 'active',
  sort_order INTEGER NOT NULL DEFAULT 0,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT ck_tag_status CHECK (status IN ('active','disabled')),
  CONSTRAINT ck_tag_type CHECK (tag_type IN ('customer','service','risk'))
);

CREATE TABLE IF NOT EXISTS crm_customer_tag (
  customer_id UUID NOT NULL REFERENCES crm_customer(id) ON DELETE CASCADE,
  tag_id UUID NOT NULL REFERENCES crm_tag(id) ON DELETE CASCADE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY (customer_id, tag_id)
);
CREATE INDEX IF NOT EXISTS idx_customer_tag_tag ON crm_customer_tag(tag_id);

CREATE INDEX IF NOT EXISTS idx_customer_source ON crm_customer(source_code);
CREATE INDEX IF NOT EXISTS idx_customer_status_created ON crm_customer(status, created_at DESC);

-- 标签基线
INSERT INTO crm_tag (tag_code, tag_name, tag_type, sort_order)
VALUES
  ('VIP', 'VIP客户', 'customer', 10),
  ('CHRONIC', '慢病管理', 'customer', 20),
  ('HIGH_RISK', '高风险关注', 'risk', 30),
  ('POST_OP', '术后随访', 'service', 40),
  ('MATERNAL', '孕产妇', 'customer', 50),
  ('CORPORATE', '企业客户', 'customer', 60)
ON CONFLICT (tag_code) DO NOTHING;

-- 客户中心权限基线
INSERT INTO sys_permission (permission_code, permission_name, module, permission_type, description, sort_order)
VALUES
  ('crm:customer:read', '查看客户', '客户中心', 'api', '查看客户列表与档案详情', 110),
  ('crm:customer:write', '管理客户', '客户中心', 'action', '创建、编辑、启停客户档案与维护标签', 120),
  ('crm:customer:sensitive:read', '查看客户敏感信息', '客户中心', 'api', '查看解密后的完整手机号与证件号，操作写入审计', 130),
  ('crm:tag:read', '查看标签', '客户中心', 'api', '查看客户标签字典', 140),
  ('crm:tag:write', '管理标签', '客户中心', 'action', '创建、编辑、启停客户标签', 150)
ON CONFLICT (permission_code) DO NOTHING;

-- 内置超级管理员自动获得新增权限
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r
CROSS JOIN sys_permission p
WHERE r.role_code = 'SUPER_ADMIN'
  AND p.permission_code LIKE 'crm:%'
ON CONFLICT DO NOTHING;
