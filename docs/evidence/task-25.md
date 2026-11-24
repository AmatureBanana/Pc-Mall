# 任务 25 空库部署与 P0 验收报告

2026-11-24 已在独立空库部署同源演示 JAR，验证重启和五条核心路径，并完成权限及历史稳定性回归。185 条原场景中，171 条适用场景通过，14 条 P1 未纳入。F03 在约定四核 8 GB 级设备上的性能仍待验证，任务 25 的发布验收保留开启。 [部署与演示](../delivery.md) · [原编号逐项追踪](task-25-trace.md)

## 执行结果

| 状态 | 原验收场景 | 补充约束与依据 |
| --- | ---: | --- |
| 通过 | 171 | 真实 HTTP、规则、Edge 或任务 24 实测证据见逐项追踪。F04-T14 的通过范围是 P0 扩展范围提示 |
| 待执行 | 0 | 指定设备的 F03 性能复测列在阻塞项 |
| 失败 | 0 | 此处统计最终结果，已修正的首次执行失败另列 |
| 阻塞 | 0 | 另有 1 项发布约束阻塞。F03 在指定四核 8 GB 级设备上的 P95 尚待验证 |
| 未纳入 | 14 | 原矩阵明确列为 P1 的场景。F04-T14 的 P1 实际扩展风险检测也在本版范围外 |

用户本轮确认 F02-T05、F02-T06、F03-T10、F03-T11、F03-T12 采用任务 29 的新版规则。追踪表保留冻结原文，并记录本版断言。当前规则回归覆盖显卡缺项拒绝、显式核显、显式独显、兼容冲突及未知参数。 [任务 29](../../.scratch/pc-mall/issues/29-remove-graphics-output-check.md) · [规则回归](../../backend/src/test/java/cn/zhuangjimao/compatibility/CompatibilityCheckTest.java) · [浏览器回归](../../frontend/tests/compatibility-diagnostics.spec.ts)

## 环境与命令

Windows 11，Oracle JDK 21.0.10，Maven 3.9.11，Node.js 24.22.1，npm 12.2.2，MySQL Community Server 8.0.45。桌面回归使用 Microsoft Edge 158.0.4370.33、Playwright 1.64.1 `msedge`、默认缩放与 1586 × 992 CSS 像素视口。浏览器配置内的其他视口用例按各自标题记录。 [浏览器配置](../../frontend/playwright.config.ts)

| 执行 | 数据或端口 | 结果与证据 |
| --- | --- | --- |
| `./scripts/package.ps1` | 锁文件安装与同源打包 | npm ci、Vue 类型检查、Vite、Maven 打包成功。JAR 含 Vue 入口及后端依赖。最终空库演示使用的构建文件 SHA-256 `FECAE550BD62BB7B7726479C1169A036B32EF6DD1AE391F236987DC54599FCEB` |
| 在独立目录运行 `local-db.ps1 start -Port 33319`，同一 JAR 连续启动两次 | 独立 MySQL 33319 的 `pc_mall_demo`，应用 18085 | 开始时 schema 0 表。首启 36 条成功迁移至 V36。次启验证 36 条迁移且保持 V36，500 个前台商品 ID 和 SKU 完全一致。两次均为 501 条商品、500 条在售、八类 |
| 两次同源与管理员检查 | 18085 | `/`、同源 JS、商品列表、商品详情、原管理员登录与后台概览各两次 HTTP 200。管理员始终 1 个且只存哈希。改用第二组初始化环境变量重启后，原管理员仍通过，新凭据登录 401 |
| 安全和订单生命周期专项 | 33307 的 `pc_mall_test` | `SessionAuthTest,ProfilePasswordTest,UserAdministrationTest,OrderCancellationTimeoutTest,OrderPaymentHistoryTest` 共 37 项通过 |
| 新增与补强的交叉场景 | 同一专用测试库 | `OrderAdministrationTest#disablingABuyerPreservesTheirPaidOrder`、`PerformanceAdministrationTest#recommendationReadsTheUpdatedCpuScore`、`ConfigurationCartTest#singleItemsAndDifferentGroupsKeepTheirIdentityAndAggregateBeyondNinetyNine` 三项通过 |
| `./scripts/test.ps1` | 专用 `pc_mall_test`，浏览器服务 18081 | 最终真实 MySQL 后端 220 项、Edge 133 项均通过，Vue/TypeScript、Vite 与同源打包通过 |
| `npm.cmd test -- product-catalog.spec.ts configuration-cart.spec.ts recommendation-cart.spec.ts mixed-order.spec.ts order-cancellation-timeout.spec.ts --grep 'SYS-T01\|guest signs in and explicitly confirms\|推荐经登录重验\|mixed checkout rejects\|owner confirms cancellation'` | 同一空库初始化后的默认演示 profile，应用 18085，500 件在售 | 8 项通过，覆盖真实浏览搜索两项、DIY 登录恢复一项、三场景推荐三项、混合支付完成和主动取消各一项 |
| 正式扫描器与 Edge 超时复查 | 同一隔离演示库，正常接口创建订单后将这笔夹具的下单时间和支付截止时间同时提前 | 截止后支付 HTTP 409。真实每分钟扫描器写入 `CANCELLED/TIMEOUT`，Edge 读取真实结果。可售库存 11 → 9 → 11 并保持稳定，购物车为空，成交明细和总额一致 |
| `python scripts/verify-performance-acceptance-data.py` 与 `python scripts/catalog-rendering.py` | 仓库固定数据包 | 125 件负载目录的新增 112 个型号、5 个设备参考和 45 条原始样本通过核验。扩展渲染包核对 74 个设备、7,540 条采纳样本 |

