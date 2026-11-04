# 全商品性能标签验证记录

日期：2026-11-03（Asia/Shanghai）。用户追加需求记录为[任务26](../../.scratch/pc-mall/issues/26-product-performance-tags.md)，实施基点 `dccc8c363fcc36bb452060cf03e1927e91106d25`（Task-08），分支Test。沿用implement、已确认的TDD公开HTTP/浏览器/确定性规则边界；本地提交包含同会话前一轮已授权的后台深蓝标题与参考分样式，保留用户原有 `.idea/jsLibraryMappings.xml`。

## 确认范围与交付

用户明确选择：CPU/GPU展示有来源的渲染分、内存/SSD展示容量分，其他类别展示关键能力，不给所有类别新造0–100分；商城卡片、搜索、详情与后台列表/详情均显示。

交付服务端动态标签、公共显示组件、公开/后台接口附加标签、批量来源读取、评分变更后标签更新和乱序响应保护。标签从当前结构化参数与统一评分入口生成；缺分、撤销、身份失配或缺参会显示待核验，不解析摘要或保留旧有效标签。契约见[全商品性能标签](../product-performance-tags.md)。

## 产品资料与证据

两位独立上下文的 `gpt-6-luna` / `max` 子智能体分别调查AMD基准和各类别能力标签，主代理复核原始记录并生成新包。报告见[AMD](../research/product-performance-tags-amd.md)与[能力标签](../research/product-performance-tags-capabilities.md)。

- 新AMD包保留417份原样完整提交，CPU284份、GPU133份，与任务08初始458份合计875份。
- 仍使用同一官方CC0快照，固定4.3.0、Windows、build/launcher/script/scene校验值、30秒和单设备；AMD CPU固定16线程单插槽，GPU用HIP。
- 原始中位数253.065820239758 / 2441.353996099423，对应内部55.306582 / 54.413540；设备名后缀必须与实际核心数一致，不混入近似型号。
- AMD CPU的120W是Default TDP，未知峰值没有补值；显卡分仍注明型号级参考，非板卡变体实测。
- 主板标签不从支持组合数推断M.2数量，不补未结构化的PCIe速度；机箱、散热器标签保留标准净空与完整尺寸含义。
- 原v1包和V15保持原字节，新包与V16独立，已有管理员参考不被初始化覆盖。

两条离线命令均通过：`python scripts/performance-data.py --verify`、`python scripts/performance-amd-data.py --verify`。逐场景计分、唯一UUID、中位数、原始内容摘要、LICENSE及生成SQL均校验；另对Git暂存区产物逐一计算SHA-256，与两个manifest全部一致。

## 开发循环

所有持久化与浏览器验证使用真实独立pc_mall_test及其专用账户；SQL仅用于测试夹具、故障注入与清理，没有用演示库或内存库替代。资料搜寻不修改业务代码，主代理完成实现与测试。

| 切片 | 红灯/发现 | 绿灯 |
| --- | --- | --- |
| 全商品接口标签 | ProductPerformanceTagsTest读取performanceTags失败；product-tags-red-api.log | 13件正常商品都有分类标签，列表/详情一致；能力字段和来源验证通过 |
| AMD精确名称 | 8-Core Processor后缀被拒，预期200实际400；product-tags-red-amd-alias.log | 正确后缀通过，错误核心数和7800X近似型号拒绝 |
| 参数与参考更新 | 参数650W改850W、缺参数、参考撤销 | 公共列表/详情与后台同读新标签，缺参/撤销变UNVERIFIED；product-tags-api-boundaries.log共13/13 |
| 审计故障夹具 | 增加用例后，旧actor级CHECK碰到其他用例既有PERFORMANCE审计 | 限定到当前PRODUCT与id，恢复隔离故障注入，不依赖方法顺序 |
| 所有页面展示 | 浏览器预期13个标签列表、实际0；product-tags-red-browser.log | 新卡片/搜索/详情/后台展示1/1，后续与评分维护专项共2/2 |
| 乱序标签覆盖 | 延迟评分GET，参数14核改15核并保存，旧响应释放后错误回14；product-tags-red-refresh-race.log | 独立请求代数与商品revision保护；参数更新和连续有效→撤销两种延迟场景均通过，product-tags-green-refresh-race.log共2/2 |
| 完整回归数据库断连 | 新列表事务抛CannotCreateTransactionException，旧故障用例预期503实际500；product-tags-full-tests.log | 显式复用既有503处理器，其他异常仍500；product-tags-green-unavailable.log为3/3 |

截图已查看：标签布局与单位可读；保存[商城截图](product-performance-tags-storefront.png)及[后台截图](product-performance-tags-administration.png)。旧任务截图被回归重绘后恢复，不混入本次提交。

## 最终验收

修复后重新执行 `./scripts/test.ps1`，日志 `.local/product-tags-final-tests.log`，退出码0：

| 检查 | 结果 |
| --- | --- |
| 真实MySQL后端全套 | 62/62，失败0、错误0、跳过0 |
| Vue/TypeScript、Vite、同源JAR | 全部通过 |
| Edge浏览器全套 | 25/25，43.0秒 |
| 两个原始数据包与Git字节校验 | 全部通过 |
| Standards / Spec审查与复查 | 各0项剩余问题，见[审查记录](product-performance-tags-review.md) |

初次全套在数据库断连用例失败，已如实记录并修复，不以失败轮次冒充最终成功。标签不是整机兼容或实际运行验证，冻结推荐/兼容引擎的后续任务范围保持不变。
