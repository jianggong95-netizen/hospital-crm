-- =====================================================================
-- CRM-022 客户查重与合并
-- 说明：支持将重复建档的客户合并到保留档案，业务数据外键整体改挂到目标客户；
--       基线 ck_customer_status 虽已包含 merged，仍放开状态 CHECK 以便后续扩展，
--       状态取值合法性由服务层（启用/停用/已合并）继续约束；
--       新增 crm_customer_merge_log 记录每次合并的目标、来源、操作人与移动明细。
-- =====================================================================

ALTER TABLE crm_customer DROP CONSTRAINT IF EXISTS ck_customer_status;

CREATE TABLE IF NOT EXISTS crm_customer_merge_log (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  target_customer_id UUID NOT NULL REFERENCES crm_customer(id),
  source_customer_id UUID NOT NULL REFERENCES crm_customer(id),
  merged_by UUID,
  detail JSONB NOT NULL DEFAULT '{}'::jsonb,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_customer_merge_log_target
  ON crm_customer_merge_log(target_customer_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_customer_merge_log_source
  ON crm_customer_merge_log(source_customer_id);
