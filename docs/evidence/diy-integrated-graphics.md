# DIY CPU 核显候选验证

日期：2026-11-08（北京时间）。任务：[28](../../.scratch/pc-mall/issues/28-diy-integrated-graphics.md)。

## 交付

DIY 显卡类别统一显示「显卡」，有已核验核显的当前 CPU 在显卡候选第一行显示免费附属选项。品牌、搜索和排序不改变核显的置顶位置。展开规格显示有来源的 CPU igpu 参数；没有可比核显渲染数据时显示待核验。

核显与独显互换，移除、替换 CPU 和保存/恢复都保持一致。显式核显选择仅保存绑定 CPU 的客户端引用，既不生成商品/销售SKU，也不传给报价或兼容接口。报价仍只合计真实商品，原有服务端兼容规则继续核验 CPU/主板输出及功耗。

按用户要求使用 gpt-6-luna、推理 max 独立子智能体核查官方规格并实现 CPU 参数 schema、V18 和来源文档。只读核验本机演示与隔离测试库在售 CPU 都为 i5-14600K、i5-14600KF、Ryzen 7 7800X3D；核显分别覆盖 UHD Graphics 770 和 Radeon Graphics，KF 无核显。原参数保留，迁移不插入商品。详见[参数及来源](../integrated-graphics.md)。

## 验证

- `./scripts/test.ps1`：后端真实 MySQL 测试 123/123、Edge 浏览器全套 48/48、TypeScript、Vite 和同源 JAR 构建通过。日志 `.local/igpu-full-tests.log`（Git 忽略）。
- 新增真实 HTTP 回归确认两款 CPU 的核显型号、正值频率及来源，无核显 CPU 不带 igpu 字段，浏览商品中没有独立核显商品。
- 新增三项浏览器回归覆盖置顶、品牌筛选/排序、独显与核显互换及总价、刷新恢复、移除、换无核显 CPU、详情故障保留可移除引用。
- 桌面截图核对后发现手机展开参数列过窄，修复为手机单列参数/值布局。重新构建并执行 DIY 与核显回归 13/13（`.local/igpu-mobile-regression.log`）。
- 最后在真实演示商城使用同源 JAR 应用 V18，并再次执行核显专项 3/3（包含新增手机参数值列宽大于 100px、页面无横向溢出断言）；桌面/手机截图人工查看通过。临时预览服务完成后关闭。演示库与测试库均已迁移到 V18。
- `git diff --check` 通过。无新增依赖，不创建 Git 提交，保留原有任务11工作区修改。

## 截图

- [桌面候选卡片](diy-integrated-graphics.png)
- [展开规格](diy-integrated-graphics-specs.png)
- [390px 手机布局](diy-integrated-graphics-mobile.png)
