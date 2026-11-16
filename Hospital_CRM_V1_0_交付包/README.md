# Hospital CRM V1.0 项目交付包

医院智慧客户服务与运营管理平台，按已确认参数制定。

## 已确认参数
- HIS：Microsoft SQL Server
- 部署：医院内网私有化
- 移动端：微信小程序 + H5
- 目标客户规模：约 10,000 人
- 收费：CRM 独立收费，不直接写入 HIS 收费表
- 商保：API + 邮件 + 人工处理
- 范围：完整 18 个一级模块，分期交付

## 技术基线
- 管理端：Vue 3 + TypeScript + Element Plus
- 患者/员工移动端：uni-app + Vue 3
- 后端：Java 21 + Spring Boot 3.x（具体小版本在项目初始化时锁定）
- CRM主库：PostgreSQL 16+
- HIS：SQL Server，只读优先，通过适配器访问
- 缓存：Redis
- 消息：RabbitMQ
- 文件：MinIO
- API：REST/JSON + OpenAPI 3.1
- 部署：Docker Compose，医院内网环境

## 目录
- `docs/01-总体实施方案.md`：产品范围、架构、业务流程、实施阶段
- `database/02-数据库设计说明.md`：实体关系、数据字典、约束
- `database/03-postgresql-schema.sql`：可执行的核心数据库初始化脚本（基础版）
- `api/04-openapi.yaml`：OpenAPI 3.1接口定义（核心接口基线）
- `api/04-标准API接口规范.md`：接口约定、认证、错误码、事件
- `codex/05-Codex开发任务书.md`：可逐项执行的开发任务
- `codex/06-Codex总指令.md`：交给 Codex 的项目级指令
- `deploy/07-内网部署说明.md`：部署拓扑、配置、备份、安全

## 使用顺序
1. 先把整个目录提交到 GitHub，建立 `main` 和 `develop` 分支。
2. 将 `codex/06-Codex总指令.md` 提供给 Codex。
3. 先执行 CRM-001 至 CRM-006，不要一次性生成全部模块。
4. 每个任务先输出设计与变更计划，经确认后再编码。
5. 每个模块完成后运行单元测试、集成测试、迁移测试，再提交 Pull Request。

## 重要边界
本交付包是可用于启动开发的 V1.0 基线，不代表已完成医院现场接口联调或合规认证。HIS实际表结构、接口地址、认证方式、患者主索引规则、支付渠道、商保公司协议必须在项目实施时由医院和厂商确认。
