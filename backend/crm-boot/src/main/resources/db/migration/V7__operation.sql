-- =====================================================================
-- CRM-007 客户运营、邀约与分诊
-- 说明：新增来源渠道、线索、联系记录、线索分派/合并/转化追溯、邀约与活动参与。
--       线索手机号/证件号沿用客户中心规则：仅保存密文、检索哈希与脱敏值。
-- =====================================================================

CREATE SEQUENCE IF NOT EXISTS crm_lead_no_seq START WITH 100000 INCREMENT BY 1;
CREATE SEQUENCE IF NOT EXISTS crm_invitation_no_seq START WITH 100000 INCREMENT BY 1;
CREATE SEQUENCE IF NOT EXISTS crm_activity_no_seq START WITH 100000 INCREMENT BY 1;

CREATE TABLE IF NOT EXISTS crm_source_channel (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  channel_code VARCHAR(64) NOT NULL UNIQUE,
  channel_name VARCHAR(100) NOT NULL,
  channel_type VARCHAR(32) NOT NULL DEFAULT 'other',
  status VARCHAR(20) NOT NULL DEFAULT 'active',
  remark VARCHAR(500),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT ck_channel_type CHECK (channel_type IN ('online','offline','referral','activity','his','other')),
  CONSTRAINT ck_channel_status CHECK (status IN ('active','disabled'))
);

INSERT INTO crm_source_channel (channel_code, channel_name, channel_type, remark)
VALUES
  ('MANUAL', '手工登记', 'other', '员工手工录入'),
  ('ONLINE', '线上渠道', 'online', '官网、公众号、小程序等'),
  ('PHONE', '电话咨询', 'offline', '电话咨询与呼入'),
  ('REFERRAL', '转介绍', 'referral', '客户或员工转介绍'),
  ('ACTIVITY', '活动获客', 'activity', '健康讲座、筛查与市场活动'),
  ('WALK_IN', '到院咨询', 'offline', '到院现场咨询'),
  ('HIS', 'HIS 导入', 'his', '由 HIS 同步的潜在客户')
ON CONFLICT (channel_code) DO NOTHING;

