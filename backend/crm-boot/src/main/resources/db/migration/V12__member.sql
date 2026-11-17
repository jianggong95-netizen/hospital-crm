-- CRM-012 会员、充值、次卡与套餐
-- 基线已有 crm_member_account / crm_member_ledger，本迁移补齐并发安全与账本快照字段，
-- 新增等级、套餐目录、次卡、扣次记录与共享授权表。

ALTER TABLE crm_member_account
  ADD COLUMN IF NOT EXISTS frozen_balance NUMERIC(18,2) NOT NULL DEFAULT 0,
  ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT now();

CREATE UNIQUE INDEX IF NOT EXISTS uq_member_account_customer ON crm_member_account(customer_id);
CREATE INDEX IF NOT EXISTS idx_member_account_level ON crm_member_account(member_level, status);

ALTER TABLE crm_member_ledger
  ADD COLUMN IF NOT EXISTS balance_after NUMERIC(18,2),
  ADD COLUMN IF NOT EXISTS points_after BIGINT,
  ADD COLUMN IF NOT EXISTS biz_no VARCHAR(64);

CREATE INDEX IF NOT EXISTS idx_member_ledger_order ON crm_member_ledger(related_order_id);
CREATE INDEX IF NOT EXISTS idx_member_ledger_type ON crm_member_ledger(ledger_type, created_at DESC);

CREATE SEQUENCE IF NOT EXISTS crm_member_account_no_seq START 100000;
CREATE SEQUENCE IF NOT EXISTS crm_member_card_no_seq START 100000;

CREATE TABLE IF NOT EXISTS crm_member_level (
  level_code VARCHAR(32) PRIMARY KEY,
  level_name VARCHAR(64) NOT NULL,
  min_points BIGINT NOT NULL DEFAULT 0,
  discount_rate NUMERIC(5,4) NOT NULL DEFAULT 1.0000,
  sort_no INT NOT NULL DEFAULT 0,
  status VARCHAR(20) NOT NULL DEFAULT 'active',
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT ck_member_level_discount CHECK (discount_rate > 0 AND discount_rate <= 1)
);

INSERT INTO crm_member_level (level_code, level_name, min_points, discount_rate, sort_no) VALUES
  ('standard', '普通会员', 0, 1.0000, 10),
  ('silver', '银卡会员', 1000, 0.9800, 20),
  ('gold', '金卡会员', 5000, 0.9500, 30),
  ('platinum', '铂金会员', 20000, 0.9200, 40),
  ('diamond', '钻石会员', 50000, 0.8800, 50)
ON CONFLICT (level_code) DO NOTHING;

CREATE TABLE IF NOT EXISTS crm_member_package (
  package_code VARCHAR(32) PRIMARY KEY,
  package_name VARCHAR(120) NOT NULL,
  package_type VARCHAR(20) NOT NULL,
  price NUMERIC(18,2) NOT NULL,
  times INT,
  balance_amount NUMERIC(18,2),
  points_amount BIGINT,
  valid_days INT NOT NULL DEFAULT 365,
  status VARCHAR(20) NOT NULL DEFAULT 'active',
  description VARCHAR(500),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT ck_member_package_type CHECK (package_type IN ('TIMES', 'BALANCE', 'POINTS')),
  CONSTRAINT ck_member_package_price CHECK (price >= 0),
  CONSTRAINT ck_member_package_payload CHECK (
    (package_type = 'TIMES' AND times IS NOT NULL AND times > 0)
    OR (package_type = 'BALANCE' AND balance_amount IS NOT NULL AND balance_amount > 0)
    OR (package_type = 'POINTS' AND points_amount IS NOT NULL AND points_amount > 0)
  )
);

INSERT INTO crm_member_package (package_code, package_name, package_type, price, times, balance_amount, points_amount, valid_days, description) VALUES
  ('PKG_TIMES_10', '陪诊次卡 10 次', 'TIMES', 1800.00, 10, NULL, NULL, 365, '10 次陪诊服务，自购买日起一年有效'),
  ('PKG_BALANCE_2000', '储值 2000 元', 'BALANCE', 2000.00, NULL, 2000.00, NULL, 730, '余额可用于陪诊与上门服务，两年有效'),
  ('PKG_POINTS_5000', '积分礼包 5000 分', 'POINTS', 0.00, NULL, NULL, 5000, 365, '活动赠送积分，按会员规则使用')
