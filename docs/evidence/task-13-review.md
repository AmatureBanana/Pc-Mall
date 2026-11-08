# 任务13独立两轴审查

日期：2026-11-08（Asia/Shanghai）。任务：[单品购物车持久化与数量管理](../../.scratch/pc-mall/issues/13-single-item-cart.md)。

范围为 `git diff 8ef1a06...434f826`：任务12提交 `8ef1a0625f9a783d8ddc404637e8352a531b48bd` 至任务13提交 `434f826`，提交列表仅 `434f826 Task-13`。修复保留在工作区，不创建提交、分支、标签或推送。原有未跟踪 `.idea/jsLibraryMappings.xml` 排除；用户在审查期间更新的 `AGENTS.md` 原样保留，不作为本次修复改动。

使用 code-review 技能，由两个全新上下文子智能体分别只读审查 Standards 和 Spec；主审独立核对控制流、补充身份边界，并协调真实 MySQL 与浏览器验证。原任务13证据中的“零遗留”不作为本次审查结论。下列发现行号指修复前版本。

## Standards

来源：`AGENTS.md`、`docs/agents/`、README、母规格 Implementation Decisions / Testing Decisions、购物车与认证、价格库存契约。仓库没有 GLOSSARY、ADR、CONTRIBUTING 或 CODING_STANDARDS。应用技能的十二项 Fowler 气味启发，仓库文档规则优先；没有值得单列的气味判断项。

1. **[P1] 退出后仍可回填前账户的购物车** — `frontend/src/cart.ts:84–102`。违反购物车契约“退出登录或会话失效会清空页面上的购物车视图，不残留他人数据”。`clear()` 未使在途请求失效；另一账户的 `refresh()` 会复用旧 `inflight`，旧响应重新写入共享购物车。实际源码内存复现 `refresh(A) → clear() → refresh(B) → 释放 A 响应`：仅加载一次，最终仍含 A 的商品。加购、改量、删除响应也须绑定身份代次。
2. **[P2] 无法建立事务时返回其他领域的错误码** — `backend/src/main/java/cn/zhuangjimao/cart/CartErrors.java:31`。违反购物车契约的数据库故障 `503/CART_UNAVAILABLE`。处理器仅捕获 `DataAccessException`；`CannotCreateTransactionException` 落入全局商品处理器，返回 `CATALOG_UNAVAILABLE`。已通过真实认证后仅注入业务事务故障的公开 HTTP 测试确认，不停止共享数据库。

初轮 Standards 共 2 项，最高 P1；账户隔离项与 Spec 第1项是同一根因，两轴分别保留，不合并或跨轴排序。

## Spec

来源：任务13、母规格 F05 / F06 与 Testing Decisions、`docs/single-item-cart.md`。配置组、结算、订单属于后续任务，未作为本票缺失项。

1. **[P1] 切换账户后旧响应泄露前账户购物车** — `frontend/src/cart.ts:84,102`。B 登录后的刷新复用 A 的在途读取，A 的晚到响应可显示其 SKU、金额与数量，违反“清空……不残留他人数据”。写响应晚到亦然。
2. **[P2] 加购响应丢失后重试会再次加购** — `frontend/src/cart.ts:59,116`。页面未保留未知结果提交的 `requestKey`。首次请求已提交但响应丢失，按提示重试生成新键，实际数量从1变2。违反任务票“重复请求……不会意外重复加购”；原测试只验证在途禁用。
3. **[P2] 晚到响应覆盖较新的购物车结果** — `frontend/src/cart.ts:88,130`。所有成功响应直接覆盖共享视图。挂起 revision1 的读取，完成数量5/revision2 的修改，再释放旧读取，页面退回数量1/revision1。多行写响应乱序同样影响数量、合计和成功反馈，违反查看与明确反馈要求。修复不需要引入任务15的服务端期望修订冲突拒绝。
4. **[P3] 缺货行改量错误码违背契约** — `backend/src/main/java/cn/zhuangjimao/cart/ShoppingCart.java:109`。已有商品库存降至0后，合法改量返回 `CART_STOCK_LIMIT`；契约要求 `OUT_OF_STOCK` 改量返回 `PRODUCT_NOT_SELECTABLE`。

初轮 Spec 共 4 项，最高 P1；未发现需要纳入本票的额外范围缺失或业务范围越界。前三项已用实际 TypeScript 源码、真实 Vue ref 和受控 HTTP 桩复现，后续通过真实服务浏览器回归验证。

## 修复与验证

