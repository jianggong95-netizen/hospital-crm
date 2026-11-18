-- CRM-017 呼叫中心与短信
-- 通话记录、录音、短信模板与消息发送队列；手机号仅存 HMAC 哈希与脱敏值，明文不落库。

CREATE TABLE IF NOT EXISTS crm_call_record (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  call_no VARCHAR(40) NOT NULL UNIQUE,
  vendor_call_id VARCHAR(120),
  direction VARCHAR(16) NOT NULL,
  caller_hash VARCHAR(64),
  caller_masked VARCHAR(32),
  callee_hash VARCHAR(64),
  callee_masked VARCHAR(32),
  customer_id UUID,
  link_type VARCHAR(16) NOT NULL DEFAULT 'none',
  link_id UUID,
  link_no VARCHAR(40),
  queue_name VARCHAR(80),
  agent_user_id UUID,
  status VARCHAR(16) NOT NULL,
  started_at TIMESTAMPTZ NOT NULL,
  ended_at TIMESTAMPTZ,
  duration_seconds INTEGER,
  recording_id UUID,
  source VARCHAR(16) NOT NULL DEFAULT 'manual',
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT ck_call_record_direction CHECK (direction IN ('inbound', 'outbound')),
  CONSTRAINT ck_call_record_link_type CHECK (link_type IN ('customer', 'complaint', 'session', 'appointment', 'none')),
  CONSTRAINT ck_call_record_status CHECK (status IN ('connected', 'missed', 'transferred')),
  CONSTRAINT ck_call_record_source CHECK (source IN ('vendor', 'manual'))
);

-- 厂商同步幂等去重：同一厂商通话号只入库一次
CREATE UNIQUE INDEX IF NOT EXISTS uq_call_record_vendor
  ON crm_call_record(vendor_call_id)
  WHERE vendor_call_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_call_record_customer ON crm_call_record(customer_id, started_at DESC);
CREATE INDEX IF NOT EXISTS idx_call_record_started ON crm_call_record(started_at DESC);
CREATE INDEX IF NOT EXISTS idx_call_record_status ON crm_call_record(status);
CREATE INDEX IF NOT EXISTS idx_call_record_caller ON crm_call_record(caller_hash, started_at DESC);

CREATE SEQUENCE IF NOT EXISTS crm_call_no_seq START WITH 900000 INCREMENT BY 1;

CREATE TABLE IF NOT EXISTS crm_call_recording (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  recording_key VARCHAR(255) NOT NULL UNIQUE,
  storage_type VARCHAR(16) NOT NULL,
  duration_seconds INTEGER,
  file_size BIGINT,
  access_count INTEGER NOT NULL DEFAULT 0,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT ck_call_recording_storage CHECK (storage_type IN ('vendor', 'oss', 'local'))
);

CREATE TABLE IF NOT EXISTS crm_sms_template (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  code VARCHAR(64) NOT NULL UNIQUE,
  name VARCHAR(120) NOT NULL,
  channel VARCHAR(16) NOT NULL DEFAULT 'sms',
  content TEXT NOT NULL,
  variables JSONB NOT NULL DEFAULT '[]'::jsonb,
  status VARCHAR(16) NOT NULL DEFAULT 'active',
  remark VARCHAR(500),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT ck_sms_template_status CHECK (status IN ('active', 'disabled'))
);

-- 模板种子：验证码 / 预约提醒 / 随访通知
INSERT INTO crm_sms_template (code, name, content, variables, remark)
VALUES
  ('VERIFY_CODE', '验证码',
   '您的验证码是 {{code}}，5 分钟内有效，请勿泄露给他人。',
   '["code"]'::jsonb, '登录与身份核验验证码'),
  ('APPT_REMIND', '预约提醒',
   '尊敬的{{name}}，您预约的{{department}}就诊时间为{{appt_time}}，请提前 15 分钟到院取号。',
   '["name", "department", "appt_time"]'::jsonb, '就诊前 24 小时预约提醒'),
  ('FOLLOWUP_NOTICE', '随访通知',
   '{{name}}您好，我是{{hospital}}随访中心，提醒您按计划完成{{topic}}复查，如有不适请及时就诊。',
   '["name", "hospital", "topic"]'::jsonb, '出院后随访通知')
ON CONFLICT (code) DO NOTHING;

CREATE TABLE IF NOT EXISTS crm_message_outbox (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  msg_no VARCHAR(40) NOT NULL UNIQUE,
  biz_key VARCHAR(120),
  template_id UUID REFERENCES crm_sms_template(id),
  channel VARCHAR(16) NOT NULL,
  scene VARCHAR(64),
  recipient_hash VARCHAR(64) NOT NULL,
  recipient_masked VARCHAR(32),
  customer_id UUID,
  content TEXT NOT NULL,
  provider VARCHAR(32),
  status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
  retry_count INTEGER NOT NULL DEFAULT 0,
  max_retries INTEGER NOT NULL DEFAULT 3,
  next_retry_at TIMESTAMPTZ,
  last_error VARCHAR(500),
  sent_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT ck_message_outbox_channel CHECK (channel IN ('sms', 'wechat', 'voice')),
  CONSTRAINT ck_message_outbox_status
    CHECK (status IN ('PENDING', 'SENDING', 'SENT', 'FAILED', 'CANCELLED'))
);

-- 业务去重：同一 biz_key 只入队一次，避免上游重试重复发送
CREATE UNIQUE INDEX IF NOT EXISTS uq_message_outbox_biz
  ON crm_message_outbox(biz_key)
  WHERE biz_key IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_message_outbox_due ON crm_message_outbox(status, next_retry_at);
CREATE INDEX IF NOT EXISTS idx_message_outbox_recipient ON crm_message_outbox(recipient_hash);

CREATE SEQUENCE IF NOT EXISTS crm_msg_no_seq START WITH 910000 INCREMENT BY 1;

-- CRM-017 权限基线：通话、录音访问与短信模板、发送分权
INSERT INTO sys_permission (permission_code, permission_name, module, permission_type, description, sort_order)
VALUES
  ('crm:call:read', '查看通话记录与来电弹屏', '呼叫中心与短信', 'api', '查看通话记录列表、详情与来电弹屏信息', 670),
  ('crm:call:write', '维护通话记录与关联', '呼叫中心与短信', 'action', '录入通话记录并关联客户、投诉、会话或预约', 680),
  ('crm:call:recording:read', '录音访问授权', '呼叫中心与短信', 'action', '获取通话录音访问地址并留痕', 690),
  ('crm:sms:read', '查看短信模板与消息队列', '呼叫中心与短信', 'api', '查看短信模板与消息发送队列状态', 700),
  ('crm:sms:send', '发送短信消息', '呼叫中心与短信', 'action', '入队发送短信、重试与手工触发调度', 710),
  ('crm:sms:template:manage', '维护短信模板', '呼叫中心与短信', 'action', '创建、编辑、启停短信模板', 720)
ON CONFLICT (permission_code) DO NOTHING;

INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r
CROSS JOIN sys_permission p
WHERE r.role_code = 'SUPER_ADMIN'
  AND (p.permission_code LIKE 'crm:call:%' OR p.permission_code LIKE 'crm:sms:%')
ON CONFLICT DO NOTHING;
