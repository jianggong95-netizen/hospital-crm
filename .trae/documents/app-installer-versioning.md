# 医院 CRM 安装包封装与版本管理

## Context

当前医院 CRM 项目的部署依赖手工脚本（`demo-runtime/start-demo.ps1` + 前端 Node 静态服务 + 外部 PostgreSQL），版本号散落在 22 个 pom、`application.yml`、`admin-web/package.json` 三处且带 SNAPSHOT 后缀，无法直接交付给医院客户做演示或部署。本次改造目标：

1. **单进程安装包**：把管理端 Vue 页面打进 `crm-api.jar`，单 jar 同时提供 API + 前端页面 + Swagger，消除 Node 依赖。
2. **Windows .exe 图形安装向导**：用 Inno Setup 生成 `HospitalCRM-Setup-<ver>.exe`，向导内配置数据库连接与管理员密码，开箱即用。
3. **版本管理**：根目录 `VERSION` 文件为唯一真相，构建脚本统一同步到 pom/yml/package.json/安装包，升级脚本可自动比对版本、回滚到上一版。

## 已确认的决策

| 项 | 选择 |
|---|---|
| 安装包形态 | Inno Setup .exe 图形安装向导 |
| 前端承载 | 打进 jar，单进程部署 |
| 服务运行 | 手动脚本启停（不注册 Windows 服务） |
| JRE | 内置便携 JRE 21 |
| PostgreSQL | 单包全带，向导内选择"使用内置库 / 连接已有库" |
| 向导配置页 | 包含（数据库连接 + 管理员密码 + 端口） |
| 默认安装目录 | 盘符根目录（如 `D:\HospitalCRM`） |

## 实施步骤

### 1. 单版本源 + 版本号同步

- 新增根目录 `VERSION`，内容 `1.0.1`（语义化，无 SNAPSHOT）。
- 修改 `backend\pom.xml`（根）：`<version>1.0.1</version>` + `<properties><maven.build.timestamp.format>yyyy-MM-dd'T'HH:mm:ss</maven.build.timestamp.format></properties>`。
- 修改 `backend\crm-boot\src\main\resources\application.yml`：`crm.version: @project.version@`，新增 `info.app.build-time: @maven.build.timestamp@`（Spring Boot parent 已启用 `@..@` 资源过滤）。
- `release/build-release.ps1` 负责把 VERSION 同步到 22 个 pom（`mvn versions:set -DnewVersion=<ver>`）与 `admin-web/package.json`。

### 2. 前端打进 jar

- 修改 `backend\crm-boot\pom.xml`：新增按文件存在激活的 profile `with-web`，`<activation><file><exists>../../admin-web/dist/index.html</exists></file></activation>`，内嵌 `maven-resources-plugin` 在 `process-resources` 阶段把 `../../admin-web/dist/**` 拷贝到 `${project.build.outputDirectory}/static/`。
- 新增 `backend\crm-boot\src\main\java\com\hospital\crm\config\WebStaticConfig.java`：`WebMvcConfigurer` 注册 `/**` → `classpath:/static/`，自定义 `PathResourceResolver`：
  - 命中真实资源 → 返回；
  - 未命中且路径**不以** `api/`、`actuator/`、`v3/`、`swagger-ui`、`error` 开头、且**不含**扩展名或扩展名不在白名单 → 返回 `classpath:/static/index.html`（SPA 回退）；
  - 否则返回 null（保持 404）。
  - `index.html` 响应加 `Cache-Control: no-cache`。
- 修改 `backend\crm-system\src\main\java\com\hospital\crm\system\security\SecurityConfig.java`：在 `anyRequest().authenticated()` 之前加 `.requestMatchers(new SpaPublicRequestMatcher()).permitAll()`。
- 新增 `backend\crm-system\src\main\java\com\hospital\crm\system\security\SpaPublicRequestMatcher.java`：实现 `RequestMatcher`，条件：方法 == GET 且路径不以 `/api`、`/actuator`、`/v3`、`/swagger-ui`、`/error` 开头。

### 3. demo-runtime 简化

- 修改 `demo-runtime\start-demo.ps1`：删除 `$nodeExe`、Admin console 段、8081 提示行；管理端入口改为 `http://localhost:8080`；`$javaExe`/`$pgBin` 改为 `param(...)` + 环境变量默认值。
- 修改 `demo-runtime\stop-demo.ps1`：端口循环 `8081, 8080` 改为 `8080`。
- 删除 `demo-runtime\admin-server.js`。

### 4. Inno Setup 安装包

**前置**：`winget install --id JRSoftware.InnoSetup -e`（需沙箱外执行或用户授权）。