按用户指定，身份隔离、未知结果重试与异步乱序交给 **gpt-6.1-sol / xhigh** 修复；两个局部后端错误映射交给 **gpt-6.1-sol / medium** 修复。使用 diagnosing-bugs 的可失败回归反馈循环；路径已经由审查定位，直接在公开 HTTP / 浏览器接缝复现，没有额外加入生产调试日志。

### 已执行的缺陷复现

| 命令 / 场景 | 结果与证据 |
| --- | --- |
| `./mvnw.cmd '-Dtest=SingleItemCartTest#outOfStockLineCannotChangeQuantityAndKeepsItsRevision' test` | 修复前 1/1 失败：期望 `PRODUCT_NOT_SELECTABLE`，实际 `CART_STOCK_LIMIT`；`.local/task-13-review-stock-red.log` |
| `./mvnw.cmd '-Dtest=CartFailureTest,SingleItemCartTest' test` | 库存修复后原购物车及新增边界 14/14 通过；事务故障注入 1/1 失败：期望 `CART_UNAVAILABLE`，实际 `CATALOG_UNAVAILABLE`；`.local/task-13-review-errors-red.log` |
| `./mvnw.cmd test` | 两处后端修复后 **142/142 通过**，exit 0；`.local/task-13-review-backend-green.log` |
| `.local/task-13-browser.ps1`，前端实现保持原样，仅新增测试 | 原有 7/7 通过；新增 8 例中 5 例失败、3 例被测试内额外刷新掩盖，不能据此声称这3项无缺陷。初轮产物 `.local/task-13-review-red-results-initial/test-results` |
| `.local/task-13-browser.ps1 -Spec @('tests/single-item-cart.spec.ts', '--grep', 'delayed\|old add waiting\|retrying an add\|out-of-order')`，补强等待与断言后，仍为原前端实现 | 新增 **8/8 失败**。先等真实请求完成/失败及渲染边界，再在新读取前检查徽标，确保迟到写不能被额外刷新掩盖；旧读取回退发生在 gate 释放后。完整控制台 `.local/task-13-review-browser-red-console.txt`，构建记录 `.local/task-13-review-browser-red-confirmed.log`，截图、trace、上下文 `.local/task-13-review-red-results` |

这些浏览器测试使用真实服务与隔离 MySQL，`route.fetch()` 先完成实际事务，再受控释放响应；丢响应用例在服务端提交后中止浏览器响应。账户切换通过 hash、页面按钮与共享 Cookie 的真实认证 API 完成，没有重新加载页面来抹掉模块状态。CSRF 等待用例证实旧动作仍发出了 POST；该次 POST 被共享 CSRF 防护拒绝，未声称该用例已造成跨账户写入。

首次沙箱内 MySQL 连接被系统拒绝（回环连接错误10013），启动脚本在沙箱内超时，属于执行环境阻断；在沙箱外按项目脚本启动既有实例后，测试仅使用明确受限的 `pc_mall_test` schema / 账户。没有重建数据库或停用安全保护。局部故障注入仅替换单个 Spring 测试上下文中的事务管理器，认证仍访问真实 MySQL。

### 最终修复与复查

已完成的修复：

- 购物车请求绑定身份代次与 `AbortSignal`，退出、失效或发现账户 ID 改变时废弃旧读取、写入与 CSRF 等待；旧请求的 `finally` 不清掉新请求的加载状态。
- 当前快照按购物车修订接纳，同修订按请求次序接纳，因此既能阻止提交结果回退，又能继续刷新未增加购物车修订的价格与库存。
- 未确认加购按当前身份、商品与数量保留原键，成功后主动再次加购使用新键。成功重放后独立读取当前状态，不复用加购前的在途读取。
- 每行独立记录处理中状态；只保存尚未提交的数量草稿，写入结束后显示最新接纳的服务端数量。
- 缺货行拒绝改量并保持数量、行 ID 与修订；业务事务无法建立时返回购物车领域错误，且不泄露异常细节。

购物车专项 **15/15 通过**（39.6秒，exit 0），包括原7例与新增8例；完整控制台 `.local/task-13-review-browser-green-console.txt`，类型检查、Vite 与同源 JAR 构建记录 `.local/task-13-review-browser-green-build.log`。后端只读复核已确认两处修复成立，无遗留。

前端只读复查确认代次、abort、旧请求清理、修订排序和行草稿修复成立，但发现同一幂等根因还有边界：首次已提交但响应丢失，同键重试若被服务前 CSRF 403 拒绝，不能推导首次未提交；此时退役旧键会让第三次点击重复加购。另一个精确断言补强为：成功重放后，在当前读取仍挂起时，也不得短暂显示首次快照。

