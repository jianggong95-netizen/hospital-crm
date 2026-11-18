-- CRM-015 在线客服与人工工作台
-- 在基线 crm_chat_session / crm_chat_message 之上扩展队列、接管、转接与历史能力。

ALTER TABLE crm_chat_session
  ADD COLUMN IF NOT EXISTS priority VARCHAR(16) NOT NULL DEFAULT 'normal',
  ADD COLUMN IF NOT EXISTS queue_entered_at TIMESTAMPTZ,
  ADD COLUMN IF NOT EXISTS claimed_at TIMESTAMPTZ,
  ADD COLUMN IF NOT EXISTS last_message_at TIMESTAMPTZ,
  ADD COLUMN IF NOT EXISTS ai_summary TEXT,
  ADD COLUMN IF NOT EXISTS authorization_scope VARCHAR(200) NOT NULL DEFAULT 'basic',
  ADD COLUMN IF NOT EXISTS closed_by UUID,
  ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT now();

-- 客服使用系统账号，移除基线 crm_staff 外键后改为关联 sys_user
ALTER TABLE crm_chat_session DROP CONSTRAINT IF EXISTS crm_chat_session_assigned_staff_id_fkey;
ALTER TABLE crm_chat_session DROP CONSTRAINT IF EXISTS fk_chat_session_assignee;
ALTER TABLE crm_chat_session
  ADD CONSTRAINT fk_chat_session_assignee FOREIGN KEY (assigned_staff_id) REFERENCES sys_user(id);

ALTER TABLE crm_chat_session ALTER COLUMN status SET DEFAULT 'AI';

ALTER TABLE crm_chat_session DROP CONSTRAINT IF EXISTS ck_chat_session_status;
ALTER TABLE crm_chat_session DROP CONSTRAINT IF EXISTS ck_chat_session_handler;
ALTER TABLE crm_chat_session DROP CONSTRAINT IF EXISTS ck_chat_session_priority;
ALTER TABLE crm_chat_session
  ADD CONSTRAINT ck_chat_session_status CHECK (status IN ('AI', 'QUEUED', 'ACTIVE', 'CLOSED'));
ALTER TABLE crm_chat_session
  ADD CONSTRAINT ck_chat_session_handler CHECK (current_handler_type IN ('ai', 'human'));
ALTER TABLE crm_chat_session
  ADD CONSTRAINT ck_chat_session_priority CHECK (priority IN ('low', 'normal', 'high', 'urgent'));

CREATE INDEX IF NOT EXISTS idx_chat_session_history ON crm_chat_session(status, last_message_at);
CREATE INDEX IF NOT EXISTS idx_chat_session_queue ON crm_chat_session(status, queue_entered_at);

CREATE SEQUENCE IF NOT EXISTS crm_chat_session_no_seq START WITH 500000 INCREMENT BY 1;

-- 消息幂等：同一会话内客户端消息号唯一，避免渠道重试重复入库
ALTER TABLE crm_chat_message ADD COLUMN IF NOT EXISTS client_message_id VARCHAR(64);
CREATE UNIQUE INDEX IF NOT EXISTS uq_chat_message_client
  ON crm_chat_message(session_id, client_message_id)
  WHERE client_message_id IS NOT NULL;

-- 转人工记录：首次确认、二次自动排队、直接转人工与客服转接均留痕
CREATE TABLE IF NOT EXISTS crm_chat_handoff (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  session_id UUID NOT NULL REFERENCES crm_chat_session(id),
  request_count INTEGER NOT NULL DEFAULT 1,
  reason VARCHAR(32) NOT NULL,
  mode VARCHAR(20) NOT NULL,
  status VARCHAR(24) NOT NULL DEFAULT 'QUEUED',
  from_staff_id UUID,
  to_staff_id UUID,
  note VARCHAR(500),
  requested_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  accepted_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT ck_chat_handoff_mode
    CHECK (mode IN ('CONFIRM', 'SECOND', 'DIRECT', 'TRANSFER')),
  CONSTRAINT ck_chat_handoff_status
    CHECK (status IN ('PENDING_CONFIRM', 'QUEUED', 'ACCEPTED', 'TRANSFERRED', 'CANCELLED'))
);
CREATE INDEX IF NOT EXISTS idx_chat_handoff_session ON crm_chat_handoff(session_id, created_at);

-- 会话事件：状态流转与操作留痕，支撑队列准确性与会话历史
CREATE TABLE IF NOT EXISTS crm_chat_event (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  session_id UUID NOT NULL REFERENCES crm_chat_session(id),
  event_type VARCHAR(32) NOT NULL,
  from_status VARCHAR(24),
  to_status VARCHAR(24),
  operator_id UUID,
  note VARCHAR(500),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_chat_event_session ON crm_chat_event(session_id, created_at);

-- CRM-015 权限基线：查看、接待处理、管理与全程历史分权
INSERT INTO sys_permission (permission_code, permission_name, module, permission_type, description, sort_order)
VALUES
  ('crm:chat:read', '查看客服会话', '在线客服', 'api', '查看排队会话、本人会话与消息记录', 610),
  ('crm:chat:handle', '客服接待处理', '在线客服', 'action', '创建会话、收发消息、请求转人工、接管与结束会话', 620),
  ('crm:chat:manage', '客服管理与转接', '在线客服', 'action', '查看全部会话历史、转接他人会话与强制结束会话', 630)
ON CONFLICT (permission_code) DO NOTHING;

INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r
CROSS JOIN sys_permission p
WHERE r.role_code = 'SUPER_ADMIN'
  AND p.permission_code LIKE 'crm:chat:%'
ON CONFLICT DO NOTHING;
