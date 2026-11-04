# 商品能力标签资料核查

核查日期：2026-11-03。范围是商城商品卡片、详情和后台可展示的型号规格标签；依据仓库已有逐字段记录与结构化种子，重点复核主板、内存、电源、机箱、散热器及 SSD 标签。此报告不声称整机已组装，也不验证 BIOS、超频、热表现或实际运行速度。

## 结论

这些值适合作为“关键规格 / 容量 / 安装空间”标签。它们描述商品的厂商规格或标称容量，不是跨品类的统一性能等级。建议将 CPU/GPU 的 Blender 参考分另行标为“性能参考分”，将内存和 SSD 的容量映射分标为“容量参考分”；这些分数采用本项目规则，不是厂商等级或整机实测，详见[性能数据契约](../performance-data.md)。

标签应从商品 `parameters` 中对应的 `code`、`value`、`unit` 和逐字段 `sourceUrl` 生成。仓库数据契约规定未知值为 JSON `null`，界面显示“未核验”；商品摘要不能作为参数替代。后台共享参数词汇定义了类别字段及必填规则，但来源 URL 的校验只确认 URL 格式，不会在线证明来源内容或验证实机事实，见 [HardwareParameters.java](../../backend/src/main/java/cn/zhuangjimao/catalog/HardwareParameters.java) 与 [商品参数数据契约](../catalog-parameters.md)。展示某个可选字段时，也应单独检查其值和来源；`missing()` 只把类别必填字段缺失纳入必填错误列表。

## 建议措辞与边界

