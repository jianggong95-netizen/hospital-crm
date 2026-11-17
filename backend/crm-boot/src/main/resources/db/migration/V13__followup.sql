-- =====================================================================
-- CRM-013 随访中心
-- 说明：在基线 crm_followup_task 之上补充分类、模板、计划与随访记录；
--       计划按触发事件生成任务，同一计划同一事件键只生成一次；
--       随访答案按模板风险规则评估，高风险自动进入人工队列（ESCALATED）。
-- =====================================================================

CREATE SEQUENCE IF NOT EXISTS crm_followup_task_no_seq START WITH 100000 INCREMENT BY 1;
CREATE SEQUENCE IF NOT EXISTS crm_followup_plan_no_seq START WITH 100010 INCREMENT BY 1;

CREATE TABLE IF NOT EXISTS crm_followup_category (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  category_code VARCHAR(40) NOT NULL,
  category_name VARCHAR(100) NOT NULL,
  description VARCHAR(500),
  status VARCHAR(20) NOT NULL DEFAULT 'active',
  sort_no INTEGER NOT NULL DEFAULT 0,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT uq_followup_category_code UNIQUE (category_code),
  CONSTRAINT ck_followup_category_status CHECK (status IN ('active','disabled'))
);

CREATE TABLE IF NOT EXISTS crm_followup_template (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  template_code VARCHAR(40) NOT NULL,
  category_code VARCHAR(40) NOT NULL,
  template_name VARCHAR(120) NOT NULL,
  channel VARCHAR(24) NOT NULL DEFAULT 'phone',
  questions JSONB NOT NULL DEFAULT '[]'::jsonb,
  risk_rules JSONB NOT NULL DEFAULT '{}'::jsonb,
  version INTEGER NOT NULL DEFAULT 1,
  status VARCHAR(20) NOT NULL DEFAULT 'active',
  description VARCHAR(500),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT uq_followup_template_code UNIQUE (template_code),
  CONSTRAINT ck_followup_template_channel CHECK (channel IN ('phone','sms','mini_program','h5','ai','manual')),
  CONSTRAINT ck_followup_template_status CHECK (status IN ('active','disabled'))
);
CREATE INDEX IF NOT EXISTS idx_followup_template_category ON crm_followup_template(category_code);

CREATE TABLE IF NOT EXISTS crm_followup_plan (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  plan_no VARCHAR(40) NOT NULL,
  plan_name VARCHAR(120) NOT NULL,
  category_code VARCHAR(40) NOT NULL,
  template_code VARCHAR(40) NOT NULL,
  trigger_type VARCHAR(40) NOT NULL,
  offset_days INTEGER NOT NULL DEFAULT 0,
  channel VARCHAR(24) NOT NULL DEFAULT 'phone',
  default_assignee_id UUID,
  status VARCHAR(20) NOT NULL DEFAULT 'active',
  description VARCHAR(500),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT uq_followup_plan_no UNIQUE (plan_no),
  CONSTRAINT ck_followup_plan_trigger CHECK (trigger_type IN ('APPOINTMENT_COMPLETED','SERVICE_COMPLETED','ORDER_PAID','MANUAL')),
  CONSTRAINT ck_followup_plan_offset CHECK (offset_days >= 0 AND offset_days <= 365),
  CONSTRAINT ck_followup_plan_channel CHECK (channel IN ('phone','sms','mini_program','h5','ai','manual')),
  CONSTRAINT ck_followup_plan_status CHECK (status IN ('active','disabled'))
);
CREATE INDEX IF NOT EXISTS idx_followup_plan_trigger ON crm_followup_plan(status, trigger_type);

