-- =====================================================================
-- CRM-014 投诉表扬中心
-- 说明：在基线 crm_complaint 之上补充处理人、时限、升级与结案信息；
--       新增处理动作、整改、患者反馈与结案复核表，保证所有动作留痕；
--       处理人与结案审核按权限分权，超期未结案自动升级。
-- =====================================================================

CREATE SEQUENCE IF NOT EXISTS crm_complaint_ticket_no_seq START WITH 200000 INCREMENT BY 1;

-- 扩展基线工单表
ALTER TABLE crm_complaint ADD COLUMN IF NOT EXISTS source VARCHAR(24) NOT NULL DEFAULT 'manual';
ALTER TABLE crm_complaint ADD COLUMN IF NOT EXISTS created_by UUID;
ALTER TABLE crm_complaint ADD COLUMN IF NOT EXISTS started_at TIMESTAMPTZ;
ALTER TABLE crm_complaint ADD COLUMN IF NOT EXISTS submitted_at TIMESTAMPTZ;
ALTER TABLE crm_complaint ADD COLUMN IF NOT EXISTS closed_by UUID;
ALTER TABLE crm_complaint ADD COLUMN IF NOT EXISTS escalated BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE crm_complaint ADD COLUMN IF NOT EXISTS escalation_level INTEGER NOT NULL DEFAULT 0;
ALTER TABLE crm_complaint ADD COLUMN IF NOT EXISTS escalated_at TIMESTAMPTZ;
ALTER TABLE crm_complaint ADD COLUMN IF NOT EXISTS escalation_reason VARCHAR(500);
ALTER TABLE crm_complaint ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT now();

ALTER TABLE crm_complaint ALTER COLUMN status SET DEFAULT 'OPEN';
-- 处理人统一使用系统用户账号（与权限、工作台一致），不再受基线 crm_staff 外键约束
ALTER TABLE crm_complaint DROP CONSTRAINT IF EXISTS crm_complaint_assignee_id_fkey;
ALTER TABLE crm_complaint DROP CONSTRAINT IF EXISTS ck_complaint_status;
ALTER TABLE crm_complaint ADD CONSTRAINT ck_complaint_status
  CHECK (status IN ('OPEN','ASSIGNED','PROCESSING','RECTIFYING','PENDING_REVIEW','CLOSED','CANCELLED'));
ALTER TABLE crm_complaint DROP CONSTRAINT IF EXISTS ck_complaint_type;
ALTER TABLE crm_complaint ADD CONSTRAINT ck_complaint_type
  CHECK (ticket_type IN ('complaint','praise','suggestion'));
ALTER TABLE crm_complaint DROP CONSTRAINT IF EXISTS ck_complaint_severity;
ALTER TABLE crm_complaint ADD CONSTRAINT ck_complaint_severity
  CHECK (severity IN ('low','normal','high','urgent'));
ALTER TABLE crm_complaint DROP CONSTRAINT IF EXISTS ck_complaint_source;
ALTER TABLE crm_complaint ADD CONSTRAINT ck_complaint_source
  CHECK (source IN ('manual','phone','online','h5','mini_program','his','letter'));

CREATE INDEX IF NOT EXISTS idx_complaint_assignee ON crm_complaint(assignee_id);
CREATE INDEX IF NOT EXISTS idx_complaint_customer ON crm_complaint(customer_id);
CREATE INDEX IF NOT EXISTS idx_complaint_escalation
  ON crm_complaint(status, escalated_at) WHERE escalated;

-- 处理动作：每一次流转、联系、整改、反馈、复核都写入，构成完整时间线
CREATE TABLE IF NOT EXISTS crm_complaint_action (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  ticket_id UUID NOT NULL REFERENCES crm_complaint(id),
  action_type VARCHAR(40) NOT NULL,
  from_status VARCHAR(24),
  to_status VARCHAR(24),
  content VARCHAR(2000),
  operator_id UUID,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_complaint_action_ticket ON crm_complaint_action(ticket_id, created_at);

CREATE TABLE IF NOT EXISTS crm_complaint_rectification (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  ticket_id UUID NOT NULL REFERENCES crm_complaint(id),
  content VARCHAR(2000) NOT NULL,
  responsible_org_id UUID,
  due_at TIMESTAMPTZ,
  status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
  submitted_at TIMESTAMPTZ,
  verified_at TIMESTAMPTZ,
  verified_by UUID,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT ck_complaint_rectification_status
    CHECK (status IN ('PENDING','SUBMITTED','VERIFIED'))
);
CREATE INDEX IF NOT EXISTS idx_complaint_rectification_ticket
  ON crm_complaint_rectification(ticket_id, created_at);

CREATE TABLE IF NOT EXISTS crm_complaint_feedback (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  ticket_id UUID NOT NULL REFERENCES crm_complaint(id),
  rating INTEGER NOT NULL,
  satisfied BOOLEAN NOT NULL DEFAULT false,
  comment VARCHAR(1000),
  channel VARCHAR(24) NOT NULL DEFAULT 'manual',
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT ck_complaint_feedback_rating CHECK (rating >= 1 AND rating <= 5)
);
CREATE INDEX IF NOT EXISTS idx_complaint_feedback_ticket ON crm_complaint_feedback(ticket_id, created_at);

CREATE TABLE IF NOT EXISTS crm_complaint_review (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  ticket_id UUID NOT NULL REFERENCES crm_complaint(id),
  reviewer_id UUID NOT NULL,
  decision VARCHAR(20) NOT NULL,
  comment VARCHAR(1000),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT ck_complaint_review_decision CHECK (decision IN ('PASS','REJECT'))
);
CREATE INDEX IF NOT EXISTS idx_complaint_review_ticket ON crm_complaint_review(ticket_id, created_at);

-- CRM-014 权限基线：查看、处理、分派、结案审核、管理分权
INSERT INTO sys_permission (permission_code, permission_name, module, permission_type, description, sort_order)
VALUES
  ('crm:complaint:read', '查看投诉表扬工单', '投诉表扬中心', 'api', '查看工单、处理动作、整改、反馈与复核记录', 560),
  ('crm:complaint:write', '投诉表扬处理与反馈', '投诉表扬中心', 'action', '创建工单、登记处理动作、整改、患者反馈与提交复核', 570),
  ('crm:complaint:assign', '投诉表扬分派', '投诉表扬中心', 'action', '分派与转派工单、设置责任部门与处理时限', 580),
  ('crm:complaint:review', '投诉表扬结案审核', '投诉表扬中心', 'action', '复核并结案或退回工单，审核人不得为当前处理人', 590),
  ('crm:complaint:manage', '投诉表扬升级与取消', '投诉表扬中心', 'action', '手动升级、取消工单与触发超时升级扫描', 600)
ON CONFLICT (permission_code) DO NOTHING;

INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r
CROSS JOIN sys_permission p
WHERE r.role_code = 'SUPER_ADMIN'
  AND p.permission_code LIKE 'crm:%'
ON CONFLICT DO NOTHING;
