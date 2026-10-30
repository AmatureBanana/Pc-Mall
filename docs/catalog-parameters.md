# 商品参数数据契约与核验

任务 02，数据版本 `catalog-parameters-v1`，核验日期 2026-10-30。

商品基础数据保留在 `products`；描述和分类参数放在一对一的 `product_specifications`。V4 按既有销售 SKU 关联真实商品 ID，不重建商品、不改 SKU 或类别、不改演示价和库存。仅将 RX 7800 XT 与 RM750e 的显示型号明确到 AMD 公版及 CP-9020295-NA，避免混用其他版本。

`parameters` 是 JSON 数组。每项包含稳定的 `code`、中文 `label`、有类型的 `value`、`unit`、逐字段 `sourceUrl` 和 `note`。集合以无重复的 JSON 数组记录，数值不混入单位字符串；未知值使用 JSON null，界面显示“未核验”。后续 DIY 与规则服务应读取同一商品来源及这些参数，不解析展示摘要，不把 null、空集合或缺失字段当作已通过。当前接口不提供兼容结论或性能评分。

| 商品 / 厂商来源 | 逐字段核验结果（参数 code → 值） |
| --- | --- |
| [Intel i5-14600KF](https://www.intel.com/content/www/us/en/products/sku/236778/intel-core-i5-processor-14600kf-24m-cache-up-to-5-30-ghz/specifications.html) | socket=LGA1700（由 FCLGA1700 规范化）；cores=14；threads=20；basePower=125 W；peakPowerEstimate=181 W，使用 Maximum Turbo Power；memoryGenerations=[DDR4, DDR5] |
| [Intel 无核显清单](https://www.intel.com/content/www/us/en/support/articles/000027144/processors/intel-core-processors.html) | i5-14600KF integratedGraphics=false |
| [AMD Ryzen 7 7800X3D](https://www.amd.com/en/products/processors/desktops/ryzen/7000-series/amd-ryzen-7-7800x3d.html) | socket=AM5；cores=8；threads=16；integratedGraphics=true；tdp=120 W；memoryGenerations=[DDR5]。peakPowerEstimate=null：该来源未核验峰值估算，不能以 TDP 替代 |
| [ASUS TUF GAMING B760M-PLUS WIFI 技术规格](https://www.asus.com/us/motherboards-components/motherboards/tuf-gaming/tuf-gaming-b760m-plus-wifi/techspec/) | socket=LGA1700；formFactor=Micro-ATX；memoryGenerations=[DDR5]；memorySlots=4；displayOutputs=[HDMI, DisplayPort]；gpuInterfaces=[PCIe x16]（物理安装规格）；ssdFormFactors=[M.2 2242, M.2 2260, M.2 2280]；ssdInterfaces=[PCIe]；ssdProtocols=[NVMe]；m2Slots=2。不是 D4 版本 |
| [MSI MAG B650M MORTAR WIFI](https://www.msi.com/Motherboard/MAG-B650M-MORTAR-WIFI/Specification) | socket=AM5；formFactor=Micro-ATX；memoryGenerations=[DDR5]；memorySlots=4；displayOutputs=[HDMI, DisplayPort]；gpuInterfaces=[PCIe x16]；ssdFormFactors=[M.2 2260, M.2 2280]；ssdInterfaces=[PCIe]；ssdProtocols=[NVMe]；m2Slots=2。物理 PCIe x16 与不同 CPU 下电气通道数分开理解 |
| [NVIDIA RTX 4070 系列规格](https://www.nvidia.com/en-us/geforce/graphics-cards/40-series/rtx-4070-family/) | 仅取 RTX 4070 SUPER 列：boardVariant=Founders Edition；vram=12 GB；installationInterface=PCIe x16；pcieGeneration=4.0；boardPowerEstimate=220 W（TGP）；length=244 mm；displayOutputs=[HDMI, DisplayPort] |
| [AMD RX 7800 XT](https://www.amd.com/en/products/graphics/desktops/radeon/7000-series/amd-radeon-rx-7800-xt.html)及[官方系列对照表](https://www.amd.com/content/dam/amd/en/documents/partner-hub/radeon/amd-radeon-rx-7000-series-quick-reference-competitive.pdf) | boardVariant=AMD reference；vram=16 GB；installationInterface=PCIe x16；pcieGeneration=4.0；boardPowerEstimate=263 W（TBP）；length=267 mm；displayOutputs=[HDMI, DisplayPort]。不适用于非公版 |
| [Kingston KF560C36BBEK2-32 数据表 4810622A](https://www.kingston.com/datasheets/KF560C36BBEK2-32.pdf) | generation=DDR5；totalCapacity=32 GB；stickCount=2 条；capacityPerStick=16 GB；dataRate=6000 MT/s（EXPO/XMP）；jedecDataRate=4800 MT/s。销售单位为完整双条套装 |
| [DeepCool AK400](https://www.deepcool.com/products/Cooling/cpuaircoolers/AK400-Performance-CPU-Cooler-1700-AM5/2021/15222.shtml) | supportedSockets=[LGA1700, LGA1200, LGA1151, LGA1150, LGA1155, AM5, AM4]；height=155 mm（完整产品，不用裸散热片 152 mm）；fanDiameter=120 mm |
| [Samsung 990 PRO Rev.1.0](https://download.semiconductor.samsung.com/resources/data-sheet/Samsung_NVMe_SSD_990_PRO_Datasheet_Rev.1.0_10119997003518.pdf) | MZ-V9P1T0BW 无散热片：capacity=1000 GB；originalCapacity=1 TB；formFactor=M.2 2280；interface=PCIe；protocol=NVMe；pcieGeneration=4.0；lanes=4 |
| [Sandisk / WD_BLACK SN850X](https://www.sandisk.com/products/ssd/internal-ssd/wd-black-sn850x-nvme-ssd?sku=WDS100T2X0E) | 只采用 1TB 无散热片 WDS100T2X0E：capacity=1000 GB；originalCapacity=1 TB；formFactor=M.2 2280；interface=PCIe；protocol=NVMe；pcieGeneration=4.0；lanes=4。原数据表旧链接已失效，改用官方型号页面 |
| [Corsair RM750e CP-9020295-NA](https://www.corsair.com/us/en/p/psu/cp-9020295-na/rme-series-rm750e-fully-modular-low-noise-atx-power-supply-cp-9020295-na) | manufacturerPartNumber=CP-9020295-NA；ratedPower=750 W；formFactor=ATX；atxVersion=3.1；length=140 mm。不使用旧款认证资料 |
| [DeepCool CC560](https://www.deepcool.com/products/Cases/CC560-Mid-Tower-ATX-PC-Case/2021/15216.shtml) | supportedMotherboardFormFactors=[ATX, Micro-ATX, Mini-ITX]；maxGpuLength=370 mm；maxCoolerHeight=163 mm；maxPsuLength=170 mm。前置冷排等额外占用仍需核验 |

SSD 按十进制 GB，1 TB=1000 GB；原始 TB 数值另存，不混用 GiB/TiB。内存采用厂商标称 GB；功率用 W，长度用 mm，数据速率用 MT/s。板型将 mATX/M-ATX 统一为 Micro-ATX。主板 SSD 三个支持集合仅描述两个 M.2 插槽，且当前都是 PCIe/NVMe 的对应组合；不描述 SATA 端口，后续增加不同插槽组合时应逐插槽建模，不能将无关集合任意做笛卡尔积。主板视频输出仍需有效 CPU 核显，不能只因存在 HDMI 端口就判定核显路径成立。

C01～C10 对应数据为：socket；generation/stickCount/memorySlots；supportedSockets；formFactor/supportedMotherboardFormFactors；SSD 规格/接口/协议；显卡安装接口；核显及视频输出集合；峰值与总板功耗估算/额定功率；显卡长度/机箱有效限长；散热器高度/机箱有效限高。AMD 峰值未知应由后续数据准备核验后补充，规则在此前应给 UNKNOWN；本任务不宣称任一整机已通过核心检查。

以上为按型号逐项整理的事实数据和本项目原创描述，没有复制厂商图片、长篇文案、外部商品数据库或第三方业务代码。厂商资料保留其原有权利，仅以链接归因，不主张其为开放许可；未采用 pc-builder-parts 数据，因而不引入其 CC BY 4.0 数据包。原有 12 个品类图仍为项目原创 SVG。所有售价、库存仍是数据库中的 CNY 演示值。