本机原始记录在 Git 忽略的 `.local/task25-package-final.log`、`.local/task25-complete-demo.log`、`.local/task25-complete-demo/boot-*.log`、`.local/task25-full-final.log`、`.local/task25-focused-api.log` 和 `.local/task25-new-evidence-final.log`。`.local/task25-complete-browser-results/live-timeout.png` 保存真实超时页面。敏感凭据由初始化脚本和进程环境传递。`target/surefire-reports` 保存 JUnit XML。

空库复测在 `.local/task25-complete-demo` 中新建 `scripts` 目录，并复制仓库原版的数据库脚本。复制后的脚本在自己的目录内创建 MySQL 数据和环境文件。Java 进程使用该文件配置的 `DB_*`，并用 `--server.port=18085` 启动已有同源 JAR。

```powershell
$probe = Join-Path $PWD '.local/task25-complete-demo'
New-Item -ItemType Directory -Path (Join-Path $probe 'scripts') -Force
Copy-Item -LiteralPath './scripts/local-db.ps1' -Destination (Join-Path $probe 'scripts/local-db.ps1')
& (Join-Path $probe 'scripts/local-db.ps1') start -Port 33319
. (Join-Path $probe '.local/env.ps1')
```

## 核心路径与约束

| 路径 | 执行与可观察结果 |
| --- | --- |
| 浏览、搜索、详情 | 正式目录的 Edge 回归覆盖八类目录中的内存分类、关键词搜索、空结果、详情刷新和返回列表。后端 `ProductCatalogTest` 覆盖全部八类、价格库存、异常及最新可售状态。SYS-T01 |
| 游客 DIY 登录恢复 | Playwright 复用确定的七件商品与显式核显，观察登录后恢复原引用、现价及 F03 复验。确认前购物车为空，主动加购后只有一个配置组。另一用例验证恢复时的改价、缺货及下架诊断。SYS-T02、04、05、06 |
| 三场景推荐加购 | 正式目录上分别以办公、游戏、创作运行，验证预算推荐、登录恢复、复核获取、主动确认、真实商品 ID、购物车归属与库存不变。SYS-T03、06，F04-T23～25 |
| 混合下单、支付并完成 | 常规测试库和正式目录均运行 `mixed-order.spec.ts`。一个买家同时结算单品和两个配置组，断言库存聚合、成交总额、分组及重试后唯一订单。对同一订单执行浏览器支付和独立管理员会话确认完成，买家刷新可见已完成。SYS-T07、08、10 |
| 取消、超时和历史稳定 | 正式目录通过真实主动取消及超时扫描后回看订单。可控时钟的真实 MySQL 回归验证截止边界、释放一次、购物车不恢复和主动取消与扫描竞争。改价、下架、改名称与改参数后，成交金额和商品、分组快照均可复查。SYS-T09、11，F06-T14、18、21、22 |

### HTTP 权限与数据隔离

- `SessionAuthTest`、`UserAdministrationTest`、`SingleItemCartTest` 和 `OrderPaymentHistoryTest` 直接请求应用，覆盖游客 401、普通用户访问后台 403、缺少 CSRF 的写请求 403，以及非本人订单和购物项 404。
- `ProfilePasswordTest` 与 Edge 的 `profile-password.spec.ts` 覆盖两个独立会话改密后失效和新密码登录。`UserAdministrationTest` 与 Edge 的 `user-administration.spec.ts` 覆盖禁用与重新启用后的旧会话失效。`OrderAdministrationTest#disablingABuyerPreservesTheirPaidOrder` 还核对禁用期间后台历史和重登后本人历史。
- `MixedOrderTest` 与 `mixed-order.spec.ts` 核对新价格、状态或规格影响后续操作，保留已有订单的成交项与组归属。只在隔离测试库制造交易及故障注入样本。

