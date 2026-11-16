-- =====================================================================
-- CRM-005 HIS SQL Server 只读适配器
-- 说明：患者查询快照、HIS 同步日志查询索引与 HIS 集成权限。
--       HIS 适配器只通过只读账号访问医院确认的患者/就诊/住院视图，
--       禁止写入临床、收费等 HIS 业务表；快照仅保存最小化聚合结果。
-- =====================================================================

CREATE TABLE IF NOT EXISTS crm_patient_snapshot (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  customer_id UUID REFERENCES crm_customer(id),
  source_system VARCHAR(50) NOT NULL,
  external_patient_id VARCHAR(100) NOT NULL,
  snapshot JSONB NOT NULL DEFAULT '{}'::jsonb,
  fetched_at TIMESTAMPTZ NOT NULL,
  valid_until TIMESTAMPTZ,
  match_confidence NUMERIC(5,4),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT ck_patient_snapshot_confidence
    CHECK (match_confidence IS NULL OR (match_confidence >= 0 AND match_confidence <= 1))
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_patient_snapshot_source_external
  ON crm_patient_snapshot(source_system, external_patient_id);
CREATE INDEX IF NOT EXISTS idx_patient_snapshot_fetched
  ON crm_patient_snapshot(fetched_at DESC);

-- 同步日志查询索引：供运维按状态与操作类型快速定位失败/降级记录
CREATE INDEX IF NOT EXISTS idx_his_sync_status_time
  ON crm_his_sync_log(status, started_at DESC);
CREATE INDEX IF NOT EXISTS idx_his_sync_operation_time
  ON crm_his_sync_log(operation, started_at DESC);

-- HIS 集成权限基线：his 前缀不属于 crm:% 自动授权范围，需在此显式授权
INSERT INTO sys_permission (permission_code, permission_name, module, permission_type, description, sort_order)
VALUES
  ('his:patient:read', '查看 HIS 患者信息', 'HIS 集成', 'api', '查询 HIS 患者候选、360 摘要与同步日志', 310),
  ('his:patient:link', '绑定 HIS 患者', 'HIS 集成', 'action', '人工核验后将 HIS 患者主索引绑定到 CRM 客户', 320)
ON CONFLICT (permission_code) DO NOTHING;

INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r
CROSS JOIN sys_permission p
WHERE r.role_code = 'SUPER_ADMIN'
  AND p.permission_code LIKE 'his:%'
ON CONFLICT DO NOTHING;
