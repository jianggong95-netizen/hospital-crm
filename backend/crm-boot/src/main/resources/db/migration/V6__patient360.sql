-- =====================================================================
-- CRM-006 患者360
-- 说明：CRM 客户主数据、家庭归属与 HIS 诊疗摘要的聚合视图。
--       本迁移仅新增查询索引与权限，不新增患者数据副本；
--       HIS 临床数据仍通过 CRM-005 只读适配器与快照降级策略获取。
-- =====================================================================

-- 按客户定位已绑定的 HIS 患者主索引，避免全表扫描
CREATE INDEX IF NOT EXISTS idx_identity_customer
  ON crm_customer_identity(customer_id, source_system, identity_type);

-- 患者360权限基线：聚合视图本身不授予敏感字段或临床数据权限
INSERT INTO sys_permission (permission_code, permission_name, module, permission_type, description, sort_order)
VALUES
  ('crm:patient360:read', '查看患者360', '患者360', 'api',
   '聚合客户档案、标签、家庭归属与HIS诊疗摘要；敏感字段仍需 crm:customer:sensitive:read，HIS数据仍需 his:patient:read', 240)
ON CONFLICT (permission_code) DO NOTHING;

-- 内置超级管理员自动获得新增权限
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r
CROSS JOIN sys_permission p
WHERE r.role_code = 'SUPER_ADMIN'
  AND p.permission_code LIKE 'crm:%'
ON CONFLICT DO NOTHING;
