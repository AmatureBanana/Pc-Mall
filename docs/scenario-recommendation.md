# 三场景预算推荐

游客和登录用户可从 DIY 装机首页选择办公、游戏或创作推荐。三个入口共用 `POST /api/recommendations`，沿用 Session CSRF，响应带 `Cache-Control: no-store`。请求示例为 `{"scenario":"GAMING","budget":"10000.00"}`。预算为大于零、最多十位整数和两位小数的 CNY 十进制字符串。

| 用途 | CPU 最低分 | 独显最低分 | 内存 | SSD | CPU / 独显 / 内存 / SSD |
| --- | ---: | ---: | ---: | ---: | --- |
| 办公 `OFFICE` | 25 | 可用经核验的核显或选独显 | 8 GB | 256 GB | 50 / 0 / 30 / 20% |
| 游戏 `GAMING` | 35 | 40 | 16 GB | 512 GB | 30 / 55 / 10 / 5% |
| 创作 `CREATION` | 35 | 35 | 16 GB | 512 GB | 35 / 35 / 20 / 10% |

CPU 和有权重的独显分沿用任务 08 固定 Blender 4.3.0 参考及 `performance-v1` 映射。内存容量节点 8／16／32 GB 对应 50／80／100 分，SSD 容量节点 256／512／1000 GB 对应 40／75／100 分，节点间线性插值。内存按数量累加容量，数量受库存及主板插槽约束。 [性能参考与来源](performance-data.md)

候选搜索保留每类 15 个候选及 64 条搜索路径，兼顾低价和高分，并全量复核入围方案的 F03 核心规则。有效候选最高分 1 分以内优先低价，同价按各类别的数值商品 ID 顺序决定，全部 ID 相同时再比较内存数量。成功响应给出当前目录中一套完整、可售、有库存、预算内且核心通过的配置，包含商品明细、预算余额、分项及权重和兼容检查范围。进入 DIY 后按当前数据复验，修改后旧推荐说明失效。

手动 DIY 继续按各项归一化分的最低分显示整机性能，40／70／90 分对应办公／游戏／创作。迁移 `V30__geekbench7_cpu_references.sql` 为三款 CPU 补录 Geekbench 7 单核和多核来源数据。导入预算方案后保留原用途分，用户手动修改配置后才启用整机评估。 [基准来源、档位与限制](research/scene-cpu-benchmarks.md)

## 推荐失败状态

| HTTP | 状态 | 含义 |
| --- | --- | --- |
| 400 | `INVALID_INPUT` | 请求格式、用途或预算无效 |
| 200 | `NO_AVAILABLE_PRODUCT` | 必需类别无在售库存，或办公没有可用核显而独显缺货 |
| 200 | `INSUFFICIENT_DATA` | 无法用已核验的评分与参数证实场景门槛或核心兼容性 |
| 200 | `BUDGET_INSUFFICIENT` | 预算低于全目录必需类别在售有货价格下界，或完整枚举后证实兼容候选均超预算 |
| 200 | `NO_COMPATIBLE_COMBINATION` | 对当前达到场景门槛的候选进行完整核验，所有组合均有已确认的冲突或缺少有效显卡选择 |
| 200 | `NO_RESULT_FOUND` | 有界搜索未命中，证据不足以断言预算不足或无兼容组合 |
| 200 | `REVALIDATION_REQUIRED` | 入围候选的权威 F03 复核或报价与搜索快照不一致 |
| 503 | `SYSTEM_ERROR` | 推荐服务或 F03 调用异常 |

枚举只在未经截断的候选组合数至多 4096 时执行。最低价结论需要更强的证据，失败响应只提供分类与原因。失败响应的商品列表为空，评分、价格和兼容结果为空。

## 验收覆盖

用例编号以原需求报告 §5.4.12、§9.2 和追踪矩阵的验收追踪表为准。两份源文件的 SHA-256 均与实施规格一致。 [实施规格](../.scratch/pc-mall/spec.md#further-notes)

| 原验收编号 | 自动化验证 |
| --- | --- |
| F04-T06、F04-T07 | 独显清零后游戏和创作均返回缺货。独显 35 分满足创作门槛，游戏仍需 40 分 |
| F04-T08、F04-T09、F04-T15 | 关闭必需类别供应后无方案。成功配置每类一个 SKU，且当前库存覆盖对应数量 |
| F04-T10、F04-T11、F04-T12、SYS-T12 | CPU 插槽冲突、机箱限高冲突、缺失插槽参数均无成功结果。不可用的 F03 读取得到错误响应，浏览器拒绝矛盾检查结果 |
| F04-T13、F04-T14 | 办公核显绑定通过共享 F03。界面展示六项未执行的扩展检查和风险范围 |
| F04-T16、F04-T17 | 无效 CPU／独显参考被过滤，核对固定插值和三套权重 |
| F04-T18、F04-T19、F04-T20 | 核对精确预算和报价加总。可靠价格下界返回预算不足，超出可完整核验的候选规模时返回当前未找到 |
| F04-T21、F04-T22 | 测试一分以内的低价优先、同价按商品 ID、重复请求一致 |
| SYS-T03 | 游客经真实接口和 Edge 操作三个入口，验证成功方案及分类失败 |

补充验证覆盖手动 DIY 的缺件、缺少 CPU 基准、实际 F03 冲突和 F03 请求异常，确保没有对应的场景评分。接口测试使用隔离的 `pc_mall_test`，故障用例在 F03 的 MyBatis 读边界注入丢失记录和 SQL 异常。浏览器使用真实接口验证成功和已证实的预算不足，其余推荐分类在网络边界注入对应响应以验证呈现及导入控制。 [规则测试](../backend/src/test/java/cn/zhuangjimao/catalog/RecommendationRulesTest.java) · [推荐接口测试](../backend/src/test/java/cn/zhuangjimao/catalog/ScenarioRecommendationTest.java) · [推荐浏览器测试](../frontend/tests/scenario-recommendation.spec.ts) · [手动 DIY 接口测试](../backend/src/test/java/cn/zhuangjimao/catalog/DiyWorkspaceTest.java) · [手动 DIY 浏览器测试](../frontend/tests/diy-workspace.spec.ts)

## 执行记录

- 修改前用 `./mvnw.cmd -Dtest=OfficeRecommendationTest#gamingAndCreationReturnScoredPlansWithDedicatedGraphics test` 复现游戏及创作请求返回 400。接入策略后，接口和规则首轮专项 7 项通过。
- 首轮 `./scripts/test.ps1` 的后端 214 项及前端构建通过。Windows 的原输出 JAR 被占用，打包阶段无法重命名该文件。
- 2026-11-18 最终运行沿用 `scripts/test.ps1` 的流程和环境，临时将 Maven 输出改到 `.local/task22-target`，浏览器端口用 18084。后端 216/216、Vue 类型检查、Vite 构建、同源 JAR 打包、Edge 127/127 均通过，临时管理员和浏览器服务已清理。日志在 `.local/task22-final.log`。

## 审查

规范审查后共享可信 SSD 容量读取，显式表达失败证据优先级，由同一服务端权重生成计算和展示，并标记任务 21 文档的历史范围。规格审查后，手动 DIY 的报价接口在 F03 未通过时隐藏整机分，办公区分独显缺货和核显数据不足，同价方案在比较内存数量前比较整组商品 ID，并增补缺请求字段的接口验证。
