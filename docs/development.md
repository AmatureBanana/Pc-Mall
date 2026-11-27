# 开发、配置与测试手册

本文档面向参与 **装机猫（`pc-mall`）** 开发、调试、构建与验收的工程师和协作代理，集中记录环境依赖、本地数据库隔离、配置契约、迁移边界及测试运行方式。

如需面向空库的演示部署与五条验收路径，另见 [空库部署与演示](delivery.md)。

## 环境与锁定依赖

- **JDK 21**（本机已验证 Oracle JDK 21.0.10）
- **Node.js 22.12+**（本机已验证 24.22.1 / npm 12.2.2）
- **MySQL 8.0**（已验证 8.0.45）；复用本机安装目录中的二进制，项目实例不注册系统服务
- **Maven Wrapper 3.9.11**（首次运行需联网下载）；Java 依赖由 Spring Boot 3.5.16 BOM 固定，MyBatis Starter 3.0.5；前端直接依赖精确版本（Vue 3.5.44、Element Plus 2.14.7、Vite 8.3.5、Playwright 1.64.1）随 `frontend/package-lock.json` 提交
- **PowerShell 7、Edge**（Playwright 默认使用本机 `msedge`，不自动下载另一套浏览器）

兼容说明与来源边界见 [MyBatis Spring Boot Starter 官方文档](https://mybatis.org/spring-boot-starter/mybatis-spring-boot-autoconfigure/)、[Vite](https://vite.dev/guide/)、[Element Plus](https://element-plus.org/en-US/guide/installation.html) 及 [数据来源与许可](data-sources.md)。

## 一次性本地数据库初始化

在仓库根目录执行（路径按本机安装位置调整）：

```powershell
$env:JAVA_HOME = 'D:\Java'
$env:MAVEN_USER_HOME = "$PWD\.local\maven-user"
./scripts/local-db.ps1 -MySqlHome 'D:\MySQL\MySQL Server 8.0'
. ./.local/env.ps1
```

行为约定：

- 默认实例仅监听 `127.0.0.1:33307`，数据保存在 `.local/mysql-data`，错误日志在 `.local/mysql-error.log`。
- 自动建立 `pc_mall_demo` 与 `pc_mall_test` 两个 schema 及各自最小权限账户（无跨库或全局权限），随机凭据写入被 Git 忽略的 `.local/env.ps1`，初始化 SQL 成功后自动删除，不触碰系统原有 MySQL 实例。
- 首次成功验证两个账户后写入 `.local/mysql-ready`。再次运行仅启动或检查同一实例，不重新建库。若存在凭据却缺少就绪标记、配置或数据目录，脚本会拒绝半成品状态且不自动删库重建。
- 停止本地实例：

```powershell
./scripts/local-db.ps1 stop
```

## 开发启动与同源打包

### 前后端分离开发态

两个终端分别执行：

```powershell
# 终端 1（仓库根目录，先配置 JAVA_HOME）
./scripts/dev-backend.ps1
```

```powershell
# 终端 2
cd frontend
npm.cmd ci
npm.cmd run dev
```

打开 `http://127.0.0.1:5173`，Vite 将 `/api` 代理至 `http://127.0.0.1:8080`。后端启动脚本自动加载 `.local/env.ps1`；数据库连接缺失、不可用或 Flyway 迁移失败时直接启动失败，不回退到内存数据库。

### 同源构建与 JAR 启动

先停止正在运行的 Vite（Windows 会锁定其原生构建模块）及持有 `target` JAR 的进程，再执行：

```powershell
./scripts/package.ps1
. ./.local/env.ps1
& "$env:JAVA_HOME\bin\java.exe" -jar target/pc-mall-0.1.0.jar
```

打开 `http://127.0.0.1:8080`。构建或重启后刷新已打开的浏览器标签页，避免旧静态资源与新接口错位。

- 该命令跳过测试；测试须按下方测试章节单独执行。
- 等效手动构建为 `frontend` 下执行 `npm ci`、`npm run build`，再在根目录执行 `./mvnw.cmd -Pdemo -DskipTests package`。若未先构建前端产物，`-Pdemo` 会由 `maven-enforcer-plugin` 明确失败。

### 首次初始化管理员

在已构建 JAR 且停止已有演示服务后执行：

```powershell
./scripts/start-with-admin.ps1 -Username your_admin
```

脚本通过隐藏输入读取密码（8～64 个 Unicode 字符且 UTF-8 不超过 72 字节），密码不进入命令行、脚本或仓库。库中已有任意管理员时不再创建、重置密码或提权；未提供初始化凭据也可正常启动（仅开放普通用户注册）。认证与权限细节见 [认证说明](authentication.md)。

## 环境变量与配置契约

| 环境变量 | 含义 |
| --- | --- |
| `DB_URL` | 演示库 JDBC URL，示例 `jdbc:mysql://127.0.0.1:33307/pc_mall_demo` |
| `DB_USERNAME` / `DB_PASSWORD` | 演示库账户 / 密码，无默认值 |
| `TEST_DB_URL` | 专用测试库 JDBC URL，schema 必须为 `pc_mall_test` |
| `TEST_DB_USERNAME` / `TEST_DB_PASSWORD` | 测试库账户必须为 `pc_mall_test`，密码无默认值 |
| `PORT` | 后端监听端口，默认 `8080`（绑定 `127.0.0.1`；修改时同步调整 Vite 代理） |
| `BROWSER_BASE_URL` | Playwright 验收服务地址，默认 `http://127.0.0.1:8080` |
| `BROWSER_ADMIN_USERNAME` / `BROWSER_ADMIN_PASSWORD` | 单独运行需管理员权限的 Playwright 用例时提供的测试服务管理员凭据 |
| `JAVA_HOME` | JDK 21 安装目录 |
| `MAVEN_USER_HOME` | 可选，Maven Wrapper 缓存目录；建议设为 `.local/maven-user` |
| `ADMIN_BOOTSTRAP_USERNAME` / `ADMIN_BOOTSTRAP_PASSWORD` | 可选，成对提供的初始管理员凭据；不写入仓库 |
| `SESSION_COOKIE_SECURE` | 本机 HTTP 默认 `false`，HTTPS 部署设 `true`；Session 始终 `HttpOnly`、`SameSite=Lax` |
| `LOGIN_MAX_FAILURES` | 登录失败阈值，默认 `5`；按用户名及实际连接 IP 独立限制 |
| `LOGIN_MAX_KEYS` | 内存活动限流键上限，默认 `10000`，至少 `2`；受限请求不创建新键 |
| `LOGIN_FAILURE_WINDOW` / `LOGIN_LOCK_DURATION` | 失败窗口 / 锁定时间，均默认 `15m`，必须为正值 |

**测试库安全保护红线**：

- 测试配置守卫在数据源与 Flyway 初始化前执行，拒绝缺失变量、非 `pc_mall_test` 的 schema 或用户名、与显式测试变量不一致的数据源覆盖，也禁止 Flyway 独立连接、目标 schema 及 Hikari 连接身份覆盖。
- 测试 JDBC 查询参数仅允许 `characterEncoding`、`connectionTimeZone`、`connectTimeout`、`socketTimeout`、`sslMode`、`allowPublicKeyRetrieval`，禁止通过 URL 改写账户或数据库。
- `test` profile 无演示库或内存库回退，启动时保持 Flyway 开启（仅故障注入测试在自身不可连接端口配置中禁用迁移）。

## 数据库迁移与夹具边界

Flyway 在应用启动时校验并执行版本化 SQL，禁止改写历史迁移文件；后续结构与数据变更必须通过新增迁移交付。

| 运行配置 | 加载位置与版本 | 初始目录规模 |
| --- | --- | --- |
| 默认演示（`application.yml`） | `classpath:db/migration`（V1～V31、V37、V45）+ `classpath:db/catalog`（V32～V36、V38～V44） | 八类共 574 件在售商品（含扩展目录与大陆主流渠道筛选） |
| 常规测试（`application-test.yml`） | 仅 `classpath:db/migration`（V1～V31、V37、V45） | 13 件基础在售夹具商品 |
| 性能压测（`PerformanceAcceptanceIT`） | 常规基础 + 临时 `performance-acceptance-v1` 样本 | 125 件（压测结束清理本轮样本） |

详情见 [空库部署与演示 § 数据与迁移](delivery.md#数据与迁移)、[目录扩展说明](catalog-expansion.md) 与 [分类参数契约](catalog-parameters.md)。

## 自动化测试与单文件开发循环

### 完整验收脚本

MySQL 就绪且前端依赖已安装后，在仓库根目录运行（请先停止持有 `target` JAR 的进程，且不要占用 `18081` 端口）：

```powershell
./scripts/test.ps1
```

该脚本串行执行：真实 MySQL 后端测试 → Vue/TypeScript 类型检查 → 前后端同源打包 → Edge 浏览器端到端测试。

- 后端与浏览器服务均使用 `test` profile 连接 `pc_mall_test`。
- 浏览器测试服务默认监听 `18081`，临时管理员凭据仅注入进程环境，结束后自动清理该临时管理员并停止测试服务。
- 不要在 `pc_mall_test` 手工预建管理员，以免与首次初始化验收冲突。
- 可选参数：`-MySqlHome` 指定 MySQL CLI 路径，`-BrowserPort` 指定测试端口。

### 单文件快速开发循环

```powershell
. ./.local/env.ps1

# 后端按模块定向执行（示例）
./mvnw.cmd -Dtest=ProductBrowserTest test
./mvnw.cmd -Dtest=ProductCatalogTest test
./mvnw.cmd -Dtest=TestDatabaseSafetyTest test
./mvnw.cmd -Dtest=SessionAuthTest test
./mvnw.cmd -Dtest=ProfilePasswordTest test
./mvnw.cmd -Dtest=CatalogAdministrationTest test
./mvnw.cmd -Dtest=UserAdministrationTest test
./mvnw.cmd -Dtest=PriceStockTest test
./mvnw.cmd -Dtest=CompatibilityCheckTest test
./mvnw.cmd -Dtest=ScenarioRecommendationTest test
./mvnw.cmd -Dtest=SingleItemCartTest test
./mvnw.cmd -Dtest=ConfigurationCartTest test
./mvnw.cmd -Dtest=MixedOrderTest test
./mvnw.cmd -Dtest=OrderPaymentHistoryTest test
./mvnw.cmd -Dtest=OrderCancellationTimeoutTest test
./mvnw.cmd -Dtest=OrderAdministrationTest test

# 前端类型检查与定向浏览器用例
cd frontend
npm.cmd run typecheck
npm.cmd test -- --grep 'guest sees'
```

单独运行需认证的 Playwright 用例文件（如 `frontend/tests/profile-password.spec.ts`）时，须显式提供指向测试服务的 `BROWSER_BASE_URL` 及对应 `BROWSER_ADMIN_USERNAME` / `BROWSER_ADMIN_PASSWORD`；常规回归优先直接使用 `./scripts/test.ps1`。

### 性能数据集离线核验与压测

```powershell
# 核验初始与 AMD Blender 4.3.0 性能数据包（合计 875 份官方完整提交、SHA-256 与 CC0 许可）
python scripts/performance-data.py --verify
python scripts/performance-amd-data.py --verify

# 核验性能验收数据集并运行串行性能/并发专项测试
. ./.local/env.ps1
python scripts/verify-performance-acceptance-data.py
./mvnw.cmd '-Dtest=PerformanceAcceptanceIT' test
```

## 核心接口与行为契约索引

修改具体业务前，请先阅读对应契约文档以确认错误码、事务边界、并发保护与权限要求：

- **商品与目录**：[分类参数契约](catalog-parameters.md) · [目录扩展说明](catalog-expansion.md) · [性能数据契约](performance-data.md) · [全商品性能标签](product-performance-tags.md) · [容量优先评分](capacity-priority-scores.md)
- **DIY 与兼容检测**：[DIY 工作区契约](diy-workspace.md) · [统一兼容检测](compatibility.md) · [核显支持说明](integrated-graphics.md)
- **智能装机推荐**：[三场景预算推荐](scenario-recommendation.md) · [办公推荐说明（早期基线）](office-recommendation.md)
- **购物车与订单**：[单品购物车](single-item-cart.md) · [配置组购物车](configuration-cart.md) · [预结算与复验](cart-checkout-preview.md) · [单品订单](single-item-orders.md) · [配置组混合下单](mixed-orders.md) · [订单查询与模拟支付](order-payment-history.md) · [取消与超时释放](order-cancellation-timeout.md)
- **账号与管理后台**：[认证与会话安全](authentication.md) · [商品管理](catalog-administration.md) · [价格与库存调整](price-stock.md) · [用户管理](user-administration.md) · [订单履约管理](order-administration.md)
- **交付与验收证据**：[空库部署与演示](delivery.md) · [任务 25 验收报告](evidence/task-25.md)
