-- =====================================================================
-- CRM-013 随访中心升级：五大回访板块 + HIS 事件对接
-- 说明：随访分类增加 visit_type 板块（门诊/住院/手术/体检/营销回访），
--       板块下可自由维护自定义子分类；计划触发类型扩展 HIS_* 四类；
--       新增 crm_followup_his_event 事件表，HIS/集成平台推送事件幂等落库，
--       由随访服务按启用计划自动生成任务。
-- =====================================================================

-- 1) 分类表增加板块字段
ALTER TABLE crm_followup_category ADD COLUMN IF NOT EXISTS visit_type VARCHAR(20) NOT NULL DEFAULT 'OUTPATIENT';
ALTER TABLE crm_followup_category DROP CONSTRAINT IF EXISTS ck_followup_category_visit_type;
ALTER TABLE crm_followup_category ADD CONSTRAINT ck_followup_category_visit_type
  CHECK (visit_type IN ('OUTPATIENT','INPATIENT','SURGERY','EXAM','MARKETING'));
CREATE INDEX IF NOT EXISTS idx_followup_category_visit ON crm_followup_category(visit_type, status);

-- 既有分类归入对应板块
UPDATE crm_followup_category SET visit_type = 'INPATIENT'  WHERE category_code IN ('POST_DISCHARGE','HIGH_RISK');
UPDATE crm_followup_category SET visit_type = 'SURGERY'    WHERE category_code = 'POST_OP';
UPDATE crm_followup_category SET visit_type = 'OUTPATIENT' WHERE category_code IN ('CHRONIC','SERVICE');

-- 2) 计划触发表达式扩展 HIS 事件
ALTER TABLE crm_followup_plan DROP CONSTRAINT IF EXISTS ck_followup_plan_trigger;
ALTER TABLE crm_followup_plan ADD CONSTRAINT ck_followup_plan_trigger CHECK (
  trigger_type IN ('APPOINTMENT_COMPLETED','SERVICE_COMPLETED','ORDER_PAID','MANUAL',
                   'HIS_OUTPATIENT_VISIT','HIS_DISCHARGE','HIS_SURGERY','HIS_EXAM_REPORT'));

-- 3) HIS 事件接收表（接口预留：HIS/ESB 推送 -> 幂等落库 -> 按计划建任务）
CREATE TABLE IF NOT EXISTS crm_followup_his_event (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  event_key VARCHAR(120) NOT NULL,
  event_type VARCHAR(40) NOT NULL,
  customer_id UUID,
  external_patient_id VARCHAR(100),
  source_system VARCHAR(50) NOT NULL DEFAULT 'HIS',
  occurred_at TIMESTAMPTZ,
  payload JSONB NOT NULL DEFAULT '{}'::jsonb,
  status VARCHAR(20) NOT NULL DEFAULT 'PROCESSED',
  created_tasks INTEGER NOT NULL DEFAULT 0,
  skipped_tasks INTEGER NOT NULL DEFAULT 0,
  error_message VARCHAR(500),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT uq_followup_his_event_key UNIQUE (event_key),
  CONSTRAINT ck_followup_his_event_type CHECK (
    event_type IN ('HIS_OUTPATIENT_VISIT','HIS_DISCHARGE','HIS_SURGERY','HIS_EXAM_REPORT')),
  CONSTRAINT ck_followup_his_event_status CHECK (status IN ('PROCESSED','NO_MATCH','FAILED'))
);
CREATE INDEX IF NOT EXISTS idx_followup_his_event_time ON crm_followup_his_event(created_at DESC);

-- 4) 种子：五大板块的自定义子分类
INSERT INTO crm_followup_category (category_code, category_name, description, visit_type, sort_no)
VALUES
  ('OP_SATISFACTION',    '门诊就诊满意度回访', '门诊就诊后 1-2 天收集满意度与投诉线索', 'OUTPATIENT', 11),
  ('OP_RETURN_REMIND',   '下次复诊提醒随访',   '按医嘱周期提醒患者按时复诊', 'OUTPATIENT', 12),
  ('OP_MEDICATION_GUIDE','门诊用药指导随访',   '门诊处方用药依从性与不良反应随访', 'OUTPATIENT', 13),
  ('INP_PRE_ADMIT',      '入院预通知随访',     '预约住院前的入院准备与注意事项告知', 'INPATIENT', 21),
  ('INP_DISCHARGE_30D',  '出院 30 天复诊随访', '出院一个月恢复情况与复诊安排', 'INPATIENT', 22),
  ('SURG_PRE_OP',        '术前告知随访',       '术前准备确认与心理疏导', 'SURGERY', 31),
  ('SURG_REVISIT_REMIND','术后复诊提醒随访',   '术后拆线/复查时间节点提醒', 'SURGERY', 32),
  ('EXM_ABNORMAL_NOTIFY','体检异常结果通知随访','重大异常指标电话通知并建议就诊', 'EXAM', 41),
  ('EXM_REPORT_SATIS',   '体检满意度回访',     '体检流程与报告解读满意度', 'EXAM', 42),
  ('EXM_RECHECK_REMIND', '体检复查提醒随访',   '建议复查项目的到期提醒', 'EXAM', 43),
  ('EXM_HEALTH_PLAN',    '健康管理计划随访',   '体检后健康干预计划执行情况', 'EXAM', 44),
  ('MK_ACTIVITY_INVITE', '活动邀约随访',       '患教/义诊等活动邀约与出席确认', 'MARKETING', 51),
  ('MK_MEMBER_RENEW',    '会员续费提醒随访',   '会员卡/权益包到期前提醒与续费意向', 'MARKETING', 52),
  ('MK_LOST_WAKEUP',     '沉睡客户唤醒随访',   '长期未就诊客户原因回访与召回', 'MARKETING', 53),
  ('MK_NPS_SURVEY',      '客户 NPS 调查随访',  '净推荐值调查，贬损者转人工回访', 'MARKETING', 54)
