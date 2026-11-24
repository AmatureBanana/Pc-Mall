# 空库部署与演示

默认演示环境使用同一个 Spring Boot JAR 提供 Vue 页面、静态资源和 `/api`。本机验收工具为 Windows PowerShell 7、JDK 21.0.10、Node.js 24.22.1、npm 12.2.2、MySQL 8.0.45 和桌面 Edge 158.0.4370.33。桌面验收采用 1586 × 992 CSS 像素视口。

## 从空库启动

将 `JAVA_HOME` 指向 JDK 21 的安装目录，并将下方占位符替换为 MySQL 8.0 的安装目录，再从仓库根目录执行。首次使用 Maven Wrapper 和 `npm ci` 时需要能访问锁文件指定的依赖仓库，已有缓存时可复用。

```powershell
$env:MAVEN_USER_HOME = "$PWD\.local\maven-user"
./scripts/local-db.ps1 start -MySqlHome '<MySQL 8.0 安装目录>'
./scripts/package.ps1
./scripts/start-with-admin.ps1 -Username demo_admin
```

项目 MySQL 首次启动会创建 `pc_mall_demo` 和 `pc_mall_test` 两个空 schema 与各自的数据库账号，监听 `127.0.0.1:33307`。它把随机数据库凭据保存到 Git 忽略的 `.local/env.ps1`。使用自管 MySQL 时，分别建立两个空 schema 和仅对所属库有迁移与业务权限的账户，然后配置环境变量。若当前目录存在 `.local/env.ps1`，启动和测试脚本会读取它。自管环境可直接启动 JAR，并在进程环境中设置所需变量。

在已有项目库旁复测时，把交付工作树复制到独立目录，确保该目录尚未运行项目 MySQL，再执行 `./scripts/local-db.ps1 start -Port 33318`。脚本为这个目录生成新的 `.local` 数据与连接配置。已有 `.local/env.ps1` 时，`-Port` 会沿用该目录原有实例的配置。打包时包含工作树的 `backend/src/main/resources/db/catalog` 和 `data/catalog-rendering-v1`，这样可复现本轮扩展目录及其数据来源。

`start-with-admin.ps1` 通过隐藏输入读取初始管理员密码。选择自己的 8～64 字符密码，UTF-8 编码最多 72 字节。库里已有管理员时，初始化保留原账户和密码。打开 `http://127.0.0.1:8080`，确认商城可访问并通过统一登录进入后台。保留专用管理员浏览器窗口和一个独立普通用户窗口，可用 InPrivate 窗口隔离 Session。

重启时先用 `Ctrl+C` 停止前台 JAR，再使用原数据库配置启动。已有管理员可正常登录。Flyway 应报告 schema 已是最新版本。演示结束后可执行 `./scripts/local-db.ps1 stop` 停止该项目实例。

```powershell
. ./.local/env.ps1
& "$env:JAVA_HOME\bin\java.exe" -jar target/pc-mall-0.1.0.jar
```

## 构建与配置

Maven Wrapper 固定 3.9.11。`pom.xml` 固定 Spring Boot 3.5.16 与 MyBatis Starter 3.0.5，Boot 的依赖管理解析出 Spring Security 6.5.11、Flyway Core 与 Flyway MySQL 11.7.2、MySQL Connector/J 9.7.0。前端的直接依赖使用精确版本，包括 Vue 3.5.44、Element Plus 2.14.7、Vite 8.3.5 与 Playwright 1.64.1，直接和间接依赖由 `frontend/package-lock.json` 固定。打包脚本运行 `npm ci`、Vue 类型检查及 Vite 构建，再用 Maven `demo` profile 把 `frontend/dist` 放入 JAR。 [Maven 配置](../pom.xml) · [Wrapper](../.mvn/wrapper/maven-wrapper.properties) · [npm 锁文件](../frontend/package-lock.json)

| 环境变量 | 用途 |
| --- | --- |
| `JAVA_HOME`、`MAVEN_USER_HOME` | JDK 21 路径与可选 Wrapper 缓存路径 |
| `DB_URL`、`DB_USERNAME`、`DB_PASSWORD` | 演示 MySQL 连接，JDBC URL 示例 `jdbc:mysql://127.0.0.1:33307/pc_mall_demo` |
| `TEST_DB_URL`、`TEST_DB_USERNAME`、`TEST_DB_PASSWORD` | 常规专用测试库连接，schema 和账户均为 `pc_mall_test` |
| `PORT` | 同源服务端口，默认 8080，绑定回环地址 |
| `SESSION_COOKIE_SECURE` | 回环 HTTP 默认 `false`，HTTPS 配置为 `true`。Session Cookie 使用 HttpOnly、SameSite=Lax |
| `ADMIN_BOOTSTRAP_USERNAME`、`ADMIN_BOOTSTRAP_PASSWORD` | 仅在首次初始化时成对提供，由启动脚本设置至 Java 进程环境 |
| `BROWSER_BASE_URL`、`BROWSER_ADMIN_USERNAME`、`BROWSER_ADMIN_PASSWORD` | 单独运行 Playwright 时使用的本机服务和测试账号，由全套测试脚本生成临时值 |
| `LOGIN_MAX_FAILURES`、`LOGIN_MAX_KEYS`、`LOGIN_FAILURE_WINDOW`、`LOGIN_LOCK_DURATION` | 登录限流，可沿用 5 次、10,000 键、15 分钟和 15 分钟的默认值 |

