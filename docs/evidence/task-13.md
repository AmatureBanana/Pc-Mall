# 任务13验证记录

日期：2026-11-08（Asia/Shanghai）。任务：[单品购物车持久化与数量管理](../../.scratch/pc-mall/issues/13-single-item-cart.md)。契约：[单品购物车](../single-item-cart.md)。

## 已交付

登录用户可从商品详情加购真实在售有库存的 SKU，同 SKU 合并为一行；账号栏“购物车”入口显示合计件数并打开 `#cart` 弹窗，可改数量、移除并查看服务端现价合计。购物车按账户持久保存，退出重登后仍在。

- **身份只取自 Session**：接口不接受 `user_id`/`accountId` 等字段（400），他人购物项按不存在应答（404），不向陌生人确认存在性。
- **数量边界**：单次加购与改量均为 1～99，且合并后或改后不得超过当前可售量；越界拒绝并**保持原记录**，不截断。
- **金额与失效行**：单价、小计与合计全部由服务端按当前 `products` 现价计算（`BigDecimal`、两位小数）；下架、缺货与库存不足的行保留并标注 `OFF_SALE`/`OUT_OF_STOCK`/`INSUFFICIENT_STOCK`，`validTotal` 只合计有效行。
- **不预占不扣减**：读取、加购、改量与删除均不锁、不改商品行，`stock`、`reserved_stock` 与 `stock_reservations*` 保持不变。
- **重复与并发**：`requestKey` 幂等（同键同内容重放首次结果、同键不同内容 409），并发加购在购物车行锁内累加不丢失，同一键的并发重试只加一次。
- 登录过期显示“请重新登录”，不把 401 当作规则错误。

## 环境与边界

- Windows、JDK 21.0.10、项目 Maven Wrapper、既有锁定 Node 24.22.1 / Vue 3.5.44 / Element Plus 2.14.7 / Playwright 1.64.1、Microsoft Edge。
- 真实 MySQL 8.0.45，显式 `pc_mall_test` schema 与同名受限账户，无内存数据库回退；`.local/env.ps1` 加载本地配置，不在证据中输出密码。
- 沿用户已确认的 TDD 接缝：浏览器端到端 + 真实隔离 MySQL HTTP 接口。仓库无前端单测设施，未新增。
- 原报告 SHA-256 `249a347c08f998e2d8f244bc332098da628a697359cf1a3239ed74dcf399f765`，矩阵 `a950301df806976c8d93e57a0c07301aebd52d10c141260415e457c72eaaee23`，与规格一致。

## 执行记录

| 命令/检查 | 结果 |
| --- | --- |
| `./mvnw.cmd -Dtest=SingleItemCartTest test`（首个可运行版本） | 12 项，1 失败 3 错误：测试夹具自身缺陷（账户名超出 20 字符、夹具会话共用同一购物车、并发测试竞态获取 CSRF），非产品断言失败 |
| 同一命令（夹具改为每用例独立账户 + 预取 CSRF） | 13/13 通过 |
| `cd frontend; npm.cmd run typecheck` | 开发循环多次通过 |
| `cd frontend; npm.cmd run build` | TypeScript、Vite 构建通过 |
| `.local/task-13-browser.ps1`（购物车专项） | 7/7 通过（19.6 秒） |
| `.local/task-13-browser.ps1 -Spec tests/single-item-cart.spec.ts,tests/guest-restoration.spec.ts` | 26/26 通过（47.8 秒） |
| `./scripts/test.ps1`（全套，审查修复后最终一次） | 真实隔离 MySQL 后端 **140/140**、Edge 浏览器 **76/76**，TypeScript、Vite 与同源 JAR 打包全部通过（exit 0），日志 `.local/task-13-full-tests.log` |

后端 140 项中含新增 `SingleItemCartTest` 13 项；浏览器 76 项中含新增 `single-item-cart.spec.ts` 7 项。审查修复合计后又执行了两次专项与一次全套，以最终全套为准。

### 首次全套运行的三处失败及处理（如实记录）

首次 `./scripts/test.ps1` 后端 **140/140 通过**，浏览器 72/75，三处失败逐一核对后均**不是产品回归**，也没有放宽实现来消除断言：

