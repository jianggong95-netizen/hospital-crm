-- CRM-018 商保中心
-- 保险公司、保单、商保案件、材料、邮件收发、状态事件与理赔对账。
-- 合规约束：公司联系人手机号仅存 HMAC 哈希与脱敏值，明文不落库；
-- 案件预计赔付（estimated_payout）与实际到账（actual_received）为两条独立字段，禁止互相覆盖。

CREATE TABLE IF NOT EXISTS crm_insurance_company (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  company_code VARCHAR(32) NOT NULL UNIQUE,
  company_name VARCHAR(120) NOT NULL,
  contact_name VARCHAR(60),
  contact_phone_hash VARCHAR(64),
  contact_phone_masked VARCHAR(32),
  contact_email VARCHAR(160),
  settlement_type VARCHAR(16) NOT NULL DEFAULT 'manual',
  status VARCHAR(16) NOT NULL DEFAULT 'active',
  remark VARCHAR(500),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT ck_insurance_company_settlement
    CHECK (settlement_type IN ('api', 'email', 'manual')),
  CONSTRAINT ck_insurance_company_status CHECK (status IN ('active', 'disabled'))
);

CREATE TABLE IF NOT EXISTS crm_insurance_policy (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  policy_no VARCHAR(64) NOT NULL UNIQUE,
  company_id UUID NOT NULL REFERENCES crm_insurance_company(id),
  customer_id UUID NOT NULL,
  holder_name VARCHAR(100),
  insured_name VARCHAR(100),
  relation VARCHAR(16) NOT NULL DEFAULT 'self',
  valid_from DATE,
  valid_to DATE,
  coverage_amount NUMERIC(14, 2) NOT NULL DEFAULT 0,
  status VARCHAR(16) NOT NULL DEFAULT 'active',
  remark VARCHAR(500),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT ck_insurance_policy_relation
    CHECK (relation IN ('self', 'spouse', 'child', 'parent', 'other')),
  CONSTRAINT ck_insurance_policy_status CHECK (status IN ('active', 'expired', 'terminated')),
  CONSTRAINT ck_insurance_policy_coverage CHECK (coverage_amount >= 0)
);
CREATE INDEX IF NOT EXISTS idx_insurance_policy_customer ON crm_insurance_policy(customer_id);
CREATE INDEX IF NOT EXISTS idx_insurance_policy_company_status ON crm_insurance_policy(company_id, status);

CREATE TABLE IF NOT EXISTS crm_insurance_case (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  case_no VARCHAR(40) NOT NULL UNIQUE,
  company_id UUID NOT NULL REFERENCES crm_insurance_company(id),
  policy_id UUID NOT NULL REFERENCES crm_insurance_policy(id),
  customer_id UUID NOT NULL,
  case_type VARCHAR(20) NOT NULL DEFAULT 'other',
  service_desc VARCHAR(500),
  state VARCHAR(24) NOT NULL DEFAULT 'registered',
  hospital_receivable NUMERIC(14, 2) NOT NULL DEFAULT 0,
  patient_pay NUMERIC(14, 2) NOT NULL DEFAULT 0,
  estimated_payout NUMERIC(14, 2) NOT NULL DEFAULT 0,
  actual_received NUMERIC(14, 2) NOT NULL DEFAULT 0,
  quotation_amount NUMERIC(14, 2),
  quotation_at TIMESTAMPTZ,
  preauth_no VARCHAR(64),
  preauth_amount NUMERIC(14, 2),
  preauth_at TIMESTAMPTZ,
  claim_no VARCHAR(64),
  claim_submitted_at TIMESTAMPTZ,
  claim_decision VARCHAR(24),
  approved_amount NUMERIC(14, 2),
  decision_reason VARCHAR(500),
  decided_at TIMESTAMPTZ,
  handler_user_id UUID,
  reconciled_at TIMESTAMPTZ,
  reconciliation_diff NUMERIC(14, 2),
  remark VARCHAR(500),
  created_by UUID,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT ck_insurance_case_type
    CHECK (case_type IN ('outpatient', 'inpatient', 'physical_exam', 'other')),
  CONSTRAINT ck_insurance_case_state CHECK (state IN (
    'registered', 'eligibility_check', 'quotation', 'pre_authorization', 'service_done',
    'claim_submitted', 'supplement_required', 'under_review', 'approved',
    'partially_approved', 'rejected', 'reconciled')),
  CONSTRAINT ck_insurance_case_decision
    CHECK (claim_decision IS NULL OR claim_decision IN ('approved', 'partially_approved', 'rejected')),
  CONSTRAINT ck_insurance_case_money CHECK (
    hospital_receivable >= 0 AND patient_pay >= 0 AND estimated_payout >= 0 AND actual_received >= 0)
);
CREATE INDEX IF NOT EXISTS idx_insurance_case_company_state ON crm_insurance_case(company_id, state);
CREATE INDEX IF NOT EXISTS idx_insurance_case_customer ON crm_insurance_case(customer_id);
CREATE INDEX IF NOT EXISTS idx_insurance_case_created ON crm_insurance_case(created_at DESC);
CREATE INDEX IF NOT EXISTS idx_insurance_case_policy ON crm_insurance_case(policy_id);

CREATE SEQUENCE IF NOT EXISTS crm_insurance_case_no_seq START WITH 800000 INCREMENT BY 1;

