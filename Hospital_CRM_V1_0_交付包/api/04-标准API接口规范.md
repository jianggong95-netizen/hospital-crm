# 标准API接口规范

## 1. 基本约定
- Base URL：`https://<crm-host>/api/v1`
- 数据格式：JSON，UTF-8。
- 时间：ISO 8601，带时区偏移；数据库统一保存UTC或带时区时间。
- 金额：十进制字符串，避免客户端浮点精度误差。
- 主键：UUID；业务单号另行生成。
- 分页：`page`从1开始，`pageSize`默认20，最大100。
- API版本：路径版本 `/v1`；不兼容变更升级主版本。
- 所有写操作支持 `Idempotency-Key`，尤其订单、支付、退款、充值、扣次。
- 所有请求返回 `traceId`，便于跨系统排查。

## 2. 认证与授权
- 用户端建议使用医院统一身份认证或OAuth2/OIDC；小程序使用受控登录会话。
- 系统间调用使用OAuth2 Client Credentials或医院认可的双向认证方案。
- 生产环境必须HTTPS；内网不等于可信网络。
- JWT短时效，刷新令牌安全存储；敏感操作二次认证。
- 采用RBAC + 数据范围 + 字段级授权。
- 每次患者360、病历摘要、录音、地址等敏感数据访问记录审计事件。
- API客户端按系统、接口范围、IP网段和有效期授权。

## 3. 统一响应
成功：
```json
{
  "code": "OK",
  "message": "success",
  "data": {},
  "traceId": "01J...",
  "timestamp": "2026-11-16T12:00:00Z"
}
```
失败：
```json
{
  "code": "VALIDATION_ERROR",
  "message": "请求参数不正确",
  "details": [{"field":"phone","reason":"格式不正确"}],
  "traceId": "01J...",
  "timestamp": "2026-11-16T12:00:00Z"
}
```

## 4. 错误码
| HTTP | code | 含义 |
|---|---|---|
| 400 | VALIDATION_ERROR | 参数校验失败 |
| 401 | UNAUTHENTICATED | 未认证 |
| 403 | FORBIDDEN | 无权限 |
| 404 | RESOURCE_NOT_FOUND | 资源不存在 |
| 409 | BUSINESS_CONFLICT | 状态冲突/重复提交 |
| 409 | IDEMPOTENCY_CONFLICT | 幂等键冲突 |
| 422 | BUSINESS_RULE_FAILED | 不满足业务规则 |
| 429 | RATE_LIMITED | 请求过于频繁 |
| 502 | UPSTREAM_ERROR | HIS/第三方服务错误 |
| 503 | SERVICE_UNAVAILABLE | 服务暂不可用 |
| 500 | INTERNAL_ERROR | 未知内部错误 |

## 5. 核心接口目录

### 客户与家庭
- `GET /customers`：分页查询客户
- `POST /customers`：创建客户
- `GET /customers/{customerId}`：客户详情
- `GET /customers/{customerId}/360`：客户360
- `GET /families`：分页查询家庭账户
- `POST /families`：创建家庭
- `GET /families/{familyId}`：家庭详情
- `GET /families/{familyId}/members`：家庭成员，`includeEnded=true` 返回关系历史
- `POST /families/{familyId}/members`：添加成员
- `PUT /families/{familyId}/members/{memberId}`：更新成员关系或家庭管理员
- `POST /families/{familyId}/members/{memberId}/authorizations`：创建家庭代办授权
- `GET /families/{familyId}/members/{memberId}/authorizations`：查询成员代办授权
- `DELETE /families/{familyId}/members/{memberId}/authorizations/{authorizationId}`：撤销代办授权
- `DELETE /families/{familyId}/members/{memberId}`：解除家庭成员关系（逻辑结束关系）
- `POST /families/{familyId}/delegation-checks`：代办权限校验，授权撤销/过期时返回 403

