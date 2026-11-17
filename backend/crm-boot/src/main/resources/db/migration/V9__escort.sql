-- =====================================================================
-- CRM-009 陪诊服务
-- 说明：新增陪诊师档案、资质、排班、陪诊订单与评价。
--       派单校验资质有效性与排班覆盖，订单状态机由服务层保证；
--       陪诊师与订单联系电话仅保存密文、检索哈希与脱敏值，不落明文。
-- =====================================================================

CREATE SEQUENCE IF NOT EXISTS crm_escort_no_seq START WITH 100000 INCREMENT BY 1;
CREATE SEQUENCE IF NOT EXISTS crm_escort_order_no_seq START WITH 100000 INCREMENT BY 1;

CREATE TABLE IF NOT EXISTS crm_escort (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  escort_no VARCHAR(32) NOT NULL UNIQUE,
  name VARCHAR(100) NOT NULL,
  gender VARCHAR(10) NOT NULL DEFAULT 'unknown',
  phone_encrypted TEXT,
  phone_hash VARCHAR(128),
  phone_masked VARCHAR(32),
  service_areas VARCHAR(500),
  status VARCHAR(20) NOT NULL DEFAULT 'active',
  remark VARCHAR(1000),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT ck_escort_gender CHECK (gender IN ('unknown','male','female')),
  CONSTRAINT ck_escort_status CHECK (status IN ('active','disabled'))
);
CREATE INDEX IF NOT EXISTS idx_escort_status ON crm_escort(status);
CREATE INDEX IF NOT EXISTS idx_escort_phone_hash ON crm_escort(phone_hash);

CREATE TABLE IF NOT EXISTS crm_escort_qualification (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  escort_id UUID NOT NULL REFERENCES crm_escort(id) ON DELETE CASCADE,
  qual_type VARCHAR(50) NOT NULL,
  cert_no VARCHAR(64) NOT NULL,
  issued_at DATE NOT NULL,
  expires_at DATE,
  status VARCHAR(20) NOT NULL DEFAULT 'active',
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT ck_escort_qual_type CHECK (qual_type IN ('nurse','caregiver','health_manager','other')),
  CONSTRAINT ck_escort_qual_status CHECK (status IN ('active','revoked')),
  CONSTRAINT uq_escort_qual UNIQUE (escort_id, qual_type, cert_no)
);
CREATE INDEX IF NOT EXISTS idx_escort_qual_escort ON crm_escort_qualification(escort_id);

CREATE TABLE IF NOT EXISTS crm_escort_schedule (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  escort_id UUID NOT NULL REFERENCES crm_escort(id) ON DELETE CASCADE,
  work_date DATE NOT NULL,
  start_time TIME NOT NULL,
  end_time TIME NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT ck_schedule_time CHECK (end_time > start_time)
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_schedule_escort_date_start ON crm_escort_schedule(escort_id, work_date, start_time);
CREATE INDEX IF NOT EXISTS idx_schedule_escort_date ON crm_escort_schedule(escort_id, work_date);

CREATE TABLE IF NOT EXISTS crm_escort_order (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  order_no VARCHAR(32) NOT NULL UNIQUE,
  customer_id UUID NOT NULL,
  appointment_id UUID,
  escort_id UUID REFERENCES crm_escort(id),
  service_date DATE NOT NULL,
  start_time TIME NOT NULL,
  end_time TIME NOT NULL,
  area VARCHAR(100) NOT NULL,
  contact_phone_encrypted TEXT,
  contact_phone_hash VARCHAR(128),
  contact_phone_masked VARCHAR(32),
  status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
  assigned_at TIMESTAMPTZ,
  accepted_at TIMESTAMPTZ,
  actual_start_at TIMESTAMPTZ,
  actual_end_at TIMESTAMPTZ,
  duration_minutes INTEGER,
  reject_reason VARCHAR(500),
  cancel_reason VARCHAR(500),
  remark VARCHAR(1000),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  version INTEGER NOT NULL DEFAULT 0,
  CONSTRAINT ck_escort_order_time CHECK (end_time > start_time),
  CONSTRAINT ck_escort_order_status CHECK (status IN ('PENDING','ASSIGNED','ACCEPTED','IN_PROGRESS','COMPLETED','CANCELLED'))
);
CREATE INDEX IF NOT EXISTS idx_escort_order_status ON crm_escort_order(status);
CREATE INDEX IF NOT EXISTS idx_escort_order_escort_date ON crm_escort_order(escort_id, service_date);
CREATE INDEX IF NOT EXISTS idx_escort_order_customer ON crm_escort_order(customer_id);
CREATE INDEX IF NOT EXISTS idx_escort_order_date ON crm_escort_order(service_date);

CREATE TABLE IF NOT EXISTS crm_escort_review (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  order_id UUID NOT NULL REFERENCES crm_escort_order(id),
  escort_id UUID NOT NULL REFERENCES crm_escort(id),
  rating INTEGER NOT NULL,
  content VARCHAR(1000),
  created_by VARCHAR(100),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT ck_escort_review_rating CHECK (rating BETWEEN 1 AND 5)
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_review_order ON crm_escort_review(order_id);
CREATE INDEX IF NOT EXISTS idx_escort_review_escort ON crm_escort_review(escort_id);

-- CRM-009 权限基线
INSERT INTO sys_permission (permission_code, permission_name, module, permission_type, description, sort_order)
VALUES
  ('crm:escort:read', '查看陪诊师与排班', '陪诊服务', 'api', '查看陪诊师档案、资质、排班与评价', 400),
  ('crm:escort:write', '管理陪诊师资质与排班', '陪诊服务', 'action', '维护陪诊师档案、资质登记停用与排班安排', 410),
  ('crm:escortorder:read', '查看陪诊订单', '陪诊服务', 'api', '查看陪诊订单列表与详情', 420),
  ('crm:escortorder:write', '创建与派单接单', '陪诊服务', 'action', '创建陪诊订单、派单、接单与拒绝', 430),
  ('crm:escortorder:manage', '陪诊履约与评价管理', '陪诊服务', 'action', '开始与完成服务、取消订单与客户评价', 440)
ON CONFLICT (permission_code) DO NOTHING;

INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r
CROSS JOIN sys_permission p
WHERE r.role_code = 'SUPER_ADMIN'
  AND p.permission_code LIKE 'crm:%'
ON CONFLICT DO NOTHING;
