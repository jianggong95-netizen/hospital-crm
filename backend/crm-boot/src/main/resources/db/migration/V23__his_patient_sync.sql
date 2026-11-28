-- =====================================================================
-- CRM-023 HIS 患者全量同步
-- 说明：V1 基线已有 crm_customer.merged_into（合并归档指向）与 ck_customer_status
--       含 merged 取值，V22 已建 crm_customer_merge_log，本迁移仅补齐同步链路字段：
--       his_synced_at 记录客户最近一次被 HIS 全量同步覆盖的时间；
--       merged_into 部分索引支撑合并追溯查询。
-- =====================================================================

ALTER TABLE crm_customer ADD COLUMN IF NOT EXISTS his_synced_at TIMESTAMPTZ;

CREATE INDEX IF NOT EXISTS idx_customer_merged_into
  ON crm_customer(merged_into) WHERE merged_into IS NOT NULL;