### HIS
- `GET /his/patients/search`：查询HIS患者候选
- `GET /his/patients/{externalPatientId}/360`：查询HIS患者360摘要
- `POST /his/patients/{externalPatientId}/link`：经人工核验后建立映射
- `GET /his/visits`：查询就诊摘要
- HIS接口不得暴露可任意执行SQL的通用端点。

### 预约与服务
- `GET /service-catalog`：服务目录
- `GET /staff/availability`：查询服务人员可用时段
- `POST /appointments`：创建预约
- `POST /service-orders`：创建服务订单
- `POST /service-orders/{id}/assignments`：派单
- `POST /service-orders/{id}/accept`：接单
- `POST /service-orders/{id}/check-in`：签到
- `POST /service-orders/{id}/start`：开始服务
- `POST /service-orders/{id}/complete`：完成服务
- `POST /service-orders/{id}/evaluations`：服务评价

### 订单支付
- `POST /orders`：创建订单
- `GET /orders/{orderId}`：订单详情
- `POST /orders/{orderId}/payments`：发起支付
- `POST /payment-callbacks/{channel}`：支付渠道回调（验签）
- `POST /payments/{paymentId}/refunds`：申请退款
- `GET /payments/{paymentId}`：支付状态

### 随访、客服、投诉
- `POST /followup/tasks`：创建随访任务
- `POST /followup/tasks/{taskId}/records`：提交随访结果
- `POST /chat/sessions`：创建客服会话
- `POST /chat/sessions/{sessionId}/handoff`：请求人工接管
- `POST /complaints`：创建投诉/表扬工单
- `POST /complaints/{ticketId}/actions`：记录处理动作
- `POST /complaints/{ticketId}/close`：申请结案

### 会员与商保
- `POST /memberships/recharges`：会员充值
- `GET /memberships/accounts/{accountId}/ledger`：会员账本
- `POST /insurance/cases`：创建商保案件
- `POST /insurance/cases/{caseId}/quotations`：询价
- `POST /insurance/cases/{caseId}/claims`：提交理赔
- `POST /insurance/cases/{caseId}/materials`：上传材料元数据

## 6. 患者360数据结构
`GET /customers/{customerId}/360`建议按权限返回聚合数据：
- customer：CRM基本档案
- identities：经授权展示的HIS身份映射
- visits：就诊摘要
- admissions：住院摘要
- serviceOrders：陪诊/上门服务
- appointments：预约
- followups：随访摘要
- memberships：会员摘要
- complaints：投诉摘要（按权限）
- consents：授权状态摘要
每个外部数据片段应带 `sourceSystem`、`fetchedAt`、`dataFreshness`。临床数据仅按需展示，不能默认复制全部病历。

## 7. HIS SQL Server适配器
- 使用独立只读账号；优先调用HIS厂商提供的服务/API/视图。
- 若必须查库，仅允许访问经厂商和医院信息科确认的只读视图或存储过程。
- SQL采用参数化查询；禁止拼接用户输入。
- 连接串和密码放密钥管理或环境变量，不进Git。
- 设置连接超时、查询超时、连接池上限和熔断。
- 记录请求TraceId、查询对象、耗时、结果数量和错误摘要；日志禁止记录完整身份证、病历正文等敏感数据。
- 患者匹配由医院确认MPI规则；姓名+生日等弱匹配不得自动合并。

## 8. Webhook事件
建议事件：
- `customer.created`
- `customer.identity.linked`
- `appointment.created`
- `appointment.cancelled`
- `service_order.assigned`
- `service_order.started`
- `service_order.completed`
- `payment.succeeded`
- `payment.refunded`
- `followup.completed`
- `complaint.escalated`
- `insurance.claim.updated`

Webhook使用签名、时间戳、重放保护、重试和失败队列。接收端必须幂等。

## 9. API发布要求
- OpenAPI文件纳入Git版本管理。
- CI检查规范、Breaking Change和示例。
- 提供测试环境、接口调用示例和错误码。
- 生产密钥与环境配置不得写入OpenAPI或仓库。
- 所有接口完成权限测试、越权测试、幂等测试和审计测试。
