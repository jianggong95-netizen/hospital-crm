-- =====================================================================
-- CRM-010 上门服务
-- 说明：新增上门服务目录、客户服务地址、上门服务订单、执行记录、
--       异常上报与人员资质。目录须经 draft→pending_review→published
--       审核后方可下单；派单与接单校验人员资质有效性；
--       服务地址联系电话仅保存密文、检索哈希与脱敏值，不落明文。
-- =====================================================================

CREATE SEQUENCE IF NOT EXISTS crm_home_visit_order_no_seq START WITH 100000 INCREMENT BY 1;

CREATE TABLE IF NOT EXISTS crm_home_service (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  service_code VARCHAR(32) NOT NULL,
  service_name VARCHAR(100) NOT NULL,
  category VARCHAR(20) NOT NULL,
  required_qual_type VARCHAR(50),
  duration_minutes INTEGER NOT NULL,
  price NUMERIC(12,2) NOT NULL DEFAULT 0,
  status VARCHAR(20) NOT NULL DEFAULT 'draft',
  description VARCHAR(1000),
  review_reason VARCHAR(500),
  reviewed_by VARCHAR(100),
  reviewed_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT uq_home_service_code UNIQUE (service_code),
  CONSTRAINT ck_home_service_category CHECK (category IN ('medical','non_medical')),
  CONSTRAINT ck_home_service_duration CHECK (duration_minutes > 0),
  CONSTRAINT ck_home_service_price CHECK (price >= 0),
  CONSTRAINT ck_home_service_status CHECK (status IN ('draft','pending_review','published','rejected','disabled'))
);
CREATE INDEX IF NOT EXISTS idx_home_service_status ON crm_home_service(status);
CREATE INDEX IF NOT EXISTS idx_home_service_category ON crm_home_service(category);

CREATE TABLE IF NOT EXISTS crm_home_address (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  customer_id UUID NOT NULL,
  address VARCHAR(300) NOT NULL,
  contact_name VARCHAR(100) NOT NULL,
  contact_phone_encrypted TEXT,
  contact_phone_hash VARCHAR(128),
  contact_phone_masked VARCHAR(32),
  is_default BOOLEAN NOT NULL DEFAULT false,
  status VARCHAR(20) NOT NULL DEFAULT 'active',
  remark VARCHAR(500),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT ck_home_address_status CHECK (status IN ('active','disabled'))
);
CREATE INDEX IF NOT EXISTS idx_home_address_customer ON crm_home_address(customer_id);
-- 每个客户最多一个启用状态的默认地址
CREATE UNIQUE INDEX IF NOT EXISTS uq_home_address_default
  ON crm_home_address(customer_id) WHERE is_default AND status = 'active';

CREATE TABLE IF NOT EXISTS crm_home_visit_order (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  order_no VARCHAR(32) NOT NULL,
  service_code VARCHAR(32) NOT NULL,
  customer_id UUID NOT NULL,
  address_id UUID NOT NULL,
  scheduled_date DATE NOT NULL,
  start_time TIME NOT NULL,
  end_time TIME NOT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
  staff_user_id UUID,
  reviewed_by VARCHAR(100),
  reviewed_at TIMESTAMPTZ,
  review_reason VARCHAR(500),
  assigned_at TIMESTAMPTZ,
  accepted_at TIMESTAMPTZ,
  actual_start_at TIMESTAMPTZ,
  actual_end_at TIMESTAMPTZ,
  duration_minutes INTEGER,
  cancel_reason VARCHAR(500),
  remark VARCHAR(1000),
  version INTEGER NOT NULL DEFAULT 0,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT uq_home_visit_order_no UNIQUE (order_no),
  CONSTRAINT ck_home_visit_order_time CHECK (end_time > start_time),
  CONSTRAINT ck_home_visit_order_status CHECK (status IN ('PENDING','APPROVED','REJECTED','ASSIGNED','ACCEPTED','IN_PROGRESS','COMPLETED','CANCELLED'))
);
CREATE INDEX IF NOT EXISTS idx_home_visit_order_status ON crm_home_visit_order(status);
CREATE INDEX IF NOT EXISTS idx_home_visit_order_service ON crm_home_visit_order(service_code);
CREATE INDEX IF NOT EXISTS idx_home_visit_order_customer ON crm_home_visit_order(customer_id);
CREATE INDEX IF NOT EXISTS idx_home_visit_order_date ON crm_home_visit_order(scheduled_date);
CREATE INDEX IF NOT EXISTS idx_home_visit_order_staff ON crm_home_visit_order(staff_user_id);

