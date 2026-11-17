-- =====================================================================
-- CRM-011 订单、收费、支付与退款
-- 说明：统一订单、订单项、支付单、渠道回调（幂等）、退款（含部分退款）、
--       对账与发票。基线 crm_order/crm_order_item/crm_payment/crm_refund
--       为空表，可安全增列与追加 CHECK 约束；CRM 收费仅记录资金流水并
--       全部落 CRM 主库，不写 HIS 收费表。
-- =====================================================================

CREATE SEQUENCE IF NOT EXISTS crm_order_no_seq START WITH 100000 INCREMENT BY 1;
CREATE SEQUENCE IF NOT EXISTS crm_payment_no_seq START WITH 100000 INCREMENT BY 1;
CREATE SEQUENCE IF NOT EXISTS crm_refund_no_seq START WITH 100000 INCREMENT BY 1;
CREATE SEQUENCE IF NOT EXISTS crm_invoice_no_seq START WITH 100000 INCREMENT BY 1;
CREATE SEQUENCE IF NOT EXISTS crm_recon_batch_no_seq START WITH 100000 INCREMENT BY 1;

-- 订单：取消原因与完成时间；状态机 CREATED→PAID→COMPLETED，
-- 未支付可 CANCELLED，支付后可 PARTIAL_REFUNDED/REFUNDED
ALTER TABLE crm_order ADD COLUMN IF NOT EXISTS cancel_reason VARCHAR(500);
ALTER TABLE crm_order ADD COLUMN IF NOT EXISTS completed_at TIMESTAMPTZ;

DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ck_order_status') THEN
    ALTER TABLE crm_order
      ADD CONSTRAINT ck_order_status CHECK (status IN ('CREATED','PAID','COMPLETED','CANCELLED','PARTIAL_REFUNDED','REFUNDED'));
  END IF;
END $$;

-- 支付：累计已退金额，保证 0 ≤ refunded_amount ≤ amount
ALTER TABLE crm_payment ADD COLUMN IF NOT EXISTS refunded_amount NUMERIC(18,2) NOT NULL DEFAULT 0;

DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ck_payment_status') THEN
    ALTER TABLE crm_payment
      ADD CONSTRAINT ck_payment_status CHECK (status IN ('PROCESSING','SUCCESS','FAILED','CLOSED'));
  END IF;
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ck_payment_refund') THEN
    ALTER TABLE crm_payment
      ADD CONSTRAINT ck_payment_refund CHECK (refunded_amount >= 0 AND refunded_amount <= amount);
  END IF;
END $$;

-- 退款：审核人/审核时间/驳回原因；状态机 PENDING→APPROVED→SUCCESS，
-- PENDING→REJECTED，执行失败为 FAILED
ALTER TABLE crm_refund ADD COLUMN IF NOT EXISTS reviewed_by VARCHAR(100);
ALTER TABLE crm_refund ADD COLUMN IF NOT EXISTS reviewed_at TIMESTAMPTZ;
ALTER TABLE crm_refund ADD COLUMN IF NOT EXISTS reject_reason VARCHAR(500);

DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ck_refund_status') THEN
    ALTER TABLE crm_refund
      ADD CONSTRAINT ck_refund_status CHECK (status IN ('PENDING','APPROVED','REJECTED','SUCCESS','FAILED'));
  END IF;
END $$;

-- 发票：申请后开票，可红冲；仅记录开票流水，票面文件以引用存储
CREATE TABLE IF NOT EXISTS crm_invoice (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  invoice_no VARCHAR(40) NOT NULL,
  order_id UUID NOT NULL REFERENCES crm_order(id),
  customer_id UUID NOT NULL,
  title VARCHAR(200) NOT NULL,
  tax_no VARCHAR(64),
  amount NUMERIC(18,2) NOT NULL,
  invoice_type VARCHAR(20) NOT NULL DEFAULT 'general',
  status VARCHAR(20) NOT NULL DEFAULT 'APPLIED',
  provider VARCHAR(32),
  file_ref VARCHAR(200),
  applied_by VARCHAR(100),
  applied_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  issued_at TIMESTAMPTZ,
  void_reason VARCHAR(500),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT uq_invoice_no UNIQUE (invoice_no),
  CONSTRAINT ck_invoice_amount CHECK (amount > 0),
  CONSTRAINT ck_invoice_type CHECK (invoice_type IN ('general','special')),
  CONSTRAINT ck_invoice_status CHECK (status IN ('APPLIED','ISSUED','VOID'))
);
CREATE INDEX IF NOT EXISTS idx_invoice_order ON crm_invoice(order_id);
CREATE INDEX IF NOT EXISTS idx_invoice_status ON crm_invoice(status);

-- 对账批次：按渠道与账期比对本地支付流水与渠道账单
CREATE TABLE IF NOT EXISTS crm_reconciliation_batch (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  batch_no VARCHAR(40) NOT NULL,
  channel VARCHAR(32) NOT NULL,
  reconcile_date DATE NOT NULL,
  local_count INTEGER NOT NULL DEFAULT 0,
  channel_count INTEGER NOT NULL DEFAULT 0,
  local_amount NUMERIC(18,2) NOT NULL DEFAULT 0,
  channel_amount NUMERIC(18,2) NOT NULL DEFAULT 0,
  diff_count INTEGER NOT NULL DEFAULT 0,
  status VARCHAR(20) NOT NULL DEFAULT 'RUNNING',
  run_by VARCHAR(100),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT uq_recon_batch_no UNIQUE (batch_no),
  CONSTRAINT ck_recon_batch_status CHECK (status IN ('RUNNING','MATCHED','DIFF','FAILED'))
);
CREATE INDEX IF NOT EXISTS idx_recon_channel_date ON crm_reconciliation_batch(channel, reconcile_date);

-- 对账差异明细：本地有渠道无 / 渠道有本地无 / 两边都有金额不等
CREATE TABLE IF NOT EXISTS crm_reconciliation_diff (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  batch_id UUID NOT NULL REFERENCES crm_reconciliation_batch(id) ON DELETE CASCADE,
  diff_type VARCHAR(20) NOT NULL,
  payment_no VARCHAR(40),
  channel_trade_no VARCHAR(100),
  local_amount NUMERIC(18,2),
  channel_amount NUMERIC(18,2),
  handled BOOLEAN NOT NULL DEFAULT false,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT ck_recon_diff_type CHECK (diff_type IN ('LOCAL_ONLY','CHANNEL_ONLY','AMOUNT_MISMATCH'))
);
CREATE INDEX IF NOT EXISTS idx_recon_diff_batch ON crm_reconciliation_diff(batch_id);

-- CRM-011 权限基线
INSERT INTO sys_permission (permission_code, permission_name, module, permission_type, description, sort_order)
VALUES
  ('crm:order:read', '查看订单与支付', '订单支付', 'api', '查看统一订单、订单项与支付退款流水', 480),
  ('crm:order:write', '创建订单与发起支付', '订单支付', 'action', '创建订单、取消与完成订单、发起支付与申请退款', 490),
  ('crm:payment:read', '查看支付退款与对账', '订单支付', 'api', '查看支付单、退款单与对账批次及差异明细', 500),
  ('crm:payment:manage', '退款审核与对账发票', '订单支付', 'action', '审核退款、执行渠道对账、开具与红冲发票', 510)
ON CONFLICT (permission_code) DO NOTHING;

INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r
CROSS JOIN sys_permission p
WHERE r.role_code = 'SUPER_ADMIN'
  AND p.permission_code LIKE 'crm:%'
ON CONFLICT DO NOTHING;