## 数据与迁移

| 用途 | 版本与初始内容 |
| --- | --- |
| 演示 | `db/migration` 的 V1～V31 加 `db/catalog` 的 V32～V36。基础目录、扩展 SKU、渲染参考分及厂商参数补录均随 JAR 安装。空库初始 500 件在售和 1 件下架，覆盖八个分类 |
| 常规回归 | `test` profile 仅加载 V1～V31。基础在售商品 13 件，测试用失效样本和新交易按用例创建 |
| 性能负载 | `performance-acceptance-v1` 在常规基础上临时加入 112 个可追溯型号，共 125 件。负载结束清理本轮样本 |

各目录的售价与库存为本地 CNY 演示值。扩展目录的部分 SKU 缺评分、核验参数或可售库存，使用前台状态和当前 F03 结果判断能否装机。兼容检查使用 `f03-core-graphics-choice-v3`，显卡栏须明确选择绑定 CPU 的核显或独显。 [扩展目录和来源](catalog-expansion.md) · [性能数据](performance-data.md) · [兼容规则](compatibility.md) · [125 件负载数据](evidence/task-24.md)

## 五条演示路径

1. **游客浏览和搜索。** 打开商城，选择「内存」，搜索 `KF560C36BBE2-16`，打开金士顿 FURY Beast 16GB 详情。核对 SKU、单根规格、当前售价及库存。刷新详情链接，关闭后回到商品列表。搜索一个不存在的关键词，确认显示空结果。
2. **游客 DIY 登录恢复。** 注册普通用户并保持访客状态。进入「DIY 装机」，任意顺序选择 `INTEL-I5-14600K`、`ASUS-TUF-B760M-PLUS-WIFI`、`KINGSTON-KF560C36BBE2-16`、`SAMSUNG-MZ-V9P1T0BW`、`CORSAIR-CP-9020295`、`DEEPCOOL-CC560`、`DEEPCOOL-AK400`，显卡栏选择此 CPU 的核显。看到核心检测通过后选择「转入购物车」和「前往登录」。登录后回到原配置，确认重新报价与兼容检查，购物车仍为空。再次打开加购确认，点「确定」后检查七件真实商品归属同一配置组。
3. **三场景推荐加购。** 分别打开办公、游戏、创作推荐，输入 `20000.00` 元。比较各自的评分、总金额和硬件明细，使用推荐结果的加购入口进入 DIY。确认当前报价、库存与兼容性后整组加购。用不同的普通用户分别演示三个场景，可独立查看每个结果。检查配置组标注「来源：智能推荐」。
4. **混合下单、模拟支付、后台完成。** 选用同一买家，保留两组不同的完整配置。可从第 2 条的方案再选两根同款内存，保存为另一组。商城另加一件 `CORSAIR-CP-9020295` 单品。购物车选中单品和两个完整分组，结算并确认服务端当前报价。核对一笔待支付订单内的三部分归属、数量、小计和成交总额。点击「模拟支付」并确认。管理员在「订单管理」按订单号查询已支付单，在详情点「完成交易」并确认。买家刷新同一订单查看已完成。此时后台修改一个成交商品的售价或上下架状态，历史金额、成交明细及分组仍与创建时一致。
5. **主动取消与超时。** 用同一有库存的 SKU 创建两笔待支付单，记下初始可售数量和两次下单后的库存。主动取消第一单，刷新后核对取消原因和释放数量。第二单保持未支付直到服务端 30 分钟截止时刻，等下一次每分钟扫描后刷新，查看超时取消原因、释放量及支付入口。两张订单重复刷新时库存保持稳定，购物车维持下单后的状态。自动化复测使用可控制时钟推进截止时刻，并协调主动取消与扫描竞争。

## 运行验收

先停止持有 `target` JAR 的进程，确保回环端口 18081 空闲。从根目录运行下列命令。后端与 Playwright 连接专用 `pc_mall_test`，性能专项串行执行。

```powershell
./scripts/local-db.ps1 start
./scripts/test.ps1
. ./.local/env.ps1
python scripts/verify-performance-acceptance-data.py
./mvnw.cmd '-Dtest=PerformanceAcceptanceIT' test
```

`target/surefire-reports` 保存后端结果，`frontend/test-results` 保存浏览器运行产物。只跑某个浏览器文件时，在 `frontend` 下使用 `npm.cmd test -- <文件>`，并显式指定隔离服务的 `BROWSER_BASE_URL` 及需要的临时管理员变量。 [执行状态、环境与原编号证据](evidence/task-25.md)