新增真实 CSRF 回归并补强重放用例后，筛选 `a CSRF rejection while retrying|retrying an add whose success response` 得到 **2/2 失败**，分别捕捉第三次请求换键与旧快照徽标出现。完整控制台 `.local/task-13-review-retry-red-console.txt`，截图、trace、上下文 `.local/task-13-review-retry-red-results`。随后仍由同一 xhigh 子智能体最小修复：仅首次明确4xx才退役；未知结果重试的4xx保留原键，401仍使身份失效；重试成功只接纳独立 GET，不接纳重放快照，读取失败明确提示已加购但刷新失败。类型检查通过，最后一轮只读复核确认原遗漏闭合，没有新的任务13发现。

### 完整回归中观察到的既有 DIY 问题

第一次 `./scripts/test.ps1` 在后端 **142/142 通过**后，浏览器 **83/84 通过**；唯一失败为 `guest-restoration.spec.ts:449` 的登录页重新加载后恢复流程，所有15条购物车用例通过。日志 `.local/task-13-review-full-green.log`（文件名沿用执行前命名，该轮并非全绿），失败产物 `.local/task-13-review-full-first-results/guest-restoration-a-login--ed64e-serves-its-workspace-origin-chromium`。

只读 trace 诊断：登录200后，同一毫秒发出两次 CSRF GET，一次取消、一次200；报价200，但兼容检测 POST 403。失败 POST 的令牌与成功 GET 返回值一致，会话也一致（仅在内存比较，没有输出凭据）。`useDiyWorkspace.ts:114–115` 挂载时先 `revalidate()`，发现待恢复后又 `restore()`；恢复再次刷新并取消前轮请求。`diy.ts:205` 独立获取 CSRF，未经过本次修改的 `auth.write`，控制器也与购物车独立。相关 DIY、请求封装及认证控制器相对任务12基线均无改动。本地 Spring Security 6.5.11 的“读取缺失→生成→保存”不是原子操作，存在初始化竞争条件。

**证据限制：** 被取消的 CSRF 响应未捕获正文，不能直接证明其覆盖了服务端令牌；没有运行原基线复现，因此具体服务端覆盖时序仍是推断。证据指向基线已有的竞争条件，不能归为本次修改引入的新 DIY 调用，也不以一次重跑通过证明根因已经消失。本次保持任务13范围，不修改旧 DIY 模块或削弱恢复断言，保留该观察供后续跟进。

### 最终验证口径与清理

最后一轮完整浏览器运行共85项，**78通过、7失败**；这7项均为新增测试共用的夹具错误 `route.fulfill: Route is already handled!`，不是业务断言失败。夹具原先在释放 gate 后立即 `unroute(path)`，抢在回调 `fulfill` 前把请求标记为已处理。已最小修正为：释放 gate → 等待本回调完成 → 移除当前路径拦截 → 等待请求完成/失败及渲染边界。没有捕获吞掉异常，没有修改任何产品断言或业务实现。该轮完整控制台 `.local/task-13-review-browser-final-console.txt`，构建 `.local/task-13-review-browser-final-build.log`，失败产物 `.local/task-13-review-fixture-failure-results`。既有 DIY 恢复在该轮通过，但不据此宣称其竞争风险已消失。

夹具修正后，执行 `.local/task-13-browser.ps1`，**购物车16/16通过**（39.8秒，exit 0）；包含原7例、新增9例，以及重放成功读取期间不显示旧快照的补强断言。完整控制台 `.local/task-13-review-cart-final-console.txt`，TypeScript、Vite 与同源 JAR 构建 `.local/task-13-review-cart-final-build.log`。最后改动仅为测试夹具，未改变前一轮已通过的其余69项所运行的业务代码。

因此最终证据为：**后端142/142；购物车浏览器16/16；其余浏览器69/69**。后两组来自相邻两次执行，不把它们写成一次85/85全绿。后端在前端最后补修后未发生代码变化，不重复执行无关后端测试。

测试服务与临时管理员由脚本清理。已恢复测试自动改写的12张旧截图，未覆盖用户更新的 `AGENTS.md` 或原有 `.idea/jsLibraryMappings.xml`。最终差异检查通过，无新增依赖、数据库迁移、生产调试日志或无关模块重构；保留本地修复、测试与证据文件，未提交或推送。

Standards 共2项，已修复2项，最高遗留严重度无；Spec 共4项，已修复4项，最高遗留严重度无。旧 DIY 的独立观察及证据限制按上节保留，不冒称已修复。