CREATE TABLE IF NOT EXISTS crm_lead (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  lead_no VARCHAR(32) NOT NULL UNIQUE,
  name VARCHAR(100) NOT NULL,
  gender VARCHAR(16) NOT NULL DEFAULT 'unknown',
  birth_date DATE,
  phone_encrypted TEXT,
  phone_hash VARCHAR(128),
  phone_masked VARCHAR(32),
  id_no_encrypted TEXT,
  id_no_hash VARCHAR(128),
  id_no_masked VARCHAR(32),
  source_channel_code VARCHAR(64) NOT NULL REFERENCES crm_source_channel(channel_code),
  status VARCHAR(20) NOT NULL DEFAULT 'NEW',
  owner_user_id UUID REFERENCES sys_user(id),
  org_id UUID REFERENCES sys_org(id),
  remark VARCHAR(1000),
  converted_customer_id UUID REFERENCES crm_customer(id),
  converted_at TIMESTAMPTZ,
  merged_into_lead_id UUID REFERENCES crm_lead(id),
  created_by UUID,
  updated_by UUID,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  version INTEGER NOT NULL DEFAULT 0,
  CONSTRAINT ck_lead_status CHECK (status IN ('NEW','CONTACTING','QUALIFIED','INVITED','CONVERTED','INVALID','MERGED')),
  CONSTRAINT ck_lead_gender CHECK (gender IN ('unknown','male','female'))
);
CREATE INDEX IF NOT EXISTS idx_lead_phone_hash ON crm_lead(phone_hash);
CREATE INDEX IF NOT EXISTS idx_lead_id_hash ON crm_lead(id_no_hash);
CREATE INDEX IF NOT EXISTS idx_lead_status_created ON crm_lead(status, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_lead_owner ON crm_lead(owner_user_id);
CREATE INDEX IF NOT EXISTS idx_lead_org ON crm_lead(org_id);
CREATE INDEX IF NOT EXISTS idx_lead_channel ON crm_lead(source_channel_code);

CREATE TABLE IF NOT EXISTS crm_lead_contact_record (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  lead_id UUID NOT NULL REFERENCES crm_lead(id) ON DELETE CASCADE,
  contact_type VARCHAR(32) NOT NULL,
  contacted_at TIMESTAMPTZ NOT NULL,
  contacted_by UUID,
  content VARCHAR(2000) NOT NULL,
  result_code VARCHAR(32) NOT NULL,
  next_follow_up_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT ck_lead_contact_type CHECK (contact_type IN ('phone','wechat','sms','visit','other')),
  CONSTRAINT ck_lead_contact_result CHECK (result_code IN ('CONNECTED','NO_ANSWER','REFUSED','INTERESTED','CONVERTED','OTHER'))
);
CREATE INDEX IF NOT EXISTS idx_lead_contact_lead ON crm_lead_contact_record(lead_id, contacted_at DESC);

CREATE TABLE IF NOT EXISTS crm_lead_assignment (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  lead_id UUID NOT NULL REFERENCES crm_lead(id) ON DELETE CASCADE,
  from_user_id UUID,
  to_user_id UUID NOT NULL REFERENCES sys_user(id),
  reason VARCHAR(500),
  assigned_by UUID,
  assigned_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_lead_assignment_lead ON crm_lead_assignment(lead_id, assigned_at DESC);

CREATE TABLE IF NOT EXISTS crm_lead_conversion (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  lead_id UUID NOT NULL UNIQUE REFERENCES crm_lead(id),
  customer_id UUID NOT NULL REFERENCES crm_customer(id),
  conversion_mode VARCHAR(32) NOT NULL,
  remark VARCHAR(500),
  converted_by UUID,
  converted_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT ck_lead_conversion_mode CHECK (conversion_mode IN ('NEW_CUSTOMER','EXISTING_CUSTOMER'))
);

CREATE TABLE IF NOT EXISTS crm_activity (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  activity_no VARCHAR(32) NOT NULL UNIQUE,
  activity_name VARCHAR(200) NOT NULL,
  activity_type VARCHAR(32) NOT NULL DEFAULT 'other',
  start_at TIMESTAMPTZ NOT NULL,
  end_at TIMESTAMPTZ NOT NULL,
  location VARCHAR(200),
  capacity INTEGER,
  status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
  description VARCHAR(2000),
  created_by UUID,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  version INTEGER NOT NULL DEFAULT 0,
  CONSTRAINT ck_activity_type CHECK (activity_type IN ('health_lecture','screening','charity','marketing','other')),
  CONSTRAINT ck_activity_status CHECK (status IN ('DRAFT','PUBLISHED','CLOSED','CANCELLED')),
  CONSTRAINT ck_activity_time CHECK (end_at > start_at),
  CONSTRAINT ck_activity_capacity CHECK (capacity IS NULL OR capacity >= 0)
);
CREATE INDEX IF NOT EXISTS idx_activity_start ON crm_activity(start_at DESC);
CREATE INDEX IF NOT EXISTS idx_activity_status ON crm_activity(status);

CREATE TABLE IF NOT EXISTS crm_activity_participant (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  activity_id UUID NOT NULL REFERENCES crm_activity(id) ON DELETE CASCADE,
  lead_id UUID REFERENCES crm_lead(id),
  customer_id UUID REFERENCES crm_customer(id),
  participant_name VARCHAR(100) NOT NULL,
  phone_encrypted TEXT,
  phone_hash VARCHAR(128),
  phone_masked VARCHAR(32),
  status VARCHAR(20) NOT NULL DEFAULT 'REGISTERED',
  registered_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  checked_in_at TIMESTAMPTZ,
  remark VARCHAR(500),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT ck_activity_participant_status CHECK (status IN ('REGISTERED','ATTENDED','CANCELLED','NO_SHOW'))
);
CREATE INDEX IF NOT EXISTS idx_activity_participant_activity ON crm_activity_participant(activity_id, registered_at DESC);
CREATE UNIQUE INDEX IF NOT EXISTS uq_activity_participant_phone
  ON crm_activity_participant(activity_id, phone_hash)
  WHERE phone_hash IS NOT NULL;

CREATE TABLE IF NOT EXISTS crm_invitation (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  invitation_no VARCHAR(32) NOT NULL UNIQUE,
  lead_id UUID REFERENCES crm_lead(id),
  customer_id UUID REFERENCES crm_customer(id),
  channel_code VARCHAR(64) NOT NULL REFERENCES crm_source_channel(channel_code),
  title VARCHAR(200) NOT NULL,
  planned_at TIMESTAMPTZ NOT NULL,
  owner_user_id UUID REFERENCES sys_user(id),
  org_id UUID REFERENCES sys_org(id),
  status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
  result VARCHAR(500),
  remark VARCHAR(1000),
  created_by UUID,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  version INTEGER NOT NULL DEFAULT 0,
  CONSTRAINT ck_invitation_status CHECK (status IN ('PENDING','CONTACTED','ACCEPTED','DECLINED','CANCELLED','EXPIRED')),
  CONSTRAINT ck_invitation_target CHECK (lead_id IS NOT NULL OR customer_id IS NOT NULL)
);
CREATE INDEX IF NOT EXISTS idx_invitation_status_planned ON crm_invitation(status, planned_at);
CREATE INDEX IF NOT EXISTS idx_invitation_owner ON crm_invitation(owner_user_id);
CREATE INDEX IF NOT EXISTS idx_invitation_lead ON crm_invitation(lead_id);
CREATE INDEX IF NOT EXISTS idx_invitation_customer ON crm_invitation(customer_id);

-- CRM-007 权限基线
INSERT INTO sys_permission (permission_code, permission_name, module, permission_type, description, sort_order)
VALUES
  ('crm:channel:read', '查看来源渠道', '客户运营', 'api', '查看来源渠道字典', 250),
  ('crm:channel:write', '管理来源渠道', '客户运营', 'action', '创建、启停来源渠道', 260),
  ('crm:lead:read', '查看线索', '客户运营', 'api', '查看线索列表、详情、联系记录、分派与转化记录', 270),
  ('crm:lead:write', '管理线索', '客户运营', 'action', '创建、编辑线索与维护联系记录', 280),
  ('crm:lead:convert', '线索转客户', '客户运营', 'action', '将线索转化为新客户或关联既有客户，并保留转化追溯', 290),
  ('crm:lead:assign', '线索分派', '客户运营', 'action', '将线索分派或转派给其他员工', 300),
  ('crm:invitation:read', '查看邀约', '客户运营', 'api', '查看邀约任务列表与详情', 310),
  ('crm:invitation:write', '管理邀约', '客户运营', 'action', '创建邀约并推进邀约状态', 320),
  ('crm:activity:read', '查看活动', '客户运营', 'api', '查看活动与参与人', 330),
  ('crm:activity:write', '管理活动', '客户运营', 'action', '创建活动、登记参与人与签到', 340)
ON CONFLICT (permission_code) DO NOTHING;

INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r
CROSS JOIN sys_permission p
WHERE r.role_code = 'SUPER_ADMIN'
  AND p.permission_code LIKE 'crm:%'
ON CONFLICT DO NOTHING;