-- 扩展基线任务表：关联计划/模板、触发事件键、风险等级与升级信息
ALTER TABLE crm_followup_task ADD COLUMN IF NOT EXISTS plan_id UUID;
ALTER TABLE crm_followup_task ADD COLUMN IF NOT EXISTS template_code VARCHAR(40);
ALTER TABLE crm_followup_task ADD COLUMN IF NOT EXISTS trigger_type VARCHAR(40);
ALTER TABLE crm_followup_task ADD COLUMN IF NOT EXISTS trigger_event_key VARCHAR(120);
ALTER TABLE crm_followup_task ADD COLUMN IF NOT EXISTS risk_level VARCHAR(20);
ALTER TABLE crm_followup_task ADD COLUMN IF NOT EXISTS assigned_at TIMESTAMPTZ;
ALTER TABLE crm_followup_task ADD COLUMN IF NOT EXISTS started_at TIMESTAMPTZ;
ALTER TABLE crm_followup_task ADD COLUMN IF NOT EXISTS escalated_at TIMESTAMPTZ;
ALTER TABLE crm_followup_task ADD COLUMN IF NOT EXISTS escalation_reason VARCHAR(500);
ALTER TABLE crm_followup_task ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT now();

ALTER TABLE crm_followup_task ALTER COLUMN status SET DEFAULT 'PENDING';
-- 随访人统一使用系统用户账号（与权限、工作台一致），不再受基线 crm_staff 外键约束
ALTER TABLE crm_followup_task DROP CONSTRAINT IF EXISTS crm_followup_task_assignee_id_fkey;
ALTER TABLE crm_followup_task DROP CONSTRAINT IF EXISTS ck_followup_task_status;
ALTER TABLE crm_followup_task ADD CONSTRAINT ck_followup_task_status
  CHECK (status IN ('PENDING','IN_PROGRESS','COMPLETED','ESCALATED','CANCELLED'));
ALTER TABLE crm_followup_task DROP CONSTRAINT IF EXISTS ck_followup_task_risk;
ALTER TABLE crm_followup_task ADD CONSTRAINT ck_followup_task_risk
  CHECK (risk_level IS NULL OR risk_level IN ('LOW','MEDIUM','HIGH'));

-- 同一计划同一触发事件只允许生成一个任务，从数据库层保证重复事件不重复创建
CREATE UNIQUE INDEX IF NOT EXISTS uq_followup_event
  ON crm_followup_task(plan_id, trigger_event_key)
  WHERE trigger_event_key IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_followup_customer ON crm_followup_task(customer_id);
CREATE INDEX IF NOT EXISTS idx_followup_assignee ON crm_followup_task(assignee_id);
CREATE INDEX IF NOT EXISTS idx_followup_escalation
  ON crm_followup_task(status, escalated_at) WHERE escalation_required;

CREATE TABLE IF NOT EXISTS crm_followup_record (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  task_id UUID NOT NULL REFERENCES crm_followup_task(id),
  template_code VARCHAR(40),
  answers JSONB NOT NULL DEFAULT '{}'::jsonb,
  risk_level VARCHAR(20) NOT NULL DEFAULT 'LOW',
  result_code VARCHAR(40),
  remark VARCHAR(1000),
  operator_id UUID,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT uq_followup_record_task UNIQUE (task_id),
  CONSTRAINT ck_followup_record_risk CHECK (risk_level IN ('LOW','MEDIUM','HIGH'))
);
CREATE INDEX IF NOT EXISTS idx_followup_record_task ON crm_followup_record(task_id);

-- 种子：随访分类
INSERT INTO crm_followup_category (category_code, category_name, description, sort_no)
VALUES
  ('POST_DISCHARGE', '出院随访', '出院患者恢复情况与用药依从性随访', 10),
  ('POST_OP', '术后随访', '手术后伤口与康复情况随访', 20),
  ('CHRONIC', '慢病随访', '高血压、糖尿病等慢病定期随访', 30),
  ('SERVICE', '服务满意度', '陪诊、上门等服务完成后的满意度回访', 40),
  ('HIGH_RISK', '高风险专病', '需要重点关注的高风险患者随访', 50)
ON CONFLICT (category_code) DO NOTHING;

