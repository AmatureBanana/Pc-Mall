# 任务 23 推荐到购物车的登录与复验联动

已核对需求报告 §5.2.12、§5.4.12、§5.5.12、§9.2 及追踪矩阵。原始 DOCX、XLSX 的 SHA-256 与实施规格一致。 [任务 23](../../.scratch/pc-mall/issues/23-recommendation-cart-handoff.md) · [实施规格](../../.scratch/pc-mall/spec.md)

办公、游戏、创作推荐均可转入 DIY 或发起加购。游客登录后恢复商品引用、内存数量、显卡选择和来源，并按当前报价、库存、上下架状态及完整 F03 结果重验。用户核对后主动确认。修改 DIY 配置后按通用规则复验，旧推荐评分和解释失效。

迁移 `V31` 保存配置组首次加入时的来源，旧组及旧幂等响应默认 `DIY`。购物车按真实 SKU 和数量去重。提交时在商品锁内比较各件确认单价和 F03 指纹，并检查当前库存与核心兼容性。 [配置组接口与迁移契约](../configuration-cart.md)

| 原验收编号 | 验证 |
| --- | --- |
| F04-T23、F02-T18 | 浏览器将三个场景的真实商品导入 DIY，核对出处及配置引用，并验证手动修改后旧评分与解释失效 |
| F04-T24、F02-T13、F05-T24、SYS-T05 | 三个场景从推荐进入统一登录，再核对恢复结果及显式确认请求。补充验证登录页刷新、会话失效和重新登录 |
| F04-T25、SYS-T11 | 隔离 MySQL 接口用例验证总价相同但各件单价变动、参数变动、缺货与下架。浏览器验证登录期间的库存变动、核对期间刷新改价和提交时再次改价 |
| F04-T26、SYS-T06 | 核对确认前的空车、组内原 SKU 和数量、来源、重复点击与接口重试。通过公开库存查询及测试库预占记录验证零库存预占 |

## 执行记录

- 用 `./mvnw.cmd -Dtest=ConfigurationCartTest#recommendationRequiresTheCurrentQuoteAndKeepsItsOriginWithoutReservingStock test` 验证旧接口拒绝新的来源字段。用旧构建运行 `npm.cmd test -- recommendation-cart.spec.ts --grep 办公`，确认推荐结果缺少加购入口。
- 首轮浏览器专项 40/42 通过。办公方案刷新登录页后遇到并发 CSRF 初始化，收敛为单次恢复重验。另一处失败来自测试对首页 URL 的断言，改为检查推荐入口可见。
- 配置组接口专项 12/12、推荐加购与配置组浏览器专项 9/9 通过。
- 2026-11-18 执行 `./scripts/test.ps1 -BrowserPort 18086`。后端 218/218、Vue 类型检查、Vite 构建、同源 JAR 打包和 Edge 133/133 均通过。完整日志位于 `.local/task23-full.log`。Flyway 在接口专项中将隔离测试库从 V30 升至 V31。

## 审查

- 规范审查复核了旧幂等快照、账户会话失效、逐件报价比对、恢复请求及接口文档。
- 规格审查补齐了同总价的逐件变价、来源与确认条件解耦、登录期间库存变化及确认期间后台刷新后的变动提示。

测试库使用 `pc_mall_test`。迁移和交互沿用仓库依赖。 [接口用例](../../backend/src/test/java/cn/zhuangjimao/cart/ConfigurationCartTest.java) · [浏览器用例](../../frontend/tests/recommendation-cart.spec.ts)