ON CONFLICT (category_code) DO NOTHING;

-- 5) 种子：新子分类对应模板
INSERT INTO crm_followup_template (template_code, category_code, template_name, channel, questions, risk_rules, description)
VALUES
  ('TPL_OP_SATISFACTION', 'OP_SATISFACTION', '门诊满意度回访', 'h5',
   '[{"code":"satisfaction","label":"整体满意度","options":["满意","一般","不满意"]},{"code":"waitTime","label":"候诊时长感受","options":["可以接受","偏久"]},{"code":"complaint","label":"是否有投诉","options":["否","是"]}]'::jsonb,
   '{"highRisk":[{"questionCode":"complaint","values":["是"]},{"questionCode":"satisfaction","values":["不满意"]}],"mediumRisk":[{"questionCode":"satisfaction","values":["一般"]},{"questionCode":"waitTime","values":["偏久"]}],"requiresHuman":true}'::jsonb,
   '门诊就诊后满意度 H5 问卷'),
  ('TPL_OP_RETURN_REMIND', 'OP_RETURN_REMIND', '复诊提醒随访', 'phone',
   '[{"code":"confirmed","label":"是否已预约复诊","options":["已预约","未预约"]},{"code":"needHelp","label":"是否需要协助预约","options":["否","是"]}]'::jsonb,
   '{"highRisk":[],"mediumRisk":[{"questionCode":"confirmed","values":["未预约"]},{"questionCode":"needHelp","values":["是"]}],"requiresHuman":false}'::jsonb,
   '复诊提醒并收集预约意愿'),
  ('TPL_INP_DISCHARGE_30D', 'INP_DISCHARGE_30D', '出院 30 天随访', 'phone',
   '[{"code":"recovery","label":"恢复情况","options":["良好","一般","较差"]},{"code":"revisit","label":"是否已复诊","options":["已复诊","未复诊"]}]'::jsonb,
   '{"highRisk":[{"questionCode":"recovery","values":["较差"]}],"mediumRisk":[{"questionCode":"recovery","values":["一般"]},{"questionCode":"revisit","values":["未复诊"]}],"requiresHuman":true}'::jsonb,
   '出院后 30 天恢复与复诊情况'),
  ('TPL_SURG_PRE_OP', 'SURG_PRE_OP', '术前告知随访', 'phone',
   '[{"code":"prepared","label":"术前准备是否完成","options":["是","否"]},{"code":"concern","label":"是否有疑虑需要医生说明","options":["无","有"]}]'::jsonb,
   '{"highRisk":[{"questionCode":"prepared","values":["否"]}],"mediumRisk":[{"questionCode":"concern","values":["有"]}],"requiresHuman":true}'::jsonb,
   '术前准备确认与疑虑收集'),
  ('TPL_SURG_REVISIT', 'SURG_REVISIT_REMIND', '术后复诊提醒随访', 'phone',
   '[{"code":"healing","label":"伤口愈合情况","options":["正常","异常"]},{"code":"scheduled","label":"是否已预约复查","options":["是","否"]}]'::jsonb,
   '{"highRisk":[{"questionCode":"healing","values":["异常"]}],"mediumRisk":[{"questionCode":"scheduled","values":["否"]}],"requiresHuman":true}'::jsonb,
   '术后复查节点提醒与伤口确认'),
  ('TPL_EXM_ABNORMAL', 'EXM_ABNORMAL_NOTIFY', '体检异常结果通知随访', 'phone',
   '[{"code":"notified","label":"是否已知晓异常指标","options":["是","否"]},{"code":"planVisit","label":"是否计划就诊","options":["是","否"]}]'::jsonb,
   '{"highRisk":[{"questionCode":"planVisit","values":["否"]}],"mediumRisk":[{"questionCode":"notified","values":["否"]}],"requiresHuman":true}'::jsonb,
   '重大异常结果通知与就诊引导'),
  ('TPL_EXM_SATIS', 'EXM_REPORT_SATIS', '体检满意度回访', 'h5',
   '[{"code":"satisfaction","label":"体检服务满意度","options":["满意","一般","不满意"]},{"code":"reportRead","label":"报告解读是否清楚","options":["清楚","不清楚"]}]'::jsonb,
   '{"highRisk":[{"questionCode":"satisfaction","values":["不满意"]}],"mediumRisk":[{"questionCode":"reportRead","values":["不清楚"]}],"requiresHuman":false}'::jsonb,
   '体检流程与报告解读满意度'),
  ('TPL_MK_ACTIVITY', 'MK_ACTIVITY_INVITE', '活动邀约随访', 'phone',
   '[{"code":"attend","label":"是否参加活动","options":["参加","不参加","待定"]},{"code":"reason","label":"不参加原因","options":["时间冲突","没兴趣","其它"]}]'::jsonb,
   '{"highRisk":[],"mediumRisk":[{"questionCode":"attend","values":["不参加"]}],"requiresHuman":false}'::jsonb,
   '活动邀约出席确认'),
  ('TPL_MK_MEMBER', 'MK_MEMBER_RENEW', '会员续费提醒随访', 'phone',
   '[{"code":"renewIntent","label":"续费意向","options":["续费","考虑中","不续费"]},{"code":"reason","label":"顾虑","options":["价格","权益","服务","其它"]}]'::jsonb,
   '{"highRisk":[],"mediumRisk":[{"questionCode":"renewIntent","values":["不续费"]}],"requiresHuman":false}'::jsonb,
   '会员到期提醒与续费意向'),
  ('TPL_MK_LOST', 'MK_LOST_WAKEUP', '沉睡客户唤醒随访', 'phone',
   '[{"code":"reason","label":"近期未到院原因","options":["工作忙","在别处就诊","体验不好","已康复","其它"]},{"code":"revisitIntent","label":"是否愿意到院","options":["愿意","不愿意"]}]'::jsonb,
   '{"highRisk":[{"questionCode":"reason","values":["体验不好"]}],"mediumRisk":[{"questionCode":"revisitIntent","values":["不愿意"]}],"requiresHuman":true}'::jsonb,
   '沉睡原因收集与召回意向'),
  ('TPL_MK_NPS', 'MK_NPS_SURVEY', '客户 NPS 调查', 'h5',
   '[{"code":"nps","label":"推荐意愿打分","options":["9","10","7","8","5","6","0","1","2","3","4"]},{"code":"comment","label":"改进建议","options":["已填写","未填写"]}]'::jsonb,
   '{"highRisk":[{"questionCode":"nps","values":["0","1","2","3","4","5","6"]}],"mediumRisk":[{"questionCode":"nps","values":["7","8"]}],"requiresHuman":true}'::jsonb,
   '净推荐值调查，0-6 贬损者转人工回访')
