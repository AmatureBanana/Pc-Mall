# 演示商品目录扩展

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

## V39 电源、机箱和 CPU 散热器

V39 按 2023-11-26 起的三年窗口，每类补入 49 个近期发布的零售 SKU。迁移至 V39 时，这三类各有 50 件在售商品，全目录有 647 件在售。功率、颜色、随附风扇等明确区分的零售版本按 SKU 计数。售价和库存使用本地演示值，既有 SKU 和规格在升级时保留。

- 电源覆盖 550～1500 W，录入额定功率、电源板型和 ATX 版本，部分型号补录长度。 [CORSAIR RMx 2024](https://www.corsair.com/newsroom/press-release/corsair-updates-popular-rmx-series-low-noise-power-supplies-with-atx-3-1-compliance-and-native-atx-12v-2x6-connector) · [be quiet! 2026 款 1200 W](https://www.bequiet.com/en/press/17208) · [Seasonic CORE GX](https://seasonic.com/news/core-gx-atx-3-2024/) · [LIAN LI Edge Gold](https://lian-li.com/product/edge-gold/)
- 机箱录入支持的主板板型、显卡限长、散热器限高与电源限长。装机参数采用默认布局。厂商标示电源长度严格小于某一数值时，采用其下方的最大整数毫米值。 [LIAN LI LANCOOL 207](https://lian-li.com/product/lancool-207/) · [be quiet! Light Base 500](https://www.bequiet.com/en/press/17378) · [Fractal Design Era 2 布局](https://support.fractal-design.com/support/solutions/articles/4000210379-era-2-cpu-cooler-gpu-support) · [Phanteks Evolv X2](https://phanteks.com/product-release-evolv-x2/)
- CPU 散热器选用风冷型号，录入支持的插槽、完整高度和风扇直径。多种扇径的型号使用主风扇直径。 [ARCTIC Freezer 8](https://www.arctic.de/en/blog/efficient-cooling-in-a-small-space) · [Noctua NH-D15 G2](https://www.noctua.at/en/news/noctua-releases-its-nh-d15-g2-next-gen-flagship-model-cpu-cooler-and-nf-a14x25r-g2-fans) · [DeepCool AK G2 Digital NYX](https://global.deepcool.com/company/pressroom/newsrelease/2025/22354.shtml?id=PressRoom) · [Montech NX400](https://www.montechpc.com/news-nx400)

## V40 中国大陆渠道筛选

按 2026-11-26 核对的公开发布与销售渠道资料，对 16 个演示 SKU 执行下架。以具体型号和零售版本为单位，优先保留有大陆常规销售渠道的主流产品。已有商品记录、规格和历史订单引用保留。本次筛选依据为公开渠道资料，库存仍为演示值。

- 显卡下架 12 件，包括 9 件 RTX 5090 32GB、2 件非 D 版 RTX 4090，以及 RTX 5070 Founders Edition。中国区发布分别列出 RTX 5090 D 系列、RTX 4090 D 和多家 RTX 5070 合作厂商型号。5070 Founders Edition 有美国区单独的发售说明，本次未核实到该公版的大陆常规销售渠道。 [RTX 5090 中国区发布](https://www.nvidia.cn/geforce/news/rtx-5090-5080-out-now/) · [RTX 5090 D v2](https://www.nvidia.cn/geforce/graphics-cards/50-series/rtx-5090-d-v2/) · [RTX 4090 D](https://www.nvidia.cn/geforce/graphics-cards/40-series/rtx-4090-d/) · [RTX 5070 中国区发布](https://www.nvidia.cn/geforce/news/rtx-5070-out-now/) · [RTX 5070 美国区发布](https://www.nvidia.com/en-us/geforce/news/rtx-5070-out-now/)
- SSD 下架 Sabrent Rocket 5 的 2TB 和 4TB 版本。本次未核实到这两个型号的大陆常规零售渠道。 [产品页](https://sabrent.com/products/sb-rkt5-4tb) · [厂商购买渠道](https://sabrent.com/pages/where-to-buy)
- CPU 散热器下架镰刀 Mugen 6 和 Mugen 6 DBE。厂商在产品公告中列出美国销售渠道，另有关于中国大陆授权分销的公开声明报道，本次未核实到这两个型号的大陆授权渠道。 [厂商发布资料](https://www.scytheus.com/news-1/introducing-mugen-6%3A-the-ultimate-cpu-cooler-series) · [声明报道](https://www.ithome.com/0/751/456.htm)

迁移至 V40 后，初始演示目录有 631 件在售，其中显卡 136 件、SSD 115 件、CPU 散热器 48 件，电源和机箱各 50 件。

## V41 大陆常见装机品牌

V41 从演示目录下架 40 件德商德静界商品，原记录及规格保留。补入 49 个 SKU 后，初始目录共 640 件在售。导入的售价和库存为演示数据，同型号的颜色或配置版本按零售 SKU 区分。

| 分类 | 补入 | 在售 |
| --- | ---: | ---: |
| 电源 | 13 | 50 |
| 机箱 | 20 | 50 |
| CPU 散热器 | 9 | 50 |
| 内存 | 7 | 44 |

- 电源补入长城 10 款和九州风神 PN-M 3 款，按厂商型号记录额定功率与 ATX 版本。 [长城大陆购买渠道](https://www.greatwall.com.cn/10863.html) · [长城型号示例](https://www.gwpst.cn/product/detail/285.html) · [九州风神 PN750M](https://cn.deepcool.com/products/PowerSupplyUnits/powersupplyunits/PN750M-ATX3.1-Modular-Power-Supply/2023/17916.shtml)
- 机箱补入九州风神、追风者与君主科技。CH160 和 CH170 数显版的显卡限长以 ATX 电源布局为基准，SFX 和 SFX-L 布局的厂商限长记在对应参数备注内。 [九州风神中国区机箱目录](https://cn.deepcool.com/products/Cases/index.shtml) · [追风者 XT 系列](https://phanteks.com/product-release-xt-series/) · [君主科技 XR](https://www.montechpc.com/xr)
- CPU 散热器补入九州风神与利民，沿用支持插槽、完整高度与风扇直径三个字段。 [九州风神 AG400 G2](https://www.deepcool.com/products/Cooling/cpuaircoolers/AG400-G2-Superior-Performance-4-Heatpipe-Single-Tower-CPU-Cooler/2025/22515.shtml) · [利民 Peerless Assassin 120 MINI](https://www.thermalright.com/product/peerless-assassin-120-mini-black/)
- 内存保留现有金士顿，新增海盗船 VENGEANCE 3 个、科赋 DDR5 UDIMM 4 个单根料号。时序按同一标称速率录入，缺少确切来源的字段留空。 [海盗船单根料号](https://www.corsair.com/us/en/p/memory/cmk16gx5m1b5600c40/vengeance-16gb-1x16gb-ddr5-dram-5600mhz-c40-memory-kit-black-cmk16gx5m1b5600c40) · [科赋规格](https://www.klevv.com/kcn/products_details/memory/D5_UDIMM) · [科赋大陆购买渠道](https://klevv.com/kcn/support/where_to_buy.php)

## V43 下架只有估算分的演示商品

V43 下架 66 件在 V42 演示目录中只有估算分的在售商品，包括 13 件 CPU、31 件显卡、19 件内存和 3 件 SSD。初始演示目录剩 574 件在售。迁移按核对过的 SKU 执行，保留商品记录、库存和历史订单引用，也保留管理员已修改规格或已录入有效跑分参考的商品。

## V44 电源模组类型

V44 按型号为 63 件演示电源补录模组类型，其中 50 件在售。当前这些型号均为全模组。后续录入可选择全模组、半模组或直出线。卡片按「额定功率 · 模组类型 · 电源板型」显示，模组类型可在详情中查看来源。管理员已录入的模组类型保留。

- 海盗船的 RMe、RMx、RMx SHIFT、HXi、HXi SHIFT 及 SF 分别按产品型号和系列接线规格核对。 [海盗船系列对照](https://www.corsair.com/us/en/explorer/diy-builder/power-supply-units/explaining-the-corsair-psu-lineup/)
- 海韵 CORE GX 和 FOCUS GX、联力 EDGE GOLD、德商德静界 Pure Power 13 M、Power Zone 2 和 Dark Power 14 按厂商产品资料核对。 [海韵 CORE GX](https://seasonic.com/core-gx-atx-3-2024/) · [联力 EDGE GOLD](https://lian-li.com/product/edge-gold/) · [德商德静界 Power Zone 2 及 Pure Power 13 M](https://www.bequiet.com/en/press/17208) · [Dark Power 14](https://www.bequiet.com/en/press/34045)
- 长城按型号、金牌版本及厂商出线类型核对。九州风神 PN650M、PN750M、PN850M 按 PN-M 系列资料核对。 [长城 G7MWG](https://www.gwpst.cn/product/detail/250.html) · [长城 E7MG](https://www.gwpst.cn/product/detail/283.html) · [九州风神 PN-M 系列](https://www.youtube.com/watch?v=931bbbc3e4s)
