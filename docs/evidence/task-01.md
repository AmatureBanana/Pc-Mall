# 任务 01 验证记录

日期：2026-10-30（Asia/Shanghai）。任务：[01 数据库商品展示与可运行工程](../../.scratch/pc-mall/issues/01-runnable-product-browser.md)。

## 范围

原验收关联 `SYS-T01` 的商品展示最小路径。此票不等于完成 SYS-T01 的后续分类搜索详情或全部 P0 验收。测试边界、代码审查基准 `142d42e`、创建项目专用本地 MySQL 实例均已获用户确认。

## 首轮已执行证据

| 检查 | 命令 / 方法 | 结果 |
| --- | --- | --- |
| 环境 | 直接执行 Java、Node、npm、mysqld 版本命令 | JDK21.0.10、Node24.22.1、npm11.19.1、MySQL8.0.45 |
| 专用数据库 | `scripts/local-db.ps1` | 新建项目数据目录；只监听127.0.0.1:33307；两个独立schema及账户 |
| 数据库权限隔离 | 用测试账户只读查询演示库 `COUNT(*)` | MySQL1142拒绝，退出码1（预期结果） |
| API 红灯 | `-Dtest=ProductBrowserTest test`，实现前空接口 | 明确失败：expected12 but was0 |
| API 绿灯 | 同一测试，真实MySQL与HTTP | 1/1通过；字段验证；重启后结果不重复 |
| 配置保护红灯 | `-Dtest=TestDatabaseSafetyTest test`，保护实现前 | 2项失败，尚不能给出保护错误 |
| 配置保护绿灯 | 同一测试，EnvironmentPostProcessor实现后 | 2/2通过；缺少配置及演示库覆盖在连接前拒绝 |
| 数据库不可用 | `-Dtest=CatalogFailureTest test` | 1/1通过；真实连接失败返回503及CATALOG_UNAVAILABLE，无成功data |
| 前端检查 | `npm run typecheck`、`npm run build` | 通过，Vue/TypeScript生产构建成功 |
| 同源包 | `mvn -Pdemo -DskipTests package`及`java -jar` | JAR提供页面HTTP200、API返回12件数据库商品 |
| 浏览器首轮 | `npm test` | 4/4通过，Edge桌面1586×992与手机390×844 |
| Wrapper | `./mvnw.cmd -version` | 使用锁定的Maven3.9.11成功启动 |

浏览器正常展示与手机路径读取真实商品接口，没有模拟API。失败重试和空响应两项在浏览器网络边界明确注入503/空JSON；错误重试恢复后再次读取真实后端。MySQL不可用的真实后端证据由独立接口测试提供。

桌面与手机截图分别为 `task-01-storefront.png`、`task-01-mobile.png`。已人工查看，包含名称、类别、价格和库存；图像标注为原创品类示意图。

## 最终验证

最终实现提交：`ff41703`（初始实现 `39dc65f`），审查基准：`142d42e`。以下验证针对同一最终源码；后续提交仅补全证据、截图、说明及任务状态。

| 检查 | 最终执行结果 |
| --- | --- |
| `scripts/package.ps1` | 通过：npm ci、Vue/TypeScript检查、Vite生产构建、Maven Wrapper 3.9.11同源JAR打包；2026-10-30 14:47:29完成 |
| `scripts/test.ps1` 后端全套 | 6项通过，0失败、0错误、0跳过：真实接口与重复迁移1项、MySQL连接失败503共1项、缺失配置/演示库覆盖/Flyway覆盖/Hikari覆盖4项；14:48:17完成 |
| `scripts/test.ps1` 前端 | TypeScript检查通过；Edge浏览器4项全部通过（5.4秒） |
| 数据库重启 | `local-db.ps1 stop` 后 `start` 成功；保留数据，不重新初始化 |
| 迁移历史 | 演示库V1、V2、V3的success均为1，products共12条；接口测试重启后仍12条 |
| 半成品保护 | 将相同脚本复制到项目内隔离目录，仅放置无凭据env夹具而不放就绪标记：在启动mysqld前明确拒绝 |
| 同源HTTP与权限 | `/`、`/api/products`、`/images/motherboard.svg`均200；`/api/admin/products`为401；无CSRF的`POST /api/products`为403 |
| 两轴审查 | 初轮发现的隔离覆盖与初始化失败路径均已修复；品牌重复提取为StoreBrand；最终Standards与Spec均0项剩余发现，详见task-01-review.md |

浏览器：Microsoft Edge `156.0.4314.50`，桌面1586×992、手机390×844，截图已逐张查看。打包产物 `target/pc-mall-0.1.0.jar` 的SHA-256为 `E2FDC3E91FC68D39AA0AFD53B0FEA73ED6224E6569C2F9311435B810CE8FD227`（产物不提交，按README复现）。本机完整原始运行日志在被忽略的`.local/final-package.log`、`.local/final-tests.log`、`.local/final-demo.log`，没有把凭据或数据库目录提交到Git。

## 用户实际内置浏览器的补充复现与恢复

独立Edge全套测试通过后，用户提供的内置浏览器截图仍显示“商品接口返回的数据不完整”。因此撤回仅凭独立Edge就宣布当前内置页面通过的结论，并检查实际浏览器。

- 在内置浏览器访问相同`http://127.0.0.1:8080/`，明确复现：`article`数量0、旧错误可见；DOM脚本为`/assets/index-D13eTMxd.js`。
- 同一内置浏览器打开`/api/products`时返回真实12件商品，与本机HTTP检查完全一致；当前服务HTML引用`/assets/index-DBxe4L5R.js`。问题是旧页面资源与当前接口不匹配，而非数据库空列表。
- 对实际内置页面执行`tab.reload()`后，DOM脚本变为当前版本，`article`数量12、旧错误消失，名称/价格/库存可见。随后再次执行同一检查得到相同通过结果，并保留已验证的内置标签页供用户查看。
- 实际内置浏览器截图为`task-01-in-app.jpg`，与独立Edge截图分开记录。此问题不改业务数据或放宽响应校验。

可重跑的实际浏览器检查（在已有内置标签页上执行）为：

```javascript
await tab.reload();
const result = await tab.playwright.evaluate(() => ({
  cards: document.querySelectorAll('article').length,
  error: document.body.innerText.includes('商品接口返回的数据不完整'),
  script: Array.from(document.scripts).map(s => s.src)
}));
if (result.cards !== 12 || result.error) throw new Error('Product browser verification failed');
```

该缓存状态存在于长期打开的内置浏览器，新的Edge测试上下文未覆盖它；不以纯函数或新上下文测试冒充此场景回归。验收补充为构建/重启后刷新**实际交付的内置标签页**并检查DOM与截图，README已补充这一操作。

本任务范围内无待执行、失败或阻塞项；不宣称任务02、完整SYS-T01或整套P0已经验收。