ON CONFLICT (template_code) DO NOTHING;

-- 6) 种子：HIS 事件触发的随访计划
INSERT INTO crm_followup_plan (plan_no, plan_name, category_code, template_code, trigger_type, offset_days, channel, description)
VALUES
  ('FP100010', '门诊就诊后满意度回访', 'OP_SATISFACTION', 'TPL_OP_SATISFACTION', 'HIS_OUTPATIENT_VISIT', 1, 'h5', 'HIS 门诊就诊完成后次日推送满意度问卷'),
  ('FP100011', '门诊复诊提醒随访', 'OP_RETURN_REMIND', 'TPL_OP_RETURN_REMIND', 'HIS_OUTPATIENT_VISIT', 14, 'phone', '门诊就诊后第 14 天提醒复诊'),
  ('FP100012', '出院 7 天恢复随访', 'POST_DISCHARGE', 'TPL_DISCHARGE_7D', 'HIS_DISCHARGE', 7, 'phone', 'HIS 出院后第 7 天恢复与用药随访'),
  ('FP100013', '出院 30 天复诊随访', 'INP_DISCHARGE_30D', 'TPL_INP_DISCHARGE_30D', 'HIS_DISCHARGE', 30, 'phone', 'HIS 出院后第 30 天复诊随访'),
  ('FP100014', '术后 3 天伤口随访', 'POST_OP', 'TPL_POST_OP_3D', 'HIS_SURGERY', 3, 'phone', 'HIS 手术完成后第 3 天伤口与疼痛随访'),
  ('FP100015', '术后复诊提醒随访', 'SURG_REVISIT_REMIND', 'TPL_SURG_REVISIT', 'HIS_SURGERY', 14, 'phone', 'HIS 手术后第 14 天复查提醒'),
  ('FP100016', '体检异常结果通知随访', 'EXM_ABNORMAL_NOTIFY', 'TPL_EXM_ABNORMAL', 'HIS_EXAM_REPORT', 1, 'phone', 'HIS 体检报告出具次日异常结果通知'),
  ('FP100017', '体检满意度回访', 'EXM_REPORT_SATIS', 'TPL_EXM_SATIS', 'HIS_EXAM_REPORT', 2, 'h5', 'HIS 体检报告出具后第 2 天满意度问卷')
ON CONFLICT (plan_no) DO NOTHING;