| 商品 / 字段 | 可展示的标签措辞 | 核查结果与容易误导之处 | 第一方来源 / 仓库记录 |
| --- | --- | --- | --- |
| ASUS TUF GAMING B760M-PLUS WIFI：内存 | `DDR5 · 4 条 DIMM` | 型号为 DDR5 版；厂商列 4 个 DIMM 插槽。技术页另列最高数据率，带 `(OC)` 的频率及实际支持还受 CPU、内存条配置影响；当前结构化数据没有该商品的最高内存频率字段，因此不要从页面摘要或其他版本补出频率标签。 | [ASUS 技术规格](https://www.asus.com/us/motherboards-components/motherboards/tuf-gaming/tuf-gaming-b760m-plus-wifi/techspec/)；[逐字段记录](../catalog-parameters.md) |
| ASUS TUF GAMING B760M-PLUS WIFI：存储 | `2 个 M.2 插槽 · 2242/2260/2280 · PCIe / NVMe` | 厂商明确总共 2 个 M.2 插槽；两个插槽各支持 2242/2260/2280。V10 的 `ssdSlots` 记录的是允许的规格/接口/协议组合，不代表三种规格各有一个插槽。不要把支持尺寸集合长度显示为插槽数，也不要与 SATA 端口混为一谈。ASUS 页面确认 PCIe 4.0 ×4，但当前结构化主板参数没有 PCIe 代际/通道字段；只从参数动态生成时不要附加这一速度规格。 | [ASUS 技术规格](https://www.asus.com/us/motherboards-components/motherboards/tuf-gaming/tuf-gaming-b760m-plus-wifi/techspec/)；[V10 组合数据](../../backend/src/main/resources/db/migration/V10__explicit_motherboard_ssd_combinations.sql) |
| MSI MAG B650M MORTAR WIFI：内存 | `DDR5 · 4 条 DIMM` | 厂商列 4 个 DDR5 插槽。其频率表包含 OC 档位，并注明支持速度受 DIMM 数量与单双面配置影响；不应把页面最高 OC 频率当作默认值或已验证运行速度。 | [MSI 技术规格](https://www.msi.com/Motherboard/MAG-B650M-MORTAR-WIFI/Specification)；[逐字段记录](../catalog-parameters.md) |
| MSI MAG B650M MORTAR WIFI：存储 | `2 个 M.2 插槽 · 2260/2280 · PCIe / NVMe` | 厂商列 2 个 M.2 插槽，均支持 2260/2280 与 PCIe 4.0 ×4。需要保留 CPU 条件：MSI 说明搭配 Ryzen 8500/8300 时 M2_2 不可用；当前种子型号是 Ryzen 7 7800X3D，但此限制尚未写入参数 note，且当前结构化参数没有 PCIe 代际/通道字段。只从参数动态生成时不要附加速度规格，也不要把 2 个物理插槽数当作对每种 CPU 都可用的 SSD 数量。 | [MSI 技术规格](https://www.msi.com/Motherboard/MAG-B650M-MORTAR-WIFI/Specification)；[V10 组合数据](../../backend/src/main/resources/db/migration/V10__explicit_motherboard_ssd_combinations.sql) |
| Kingston KF560C36BBEK2-32：内存容量与速度 | `DDR5 · 32 GB（2 × 16 GB）`；`XMP/EXPO 档 6000 MT/s · JEDEC 默认 4800 MT/s` | 32 GB 是整套容量，2 条各 16 GB。6000 MT/s 是产品的 XMP/EXPO 配置档；数据表同时注明默认 JEDEC 4800 MT/s，并警告部分主板/系统配置可能无法按标称档位运行。因此不要单独写成“运行速度 6000”或保证整机达到该速度。 | [Kingston 型号数据表](https://www.kingston.com/datasheets/KF560C36BBEK2-32.pdf)；[逐字段记录](../catalog-parameters.md) |
| Corsair RM750e CP-9020295-NA：电源 | `750 W · ATX 3.1 · CP-9020295-NA` | 精确料号对应页面和规格列出 750 W continuous power / total power、ATX12V version 3.1。可用“额定/连续功率 750 W”，不要仅凭这个数值宣称某个 CPU/GPU 整机已适配或实测稳定。 | [Corsair 型号页与规格](https://www.corsair.com/us/en/p/psu/cp-9020295-na/rme-series-rm750e-fully-modular-low-noise-atx-power-supply-cp-9020295-na)；[逐字段记录](../catalog-parameters.md) |
| DeepCool CC560：机箱净空 | `厂商标注显卡限长 370 mm · CPU 散热器限高 163 mm` | CC560 产品页将 370 mm 与 163 mm 列为 compatibility limit，并列出前置 3 × 120 mm 预装风扇。370 mm 可作为标准规格标签；页面没有量化安装前置冷排或其他占位部件后会减少多少净空，不能把它说成所有配置下均可用的保证。仓库现有字段备注也限定“标准安装条件”，提醒前置冷排需另核。 | [DeepCool CC560 规格](https://www.deepcool.com/products/Cases/CC560-Mid-Tower-ATX-PC-Case/2021/15216.shtml)；[V4 字段及备注](../../backend/src/main/resources/db/migration/V4__catalog_specifications.sql) |
| DeepCool AK400：散热器 | `完整高度 155 mm · 支持 LGA1700 / AM5` | 厂商产品尺寸为 127 × 97 × 155 mm；另单列散热片尺寸 120 × 45 × 152 mm。应显示装有风扇的完整产品高度 155 mm，不能误取裸塔 152 mm。LGA1700 和 AM5 是安装座支持信息，不代表具体 CPU 的散热余量或温度表现。 | [DeepCool AK400 规格](https://www.deepcool.com/products/Cooling/cpuaircoolers/AK400-Performance-CPU-Cooler-1700-AM5/2021/15222.shtml)；[逐字段记录](../catalog-parameters.md) |
| Samsung 990 PRO MZ-V9P1T0BW、WD_BLACK SN850X WDS100T2X0E：SSD 容量 | `标称 1 TB（十进制规范容量 1000 GB）· M.2 2280 · PCIe 4.0 ×4 · NVMe` | 结构化记录分别保存 `capacity=1000 GB` 与 `originalCapacity=1 TB`，可同时生成一致标签；不要改成 1024 GB，也不要把标称十进制容量说成操作系统显示的可用空间。两项厂商页面/数据表链接本轮浏览器抓取不可访问；数值仅沿用仓库已有逐字段核验记录，没有在本轮重新打开一手文档。 | [Samsung 官方数据表](https://download.semiconductor.samsung.com/resources/data-sheet/Samsung_NVMe_SSD_990_PRO_Datasheet_Rev.1.0_10119997003518.pdf)、[Sandisk/WD_BLACK 官方型号页](https://www.sandisk.com/products/ssd/internal-ssd/wd-black-sn850x-nvme-ssd?sku=WDS100T2X0E)；[逐字段记录及十进制容量约定](../catalog-parameters.md) |

## 数据状态与范围说明

- ASUS 与 MSI 的 DIMM 数和 M.2 数是物理插槽数量；尺寸、接口、协议是各槽可接受的 SSD 规格。当前 V10 对每张主板保存的 M.2 组合为 PCIe/NVMe；未来混合 SATA 与 PCIe 的主板应按槽逐一建模，不能任意组合多个集合。
- ASUS/MSI 第一方规格页列出的 PCIe 4.0 ×4 与当前结构化的主板 `parameters` 不同：schema 和 V4/V10 种子没有存储 M.2 代际或通道数。因此这项研究能确认厂商页面上的规格，但当前字段驱动的标签只能显示已结构化的接口/协议/尺寸与插槽数。
- 机箱标签表达厂商公布的部件尺寸上限，不是已装入商品组合的自动兼容结论；散热器高度比较也不能证明热性能。电源瓦数本身也不证明整机功率需求满足。
- 内存的 `6000 MT/s` 在种子中 `label` 已写明“EXPO/XMP 数据速率”，建议前台呈现时明确标成配置档速度，并与默认 JEDEC 值分开。对照主板的 CPU/DIMM 条件后，才能讨论某一组合是否实际训练到该档位；本报告没有进行此验证。
- SSD 的 Samsung 和 WD 数值、AMD 的峰值功耗未知状态及 AMD 显卡能力不在本轮重新核验范围。缺乏一手资料或结构化值时应显示“待核验”，不要从摘要推断；AMD 条目由并行的专门核查处理。
- 已有性能分应与这些规格标签分离。[性能数据契约](../performance-data.md)说明 CPU/GPU 分数基于 Blender Open Data 型号级参考样本，经项目映射形成，不是 Blender 官方等级或游戏 FPS；内存/SSD 分数按容量映射计算，不是性能基准测试。本报告未重新审计这些原始样本或 AMD 数据。

## 复核依据

- [商品参数数据契约](../catalog-parameters.md)：型号、逐字段来源、单位与未知值规则。
- [V4 商品参数迁移](../../backend/src/main/resources/db/migration/V4__catalog_specifications.sql)：首批结构化参数及字段来源。
- [V10 主板 SSD 有效组合迁移](../../backend/src/main/resources/db/migration/V10__explicit_motherboard_ssd_combinations.sql)：支持组合不是物理插槽计数。
- [HardwareParameters.java](../../backend/src/main/java/cn/zhuangjimao/catalog/HardwareParameters.java)：类别字段、单位和 required 规则；来源 URL 仅做格式检查。
- [性能数据契约](../performance-data.md)：CPU/GPU 样本参考分及内存/SSD 容量映射分的边界。