1. `guest-restoration.spec.ts` 两处 `expect(purchases).toEqual([])`。该断言原先把 `/api/cart` 的**任何**请求都算作购买副作用；任务13新增的账号栏徽标会合法地读取 `GET /api/cart`。改为：副作用断言只统计购物车**写**请求与全部订单请求（`watchPurchaseTraffic` 按方法过滤），并新增 `watchCartTraffic` 断言恢复期间发生的购物车调用**只有**读、没有写。原“恢复不产生购买副作用”的语义保持不变且更精确。
2. `single-item-cart.spec.ts` 数量用例断言输入 `100` 后提示“1～99”，真实行为是输入框 `maxlength="2"` 把 `100` 截为 `10`，随后服务端以库存上限拒绝。实现无误，测试前提有误：改为先断言 `maxlength` 与截断后的值，再用 `9`（超过可售 4）验证服务端 `CART_STOCK_LIMIT`，并断言输入框回退到服务端保存的数量。

另有一次 `.local/task-13-browser.ps1` 失败：新加的会话过期用例在选择器上命中两个“登录”按钮（账号栏与登录弹窗），改用 `.account-bar` 作用域定位后通过。全部为测试夹具/定位问题，期间修复的真实产品缺陷是**登录过期时购物车只报规则错误**（见下）。

### 全套首次中断说明

首次执行 `./scripts/test.ps1` 时，本机只有 Windows PowerShell 5.1，脚本的 `$ErrorActionPreference = 'Stop'` 会把 `mvnw.cmd` 写到 stderr 的 Mockito/JDK 代理警告当成终止错误，导致运行在第一个测试类启动后中断。该次运行**不是测试失败**。按任务12同样方式改为 `& ./scripts/test.ps1 > .local/task-13-full-tests.log` 重跑，未修改仓库内脚本。

## 实现中发现并修复的真实缺陷

1. **失效行不可改量**：初版允许对下架行直接改数量，与服务端“下架不得入车/改量”冲突；改为服务端拒绝对非在售行改量，页面禁用该行数量输入并提示“请移除”。审查后又修正为：库存不足行仍可改小数量自救，只对下架与缺货禁用。
2. **登录过期被当作规则错误**：初版购物车写操作把 401 显示为数量/库存错误文案；改为统一的 `failure()` 区分 401 并提示重新登录、清空页面购物车视图，并补浏览器回归。
3. **购物车视图跨账户残留**：退出登录或会话失效时清空页面购物车，避免前一账户的金额短暂残留。
4. **本地校验拒绝后输入框不回退**：全套测试捕获到输入 `0` 被前端拦住后，输入框仍显示 `0`，与“非法修改保持原记录”不符；改为拒绝时立即恢复服务端持有数量。
5. **缺货商品错误码**：见两轴审查 Spec 第 1 项。

## 两轴审查与修复

按 implement 要求使用 code-review 技能：以开始时 HEAD `8ef1a062` 为基线，两个全新上下文子代理并行只读审查 Standards 与 Spec；子代理未修改文件，自动化验证由主代理执行。审查覆盖全部新增与修改文件，排除原有未跟踪 `.idea/jsLibraryMappings.xml`。

**Standards（发现项，均已修复）**

1. 任务票 `Status` 仍为 `ready-for-agent`，而验收已全部勾选 → 改为 `resolved`，与 01～12 及 26～30 一致。
2. 契约文档错误码表漏了实现中的 `CART_REQUEST_INCOMPLETE`，且新增的 `CART_CONFLICT` 未记录 → 补齐两张表并说明两者的不可达/兜底语义。
3. `AccountPrincipal` 同时保留包内 `accountId()` 与公开 `ownerId()` 表示同一字段 → 保留一个包内访问器与一个有说明的公开访问器，注释写明用途，不再让 Javadoc 与实现矛盾。
4. 会话标志 `signedIn` 放在 `cart.ts`，由身份之外的模块发布 → 移入既有的 `auth.ts`，仍由唯一轮询会话的账号栏发布，其他表面不再自行请求 `/api/auth/me`。
5. `CartPanel` 声明了从不触发的 `expired` emit，`AccountPanel` 存在对应死接线；另有一个从未使用的 `stateLabel` computed → 一并删除。
6. 1～99 分散在服务端常量、SQL 约束、前端校验与模板五处 → 前端收敛到单一 `cartQuantity` 常量并复用于校验、提示与 `maxlength`，服务端常量与 SQL 约束保留在各自权威位置。
7. `guest-restoration.spec.ts` 两处重复的路径正则 → 提取为共享常量。
8. 手写 `new CartMapper.CartRow(header.revision() + 1)` 与忽略 `bumpRevision` 影响行数，与同类的 `CatalogAdministration` 写法不一致 → 统一为检查影响行数，失败即 `changeConflict()`，避免静默提交。
9. 后端测试方法用 snake_case，与本仓库其余测试（camelCase）不一致 → 全部改为 camelCase，并去掉全限定 `MallApplication`。
10. `cartCalls.every(...)` 在空数组上恒真却声称证明了“只有读” → 增加 `expect(cartCalls).not.toEqual([])`，确保确实发生过读。

