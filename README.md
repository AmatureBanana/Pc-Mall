# 装机猫 · 电脑硬件商城

任务 01～03：从空 MySQL 库经 Flyway 迁移、MyBatis 查询、公开 HTTP 接口，到 Vue 商品页面；支持八类硬件查询、搜索、详情，以及注册、Session 登录、CSRF 和后台角色准入。

## 环境与依赖

- JDK 21；本机已验证 Oracle JDK 21.0.10。
- Node.js 22.12+；本机已验证 24.22.1 / npm 11.19.1。
- MySQL 8.0（已验证 8.0.45）；复用安装目录中的程序，项目实例不注册系统服务。
- Maven Wrapper 锁定 3.9.11；首次运行需要联网下载。Java 依赖由 Spring Boot 3.5.16 BOM 固定，MyBatis Starter 3.0.5；前端直接依赖精确版本与完整 `package-lock.json` 一并提交。
- PowerShell 7、Edge（Playwright 默认使用本机 `msedge`，不自动下载另一套浏览器）。

Spring Boot 3.5 / MyBatis Starter 3.0 的兼容范围见[官方说明](https://mybatis.org/spring-boot-starter/mybatis-spring-boot-autoconfigure/)。[Vite](https://vite.dev/guide/) 与 [Element Plus](https://element-plus.org/en-US/guide/installation.html) 使用正式发布依赖。来源、许可和数据边界见 [docs/data-sources.md](docs/data-sources.md)。

## 一次性初始化

在仓库根目录执行，路径按本机安装位置调整：

```powershell
$env:JAVA_HOME = 'D:\Java'
$env:MAVEN_USER_HOME = "$PWD\.local\maven-user"
./scripts/local-db.ps1 -MySqlHome 'D:\MySQL\MySQL Server 8.0'
. ./.local/env.ps1
```

默认新实例只监听 `127.0.0.1:33307`，数据放在 `.local/mysql-data`，日志在 `.local/mysql-error.log`。脚本建立 `pc_mall_demo` 和 `pc_mall_test`；两个账户仅有各自库内迁移与数据访问权限，没有跨库或全局权限。root、演示和测试密码随机生成，保存在被 Git 忽略的 `.local/env.ps1`，初始化 SQL 成功后删除。不使用原有系统 MySQL 的数据目录、账户或端口。

首次成功验证两个数据库账户后写入 `.local/mysql-ready`。再次执行脚本只启动或检查同一实例，不重新建库或导入。已有凭据却缺少就绪标记、配置或数据目录时，脚本在连接和启动前拒绝半成品；检查日志和本地配置后再处理，不提供自动删库重建。停止实例：

```powershell
./scripts/local-db.ps1 stop
```

应用启动时自动运行版本化 SQL：V1 建表、V2 导入 12 个型号、V3 修正机箱来源、V4 增加分类参数、V5～V7 建立账号与初始化锁及更新时间。Flyway 校验历史且禁用 clean，重复启动不重复导入；不要编辑已执行的迁移，应新增版本。参数契约见 [docs/catalog-parameters.md](docs/catalog-parameters.md)。

## 开发启动

两个终端分别执行：

```powershell
# 终端 1，仓库根目录，设置 JAVA_HOME 后执行
./scripts/dev-backend.ps1
```

```powershell
# 终端 2
cd frontend
npm.cmd ci
npm.cmd run dev
```

打开 `http://127.0.0.1:5173`。Vite 把 `/api` 代理到 `http://127.0.0.1:8080`。后端启动脚本自动读取本地 env 文件；连接配置缺失、库不可用或迁移失败时启动失败，不回退到内存数据库。

## 同源演示构建与启动

先停止正在运行的 Vite（Windows 会锁定其原生构建模块），然后：

```powershell
./scripts/package.ps1
. ./.local/env.ps1
& "$env:JAVA_HOME\bin\java.exe" -jar target/pc-mall-0.1.0.jar
```

打开 `http://127.0.0.1:8080`。构建或重启后，对已经打开的浏览器标签页执行刷新；尤其要在实际交付的内置浏览器检查商品列表，避免旧页面资源与新接口不匹配。JAR 内包含前端页面和本地 SVG，商品仍通过同一源的真实 `/api/products` 接口读取 MySQL。该构建命令跳过测试；测试需按下一节单独运行。等效手动构建为 `frontend` 下 `npm ci`、`npm run build`，随后仓库根目录 `./mvnw.cmd -Pdemo -DskipTests package`。未构建页面时 `-Pdemo` 明确失败。

## 配置契约

| 环境变量 | 含义 |
| --- | --- |
| `DB_URL` | 演示 JDBC URL，示例 `jdbc:mysql://127.0.0.1:33307/pc_mall_demo` |
| `DB_USERNAME` / `DB_PASSWORD` | 演示库账户 / 密码，无默认值 |
| `TEST_DB_URL` | 显式专用 MySQL URL，schema 必须为 `pc_mall_test` |
| `TEST_DB_USERNAME` / `TEST_DB_PASSWORD` | 测试账户必须为 `pc_mall_test`，密码无默认值 |
| `PORT` | 后端端口，默认 8080；修改时同步调整 Vite 代理 |
| `BROWSER_BASE_URL` | Playwright 验收服务地址，默认 `http://127.0.0.1:8080` |
| `JAVA_HOME` | JDK 21 安装目录 |
| `MAVEN_USER_HOME` | 可选，Wrapper 分发包缓存；建议设为项目 `.local/maven-user` |
| `ADMIN_BOOTSTRAP_USERNAME` / `ADMIN_BOOTSTRAP_PASSWORD` | 可选，成对提供的初始管理员凭据；不写入仓库，已有管理员时不重置或新增 |
| `SESSION_COOKIE_SECURE` | 本机 HTTP 默认 false，HTTPS 部署设 true；Session 始终 HttpOnly、SameSite=Lax |
| `LOGIN_MAX_FAILURES` | 登录失败阈值，默认 5；按用户名及实际连接 IP 独立限制 |
| `LOGIN_MAX_KEYS` | 内存活动限流键上限，默认10000，至少2；受限请求不创建新键 |
| `LOGIN_FAILURE_WINDOW` / `LOGIN_LOCK_DURATION` | 失败窗口 / 锁定时间，均默认 15m，必须为正值 |

可使用自行管理的 MySQL：手工建立上述两个 schema 与分别受限的账户，在环境中设置变量。测试配置保护在数据源和 Flyway 创建之前运行，拒绝缺失变量、其他 schema、其他用户名、与显式测试变量不一致的数据源覆盖，也禁止 Flyway 独立连接、目标 schema 和 Hikari 连接身份覆盖。测试 JDBC 查询选项只接受 `characterEncoding`、`connectionTimeZone`、`connectTimeout`、`socketTimeout`、`sslMode`、`allowPublicKeyRetrieval`；不能在 URL 中改写账户或数据库。`test` profile 无演示库或内存库回退；启动时保持 Flyway 开启。故障注入测试仅在自己的不可连接端口配置中禁用迁移。

## 测试与验收

MySQL 就绪、前端依赖已安装后，在仓库根目录运行（测试脚本会自行启动隔离服务；不要另占用 18081 端口）：

```powershell
./scripts/test.ps1
```

它执行真实 MySQL 后端测试、Vue/TypeScript 检查、前后端同源构建和 Edge 浏览器测试。后端及浏览器服务均启用 `test` profile，仅使用测试库；浏览器服务默认在 18081，临时管理员凭据只放进程环境，结束时只删除该临时管理员并停止自己的进程。商品故障和空响应测试在浏览器网络边界注入，不冒充数据库故障证据。默认商品为固定夹具，不自动重置库；测试库请勿手工创建管理员，以免与首次初始化验收冲突。MySQL CLI 路径可传 `-MySqlHome`，测试端口可传 `-BrowserPort`。脚本会重新打包，请先停止持有 `target` JAR 的进程。

单文件开发循环：

```powershell
. ./.local/env.ps1
./mvnw.cmd -Dtest=ProductBrowserTest test
./mvnw.cmd -Dtest=ProductCatalogTest test
./mvnw.cmd -Dtest=TestDatabaseSafetyTest test
./mvnw.cmd -Dtest=CatalogFailureTest test
./mvnw.cmd -Dtest=SessionAuthTest test
cd frontend
npm.cmd run typecheck
npm.cmd test -- --grep 'guest sees'
```

单独运行认证浏览器文件须显式设置指向测试服务的 `BROWSER_BASE_URL` 及该服务的 `BROWSER_ADMIN_USERNAME` / `BROWSER_ADMIN_PASSWORD`；推荐直接用完整脚本自动处理临时凭据。执行结果见[任务01证据](docs/evidence/task-01.md)、[任务02证据](docs/evidence/task-02.md)及[任务03证据](docs/evidence/task-03.md)。

## 注册、登录与管理员

商城顶部提供注册和统一登录。注册只创建普通用户，成功后进入登录；登录按服务端角色进入商城或后台。后台提供商品、用户、订单的基础导航；维护功能由后续任务接入。用户可直接打开 `/#login`、`/#register`、`/#admin`，权限仍由后端检查。

首次建立管理员，在已构建 JAR 且已停止已有演示服务后运行：

```powershell
./scripts/start-with-admin.ps1 -Username your_admin
```

脚本隐式读取密码并启动应用，密码不进入命令行、脚本或仓库。已有任意管理员时初始化不再创建账户，也不重置密码或提升普通账户。未提供初始化配置时可以正常启动，仅开放普通用户注册。完整接口、权限和交接约定见[认证说明](docs/authentication.md)。

## 当前交付边界

公开的 `GET /api/products?category=MEMORY&keyword=Kingston` 返回当前上架商品。category 可省略，或使用 CPU、MOTHERBOARD、GPU、MEMORY、SSD、PSU、CASE、COOLER；keyword 最长100字符，首尾空白忽略，对名称、品牌、型号、SKU和摘要做不区分大小写的基础文本匹配，`%`/`_`按字面查询。二者组合过滤。合法空结果为 HTTP200、`data: []`；非法类别或过长关键词为400、`INVALID_QUERY`。

`GET /api/products/{id}` 返回 `data: {product, description, parameters, dataVersion}`，其中 product 保留真实ID、销售SKU、类别、品牌、型号、图片地址、两位小数金额字符串、库存与来源说明，parameters 保留有类型数值、集合、单位和逐字段来源。详情读取同一数据库来源，在只读事务中形成一致快照；不缓存价格、库存或上下架状态。不合法ID为400/`INVALID_PRODUCT_ID`，不存在为404/`PRODUCT_NOT_FOUND`，下架为409/`PRODUCT_NOT_SELECTABLE`；缺货仍能查看并显示“暂时缺货”。服务失败为503/500，不转换为空列表。

点击商品名称或图片打开详情，`/#product/7`这样的链接可直接打开并刷新；弹窗提供价格库存刷新、错误重试和关闭操作。类别及关键词走真实后端，价格范围与排序继续对当前查询结果做便捷筛选。账号接口、Session/CSRF 和后台准入已接入；其他路径默认拒绝。个人资料编辑、DIY工作区、兼容规则、购物车、订单、推荐与后台维护由后续任务实现。