CREATE TABLE IF NOT EXISTS crm_insurance_material (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  case_id UUID NOT NULL REFERENCES crm_insurance_case(id),
  material_type VARCHAR(20) NOT NULL DEFAULT 'other',
  file_name VARCHAR(200) NOT NULL,
  file_key VARCHAR(255) NOT NULL,
  file_hash VARCHAR(64) NOT NULL,
  file_size BIGINT,
  source VARCHAR(16) NOT NULL DEFAULT 'manual',
  status VARCHAR(16) NOT NULL DEFAULT 'pending',
  note VARCHAR(500),
  uploaded_by UUID,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT ck_insurance_material_type
    CHECK (material_type IN ('diagnosis', 'invoice', 'detail', 'prescription', 'report', 'other')),
  CONSTRAINT ck_insurance_material_source CHECK (source IN ('upload', 'email', 'manual')),
  CONSTRAINT ck_insurance_material_status CHECK (status IN ('pending', 'valid', 'invalid'))
);
CREATE INDEX IF NOT EXISTS idx_insurance_material_case ON crm_insurance_material(case_id);

CREATE TABLE IF NOT EXISTS crm_insurance_email (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  case_id UUID NOT NULL REFERENCES crm_insurance_case(id),
  direction VARCHAR(16) NOT NULL,
  message_id VARCHAR(255),
  subject VARCHAR(300) NOT NULL,
  from_address VARCHAR(160),
  to_address VARCHAR(160),
  body_excerpt VARCHAR(1000),
  sent_at TIMESTAMPTZ,
  received_at TIMESTAMPTZ,
  attachment_count INTEGER NOT NULL DEFAULT 0,
  operator_user_id UUID,
  status VARCHAR(16) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT ck_insurance_email_direction CHECK (direction IN ('outbound', 'inbound')),
  CONSTRAINT ck_insurance_email_status CHECK (status IN ('sent', 'received', 'failed')),
  CONSTRAINT ck_insurance_email_attachment_count CHECK (attachment_count >= 0)
);

-- 邮件同步幂等：同一 message_id 仅入库一次（出站邮件可无 message_id）
CREATE UNIQUE INDEX IF NOT EXISTS uq_insurance_email_message
  ON crm_insurance_email(message_id)
  WHERE message_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_insurance_email_case ON crm_insurance_email(case_id, created_at);

CREATE TABLE IF NOT EXISTS crm_insurance_email_attachment (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  email_id UUID NOT NULL REFERENCES crm_insurance_email(id),
  file_name VARCHAR(200) NOT NULL,
  file_hash VARCHAR(64) NOT NULL,
  file_size BIGINT,
  material_id UUID,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_insurance_email_attachment_email
  ON crm_insurance_email_attachment(email_id);

CREATE TABLE IF NOT EXISTS crm_insurance_event (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  case_id UUID NOT NULL REFERENCES crm_insurance_case(id),
  event_type VARCHAR(32) NOT NULL,
  from_state VARCHAR(24),
  to_state VARCHAR(24),
  operator_user_id UUID,
  detail JSONB NOT NULL DEFAULT '{}'::jsonb,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_insurance_event_case ON crm_insurance_event(case_id, created_at);

CREATE TABLE IF NOT EXISTS crm_insurance_reconciliation (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  company_id UUID NOT NULL REFERENCES crm_insurance_company(id),
  period VARCHAR(7) NOT NULL,
  case_count INTEGER NOT NULL DEFAULT 0,
  receivable_amount NUMERIC(14, 2) NOT NULL DEFAULT 0,
  received_amount NUMERIC(14, 2) NOT NULL DEFAULT 0,
  difference_amount NUMERIC(14, 2) NOT NULL DEFAULT 0,
  status VARCHAR(16) NOT NULL DEFAULT 'pending',
  confirmed_by UUID,
  confirmed_at TIMESTAMPTZ,
  remark VARCHAR(500),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT uq_insurance_reconciliation_company_period UNIQUE (company_id, period),
  CONSTRAINT ck_insurance_reconciliation_status
    CHECK (status IN ('pending', 'matched', 'mismatch')),
  CONSTRAINT ck_insurance_reconciliation_amount CHECK (
    receivable_amount >= 0 AND received_amount >= 0 AND case_count >= 0)
);

-- CRM-018 权限基线：查看、主数据维护、案件流转、材料、邮件与对账分权
INSERT INTO sys_permission (permission_code, permission_name, module, permission_type, description, sort_order)
VALUES
  ('crm:insurance:read', '查看商保中心', '商保中心', 'api', '查看保险公司、保单、案件、材料、邮件与对账数据', 730),
  ('crm:insurance:manage', '维护商保主数据', '商保中心', 'action', '维护保险公司与保单信息、启停主数据', 740),
  ('crm:insurance:case:write', '商保案件处理', '商保中心', 'action', '创建商保案件、分派处理人与状态流转', 750),
  ('crm:insurance:material:write', '商保材料维护', '商保中心', 'action', '上传登记案件材料并校验材料有效性', 760),
  ('crm:insurance:email:write', '商保邮件登记', '商保中心', 'action', '登记商保往来邮件与附件哈希', 770),
  ('crm:insurance:reconcile', '商保理赔对账', '商保中心', 'action', '生成商保对账批次并确认对账结果', 780)
ON CONFLICT (permission_code) DO NOTHING;

INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r
CROSS JOIN sys_permission p
WHERE r.role_code = 'SUPER_ADMIN'
  AND p.permission_code LIKE 'crm:insurance:%'
ON CONFLICT DO NOTHING;