ON CONFLICT (package_code) DO NOTHING;

CREATE TABLE IF NOT EXISTS crm_member_card (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  card_no VARCHAR(40) NOT NULL UNIQUE,
  customer_id UUID NOT NULL REFERENCES crm_customer(id),
  package_code VARCHAR(32) NOT NULL,
  package_name VARCHAR(120) NOT NULL,
  total_times INT NOT NULL,
  used_times INT NOT NULL DEFAULT 0,
  remaining_times INT NOT NULL,
  valid_from DATE NOT NULL,
  valid_to DATE NOT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
  source_order_id UUID,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT ck_member_card_times CHECK (total_times > 0 AND used_times >= 0 AND remaining_times >= 0),
  CONSTRAINT ck_member_card_status CHECK (status IN ('ACTIVE', 'EXHAUSTED', 'EXPIRED', 'VOID'))
);

CREATE INDEX IF NOT EXISTS idx_member_card_customer ON crm_member_card(customer_id, status);
CREATE INDEX IF NOT EXISTS idx_member_card_order ON crm_member_card(source_order_id);

CREATE TABLE IF NOT EXISTS crm_member_card_usage (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  card_id UUID NOT NULL REFERENCES crm_member_card(id),
  times INT NOT NULL,
  biz_type VARCHAR(32),
  biz_no VARCHAR(64),
  status VARCHAR(20) NOT NULL DEFAULT 'CONSUMED',
  remark VARCHAR(500),
  operator_id UUID,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  refunded_at TIMESTAMPTZ,
  CONSTRAINT ck_member_card_usage_times CHECK (times > 0),
  CONSTRAINT ck_member_card_usage_status CHECK (status IN ('CONSUMED', 'REFUNDED'))
);

CREATE INDEX IF NOT EXISTS idx_member_card_usage_card ON crm_member_card_usage(card_id, created_at DESC);

CREATE TABLE IF NOT EXISTS crm_member_share (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  owner_customer_id UUID NOT NULL REFERENCES crm_customer(id),
  beneficiary_customer_id UUID NOT NULL REFERENCES crm_customer(id),
  resource_type VARCHAR(20) NOT NULL,
  resource_id UUID,
  can_consume BOOLEAN NOT NULL DEFAULT TRUE,
  valid_from TIMESTAMPTZ NOT NULL DEFAULT now(),
  valid_to TIMESTAMPTZ,
  status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  revoked_at TIMESTAMPTZ,
  CONSTRAINT ck_member_share_type CHECK (resource_type IN ('BALANCE', 'POINTS', 'CARD')),
  CONSTRAINT ck_member_share_not_self CHECK (owner_customer_id <> beneficiary_customer_id)
);

CREATE INDEX IF NOT EXISTS idx_member_share_beneficiary ON crm_member_share(beneficiary_customer_id, status);
CREATE INDEX IF NOT EXISTS idx_member_share_owner ON crm_member_share(owner_customer_id, status);

INSERT INTO sys_permission (permission_code, permission_name, module, permission_type, description, sort_order)
VALUES
  ('crm:member:read', '查看会员账户与账本', '会员中心', 'api', '查看会员等级、账户余额、积分、次卡、套餐与共享授权', 500),
  ('crm:member:write', '会员充值消费与扣次', '会员中心', 'action', '开户、充值、消费、积分调整、购买套餐、扣次与返还、共享授权', 510),
  ('crm:member:manage', '会员套餐与权益管理', '会员中心', 'action', '维护套餐目录、启停套餐、退款权益返还', 520)
ON CONFLICT (permission_code) DO NOTHING;

INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r
CROSS JOIN sys_permission p
WHERE r.role_code = 'SUPER_ADMIN'
  AND p.permission_code LIKE 'crm:%'
ON CONFLICT (role_id, permission_id) DO NOTHING;
