-- CRM-016 AI 知识库与数字人
-- 知识库分类、文档、切片、版本、审核留痕与 AI 问答追溯。

CREATE TABLE IF NOT EXISTS crm_kb_category (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  code VARCHAR(64) NOT NULL UNIQUE,
  name VARCHAR(120) NOT NULL,
  sort_order INTEGER NOT NULL DEFAULT 100,
  status VARCHAR(16) NOT NULL DEFAULT 'active',
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT ck_kb_category_status CHECK (status IN ('active', 'disabled'))
);

CREATE TABLE IF NOT EXISTS crm_kb_document (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  doc_no VARCHAR(40) NOT NULL UNIQUE,
  title VARCHAR(200) NOT NULL,
  category_id UUID REFERENCES crm_kb_category(id),
  summary VARCHAR(500),
  source_type VARCHAR(16) NOT NULL DEFAULT 'manual',
  file_name VARCHAR(255),
  file_size BIGINT,
  file_type VARCHAR(16),
  status VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
  current_version INTEGER NOT NULL DEFAULT 0,
  owner_id UUID,
  published_at TIMESTAMPTZ,
  offline_at TIMESTAMPTZ,
  offline_reason VARCHAR(500),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT ck_kb_document_source CHECK (source_type IN ('manual', 'file')),
  CONSTRAINT ck_kb_document_status CHECK (status IN ('DRAFT', 'REVIEWING', 'PUBLISHED', 'OFFLINE'))
);
CREATE INDEX IF NOT EXISTS idx_kb_document_list ON crm_kb_document(status, updated_at);
CREATE INDEX IF NOT EXISTS idx_kb_document_category ON crm_kb_document(category_id);

CREATE SEQUENCE IF NOT EXISTS crm_kb_doc_no_seq START WITH 700000 INCREMENT BY 1;

-- 切片：doc_version = current_version 为已发布版本，current_version + 1 为草稿版本
CREATE TABLE IF NOT EXISTS crm_kb_chunk (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  document_id UUID NOT NULL REFERENCES crm_kb_document(id),
  doc_version INTEGER NOT NULL,
  chunk_index INTEGER NOT NULL,
  content TEXT NOT NULL,
  char_count INTEGER NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT uq_kb_chunk UNIQUE (document_id, doc_version, chunk_index)
);
CREATE INDEX IF NOT EXISTS idx_kb_chunk_document ON crm_kb_chunk(document_id, doc_version);

CREATE TABLE IF NOT EXISTS crm_kb_version (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  document_id UUID NOT NULL REFERENCES crm_kb_document(id),
  version_no INTEGER NOT NULL,
  title VARCHAR(200) NOT NULL,
  summary VARCHAR(500),
  chunk_count INTEGER NOT NULL DEFAULT 0,
  change_note VARCHAR(500),
  created_by UUID,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT uq_kb_version UNIQUE (document_id, version_no)
);

CREATE TABLE IF NOT EXISTS crm_kb_review (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  document_id UUID NOT NULL REFERENCES crm_kb_document(id),
  version_no INTEGER NOT NULL,
  action VARCHAR(16) NOT NULL,
  reviewer_id UUID,
  comment VARCHAR(500),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT ck_kb_review_action CHECK (action IN ('SUBMIT', 'APPROVE', 'REJECT', 'OFFLINE'))
);
CREATE INDEX IF NOT EXISTS idx_kb_review_document ON crm_kb_review(document_id, created_at);

-- AI 问答追溯：答案、置信度与引用来源，低置信度自动转人工留痕
CREATE TABLE IF NOT EXISTS crm_ai_query_log (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  question TEXT NOT NULL,
  answer TEXT NOT NULL,
  confidence NUMERIC(5,4),
  refs JSONB NOT NULL DEFAULT '[]'::jsonb,
  scene VARCHAR(16) NOT NULL DEFAULT 'chat',
  session_id UUID,
  needs_handoff BOOLEAN NOT NULL DEFAULT false,
  asked_by UUID,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT ck_ai_query_scene CHECK (scene IN ('chat', 'followup'))
);
CREATE INDEX IF NOT EXISTS idx_ai_query_log_time ON crm_ai_query_log(created_at);

-- CRM-016 权限基线：查看、维护与审核分权
INSERT INTO sys_permission (permission_code, permission_name, module, permission_type, description, sort_order)
VALUES
  ('crm:knowledge:read', '查看AI知识库', '知识库与数字人', 'api', '查看分类、文档、问答记录与执行 RAG 检索', 640),
  ('crm:knowledge:write', '维护知识库', '知识库与数字人', 'action', '上传解析、编辑切片、提交审核', 650),
  ('crm:knowledge:review', '知识库审核与下架', '知识库与数字人', 'action', '审核通过、驳回与失效知识下架', 660)
ON CONFLICT (permission_code) DO NOTHING;

INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r
CROSS JOIN sys_permission p
WHERE r.role_code = 'SUPER_ADMIN'
  AND p.permission_code LIKE 'crm:knowledge:%'
ON CONFLICT DO NOTHING;