-- 种子：随访模板（问题与风险规则）
INSERT INTO crm_followup_template (template_code, category_code, template_name, channel, questions, risk_rules, description)
VALUES
  ('TPL_DISCHARGE_7D', 'POST_DISCHARGE', '出院 7 日随访', 'phone',
   '[{"code":"condition","label":"恢复情况","options":["良好","一般","加重"]},{"code":"medication","label":"是否按时用药","options":["是","否"]},{"code":"readmission","label":"是否再次就诊","options":["否","是"]}]'::jsonb,
   '{"highRisk":[{"questionCode":"condition","values":["加重"]},{"questionCode":"readmission","values":["是"]}],"mediumRisk":[{"questionCode":"medication","values":["否"]}],"requiresHuman":true}'::jsonb,
   '出院后第 7 天电话随访'),
  ('TPL_POST_OP_3D', 'POST_OP', '术后 3 日随访', 'phone',
   '[{"code":"wound","label":"伤口情况","options":["正常","渗液","红肿"]},{"code":"pain","label":"疼痛程度","options":["无","轻度","中度","重度"]}]'::jsonb,
   '{"highRisk":[{"questionCode":"wound","values":["渗液","红肿"]},{"questionCode":"pain","values":["重度"]}],"mediumRisk":[{"questionCode":"pain","values":["中度"]}],"requiresHuman":true}'::jsonb,
   '术后第 3 天电话随访伤口与疼痛'),
  ('TPL_SERVICE_SATISFACTION', 'SERVICE', '服务满意度回访', 'h5',
   '[{"code":"satisfaction","label":"整体满意度","options":["满意","一般","不满意"]},{"code":"complaint","label":"是否有投诉","options":["否","是"]}]'::jsonb,
   '{"highRisk":[{"questionCode":"complaint","values":["是"]}],"mediumRisk":[{"questionCode":"satisfaction","values":["一般"]}],"requiresHuman":true}'::jsonb,
   '服务完成后通过 H5 收集满意度'),
  ('TPL_CHRONIC_30D', 'CHRONIC', '慢病月度随访', 'phone',
   '[{"code":"bloodPressure","label":"血压控制","options":["正常","偏高","偏低"]},{"code":"medication","label":"是否规律服药","options":["是","否"]}]'::jsonb,
   '{"highRisk":[{"questionCode":"bloodPressure","values":["偏高","偏低"]}],"mediumRisk":[{"questionCode":"medication","values":["否"]}],"requiresHuman":true}'::jsonb,
   '慢病患者每月电话随访')
ON CONFLICT (template_code) DO NOTHING;

-- 种子：随访计划（按事件触发）
INSERT INTO crm_followup_plan (plan_no, plan_name, category_code, template_code, trigger_type, offset_days, channel, description)
VALUES
  ('FP100001', '服务完成后出院随访', 'POST_DISCHARGE', 'TPL_DISCHARGE_7D', 'SERVICE_COMPLETED', 1, 'phone', '服务完成后次日创建出院随访任务'),
  ('FP100002', '服务完成后术后随访', 'POST_OP', 'TPL_POST_OP_3D', 'SERVICE_COMPLETED', 3, 'phone', '服务完成后第 3 天创建术后随访任务'),
  ('FP100003', '收费完成后满意度回访', 'SERVICE', 'TPL_SERVICE_SATISFACTION', 'ORDER_PAID', 3, 'h5', '订单支付后第 3 天创建满意度回访任务'),
  ('FP100004', '预约完成后慢病随访', 'CHRONIC', 'TPL_CHRONIC_30D', 'APPOINTMENT_COMPLETED', 7, 'phone', '预约完成 7 天后创建慢病随访任务')
ON CONFLICT (plan_no) DO NOTHING;

-- CRM-013 权限基线
INSERT INTO sys_permission (permission_code, permission_name, module, permission_type, description, sort_order)
VALUES
  ('crm:followup:read', '查看随访分类模板计划与任务', '随访中心', 'api', '查看随访分类、模板、计划、任务、记录与人工队列', 530),
  ('crm:followup:write', '随访任务分配与结果提交', '随访中心', 'action', '创建随访任务、分配随访人、开始随访与提交随访记录', 540),
  ('crm:followup:manage', '随访配置与异常升级处置', '随访中心', 'action', '维护分类/模板/计划、手动升级与处置人工队列任务', 550)
ON CONFLICT (permission_code) DO NOTHING;

INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r
CROSS JOIN sys_permission p
WHERE r.role_code = 'SUPER_ADMIN'
  AND p.permission_code LIKE 'crm:%'
ON CONFLICT DO NOTHING;
