# 医院CRM V1.0（Hospital CRM）

[![CI](https://github.com/jianggong95-netizen/hospital-crm/actions/workflows/ci.yml/badge.svg)](https://github.com/jianggong95-netizen/hospital-crm/actions/workflows/ci.yml)
![Java 21](https://img.shields.io/badge/Java-21-2D6DAD)
![Spring Boot 3.5](https://img.shields.io/badge/Spring%20Boot-3.5-6DB33F)
![Vue 3](https://img.shields.io/badge/Vue-3-42B883)
![PostgreSQL 16](https://img.shields.io/badge/PostgreSQL-16-336791)
![Repository](https://img.shields.io/badge/repository-private-lightgrey)

医院智慧客户服务与运营管理平台，面向医院内网私有化部署，覆盖客户中心、患者 360、家庭账户、预约、陪诊、上门服务、随访、客服、AI 数字人、呼叫中心、商保、投诉、会员、订单支付与运营分析。

当前完成 **CRM-001 项目初始化**、**CRM-002 认证与权限**、**CRM-003 客户中心**、**CRM-004 家庭账户**、**CRM-005 HIS 集成** 与 **CRM-006 患者 360**：可运行的后端、管理端、移动端骨架，JWT 认证、RBAC 与数据范围、审计日志、客户档案加密与脱敏、标签管理、家庭账户与代办授权、HIS 只读适配与患者查询、CRM/HIS 聚合的患者 360 视图、Docker 开发环境、数据库迁移与 CI。

> 规划与契约文档位于 `Hospital_CRM_V1_0_交付包/`，本仓库代码按其中的总体方案、数据库设计与 API 规范实施。

## 目录

- [技术基线](#技术基线)
- [目录结构](#目录结构)
- [环境要求](#环境要求)
- [快速启动](#快速启动)
- [HIS 集成配置（CRM-005）](#his-集成配置crm-005)
- [患者 360（CRM-006）](#患者-360crm-006)
- [健康检查与验收](#健康检查与验收)
- [数据库与迁移](#数据库与迁移)
- [API 约定](#api-约定)
- [已实现能力与明确未包含](#已实现能力与明确未包含)
- [本地验证记录（CRM-001 ~ CRM-006）](#本地验证记录crm-001--crm-006)
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
│  ├─ crm-boot/                  # Spring Boot 启动、Web 配置、Flyway 迁移
│  ├─ Dockerfile
│  └─ pom.xml
├─ admin-web/                    # Vue 3 管理端（登录、工作台、客户中心、患者 360、家庭账户、标签、系统管理）
├─ patient-mobile/               # uni-app 患者与员工移动端（H5 / 微信小程序）
├─ .github/workflows/ci.yml      # 后端测试、前端构建、Compose 校验
├─ docker-compose.yml            # PostgreSQL / Redis / RabbitMQ / MinIO / 应用
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

## 数据库与迁移

- 迁移目录：`backend/crm-boot/src/main/resources/db/migration`
- 已应用脚本禁止修改；所有变更新增 `V2__xxx.sql`、`V3__xxx.sql`
- 基线包含客户、家庭、员工、服务目录、订单、支付、退款、随访、客服、投诉、会员、HIS 日志与审计等核心表
- CRM-002 起新增 `sys_user`、`sys_role`、`sys_permission`、`sys_org` 等系统表
- CRM-003 新增 `crm_tag`、`crm_customer_tag` 与客户号序列
- CRM-004 新增 `crm_family_authorization`、家庭成员状态字段与家庭账户权限
- CRM-005 新增 `crm_patient_snapshot`、HIS 同步日志查询索引与 `his:patient:read` / `his:patient:link` 权限
- CRM-006 新增客户外部身份查询索引与 `crm:patient360:read` 权限，不复制 HIS 临床数据
- 迁移测试 `DatabaseMigrationTest` 在 CI 中连接真实 PostgreSQL 执行

## API 约定

- Base URL：`/api/v1`，JSON / UTF-8，主键 UUID
- 认证：`Authorization: Bearer <accessToken>`；令牌过期后使用 `/api/v1/auth/refresh` 轮换
- 统一响应：`code`、`message`、`data`、`details`、`traceId`、`timestamp`
- 错误码遵循 `api/04-标准API接口规范.md`，由 `GlobalExceptionHandler` 统一输出；401/403 同样返回统一结构
- 时间使用 ISO 8601；金额使用十进制字符串或 `numeric`，禁止浮点
- 写接口统一校验权限、输入、状态机与 `Idempotency-Key`
- 接口变更必须同步更新 `Hospital_CRM_V1_0_交付包/api/04-openapi.yaml`（已补充 CRM-002 系统管理、CRM-003 客户中心、CRM-004 家庭账户、CRM-005 HIS 集成与 CRM-006 患者 360 接口）

## 已实现能力与明确未包含

已实现：

- Maven 多模块工程、统一响应与异常处理、traceId 透传
- Actuator 健康检查、就绪/存活探针、Prometheus 指标端点
- Flyway 数据库迁移（V1 业务基线 + V2 认证与权限 + V3 客户中心 + V4 家庭账户 + V5 HIS 集成 + V6 患者 360）
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
- 敏感操作审计（登录、令牌、用户、角色、组织、客户、标签、家庭、代办授权、HIS 查询与患者 360 查看），支持按动作、资源、时间与数据范围查询
- 管理端登录页、权限路由守卫、401 自动刷新、客户中心、患者 360、家庭账户、标签、用户/角色/组织/审计管理页面
- 移动端 H5 / 小程序骨架与后端连通性检测
- Docker Compose 开发环境与 GitHub Actions CI

明确未包含（按任务逐步交付）：

- 预约、服务订单、随访、会员、投诉等业务模块（CRM-007 及后续任务）
- 支付、退款、会员、随访、客服、商保等业务模块
- 单点登录（LDAP/CAS）、短信验证码登录、MFA（后续任务按医院要求扩展）
- 真实 HIS 表结构、接口地址、患者主索引规则、支付与商保协议

> **安全提示**：CRM-003 已实现客户敏感字段加密与脱敏，CRM-004 已确保家庭关系不构成病历访问授权、代办授权可即时撤销，CRM-005 的 HIS 适配器只读、参数化且带只读账号校验与熔断，CRM-006 的患者 360 按分区逐项授权并明确标注数据来源与过期状态。预约、订单、随访等业务尚未实现。客户加密密钥、初始管理员、JWT 密钥与 HIS 连接凭据仅用于本地开发，生产部署前必须更换并妥善保管；更换客户加密密钥会导致历史密文无法解密，必须先完成密钥轮换迁移。禁止在开发/测试环境写入真实患者数据。

## 本地验证记录（CRM-001 ~ CRM-006）

| 验证项 | 命令 | 结果 |
|---|---|---|
| 后端编译与单元/切片测试 | `mvnw.cmd test` | 通过（68 个测试通过，8 个 CI 专用迁移测试按设计在本地跳过；其中患者 360 服务测试 7 个） |
| 后端可执行包 | `mvnw.cmd -DskipTests package` | 生成 `crm-boot/target/crm-api.jar` |
| PostgreSQL 16 真实迁移与启动 | 本地 PostgreSQL + Flyway V1/V2/V3/V4/V5/V6 + 启动 jar | 通过，`flyway_schema_history` 最新版本为 `6:true`，`idx_identity_customer` 与 `crm:patient360:read` 已创建，健康检查返回 `UP` |
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
| HIS 未配置 fail-closed | 未配置 `CRM_HIS_*` 调用 search/360/link | 返回 503 统一 JSON，不连接任何 HIS、不伪造数据 |
| HIS 连接错误 | 启用 HIS 但配置不可达地址 | 返回 502 `UPSTREAM_ERROR`，同步日志记录失败与错误码 |
| HIS 快照降级 | 预置未过期快照后调用 360 | 返回 200 且 `dataStatus=STALE`、`stale=true`，保留原始来源与获取时间；同步日志记录 `DEGRADED` |
| HIS 人工核验 | `verified=false` 调用 link | 返回 422，不写入绑定关系 |
| HIS 注入式输入 | 关键字传 `' or 1=1 --` | 作为普通参数处理，返回 0 条候选，无 SQL 异常 |
| HIS 同步日志 | 查询 `/api/v1/his/sync-logs` | 分页返回检索/360/绑定记录与失败错误码，未配置 HIS 时仍可查询本地日志 |
| OpenAPI 契约 | YAML 解析与 `$ref` 校验 | 43 个路径、80 个 schema，引用完整 |
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
