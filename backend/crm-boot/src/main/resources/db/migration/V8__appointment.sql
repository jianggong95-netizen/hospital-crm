-- =====================================================================
-- CRM-008 预约中心
-- 说明：新增预约类型、服务时段、预约、提醒与爽约记录。
--       时段容量采用 booked_count 条件更新防止并发超卖；
--       取消/改期按预约类型提前小时数校验；提醒失败记录重试次数与下次重试时间。
-- =====================================================================

CREATE SEQUENCE IF NOT EXISTS crm_appointment_no_seq START WITH 100000 INCREMENT BY 1;

CREATE TABLE IF NOT EXISTS crm_appointment_type (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  type_code VARCHAR(64) NOT NULL UNIQUE,
  type_name VARCHAR(100) NOT NULL,
  duration_minutes INTEGER NOT NULL DEFAULT 30,
  cancel_cutoff_hours INTEGER NOT NULL DEFAULT 2,
  status VARCHAR(20) NOT NULL DEFAULT 'active',
  remark VARCHAR(500),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT ck_appt_type_status CHECK (status IN ('active','disabled')),
  CONSTRAINT ck_appt_type_duration CHECK (duration_minutes BETWEEN 1 AND 480),
  CONSTRAINT ck_appt_type_cutoff CHECK (cancel_cutoff_hours BETWEEN 0 AND 720)
);

INSERT INTO crm_appointment_type (type_code, type_name, duration_minutes, cancel_cutoff_hours, remark)
VALUES
  ('OUTPATIENT', '门诊预约', 30, 2, '普通门诊号源预约'),
  ('HEALTH_CHECK', '体检预约', 120, 24, '健康体检套餐预约'),
  ('FOLLOW_UP', '复诊随访', 30, 4, '医生要求的复诊与随访到院'),
  ('PHONE_CONSULT', '电话咨询', 15, 1, '电话或线上咨询服务'),
  ('SPECIAL', '专病专案', 60, 24, '专病团队与多学科会诊')
ON CONFLICT (type_code) DO NOTHING;

CREATE TABLE IF NOT EXISTS crm_service_slot (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  type_code VARCHAR(64) NOT NULL REFERENCES crm_appointment_type(type_code),
  slot_date DATE NOT NULL,
  start_time TIME NOT NULL,
  end_time TIME NOT NULL,
  capacity INTEGER NOT NULL,
  booked_count INTEGER NOT NULL DEFAULT 0,
  status VARCHAR(20) NOT NULL DEFAULT 'OPEN',
  remark VARCHAR(500),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  version INTEGER NOT NULL DEFAULT 0,
  CONSTRAINT ck_slot_capacity CHECK (capacity >= 1),
  CONSTRAINT ck_slot_booked CHECK (booked_count >= 0 AND booked_count <= capacity),
  CONSTRAINT ck_slot_time CHECK (end_time > start_time),
  CONSTRAINT ck_slot_status CHECK (status IN ('OPEN','CLOSED'))
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_slot_type_date_start ON crm_service_slot(type_code, slot_date, start_time);
CREATE INDEX IF NOT EXISTS idx_slot_date_type ON crm_service_slot(slot_date, type_code);
CREATE INDEX IF NOT EXISTS idx_slot_status ON crm_service_slot(status);

CREATE TABLE IF NOT EXISTS crm_appointment (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  appointment_no VARCHAR(32) NOT NULL UNIQUE,
  customer_id UUID NOT NULL REFERENCES crm_customer(id),
  type_code VARCHAR(64) NOT NULL REFERENCES crm_appointment_type(type_code),
  slot_id UUID NOT NULL REFERENCES crm_service_slot(id),
  status VARCHAR(20) NOT NULL DEFAULT 'BOOKED',
  contact_phone_encrypted TEXT,
  contact_phone_hash VARCHAR(128),
  contact_phone_masked VARCHAR(32),
  remark VARCHAR(1000),
  cancelled_at TIMESTAMPTZ,
  cancel_reason VARCHAR(500),
  completed_at TIMESTAMPTZ,
  no_show_at TIMESTAMPTZ,
  org_id UUID REFERENCES sys_org(id),
  created_by UUID,
  updated_by UUID,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  version INTEGER NOT NULL DEFAULT 0,
  CONSTRAINT ck_appointment_status CHECK (status IN ('BOOKED','CONFIRMED','COMPLETED','CANCELLED','NO_SHOW'))
);
CREATE INDEX IF NOT EXISTS idx_appointment_customer ON crm_appointment(customer_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_appointment_slot ON crm_appointment(slot_id);
CREATE INDEX IF NOT EXISTS idx_appointment_status ON crm_appointment(status);
CREATE UNIQUE INDEX IF NOT EXISTS uq_appointment_active_slot
  ON crm_appointment(customer_id, slot_id)
  WHERE status IN ('BOOKED','CONFIRMED');

CREATE TABLE IF NOT EXISTS crm_appointment_reminder (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  appointment_id UUID NOT NULL REFERENCES crm_appointment(id) ON DELETE CASCADE,
  channel VARCHAR(20) NOT NULL DEFAULT 'sms',
  planned_at TIMESTAMPTZ NOT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
  retry_count INTEGER NOT NULL DEFAULT 0,
  max_retries INTEGER NOT NULL DEFAULT 3,
  next_retry_at TIMESTAMPTZ,
  last_error VARCHAR(500),
  sent_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT ck_reminder_channel CHECK (channel IN ('sms','wechat','phone')),
  CONSTRAINT ck_reminder_status CHECK (status IN ('PENDING','SENT','FAILED','CANCELLED')),
  CONSTRAINT ck_reminder_retry CHECK (retry_count >= 0 AND max_retries BETWEEN 1 AND 10)
);
CREATE INDEX IF NOT EXISTS idx_reminder_status_planned ON crm_appointment_reminder(status, planned_at);
CREATE INDEX IF NOT EXISTS idx_reminder_appointment ON crm_appointment_reminder(appointment_id);

CREATE TABLE IF NOT EXISTS crm_no_show_record (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  appointment_id UUID NOT NULL UNIQUE REFERENCES crm_appointment(id),
  customer_id UUID NOT NULL REFERENCES crm_customer(id),
  appointment_at TIMESTAMPTZ NOT NULL,
  reason VARCHAR(500),
  recorded_by UUID,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_no_show_customer ON crm_no_show_record(customer_id, appointment_at DESC);

-- CRM-008 权限基线
INSERT INTO sys_permission (permission_code, permission_name, module, permission_type, description, sort_order)
VALUES
  ('crm:appttype:read', '查看预约类型与时段', '预约中心', 'api', '查看预约类型与服务时段配置', 350),
  ('crm:appttype:write', '管理预约类型与时段', '预约中心', 'action', '维护预约类型、发布与关闭服务时段', 360),
  ('crm:appointment:read', '查看预约', '预约中心', 'api', '查看预约列表、详情、提醒与爽约记录', 370),
  ('crm:appointment:write', '管理预约', '预约中心', 'action', '创建预约、改期与取消', 380),
  ('crm:appointment:manage', '预约履约管理', '预约中心', 'action', '确认、完成、标记爽约与提醒重试', 390)
ON CONFLICT (permission_code) DO NOTHING;

INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r
CROSS JOIN sys_permission p
WHERE r.role_code = 'SUPER_ADMIN'
  AND p.permission_code LIKE 'crm:%'
ON CONFLICT DO NOTHING;
