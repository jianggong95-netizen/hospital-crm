# 医院CRM V1.0（Hospital CRM）

[![CI](https://github.com/jianggong95-netizen/hospital-crm/actions/workflows/ci.yml/badge.svg)](https://github.com/jianggong95-netizen/hospital-crm/actions/workflows/ci.yml)
![Java 21](https://img.shields.io/badge/Java-21-2D6DAD)
![Spring Boot 3.5](https://img.shields.io/badge/Spring%20Boot-3.5-6DB33F)
![Vue 3](https://img.shields.io/badge/Vue-3-42B883)
![PostgreSQL 16](https://img.shields.io/badge/PostgreSQL-16-336791)
![Repository](https://img.shields.io/badge/repository-private-lightgrey)

医院智慧客户服务与运营管理平台，面向医院内网私有化部署，覆盖客户中心、患者 360、家庭账户、预约、陪诊、上门服务、随访、客服、AI 数字人、呼叫中心、商保、投诉、会员、订单支付与运营分析。

当前完成 **CRM-001 项目初始化**、**CRM-002 认证与权限**、**CRM-003 客户中心**、**CRM-004 家庭账户**、**CRM-005 HIS 集成**、**CRM-006 患者 360**、**CRM-007 客户运营、邀约与分诊**、**CRM-008 预约中心**、**CRM-009 陪诊服务**、**CRM-010 上门服务**、**CRM-011 订单、收费、支付与退款**、**CRM-012 会员、充值、次卡与套餐**、**CRM-013 随访中心**、**CRM-014 投诉表扬中心**、**CRM-015 在线客服与人工工作台**、**CRM-016 AI 知识库与数字人**、**CRM-017 呼叫中心与短信**、**CRM-018 商保中心**、**CRM-019 运营分析** 与 **CRM-020 运维监控与发布回滚**：可运行的后端、管理端、移动端骨架，JWT 认证、RBAC 与数据范围、审计日志、客户档案加密与脱敏、标签管理、家庭账户与代办授权、HIS 只读适配与患者查询、CRM/HIS 聚合的患者 360 视图、线索与来源渠道、邀约任务、活动参与、线索转客户追溯、预约类型与服务时段、预约与改期取消（并发防超卖）、提醒失败重试与爽约记录、陪诊师排班防冲突与派单五重校验、履约计时与评价、上门服务目录审核与资质双重拦截及异常升级处置、统一订单与支付回调幂等入账、部分退款、渠道对账与发票接口抽象、会员等级与余额账本、积分与自动升级、次卡扣次与返还、套餐购买、共享授权与退款权益返还、随访分类模板计划、事件触发与重复事件去重、随访任务分配与记录、高风险自动人工队列、投诉表扬建单与 SLA、分派与处理动作留痕、整改与患者反馈、结案复核分权、超时自动升级、客服会话与消息、首次确认二次排队与直接转人工、人工队列与条件接管、客服转接与会话级权限隔离、AI 交接摘要、知识文档上传解析与切片、送审敏感扫描、审核发布与版本快照、失效知识下架、RAG 检索问答与引用来源追溯、低置信度转人工联动与 AI 随访话术草稿、Docker 开发环境、数据库迁移与 CI。CRM-019 运营分析提供指标口径定义、运营看板、报表查询、导出审批与组织数据权限能力。CRM-020 运维监控与发布回滚提供接口与定时任务监控、阈值告警、数据库备份恢复演练与一键部署回滚能力。

> 规划与契约文档位于 `Hospital_CRM_V1_0_交付包/`，本仓库代码按其中的总体方案、数据库设计与 API 规范实施。

## 目录

- [技术基线](#技术基线)
- [目录结构](#目录结构)
- [环境要求](#环境要求)
- [快速启动](#快速启动)
- [HIS 集成配置（CRM-005）](#his-集成配置crm-005)
- [患者 360（CRM-006）](#患者-360crm-006)
- [客户运营、邀约与分诊（CRM-007）](#客户运营邀约与分诊crm-007)
- [预约中心（CRM-008）](#预约中心crm-008)
- [陪诊服务（CRM-009）](#陪诊服务crm-009)
- [上门服务（CRM-010）](#上门服务crm-010)
- [订单、收费、支付与退款（CRM-011）](#订单收费支付与退款crm-011)
- [会员、充值、次卡与套餐（CRM-012）](#会员充值次卡与套餐crm-012)
- [随访中心（CRM-013）](#随访中心crm-013)
- [投诉表扬中心（CRM-014）](#投诉表扬中心crm-014)
- [在线客服与人工工作台（CRM-015）](#在线客服与人工工作台crm-015)
- [AI 知识库与数字人（CRM-016）](#ai-知识库与数字人crm-016)
- [呼叫中心与短信（CRM-017）](#呼叫中心与短信crm-017)
- [商保中心（CRM-018）](#商保中心crm-018)
- [运营分析（CRM-019）](#运营分析crm-019)
- [运维监控与发布回滚（CRM-020）](#运维监控与发布回滚crm-020)
- [健康检查与验收](#健康检查与验收)
- [数据库与迁移](#数据库与迁移)
- [API 约定](#api-约定)
- [已实现能力与明确未包含](#已实现能力与明确未包含)
- [本地验证记录（CRM-001 ~ CRM-020）](#本地验证记录crm-001--crm-020)
- [CI](#ci)
- [模块扩展约定](#模块扩展约定)
- [生产部署与安全要点](#生产部署与安全要点)
- [文档索引](#文档索引)

## 技术基线

| 层次 | 技术 |
|---|---|
| 后端 | Java 21、Spring Boot 3.5、Spring MVC、Flyway、Actuator、springdoc |
| CRM 主库 | PostgreSQL 16+ |
| HIS | Microsoft SQL Server（只读适配，CRM-005 接入） |
| 缓存 / 消息 / 文件 | Redis 7、RabbitMQ 3.13、MinIO |
| 管理端 | Vue 3、TypeScript、Element Plus、Vite |
| 移动端 | uni-app + Vue 3（微信小程序与 H5 共用业务层） |
| 部署 | Docker Compose、Nginx、医院内网 |

## 目录结构

```text
.
├─ backend/                      # Maven 多模块后端
│  ├─ crm-common/                # 统一响应、错误码、异常、traceId 上下文
│  ├─ crm-system/                # 系统管理域（认证、用户、组织、角色、权限、审计）
│  ├─ crm-customer/              # 客户中心（客户档案、敏感字段加密、检索、标签）
│  ├─ crm-family/                # 家庭账户（成员、关系历史、家庭管理员、代办授权）
│  ├─ crm-his/                   # HIS 集成（SQL Server 只读适配、患者查询、熔断、同步日志）
│  ├─ crm-patient360/            # 患者 360（CRM 与 HIS 聚合、字段权限、分页与短时缓存）
│  ├─ crm-operation/             # 客户运营（来源渠道、线索、联系记录、分派、转化追溯、邀约、活动）
│  ├─ crm-appointment/           # 预约中心（预约类型、服务时段、预约与改期取消、提醒重试、爽约记录）
│  ├─ crm-escort/                # 陪诊服务（陪诊师档案与资质、排班防冲突、派单校验、履约计时、评价）
│  ├─ crm-homevisit/             # 上门服务（项目目录与审核、服务地址、人员资质、订单派单、执行记录、异常上报与升级）
│  ├─ crm-order/                 # 订单与收银（统一订单、支付单、渠道回调、退款、对账、发票接口抽象）
│  ├─ crm-member/                # 会员（等级、余额账本、积分、次卡、套餐、共享授权、退款权益返还）
│  ├─ crm-followup/              # 随访中心（分类、模板、计划、事件触发、任务分配、随访记录、高风险人工队列）
│  ├─ crm-analytics/             # 运营分析（指标口径定义、运营看板、报表定义与取数、导出审批闭环、组织数据权限）
│  ├─ crm-ops/                   # 运维监控（接口与任务监控、阈值告警、备份恢复登记、发布回滚编排）
│  ├─ crm-boot/                  # Spring Boot 启动、Web 配置、Flyway 迁移
│  ├─ Dockerfile
│  └─ pom.xml
├─ admin-web/                    # Vue 3 管理端（登录、工作台、客户中心、患者 360、家庭账户、客户运营、预约中心、陪诊服务、上门服务、收银与订单、会员与权益、随访中心、运营分析、运维监控、标签、系统管理）
├─ ops/                          # 运维脚本与手册（deploy 部署回滚、pg 备份恢复演练、monitoring 监控栈、logging 日志告警、运维手册）
├─ patient-mobile/               # uni-app 患者与员工移动端（H5 / 微信小程序）
├─ .github/workflows/ci.yml      # 后端测试、前端构建、Compose 校验
├─ docker-compose.yml            # PostgreSQL / Redis / RabbitMQ / MinIO / 应用（含 ops profile：Prometheus / Alertmanager）
├─ .env.example                  # 本地开发环境变量示例
└─ Hospital_CRM_V1_0_交付包/      # 需求、数据库、API、部署规划文档（只读）
```

## 环境要求

- JDK 21（后端编译目标为 21）
- Maven 3.9+，或直接使用仓库内 Maven Wrapper（`backend/mvnw`、`backend/mvnw.cmd`）
- Node.js 20+（建议 22 LTS）与 npm 10+
- Docker 24+ 与 Docker Compose v2（启动 PostgreSQL 等依赖）

## 快速启动

### 方式一：本地进程开发（推荐）

1. 准备环境变量并启动基础依赖：

```powershell
Copy-Item .env.example .env
docker compose up -d postgres redis rabbitmq minio
```

2. 启动后端（首次启动由 Flyway 自动建表）：

```powershell
cd backend
.\mvnw.cmd spring-boot:run
```

macOS / Linux 使用 `./mvnw spring-boot:run`。已有 Maven 时也可执行 `mvn spring-boot:run`。首次执行 Wrapper 会从 Maven Central 下载 Maven 3.9.9。

3. 启动管理端：

```powershell
cd admin-web
npm install
npm run dev
```

4. 启动患者端 H5：

```powershell
cd patient-mobile
npm install
npm run dev:h5
```

| 服务 | 地址 |
|---|---|
| CRM API | http://localhost:8080 |
| 健康检查 | http://localhost:8080/actuator/health |
| 就绪探针（含数据库） | http://localhost:8080/actuator/health/readiness |
| OpenAPI JSON | http://localhost:8080/v3/api-docs |
| Swagger UI | http://localhost:8080/swagger-ui.html |
| 管理端 | http://localhost:5173 |
| 患者端 H5 | http://localhost:5174 |

首次启动时后端会自动创建初始管理员（由 `CRM_BOOTSTRAP_ADMIN_*` 控制），开发环境默认 `admin / Crm@123456`。管理端打开后进入登录页，登录成功后方可访问系统管理页面。生产环境必须显式设置 `CRM_JWT_SIGNING_KEY`、`CRM_DATA_ENCRYPTION_KEY` 与 `CRM_BOOTSTRAP_ADMIN_PASSWORD`，缺失时 `prod` 配置将拒绝启动。客户手机号与证件号使用 AES-256-GCM 加密存储，`CRM_DATA_ENCRYPTION_KEY` 必须是 Base64 编码的 32 字节密钥。

### 方式二：Docker Compose 全栈启动

```powershell
Copy-Item .env.example .env
docker compose up --build -d
docker compose ps
```

| 服务 | 地址 |
|---|---|
| 管理端 | http://localhost:8081 |
| 患者端 H5 | http://localhost:8082 |
| CRM API | http://localhost:8080 |
| RabbitMQ 控制台 | http://localhost:15672 |
| MinIO 控制台 | http://localhost:9001 |

停止并保留数据：`docker compose down`；同时清理数据卷：`docker compose down -v`（会删除本地数据库数据，谨慎执行）。

### 本地开发默认凭据（仅限开发环境）

| 组件 | 用户名 | 密码 |
|---|---|---|
| PostgreSQL | `crm` | `crm_dev_password` |
| RabbitMQ | `crm` | `crm_dev_password` |
| MinIO | `crmadmin` | `crm_dev_minio` |
| CRM 初始管理员 | `admin` | `Crm@123456` |

生产环境必须替换为医院密钥管理系统或受控 Secret 中的强密码，禁止沿用默认值。

## HIS 集成配置（CRM-005）

HIS 适配器默认关闭；未配置时 `/api/v1/his/patients/**` 患者查询 fail-closed 返回 503，不会伪造数据。

1. 由医院提供 SQL Server 只读账号、患者/就诊/住院只读视图与字段字典。
2. 在 `.env` 配置 `CRM_HIS_ENABLED=true`、`CRM_HIS_URL`、`CRM_HIS_USERNAME`、`CRM_HIS_PASSWORD`；
   视图与字段映射通过 `crm.his.patient-view.*`、`crm.his.visit-view.*`、`crm.his.admission-view.*` 覆盖。
3. 启动后调用 `/api/v1/his/patients/search` 验证连通性、字段映射与只读账号校验。

| 配置 | 说明 | 默认 |
|---|---|---|
| `crm.his.enabled` | 是否启用 HIS 集成 | `false` |
| `crm.his.url` | SQL Server 只读视图连接串（`jdbc:sqlserver:`） | 空 |
| `crm.his.query-timeout-seconds` | 单次查询超时（秒） | `5` |
| `crm.his.search-limit` | 患者候选返回上限 | `20` |
| `crm.his.read-only-check` | 校验账号是否具备写权限，具备则拒绝查询 | `true` |
| `crm.his.circuit-breaker.*` | 连续失败阈值 / 熔断冷却 / 半开探测数 | 5 / 30s / 1 |
| `crm.his.snapshot-valid-minutes` | 360 快照有效期 | `30` |
| `crm.his.snapshot-max-stale-minutes` | HIS 不可用时快照最长可用时长 | `1440` |

> 安全约束：适配器只允许参数化 SELECT；SQL 守卫拒绝分号、注释、写操作、危险关键字与非白名单视图。查询关键字、证件号等敏感明文不写入日志与审计。示例视图名 `dbo.v_crm_patient` 等仅为占位，必须在医院现场确认后替换。
>
> 权限说明：`his:patient:read` / `his:patient:link` 在 V5 中默认仅授予 `SUPER_ADMIN`，其他角色需在角色管理中显式分配。
>
> 回滚说明：将 `CRM_HIS_ENABLED=false` 即可停止所有 HIS 对外查询（患者接口返回 503，已缓存快照仍会以 `STALE` 明确标记），无需回滚 V5；V5 仅为新增快照表、索引与权限，属增量迁移。

## 患者 360（CRM-006）

`GET /api/v1/customers/{customerId}/360` 以 CRM 客户为主键聚合客户档案、标签、家庭归属与 HIS 就诊/住院摘要。视图本身不复制 HIS 临床数据，也不扩大任何领域权限：

- **字段权限**：基础权限 `crm:patient360:read`；完整手机号/证件号仍需 `crm:customer:sensitive:read`；家庭分区需 `crm:family:read`；HIS 分区需 `his:patient:read`。无权限分区返回 `RESTRICTED`，不调用下游、不返回字段。
- **数据来源与更新时间**：`dataSources` 逐分区返回 `sourceSystem`、`dataStatus`、`fetchedAt`、`validUntil`、`cached` 与说明；HIS 分区额外返回匹配置信度与人工核验标记。
- **HIS 不可用**：未绑定返回 `NOT_LINKED`；未配置/查询失败且无快照返回 `UNAVAILABLE` 与错误码；存在有效快照时透传 CRM-005 的 `STALE` 标记。以上状态均不补齐、不推测、不伪造诊疗数据。
- **分页**：`visitPage` / `visitPageSize` 与 `admissionPage` / `admissionPageSize` 独立分页，默认 10，上限 50。
- **缓存**：HIS 结果按患者主索引进入进程内 TTL 缓存，默认 60 秒；`refresh=true` 跳过短时缓存；失败结果不缓存。
- **审计**：每次查看写入 `PATIENT360_VIEW`，仅记录分区状态、是否敏感可见与是否命中缓存，不记录诊疗内容或身份标识明文。

| 配置 | 说明 | 默认 |
|---|---|---|
| `crm.patient360.cache-ttl-seconds` | HIS 聚合结果短时缓存 TTL（秒），≤0 表示关闭 | `60` |
| `crm.patient360.cache-max-entries` | 进程内缓存最大条目数，触顶优先清理过期项 | `500` |
| `crm.patient360.default-page-size` | 就诊/住院分区默认分页大小 | `10` |
| `crm.patient360.max-page-size` | 就诊/住院分区最大分页大小 | `50` |

> 权限说明：`crm:patient360:read` 在 V6 中默认仅授予 `SUPER_ADMIN`，其他角色需在角色管理中显式分配；该权限不隐含敏感字段或 HIS 临床数据权限。

## 客户运营、邀约与分诊（CRM-007）

`crm-operation` 模块承接线索到客户的完整运营链路，所有写操作受 RBAC 与数据范围约束，敏感字段沿用客户中心规则（仅密文、检索哈希与脱敏值）：

- **来源渠道**：`GET/POST /api/v1/operation/channels`、`PUT /channels/{channelId}/status`；V7 预置 `MANUAL`、`ONLINE`、`PHONE`、`REFERRAL`、`ACTIVITY`、`WALK_IN`、`HIS` 七个渠道，支持启停。
- **线索**：`GET/POST /api/v1/operation/leads`、`GET/PUT /leads/{leadId}`、`PUT /leads/{leadId}/status`；状态机为 `NEW→CONTACTING→QUALIFIED→INVITED`，`INVALID` 可随时终止，`CONVERTED`/`MERGED` 仅由转化或合并产生。
- **重复核验**：创建前按手机号、证件号哈希比对活跃线索；命中时返回 409 与疑似线索号，确认非同一人后可传 `forceCreate=true` 强制创建；`GET /leads/duplicates` 支持主动核验。
- **联系记录**：`GET/POST /leads/{leadId}/contacts`，记录联系类型、时间、结果与下次跟进时间；首次登记联系记录会把 `NEW` 线索推进到 `CONTACTING`。
- **分派与转派**：`POST /leads/{leadId}/assign` 记录 `fromUserId`、`toUserId` 与原因；`GET /leads/{leadId}/assignments` 返回完整分派历史；`GET /leads/assignees` 提供可分派员工候选。
- **转化追溯**：`POST /leads/{leadId}/convert` 可新建客户或关联既有客户，写入 `crm_lead_conversion`（`NEW_CUSTOMER` / `EXISTING_CUSTOMER`），线索保留 `convertedCustomerId` 与 `convertedAt`；`GET /leads/{leadId}/conversion` 按线索号与客户号双向追溯。
- **线索合并**：`POST /leads/{leadId}/merge` 将当前线索标记 `MERGED` 并指向目标线索，不复制敏感字段。
- **邀约**：`GET/POST /api/v1/operation/invitations`、`GET /invitations/{invitationId}`、`PUT /invitations/{invitationId}/status`；状态机为 `PENDING→CONTACTED→ACCEPTED/DECLINED`，任一步可 `CANCELLED`/`EXPIRED`；接受后关联线索自动同步为 `INVITED`。
- **活动**：`GET/POST /api/v1/operation/activities`、`GET /activities/{activityId}`、`PUT /activities/{activityId}/status`；支持 `DRAFT→PUBLISHED→CLOSED` 与 `CANCELLED`。`GET/POST /activities/{activityId}/participants` 完成报名，`PUT /activities/{activityId}/participants/{participantId}/status` 完成签到/爽约/取消；仅已发布活动可报名，名额满返回 422，同活动同手机号重复报名返回 409。
- **审计**：渠道、线索创建/编辑/状态、联系记录、分派、合并、转化、邀约与活动操作均写入 `sys_audit_log`，记录线索号/客户号等业务标识，不写入手机号、证件号明文。

| 权限 | 说明 |
|---|---|
| `crm:channel:read` / `crm:channel:write` | 查看 / 创建与启停来源渠道 |
| `crm:lead:read` / `crm:lead:write` | 查看 / 创建、编辑线索与联系记录 |
| `crm:lead:convert` | 线索转客户并保留转化追溯 |
| `crm:lead:assign` | 线索分派与转派 |
| `crm:invitation:read` / `crm:invitation:write` | 查看 / 创建与推进邀约 |
| `crm:activity:read` / `crm:activity:write` | 查看 / 创建活动、报名与签到 |

> 上线提示：V7 中上述 10 个权限默认仅授予 `SUPER_ADMIN`，其他角色需在角色管理中显式分配；线索手机号/证件号与活动参与人手机号均以 AES-256-GCM 加密存储，接口仅返回脱敏值。

## 预约中心（CRM-008）

`crm-appointment` 模块覆盖预约全生命周期（类型、时段、预约、改期、取消、提醒、爽约），写操作均受 RBAC 权限控制，预约联系电话沿用客户中心加密与脱敏规则：

- **预约类型**：`GET/POST /api/v1/appointment-types`、`PUT /{typeCode}`、`PUT /{typeCode}/status`；V8 预置 OUTPATIENT（30 分钟 / 截止 2h）、HEALTH_CHECK（120 / 24h）、FOLLOW_UP（30 / 4h）、PHONE_CONSULT（15 / 1h）、SPECIAL（60 / 24h）五个类型，支持启停。
- **服务时段**：`GET/POST /api/v1/service-slots`、`PUT /{slotId}`、`PUT /{slotId}/status`；类型 + 日期 + 开始时间唯一（重复创建返回 409）；已被预约的时段不可修改时间、容量不得调低到已约数以下；支持开闭。
- **预约**：`GET/POST /api/v1/appointments`、`GET /{id}`、`PUT /reschedule`、`PUT /cancel`、`PUT /status`；状态机 `BOOKED→CONFIRMED→COMPLETED`，`CANCELLED`/`NO_SHOW` 为终态；同一客户同一时段仅允许一条活跃预约（重复返回 409）。
- **并发防超卖**：占名额使用条件更新 `bookedCount+1 WHERE status='OPEN' AND bookedCount<capacity`，影响行数为 0 即返回 409「名额已满或已关闭」；数据库另有 `CHECK (booked_count <= capacity)` 兜底。冒烟中容量 1 时段两个并发请求恰好一个 200、一个 409。
- **取消与改期截止**：按预约类型 `cancel_cutoff_hours` 计算截止时间（时段开始 − 截止时长），超时取消/改期返回 422 并提示联系前台；取消释放名额，改期先占新时段再释放旧时段，爽约不释放名额。
- **提醒与重试**：每条预约按计划时间（开始前 24 小时，不足 24 小时立即发送）生成提醒，`@Scheduled` 默认每 60 秒扫描到期提醒（`crm.appointment.reminder.scan-interval-ms` 可配置）；发送失败按指数退避（300s × 2^(n-1)）自动重试，最多 3 次，达到上限后停止自动重试并可用 `PUT /api/v1/appointment-reminders/{reminderId}/retry` 人工重试；`POST /appointment-reminders/dispatch` 支持手动触发批量派发；预约进入终态后其未发送提醒自动置为 `CANCELLED`。
- **爽约记录**：标记 `NO_SHOW` 要求时段已开始，写入 `crm_no_show_record`（每条预约唯一），`GET /api/v1/no-shows` 分页查询；终态（COMPLETED / NO_SHOW）预约不可再次变更状态（422）。
- **审计**：预约创建/改期/取消/状态、时段与类型管理、提醒派发与重试均写入 `sys_audit_log`，仅记录业务号，不含手机号明文。

| 权限 | 说明 |
|---|---|
| `crm:appttype:read` / `crm:appttype:write` | 查看 / 管理预约类型与服务时段 |
| `crm:appointment:read` / `crm:appointment:write` | 查看 / 创建、改期、取消预约 |
| `crm:appointment:manage` | 确认、完成、爽约、提醒派发与重试等履约管理 |

| 配置 | 说明 | 默认 |
|---|---|---|
| `crm.appointment.reminder.scan-interval-ms` | 到期提醒扫描间隔（毫秒） | `60000` |
| `crm.appointment.reminder.initial-delay-ms` | 应用启动后首次扫描延迟（毫秒） | `30000` |

> 上线提示：V8 中上述 5 个权限默认仅授予 `SUPER_ADMIN`，其他角色需在角色管理中显式分配；默认提醒实现为日志通道（`LoggingAppointmentNotifier`），接入真实短信 / 微信通道需实现 `AppointmentNotifier` 接口并注册为组件。

## 陪诊服务（CRM-009）

`crm-escort` 模块覆盖陪诊服务全生命周期（陪诊师档案、资质、排班、派单、接单、履约计时、评价），写操作均受 RBAC 权限控制，陪诊师与订单联系电话沿用客户中心加密与脱敏规则：

- **陪诊师档案**：`GET/POST /api/v1/escorts`、`GET /{escortId}`、`PUT /{escortId}`、`PUT /{escortId}/status`；陪诊号 `ES100000+` 取自数据库序列；服务区域为标签列表；支持启停，停用后不可被派单。
- **资质管理**：`POST /api/v1/escorts/{escortId}/qualifications`、`PUT /api/v1/escorts/{escortId}/qualifications/{qualificationId}/revoke`；类型 `nurse/caregiver/health_manager/other`，含证书号、发证日期与可选到期日；到期日早于服务日期或已停用的资质不满足派单条件。
- **排班**：`GET/POST /api/v1/escort-schedules`、`PUT /{scheduleId}`、`DELETE /{scheduleId}`；同一陪诊师同一日期时间段重叠返回 409「排班与已有班次重叠」（更新时排除自身），数据库唯一索引 `(escort_id, work_date, start_time)` 兜底；过去日期不可排班（422），删除仅允许未来日期班次。
- **陪诊订单**：`GET/POST /api/v1/escort-orders`、`GET /{orderId}`；订单号 `E100000+`；状态机 `PENDING→ASSIGNED→ACCEPTED→IN_PROGRESS→COMPLETED`，`CANCELLED` 为终态，拒绝（`reject`）退回 `PENDING` 并保留原因；非法流转一律 422。
- **派单五重校验**：`PUT /{orderId}/dispatch` 要求订单为 `PENDING`，陪诊师存在且启用、持有服务日有效的未过期未停用资质、当日存在覆盖服务时间窗的排班、且该时段无其他活跃订单（`ASSIGNED/ACCEPTED/IN_PROGRESS` 时间重叠），任一不满足返回 422。
- **履约计时**：`PUT /{orderId}/start` 记录 `actual_start_at`，`PUT /{orderId}/finish` 记录 `actual_end_at` 并计算 `duration_minutes`；服务开始后不可取消（422「服务已开始，请使用完成」）。
- **评价**：`POST /api/v1/escort-orders/{orderId}/review`（1–5 星 + 内容）仅 `COMPLETED` 订单可评（422），每单一条（重复 409）；`GET /api/v1/escort-reviews` 分页查询（可按陪诊师过滤）；陪诊师列表与详情返回平均分 `avgRating` 与评价数 `reviewCount`。
- **审计**：档案/资质/排班/订单/评价全部动作写入 `sys_audit_log`（`ESCORT_*`、`ESCORTQUAL_*`、`SCHEDULE_*`、`ESCORTORDER_*`），仅记录业务号，不含手机号明文。

| 权限 | 说明 |
|---|---|
| `crm:escort:read` / `crm:escort:write` | 查看 / 管理陪诊师档案、资质与排班 |
| `crm:escortorder:read` / `crm:escortorder:write` | 查看 / 创建、派单、接单、拒绝订单 |
| `crm:escortorder:manage` | 开始、完成、取消、评价等履约管理 |

> 上线提示：V9 中上述 5 个权限默认仅授予 `SUPER_ADMIN`，其他角色需在角色管理中显式分配；陪诊订单可关联预约（`appointmentId` 可选），但不强制依赖预约中心。

## 上门服务（CRM-010）

`crm-homevisit` 模块覆盖上门服务全链路（项目目录与审核、服务地址、人员资质、订单审核与派单、执行记录、异常上报与升级），写操作均受 RBAC 权限控制，地址联系电话沿用客户中心加密与脱敏规则：

- **服务目录与审核**：`GET/POST /api/v1/home-services`、`PUT /{serviceCode}`、`PUT /{serviceCode}/submit`、`PUT /{serviceCode}/review`、`PUT /{serviceCode}/status`；审核链 `draft→pending_review→published/rejected`，驳回必须填写原因（422），非待审状态不可重复审核（422）；仅 `published` 项目可下单（未发布下单 422「服务不存在或未发布」）；`published↔disabled` 由管理权限启停；V10 预置 HOME_INJECTION（居家注射）、HOME_DRESSING（居家换药）、HOME_CLEANING（居家保洁）三条已发布服务。
- **服务地址簿**：`GET/POST /api/v1/home-addresses`、`GET/PUT/DELETE /{addressId}`、`PUT /{addressId}/default`；联系电话加密存储；每个客户仅允许一个默认地址（部分唯一索引 `uq_home_address_default` 兜底），设为默认时自动清除旧默认位。
- **人员资质**：`GET/POST /api/v1/home-staff-qualifications`、`PUT /{staffQualificationId}/revoke`；资质含类型、证书号与有效期，过期或停用即失效。
- **订单审核与派单**：`GET/POST /api/v1/home-visit-orders`、`GET /{orderId}`、`PUT /{orderId}/review`、`PUT /{orderId}/dispatch`、`accept`、`reject`、`start`、`finish`、`cancel`；订单号 `H100000+`；状态机 `PENDING→APPROVED→ASSIGNED→ACCEPTED→IN_PROGRESS→COMPLETED`，`REJECTED`/`CANCELLED` 为终态，拒绝派单退回 `APPROVED` 并清空派单信息；派单与接单双重资质拦截——服务人员不具备该项目的有效资质返回 422「上门服务人员不具备该项目的有效资质」。
- **执行记录**：`POST /{orderId}/executions` 仅进行中订单可登记；`start`/`finish` 记录实际起止并计算 `duration_minutes`。
- **异常上报与升级**：`POST /{orderId}/exceptions` 仅进行中订单可上报（`OPEN`）；`PUT /api/v1/home-visit-exceptions/{exceptionId}/escalate` 升级（`ESCALATED`）、`PUT /{exceptionId}/resolve` 处置（`RESOLVED`）；存在未处置异常时完成订单返回 422「存在未处理的异常上报，请先处置」。
- **审计**：目录/审核/地址/资质/订单/派单/执行/异常动作写入 `sys_audit_log`（`HOMESVC_*`、`HOMEADDR_*`、`STAFFQUAL_*`、`HV_ORDER_*`、`HV_EXCEPTION_*`），仅记录业务号，不含手机号明文。

| 权限 | 说明 |
|---|---|
| `crm:homevisit:read` / `crm:homevisit:write` | 查看 / 管理目录、地址、资质、订单与派单 |
| `crm:homevisit:manage` | 目录审核与启停、订单审核、异常升级与处置 |

> 上线提示：V10 中上述 3 个权限默认仅授予 `SUPER_ADMIN`，其他角色需在角色管理中显式分配；默认地址切换使用 `saveAndFlush` 保证旧行 UPDATE 先于新行 INSERT，避免与部分唯一索引冲突。

## 订单、收费、支付与退款（CRM-011）

`crm-order` 模块承接统一收银链路（订单、支付单、渠道回调、退款、对账、发票），金额一律由服务端重算，全部资金动作写入 `sys_audit_log`。**CRM 只在 CRM 主库记录资金流水，不写 HIS 收费表** —— 该模块不依赖 `crm-his`，不含任何 HIS 写操作。

- **统一订单**：`GET/POST /api/v1/orders`、`GET /orders/{orderId}`、`PUT /orders/{orderId}/cancel`、`PUT /orders/{orderId}/complete`；`items` 行金额 = 数量 × 单价（按分四舍五入），订单总额 = 行金额合计，应付 = 总额 − 优惠，优惠超过总额返回 422；`Idempotency-Key` 重复返回 409；状态机 `CREATED→PAID→COMPLETED`，未支付可 `CANCELLED`，退款后为 `PARTIAL_REFUNDED`/`REFUNDED`。
- **支付单**：`POST /orders/{orderId}/payments`、`GET /api/v1/payments`、`GET /payments/{paymentId}`；支付金额必须大于 0 且不超过「应付 − 实付」，超限返回 422；幂等键重复返回 409；渠道 `wechat/alipay/bank/cash/other` 经 `PaymentGateway` 抽象接入，默认 stub 返回占位交易号。
- **渠道回调**：`POST /api/v1/payment-callbacks/{channel}`（免登录、强制验签）；验签为 HMAC-SHA256（规范化串 `paymentNo=&amount=&success=&tradeNo=`），缺失签名 400、签名错误 401；**同一支付单重复回调按幂等回放**（返回 `replayed=true`），`markSuccess` 条件更新只成功一次，`paid_amount` 不会重复累加。
- **退款**：`POST /payments/{paymentId}/refunds`、`GET /api/v1/refunds`、`GET /refunds/{refundId}`、`PUT /refunds/{refundId}/approve`、`PUT /refunds/{refundId}/reject`；仅 `SUCCESS` 支付单可退，金额不超过「实付 − 累计已退」，支持多次部分退款；审核时支付单与订单退款额以条件更新原子累加，并发超退返回 422 并整体回滚；状态机 `PENDING→APPROVED→SUCCESS`，`PENDING→REJECTED`，执行失败为 `FAILED`。
- **渠道对账**：`POST/GET /api/v1/reconciliations`、`GET /reconciliations/{batchId}/diffs`；按渠道与账期比对本地成功支付与渠道账单（`ChannelStatementProvider` 抽象），逐笔标记 `LOCAL_ONLY` / `CHANNEL_ONLY` / `AMOUNT_MISMATCH`，全部一致为 `MATCHED`，否则 `DIFF`。默认 stub 返回空账单，本地成功支付如实记为单边差异，不伪造渠道数据；对接微信/支付宝对账单文件时替换该实现即可。
- **发票**：`POST /api/v1/invoices/orders/{orderId}`、`GET /invoices`、`PUT /invoices/{invoiceId}/issue`、`PUT /invoices/{invoiceId}/void`；可开票净额 = 实付 − 已退，同订单 `APPLIED`/`ISSUED` 累计不得超过净额；开票经 `InvoiceProvider` 抽象，默认 stub 返回占位文件引用；红冲置 `VOID` 释放额度。
- **审计**：`ORDER_CREATE` / `ORDER_CANCEL` / `ORDER_COMPLETE` / `PAYMENT_INITIATE` / `PAYMENT_CALLBACK` / `REFUND_APPLY` / `REFUND_APPROVE` / `REFUND_REJECT` / `REFUND_SUCCESS` / `RECON_RUN` / `INVOICE_APPLY` / `INVOICE_ISSUE` / `INVOICE_VOID` 均落库，仅记录业务单号与金额，不含客户手机号。

| 权限 | 说明 |
|---|---|
| `crm:order:read` / `crm:order:write` | 查看订单与支付 / 创建订单、发起支付、申请退款、申请开票 |
| `crm:payment:read` / `crm:payment:manage` | 查看退款与对账、发票 / 退款审核、执行对账、开票与红冲 |

| 配置 | 说明 | 默认 |
|---|---|---|
| `crm.payment.callback-secret` | 支付回调 HMAC-SHA256 验签密钥（`CRM_PAYMENT_CALLBACK_SECRET`） | `crm_dev_callback_secret` |

> 上线提示：V11 中上述 4 个权限默认仅授予 `SUPER_ADMIN`，其他角色需在角色管理中显式分配；生产环境必须替换 `CRM_PAYMENT_CALLBACK_SECRET` 为渠道分配的密钥，并将 `StubPaymentGateway`、`StubChannelStatementProvider`、`StubInvoiceProvider` 替换为真实渠道实现。

### CRM-011 验收清单

| 验收项 | 结果 |
|---|---|
| 金额校验 | 订单总额由服务端按明细重算（`2×49.995 + 1×100.00 = 199.99`），应付 = 总额 − 优惠；优惠超总额 422；支付超未付余额 422；退款超可退余额 422；开票超净额 422 |
| 重复回调不重复入账 | 首次回调 `replayed=false` 且实付 190.00；重复回调 `replayed=true`，`markSuccess` 条件更新返回 0 后直接回放，订单实付仍 190.00；并发重复回调不触发 `accumulatePaid` |
| 部分退款正确 | 分两次退 50.00 + 140.00，支付单与订单 `refunded_amount` 累计 190.00，订单由 `PARTIAL_REFUNDED` 转 `REFUNDED`；第三笔超退 422 且整体回滚 |
| 回调验签 | 缺失签名 400（参数校验）、错误签名 401，均不入账 |
| 渠道对账 | 按渠道与账期生成批次，逐笔标记 `LOCAL_ONLY`/`CHANNEL_ONLY`/`AMOUNT_MISMATCH`；stub 空账单下本地 2 笔如实记为 2 笔差异，无伪造 |
| 发票额度 | 申请 100 → 开具 → 红冲闭环；红冲后释放额度；全额退款后订单不可再开票（422） |
| 不写 HIS 收费表 | `crm-order/pom.xml` 仅依赖 `crm-system`、`crm-customer`，无 `crm-his` 依赖与写库调用 |
| 审计留痕 | 13 类资金动作写入 `sys_audit_log`，`detail` 中手机号明文匹配 0 条 |
| 数据库迁移 | `V11__order_payment.sql` 新增对账批次/差异、发票表与 4 项权限，并为支付单补充渠道交易号与幂等键唯一索引 |

## 会员、充值、次卡与套餐（CRM-012）

`crm-member` 模块承接会员等级、储值余额、积分、次卡、套餐与共享授权。**余额与积分的每一次变动都会写入 `crm_member_ledger`**（含变动额、积分增减、变动后余额/积分、关联订单与业务号），扣减一律使用数据库条件更新（`balance >= amount`、`remaining_times >= times`、`points >= delta`），并发请求不会超扣。

- **等级**：`GET /api/v1/memberships/levels` 返回 5 级种子（普通/银卡/金卡/铂金/钻石，门槛 0/1000/5000/20000/50000 分，折扣率 1.00/0.98/0.95/0.92/0.88）；积分调整后按门槛自动重算等级。
- **账户**：`GET/POST /api/v1/memberships/accounts`、`GET /accounts/{accountId}`（含有效次卡）、`GET /accounts/{accountId}/ledger`；同一客户重复开户返回 409。
- **充值与消费**：`POST /memberships/recharges`、`POST /memberships/consumptions`；充值写 `RECHARGE` 账本；消费按客户定位账户，余额不足且 `allowShared=true` 时由已授权的家庭成员账户代付，仍不足返回 422「账户余额不足」。
- **积分**：`POST /memberships/points/adjustments`；正数增加、负数扣减（条件更新防负），写 `POINTS_ADJUST` 账本并刷新等级。
- **套餐**：`GET/POST /memberships/packages`、`PUT /packages/{packageCode}`、`PUT /packages/{packageCode}/status`、`POST /packages/{packageCode}/purchase`；`TIMES` 生成次卡（`validTo = 购买日 + validDays`），`BALANCE` 入余额，`POINTS` 入积分，权益发放写 `PACKAGE_PURCHASE` 账本；未启用套餐购买返回 422。
- **次卡**：`GET /memberships/cards`、`GET /cards/{cardId}`（含扣次记录）、`POST /cards/{cardId}/consumptions`、`POST /cards/{cardId}/refunds`；扣次写 `crm_member_card_usage`（`CONSUMED`）与 `CARD_CONSUME` 账本，剩余为 0 自动置 `EXHAUSTED`；返还回补次数并写 `CARD_REFUND` 账本，对同一扣次记录重复返还返回 422。
- **共享授权**：`GET/POST /memberships/shares`、`PUT /shares/{shareId}/revoke`；授权人与使用人不能相同，重复有效授权返回 409，撤销后再次撤销返回 422。
- **退款权益返还**：`POST /memberships/refund-reversals`；`RECHARGE` 订单按退款金额扣回余额（不超过当前余额）；`PACKAGE_PURCHASE` 订单作废生成的次卡并返回剩余次数、按退款比例扣回积分；每次扣回写 `REFUND_REVERSAL` / `CARD_VOID_ON_REFUND` 账本。
- **审计**：`MEMBER_ACCOUNT_OPEN` / `MEMBER_RECHARGE` / `MEMBER_CONSUME` / `MEMBER_POINTS_ADJUST` / `MEMBER_REFUND_REVERSAL` / `MEMBER_PACKAGE_CREATE` / `MEMBER_PACKAGE_UPDATE` / `MEMBER_PACKAGE_STATUS` / `MEMBER_PACKAGE_PURCHASE` / `MEMBER_CARD_CONSUME` / `MEMBER_CARD_REFUND` / `MEMBER_SHARE_CREATE` / `MEMBER_SHARE_REVOKE` 均落库，仅记录账户号、金额、次数与业务号，不含客户手机号。

| 权限 | 说明 |
|---|---|
| `crm:member:read` | 查看会员等级、账户、账本、次卡、套餐与共享授权 |
| `crm:member:write` | 开户、充值、余额消费、积分调整、购买套餐、扣次与返还、共享授权 |
| `crm:member:manage` | 维护套餐目录与启停、退款权益返还 |

> 上线提示：V12 中上述 3 个权限默认仅授予 `SUPER_ADMIN`，其他角色需在角色管理中显式分配；余额、积分、次卡均为权益载体，退款返还接口建议只开放给财务或会员主管角色。

### CRM-012 验收清单

| 验收项 | 结果 |
|---|---|
| 并发消费不超扣 | 余额 100 的账户两笔并发消费 80，恰好一笔成功、一笔 422「账户余额不足」，余额不会为负 |
| 每次变动有账本 | 开户、充值、消费、积分调整、套餐购买、扣次、返还、退款返还均写 `crm_member_ledger`，条目记录变动额与变动后快照 |
| 次卡不超扣 | 剩余 2 次的次卡并发扣 2 次仅一笔成功；扣到 0 自动置 `EXHAUSTED`；过期/作废卡扣次 422 |
| 扣次返还 | 返还回补次数并写 `CARD_REFUND` 账本；同一扣次记录重复返还 422 |
| 套餐购买 | `TIMES` 生成次卡并绑定来源订单；`BALANCE`/`POINTS` 直接入账；未启用套餐 422；缺少对应载荷 422 |
| 共享代付 | 余额不足且存在有效 `BALANCE` 授权时由授权人账户扣款；无授权仍返回 422 |
| 积分升级 | 积分跨过门槛后自动升级（如 1000 分升银卡）；扣减不会把积分扣成负数 |
| 退款返还权益 | 充值退款按金额扣回余额；套餐退款作废次卡并返还剩余次数、按比例扣回积分 |
| 数据库迁移 | `V12__member.sql` 新增等级/套餐/次卡/扣次/共享 5 张表、2 个业务号序列、3 项权限，并为账户与账本补充冻结金额、变动后快照与索引 |

## 随访中心（CRM-013）

`crm-followup` 模块承接随访分类、模板、计划、事件触发、任务分配、随访记录与高风险人工队列。计划按事件类型与偏移天数生成任务，**同一计划同一事件键只生成一次**（数据库部分唯一索引 `uq_followup_event` 兜底）；随访答案按模板风险规则评估，命中高风险规则自动进入人工队列（`ESCALATED`）。

- **分类**：`GET/POST /api/v1/followup/categories`、`PUT /categories/{categoryCode}`；种子含出院、术后、慢病、服务满意度、高风险专病 5 类。
- **模板**：`GET/POST /templates`、`PUT /templates/{templateCode}`、`PUT /templates/{templateCode}/status`；`questions` 为问题与选项，`riskRules` 定义 `highRisk` / `mediumRisk` 命中答案，模板每次修改版本号递增。
- **计划**：`GET/POST /plans`、`PUT /plans/{planNo}`、`PUT /plans/{planNo}/status`；触发类型支持 `APPOINTMENT_COMPLETED` / `SERVICE_COMPLETED` / `ORDER_PAID` / `MANUAL`，偏移 0~365 天，可配置默认随访人与渠道；模板必须属于所选分类且分类与模板均启用。
- **事件触发**：`POST /followup/triggers`，入参 `triggerType`、`eventKey`、`customerId`、`occurredAt`；按 `occurredAt + offsetDays` 计算计划时间，重复投递返回 `created=0`、`skipped=N`。
- **任务**：`GET /followup/tasks`（支持状态、分类、风险、随访人、客户、是否升级与计划日期区间）、`POST /followup/tasks`、`GET /tasks/{taskId}`（含问卷与记录）；分配 `PUT /tasks/{taskId}/assign`、开始 `PUT /tasks/{taskId}/start`、取消 `PUT /tasks/{taskId}/cancel`。
- **结果与风险**：`POST /tasks/{taskId}/records`；命中高风险规则任务转 `ESCALATED` 并记录 `escalatedAt` 与升级原因，中风险完成但标记 `MEDIUM`，低风险正常完成；一个任务只允许提交一次结果。
- **人工队列**：`GET /followup/tasks?status=ESCALATED&escalationRequired=true`；手动升级 `PUT /tasks/{taskId}/escalate`、处置 `PUT /tasks/{taskId}/resolve`（可附处置说明，写入任务 `result_payload.resolution`）。
- **审计**：`FOLLOWUP_CATEGORY_CREATE/UPDATE`、`FOLLOWUP_TEMPLATE_CREATE/UPDATE/STATUS`、`FOLLOWUP_PLAN_CREATE/UPDATE/STATUS`、`FOLLOWUP_TASK_CREATE`、`FOLLOWUP_TRIGGER`、`FOLLOWUP_TASK_ASSIGN`、`FOLLOWUP_TASK_START`、`FOLLOWUP_RECORD`、`FOLLOWUP_ESCALATE`、`FOLLOWUP_RESOLVE`、`FOLLOWUP_CANCEL` 均落库，仅记录任务号、分类、事件键与风险等级。

| 权限 | 说明 |
|---|---|
| `crm:followup:read` | 查看分类、模板、计划、任务、记录与人工队列 |
| `crm:followup:write` | 创建任务、事件触发、分配随访人、开始随访与提交结果 |
| `crm:followup:manage` | 维护分类/模板/计划、手动升级、处置人工队列与取消任务 |

> 上线提示：V13 中上述 3 个权限默认仅授予 `SUPER_ADMIN`；随访人使用系统用户账号（V13 移除了基线 `crm_staff` 外键），分配前请确认对应账号已启用并有随访职责。

### CRM-013 验收清单

| 验收项 | 结果 |
|---|---|
| 任务按规则生成 | `POST /followup/triggers` 按启用计划生成任务数等于匹配计划数，计划时间 = 事件时间 + 偏移天数 |
| 重复事件不重复创建 | 同一 `eventKey` 再次触发返回 `created=0`、`skipped=N`，任务总数不变 |
| 高风险回答进入人工队列 | 命中 `highRisk` 规则的答案使任务转 `ESCALATED`、`riskLevel=HIGH`、`escalationRequired=true`，可在人工队列查询并处置 |
| 中低风险正常完成 | 命中 `mediumRisk` 标记 `MEDIUM` 并完成，未命中为 `LOW` 并完成，均不进入人工队列 |
| 随访记录唯一 | 同一任务重复提交结果返回 409；已完成/已升级/已取消任务提交结果返回 422 |
| 状态机 | 开始仅允许 `PENDING`；分配仅允许 `PENDING`；处置仅允许 `ESCALATED`；终态任务取消返回 422 |
| 配置校验 | 重复分类编码 409；非法渠道 400；模板与分类不匹配 422；分类或模板停用时新建计划 422 |
| 数据库迁移 | `V13__followup.sql` 新增分类/模板/计划/记录 4 张表，扩展任务表 8 个字段与 `uq_followup_event` 唯一索引，新增 5 分类、4 模板、4 计划种子与 3 项权限 |

## 投诉表扬中心（CRM-014）

`crm-complaint` 模块承接投诉、表扬与建议工单的建单、分派、处理动作、整改、患者反馈、结案复核与超时升级。工单状态机为 `OPEN→ASSIGNED→PROCESSING→RECTIFYING→PENDING_REVIEW→CLOSED`，复核退回回到 `PROCESSING`，`CLOSED`/`CANCELLED` 为终态；**处理人与结案审核人分权**，审核人不得为当前处理人。

- **建单**：`GET/POST /api/v1/complaints`；建单时按严重程度套用默认 SLA（紧急 4 小时、较高 24 小时、一般 48 小时、低 72 小时），也可显式指定处理时限；指定处理人时直接进入 `ASSIGNED`。
- **查询**：支持工单号/标题关键字、类型、等级、状态、处理人、客户、是否升级、是否超期与创建日期区间；列表返回脱敏手机号、责任部门、超期标记与升级层级。
- **分派与处理**：`PUT /complaints/{ticketId}/assign`、`PUT /complaints/{ticketId}/start`；分派仅允许 `OPEN`/`ASSIGNED`，开始处理仅允许 `ASSIGNED`。
- **处理动作**：`POST /complaints/{ticketId}/actions`，动作类型含联系、调查、回复、备注、整改、反馈、升级、复核等，全部写入 `crm_complaint_action` 时间线。
- **整改**：`POST /complaints/{ticketId}/rectifications`、`PUT /complaints/{ticketId}/rectifications/{rectificationId}/submit`；处理中/整改中才能登记整改，整改未提交不能提交结案复核。
- **患者反馈**：`POST /complaints/{ticketId}/feedback`，记录 1~5 分评分、是否满意、意见与渠道。
- **复核结案**：`PUT /complaints/{ticketId}/submit-review`（需至少一条处理记录且无未提交整改）、`PUT /complaints/{ticketId}/review`（`PASS` 结案并核验整改，`REJECT` 退回处理）；结案审核需 `crm:complaint:review` 权限。
- **升级**：手动升级 `PUT /complaints/{ticketId}/escalate`；超时扫描 `POST /complaints/escalation-scan` 与每 5 分钟执行的定时任务对超过处理时限且未结案的工单自动升级，使用条件更新 `where escalated = false` 保证同一工单不重复升级。
- **取消**：`PUT /complaints/{ticketId}/cancel`，终态工单拒绝变更。
- **审计**：`COMPLAINT_CREATE`、`COMPLAINT_ASSIGN`、`COMPLAINT_START`、`COMPLAINT_ACTION`、`COMPLAINT_RECTIFICATION_CREATE`、`COMPLAINT_RECTIFICATION_SUBMIT`、`COMPLAINT_FEEDBACK`、`COMPLAINT_SUBMIT_REVIEW`、`COMPLAINT_REVIEW`、`COMPLAINT_ESCALATE`、`COMPLAINT_CANCEL`、`COMPLAINT_ESCALATION_SCAN` 均落库，仅记录工单号、类型、等级、处理人与流转结果。

| 权限 | 说明 |
|---|---|
| `crm:complaint:read` | 查看工单、处理动作、整改、反馈与复核记录 |
| `crm:complaint:write` | 建单、登记处理动作、整改、患者反馈与提交复核 |
| `crm:complaint:assign` | 分派与转派工单、设置责任部门与处理时限 |
| `crm:complaint:review` | 结案审核（通过结案或退回），审核人不得为当前处理人 |
| `crm:complaint:manage` | 手动升级、取消工单与触发超时升级扫描 |

> 上线提示：V14 中上述 5 个权限默认仅授予 `SUPER_ADMIN`；处理人与审核人使用系统用户账号（V14 移除了基线 `crm_staff` 外键），请按岗位分别授予 `crm:complaint:write` 与 `crm:complaint:review`，避免同一人自审自结。

### CRM-014 验收清单

| 验收项 | 结果 |
|---|---|
| 处理人与结案审核分权 | 审核接口要求 `crm:complaint:review` 权限；审核人等于当前处理人时返回 422「结案审核人不得为当前处理人」 |
| 超时自动升级 | `POST /complaints/escalation-scan` 将 `dueAt` 已过且未结案工单标记升级并写时间线；重复扫描返回 `escalated=0`，同一工单不重复升级 |
| 所有动作留痕 | 建单、分派、开始、处理动作、整改登记/提交、患者反馈、提交复核、复核通过/退回、升级、取消均写入 `crm_complaint_action` 并记录操作人 |
| 状态机 | 分派仅 `OPEN`/`ASSIGNED`；开始仅 `ASSIGNED`；提交复核仅 `PROCESSING`/`RECTIFYING`；复核仅 `PENDING_REVIEW`；终态工单动作/升级/取消均返回 422 |
| 复核前置校验 | 无处理记录不能提交复核；存在 `PENDING` 整改任务时提交复核返回 422 |
| 整改闭环 | 复核通过后 `SUBMITTED` 整改自动转 `VERIFIED` 并记录核验人；重复提交整改返回 422 |
| 建单校验 | 非法类型/等级/来源返回 400；处理人或责任部门不存在返回 404 |
| 数据库迁移 | `V14__complaint.sql` 扩展基线工单表 11 个字段并重建状态/类型/等级/来源约束，新增处理动作、整改、反馈、复核 4 张表、工单号序列与 5 项权限 |

## 在线客服与人工工作台（CRM-015）

`crm-chat` 模块承接在线会话、消息、转人工、排队、人工接管、转接与会话历史。会话状态机为 `AI→QUEUED→ACTIVE→CLOSED`，`CLOSED` 为终态。

- **会话与历史**：`GET/POST /api/v1/chat/sessions`、`GET /chat/sessions/{sessionId}`；支持状态、渠道、客户、接待人、优先级与日期筛选，详情返回最近 200 条消息、转人工记录与会话轨迹。
- **消息**：`GET/POST /chat/sessions/{sessionId}/messages`；消息发送方支持 `CUSTOMER/AI/STAFF/SYSTEM`，客服消息仅限人工接待且为当前接待人，AI 消息仅可写入 AI 接待阶段；`clientMessageId` 保证渠道重试幂等。
- **转人工规则**：`POST /chat/sessions/{sessionId}/handoff`
  - `explicit`（客户明确要求人工）第一次返回 `needsConfirmation=true`，仅提示确认并提供人工入口；第二次请求自动进入人工队列；
  - `complaint`、`urgent`、`sensitive`、`low_confidence` 直接进入人工队列，优先级分别为高、紧急、高、普通；
  - 入队时生成 AI 接待摘要，接管时随完整上下文一并返回。
- **人工队列**：`GET /chat/queue`，仅返回状态为 `QUEUED` 的会话，按「紧急→高→普通→低」再按入队时间排序，返回等待秒数与队列位置。
- **接管**：`POST /chat/sessions/{sessionId}/claim`；条件更新 `where status='QUEUED'` 保证并发接管只有一个成功，失败返回 409；成功后返回全部历史消息、AI 摘要、患者授权范围与转人工记录。
- **转接**：`POST /chat/sessions/{sessionId}/transfer`；当前接待客服可转接本人会话，`crm:chat:manage` 可转接任意进行中会话，转接不丢失任何历史。
- **结束**：`POST /chat/sessions/{sessionId}/close`；终态不可再发送消息，重复结束返回 422。
- **权限隔离**：普通客服只能查看排队会话与本人接待/历史会话，访问他人会话返回 403；全部历史仅 `crm:chat:manage` 可见。
- **审计**：`CHAT_SESSION_CREATE`、`CHAT_MESSAGE_SEND`、`CHAT_HANDOFF_CONFIRM`、`CHAT_HANDOFF_QUEUE`、`CHAT_SESSION_CLAIM`、`CHAT_SESSION_TRANSFER`、`CHAT_SESSION_CLOSE` 均落库，仅记录会话号、渠道、发送方与转人工原因。

| 权限 | 说明 |
|---|---|
| `crm:chat:read` | 查看排队会话、本人接待会话、消息与会话轨迹 |
| `crm:chat:handle` | 创建会话、收发消息、请求转人工、接管、转接本人会话与结束会话 |
| `crm:chat:manage` | 查看全部会话历史、转接任意进行中会话、强制结束会话 |

> 上线提示：V15 中 3 项客服权限默认仅授予 `SUPER_ADMIN`，请按客服班组分配 `crm:chat:read` / `crm:chat:handle`，主管另授 `crm:chat:manage`。本期未接入真实 IM 渠道与 AI 引擎，AI 消息由 `senderType=AI` 接口写入（供 CRM-016 接入）；患者 H5 会话入口与渠道网关留待后续渠道集成。

### CRM-015 验收清单

| 验收项 | 结果 |
|---|---|
| 首次确认与二次排队 | 首次 `explicit` 返回 `needsConfirmation=true` 且状态仍为 `AI`；第二次请求返回 200 并进入 `QUEUED`，队列位置 1 |
| 直接转人工 | 投诉、紧急、敏感、低置信度直接入队；投诉优先级 `high`、紧急优先级 `urgent`，队列按优先级排序 |
| 接管保留 AI 上下文 | 接管返回全部历史消息、`aiSummary`、`authorizationScope` 与转人工记录；转人工记录状态变为 `ACCEPTED` |
| 并发接管 | 条件更新保证只有一个客服接管成功，重复接管返回 409「会话已被其他客服接管」 |
| 会话权限隔离 | 非接待客服查看他人会话返回 403；主管具备 `crm:chat:manage` 可查看全部并转接 |
| 转接完整性 | 转接后接待人变更、from/to 落库，消息、摘要与授权范围完整保留 |
| 终止与终态 | 结束会话后状态 `CLOSED`；终态发送消息、重复结束均返回 422 |
| 消息幂等 | 相同 `clientMessageId` 重复提交仅入库一条消息 |
| 数据库迁移 | `V15__chat.sql` 扩展基线会话/消息表，重建状态/接待方/优先级约束，新增 `crm_chat_handoff`、`crm_chat_event`、会话号序列、消息幂等索引与 3 项权限 |

## AI 知识库与数字人（CRM-016）

`crm-knowledge` 模块承接知识分类、文档、文件上传解析、切片、审核发布、版本快照、失效下架与 RAG 检索问答。文档状态机为 `DRAFT→REVIEWING→PUBLISHED→OFFLINE`，仅「已发布」文档的当前版本切片参与 AI 检索，未经授权的患者数据不得进入知识库（送审前强制敏感信息扫描）。

- **分类**：`GET/POST /api/v1/knowledge/categories`、`PUT /knowledge/categories/{categoryId}`；编码唯一，停用后不再出现在新建下拉中。
- **文档与上传**：`POST /knowledge/documents` 手工录入、`POST /knowledge/documents/upload` 上传 `.txt/.md`（≤5MB）自动解析；内容按 ≤500 字合并切片，初始均为草稿，`PUT /knowledge/documents/{documentId}/content` 仅草稿/已下架可编辑并重建草稿切片。
- **送审敏感扫描**：`POST /knowledge/documents/{documentId}/submit`；命中身份证号（`\d{17}[\dXx]`）或手机号（`1[3-9]\d{9}`）等患者标识返回 422 拒绝入库，保证患者数据不进入 AI 知识库。
- **审核发布与版本**：`POST .../approve` 版本号 +1、草稿切片转正并生成版本快照（标题/摘要/切片数/变更说明），并发审核以旧版本号条件更新冲突返回 409；`POST .../reject` 驳回必填原因并退回草稿。
- **失效下架**：`POST .../offline` 必填下架原因；下架后检索立即不再引用，历史版本与审核留痕保留。
- **RAG 检索问答**：`POST /knowledge/rag/ask`；中文双字组合 + 英文词元打分，仅取已发布文档当前版本切片，答案携带引用来源（知识编号 `KB7xxxxx` + 版本 + 切片号与相关度），置信度低于 0.35 返回 `needsHandoff=true` 提示转人工；携带 `sessionId` 时自动调用客服会话转人工（原因 `low_confidence`）进入人工队列。
- **AI 随访话术**：`scene=followup` 输出【随访话术草稿】，供随访人员复核后使用，不直接触达患者。
- **问答追溯**：每次问答写入 `crm_ai_query_log`（问题、答案、置信度、引用、场景、是否转人工、提问人），`GET /knowledge/rag/logs` 分页可查。

| 权限 | 说明 |
|---|---|
| `crm:knowledge:read` | 查看分类、文档、问答记录与执行 RAG 检索 |
| `crm:knowledge:write` | 上传解析、编辑切片、提交审核 |
| `crm:knowledge:review` | 审核通过、驳回与失效知识下架 |

> 上线提示：V16 中 3 项知识库权限默认仅授予 `SUPER_ADMIN`，建议知识维护岗授 `read/write`、知识审核岗授 `read/review` 实现维护与发布分权。本期 RAG 为内置关键词检索实现，接入外部大模型时替换 `AiAnswerService` 检索与生成边界即可，引用追溯与低置信度转人工契约不变。

### CRM-016 验收清单

| 验收项 | 结果 |
|---|---|
| AI 答案可追溯 | RAG 答案携带引用来源（知识编号、版本、切片号、相关度），问答全程写入 `crm_ai_query_log` |
| 低置信度转人工 | 置信度 < 0.35 返回 `needsHandoff=true`；携带 `sessionId` 时自动请求该会话转人工进入客服队列 |
| 知识失效可下架 | 下架必填原因，`PUBLISHED→OFFLINE` 后检索立即不再引用该知识 |
| 未经授权患者数据拦截 | 送审前扫描身份证号/手机号，命中返回 422，文档无法进入审核与发布 |
| 上传解析与切片 | `.txt/.md` 上传自动解析并按 ≤500 字合并切片，草稿状态不参与检索 |
| 审核发布与版本 | 发布版本号 +1 并生成版本快照；并发审核冲突返回 409 |
| 仅已发布可检索 | 草稿/待审/驳回/下架文档的切片均不出现在检索结果中 |
| 数据库迁移 | `V16__knowledge.sql` 新增分类、文档、切片、版本、审核、问答日志 6 张表、知识编号序列与 3 项 `crm:knowledge:*` 权限 |

## 呼叫中心与短信（CRM-017）

`backend/crm-callcenter` 模块承接呼叫中心适配、来电弹屏、通话记录、录音索引与访问授权、短信模板与消息发送 Outbox。呼叫中心厂商未定，通过适配器抽象接入：默认未接入，相关接口返回 502，**禁止伪造录音地址与通话数据**。

- **呼叫中心适配器**：`crm.callcenter.enabled` / `crm.callcenter.provider`（环境变量 `CRM_CALLCENTER_ENABLED`、`CRM_CALLCENTER_PROVIDER`）控制接入与厂商实现；上线前需医院确认呼叫中心厂商 API 与录音存储后接入。
- **来电弹屏**：`GET /api/v1/callcenter/incoming-popup?phone=`；按手机号 HMAC-SHA-256 哈希匹配 CRM 客户，仅返回脱敏客户信息与通话历史次数、最近通话时间，不返回明文号码。
- **通话记录**：`GET/POST /callcenter/calls`、`GET /callcenter/calls/{id}`；厂商话单同步以 `vendorCallId` 幂等，重复提交返回既有记录；人工补录来源 `source=manual`；支持按方向、状态、客户、关联类型与日期区间筛选。
- **通话关联**：`POST /callcenter/calls/{id}/link` 可关联客户、投诉工单、客服会话或预约；目标对象不存在返回 404，不创建悬空关联。
- **录音索引与访问授权**：`GET /callcenter/calls/{id}/recording`；录音仅存索引（key/存储类型/时长/大小），访问地址由该接口即时签发，需独立权限 `crm:call:recording:read`，每次访问写入审计 `CALL_RECORDING_ACCESS`；该通话无录音返回 422，呼叫中心未接入返回 502。
- **短信模板**：`GET/POST /callcenter/sms/templates`、`GET/PUT /callcenter/sms/templates/{id}`、`POST /callcenter/sms/templates/{id}/status`；`content` 中 `{{变量}}` 占位符自动提取为变量清单，编码重复返回 409，支持启用/停用。
- **消息 Outbox**：`POST /callcenter/messages` 先落 `crm_message_outbox` 再由派发器发送；`bizKey` 去重幂等（重复提交返回既有消息，不重复发送）；模板与 `content` 二选一否则 400，模板不存在 404，模板停用或变量缺失 422；每 60 秒定时派发（`POST /callcenter/messages/dispatch` 为同一入口，返回 `scanned/sent/failed/at`）；失败按 300s×2^(n-1) 指数退避最多自动重试 3 次，超限后仅可人工重试（`POST /callcenter/messages/{id}/retry`，仅 `PENDING/FAILED` 可重试，其余 422）；派发与重试均用条件更新防止并发重发。
- **短信服务商**：`crm.sms.provider`（环境变量 `CRM_SMS_PROVIDER`，默认 `simulated`）；接入服务商协议后替换实现，接口契约不变。
- **敏感数据**：主叫/被叫/接收号码仅返回脱敏值（前3后4），后端以 HMAC-SHA-256 哈希检索存储，明文不入库、不进审计。

| 权限 | 说明 |
|---|---|
| `crm:call:read` | 查看来电弹屏、通话记录列表与详情 |
| `crm:call:write` | 同步/补录通话记录、关联通话与业务对象 |
| `crm:call:recording:read` | 获取录音访问授权（独立授权、访问留审计） |
| `crm:sms:read` | 查看短信模板与消息发送记录 |
| `crm:sms:template:manage` | 新建、修改、启停用短信模板 |
| `crm:sms:send` | 发送消息、人工重试与触发 Outbox 派发 |

> 上线提示：V17 中 6 项权限默认仅授予 `SUPER_ADMIN`，建议录音访问授权 `crm:call:recording:read` 单独授予质检/主管岗，保证每次录音收听可审计。呼叫中心厂商未定：上线前必须由医院确认呼叫中心厂商 API 与录音存储方式，配置 `CRM_CALLCENTER_ENABLED=true` 与 `CRM_CALLCENTER_PROVIDER` 后接入，未接入期间通话记录仍可人工补录、录音授权接口固定返回 502。短信渠道默认 `CRM_SMS_PROVIDER=simulated`（模拟发送、状态可查），上线前替换为医院确认的服务商实现。

### CRM-017 验收清单

| 验收项 | 结果 |
|---|---|
| 呼叫中心未接入返回 502 | `CRM_CALLCENTER_ENABLED=false` 时录音授权接口返回 502，不伪造录音地址 |
| 来电弹屏脱敏匹配 | 按手机号哈希命中客户，仅返回脱敏档案（前3后4）与通话历史统计 |
| 话单幂等同步 | 同一 `vendorCallId` 重复提交仅入库一条 `crm_call_record`，返回既有记录 |
| 通话关联完整性 | 关联客户/投诉工单/会话/预约成功回显 `linkType/linkNo`；目标不存在 404 |
| 录音访问审计 | 每次录音授权写入 `CALL_RECORDING_ACCESS` 审计并累计 `accessCount`；无录音 422 |
| 短信模板变量提取 | `{{变量}}` 自动提取变量清单；编码重复 409；停用后按模板发送 422 |
| 消息幂等去重 | 相同 `bizKey` 重复提交返回既有消息，不重复发送 |
| 指数退避重试 | 失败按 300s×2^(n-1) 排期最多 3 次，超限仅人工重试；条件更新防并发重发 |
| 定时派发 | 60 秒定时任务与 `POST /callcenter/messages/dispatch` 返回 `scanned/sent/failed` |
| 号码脱敏存储 | 号码仅存密文/HMAC 哈希/脱敏值，接口与审计明文号码 0 条 |
| 数据库迁移 | `V17__callcenter.sql` 新增通话记录、录音索引、短信模板、消息 Outbox 4 张表、2 个业务号序列、3 条短信模板种子与 6 项权限 |

## 商保中心（CRM-018）

`backend/crm-insurance` 模块承接保险公司与保单主数据、商保案件 12 态状态机、询价/预授权/理赔、材料登记、邮件往来与按公司+账期对账。数据库迁移 `V18__insurance.sql` 新增 8 张表、1 个业务号序列与 6 项权限，接口契约见 `04-openapi.yaml` 的「商保中心」tag。

- **保险公司与保单**：`GET/POST /api/v1/insurance/companies`、`GET/PUT /insurance/companies/{id}`、`POST /insurance/companies/{id}/status`；`GET/POST /insurance/policies`、`GET /insurance/policies/{id}`、`POST /insurance/policies/{id}/status`。公司编码与保单号全局唯一，重复返回 409；客户不存在 404；停用公司不得新建保单（422）；保单非 `active` 或已过期不得建案（422）；保单有效期起止倒置返回 400。联系人电话仅存 HMAC 哈希与前 3 后 4 脱敏值。
- **案件 12 态状态机**：`registered → eligibility_check → quotation → pre_authorization → service_done → claim_submitted → under_review → approved/partially_approved/rejected → reconciled`；`claim_submitted` 也可先进入 `supplement_required` 补充材料后再回 `under_review`，三种理赔结论均可进入 `reconciled`，`reconciled` 为终态。非法流转返回 422，目标状态必备字段缺失返回 422，每次流转均写入 `crm_insurance_event` 审计（起止状态、操作人、备注与金额快照）。
- **询价/预授权/理赔**：`POST /insurance/cases/{id}/transition`；按状态机校验后分别落库询价金额与时间、预授权号/金额与时间、理赔号与提交时间、理赔结论/核定金额/原因与决定时间，回款到账金额单独记录。
- **材料 SHA-256 哈希**：`GET/POST /insurance/cases/{id}/materials`、`POST /insurance/materials/{id}/status`；材料登记必须提供对象存储键与 64 位十六进制 SHA-256 哈希，格式非法返回 400，材料状态为 `pending/valid/invalid`；已对账案件不允许新增材料（422）。
- **邮件 Message-ID 幂等与附件哈希**：`GET/POST /insurance/cases/{id}/emails`；入站邮件按 Message-ID 唯一，相同 `messageId` 重复提交幂等返回既有邮件、不重复入库；每个附件必须提供 SHA-256 哈希，缺失或格式非法返回 400。
- **人工分派**：`POST /insurance/cases/{id}/assign` 指定 `handlerUserId`；处理人不存在返回 404，分派与流转均写入 `crm_insurance_event`。
- **对账按公司+账期汇总**：`GET/POST /insurance/reconciliations`、`GET /insurance/reconciliations/{id}`、`POST /insurance/reconciliations/{id}/confirm`；按账期内已决案件汇总医院应收与实际到账，`|差额| < 0.01` 判为 `matched`，否则 `mismatch`；账期非 `YYYY-MM` 返回 400，公司不存在返回 404；同公司同账期重复生成按最新案件重算并更新同一批次，确认后批次锁定、重复确认返回 422。

金额口径说明：

| 金额口径 | 字段 | 说明 |
|---|---|---|
| 医院应收 | `hospitalReceivable` | 本次服务医院应收总额，独立口径 |
| 患者自付 | `patientPay` | 患者自付部分，独立口径 |
| 预计赔付 | `estimatedPayout` | 按保单、预授权与理赔结论测算的预计可赔金额，独立字段，**不得视为已收款**，不得被实际到账覆盖 |
| 实际到账 | `actualReceived` | 保险公司实际回款金额，仅由对账/回款登记写入，独立字段 |

> 四类金额分别存储与展示，状态流转、投保对账汇总均不得互相覆盖；收款与收入统计只认 `actualReceived`，`estimatedPayout` 不计入实收。

| 权限 | 说明 |
|---|---|
| `crm:insurance:read` | 查看保险公司、保单、案件、材料、邮件与对账数据 |
| `crm:insurance:manage` | 维护保险公司与保单主数据、启停主数据 |
| `crm:insurance:case:write` | 创建商保案件、人工分派处理人与状态流转 |
| `crm:insurance:material:write` | 上传登记案件材料并标记材料有效性 |
| `crm:insurance:email:write` | 登记商保往来邮件与附件哈希 |
| `crm:insurance:reconcile` | 生成商保对账批次并确认对账结果 |

> 上线提示：`V18__insurance.sql` 中 6 项商保权限默认仅授予 `SUPER_ADMIN`，建议按商保专员（`crm:insurance:read` + `crm:insurance:case:write`）、材料录入（`crm:insurance:material:write`）、邮件登记（`crm:insurance:email:write`）与对账复核（`crm:insurance:reconcile`）分岗授权，主数据维护单独授予 `crm:insurance:manage`。本期不直连保险公司理赔 API，`settlementType=api/email/manual` 仅表达结算通道；邮件接入需由邮件网关保证 Message-ID 与附件 SHA-256 哈希。

### CRM-018 验收清单

| 验收项 | 结果 |
|---|---|
| 保险公司/保单主数据：公司编码与保单号重复返回 409，客户不存在 404，有效期倒置 400，停用公司新建保单 422 | 编码/保单号重复实测 409、有效期倒置实测 400；客户不存在与停用公司分支由单元测试覆盖 |
| 案件创建：保单不存在 404，保单非生效中或已过期 422，初始状态 `registered` | 实测初始状态 `registered`、案件号 `IC8xxxxx`；404/422 分支由单元测试覆盖 |
| 12 态状态机：仅允许既定迁移，非法流转 422，`reconciled` 终态不可再流转 | 实测 `eligibility_check → approved` 返回 422；完整 8 步迁移全部成功，终态约束由状态机白名单保证 |
| 必备字段校验：询价金额、预授权号、理赔号、核定赔付金额、驳回原因、实际到账金额缺失分别返回 422 | 询价金额、预授权号、理赔号、实际到账缺失均实测 422；驳回原因与核定金额缺失由单元测试覆盖 |
| 核定金额上限：核定赔付金额不得大于医院应收，超出返回 422 | 实测核定 99999 超过应收 10000 返回 422 |
| 事件审计：创建、分派、状态流转、材料与邮件登记均写入 `crm_insurance_event`，含起止状态与操作人 | 实测单案件 8 条 `STATE_CHANGE` 事件均带 `fromState/toState`；材料、邮件事件独立留痕 |
| 金额独立口径：`estimatedPayout` 与 `actualReceived` 独立存储、互不覆盖，收款统计只取 `actualReceived` | 实测审批后 `estimatedPayout=9000` 且 `actualReceived=0`；对账写入 `actualReceived=8500` 后预计赔付仍为 9000，差额 `-500` |
| 材料哈希：`fileHash` 非 64 位十六进制返回 400，已对账案件新增材料 422，材料状态仅 `valid/invalid` | 实测非法哈希 400、64 位 SHA-256 登记成功且状态 `pending`；已对账拦截由单元测试覆盖 |
| 邮件幂等与附件哈希：inbound 相同 `messageId` 反复提交仅一条且返回既有邮件，附件哈希缺失/非法 400 | 实测两条附件哈希入库、重复 `messageId` 返回同一邮件 ID、非法附件哈希 400 |
| 人工分派：处理人不存在 404，分派结果回显 `handlerName` 并写入事件 | 接口与事件实现完成，冒烟未覆盖分派分支（由单元测试覆盖） |
| 对账汇总：按公司+账期汇总医院应收与实际到账，账期非法 400，公司不存在 404，同公司同账期重算不重复建批次，重复确认 422 | 实测账期非法 400；汇总结论 `10000/0/-10000 mismatch` → 回款后 `10000/8500/-1500 mismatch`；确认成功、重复确认 422 |
| 权限分权：6 项 `crm:insurance:*` 权限生效，只读账号写操作 403（材料/邮件/对账各自独立） | V18 已写入 6 项权限并授予 `SUPER_ADMIN`，全部写接口标注 `@PreAuthorize`；只读账号 403 未在冒烟中单独构造 |
| 数据库迁移：`V18__insurance.sql` 8 张表、案件号序列、6 项权限可重复部署 | PostgreSQL 16.4 实测 Flyway 由 v17 增量迁移至 v18 成功，8 张表、`(company_id, period)` 唯一约束、`message_id` 部分唯一索引与 6 项权限均已创建 |

## 运营分析（CRM-019）

`backend/crm-analytics` 模块承接运营指标口径定义、运营看板、报表定义与取数、数据导出申请-审批-下载闭环，共 14 个接口。数据库迁移 `V19__analytics.sql` 新增 3 张表（`crm_metric_definition` 含 `caliber_description` 口径说明 NOT NULL、`crm_report_definition`、`crm_export_request` 含 `csv_content/file_hash/row_count`）、12 条种子指标、2 条种子报表（`RPT_METRIC_SUMMARY` / `RPT_CUSTOMER_DETAIL`）与 6 项权限（sort 790~840），接口契约见 `04-openapi.yaml` 的「Analytics」tag。

- **指标定义（口径强制）**：`GET/POST /api/v1/analytics/metrics`、`PUT /analytics/metrics/{metricId}`、`PUT /analytics/metrics/{metricId}/status`；`caliberDescription` 指标口径说明与 `dataSource` 数据来源为必填（验收要求：每项指标必须有口径说明），指标编码全局唯一，重复返回 409；12 条种子指标覆盖新增客户数、客户总数、线索数、线索转化率、预约量、爽约率、订单实收金额、随访完成率、投诉量、电话接通率、短信送达率与商保实收金额。
- **运营看板**：`GET /analytics/dashboard?from&to`；`from`/`to` 必填（YYYY-MM-DD），跨度超过 366 天返回 400；返回 `dataFrom/dataTo/generatedAt` 使数据时间范围与生成时间可追溯；`scopeAll=false` 时无组织归属的指标 `value=null` 且 `scopeNote` 说明需全院权限。
- **报表定义与取数**：`GET/POST /analytics/reports`、`PUT /analytics/reports/{reportId}/status`、`GET /analytics/reports/{reportCode}/data?from&to`；报表类型 `metric_summary` 指标汇总 / `customer_detail` 客户明细；`customer_detail` 仅返回脱敏列（`customer_no/name/phone_masked/id_no_masked/source_code/status/created_at`），非全院数据权限用户查询返回 403；取数结果携带 `dataFrom/dataTo/generatedAt/rowCount`，满足报表可追溯要求。
- **导出审批闭环**：`POST /analytics/exports` 提交申请（`reason` 必填）→ `PUT /analytics/exports/{exportId}/approve|reject` 审批（仅 `pending` 可处理，否则 422；驳回必须填写理由）→ `GET /analytics/exports/{exportId}/download` 下载；审批通过时生成脱敏 CSV 并记录 SHA-256 `fileHash` 与 `rowCount`，未批准下载返回 422；仅申请人本人或具备 `crm:analytics:export:approve` 者可下载（否则 403）；`GET /analytics/exports` 无审批权限的用户仅见本人申请。
- **组织数据权限**：看板与报表取数按用户 DataScope（全院 / 本组织及下级 / 本组织 / 仅本人 / 自定义）生效，无全院权限不暴露无组织归属指标与未脱敏明细。
- **审计**：`METRIC_CREATE` / `METRIC_UPDATE` / `METRIC_STATUS`、`REPORT_CREATE` / `REPORT_STATUS`、`EXPORT_REQUEST` / `EXPORT_APPROVE` / `EXPORT_REJECT` / `EXPORT_DOWNLOAD` 全部写入 `sys_audit_log`。
- **错误码**：400 `VALIDATION_ERROR`、403 `FORBIDDEN`、404 `RESOURCE_NOT_FOUND`、409 `BUSINESS_CONFLICT`、422 `BUSINESS_RULE_FAILED`，统一 `ApiResponse` 包裹返回。

| 权限 | 说明 |
|---|---|
| `crm:analytics:read` | 查看指标定义、运营看板、报表与导出申请记录 |
| `crm:analytics:metric:manage` | 新建、修改指标定义与启用/停用指标 |
| `crm:analytics:report:manage` | 新建报表定义与启用/停用报表 |
| `crm:analytics:export:request` | 提交数据导出申请 |
| `crm:analytics:export:approve` | 审批通过/驳回导出申请，并查看全部申请 |
| `crm:analytics:export:download` | 下载已审批通过的脱敏 CSV 导出文件 |

> 上线提示：`V19__analytics.sql` 中 6 项 `crm:analytics:*` 权限默认仅授予 `SUPER_ADMIN`，建议按数据查看（`crm:analytics:read`）、指标/报表配置（`crm:analytics:metric:manage`、`crm:analytics:report:manage`）、导出申请（`crm:analytics:export:request`）与导出审批/下载（`crm:analytics:export:approve`、`crm:analytics:export:download`）分岗授权，导出审批仅授予运营管理员，保证申请与审批互相制约、全程审计。

### CRM-019 验收清单

| 验收项 | 结果 |
|---|---|
| 每项指标必须有口径说明：`caliberDescription` 接口层必填（缺失 400）+ 数据库 `caliber_description` NOT NULL，看板卡片随指标携带口径 | 通过：冒烟实测 12 条种子指标口径说明齐备，缺失口径创建返回 400，看板卡片与汇总报表行均携带 `caliberDescription` |
| 导出脱敏：审批生成脱敏 CSV（手机号/证件号仅脱敏值）并记录 SHA-256 哈希与行数，未批准下载 422、越权下载 403 | 通过：下载 CSV 含 `138****xxxx` 脱敏值、无明文手机号与 11 位连续数字；`file_hash` 为 64 位十六进制且记录行数；未批准下载 422、`self` 范围用户下载客户明细 403 |
| 报表可追溯时间范围：看板与报表取数返回 `dataFrom/dataTo/generatedAt`，可核对数据时间范围与生成时间 | 通过：看板与报表响应均回显 `dataFrom/dataTo/generatedAt`，CSV 首行注释数据时间范围与生成时间；跨度 >366 天拒绝 400 |

## 运维监控与发布回滚（CRM-020）

`crm-ops` 模块提供接口与定时任务监控、阈值告警、备份恢复与发布回滚能力，所有运维接口受 RBAC 约束并全程审计。

- **接口监控**：`OpsMetricsFilter` 记录各 API 的请求量、5xx 错误率、平均耗时与 401 认证失败次数，经 `/actuator/prometheus` 暴露；`crm.ops.metrics.endpoint-public`（环境变量 `CRM_OPS_METRICS_PUBLIC`）控制 Prometheus 抓取端点是否允许匿名访问，默认关闭需认证。
- **任务监控**：`JobMonitor` 统一埋点预约提醒、短信 outbox、投诉升级 3 个定时任务，每次执行写入 `crm_ops_job_run`（成功/失败、耗时、异常摘要），超过 3 倍调度周期未上报即判定失联。
- **告警规则**：内置 8 条规则（数据库不可用、任务失联、任务失败、备份过期、备份失败、接口 5xx 比例、接口平均耗时、认证失败激增），`OpsAlertJob` 周期评估产生 OPEN 告警，恢复后自动 RESOLVED；同一去重键（`alert_code + 未关闭状态`）部分唯一索引保证不重复告警；支持 `POST /api/v1/ops/alerts/raise` 接入外部日志告警，`POST /alerts/{id}/ack` 确认（幂等）。
- **备份与恢复**：`ops/pg/backup.ps1|sh`（pg_dump 自定义格式 + SHA-256 校验 + 保留策略清理）、`ops/pg/restore.ps1|sh`（校验哈希后恢复并检查表与 Flyway 版本）、`ops/pg/drill.ps1|sh`（备份→恢复演练库→对比行数与迁移版本→调用 `/ops/backups` 登记并回写 `restore_verified`→清理演练库）。
- **部署与回滚**：`ops/deploy/deploy.ps1|sh` 预检（Java、端口、磁盘）→ 部署前自动全量备份 → 保留旧 jar 为 `.prev` → 启动并等待就绪探针 → 失败自动回滚上一版本并保留坏包为 `.failed`；`ops/deploy/rollback.ps1|sh` 手工回滚。
- **日志与告警联动**：`logback-spring.xml` 输出 JSON 文件日志（`CRM_LOG_PATH`），`ops/logging/log-alert.ps1|sh` 周期扫描 ERROR 级别日志并调用 `/ops/alerts/raise` 上报。
- **监控栈**：`ops/monitoring/` 提供 Prometheus 抓取与告警规则、Alertmanager 配置；`docker compose --profile ops up` 启动 prometheus 与 alertmanager。
- **运维手册**：`ops/运维手册.md` 覆盖部署、回滚、备份恢复演练、告警处置与常见故障排查步骤。

### CRM-020 验收清单

| 验收项 | 结果 |
|---|---|
| 备份恢复演练通过 | 通过：`ops/pg/drill.ps1` 实测 PASS——备份 326KB 并记录 SHA-256，恢复至演练库后关键表行数与 Flyway 版本（20/20）与源库一致，结果经 `/ops/backups` 登记并回写 `restore_verified=true` |
| 关键接口告警生效 | 通过：`OpsAlertJob` 实测自动产生 `BACKUP_STALE`（HIGH）告警并在备份成功后自动 RESOLVED；8 条规则可查，外部 raise→OPEN→ack→ACKED 全流程冒烟通过，非法级别 400 |
| 发布回滚方案可执行 | 通过：`deploy.ps1` 实测部署成功（部署前自动备份+就绪探针通过）；`rollback.ps1` 手工回滚成功；部署坏包时自动检测进程退出并回滚，服务恢复就绪 |

## 健康检查与验收

```powershell
Invoke-RestMethod http://localhost:8080/actuator/health

# 登录获取访问令牌（/api/v1/system/info 已要求认证）
$login = Invoke-RestMethod -Method Post http://localhost:8080/api/v1/auth/login `
  -ContentType 'application/json' `
  -Body '{"username":"admin","password":"Crm@123456"}'
$headers = @{ Authorization = "Bearer $($login.data.accessToken)" }

Invoke-RestMethod http://localhost:8080/api/v1/auth/me -Headers $headers
Invoke-RestMethod 'http://localhost:8080/api/v1/system/users?page=1&pageSize=10' -Headers $headers
```

统一响应示例：

```json
{
  "code": "OK",
  "message": "success",
  "data": {
    "application": "hospital-crm",
    "version": "1.0.0-SNAPSHOT",
    "profiles": ["dev"],
    "javaVersion": "21.0.13",
    "os": "Windows 11",
    "timezone": "Asia/Shanghai",
    "serverTime": "2026-11-16T13:00:00Z"
  },
  "traceId": "5f0d8f0c7a2e4c1b9a3d6e8f0b1c2d3e",
  "timestamp": "2026-11-16T13:00:00Z"
}
```

响应头 `X-Trace-Id` 与日志 `[traceId]` 一致，便于跨系统排障。

### CRM-001 验收清单

| 验收项 | 结果 |
|---|---|
| 代码仓库结构（后端多模块、管理端、移动端、部署、CI） | 已建立 |
| 后端可启动，`/actuator/health` 返回 UP | 依赖 PostgreSQL 启动后可用 |
| 管理端可启动并展示运行状态 | 已实现 |
| 移动端 H5 可启动 | 已实现 |
| Docker 开发环境 | 已提供 Compose |
| 数据库迁移 | Flyway `V1__baseline.sql` |
| CI 构建与测试 | GitHub Actions 工作流已配置 |

### CRM-002 验收清单

| 验收项 | 结果 |
|---|---|
| 未授权请求被拒绝 | 未携带令牌访问受保护接口返回 401，统一 JSON 且含 `traceId` / `X-Trace-Id` |
| 角色权限生效 | 无 `system:role:read` 的账号访问角色接口返回 403 |
| 数据范围生效 | `org` 范围账号查询用户列表仅返回本组织成员；组织树、审计日志同样按范围过滤 |
| 敏感操作审计 | 登录、刷新令牌、用户、角色、组织操作写入 `sys_audit_log`，含操作人、IP、traceId |
| 刷新令牌轮换 | 刷新一次后旧令牌立即失效；检测到复用则撤销该用户全部刷新令牌 |
| 登录失败锁定 | 默认连续失败 5 次锁定 15 分钟（`crm.security.login` 可配置） |
| 内置管理员保护 | `SUPER_ADMIN` 角色不可修改，非超级管理员不能分配该角色 |
| 管理端认证闭环 | 登录页、路由守卫、权限菜单、401 自动刷新、403 页面已实现 |
| 数据库迁移 | `V2__system_auth.sql`：8 张系统表 + ROOT 组织 + 8 个权限项 + 超级管理员角色 |

### CRM-003 验收清单

| 验收项 | 结果 |
|---|---|
| 客户档案可新增、查询、修改 | `/api/v1/customers` 支持分页检索、创建、详情、更新与启停，均受 RBAC 权限控制 |
| 身份字段加密存储 | 手机号、证件号落库为 AES-256-GCM 密文，另有 HMAC 检索哈希与脱敏值，数据库无明文 |
| 手机号与证件号哈希检索 | 输入 11 位手机号或 15/18 位身份证号时按哈希精确匹配，姓名/客户号走模糊检索 |
| 列表不泄露敏感明文 | 列表与创建/更新响应只返回 `phoneMasked`、`idNoMasked`，无解密字段 |
| 敏感信息按权限查看 | 详情接口仅在拥有 `crm:customer:sensitive:read` 时返回完整手机号/证件号，并写入 `CUSTOMER_SENSITIVE_VIEW` 审计 |
| 重复客户提示 | 手机号或证件号已存在时返回 409 并提示既有客户号，可显式 `forceCreate` 强制创建 |
| 标签与来源 | 标签字典 CRUD 与客户标签分配；来源支持手工/门诊/住院/体检/线上/转介绍/活动 |
| 审计留痕 | 创建、更新、启停、敏感查看、标签变更均写入 `sys_audit_log` |

### CRM-004 验收清单

| 验收项 | 结果 |
|---|---|
| 家庭创建 | `/api/v1/families` 支持创建、分页查询与详情；创建时管理员客户自动建立「本人」关系 |
| 成员关联与关系历史 | 支持添加成员、维护家庭关系；解除关系采用逻辑结束并保留记录，`includeEnded=true` 可追溯历史 |
| 家庭管理员 | 每个家庭保留一名管理员，设置新管理员自动取消原管理员；管理员不能直接解除关系 |
| 代办授权 | 支持按成员创建代办授权（代预约、代支付、共享套餐、资料维护），可设置有效期 |
| 授权撤销 | 撤销后授权立即失效；解除成员关系时自动撤销其全部生效中授权 |
| 家庭关系不授予病历权限 | 代办范围白名单不含病历与临床数据，提交 `medical_*` 等范围返回 422；家庭关系本身不产生任何病历访问权限 |
| 撤销后代办接口拒绝访问 | `POST /families/{familyId}/delegation-checks` 在授权撤销、过期、关系解除或家庭停用时返回 403，并写入 `FAMILY_DELEGATION_DENIED` 审计 |
| 审计留痕 | 家庭创建、成员变更、管理员变更、关系解除、授权创建/撤销均写入 `sys_audit_log` |

### CRM-005 验收清单

| 验收项 | 结果 |
|---|---|
| 连接配置 | `crm.his.*` 支持连接串、账号、连接池、查询超时、候选上限、快照有效期与视图/字段映射；默认关闭 |
| 只读账号校验 | SQL Server 账号若具备 INSERT/UPDATE/DELETE/ALTER 权限，`read-only-check=true` 时拒绝查询并返回 503 |
| 无写权限 | 适配器仅构建参数化 SELECT；SQL 守卫拒绝分号、注释、写语句、危险关键字与非白名单视图 |
| SQL 参数化 | 手机号、证件号、病案号、患者主索引与模糊关键字均通过命名参数绑定，注入式输入按普通值处理 |
| 患者查询与映射 | `GET /his/patients/search` 支持手机号/证件号/病案号/患者主索引及自动推断，视图与字段可配置 |
| 最小化与脱敏 | 候选仅返回脱敏姓名、脱敏手机号与病案号，不返回证件号明文；所有候选标记需人工核验 |
| 患者 360 与缓存标记 | `GET /his/patients/{id}/360` 返回就诊/住院摘要，附带来源系统、获取时间、有效期、匹配置信度与 `LIVE/STALE` |
| HIS 不可用降级 | 查询失败或熔断且存在未过期快照时返回 `STALE` 数据；无可用快照时明确返回 502/503，不伪造数据 |
| 超时与熔断 | 查询超时由 JDBC 控制；连续失败达到阈值后熔断，冷却后半开探测，成功恢复 |
| 查询审计与同步日志 | 每次检索、360 查看、绑定与失败均写入 `crm_his_sync_log` 与 `sys_audit_log`，含 traceId、耗时与错误码 |
| 人工核验绑定 | `POST /his/patients/{id}/link` 要求 `verified=true`，绑定写入 `crm_customer_identity`（密文 + 哈希），同一标识不可重复绑定不同客户 |
| 同步日志查询 | `GET /his/sync-logs` 支持按状态、操作分页查询，受 `his:patient:read` 保护 |
| 无真实数据进入测试仓库 | 自动化测试使用 H2 MSSQLServer 模式与虚构患者数据，不连接真实 HIS |

### CRM-006 验收清单

| 验收项 | 结果 |
|---|---|
| CRM 与 HIS 聚合视图 | `GET /api/v1/customers/{customerId}/360` 聚合客户档案、标签、家庭归属与 HIS 就诊/住院摘要；采用独立 `crm-patient360` 模块 |
| 数据来源与更新时间 | 每个分区返回 `sourceSystem`、`dataStatus`、`fetchedAt`、`validUntil`、`cached` 与说明；HIS 分区保留匹配置信度与人工核验标记 |
| 字段权限 | 基础权限 `crm:patient360:read`；敏感手机号/证件号需 `crm:customer:sensitive:read`；家庭分区需 `crm:family:read`；HIS 分区需 `his:patient:read`；无权限分区返回 `RESTRICTED` 且不含业务数据 |
| 不同角色数据范围不同 | 单元测试覆盖仅 360 权限、家庭+HIS 权限与敏感权限组合；无权限分区不调用下游服务，不返回任何临床字段 |
| HIS 不可用不伪造数据 | HIS 未配置/失败且无快照时返回 `UNAVAILABLE` 与错误码，就诊/住院为空；存在有效快照时透传 CRM-005 的 `STALE` 标记；未绑定患者返回 `NOT_LINKED` |
| 分页 | 就诊、住院分区独立分页（`visitPage` / `admissionPage`，默认 10，上限 50），返回 `total` 与 `totalPages` |
| 缓存策略 | HIS 结果按患者主索引进行进程内 TTL 缓存（默认 60 秒，可配置），命中时标记 `cached=true`；`refresh=true` 可跳过短时缓存；失败结果不缓存 |
| 审计留痕 | 每次 360 查看写入 `PATIENT360_VIEW`，仅记录分区状态与是否敏感可见，不写入诊疗内容、证件号或外部患者标识明文 |

### CRM-007 验收清单

| 验收项 | 结果 |
|---|---|
| 线索来源渠道 | V7 预置 7 个渠道并支持创建/启停；线索必须绑定有效来源渠道 |
| 线索全流程 | 线索创建、编辑、状态机、联系记录、分派/转派、合并与失效均受权限与状态校验 |
| 线索转客户可追溯 | `POST /leads/{leadId}/convert` 新建或关联客户并写入 `crm_lead_conversion`；线索保留 `convertedCustomerId` 与 `convertedAt`，`GET /leads/{leadId}/conversion` 可回溯线索号与客户号 |
| 重复线索提示合并核验 | 手机号/证件号哈希命中活跃线索时返回 409 与疑似线索号；`forceCreate=true` 可确认非同一人后建档；`GET /leads/duplicates` 支持主动核验，`POST /leads/{leadId}/merge` 标记 `MERGED` 并指向目标线索 |
| 邀约任务 | 邀约支持关联线索或客户，状态机 `PENDING→CONTACTED→ACCEPTED/DECLINED` 并可取消/过期；接受后线索同步为 `INVITED` |
| 活动参与 | 活动 `DRAFT→PUBLISHED→CLOSED`；仅发布后可报名，名额满返回 422，同活动同手机号重复报名返回 409，支持签到/爽约/取消 |
| 字段权限与脱敏 | 线索手机号/证件号与参与人手机号均加密存储，列表与详情仅返回脱敏值；接口按 `crm:lead:*`、`crm:invitation:*`、`crm:activity:*` 分权 |
| 审计留痕 | 渠道、线索、联系记录、分派、合并、转化、邀约与活动操作写入 `sys_audit_log`，不含手机号/证件号明文 |
| 数据库迁移 | `V7__operation.sql` 新增 8 张表、3 个业务号序列、7 条渠道种子与 10 项权限 |

### CRM-008 验收清单

| 验收项 | 结果 |
|---|---|
| 并发下预约不超卖 | 条件更新原子占位 + 数据库 `CHECK (booked_count <= capacity)` 兜底；冒烟中容量 1 时段两个并发请求恰好 200/409，`booked_count` 始终不超过容量 |
| 取消规则生效 | 截止窗口外取消/改期 200 并释放名额（时段回到 0/2）；截止窗口内取消返回 422 并提示联系前台；同客户同时段重复预约 409 |
| 提醒失败可重试 | 失败后 `retryCount+1` 并按 300s×2^(n-1) 指数退避，达 3 次上限停止自动重试；人工重试接口对 PENDING/FAILED 返回 200，对 SENT/CANCELLED 返回 422；定时任务与手动 dispatch 均验证发送成功 |
| 预约类型与时段 | V8 预置 5 个类型；重复时段 409；已约时段不可改时间、容量不得低于已约数；时段关闭后预约返回 409 |
| 改期与爽约 | 改期先占新时段再释放旧时段（1/3 核对一致）；爽约要求时段已开始，生成 `crm_no_show_record` 且不释放名额（1/1）；终态预约再变更状态返回 422 |
| 敏感字段加密 | 预约手机号仅存 AES-256-GCM 密文 + HMAC 哈希 + 脱敏值，数据库明文模式匹配 0 条 |
| 审计留痕 | `APPT_CREATE/RESCHEDULE/CANCEL/STATUS`、`SLOT_*`、`APPTTYPE_*`、`REMINDER_DISPATCH/RETRY` 均写入 `sys_audit_log`，不含手机号明文 |
| 数据库迁移 | `V8__appointment.sql` 新增 5 张表、1 个预约号序列、5 条类型种子与 5 项权限，含时段唯一索引与活跃预约部分唯一索引 |

### CRM-009 验收清单

| 验收项 | 结果 |
|---|---|
| 排班冲突被阻止 | 同陪诊师同日重叠班次返回 409「排班与已有班次重叠」；唯一索引 `(escort_id, work_date, start_time)` 兜底；过去日期 422 |
| 服务时间可追踪 | `start` 记录 `actual_start_at`、`finish` 记录 `actual_end_at` 并计算 `duration_minutes`；详情与列表均返回实际起止与时长 |
| 订单状态机 | `PENDING→ASSIGNED→ACCEPTED→IN_PROGRESS→COMPLETED`，拒绝退回 `PENDING`，取消仅服务开始前可用（进行中 422）；终态再变更 422 |
| 派单资质与排班校验 | 无有效资质（过期/停用）422「陪诊师无有效资质」；无覆盖排班 422「陪诊师该服务时段无覆盖排班」；同时段已有活跃订单 422；停用陪诊师 422 |
| 评价规则 | 仅 `COMPLETED` 可评（422），每单一条（重复 409）；`/escort-reviews` 分页与陪诊师 `avgRating/reviewCount` 聚合正确 |
| 敏感字段加密 | 陪诊师与订单联系电话仅存 AES-256-GCM 密文 + HMAC 哈希 + 脱敏值，数据库明文模式匹配 0 条 |
| 审计留痕 | `ESCORT_*`、`ESCORTQUAL_*`、`SCHEDULE_*`、`ESCORTORDER_*` 共 16 类动作写入 `sys_audit_log`，不含手机号明文 |
| 数据库迁移 | `V9__escort.sql` 新增 5 张表、2 个业务号序列、5 项权限，含排班唯一索引与评价订单唯一索引 |

### CRM-010 验收清单

| 验收项 | 结果 |
|---|---|
| 无资质不能接单 | 派单与接单双重资质校验：无该项目有效资质（缺失/过期/停用）返回 422「上门服务人员不具备该项目的有效资质」；真实无资质账号派单同样被拒 |
| 审核规则生效 | 目录仅 `pending_review` 可审核；驳回必填原因（422）；终态重复审核 422；未发布（`draft`/`pending_review`/`rejected`/`disabled`）项目下单 422「服务不存在或未发布」 |
| 异常可升级 | `OPEN→ESCALATED→RESOLVED` 闭环；已解决异常重复处置 422；存在未处置异常时完成订单 422「存在未处理的异常上报，请先处置」 |
| 订单状态机 | `PENDING→APPROVED→ASSIGNED→ACCEPTED→IN_PROGRESS→COMPLETED`，拒绝派单退回 `APPROVED` 并清空派单信息；非法流转 422 |
| 默认地址唯一 | 部分唯一索引 `uq_home_address_default` 兜底；切换默认自动清除旧默认位 |
| 服务时间可追踪 | `start`/`finish` 记录实际起止并计算 `duration_minutes`；执行记录仅进行中订单可登记 |
| 敏感字段加密 | 地址联系电话仅存 AES-256-GCM 密文 + HMAC 哈希 + 脱敏值，数据库明文模式匹配 0 条 |
| 审计留痕 | `HOMESVC_*`、`HOMEADDR_*`、`STAFFQUAL_*`、`HV_ORDER_*`、`HV_EXCEPTION_*` 动作写入 `sys_audit_log`，不含手机号明文 |
| 数据库迁移 | `V10__homevisit.sql` 新增 6 张表、订单号序列、3 条已发布服务种子与 3 项权限，含默认地址部分唯一索引 |

## 数据库与迁移

- 迁移目录：`backend/crm-boot/src/main/resources/db/migration`
- 已应用脚本禁止修改；所有变更新增 `V2__xxx.sql`、`V3__xxx.sql`
- 基线包含客户、家庭、员工、服务目录、订单、支付、退款、随访、客服、投诉、会员、HIS 日志与审计等核心表
- CRM-002 起新增 `sys_user`、`sys_role`、`sys_permission`、`sys_org` 等系统表
- CRM-003 新增 `crm_tag`、`crm_customer_tag` 与客户号序列
- CRM-004 新增 `crm_family_authorization`、家庭成员状态字段与家庭账户权限
- CRM-005 新增 `crm_patient_snapshot`、HIS 同步日志查询索引与 `his:patient:read` / `his:patient:link` 权限
- CRM-006 新增客户外部身份查询索引与 `crm:patient360:read` 权限，不复制 HIS 临床数据
- CRM-007 新增 `crm_source_channel`、`crm_lead`、`crm_lead_contact_record`、`crm_lead_assignment`、`crm_lead_conversion`、`crm_invitation`、`crm_activity`、`crm_activity_participant` 及线索/邀约/活动业务号序列与 `crm:channel:*` / `crm:lead:*` / `crm:invitation:*` / `crm:activity:*` 权限
- CRM-008 新增 `crm_appointment_type`、`crm_service_slot`、`crm_appointment`、`crm_appointment_reminder`、`crm_no_show_record`、预约号序列、5 条类型种子与 `crm:appttype:*` / `crm:appointment:*` 权限；时段容量含数据库级 `CHECK` 约束
- CRM-009 新增 `crm_escort`、`crm_escort_qualification`、`crm_escort_schedule`、`crm_escort_order`、`crm_escort_review`、陪诊号与订单号序列及 `crm:escort:*` / `crm:escortorder:*` 权限；排班唯一索引与评价订单唯一索引兜底
- CRM-010 新增 `crm_home_service`、`crm_home_address`、`crm_home_visit_order`、`crm_home_visit_execution`、`crm_home_visit_exception`、`crm_home_staff_qualification`、上门服务订单号序列、3 条已发布服务种子与 `crm:homevisit:*` 权限；默认地址使用部分唯一索引保证每客户唯一
- CRM-011 在基线 `crm_order`、`crm_order_item`、`crm_payment`、`crm_refund` 之上新增 `crm_reconciliation_batch`、`crm_reconciliation_diff`、`crm_invoice`、订单/支付/退款/发票业务号序列与 `crm:order:*` / `crm:payment:*` 权限；支付单补充渠道交易号与幂等键唯一索引，退款单补充审核与渠道退款号
- CRM-012 在基线 `crm_member_account`、`crm_member_ledger` 之上新增 `crm_member_level`、`crm_member_package`、`crm_member_card`、`crm_member_card_usage`、`crm_member_share`、账户号/卡号序列、5 条等级种子、3 条套餐种子与 `crm:member:*` 权限；账户补充冻结金额与更新时间，账本补充变动后余额/积分与业务号并建立客户唯一索引
- CRM-013 在基线 `crm_followup_task` 之上新增 `crm_followup_category`、`crm_followup_template`、`crm_followup_plan`、`crm_followup_record`、任务号/计划号序列、5 条分类、4 条模板、4 条计划种子与 `crm:followup:*` 权限；任务表补充计划、模板、触发事件键、风险等级、分配/开始/升级时间与升级原因，并建立 `(plan_id, trigger_event_key)` 部分唯一索引防止重复建单
- CRM-014 在基线 `crm_complaint` 之上补充来源、建单人、开始/提交/结案人、升级标记、升级层级/时间/原因与更新时间，重建状态/类型/等级/来源约束并移除 `crm_staff` 外键；新增 `crm_complaint_action`、`crm_complaint_rectification`、`crm_complaint_feedback`、`crm_complaint_review` 4 张表、工单号序列与 5 项 `crm:complaint:*` 权限
- CRM-015 在基线 `crm_chat_session` / `crm_chat_message` 之上补充优先级、入队/接管/最近消息时间、AI 摘要、患者授权范围、结束人与更新时间，重建状态/接待方/优先级约束并移除 `crm_staff` 外键改为关联 `sys_user`；新增 `crm_chat_handoff`、`crm_chat_event` 2 张表、会话号序列、消息幂等部分唯一索引与 3 项 `crm:chat:*` 权限
- CRM-016 新增 `crm_kb_category`、`crm_kb_document`、`crm_kb_chunk`、`crm_kb_version`、`crm_kb_review`、`crm_ai_query_log` 6 张表、知识编号序列（`KB7xxxxx`）与 3 项 `crm:knowledge:*` 权限；切片按 `(document_id, doc_version, chunk_index)` 唯一，`doc_version = current_version` 为已发布版本、`+1` 为草稿版本
- CRM-017 由 `V17__callcenter.sql` 新增 `crm_call_record`、`crm_call_recording`、`crm_sms_template`、`crm_message_outbox` 4 张表、2 个业务号序列、3 条短信模板种子与 6 项 `crm:call:*` / `crm:sms:*` 权限；通话记录以 `vendor_call_id` 唯一约束保证话单幂等，消息以 `biz_key` 唯一约束保证去重
- CRM-018 由 `V18__insurance.sql` 新增 `crm_insurance_company`、`crm_insurance_policy`、`crm_insurance_case`、`crm_insurance_material`、`crm_insurance_email`、`crm_insurance_email_attachment`、`crm_insurance_event`、`crm_insurance_reconciliation` 8 张表、案件号序列 `crm_insurance_case_no_seq` 与 6 项 `crm:insurance:*` 权限；入站邮件以 `message_id` 部分唯一索引保证幂等，对账以 `(company_id, period)` 唯一约束保证同公司同账期唯一，金额字段以 `NUMERIC` 与 `CHECK >= 0` 约束
- 迁移测试 `DatabaseMigrationTest` 在 CI 中连接真实 PostgreSQL 执行

## API 约定

- Base URL：`/api/v1`，JSON / UTF-8，主键 UUID
- 认证：`Authorization: Bearer <accessToken>`；令牌过期后使用 `/api/v1/auth/refresh` 轮换
- 统一响应：`code`、`message`、`data`、`details`、`traceId`、`timestamp`
- 错误码遵循 `api/04-标准API接口规范.md`，由 `GlobalExceptionHandler` 统一输出；401/403 同样返回统一结构
- 时间使用 ISO 8601；金额使用十进制字符串或 `numeric`，禁止浮点
- 写接口统一校验权限、输入、状态机与 `Idempotency-Key`
- 接口变更必须同步更新 `Hospital_CRM_V1_0_交付包/api/04-openapi.yaml`（已补充 CRM-002 系统管理、CRM-003 客户中心、CRM-004 家庭账户、CRM-005 HIS 集成、CRM-006 患者 360、CRM-007 客户运营、CRM-008 预约中心、CRM-009 陪诊服务、CRM-010 上门服务、CRM-011 订单支付退款对账发票、CRM-012 会员充值次卡套餐共享授权、CRM-013 随访分类模板计划任务人工队列、CRM-014 投诉表扬工单分派整改反馈复核升级、CRM-015 在线客服会话消息排队接管转接、CRM-016 AI 知识库分类文档上传审核版本下架 RAG 问答追溯接口、CRM-017 呼叫中心来电弹屏/通话记录/关联/录音授权、短信模板与消息 Outbox 派发重试接口与 CRM-018 商保中心保险公司/保单/案件/材料/邮件/对账接口）

## 已实现能力与明确未包含

已实现：

- Maven 多模块工程、统一响应与异常处理、traceId 透传
- Actuator 健康检查、就绪/存活探针、Prometheus 指标端点
- Flyway 数据库迁移（V1 业务基线 + V2 认证与权限 + V3 客户中心 + V4 家庭账户 + V5 HIS 集成 + V6 患者 360 + V7 客户运营 + V8 预约中心 + V9 陪诊服务 + V10 上门服务 + V11 订单支付退款对账 + V12 会员充值次卡套餐 + V13 随访中心 + V14 投诉表扬中心 + V15 在线客服 + V16 AI 知识库与数字人）
- JWT 无状态认证、刷新令牌轮换与复用检测、登录失败锁定
- 用户、组织、角色、权限管理，RBAC + 五级数据范围（全院 / 本组织及下级 / 本组织 / 仅本人 / 自定义）
- 客户档案 CRUD、AES-256-GCM 敏感字段加密、HMAC 哈希检索、脱敏展示与敏感权限控制
- 客户标签字典与标签分配、客户来源分类、重复客户提示
- 家庭账户创建、成员关联与关系历史、家庭管理员、代办授权与撤销
- 代办权限校验接口，授权撤销/过期后立即拒绝；代办范围排除病历与临床数据
- HIS SQL Server 只读适配器：独立只读连接池、可配置视图/字段映射、查询超时、只读账号校验与连续失败熔断
- HIS 患者候选查询与 360 聚合：最小化脱敏返回，快照标记来源/获取时间/有效期/置信度，HIS 不可用时返回 `STALE`
- HIS 同步日志与查询审计；人工核验后绑定 HIS 患者主索引，绑定值加密并支持哈希检索
- 患者 360 聚合视图：CRM 客户/标签/家庭 + HIS 就诊/住院摘要，分区来源与更新时间、字段权限、独立分页与 HIS 短时缓存；HIS 不可用时明确返回 `UNAVAILABLE`/`STALE`，不伪造数据
- 客户运营：来源渠道字典、线索创建与状态机、重复线索核验与强制建档、联系记录、分派/转派历史、线索合并、线索转客户追溯（新建或关联既有客户）
- 邀约任务：关联线索/客户、状态机推进、接受后线索同步 `INVITED`；活动运营：创建、发布、报名（名额与手机号去重）、签到/爽约/取消
- 预约中心：预约类型与服务时段管理、预约创建/改期/取消（截止规则）、条件更新原子占位并发防超卖、提醒定时派发与失败指数退避重试、人工重试、爽约记录与终态锁定
- 陪诊服务：陪诊师档案与资质管理、排班重叠冲突阻止、订单派单五重校验（启用/资质/排班覆盖/时段冲突）、状态机流转与拒绝退回、履约起止计时、完成评价与评分聚合
- 上门服务：项目目录审核链（草稿→待审→发布/驳回方可下单）、客户服务地址簿（默认位唯一）、人员资质库、派单与接单双重资质拦截、执行记录、异常上报/升级/处置闭环与未处置异常阻止完成
- 订单与收银：服务端重算金额的统一订单、支付单与渠道回调（HMAC-SHA256 验签、重复回调幂等回放不重复入账）、多次部分退款与条件更新防超退、渠道对账批次与差异明细、发票申请/开具/红冲与可开票净额控制；`PaymentGateway` / `ChannelStatementProvider` / `InvoiceProvider` 均为可替换抽象，CRM 收费不写 HIS 收费表
- 会员与权益：会员等级与折扣、储值余额与冻结金额、积分增减与按门槛自动升级、次卡购买/扣次/返还（条件更新防超扣）、套餐目录与三种套餐类型、家庭共享授权与代付、订单退款按规则返还权益；余额与积分每次变动写账本
- 随访中心：随访分类/模板/计划配置、按事件与偏移天数生成任务、同一事件键去重、任务分配/开始/提交/取消、答案风险规则评估、高风险自动人工队列与处置
- 投诉表扬中心：投诉/表扬/建议统一建单与按等级 SLA、分派与转派、处理动作时间线、整改任务与核验、患者满意度反馈、提交复核、结案审核与退回（审核人与处理人分权）、手动升级与超时自动升级
- 在线客服：会话与消息、首次明确要求人工仅确认、二次请求自动排队、投诉/紧急/敏感/低置信度直接转人工、转人工时生成 AI 接待摘要、按优先级与入队时间排队、条件更新并发接管、客服转接、结束会话、消息客户端号幂等、会话按接待人隔离（管理权限可查看全部历史）
- AI 知识库与数字人：知识分类与文档、`.txt/.md` 上传解析与 ≤500 字合并切片、送审前患者标识敏感扫描拦截、审核发布版本快照与并发冲突保护、驳回退回草稿、失效知识下架即时退出检索、RAG 检索问答携带知识编号/版本/切片引用、低置信度提示转人工并可联动客服会话入队、AI 随访话术草稿、问答全程追溯留痕
- 运营分析：指标定义强制口径说明与数据来源、12 条种子指标、运营看板与报表取数（返回数据时间范围与生成时间，可追溯）、客户明细报表仅返回脱敏列、导出申请-审批-下载闭环（审批生成脱敏 CSV 并记录 SHA-256 哈希与行数、未批准 422、越权 403）、组织数据权限按 DataScope 生效、指标/报表/导出 9 类审计动作全程留痕、`V19__analytics.sql` 迁移与 6 项 `crm:analytics:*` 权限
- 敏感操作审计（登录、令牌、用户、角色、组织、客户、标签、家庭、代办授权、HIS 查询、患者 360 查看、渠道、线索、邀约、活动、预约/时段/提醒、陪诊师/排班/陪诊订单、上门服务目录/地址/订单/异常、订单/支付/退款/对账/发票、会员开户/充值/消费/积分/套餐/次卡/共享/退款返还、随访分类/模板/计划/任务/触发/分配/记录/升级/处置、投诉建单/分派/处理/整改/反馈/复核/升级/取消/超时扫描与客服会话创建/消息/转人工确认/入队/接管/转接/结束），支持按动作、资源、时间与数据范围查询
- 管理端登录页、权限路由守卫、401 自动刷新、客户中心、患者 360、家庭账户、客户运营、预约中心、陪诊服务、上门服务、收银与订单、会员与权益、随访中心、投诉表扬、客服工作台、AI 知识库、标签、用户/角色/组织/审计管理页面
- 移动端 H5 / 小程序骨架与后端连通性检测
- Docker Compose 开发环境与 GitHub Actions CI

明确未包含（按任务逐步交付）：

- 商保中心（CRM-018）与真实保险公司理赔协议/邮件网关对接、只读账号 403 分权联调；接口契约、`V18__insurance.sql`、`backend/crm-insurance` 模块与管理端页面已交付并完成本地验收。外部大模型接入（本期 AI 数字人为内置检索式实现，接口契约已预留替换边界）
- 单点登录（LDAP/CAS）、短信验证码登录、MFA（后续任务按医院要求扩展）
- 真实 HIS 表结构、接口地址、患者主索引规则、支付与商保协议

> **安全提示**：CRM-003 已实现客户敏感字段加密与脱敏，CRM-004 已确保家庭关系不构成病历访问授权、代办授权可即时撤销，CRM-005 的 HIS 适配器只读、参数化且带只读账号校验与熔断，CRM-006 的患者 360 按分区逐项授权并明确标注数据来源与过期状态，CRM-007 的线索与活动参与人手机号同样加密存储且接口只返回脱敏值，CRM-008 的预约联系电话仅存密文/哈希/脱敏值且名额受数据库约束保护，CRM-009 的陪诊师与订单联系电话同样仅存密文/哈希/脱敏值且派单受资质与排班双重校验，CRM-010 的上门服务地址联系电话同样仅存密文/哈希/脱敏值且人员资质在派单与接单双重拦截，CRM-011 的支付回调强制 HMAC-SHA256 验签且重复回调按幂等回放、退款与对账均以条件更新防并发超额，CRM 收费只在主库记录流水不写 HIS 收费表。CRM-012 的会员余额与积分变动全部写账本且条件更新防超扣，CRM-013 的随访风险规则在服务端评估、高风险任务只能由具备管理权限的角色处置，CRM-014 的投诉工单处理与结案审核按权限分权、审核人不得为当前处理人且超时升级采用条件更新防重复，CRM-015 的客服会话按接待人隔离、接管与转接使用条件更新防并发抢占、消息支持客户端号幂等且转人工记录与状态轨迹全程留痕，CRM-016 的知识文档送审前强制扫描身份证号/手机号等患者标识并拒绝入库、AI 仅检索已审核发布知识且答案必须携带可追溯引用、低置信度不臆答而是转人工。CRM-017 的来电弹屏与通话/消息接口仅返回脱敏号码（前3后4）、号码以 HMAC-SHA-256 哈希检索存储，录音访问需独立权限 `crm:call:recording:read` 且每次访问写入审计，呼叫中心未接入时返回 502、禁止伪造录音地址。客户加密密钥、初始管理员、JWT 密钥与 HIS 连接凭据仅用于本地开发，生产部署前必须更换并妥善保管；更换客户加密密钥会导致历史密文无法解密，必须先完成密钥轮换迁移。禁止在开发/测试环境写入真实患者数据。

## 本地验证记录（CRM-001 ~ CRM-020）

| 验证项 | 命令 | 结果 |
|---|---|---|
| 后端编译与单元/切片测试 | `mvnw.cmd test` | 通过（468 个用例，0 失败，14 个 CI 专用迁移测试按设计在本地跳过；其中患者 360 测试 7 个、客户运营测试 17 个、预约中心测试 22 个、陪诊服务测试 40 个、上门服务测试 50 个、订单与收银测试 43 个、会员测试 32 个、随访测试 27 个、投诉表扬测试 19 个、在线客服测试 21 个、知识库测试 22 个、呼叫中心测试 22 个、商保测试 26 个、运营分析测试 20 个、运维监控测试 25 个） |
| 后端可执行包 | `mvnw.cmd -DskipTests package` | 生成 `crm-boot/target/crm-api.jar` |
| PostgreSQL 16 真实迁移与启动 | 本地 PostgreSQL + Flyway V1~V16 + 启动 jar | 通过，启动日志从 `15` 增量迁移到 `16`，`flyway_schema_history` 最新版本为 `16:true`，健康检查返回 `UP` |
| CRM-007 V7 迁移内容 | 查询 `information_schema` / `crm_source_channel` / `sys_permission` | 8 张新表、3 个业务号序列、7 条渠道种子、10 项权限均已创建；`SUPER_ADMIN` 自动授予全部 `crm:*` 权限 |
| 重复线索核验 | 相同手机号连续创建线索，再带 `forceCreate=true` 创建 | 首次 200；重复创建返回 409 `BUSINESS_CONFLICT` 并提示既有线索号；强制创建 200；`/leads/duplicates` 返回 2 条疑似线索 |
| 线索转客户追溯 | 新建客户转化 + 关联既有客户转化 | `NEW_CUSTOMER` 与 `EXISTING_CUSTOMER` 均写入 `crm_lead_conversion`，`/leads/{id}/conversion` 返回线索 ID 与客户号；客户已存在时返回 422 并提示改为关联 |
| 邀约状态推进 | 创建邀约 → CONTACTED → ACCEPTED | 邀约号 `I100000`，状态推进成功；接受后关联线索自动变为 `INVITED` |
| 活动报名与签到 | 发布前报名、发布后报名、重复手机号、超额、签到、关闭 | 发布前与超额返回 422，重复手机号返回 409，报名与签到 200，关闭时有效报名人数 2 |
| 线索与参与人加密 | 查询 `crm_lead`、`sys_audit_log` | 手机号仅存 52 字符密文、HMAC 哈希与脱敏值，明文模式匹配 0 条；审计中手机号明文 0 条 |
| CRM-008 V8 迁移内容 | 查询 `information_schema` / `crm_appointment_type` / `sys_permission` | 5 张新表、预约号序列、5 条类型种子、5 项权限与 `uq_slot_type_date_start` / `uq_appointment_active_slot` 等索引均已创建；`SUPER_ADMIN` 自动授予全部 `crm:*` 权限 |
| 预约并发防超卖 | 容量 1 时段两个并发 `POST /appointments` | 恰好一个 200、一个 409「名额已满或已关闭」，`booked_count=1` 不超卖；同客户同时段重复预约返回 409 |
| 预约取消与改期规则 | 取消、改期、临近截止取消、关闭时段后预约 | 取消 200 并释放名额（时段回到 0/2）；改期先占新再释放旧（新时段 1/3）；截止窗口内取消 422 提示联系前台；重复时段 409 |
| 提醒派发与重试 | `POST /appointment-reminders/dispatch`、SENT 提醒人工重试 | dispatch `scanned=1 sent=1`；定时任务自动派发验证通过；SENT 提醒重试返回 422（仅 PENDING/FAILED 可重试，规则正确） |
| 爽约全流程 | 创建 1 分钟后开始的时段并预约，时段开始后标记 `NO_SHOW` | 200 且状态 `NO_SHOW`；名额不释放（1/1）；`/no-shows` 返回记录、详情 `noShowCount=1`；终态再变更状态返回 422 |
| 预约手机号加密 | 查询 `crm_appointment` | 仅存 AES-GCM 密文、HMAC 哈希与脱敏值（`139****8001`），明文模式匹配 0 条；审计动作 `APPT_*` / `SLOT_*` / `REMINDER_*` 均已落库 |
| CRM-009 V9 迁移内容 | 查询 `information_schema` / `sys_permission` | 5 张新表、2 个业务号序列、5 项权限与 `uq_schedule_escort_date_start` / `uq_review_order` 索引均已创建；`SUPER_ADMIN` 自动授予全部 `crm:*` 权限 |
| 排班冲突与日期规则 | 同日重叠班次、过去日期排班 | 重叠返回 409「排班与已有班次重叠」；过去日期返回 422 |
| 派单五重校验 | 过期资质 / 无排班 / 停用陪诊师 / 同时段已有订单分别派单 | 依次返回 422「陪诊师无有效资质」「陪诊师该服务时段无覆盖排班」「陪诊师该时段已有其他订单」；停用后派单 422 |
| 订单状态机与拒绝 | 派单后重复派单、拒绝退回、拒绝后再派单、终态再接单 | 非 `PENDING` 派单 422；拒绝退回 `PENDING` 并可再次派单成功；`COMPLETED` 后接单/取消 422 |
| 履约计时 | 开始→完成 | `start` 记录 `actual_start_at`；进行中取消 422「服务已开始，请使用完成」；`finish` 记录 `actual_end_at` 并计算 `duration_minutes` |
| 评价规则与评分聚合 | 完成订单评价、重复评价、评价列表 | 评价 200；重复评价 409；`/escort-reviews` 分页返回 2 条；陪诊师详情 `avgRating=5.0` |
| 陪诊手机号加密 | 查询 `crm_escort` / `crm_escort_order` | 仅存 AES-GCM 密文、HMAC 哈希与脱敏值，明文模式匹配 0 条；审计 16 类动作（`ESCORT_*` / `SCHEDULE_*` / `ESCORTORDER_*`）均已落库 |
| CRM-010 V10 迁移内容 | 查询 `information_schema` / `crm_home_service` / `sys_permission` | 6 张新表、订单号序列、3 条已发布服务种子、3 项权限与 `uq_home_address_default` 部分唯一索引均已创建；`SUPER_ADMIN` 自动授予全部 `crm:*` 权限 |
| 目录审核链 | 未发布项目下单、驳回不带原因、终态重复审核 | 未发布下单 422「服务不存在或未发布」；驳回缺原因 422；`published` 后再审核 422；审核发布后正常下单 |
| 资质双重拦截 | 无资质账号派单 / 派单后资质失效再接单 | 派单返回 422「上门服务人员不具备该项目的有效资质」（以真实无资质账号验证）；接单二次校验同样 422 |
| 异常升级与处置闭环 | 上报异常后完成订单、升级、处置、重复处置 | 未处置异常完成订单 422「存在未处理的异常上报，请先处置」；`OPEN→ESCALATED→RESOLVED` 流转正常；已解决重复处置 422 |
| 默认地址切换 | 同客户先后设置两个默认地址 | 第二个默认地址 200 且旧默认位自动清除，`uq_home_address_default` 无冲突 |
| 上门联系电话加密 | 查询 `crm_home_address` | 仅存 AES-GCM 密文、HMAC 哈希与脱敏值，明文模式匹配 0 条；`HOMESVC_*` / `HOMEADDR_*` / `STAFFQUAL_*` / `HV_*` 审计动作均已落库 |
| 未认证访问 | 未携带令牌请求 `/operation/leads` | 返回 401 统一 JSON |
| 未认证与越权 | `curl` 访问用户/角色/组织/客户接口 | 未认证返回 401；缺权限返回 403，均为统一 JSON |
| 数据范围 | 科室管理员账号查询用户列表 | 仅返回本组织成员，越范围用户不可见 |
| 刷新令牌轮换与复用 | 连续调用 `/auth/refresh` | 首次成功，旧令牌复用返回 401 并撤销全部令牌 |
| 客户敏感字段加密 | 创建客户后查询 `crm_customer` | 手机号/证件号仅存 AES-GCM 密文与 HMAC 哈希，脱敏值正确 |
| 客户检索与防重 | `keyword` 传手机号/证件号、重复手机号建档 | 哈希精确检索命中；重复返回 409 并提示既有客户号 |
| 敏感信息权限 | 只读账号查看客户详情 | 响应 `sensitiveVisible=false` 且无明文；管理员查看写入审计 |
| 家庭创建与成员关联 | 创建家庭并添加成员、查询关系历史 | 家庭号 `F100000+`，成员关系与管理员正确落库，已解除关系保留可追溯 |
| 代办授权与撤销 | 创建授权后校验、撤销后再次校验 | 撤销前返回 200；撤销后返回 403 并写入 `FAMILY_DELEGATION_DENIED` |
| 病历权限隔离 | 以 `medical_record` 作为代办范围提交 | 返回 422，提示家庭关系不构成病历访问授权 |
| 审计留痕 | 查询 `sys_audit_log` | 登录、用户/角色/组织、客户、标签、家庭、代办授权与 HIS 查询事件均已落库 |
| 管理端类型检查与构建 | `npm run build` | 通过，`FamiliesView` 页面正常打包，产物 `admin-web/dist` |
| 管理端页面实测 | 客户中心 / 标签页（CRM-003 阶段） | 列表脱敏、详情解密、新建客户、标签列表正常；家庭账户页以接口与构建验证为准 |
| 移动端 H5 构建 | `npm run build:h5` | 通过，产物 `dist/build/h5` |
| HIS 单元与适配器测试 | `mvnw.cmd -pl crm-his -am test` | 通过（SQL 守卫、熔断状态机、查询构建与 H2 MSSQLServer 模式查询共 19 个测试） |
| 患者 360 角色字段差异 | 管理员与仅 `crm:patient360:read` 账号分别请求 `/customers/{id}/360` | 管理员 `sensitiveVisible=true` 且返回完整手机号；受限账号 `sensitiveVisible=false`、手机号脱敏，`family/his` 均为 `RESTRICTED` 且不返回业务字段 |
| 患者 360 未绑定与 HIS 不可用 | 未绑定客户、人工写入模拟 HIS 身份后请求 360 | 未绑定返回 `NOT_LINKED`；HIS 关闭时返回 `UNAVAILABLE` + `SERVICE_UNAVAILABLE`，姓名/就诊/住院为空，外部患者标识脱敏，不伪造数据 |
| 患者 360 审计与脱敏 | 查询 `sys_audit_log` 中 `PATIENT360_VIEW` | 审计仅含分区状态、`sensitiveVisible`、`hisCached` 与客户号；检索外部患者标识明文数量为 0 |
| 患者 360 管理端构建 | `npm run build` | 通过，`Patient360View` 路由与页面正常打包 |
| 预约中心管理端构建 | `npm run build` | 通过，`AppointmentView`（预约/时段/类型/爽约四标签页）路由与页面正常打包，`vue-tsc` 类型检查无错误 |
| 陪诊服务管理端构建 | `npm run build` | 通过，`EscortView`（订单/陪诊师/排班/评价四标签页）路由与页面正常打包，详情嵌套结构在 api 层拍平适配，`vue-tsc` 类型检查无错误 |
| 上门服务管理端构建 | `npm run build` | 通过，`HomeVisitView`（订单/目录/地址/资质/异常五标签页）路由与页面正常打包，详情嵌套结构在 api 层拍平适配，`vue-tsc` 类型检查无错误 |
| CRM-011 V11 迁移内容 | 查询 `flyway_schema_history` / `crm_reconciliation_batch` / `crm_invoice` | 启动日志显示从 `10` 增量迁移到 `11`；对账批次/差异、发票表、4 项权限与支付单唯一索引均已创建 |
| 订单金额与幂等 | 创建订单（2×49.995 + 1×100.00，优惠 9.99）、重复提交同一幂等键、优惠超总额 | 总额 199.99、应付 190.00；重复幂等键 409；优惠 100.01 超总额 422 |
| 重复回调不重复入账 | 同一支付单连续回调两次（HMAC-SHA256 签名） | 首次 `replayed=false` 且实付 190.00；第二次 `replayed=true`，实付仍 190.00；缺签名 400、错误签名 401 |
| 部分退款正确 | 分两笔退 50.00 与 140.00，并尝试超退 200.00 | 累计已退 190.00，订单由 `PARTIAL_REFUNDED` 转 `REFUNDED`；超退 422 且整体回滚 |
| 发票与渠道对账 | 申请 100 → 开具 → 红冲；执行渠道对账并查差异明细 | 状态 `APPLIED`→`ISSUED`→`VOID`；全额退款后开票 422；对账批次 `DIFF` 并逐笔标记 `LOCAL_ONLY`，stub 不伪造渠道数据 |
| 收银不写 HIS 收费表 | 检查 `crm-order/pom.xml` 依赖与调用 | 仅依赖 `crm-system`、`crm-customer`，无 `crm-his` 依赖，不含任何 HIS 写操作 |
| 收银与订单管理端构建 | `npm run build` | 通过，`OrderView`（订单/支付/退款/对账/发票五标签页）路由与页面正常打包，`vue-tsc` 类型检查无错误 |
| CRM-012 V12 迁移内容 | 查询 `flyway_schema_history` / `information_schema` / `crm_member_level` / `crm_member_package` / `sys_permission` | 启动日志显示从 `11` 增量迁移到 `12`；等级/套餐/次卡/扣次/共享 5 张表、2 个业务号序列、5 条等级种子、3 条套餐种子与 3 项 `crm:member:*` 权限均已创建；`SUPER_ADMIN` 自动授予全部 `crm:*` 权限 |
| 并发消费不超扣 | 余额 100 的账户两笔并发 `POST /memberships/consumptions`（各 80.00） | 恰好一个 200、一个 422「账户余额不足」，并发后余额 20.00，未出现负数 |
| 每次变动有账本 | 充值、消费、积分调整、三类套餐购买、扣次、返还、退款返还后查询 `/accounts/{id}/ledger` | 12 条账本覆盖 `RECHARGE` / `CONSUME` / `POINTS_ADJUST` / `PACKAGE_PURCHASE` / `CARD_CONSUME` / `CARD_REFUND` / `REFUND_REVERSAL` / `CARD_VOID_ON_REFUND`，均记录变动额、积分增减与变动后快照 |
| 积分与自动升级 | 积分 +1000、扣减 99999、购买积分套餐 | +1000 后升为 `silver`；超扣返回 422「账户积分不足」；购买 5000 分积分套餐后积分 6000 升为 `gold` |
| 次卡扣次与返还 | 购买 `PKG_TIMES_10` → 扣 3 次 → 返还 3 次 → 重复返还 | 剩余 10→7→10；扣次写 `crm_member_card_usage` 与 `CARD_CONSUME` 账本；重复返还原扣次记录 422「该扣次记录已返还，请勿重复操作」 |
| 共享授权与代付 | 创建 A→B 余额授权、重复授权、自授权、B 无余额消费 15.00 | 授权 200；重复 409、自授权 422；代付成功且扣减授权人余额至 5.00；无授权时超余额 422 |
| 套餐维护与停用拦截 | 新建套餐 → 停用 → 购买；购买不存在套餐 | 停用后购买 422「套餐未启用，不能购买」；不存在套餐 404 |
| 退款权益返还 | 次卡订单退款、充值订单退款 | 次卡订单退款作废 1 张卡并返还剩余 10 次；充值订单退款 20.00 扣回余额 20.00，次卡状态 `VOID` |
| 会员权限与审计 | 查询 `sys_audit_log` 中 `MEMBER%` | 12 类动作（开户/充值/消费/积分/套餐/次卡/共享/退款返还）均已落库；审计中手机号明文 0 条 |
| 会员与权益管理端构建 | `npm run build` | 通过，`MemberView`（账户/次卡/套餐/共享授权四标签页）路由与页面正常打包，`vue-tsc` 类型检查无错误 |
| CRM-013 V13 迁移内容 | 查询 `flyway_schema_history` / `information_schema` / `crm_followup_category` / `crm_followup_plan` / `sys_permission` | 启动日志显示从 `12` 增量迁移到 `13`；分类/模板/计划/记录 4 张表、任务表 8 个扩展字段、任务号/计划号序列、5 条分类、4 条模板、4 条计划种子与 3 项 `crm:followup:*` 权限均已创建；`uq_followup_event` 唯一索引生效 |
| 事件触发与去重 | `POST /followup/triggers`（`SERVICE_COMPLETED`）连续两次同一 `eventKey` | 首次 `created=2 skipped=0`；第二次 `created=0 skipped=2`；按客户查询任务数仍为 2，计划时间 = 事件时间 + 偏移天数 |
| 高风险进入人工队列 | 提交答案 `condition=加重` | 任务转 `ESCALATED`、`riskLevel=HIGH`、`escalationRequired=true`，并出现在 `status=ESCALATED&escalationRequired=true` 查询结果中；处置后 `COMPLETED` |
| 中低风险完成 | 提交 `medication=否`、`condition=良好` | 分别标记 `MEDIUM` 与 `LOW` 且状态 `COMPLETED`，均不进入人工队列 |
| 随访记录与状态机 | 重复提交结果、重复处置、终态取消 | 重复提交 409「该随访任务已提交结果」；重复处置 422；已取消任务再次取消 422 |
| 配置校验 | 重复分类编码、非法渠道、模板与分类不匹配、停用计划 | 分别返回 409、400、422、200（停用成功）；新建计划号 `FP100010+` |
| 随访审计与去重校验 | 查询 `sys_audit_log` 中 `FOLLOWUP%` 与 `(plan_id, trigger_event_key)` 分组 | 10 类动作均已落库；同一计划同一事件键重复任务数 0；审计中手机号明文 0 条 |
| 随访中心管理端构建 | `npm run build` | 通过，`FollowupView`（任务/人工队列/计划/模板/分类五标签页）路由与页面正常打包，`vue-tsc` 类型检查无错误 |
| HIS 未配置 fail-closed | 未配置 `CRM_HIS_*` 调用 search/360/link | 返回 503 统一 JSON，不连接任何 HIS、不伪造数据 |
| HIS 连接错误 | 启用 HIS 但配置不可达地址 | 返回 502 `UPSTREAM_ERROR`，同步日志记录失败与错误码 |
| HIS 快照降级 | 预置未过期快照后调用 360 | 返回 200 且 `dataStatus=STALE`、`stale=true`，保留原始来源与获取时间；同步日志记录 `DEGRADED` |
| HIS 人工核验 | `verified=false` 调用 link | 返回 422，不写入绑定关系 |
| HIS 注入式输入 | 关键字传 `' or 1=1 --` | 作为普通参数处理，返回 0 条候选，无 SQL 异常 |
| HIS 同步日志 | 查询 `/api/v1/his/sync-logs` | 分页返回检索/360/绑定记录与失败错误码，未配置 HIS 时仍可查询本地日志 |
| OpenAPI 契约 | YAML 解析与 `$ref` 校验 | 205 个路径、206 个 schema，schema、parameter 与 response 引用全部可解析 |
| CRM-015 V15 迁移内容 | 查询 `flyway_schema_history` / `information_schema` / `sys_permission` | 启动日志从 `14` 增量迁移到 `15`；`crm_chat_handoff`、`crm_chat_event` 2 张表、会话号序列、消息幂等索引、状态/接待方/优先级约束与 3 项 `crm:chat:*` 权限均已创建；`SUPER_ADMIN` 自动授予全部 `crm:*` 权限 |
| 首次确认与二次排队 | 同一会话连续两次 `POST /chat/sessions/{id}/handoff`（`explicit`） | 首次 `needsConfirmation=true`、状态仍为 `AI`、请求计数 1；第二次 200 进入 `QUEUED`，队列位置 1，AI 摘要已生成 |
| 直接转人工与优先级 | `complaint` / `urgent` 原因转人工后查询 `/chat/queue` | 两者均直接入队；投诉优先级 `high`、紧急优先级 `urgent`；队列按优先级与入队时间排序，等待秒数正确递增 |
| 接管保留 AI 上下文 | 排队会话调用 `POST /chat/sessions/{id}/claim` | 200 且状态 `ACTIVE`；返回全部历史消息、AI 摘要、授权范围与转人工记录；转人工记录变为 `ACCEPTED` |
| 并发接管 | 同一排队会话第二次调用 claim | 返回 409「会话已被其他客服接管」，接待人不变 |
| 会话权限隔离 | 客服 B 查看/转接客服 A 的会话；主管转接 | 非接待人详情 403、转接 403；主管转接成功且 from/to 落库，历史消息、摘要与授权范围完整保留 |
| 结束与终态 | 结束会话后再次发送消息、重复结束 | 结束 200 且状态 `CLOSED`；终态发送消息 422；重复结束 422；会话轨迹记录 `CLOSED` 事件 |
| 消息幂等 | 相同 `clientMessageId` 连续发送两次 | 消息条数只增加 1，重复请求返回同一会话详情 |
| 在线客服管理端构建 | `npm run build` | 通过，`ChatWorkbenchView`（队列/我的会话/全部会话 + 聊天面板）路由与页面正常打包，`vue-tsc` 类型检查无错误 |
| CRM-016 V16 迁移内容 | 查询 `flyway_schema_history` / `information_schema` / `sys_permission` | 启动日志从 `15` 增量迁移到 `16`；分类/文档/切片/版本/审核/问答日志 6 张表、知识编号序列（`KB7xxxxx`）、切片唯一索引与 3 项 `crm:knowledge:*` 权限均已创建；`SUPER_ADMIN` 自动授予全部 `crm:*` 权限 |
| 草稿不检索与敏感拦截 | 草稿知识直接 RAG 提问；含手机号文档送审 | 草稿问答 `refs=0` 且 `needsHandoff=true`；含 `13812345678` 的文档送审返回 422，文档仍停留在 `DRAFT`，患者数据无法进入审核与发布 |
| 上传解析与审核流转 | 上传 `.txt` 解析、送审、驳回（缺原因/带原因）、再送审发布 | 上传 200 且状态 `DRAFT`、来源 `file`；驳回缺原因 400、带原因退回 `DRAFT`；发布后 `PUBLISHED` 且 `currentVersion=1`，版本快照与审核留痕齐全 |
| 已发布内容保护 | 对 `PUBLISHED` 文档直接 `PUT /content` | 返回 422，已发布知识必须走「编辑草稿 → 送审 → 发布」流程 |
| RAG 引用可追溯 | 提问「陪诊服务收费标准是什么」 | 命中已发布知识，引用携带 `KB7xxxxx` 编号、版本 v1、切片号与相关度；高置信度不转人工 |
| 低置信度转人工联动 | 无关问题携带 `sessionId` 提问后查询 `/chat/queue` | `needsHandoff=true`，会话以 `low_confidence` 原因进入人工队列，问答日志记录 `sessionId` 与转人工标记 |
| AI 随访话术 | `scene=followup` 提问随访注意事项 | 答案以【随访话术草稿】输出并携带引用来源 |
| 知识下架即时生效 | 下架（缺原因/带原因）后重复原问题检索 | 缺原因 400；下架后检索结果不再包含该知识编号，历史版本与审核记录保留 |
| 问答日志追溯 | 查询 `/knowledge/rag/logs` | 每次问答留痕问题、答案、置信度、引用来源与是否转人工 |
| AI 知识库管理端构建 | `npm run build` | 通过，`KnowledgeView`（知识文档/分类管理/AI 问答测试/问答记录四标签页 + 详情抽屉）路由与页面正常打包，`vue-tsc` 类型检查无错误 |
| CRM-017 OpenAPI 契约 | YAML 解析与 `$ref` 校验 | 通过：新增 tag「呼叫中心与短信」、11 个 `/callcenter` 路径与 14 个 schema，全文件 212 个路径、220 个 schema，876 处 `$ref` 引用全部可解析 |
| CRM-017 后端单元测试 | `mvnw test`（JDK 21，全 19 模块） | BUILD SUCCESS；`crm-callcenter` 22 个用例全部通过（弹屏命中/未命中、话单幂等、脱敏存储、关联校验、录音 422/502、模板变量提取与 409/422、bizKey 去重、退避与超限置空、条件更新防重发、人工重试规则） |
| CRM-017 V17 迁移内容 | `DatabaseMigrationTest` 连接 PostgreSQL 16.4 执行 | 通过：Flyway 从 v16 增量迁移到 v17（17/17 全部成功）；`crm_call_record`、`crm_call_recording`、`crm_sms_template`、`crm_message_outbox` 4 张表、`CC9xxxxx`/`MS9xxxxx` 2 个业务号序列、3 条短信模板种子、6 项 `crm:call:*` / `crm:sms:*` 权限与 `SUPER_ADMIN` 授权均已创建 |
| CRM-017 接口冒烟（27 项断言） | 启动 `crm-api.jar` 连接本地 PostgreSQL 后逐项调用 | 全部通过：来电弹屏命中/未命中且仅返回脱敏号；话单 `vendorCallId` 重复提交返回同一记录、时长自动计算；通话关联客户回显 `linkNo`、关联不存在工单 404；录音无记录 422、未接入 502 不伪造地址；模板 `{{变量}}` 自动提取、编码重复 409、停用后发送 422；消息 `bizKey` 去重幂等、变量缺失 422、手动派发置 `SENT`、已发送重试拒绝 422 |
| CRM-017 敏感数据与审计 | `psql` 查询 `crm_call_record` / `crm_message_outbox` / `sys_audit_log` | 通过：全库 0 条明文号码（仅 64 位 HMAC 哈希 + 前3后4 脱敏值）；审计留痕 `CALL_POPUP`、`CALL_RECORD_CREATE`、`CALL_RECORD_LINK`、`MESSAGE_ENQUEUE`、`MESSAGE_DISPATCH`，明细不含完整号码 |
| CRM-017 管理端构建 | `npm run build`（vue-tsc + vite） | 通过，`CallCenterView`（通话记录/来电弹屏/短信模板/消息队列四标签页 + 详情抽屉）路由与页面正常打包，类型检查无错误 |
| CRM-018 OpenAPI 契约 | YAML 解析与 `$ref` 校验 | 通过：新增 tag「商保中心」、17 个 `/insurance` 路径（24 个操作）与 18 个 schema，全文件 228 个路径、238 个 schema，969 处 `$ref` 全部可解析 |
| CRM-018 后端单元测试 | `mvnw test`（JDK 21，全 20 模块） | BUILD SUCCESS；`crm-insurance` 26 个用例全部通过（主数据 409/404/400/422、12 态状态机与必备字段、核定上限、金额隔离、材料与附件哈希、邮件 Message-ID 幂等、对账汇总与重复确认） |
| CRM-018 V18 迁移内容 | `DatabaseMigrationTest` 连接 PostgreSQL 16.4 执行 | 通过：Flyway 从 v17 增量迁移到 v18（14 个迁移用例全绿）；`crm_insurance_company/policy/case/material/email/email_attachment/event/reconciliation` 8 张表、`crm_insurance_case_no_seq`、`(company_id, period)` 唯一约束、`message_id` 部分唯一索引与 6 项 `crm:insurance:*` 权限均已创建 |
| CRM-018 接口冒烟（37 项断言） | 启动 `crm-api.jar` 连接本地 PostgreSQL 后逐项调用 | 全部通过：公司/保单编码重复 409、有效期倒置 400、联系人电话脱敏；案件初始 `registered`；非法流转 422、询价/预授权/理赔/对账必备字段 422、核定金额超应收 422；审批后 `estimatedPayout=9000`、`actualReceived=0`；回款后 `actualReceived=8500` 且预计赔付保持 9000、差额 `-500`；材料非法哈希 400、邮件附件哈希入库、重复 `messageId` 幂等；对账两轮汇总与确认、重复确认 422；单案件 8 条状态事件 |
| CRM-018 敏感数据与审计 | `psql` 查询 `crm_insurance_company` / `crm_insurance_email_attachment` / `sys_audit_log` | 通过：公司联系号码 0 条明文（64 位 HMAC 哈希 + `139****1111` 脱敏）；全部邮件附件哈希均为 64 位十六进制；审计留痕 `INSURANCE_COMPANY_CREATE`、`INSURANCE_POLICY_CREATE`、`INSURANCE_CASE_CREATE`、`INSURANCE_CASE_TRANSITION`、`INSURANCE_MATERIAL_ADD`、`INSURANCE_EMAIL_RECORD`、`INSURANCE_RECONCILE_GENERATE`、`INSURANCE_RECONCILE_CONFIRM` |
| CRM-018 管理端构建 | `npm run build`（vue-tsc + vite） | 通过，`InsuranceView`（保险公司/保单/商保案件/对账中心四标签页 + 案件详情抽屉）路由与页面正常打包，类型检查无错误 |
| CRM-019 OpenAPI 契约 | YAML 解析与 `$ref` 校验 | 通过：新增 tag「Analytics」、11 个 `/analytics` 路径（14 个操作）与 15 个 schema，download 端点为 `text/csv`；全文件 1014 处 `$ref`（284 个唯一）全部可解析、无悬空 |
| CRM-019 后端单元测试 | `mvnw test`（JDK 21，全 21 模块） | BUILD SUCCESS；`crm-analytics` 20 个用例全部通过（指标口径必填 400、编码重复 409、启停、时间跨度 366 天、比率分母为 0 不计算、全院范围不拼过滤条件、受限范围 scopeNote、导出仅 pending 可批/驳回必填原因/未批准 422/越权 403、CSV 无 11 位连续数字、file_hash 64 位十六进制） |
| CRM-019 V19 迁移内容 | `DatabaseMigrationTest` 连接 PostgreSQL 16.4 执行 | 通过：全新库 V1→V19 全链路 14 个迁移用例全绿；`crm_metric_definition/report_definition/export_request` 3 张表、状态 CHECK、`(status, created_at)` 索引、12 条口径说明齐备的种子指标、2 条种子报表与 6 项 `crm:analytics:*` 权限均已创建 |
| CRM-019 接口冒烟（42 项断言） | 启动 `crm-api.jar` 连接本地 PostgreSQL 后逐项调用 | 全部通过：种子指标 12 条且口径齐备；看板 scopeAll、卡片 ≥12、`dataFrom/dataTo/generatedAt` 回显、跨度 >366 天 400；指标创建重复 409、缺口径 400、更新/启停生效；汇总报表 12 行含口径列；客户明细仅脱敏手机号；导出 pending→批准（哈希 64 位、行数≥1）→重复批准 422→下载 CSV 含 `****`、无明文手机号、无 11 位连续数字；驳回必填原因、被驳回下载 422；`self` 范围分析师：scopeAll=false、客户指标 value=null+范围提示、客户明细 403、审批 403、仅见本人申请 |
| CRM-019 敏感数据与审计 | `psql` 查询 `crm_export_request` / `sys_audit_log` | 通过：`csv_content` 含 11 位连续数字的记录 0 条；已批准导出 `file_hash` 全部为 64 位十六进制；审计留痕 `METRIC_CREATE/METRIC_UPDATE/METRIC_STATUS`、`REPORT_CREATE/REPORT_STATUS`、`EXPORT_REQUEST/EXPORT_APPROVE/EXPORT_REJECT/EXPORT_DOWNLOAD` 九类动作齐全 |
| CRM-019 管理端构建 | `npm run build`（vue-tsc + vite） | 通过，`AnalyticsView`（指标概览/指标口径/报表查询/导出审批四标签页）路由与页面正常打包，类型检查无错误 |
| CRM-020 OpenAPI 契约 | `Hospital_CRM_V1_0_交付包/api/04-openapi.yaml` 结构校验 | 通过：新增 Ops tag 9 个路径 / 10 个操作 / 17 个 `Ops*` schema，全文件 1068 处 `$ref` 均可解析 |
| CRM-020 后端单元/切片测试 | `mvnw test`（JDK 21，全 22 模块） | BUILD SUCCESS；`crm-ops` 25 个用例全部通过（指标注册与 5xx/耗时/401 统计、任务埋点成功/异常记录、告警阈值触发/去重/自动恢复/确认幂等、备份登记哈希校验与保留清理、概览聚合与数据库探活） |
| CRM-020 V20 迁移 | 本地 PostgreSQL 启动 `crm-api.jar` + `psql` 查询 | 通过：Flyway 增量迁移至 `20:true`（`flyway_schema_history` 共 20 条）；3 张 `crm_ops_*` 表、`dedup_key` 未关闭部分唯一索引、`crm:ops:read`/`crm:ops:manage` 权限（sort 850/860）仅授予 `SUPER_ADMIN` |
| CRM-020 运维接口冒烟（19 项断言） | 启动 `crm-api.jar` 连接本地 PostgreSQL 后逐项调用 | 全部通过：未登录 401；概览含 jobs/alerts/backups；3 个定时任务已注册且运行记录可查；8 条告警规则；外部 raise→OPEN→ack→ACKED、重复 ack 幂等、非法级别 400；备份登记（SHA-256）→非法哈希 400→恢复核验回写→列表可查；`/actuator/prometheus` 匿名可抓、`/actuator/metrics` 需认证 401 |
| CRM-020 备份恢复演练 | `ops/pg/drill.ps1`（含 `/ops/backups` 登记与清理） | 通过：备份 326KB+SHA-256，恢复至演练库后关键表行数与 Flyway 版本（20/20）与源库一致，`restore_verified=true` 回写成功，演练库清理 |
| CRM-020 部署与回滚演练 | `ops/deploy/deploy.ps1` / `rollback.ps1` | 通过：正常部署（预检+部署前自动备份+就绪探针）成功；手工回滚 `ROLLBACK_STATUS=SUCCESS`；部署坏包时自动检测进程退出、回滚旧 jar 并保留 `.failed`，服务恢复就绪 |
| CRM-020 敏感数据与审计 | `psql` 查询 `crm_ops_alert` / `sys_audit_log` | 通过：`OpsAlertJob` 自动产生 `BACKUP_STALE`（HIGH）告警并在备份成功后自动 RESOLVED；审计留痕 `OPS_ALERT_ACK/OPS_ALERT_RAISE/OPS_BACKUP_REPORT/OPS_BACKUP_VERIFY` 四类动作齐全 |
| CRM-020 管理端构建 | `npm run build`（vue-tsc + vite） | 通过，`OpsView`（概览/任务/告警/备份四标签页）路由与页面正常打包，类型检查无错误 |
| Compose 配置 | YAML 结构校验 | 通过；本机未安装 Docker，未实际启动容器 |

## CI

`.github/workflows/ci.yml` 包含四个作业：

1. **backend**：JDK 21 + PostgreSQL 16 服务，执行 `mvn verify`，并验证 Flyway 迁移
2. **admin-web**：`npm ci` + 类型检查 + Vite 构建
3. **patient-mobile**：`npm ci` + H5 构建
4. **compose-config**：校验 `docker-compose.yml` 配置语法

本地等价命令：

```powershell
cd backend
.\mvnw.cmd test

cd ..\admin-web
npm run build

cd ..\patient-mobile
npm run build:h5
```

## 模块扩展约定

- 每个一级模块对应一个 Maven 模块（如 `crm-customer`、`crm-family`、`crm-his`、`crm-order`）
- 模块内按 `api / application / domain / infrastructure` 分包，模块间通过应用服务接口与领域事件交互
- 新模块的 Flyway 脚本统一放在 `crm-boot/src/main/resources/db/migration`，版本号全局唯一
- 新模块在 `crm-boot/pom.xml` 中引入依赖，不直接访问其他模块的数据库表
- HIS 访问必须封装在 `crm-his-adapter`，默认只读、参数化查询、超时与熔断
- 单模块任务完成标准：编译通过、自动化测试通过、权限与越权测试通过、OpenAPI 已更新、迁移脚本可重复部署

## 生产部署与安全要点

完整部署拓扑、网络分区、备份恢复与上线检查见 `Hospital_CRM_V1_0_交付包/deploy/07-内网部署说明.md`。上线前至少确认：

- 使用 `SPRING_PROFILES_ACTIVE=prod`，Swagger 已关闭，健康检查明细不对外暴露
- HTTPS 证书、域名、反向代理与 WAF / API 网关已就绪
- 数据库、Redis、RabbitMQ、MinIO 使用独立强密码，密钥不入 Git
- `CRM_DATA_ENCRYPTION_KEY` 独立生成并纳入密钥管理，完成备份与轮换演练（轮换前需迁移历史密文）
- HIS 只读账号、视图清单、字段字典与患者主索引规则经医院确认
- 备份与恢复演练通过，日志已脱敏，审计事件可追溯
- 患者端小程序备案、隐私政策与授权流程完成
- 镜像版本锁定，不使用 `latest` 标签

## 文档索引

- `Hospital_CRM_V1_0_交付包/docs/01-总体实施方案.md`
- `Hospital_CRM_V1_0_交付包/database/02-数据库设计说明.md`
- `Hospital_CRM_V1_0_交付包/database/03-postgresql-schema.sql`
- `Hospital_CRM_V1_0_交付包/api/04-标准API接口规范.md`
- `Hospital_CRM_V1_0_交付包/api/04-openapi.yaml`
- `Hospital_CRM_V1_0_交付包/codex/05-Codex开发任务书.md`
- `Hospital_CRM_V1_0_交付包/deploy/07-内网部署说明.md`
