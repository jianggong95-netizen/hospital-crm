-- CRM-020 系统集成、上线与运维
-- 交付：接口监控（任务/接口指标）、任务监控、备份与恢复演练登记、告警规则与处置。
-- 约束：
--   1) 定时任务每次执行落库一条 crm_ops_job_run，失败保留异常摘要（不含敏感数据）；
--   2) 备份记录必须来自真实备份脚本上报，file_hash 为 64 位 SHA-256，恢复演练结果单独登记；
--   3) 同一 dedup_key 仅允许存在一条 OPEN/ACKED 告警，恢复后置 RESOLVED 并可再次触发；
--   4) updated_at 由应用层 @PreUpdate 维护（与 V18/V19 做法一致）。

CREATE TABLE IF NOT EXISTS crm_ops_job_run (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  job_code VARCHAR(64) NOT NULL,
  status VARCHAR(16) NOT NULL,
  started_at TIMESTAMPTZ NOT NULL,
  finished_at TIMESTAMPTZ NOT NULL,
  duration_ms BIGINT NOT NULL DEFAULT 0,
  message VARCHAR(500),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT ck_ops_job_run_status CHECK (status IN ('SUCCESS', 'FAILED'))
);
CREATE INDEX IF NOT EXISTS idx_ops_job_run_job_started
  ON crm_ops_job_run(job_code, started_at DESC);
CREATE INDEX IF NOT EXISTS idx_ops_job_run_created
  ON crm_ops_job_run(created_at);

CREATE TABLE IF NOT EXISTS crm_ops_backup_record (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  backup_code VARCHAR(80) NOT NULL UNIQUE,
  backup_type VARCHAR(16) NOT NULL,
  status VARCHAR(16) NOT NULL,
  size_bytes BIGINT NOT NULL DEFAULT 0,
  file_hash VARCHAR(64),
  target VARCHAR(120),
  started_at TIMESTAMPTZ NOT NULL,
  finished_at TIMESTAMPTZ NOT NULL,
  duration_seconds BIGINT,
  message VARCHAR(500),
  reported_by VARCHAR(120),
  restore_verified BOOLEAN NOT NULL DEFAULT false,
  restore_verified_at TIMESTAMPTZ,
  restore_message VARCHAR(500),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT ck_ops_backup_type CHECK (backup_type IN ('FULL', 'WAL', 'OBJECT')),
  CONSTRAINT ck_ops_backup_status CHECK (status IN ('SUCCESS', 'FAILED')),
  CONSTRAINT ck_ops_backup_size CHECK (size_bytes >= 0),
  CONSTRAINT ck_ops_backup_time CHECK (finished_at >= started_at)
);
CREATE INDEX IF NOT EXISTS idx_ops_backup_status_started
  ON crm_ops_backup_record(status, started_at DESC);

CREATE TABLE IF NOT EXISTS crm_ops_alert (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  alert_code VARCHAR(64) NOT NULL,
  severity VARCHAR(16) NOT NULL,
  status VARCHAR(16) NOT NULL DEFAULT 'OPEN',
  title VARCHAR(200) NOT NULL,
  detail TEXT,
  dedup_key VARCHAR(160) NOT NULL,
  source VARCHAR(32) NOT NULL DEFAULT 'SYSTEM',
  first_seen_at TIMESTAMPTZ NOT NULL,
  last_seen_at TIMESTAMPTZ NOT NULL,
  occurrences INTEGER NOT NULL DEFAULT 1,
  acked_by UUID,
  acked_at TIMESTAMPTZ,
  resolved_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT ck_ops_alert_severity CHECK (severity IN ('CRITICAL', 'HIGH', 'MEDIUM', 'LOW')),
  CONSTRAINT ck_ops_alert_status CHECK (status IN ('OPEN', 'ACKED', 'RESOLVED'))
);
CREATE INDEX IF NOT EXISTS idx_ops_alert_status_last_seen
  ON crm_ops_alert(status, last_seen_at DESC);
-- 同一告警去重键只允许一条未关闭记录，避免定时评估重复插入
CREATE UNIQUE INDEX IF NOT EXISTS uk_ops_alert_open_dedup
  ON crm_ops_alert(dedup_key) WHERE status IN ('OPEN', 'ACKED');

-- CRM-020 权限基线：运维只读与运维处置（备份上报、恢复演练登记、告警确认与外部接入）
INSERT INTO sys_permission (permission_code, permission_name, module, permission_type, description, sort_order)
VALUES
  ('crm:ops:read', '查看运维监控', '运维监控', 'api', '查看服务总览、定时任务状态与运行记录、告警列表、告警规则与备份记录', 850),
  ('crm:ops:manage', '运维处置', '运维监控', 'action', '上报备份结果、登记恢复演练结果、确认告警与接入外部日志告警', 860)
ON CONFLICT (permission_code) DO NOTHING;

INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r
CROSS JOIN sys_permission p
WHERE r.role_code = 'SUPER_ADMIN'
  AND p.permission_code LIKE 'crm:ops:%'
ON CONFLICT DO NOTHING;