CREATE TABLE IF NOT EXISTS crm_home_visit_execution (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  order_id UUID NOT NULL REFERENCES crm_home_visit_order(id) ON DELETE CASCADE,
  visit_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  content VARCHAR(1000) NOT NULL,
  recorded_by VARCHAR(100),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_home_visit_execution_order ON crm_home_visit_execution(order_id);

CREATE TABLE IF NOT EXISTS crm_home_visit_exception (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  order_id UUID NOT NULL REFERENCES crm_home_visit_order(id) ON DELETE CASCADE,
  level VARCHAR(10) NOT NULL,
  category VARCHAR(50) NOT NULL,
  description VARCHAR(1000) NOT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'OPEN',
  escalated_by VARCHAR(100),
  escalated_at TIMESTAMPTZ,
  escalate_reason VARCHAR(500),
  resolved_by VARCHAR(100),
  resolved_at TIMESTAMPTZ,
  resolution VARCHAR(1000),
  reported_by VARCHAR(100),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT ck_home_exception_level CHECK (level IN ('low','medium','high','critical')),
  CONSTRAINT ck_home_exception_category CHECK (category IN ('vital_sign','equipment','patient_condition','staff_safety','other')),
  CONSTRAINT ck_home_exception_status CHECK (status IN ('OPEN','ESCALATED','RESOLVED'))
);
CREATE INDEX IF NOT EXISTS idx_home_exception_order ON crm_home_visit_exception(order_id);
CREATE INDEX IF NOT EXISTS idx_home_exception_status ON crm_home_visit_exception(status);

CREATE TABLE IF NOT EXISTS crm_home_staff_qualification (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id UUID NOT NULL,
  qual_type VARCHAR(50) NOT NULL,
  cert_no VARCHAR(64) NOT NULL,
  issued_at DATE NOT NULL,
  expires_at DATE,
  status VARCHAR(20) NOT NULL DEFAULT 'active',
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT uq_home_staff_qual UNIQUE (user_id, qual_type, cert_no),
  CONSTRAINT ck_home_staff_qual_type CHECK (qual_type IN ('nurse','caregiver','health_manager','doctor','other')),
  CONSTRAINT ck_home_staff_qual_status CHECK (status IN ('active','revoked'))
);
CREATE INDEX IF NOT EXISTS idx_home_staff_qual_user ON crm_home_staff_qualification(user_id);

-- 种子：三个已发布的上门服务项目
INSERT INTO crm_home_service (service_code, service_name, category, required_qual_type, duration_minutes, price, status, description)
VALUES
  ('HOME_INJECTION', '居家注射', 'medical', 'nurse', 30, 80.00, 'published', '由持证护士上门执行皮下与肌肉注射'),
  ('HOME_DRESSING', '居家换药', 'medical', 'nurse', 45, 120.00, 'published', '由持证护士上门进行伤口清创与换药'),
  ('HOME_CLEANING', '居家保洁陪同', 'non_medical', NULL, 120, 150.00, 'published', '上门保洁与生活照护陪同，无医疗资质要求')
ON CONFLICT (service_code) DO NOTHING;

-- CRM-010 权限基线
INSERT INTO sys_permission (permission_code, permission_name, module, permission_type, description, sort_order)
VALUES
  ('crm:homevisit:read', '查看上门服务目录地址与订单', '上门服务', 'api', '查看上门服务项目、服务地址、人员资质与订单', 450),
  ('crm:homevisit:write', '上门服务建档派单与执行记录', '上门服务', 'action', '维护服务项目与服务地址、创建订单、派单接单与登记执行记录', 460),
  ('crm:homevisit:manage', '上门服务审核与异常处置', '上门服务', 'action', '审核服务项目与订单、启停服务、升级与处置异常上报', 470)
ON CONFLICT (permission_code) DO NOTHING;

INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r
CROSS JOIN sys_permission p
WHERE r.role_code = 'SUPER_ADMIN'
  AND p.permission_code LIKE 'crm:%'
ON CONFLICT DO NOTHING;
