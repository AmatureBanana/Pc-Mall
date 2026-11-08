# 任务10两轴审查

日期：2026-11-07。按 implement 技能要求应用 code-review 技能，两个独立上下文子代理分别只读审查 Standards 和 Spec。

用户明确要求完成后不在本地或远端保存 Git 节点，因此不采用技能默认的提交步骤。固定基线为开始时的 `8687df35656bc5dd8bee18d0ba1e7ced8201543b`，确认引用可解析、差异非空。使用 `git diff 8687df35656bc5dd8bee18d0ba1e7ced8201543b --` 及新增任务10文件全文审查；没有新增提交。排除原有用户文件 `.idea/jsLibraryMappings.xml`、`.local/` 与测试重新生成的历史截图。

## Standards

审查依据：`AGENTS.md`、`docs/agents/`、`README.md`、既有商品/认证/DIY 契约，以及技能附带的 Fowler 代码气味基线。仓库无 CODING_STANDARDS、CONTRIBUTING、GLOSSARY 或 ADR 文件。

发现项与处理：

| 发现 | 处理 |
| --- | --- |
| 类别名称不一致：服务端用「独立显卡」，目录与前端用「独显」 | 新增 `Labels`，直接使用 `product_categories.label` 的写法 |
| `CompatibilityService` 内重复声明了未被使用的 `InvalidConfiguration` | 删除重复类型，改为独立的包级 `InvalidConfiguration`，服务不再依赖 Web 建议类的嵌套类型 |
| `Part` 携带 `productId/sku/name/status` 却从不读取；Mapper 也多查了列 | 收窄为 `Part(category, parameters)`；Mapper 只选 `id, category, status, parameters` |
| `Read(Object...)` 用交错的裸 `Object` 与强转 | 改为类型安全的 `Field(category, code)` 记录 |
| 五条规则重复 `allPresent→pending / allUsable→unknown` 守卫 | 保留各规则显式守卫（每条消息不同），抽公共 `Read` 收拢读取与结果构造 |
| `DiyWorkspace.vue` 的 `checkFor` 定义后从未使用 | 删除 |
| `CompatibilityRules` 为 `public` 但参数是包级类型，修饰符无意义 | 收回到包级 |
| 未使用 `import`、测试中两条断言挤在一行 | 清理 |

保留的跨包 `public`：`HardwareCategory` 与 `HardwareParameters.httpUrl` 必须对新包可见，已补注释说明共享理由。

结论：无遗留硬性规范违例，无遗留阻塞项。

## Spec

审查依据：任务10、母规格 F03 与商品参数章节、`docs/authentication.md` 预留的检测入口、用户本轮三项决定。

发现项与处理：

| 发现 | 处理 |
| --- | --- |
| 真实数据库边界缺 UNKNOWN 用例（原用例仅 PENDING/NOT_FOUND，UNKNOWN 只在规则与浏览器夹具中） | 新增真实库用例：清空 socket 的 `sourceUrl` 后 C01 为 UNKNOWN 且 C04 仍 PASS；另新增畸形集合值用例确认降级为 UNKNOWN 而非 500 |
| 非文本集合条目会 NPE 变 500，与「异常返回 ERROR」不符 | `usable()` 与 `values()` 改为校验元素类型，畸形数组与安装组合降级为 UNKNOWN；新增两条规则用例 |
| PENDING 消息泄露枚举码（`尚未选择 MOTHERBOARD`），不满足「可读解释」 | 改用中文标签输出「尚未选择或当前不可用：主板、内存」；新增断言禁止出现枚举码 |
| 已选但已下架/不存在的配件在界面被说成「待选择」 | `missing` 每项渲染各自的 `NOT_SELECTED/NOT_FOUND/OFF_SALE` 与消息，界面分列展示；契约文档同步说明 |
| 「无来源即 UNKNOWN」可能让已知值永远无法 PASS | 保留该语义：无来源值不作为通过依据，但值本身仍在 `details` 中如实显示；已在契约中写明 |
| `scripts/test.ps1` 就绪探测改动、`HardwareCategory` 可见性变化属范围外 | 探测改动是让既有脚本在 PowerShell 5.1 下可运行的最小兼容修复，未改测试内容；可见性为跨包必需 |

审查同时确认：C01–C05 与冻结表逐项对应；每条规则返回编号、类别、参数与解释；真实 MySQL 上单条 FAIL 不清空其他结果；匿名可访问且遵循 CSRF；`corePassed` 恒否并列出 C06–C10；界面无整机通过表述。

结论：3 项真实缺陷已修复（畸形数据 500、PENDING 不可读、已选失效被误述），2 项测试缺口已补，0 项遗留。

Standards：0 项遗留；Spec：5 项已修复、0 项遗留。完整测试与适用范围见[验证记录](task-10.md)。

## 独立复审的两项 P2 修复验收

2026-11-07：后续独立复审发现以下两个独立缺陷（类型问题同时涉及 Standards 和 Spec），用户要求修复并验收。前文保留初次审查记录，本节记录本轮修复结论。

| 缺陷 | 修复 | 验收 |
| --- | --- | --- |
| `Read.usable()` 未按字段验证类型：数组形式的 `socket` 触发空指针，字符串 `stickCount="bad"` 被转为 0 后误判 PASS | 从 `HardwareParameters` 提取并共享原有单字段类型/词汇校验；诊断仅使用合法值及有效来源，畸形值视为未知，不清空其他规则结果 | 规则测试覆盖错误标量、集合、非法词汇、非正整数、越界数值、畸形安装组合；真实 MySQL HTTP 测试确认数组/对象插槽返回 200、C01/C03 UNKNOWN、C04 PASS，并确认字符串条数为 UNKNOWN |
| C02/C05 在任一字段未知时提前 UNKNOWN，隐藏已知冲突 | C02 独立检查代际与实际条数的确定冲突；C05 用已知条件筛选同一个有效安装组合，无匹配即 FAIL；没有确定冲突且数据不足才 UNKNOWN，全部必要数据已核验且匹配才 PASS | 规则及真实 MySQL HTTP 覆盖 DDR 冲突＋条数未知、SSD 规格冲突＋协议未知；规则另覆盖条数冲突＋代际未知、部分 SSD 参数跨插槽不能拼接，以及仍可能匹配时保持 UNKNOWN |

新增 7 条规则测试和 4 条真实库 HTTP 测试。修复前规则测试 28 条中出现 6 项断言失败和 1 项空指针错误，能实际捕获本轮缺陷；修复后规则 28/28、HTTP 12/12 全部通过。原始四个最小复现输入的结果依次为 FAIL、FAIL、UNKNOWN、UNKNOWN，无异常。

完整 `./scripts/test.ps1` 验收通过：真实隔离 MySQL 后端 109/109，Edge 40/40，TypeScript、Vite 构建及同源 JAR 打包全部通过。历史截图经测试重新生成后已恢复本轮运行前内容。未新增依赖、迁移或 Git 节点。

本轮两项 P2 均已修复并通过验收；C06～C10 与完整整机准入仍按任务11交接。详细执行记录见[任务10验证记录](task-10.md)。
