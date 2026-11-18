-- CRM-019 运营分析
-- 指标定义、报表定义、导出申请与审批。
-- 约束：每项指标必须带口径说明（caliber_description NOT NULL）；
--       导出 CSV 仅含脱敏列，审批时生成并保存 file_hash 以便追溯；
--       报表结果均记录数据时间范围与生成时间（应用层 generatedAt）。
-- updated_at 由应用层 @PreUpdate 维护（与 V18 商保模块做法一致）。

CREATE TABLE IF NOT EXISTS crm_metric_definition (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  metric_code VARCHAR(64) NOT NULL UNIQUE,
  metric_name VARCHAR(120) NOT NULL,
  category VARCHAR(40) NOT NULL,
  caliber_description TEXT NOT NULL,
  data_source VARCHAR(120) NOT NULL,
  unit VARCHAR(20),
  decimal_places INTEGER NOT NULL DEFAULT 0,
  status VARCHAR(20) NOT NULL DEFAULT 'enabled',
  remark VARCHAR(500),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT ck_metric_category CHECK (category IN
    ('customer', 'operation', 'appointment', 'revenue', 'followup',
     'complaint', 'callcenter', 'insurance')),
  CONSTRAINT ck_metric_status CHECK (status IN ('enabled', 'disabled'))
);

CREATE TABLE IF NOT EXISTS crm_report_definition (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  report_code VARCHAR(64) NOT NULL UNIQUE,
  report_name VARCHAR(120) NOT NULL,
  description VARCHAR(500),
  report_type VARCHAR(32) NOT NULL,
  metric_codes TEXT,
  status VARCHAR(20) NOT NULL DEFAULT 'enabled',
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT ck_report_type CHECK (report_type IN ('metric_summary', 'customer_detail')),
  CONSTRAINT ck_report_status CHECK (status IN ('enabled', 'disabled'))
);

