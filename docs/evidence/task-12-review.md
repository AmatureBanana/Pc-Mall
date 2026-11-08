# 任务12独立两轴审查

日期：2026-11-08（Asia/Shanghai）。任务：[游客登录恢复与权威重验](../../.scratch/pc-mall/issues/12-guest-restoration.md)。

用户确认基线为 `7948abfbe37fbe12a44c50bc562b20e6d93c20d0`（HEAD，Task-11）。比较 `git diff 7948abf --` 并完整读取任务12新增未跟踪文件；`git log 7948abf..HEAD --oneline` 为空。排除原有未跟踪 `.idea/jsLibraryMappings.xml`。只保留工作区改动，不创建提交、分支、标签或推送。

采用 code-review 技能，两个全新上下文子智能体分别只读检查 Standards 与 Spec；主审独立复核控制流并运行验证，没有把任务12原证据的自审结论当作本次验证结果。下列行号指本次修复前版本。

## Standards

规范来源：`AGENTS.md`、`docs/agents/`、README、母规格 Implementation Decisions / Testing Decisions、认证与 DIY / 兼容检测契约。仓库没有 GLOSSARY、ADR、CONTRIBUTING 或 CODING_STANDARDS。按技能应用十二项 Fowler 气味启发，仓库规范优先。

初轮发现 **2 项 P2**：

1. `frontend/src/diy.ts:135` 的 `comparable` 未要求规则版本一致；不同规则时仅把 `catalogChanged` 置为 false，可能显示“当前数据与登录前所见一致”。违反 `docs/diy-workspace.md` 中“规则版本已变”时不可声称一致的契约。
2. `frontend/src/useDiyWorkspace.ts:64` 的 `seenBaseline.value ?? readSeen()` 混淆“未冻结”与“已冻结但没有快照”。进入工作区后立即打开登录框、后台随后完成读取时，登录期间的新快照会冒充登录前基线。违反无快照不可比较及打开登录框时冻结基线的契约。`null` 承担两种生命周期含义也是 Primitive Obsession 的判断性启发，并非单独硬性风格违规。

修复后复核补充 **1 项 P2**：`frontend/src/diy.ts:155`（第一轮修复后行号）在当前为 VALID、登录前金额为 null 时直接跳过，没有比较已保存的状态。缺货引用补货后变为 VALID，而兼容指纹不含库存，因此可能错误声称一致。该项与前两项根因不同；同一修复子智能体已补充 `AVAILABILITY_CHANGED` 状态变化诊断与真实补货回归。Standards 累计 3 项 P2，均已修复，复核无遗留。

## Spec

规格来源：任务12及母规格，与当前 DIY、认证、完整九项 F03 检测契约交叉核对；后续任务已取消的 C07 不作为缺失项。

初轮发现 **5 项 P2**，均属已实现但逻辑错误；未发现新增业务范围越界：

1. `frontend/src/useDiyWorkspace.ts:69`：恢复较慢时，focus / visibility / 定时重检会中止恢复的请求；原恢复在新请求仍为 loading 时发布错误，新请求成功后仍停留错误摘要。违反旧尝试不得产生错误提示及恢复重验契约。
2. `frontend/src/useDiyWorkspace.ts:64`：首次恢复立即清空冻结基线；失败后重试改用后台更新后的快照，漏报登录前后变价。违反“打开登录框时把快照冻结一份……恢复时优先使用它”。
3. `frontend/src/diy.ts:135`：规则变化仍可能声明一致。与 Standards 第1项为同一根因，分别保留两轴结论，不重复计为独立修复。
4. `frontend/src/workspaceSession.ts:14`：来源只在内存，DIY → 登录 → 刷新 `#login` → 登录会返回商城且无恢复。违反“登录成功后回到同一 `/#diy`”。主审同时核实另一条来源生命周期问题：浏览器后退离开登录框时没有清来源，后续普通商城登录可能误返回 DIY。
5. `frontend/src/AccountPanel.vue:98`：慢登录请求在途时关闭弹窗会清来源；成功响应随后读取空来源并返回商城、跳过恢复。违反“关闭登录框都留在原工作区”。

修复后复核补充 **1 项 P3**：`docs/compatibility.md:80` 原先声称“登录失败或取消时不会调用本接口”，与允许关闭在途登录后接受服务端成功响应并重验的实际边界矛盾。已同步为真正失败或未提交即取消不触发登录恢复；迟到成功且仍在 DIY 时重验、不导航，常规后台重检独立进行。Spec 累计 5 项 P2 与 1 项 P3。