**Spec（发现项，均已修复）**

1. **真实缺陷——缺货商品的错误码与契约不符**：文档写 `PRODUCT_NOT_SELECTABLE`，实现只校验 `ON_SALE`，`stock=0` 落到 `CART_STOCK_LIMIT`；测试只断言 409 因而无法发现 → 加购时显式判断可售量为 0 并返回 `PRODUCT_NOT_SELECTABLE`。
2. **测试无法区分规则**：所有拒绝只断言状态码 → 新增 `outcome()`/`errorCode()`，改为断言 `CART_QUANTITY_LIMIT`、`CART_STOCK_LIMIT`、`PRODUCT_NOT_SELECTABLE`、`CART_ITEM_NOT_FOUND`、`IDEMPOTENCY_KEY_REUSED`、`INVALID_CART_INPUT`、`PRODUCT_NOT_FOUND`、`CSRF_INVALID`。
3. **`no-store` 只覆盖成功响应** → `CartErrors` 的所有错误响应也带 `no-store`；文档同步说明 401/403 由共享安全链返回。
4. **浏览器恒真断言**：`click({ force: true, trial: true })` 是试点击、从不派发事件 → 删除该“证明”，保留真正起作用的 `toBeDisabled()` 与请求计数断言。
5. **越权仅覆盖“看不到”** → 浏览器用例改为用第二个真实账户直接提交第一个账户的 `itemId` 改量与删除，断言 404/`CART_ITEM_NOT_FOUND`；后端用例补充伪造 `user_id` 字段被拒。
6. **全局表断言脆弱**：`COUNT(*) FROM stock_reservations` 受其他夹具影响 → 改为按夹具商品限定。
7. **失效行的可修复性未被验证**：`INSUFFICIENT_STOCK` 本可改小数量自救，但页面禁用了所有非 VALID 行的输入 → 页面改为仅对 `OFF_SALE`/`OUT_OF_STOCK` 禁用，浏览器用例补“库存降到低于购物车数量后可改量恢复有效”。
8. **`revision` 已公开但无约束**属任务15范围 → 保留显示用途，在文档边界中明确本票不接受期望修订、不做基于修订的冲突拒绝，不越界实现任务15。

最终两轴遗留均为 0 项。

## 验收关联

| 原场景 | 本票证据及限制 |
| --- | --- |
| F05-T01、F05-T02 | `SingleItemCartTest` 断言加购后 `items` 仅一行、数量合并、金额等于服务端现价乘积 |
| F05-T03、F05-T04 | 1、60、99 与 0、100 的输入边界；合并超 99 与超可售量分别返回 409 且原行不变 |
| F05-T05、F05-T06 | 删除后明细为空；读、加购、改量、删除全过程 `stock`/`reserved_stock`/`stock_reservations*` 不变 |
| F01-T09、F01-T15 | 匿名 401、他人 itemId 404、伪造 `accountId` 400；两个真实账户互不可见 |
| SYS-T04 | CSRF 缺失写入 403，且不产生行；`cart_request` 幂等记录与主键归属 |

补充用例独立命名（如“同键并发只加一次”“价格变化在下一次读取生效而不改写已存行”“200 毫秒级并发加购不丢数量”），不伪造冻结验收编号。**配置组入车与结算**由任务14、15 提供，本票不实现也不宣称已验证；**下单、预占与支付**由任务16～20 提供。

## 阶段边界

无新增依赖，未复制参考项目代码或第三方数据。不实现配置组、混选结算、报价确认、订单、支付或任何库存预占/扣减。按用户要求仅保留工作区改动，不创建提交、分支、标签或推送；原有未跟踪 `.idea/jsLibraryMappings.xml` 未改动。

## 后续独立复审

2026-11-08 的[独立两轴复审](task-13-review.md)在本实施记录之外发现并修复了账户切换旧响应、未知结果加购重试、异步视图回退及两处后端错误映射。复审另补真实响应丢失、CSRF拒绝后的安全重试、乱序与账户隔离回归。最新验证为后端142/142、购物车浏览器16/16、其余浏览器69/69，来自审查记录注明的执行批次；原实施阶段140/76的记录保留为历史。完整失败与修复证据、独立旧 DIY CSRF 观察及其限制以复审记录为准。