## 公共商品与未编号约束

| 基线位置 | 适用行为或范围 | 证据和状态 |
| --- | --- | --- |
| G-01、报告 §3.1／§4.1、SYS-T01 | 游客与登录用户使用统一商品源，八类、关键词检索、真实型号、现价、库存、详情及有来源的参数 | 通过。空库及正式目录的 Edge 回归，`ProductCatalogTest` 与 `CatalogFailureTest` 核对八类和错误状态 |
| 报告 §5.1 与 NFR-01 | 密码哈希、注册角色保护、会话、CSRF、认证和所有权边界 | 通过。空库管理员检查和权限专项 |
| 报告 §5.2–§5.4 | 当前标签页配置、缺件／冲突优先级、价格和库存独立核验、三用途门槛及权重、确定性与无解证据、扩展范围声明 | 本版已通过。显卡选择按用户确认的任务 29 规则执行，实际 P1 扩展规则未纳入 |
| 报告 §5.5–§5.7 与 NFR-02／03 | 持久化单品与整组、全车 SKU 汇总、精确金额、选中项结算、订单状态转换、并发、事务、库存幂等、审计与历史 | 通过。真实 MySQL 套件及混合路径，任务 24 的负载与并发原始证据 |
| 报告 §5.7 与 §6 | 分类参数、来源、记录保护、状态与审计，独立的测试夹具和产品数据 | 通过。目录、数据来源、后台维护、隔离测试库及数据专项证据 |
| NFR-04 与报告 §7.1 | F03、F04、F05、F06 性能及 F07 分页初步目标 | 部分阻塞。任务 24 已测 125 件目录、规定并发和 P95。F03 实测设备 8 核 16 线程、约 15.3 GiB，四核 8 GB 级条件未验证。[原始性能记录](task-24.md) |
| NFR-05／08 与报告 §7.2、§9.6 | 选定的桌面浏览器，动作反馈及异常可读性 | 本轮 Edge 在 1586 × 992 视口通过。移动端、平板和其他浏览器无强制验收要求 |
| NFR-06／07 | 商品、兼容、推荐、购物车、订单等按职责组织，规则与数据具备扩展位置 | 按代码结构核对。规则具有独立标识与测试，分类参数与版本化数据按原层次维护 |
| NFR-09 | 核心规则具备可复现测试，独立时钟、并发协调和真实持久化 | 通过。测试入口见逐项追踪和复现命令 |
| NFR-10 | 可部署、启动和完整业务演示 | 同源空库与五条演示路径已验证。P0 发布验收仍需指定设备的性能证据 |

## 首次执行失败及恢复

- 默认权限下，InnoDB 打开已有项目数据时报告 `ibdata1` 不可写，`npm ci` 无法解析依赖仓库。使用获准的项目本地 MySQL 和依赖安装权限重跑后，两步通过。
- 首次临时核对脚本在迁移后因 PowerShell 保留变量命名中断。在另一个独立空实例重跑后，两次启动均通过。首次用正式目录执行的浏览器冒烟有两项卡在旧夹具数量 13，改为使用真实列表数量后六项通过，仍核对原搜索、详情与刷新行为。
- 追加的后台联动专项首轮暴露两处测试夹具错误。缩短用户名并更新已有规格行后，三个专项均通过。问题与本轮测试实现相关。原始记录保存在 `.local/task25-new-evidence.log`。

## 剩余交付条件

在四核 8 GB 级设备上，用约 100～200 件商品、预热后 10 并发复测 F03 完整检测的 P95≤1 秒。该环境尚缺，任务 25 保留发布验收开启。 [任务 24](task-24.md) · [任务 25](../../.scratch/pc-mall/issues/25-reproducible-delivery.md)

## Standards

规范审查发现同账号的新会话仍使用原购物车，已把三场景演示说明改为使用不同账号。审查另指出两段浏览器登录交互可能重复，当前用例保留两个会话的显式操作。已核对相对链接、Java 方法引用和差异格式。

## Spec

规格审查发现空库命令缺少目录隔离步骤、正式目录未覆盖后两条路径及 F04-T13 推荐引用不充分。已补记隔离命令，实际复测正式目录的混合支付和后台完成、主动取消和超时扫描，并加入独显缺货时仍返回办公核显的接口和浏览器证据。用户已确认任务 29 为五条显卡相关场景的本版口径。指定设备的 F03 性能项仍阻塞发布验收。
