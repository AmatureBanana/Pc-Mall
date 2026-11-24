# 500 件演示商品目录

V32 为默认演示商城补充 487 个 SKU。基础目录的 13 件在售商品和 1 件下架商品保留原有 ID、状态、库存和价格。500 件以 `/api/products` 返回的前台可见商品为准，已下架商品单独留存。迁移根据执行时的在售数量计算补充上限，已有 SKU 或同类同名、同型号记录会被跳过。`test` 配置仍使用小目录，供依赖固定商品的回归测试使用。

| 分类 | 新增 | 前台总数 |
| --- | ---: | ---: |
| CPU | 60 | 63 |
| 显卡 | 146 | 148 |
| SSD | 115 | 117 |
| 主板 | 130 | 132 |
| 内存 | 36 | 37 |
| 电源、机箱、CPU 散热器 | 0 | 3 |
| 合计 | 487 | 500 |

## 筛选与来源

- 五年范围从 2021-11-18 起。CPU、显卡和主板按产品系列或芯片组上市时间筛选，例如 Ryzen 7000 及更新系列、Intel 酷睿 13/14 代及 Ultra 200S、GeForce RTX 40/50、Radeon RX 7000/9000，以及相应的新主板芯片组。[AMD Ryzen 7000 发布资料](https://www.amd.com/en/newsroom/press-releases/2022-8-29-amd-launches-ryzen-7000-series-desktop-processors-.html) · [Intel 桌面处理器上市时间](https://www.intel.com/content/www/us/en/support/articles/000099655/processors.html) · [NVIDIA RTX 40 发布资料](https://nvidianews.nvidia.com/news/nvidia-delivers-quantum-leap-in-performance-introduces-new-era-of-neural-rendering-with-geforce-rtx-40-series) · [NVIDIA RTX 50 发布资料](https://nvidianews.nvidia.com/news/nvidia-blackwell-geforce-rtx-50-series-opens-new-world-of-ai-computer-graphics) · [AMD Radeon RX 7000 发布资料](https://www.amd.com/en/newsroom/press-releases/2022-11-3-amd-unveils-world-s-most-advanced-gaming-graphics-.html) · [AMD Radeon RX 9000 发布资料](https://ir.amd.com/news-events/press-releases/detail/1238/amd-unveils-next-generation-amd-rdna-4-architecture-with-the-launch-of-amd-radeon-rx-9000-series-graphics-cards)
- 硬盘沿用商城的 SSD 分类，选近期消费级 NVMe 系列或 PCIe 5.0 型号。内存沿用单根销售规则，选近期 DDR5 产品系列和规格。来源快照提供型号和规格，上市时间采用系列或平台口径。[三星 990 PRO](https://news.samsung.com/us/samsung-990-pro-series-ssds-pre-order-november-1st/) · [三星 9100 PRO](https://news.samsung.com/us/samsung-announces-9100-pro-series-ssds-with-breakthrough-pcie-5-0-performance/) · [金士顿 DDR5 发布记录](https://www.kingston.com/unitedkingdom/en/company/press/year/2022/language/english) · [威刚 LANCER BLADE 发布资料](https://staging.adata.com/bd/news/1073/)
- 商品和参数保留固定版本的来源链接。导入数据取自 Doc Oliver 的 PC Part Dataset 2025-07-23 快照，按商城字段筛选、去重和规范化。导入使用原有品类插图和本地 CNY 演示标价。[数据快照](https://github.com/docyx/pc-part-dataset/tree/c52a04ca9465c83997ed335f7767b09a2005dd26/data/json) · [MIT 许可](catalog-expansion-LICENSE.txt)
- 新增 SSD 和内存使用演示库存。新增 CPU、显卡和主板的库存为 0，详情只列已有来源的规格。CPU 峰值功耗、显卡板卡功耗、主板 M.2 有效安装组合等资料待补充，具体装机结论需要按型号复核。

## 参数补录与评分

V33 至 V36 在同一批 SKU 上补充资料。V33 使用 [Blender 官方 Open Data CC0 快照](https://opendata.blender.org/download/)。每条提交按固定 Blender 4.3.0、Windows 64 位、单设备和完整三场景筛选。CPU 同一型号只取一个线程配置，选择完整样本最多的配置。显卡按明确的 GPU 芯片匹配 OptiX、HIP 或 oneAPI，评分属于芯片型号级参考。74 个设备的 7,540 条原始提交保存在 `data/catalog-rendering-v1`。离线核验运行 `python scripts/catalog-rendering.py`。

V34 和 V36 按品牌、型号和容量补录 SSD 的标称顺序读写，以及单根内存同一档位的 tRCD 和 tRP。评分继续由 [容量优先策略](capacity-priority-scores.md) 根据带来源的当前参数计算。V34、V35 也补录了 5 款 Intel CPU 的最大睿频功耗、22 款 AMD CPU 的线程数和适用的核显规格、1 件显卡的板卡功耗及 17 件主板的安装组合。具体来源保存在各商品的分类参数中。

| 新增品类 | 商品数 | 已给出有效数值评分 | 当前缺口 |
| --- | ---: | ---: | --- |
| CPU | 60 | 47 | 13 款缺足够的合格 Blender 样本，仍有部分型号待补峰值功耗 |
| 显卡 | 146 | 115 | RX 9060 XT、RX 9070、RX 9070 XT 的 31 件板卡缺合格样本，部分板卡的装机参数待核验 |
| SSD | 115 | 110 | 4 件待确认对应规格的读写，1 件 4096 GB 商品已补录读写且容量超出当前评分档位 |
| 内存 | 36 | 20 | 16 件待核对单根料号及完整时序 |
| 主板 | 130 | — | 按既有规则展示能力标签，17 件已补录 M.2 安装组合，113 件仍待逐板核对 |
