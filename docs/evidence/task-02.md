# 任务 02 验证记录

日期：2026-10-30（Asia/Shanghai）。任务：[02 商城分类、搜索与商品详情](../../.scratch/pc-mall/issues/02-product-catalog.md)。审查基准由用户确认为 `1d56951`，使用母规格已确认的公开接口与浏览器测试边界。仅覆盖 SYS-T01 中分类、搜索、详情及其异常反馈，不代表全部系统验收。

## TDD 与单文件验证

| 检查 | 命令 / 结果 |
| --- | --- |
| 后端分类红灯 | `./mvnw.cmd -Dtest=ProductCatalogTest test`：筛选 CPU 却包含 MOTHERBOARD，失败 |
| 后端分类绿灯 | 同文件 1/1 通过，八类均从真实 MySQL 查询 |
| 后端搜索红灯 | `-Dtest=ProductCatalogTest#keywordSearchCombinesWithCategoryAndTreatsSqlWildcardsLiterally`：预期0实际2，搜索被忽略 |
| 后端搜索绿灯 | 同文件2/2通过，组合筛选、无匹配、字面百分号及注入文本 |
| 后端详情红灯 | `-Dtest=ProductCatalogTest#detailsKeepSalesIdentityAndSeparateKitCapacityFromStickCount`：预期200实际401，详情入口不存在 |
| 后端详情绿灯 | V4迁移后同文件3/3通过；最终扩充为6/6，通过八类、12件详情、32GB/2条、SSD1000GB/原始TB、集合、无效输入、不存在、当前数据与下架反馈 |
| 浏览器查询红灯 | `npm test -- tests/product-catalog.spec.ts --timeout=10000`：前端没有发送类别查询请求，等待响应超时 |
| 浏览器查询绿灯 | 同路径通过，真实服务端查询、关键词与空结果清除 |
| 浏览器详情红灯 | `--grep 'guest opens' --timeout=10000`：没有商品详情链接 |
| 浏览器详情绿灯 | 详情、身份、容量与条数、直接链接刷新通过 |
| 浏览器新增全文件 | `npm test -- tests/product-catalog.spec.ts`：8/8通过，额外覆盖八类、无效ID、下架反馈、失败后清空旧详情、重试、旧查询晚到 |
| TypeScript | 多轮 `npm run typecheck` 均通过 |
| 视觉核验 | 人工查看真实页面截图，发现遗漏的 Element Plus dialog CSS；补全后复验弹窗位于视口、表格清楚可读，截图为 task-02-memory-detail.png |

真实 MySQL 测试仅连接 `pc_mall_test`，沿用启动前 URL/账户/覆盖保护。数据变更场景的 JDBC 仅作为测试夹具刺激，观察结果全部经公开 HTTP；finally 还原原价、库存及状态。没有增加未鉴权测试写接口，没有使用内存数据库。正常浏览器路径读取演示库；503、下架和晚响应夹具在浏览器网络边界注入，不冒充真实数据库证据。后端测试单独覆盖真实上下架状态与价格库存变化。

参数来源见 [核验记录](../catalog-parameters.md)。AMD 7800X3D 峰值估算为明确未知，不以120W TDP填充；后续数据准备与兼容检查须保留UNKNOWN语义。

## 最终验证

最终业务源码提交 `a9bccc4`（初始实现 `239c40e`）；随后只调整浏览器等待断言、截图、文档及任务状态。

| 检查 | 最终执行结果 |
| --- | --- |
| `scripts/package.ps1` | 通过：npm ci、TypeScript、Vite与同源JAR构建，16:36:50完成 |
| 审查后重新构建 | `frontend`执行`npm run build`，根目录执行`./mvnw.cmd -Pdemo -DskipTests package`：通过，16:38:20完成；当前演示进程使用此产物 |
| 最终后端全套 | `scripts/test.ps1`：12项、0失败、0错误、0跳过，16:40:36完成。含6项商品目录、1项迁移/重启、1项真实连接失败503、4项测试库保护 |
| 最终前端全套 | 同脚本：Vue/TypeScript通过；Edge浏览器12/12通过，16.1秒（原有4项及任务02新增8项） |
| 同源HTTP抽查 | SSD分类返回2件；`/api/products/9`返回真实ID9、SKU `SAMSUNG-MZ-V9P1T0BW`、799.00、库存18及`catalog-parameters-v1` |
| 实际内置浏览器 | 同源首页显示12件；点击“内存”显示1件；小写型号关键词搜索后仍显示该内存；点击商品详情显示SKU、型号、899.00/库存20、32GB与2条；在`/#product/7`直接刷新后详情保留、数据正确，标签页已保留供用户查看 |
| 视觉 | 最终桌面截图`task-02-memory-detail.png`已人工查看；实际内置浏览器截图单独显示于本次会话工具结果，不将Edge截图冒充内置截图 |
| 两轴审查 | 两个全新上下文代理独立审查；初轮规范建议1项已修复，最终Standards=0、Spec=0，见[审查记录](task-02-review.md) |
| 差异检查 | `git diff --check`通过；原任务01截图由全套测试重写后还原，避免改动历史证据；用户原有`.idea`与`outputs/`未提交 |

第一次完整测试中后端12项与TypeScript已通过，浏览器11/12；失败为详情刷新后直接读取弹窗坐标，入场动画中间帧y=-3.40导致断言失败。改用Playwright自动等待`toBeInViewport()`，截图关闭动画，单文件回归通过后完整脚本重跑12/12。没有修改产品行为或放宽接口参数验证。首次失败日志与最终通过日志分别保存，不把首轮记成通过。

最终JAR SHA-256：`143C1232F84EECDFB7DAD720968558AC185741324CE7CD69E1F1EB3297B35A75`。产物与完整日志在被忽略的`target/`、`.local/task-02-*.log`中。最终构建日志为`task-02-reviewed-package.log`/`task-02-final-frontend.log`，测试为`task-02-final-tests-rerun.log`，启动为`task-02-reviewed-demo.log`；没有提交凭据、数据库文件或构建产物。

本任务范围内无未解决的失败或阻塞。AMD峰值估算仍明确未知，并非已验证可推荐的完整数据包；后续任务08负责可信推荐样本，不在本票伪造其验收。
