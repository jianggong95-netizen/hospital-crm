-- Hospital CRM V1.0 核心数据库初始化脚本
-- PostgreSQL 16+
-- 注意：此为核心表基线；其余模块按 database/02-数据库设计说明.md 分任务补齐。
CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE IF NOT EXISTS crm_customer (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  customer_no VARCHAR(32) NOT NULL UNIQUE,
  name VARCHAR(100) NOT NULL,
  gender VARCHAR(16) NOT NULL DEFAULT 'unknown',
  birth_date DATE,
  phone_encrypted TEXT,
  phone_hash VARCHAR(128),
  phone_masked VARCHAR(32),
  id_no_encrypted TEXT,
  id_no_hash VARCHAR(128),
  id_no_masked VARCHAR(32),
  source_code VARCHAR(32),
  status VARCHAR(20) NOT NULL DEFAULT 'active',
  merged_into UUID REFERENCES crm_customer(id),
  notes TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT ck_customer_status CHECK (status IN ('active','merged','disabled'))
);
CREATE INDEX IF NOT EXISTS idx_customer_phone_hash ON crm_customer(phone_hash);
CREATE INDEX IF NOT EXISTS idx_customer_id_hash ON crm_customer(id_no_hash);
CREATE INDEX IF NOT EXISTS idx_customer_name ON crm_customer(name);