CREATE TABLE IF NOT EXISTS crm_export_request (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  report_code VARCHAR(64) NOT NULL,
  requester_user_id UUID NOT NULL,
  data_from DATE NOT NULL,
  data_to DATE NOT NULL,
  reason VARCHAR(500) NOT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'pending',
  approver_user_id UUID,
  approved_at TIMESTAMPTZ,
  reject_reason VARCHAR(500),
  row_count INTEGER,
  file_hash VARCHAR(64),
  csv_content TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT ck_export_status CHECK (status IN ('pending', 'approved', 'rejected')),
  CONSTRAINT ck_export_range CHECK (data_to >= data_from)
);
CREATE INDEX IF NOT EXISTS idx_export_request_status_created
  ON crm_export_request(status, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_export_request_requester
  ON crm_export_request(requester_user_id);

-- 种子指标 12 条：口径说明如实描述来源表、时间字段与组织数据权限限制
INSERT INTO crm_metric_definition
  (metric_code, metric_name, category, caliber_description, data_source, unit, decimal_places)
VALUES
  ('new_customer_count', '新增客户数', 'customer',
   '统计区间内创建（crm_customer.created_at 落在区间，按系统时区日终左闭右开）且当前状态为 active 的客户数量；crm_customer 无组织归属字段，非全院数据权限下不可计算。',
   'crm_customer.created_at / status', 'count', 0),
  ('customer_total', '客户总数', 'customer',
   '截至 dataTo 日终（crm_customer.created_at 小于结束日次日零点）状态为 active 的客户全量计数，不做起点过滤；无组织归属字段，非全院数据权限下不可计算。',
   'crm_customer.created_at / status', 'count', 0),
  ('lead_count', '新增线索数', 'customer',
   '统计区间内创建（crm_lead.created_at 落在区间）的线索数量；非全院数据权限按 owner_user_id 属于允许用户集合或 org_id 属于允许组织集合过滤。',
   'crm_lead.created_at', 'count', 0),
  ('lead_conversion_rate', '线索转化率', 'customer',
   '区间内状态为 CONVERTED 或 MERGED 的线索数 ÷ 区间内新增线索数 × 100%；分母为 0 时不计算；数据范围过滤与新增线索数一致。',
   'crm_lead.created_at / status', 'percent', 2),
  ('appointment_count', '预约单量', 'appointment',
   '统计区间内创建（crm_appointment.created_at 落在区间）的预约数量；非全院数据权限按 org_id 属于允许组织集合过滤。',
   'crm_appointment.created_at', 'count', 0),
  ('no_show_rate', '爽约率', 'appointment',
   '区间内状态为 NO_SHOW 的预约数 ÷ 区间内已到期预约数（状态 COMPLETED/NO_SHOW/CANCELLED）× 100%；分母为 0 时不计算；数据范围过滤与预约单量一致。',
   'crm_appointment.created_at / status', 'percent', 2),
  ('order_paid_amount', '订单实收金额', 'revenue',
   'crm_order 无独立支付时间字段，口径为：updated_at 落在区间内且状态为 PAID/COMPLETED/PARTIAL_REFUNDED/REFUNDED 的订单 paid_amount 求和（以 updated_at 近似支付时间）；订单无组织归属字段，非全院数据权限下不可计算。',
   'crm_order.updated_at / paid_amount / status', 'yuan', 2),
  ('followup_completion_rate', '随访完成率', 'followup',
   '区间内计划时间（crm_followup_task.planned_at）到期且状态为 COMPLETED 的随访任务数 ÷ 区间内到期（planned_at 落在区间）随访任务总数 × 100%；分母为 0 时不计算；非全院数据权限按 assignee_id 属于允许用户集合过滤。',
   'crm_followup_task.planned_at / status', 'percent', 2),
  ('complaint_count', '投诉建单数', 'complaint',
   '统计区间内创建（crm_complaint.created_at 落在区间）且工单类型 ticket_type=complaint 的工单数量（表扬 praise 与建议 suggestion 不计入）；非全院数据权限按 created_by 属于允许用户集合过滤。',
   'crm_complaint.created_at / ticket_type', 'count', 0),
  ('call_connected_rate', '通话接通率', 'callcenter',
   '区间内开始时间（crm_call_record.started_at）落在区间且状态 connected 的通话数 ÷ 区间内通话总数 × 100%；分母为 0 时不计算；非全院数据权限按 agent_user_id 属于允许用户集合过滤。',
   'crm_call_record.started_at / status', 'percent', 2),
  ('sms_delivery_rate', '短信送达率', 'callcenter',
   '区间内创建（crm_message_outbox.created_at 落在区间）且状态 SENT 的消息数 ÷ 区间内消息总数 × 100%；分母为 0 时不计算；crm_message_outbox 无处理人归属字段，非全院数据权限下不可计算。',
   'crm_message_outbox.created_at / status', 'percent', 2),
  ('insurance_received_amount', '商保到账金额', 'insurance',
   '统计区间内核赔决定时间（crm_insurance_case.decided_at）落在区间的案件 actual_received 求和；非全院数据权限按 handler_user_id 或 created_by 属于允许用户集合过滤。',
   'crm_insurance_case.decided_at / actual_received', 'yuan', 2)
ON CONFLICT (metric_code) DO NOTHING;

-- 种子报表 2 条：指标汇总（含全部 12 个指标）与客户明细导出
INSERT INTO crm_report_definition
  (report_code, report_name, description, report_type, metric_codes)
VALUES
  ('RPT_METRIC_SUMMARY', '运营指标汇总', '按时间区间汇总全部启用指标，结果含口径说明与数据时间范围，可追溯。',
   'metric_summary',
   'new_customer_count,customer_total,lead_count,lead_conversion_rate,appointment_count,no_show_rate,order_paid_amount,followup_completion_rate,complaint_count,call_connected_rate,sms_delivery_rate,insurance_received_amount'),
  ('RPT_CUSTOMER_DETAIL', '客户明细导出', '区间内新增客户明细，仅输出脱敏列（手机号/证件号为 masked 列），需要全院数据权限。',
   'customer_detail', NULL)
ON CONFLICT (report_code) DO NOTHING;

-- CRM-019 权限基线：查看、指标/报表维护、导出申请/审批/下载分权
INSERT INTO sys_permission (permission_code, permission_name, module, permission_type, description, sort_order)
VALUES
  ('crm:analytics:read', '查看运营分析', '运营分析', 'api', '查看指标定义、看板、报表数据与导出申请列表', 790),
  ('crm:analytics:metric:manage', '维护指标定义', '运营分析', 'action', '创建、编辑与启停指标定义（必须维护口径说明）', 800),
  ('crm:analytics:report:manage', '维护报表定义', '运营分析', 'action', '创建报表定义与启停', 810),
  ('crm:analytics:export:request', '发起数据导出申请', '运营分析', 'action', '按报表与时间范围发起数据导出申请', 820),
  ('crm:analytics:export:approve', '审批数据导出申请', '运营分析', 'action', '审批通过或驳回数据导出申请，通过时生成脱敏 CSV 与哈希', 830),
  ('crm:analytics:export:download', '下载已批准导出文件', '运营分析', 'api', '下载审批通过的脱敏 CSV 文件（仅申请人或审批人）', 840)
ON CONFLICT (permission_code) DO NOTHING;

INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r
CROSS JOIN sys_permission p
WHERE r.role_code = 'SUPER_ADMIN'
  AND p.permission_code LIKE 'crm:analytics:%'
ON CONFLICT DO NOTHING;