**新增目录** `installer\`：

- `installer\HospitalCRM.iss`：
  - `AppId={{7E4C0D2A-9F31-4B58-8E6A-CRM01HOSPITAL}`（固定 GUID，升级覆盖同一程序）。
  - `#define MyAppVersion` 由 `/D` 注入；`DefaultDirName={sd}\HospitalCRM`（盘符根目录）。
  - `[Files]`：`staging\app\crm-api.jar` → `{app}\app\`；`staging\jre\` → `{app}\jre\`；`staging\pgsql\` → `{app}\pgsql\`（向导选"连接已有库"时跳过）；`version.json`、`env.template` → `{app}\`（`.env` 用 `onlyifdoesntexist`，升级不覆盖）；`installer\scripts\*.ps1` → `{app}\bin\`。
  - `[Dirs]`：`{app}\logs`、`{app}\backups`、`{app}\run`。
  - `[Code]`：`PrepareToInstall` 检测旧版安装目录（反查 AppId）→ 自动执行旧版 `stop.ps1`；`InitializeUninstall` 提示并执行 `stop.ps1`。
  - 向导配置页：`CreateInputQueryPage` 收集数据库 host/port/db/user/password + 管理员密码 + 端口，写入 `.env`（仅首装）。
  - 向导类型页：`TInputOptionWizardPage` 选"使用内置数据库 / 连接已有数据库"，后者跳过 pgsql 文件拷贝。
  - `[Run]`：安装尾屏勾选"启动 Hospital CRM"→ 调 `bin\start.ps1`。
  - 输出：`OutputDir=..\dist\installer`、`OutputBaseFilename=HospitalCRM-Setup-{#MyAppVersion}`。

- `installer\scripts\`：
  - `start.ps1`：读 `.env`，用 WMI `Invoke-CimMethod Win32_Process Create` 启动 postgres（内置库时）+ java（`{app}\jre\bin\java.exe -jar {app}\app\crm-api.jar` + Spring 属性参数），写 pid 文件，轮询 readiness。
  - `stop.ps1`：按 pid 文件 + 端口 8080 停进程。
  - `status.ps1`：读 `version.json`，GET `/actuator/info` 比对版本，打印运行状态。
  - `upgrade.ps1`：复用 `ops\deploy\deploy.ps1` 的 `Test-HealthOnce`（含 PS5 byte[] UTF-8 解码）；流程：读新包 `version.json` → 比对当前版本 → stop → 备份 DB（内置库 `pg_dump` 到 `{app}\backups`）→ 备份 jar → 替换 → start → 轮询 readiness → 失败回滚 `.prev`。
  - `rollback.ps1`：还原 `.prev` jar + `version.json`，重启。

- `installer\templates\env.template`：基于 `.env.example` 精简，含 DB 连接、端口、JWT key、bootstrap 管理员、加密 key。

### 5. 一键构建脚本

新增 `release\build-release.ps1`：

```powershell
param([string]$Version, [switch]$SkipInstaller, [switch]$NoTag)

1  读 VERSION（$Version 缺省时）；校验 ^\d+\.\d+\.\d+$；工作树必须 clean
2  记录 gitCommit = git rev-parse --short HEAD
3  cd admin-web; npm ci; npm run build            → admin-web/dist
4  cd backend; mvn versions:set -DnewVersion=$Ver; mvn -B -DskipTests package
   （with-web profile 因 dist 存在自动生效 → crm-api.jar 内含前端）
5  staging 组装到 dist\staging\：
   app\crm-api.jar、version.json（含 sha256、gitCommit、buildTime、schemaVersion）、env.template、bin\*.ps1、README.txt、jre\、pgsql\
6  调 ISCC installer\HospitalCRM.iss /DMyAppVersion=$Ver /DStaging=... 
   → dist\installer\HospitalCRM-Setup-$Ver.exe，打印 exe 的 sha256
7  git commit release 变更 + git tag v$Ver（-NoTag 跳过）；versions:revert 可选
```

产物目录 `dist/` 加入 `.gitignore`。

### 6. 版本清单 version.json

由 `build-release.ps1` 生成，打进安装包，安装后位于安装目录根：

```json
{
  "version": "1.0.1",
  "buildTime": "2026-11-19T15:30:00",
  "gitCommit": "a648f4c",
  "jarSha256": "abc123...",
  "installerName": "HospitalCRM-Setup-1.0.1.exe",
  "schemaVersion": 20
}
```

`upgrade.ps1` 与 `status.ps1` 据此比对"运行中版本 vs 包内版本"。

## 关键文件清单

| 操作 | 路径 |
|---|---|
| 新增 | `VERSION` |
| 修改 | `backend\pom.xml`（版本号 + timestamp 格式） |
| 修改 | `backend\crm-boot\pom.xml`（with-web profile） |
| 新增 | `backend\crm-boot\src\main\java\com\hospital\crm\config\WebStaticConfig.java` |
| 修改 | `backend\crm-system\src\main\java\com\hospital\crm\system\security\SecurityConfig.java` |
| 新增 | `backend\crm-system\src\main\java\com\hospital\crm\system\security\SpaPublicRequestMatcher.java` |
| 修改 | `backend\crm-boot\src\main\resources\application.yml`（`crm.version: @project.version@`） |
| 修改 | `demo-runtime\start-demo.ps1`、`demo-runtime\stop-demo.ps1` |
| 删除 | `demo-runtime\admin-server.js` |
| 新增 | `release\build-release.ps1` |
| 新增 | `installer\HospitalCRM.iss` |
| 新增 | `installer\scripts\{start,stop,status,upgrade,rollback}.ps1` |
| 新增 | `installer\templates\env.template` |
| 修改 | `.gitignore`（加 `dist/`） |

## 验收步骤

1. 安装 Inno Setup：`winget install --id JRSoftware.InnoSetup -e`。
2. 构建：`.\release\build-release.ps1 -Version 1.0.1`。
3. 静默安装：`dist\installer\HospitalCRM-Setup-1.0.1.exe /VERYSILENT /DIR=D:\crm-test`。
4. 启动：`D:\crm-test\bin\start.ps1`，等待就绪。
5. 单进程验证（PowerShell `Invoke-WebRequest`，注意 byte[] 解码）：
   - `GET /` → 200 `text/html`（SecurityConfig 未 401）。
   - `GET /customers`、`GET /login` → 200 index.html（SPA 回退）。
   - `GET /assets/<hash>.js` → 200；`GET /favicon.ico` → 200。
   - `POST /api/v1/auth/login` → 200 拿 token；**`GET /api/v1/customers` 无 token → 401**（安全边界未放大）。
   - `GET /actuator/info` → version=1.0.1；`GET /swagger-ui.html` → 200。
   - 浏览器 `http://localhost:8080` 登录、刷新 `/system/users` 不回退 404。
6. 升级覆盖：改 VERSION=1.0.2 重新构建 → 双击 1.0.2 exe → 确认无"两个程序"并存（AppId 生效）、`.env` 未被覆盖、`version.json` 为 1.0.2、服务自动拉起。
7. 回滚演练：手工把 `app\crm-api.jar` 换成损坏 jar 后跑 `upgrade.ps1` → 自动还原 `.prev` 且 readiness 恢复 UP。
8. 卸载：确认 `.env`、`backups\`、`pgdata\` 按选项保留。
9. 回归：`mvn -B verify`（CI ubuntu 无 dist 时 with-web profile 不激活，后端测试不受影响）。

## 实测结论（2026-11-19）

已在本机完整跑通：构建 → 安装 → 启动 → 升级 → 回滚 → 卸载。

| 项 | 结果 |
|---|---|
| `build-release.ps1 -Version 1.0.1 -WithJre -WithPg` | 产出 `dist\installer\HospitalCRM-Setup-1.0.1.exe`（约 322 MB，含 JRE 21 + PostgreSQL 16） |
| 静默安装 | `/VERYSILENT /DBMODE=internal` 安装成功，生成 `config\.env`（JWT/AES 密钥由 Windows CSPRNG 生成） |
| 单进程 | `GET /` 200、`GET /dashboard` 200（SPA 回退）、`GET /api/v1/*` 无 token 401、`POST /api/v1/auth/login` 200、`/actuator/info` version=1.0.1 |
| 升级 | `upgrade.ps1 -NewJar 1.0.2.jar` → 自动 pg_dump 备份、换 jar、重启、readiness 通过，`status.ps1` 显示 v1.0.2 |
| 回滚 | `rollback.ps1` → 还原 `.prev`，`status.ps1` 回到 v1.0.1 |
| 卸载 | `/KEEPDATA=false` 静默卸载，安装目录清空干净 |

### 部署必读（两个真实坑）

1. **PostgreSQL 拒绝以管理员令牌运行**：`postgres.exe` 在提权进程下直接拒绝启动（"不允许以管理员权限的用户运行 PostgreSQL 服务器"）。
   - 交互式安装：向导本身提权，但 `[Run]` 的 `init-db.ps1` / `start.ps1` 带 `runasoriginaluser`，会以普通用户令牌执行，正常。
   - **静默安装（`/VERYSILENT`）无法降权**（Inno 在静默模式下回退到当前提权令牌），此时跳过自动建库；首次由普通用户运行 `bin\start.ps1` 会自动补建数据库（`start.ps1` 已内置自愈）。因此**静默部署后必须用普通用户启动一次**。
   - 日常启停请在普通（非管理员）PowerShell 中运行 `bin\start.ps1`，脚本检测到提权会直接报错而不是卡住。
2. **`initdb` 需要 C locale**：中文 Windows 的 ANSI 区域（`Chinese (Simplified)_China.936`）没有匹配的文本检索配置，`initdb` 会直接失败；脚本已固定 `--locale=C --encoding=UTF8`。

### 其它已修复问题

- `$PSScriptRoot` 在 `param()` 默认值中为空 → 所有脚本改为在函数体内解析安装根目录。
- Inno 的 `[Run]` 中向导页面控件与 `Null` 比较会抛 Type Mismatch → 改用 `GPagesReady` 标志。
- 安装目录 ACL：`[Dirs]` 加 `Permissions: authusers-modify`，否则普通用户无法写 `logs`、`pgdata`、替换 jar。
- 安装包路径含中文时 `generated.iss` 会被 ANSI 编码破坏 → 传相对路径。