CREATE TABLE IF NOT EXISTS crm_customer_identity (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  customer_id UUID NOT NULL REFERENCES crm_customer(id),
  source_system VARCHAR(50) NOT NULL,
  identity_type VARCHAR(32) NOT NULL,
  identity_value_encrypted TEXT,
  identity_value_hash VARCHAR(128) NOT NULL,
  is_verified BOOLEAN NOT NULL DEFAULT false,
  valid_from TIMESTAMPTZ NOT NULL DEFAULT now(),
  valid_to TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_identity_lookup
  ON crm_customer_identity(source_system, identity_type, identity_value_hash);

CREATE TABLE IF NOT EXISTS crm_family (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  family_no VARCHAR(32) NOT NULL UNIQUE,
  family_name VARCHAR(100) NOT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'active',
  created_by UUID,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS crm_family_member (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  family_id UUID NOT NULL REFERENCES crm_family(id),
  customer_id UUID NOT NULL REFERENCES crm_customer(id),
  relationship_code VARCHAR(32) NOT NULL,
  is_admin BOOLEAN NOT NULL DEFAULT false,
  authorization_scope JSONB NOT NULL DEFAULT '{}'::jsonb,
  valid_from TIMESTAMPTZ NOT NULL DEFAULT now(),
  valid_to TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_family_member_family ON crm_family_member(family_id);
CREATE INDEX IF NOT EXISTS idx_family_member_customer ON crm_family_member(customer_id);

CREATE TABLE IF NOT EXISTS crm_customer_consent (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  customer_id UUID NOT NULL REFERENCES crm_customer(id),
  consent_type VARCHAR(50) NOT NULL,
  purpose_code VARCHAR(50) NOT NULL,
  status VARCHAR(20) NOT NULL,
  scope JSONB NOT NULL DEFAULT '{}'::jsonb,
  policy_version VARCHAR(50),
  obtained_at TIMESTAMPTZ,
  revoked_at TIMESTAMPTZ,
  evidence_file_id UUID,
  operator_id UUID,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_consent_customer ON crm_customer_consent(customer_id, consent_type);

CREATE TABLE IF NOT EXISTS crm_staff (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  staff_no VARCHAR(32) NOT NULL UNIQUE,
  name VARCHAR(100) NOT NULL,
  staff_type VARCHAR(32) NOT NULL,
  mobile_encrypted TEXT,
  mobile_masked VARCHAR(32),
  status VARCHAR(20) NOT NULL DEFAULT 'active',
  org_id UUID,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS crm_service_catalog (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  service_code VARCHAR(40) NOT NULL UNIQUE,
  service_name VARCHAR(120) NOT NULL,
  service_type VARCHAR(32) NOT NULL,
  description TEXT,
  base_price NUMERIC(18,2) NOT NULL DEFAULT 0,
  minimum_duration_minutes INTEGER,
  billing_rule JSONB NOT NULL DEFAULT '{}'::jsonb,
  requires_review BOOLEAN NOT NULL DEFAULT false,
  requires_qualification BOOLEAN NOT NULL DEFAULT false,
  status VARCHAR(20) NOT NULL DEFAULT 'active',
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS crm_staff_schedule (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  staff_id UUID NOT NULL REFERENCES crm_staff(id),
  start_at TIMESTAMPTZ NOT NULL,
  end_at TIMESTAMPTZ NOT NULL,
  schedule_status VARCHAR(20) NOT NULL DEFAULT 'available',
  service_area JSONB NOT NULL DEFAULT '{}'::jsonb,
  notes TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT ck_schedule_time CHECK (end_at > start_at)
);
CREATE INDEX IF NOT EXISTS idx_schedule_staff_time ON crm_staff_schedule(staff_id, start_at, end_at);

CREATE TABLE IF NOT EXISTS crm_order (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  order_no VARCHAR(40) NOT NULL UNIQUE,
  customer_id UUID NOT NULL REFERENCES crm_customer(id),
  family_id UUID REFERENCES crm_family(id),
  order_type VARCHAR(32) NOT NULL,
  status VARCHAR(24) NOT NULL,
  currency CHAR(3) NOT NULL DEFAULT 'CNY',
  total_amount NUMERIC(18,2) NOT NULL DEFAULT 0,
  discount_amount NUMERIC(18,2) NOT NULL DEFAULT 0,
  payable_amount NUMERIC(18,2) NOT NULL DEFAULT 0,
  paid_amount NUMERIC(18,2) NOT NULL DEFAULT 0,
  refunded_amount NUMERIC(18,2) NOT NULL DEFAULT 0,
  idempotency_key VARCHAR(100),
  source_channel VARCHAR(32),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT ck_order_amounts CHECK (
    total_amount >= 0 AND discount_amount >= 0 AND payable_amount >= 0
    AND paid_amount >= 0 AND refunded_amount >= 0
  )
);
CREATE INDEX IF NOT EXISTS idx_order_customer_time ON crm_order(customer_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_order_status_time ON crm_order(status, created_at DESC);
CREATE UNIQUE INDEX IF NOT EXISTS uq_order_idempotency
  ON crm_order(idempotency_key) WHERE idempotency_key IS NOT NULL;

CREATE TABLE IF NOT EXISTS crm_order_item (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  order_id UUID NOT NULL REFERENCES crm_order(id),
  item_code VARCHAR(40) NOT NULL,
  item_name VARCHAR(120) NOT NULL,
  quantity NUMERIC(12,3) NOT NULL DEFAULT 1,
  unit_price NUMERIC(18,2) NOT NULL DEFAULT 0,
  amount NUMERIC(18,2) NOT NULL DEFAULT 0,
  item_snapshot JSONB NOT NULL DEFAULT '{}'::jsonb
);

CREATE TABLE IF NOT EXISTS crm_payment (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  payment_no VARCHAR(40) NOT NULL UNIQUE,
  order_id UUID NOT NULL REFERENCES crm_order(id),
  channel VARCHAR(32) NOT NULL,
  channel_trade_no VARCHAR(100),
  amount NUMERIC(18,2) NOT NULL,
  status VARCHAR(20) NOT NULL,
  idempotency_key VARCHAR(100) NOT NULL UNIQUE,
  paid_at TIMESTAMPTZ,
  callback_payload_hash VARCHAR(128),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_payment_channel_trade
  ON crm_payment(channel, channel_trade_no) WHERE channel_trade_no IS NOT NULL;

CREATE TABLE IF NOT EXISTS crm_refund (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  refund_no VARCHAR(40) NOT NULL UNIQUE,
  payment_id UUID NOT NULL REFERENCES crm_payment(id),
  order_id UUID NOT NULL REFERENCES crm_order(id),
  amount NUMERIC(18,2) NOT NULL,
  reason TEXT,
  status VARCHAR(20) NOT NULL,
  channel_refund_no VARCHAR(100),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  completed_at TIMESTAMPTZ
);

CREATE TABLE IF NOT EXISTS crm_service_order (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  order_id UUID NOT NULL UNIQUE REFERENCES crm_order(id),
  service_catalog_id UUID NOT NULL REFERENCES crm_service_catalog(id),
  service_type VARCHAR(32) NOT NULL,
  requested_start_at TIMESTAMPTZ,
  requested_duration_minutes INTEGER,
  service_address_encrypted TEXT,
  service_address_masked VARCHAR(255),
  assigned_staff_id UUID REFERENCES crm_staff(id),
  service_status VARCHAR(24) NOT NULL,
  patient_requirements JSONB NOT NULL DEFAULT '{}'::jsonb,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_service_order_status ON crm_service_order(service_status, requested_start_at);

CREATE TABLE IF NOT EXISTS crm_service_execution (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  service_order_id UUID NOT NULL REFERENCES crm_service_order(id),
  staff_id UUID NOT NULL REFERENCES crm_staff(id),
  checkin_at TIMESTAMPTZ,
  started_at TIMESTAMPTZ,
  completed_at TIMESTAMPTZ,
  execution_notes TEXT,
  patient_confirmation_at TIMESTAMPTZ,
  exception_flag BOOLEAN NOT NULL DEFAULT false,
  exception_detail TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS crm_service_evaluation (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  service_order_id UUID NOT NULL REFERENCES crm_service_order(id),
  customer_id UUID NOT NULL REFERENCES crm_customer(id),
  rating SMALLINT NOT NULL,
  content TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT ck_evaluation_rating CHECK (rating BETWEEN 1 AND 5)
);

CREATE TABLE IF NOT EXISTS crm_followup_task (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  task_no VARCHAR(40) NOT NULL UNIQUE,
  customer_id UUID NOT NULL REFERENCES crm_customer(id),
  category_code VARCHAR(40) NOT NULL,
  channel VARCHAR(24) NOT NULL,
  assignee_id UUID REFERENCES crm_staff(id),
  planned_at TIMESTAMPTZ NOT NULL,
  status VARCHAR(24) NOT NULL DEFAULT 'pending',
  result_code VARCHAR(40),
  result_payload JSONB NOT NULL DEFAULT '{}'::jsonb,
  escalation_required BOOLEAN NOT NULL DEFAULT false,
  completed_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_followup_due ON crm_followup_task(status, planned_at);

CREATE TABLE IF NOT EXISTS crm_chat_session (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  session_no VARCHAR(40) NOT NULL UNIQUE,
  customer_id UUID REFERENCES crm_customer(id),
  channel VARCHAR(24) NOT NULL,
  current_handler_type VARCHAR(20) NOT NULL DEFAULT 'ai',
  assigned_staff_id UUID REFERENCES crm_staff(id),
  handoff_request_count INTEGER NOT NULL DEFAULT 0,
  status VARCHAR(20) NOT NULL DEFAULT 'open',
  started_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  ended_at TIMESTAMPTZ
);
CREATE INDEX IF NOT EXISTS idx_chat_queue ON crm_chat_session(status, current_handler_type, started_at);

CREATE TABLE IF NOT EXISTS crm_chat_message (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  session_id UUID NOT NULL REFERENCES crm_chat_session(id),
  sender_type VARCHAR(20) NOT NULL,
  sender_id UUID,
  content TEXT NOT NULL,
  content_type VARCHAR(20) NOT NULL DEFAULT 'text',
  ai_confidence NUMERIC(5,4),
  knowledge_refs JSONB NOT NULL DEFAULT '[]'::jsonb,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_chat_message_session_time ON crm_chat_message(session_id, created_at);

CREATE TABLE IF NOT EXISTS crm_complaint (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  ticket_no VARCHAR(40) NOT NULL UNIQUE,
  customer_id UUID NOT NULL REFERENCES crm_customer(id),
  ticket_type VARCHAR(20) NOT NULL,
  severity VARCHAR(20) NOT NULL,
  subject VARCHAR(200) NOT NULL,
  description TEXT NOT NULL,
  status VARCHAR(24) NOT NULL,
  responsible_org_id UUID,
  assignee_id UUID REFERENCES crm_staff(id),
  due_at TIMESTAMPTZ,
  closed_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_complaint_status_due ON crm_complaint(status, due_at);

CREATE TABLE IF NOT EXISTS crm_member_account (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  account_no VARCHAR(40) NOT NULL UNIQUE,
  customer_id UUID NOT NULL REFERENCES crm_customer(id),
  member_level VARCHAR(32) NOT NULL DEFAULT 'standard',
  balance NUMERIC(18,2) NOT NULL DEFAULT 0,
  points BIGINT NOT NULL DEFAULT 0,
  status VARCHAR(20) NOT NULL DEFAULT 'active',
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS crm_member_ledger (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  account_id UUID NOT NULL REFERENCES crm_member_account(id),
  ledger_type VARCHAR(24) NOT NULL,
  amount NUMERIC(18,2) NOT NULL DEFAULT 0,
  points_delta BIGINT NOT NULL DEFAULT 0,
  related_order_id UUID REFERENCES crm_order(id),
  reason TEXT,
  operator_id UUID,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_member_ledger_account_time ON crm_member_ledger(account_id, created_at DESC);

CREATE TABLE IF NOT EXISTS crm_his_sync_log (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  source_system VARCHAR(50) NOT NULL,
  operation VARCHAR(80) NOT NULL,
  request_trace_id VARCHAR(80),
  external_patient_id VARCHAR(100),
  status VARCHAR(20) NOT NULL,
  error_code VARCHAR(80),
  error_message TEXT,
  started_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  finished_at TIMESTAMPTZ
);
CREATE INDEX IF NOT EXISTS idx_his_sync_time ON crm_his_sync_log(started_at DESC);

CREATE TABLE IF NOT EXISTS sys_audit_log (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  actor_user_id UUID,
  action VARCHAR(80) NOT NULL,
  resource_type VARCHAR(80) NOT NULL,
  resource_id VARCHAR(100),
  patient_customer_id UUID,
  purpose_code VARCHAR(50),
  ip_address INET,
  user_agent TEXT,
  detail JSONB NOT NULL DEFAULT '{}'::jsonb,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_audit_actor_time ON sys_audit_log(actor_user_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_audit_resource ON sys_audit_log(resource_type, resource_id);
