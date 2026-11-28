-- =====================================================================
-- CRM-004 / CRM-013 增强：成员关联视图与随访板块统计索引
-- 说明：本次增强不新增表/列——家庭成员关联由既有 relationship_code 推导，
--       板块统计与 HIS 事件目录复用现有 crm_followup_task / crm_followup_category /
--       crm_followup_plan，仅补充聚合所需索引：
--       1) crm_followup_task.category_code：板块/分类统计的关联分组与列表按分类过滤；
--       2) crm_followup_task(status, planned_at)：状态计数与逾期（PENDING 且计划已过）统计。
--       家庭成员查询已由 V4 的 idx_family_member_family_status /
--       idx_family_member_customer_status 覆盖，无需新增。
-- =====================================================================

CREATE INDEX IF NOT EXISTS idx_followup_task_category
  ON crm_followup_task(category_code);
CREATE INDEX IF NOT EXISTS idx_followup_task_status_planned
  ON crm_followup_task(status, planned_at);