以上均局限于前端恢复状态、比较诊断及导航，未发现后端数据破坏或权限问题。按用户要求交给 **gpt-6.1-sol / medium** 子智能体修复。Spec 累计 5 项 P2 与 1 项 P3 均已修复，复核无遗留。

## 修复与验证

修复结果：

- 比较基线须满足规则版本、配置修订和全部类别/商品引用匹配；拒绝重复类别，并比较已保存的可售状态。恢复可售与变价分别诊断。
- 明确区分未冻结和冻结为空，恢复失败后的重试沿用原始基线；成功、关闭摘要或修订变化后释放。
- 恢复中的权威读取与后台重检协调，相关手动刷新在恢复期间禁用；编辑仍立即产生新修订，旧结果不能覆盖。
- 短生命周期 `pc-mall.diy.sign-in.v1` 仅保存当前标签页返回来源及诊断基线，认证页刷新保留；成功、取消和历史导航离开认证流程时清理。注册转登录保留来源，存储失败仍允许当前内存流程。
- 登录请求捕获所属流程，取消后的迟到成功可以反映服务端已建立的账户状态，但不改变用户导航；仍在 DIY 时重新读取当前服务端数据。
- DIY、兼容检测契约、任务原证据和任务复审链接同步更新。

基线全套独立复跑通过：后端 127/127、浏览器 58/58，类型检查、Vite 及同源 JAR 构建通过。修复前新增9条回归，在原实现上原8条通过、新9条全部失败（exit 1），截图、trace 和错误上下文保存在 `.local/task-12-review-red-results`；说明见 `.local/task-12-review-red-summary.md`。第一轮修复后专项 18/18 通过（包括补充的 UI 注册→登录），日志 `.local/task-12-review-green.log`。此结果早于上述补货状态遗漏的修复，不作为最终通过结论。

随后 `./scripts/test.ps1` 全套通过：真实隔离 MySQL 后端 **127/127**、Edge 浏览器 **68/68**，类型检查、Vite 与同源 JAR 构建通过，exit 0；日志 `.local/task-12-review-final.log`。

补货遗漏通过真实接口单独红灯验证：专用 PSU 库存 5→0，观察到 `OUT_OF_STOCK / null` 快照，打开登录后补回 5；前后 `catalogVersion` 相同，修复前仍错误显示一致。新增回归 **1/1 失败**，失败产物保留在 `.local/task-12-review-stock-red-results`，日志 `.local/task-12-review-stock-red.log`。修复后恢复专项 **19/19 通过**（31.6秒，exit 0），类型检查、Vite 与同源 JAR 构建通过，日志 `.local/task-12-review-stock-green.log`。

补货修复后执行 `.local/task-12-browser.ps1 -Spec tests`，重新完成 TypeScript 检查、Vite 与同源 JAR 构建，完整 Edge 浏览器 **69/69 通过**（1.7分钟，exit 0），日志 `.local/task-12-review-browser-final.log`。后端没有任何生产代码改动，沿用上述127项结果。最终覆盖包含恢复专项19项，以及既有认证、改密、商品、后台维护与 DIY 路径；测试服务和临时管理员由脚本清理。

首次沙箱内执行 `./scripts/test.ps1` 因 `java.net.SocketException: Permission denied: getsockopt` 无法连接本机隔离 MySQL，属于执行环境阻断，不作为业务断言失败。随后申请沙箱外执行同一脚本；日志 `.local/task-12-review-full.log`。所有 MySQL 操作仍受项目现有 `pc_mall_test` schema 与账户保护。

购物车真实创建入口仍属于任务13，本票恢复复用只读商品报价与兼容检测，并验证没有购买请求及相关持久化副作用；不把尚不存在的购物车表面当成已验证的创建断言。未更改此项既有、已确认的阶段边界。

两路只读复核均确认修复已落实。Standards 共3项P2、Spec 共5项P2与1项P3，全部已修复；两轴遗留均0项，最高遗留严重度均无。两轴中的规则版本问题为同一根因，不合并为跨轴总数。

已还原测试自动重写的12张既有截图：这些文件在本次审查开始时均无改动。最终只保留任务12实现、测试与文档改动；HEAD仍为 `7948abfbe37fbe12a44c50bc562b20e6d93c20d0`，暂存区无变化，未创建Git节点、分支、标签或推送。
