# 装机场景 CPU 单核与多核基准数据

采集日期为 2026-11-18（北京时间，UTC+08:00）。下表从同一份 Primate Labs Geekbench Browser 处理器总榜读取，使用 **Geekbench 7 CPU Benchmark** 的型号级单核和多核原始分数。逐型号页用于复核处理器名称和两项分数。 [Geekbench Processor Benchmark Chart](https://browser.geekbench.com/processor-benchmarks)

## 目录 CPU

| Geekbench 精确型号 | 单核分数 | 多核分数 | 型号页 | 厂商型号核对 |
| --- | ---: | ---: | --- | --- |
| Intel Core i5-14600K | 2512 | 17129 | [Geekbench](https://browser.geekbench.com/processors/intel-core-i5-14600k) | [Intel ARK 236799](https://www.intel.com/content/www/us/en/products/sku/236799/intel-core-i5-processor-14600k-24m-cache-up-to-5-30-ghz/specifications.html) |
| Intel Core i5-14600KF | 2458 | 17209 | [Geekbench](https://browser.geekbench.com/processors/intel-core-i5-14600kf) | [Intel ARK 236778](https://www.intel.com/content/www/us/en/products/sku/236778/intel-core-i5-processor-14600kf-24m-cache-up-to-5-30-ghz/specifications.html) |
| AMD Ryzen 7 7800X3D | 2437 | 15609 | [Geekbench](https://browser.geekbench.com/processors/amd-ryzen-7-7800x3d) | [AMD 产品规格](https://www.amd.com/en/products/processors/desktops/ryzen/7000-series/amd-ryzen-7-7800x3d.html) |

按品牌与完整型号匹配目录 CPU。`i5-14600K` 和 `i5-14600KF` 各自使用独立的榜单条目，`7800X3D` 保留完整后缀。 [商品参数](../catalog-parameters.md) · [性能参考](../performance-data.md)

## 扩展目录补录

2026-11-25 为扩展目录新增 60 款 CPU 的单核和多核成绩。48 款按品牌和完整型号匹配官方 Geekbench 7 处理器总榜，原先已录入的 3 款保留其历史成绩。详情页随成绩显示来源和采集口径。 [Geekbench 7 处理器总榜](https://browser.geekbench.com/processor-benchmarks) · [补录迁移](../../backend/src/main/resources/db/catalog/V38__expanded_cpu_geekbench7_references.sql)

另外 12 款采用 Geekbench Browser 上可核对准确型号、单核和多核的单次 CPU 测试。这些成绩受测试平台及设置影响，不能当作型号平均分。Geekbench 7.0 和 7.1 的 CPU 分数可以比较。 [版本说明](https://www.geekbench.com/blog/2026/09/geekbench-71/)

| 型号 | 单核 | 多核 | 测试版本和系统 | 来源 |
| --- | ---: | ---: | --- | --- |
| AMD Ryzen 5 7600X3D | 2371 | 11534 | Geekbench 7.1.0 Linux | [公开结果](https://browser.geekbench.com/v7/cpu/812463) |
| AMD Ryzen 5 8400F | 2236 | 10331 | Geekbench 7.1.0 Windows | [公开结果](https://browser.geekbench.com/v7/cpu/906853) |
| AMD Ryzen 5 8500G | 2145 | 9149 | Geekbench 7.1.0 Windows | [公开结果](https://browser.geekbench.com/v7/cpu/875358) |
| AMD Ryzen 5 8600G | 2314 | 11234 | Geekbench 7.1.0 Linux | [公开结果](https://browser.geekbench.com/v7/cpu/958663) |
| Intel Core Ultra 5 245K | 2617 | 19492 | Geekbench 7.0.0 Windows | [公开结果](https://browser.geekbench.com/v7/cpu/146096) |
| Intel Core Ultra 5 225F | 2125 | 11849 | Geekbench 7.1.0 Windows | [公开结果](https://browser.geekbench.com/v7/cpu/921808) |
| Intel Core Ultra 5 235 | 2661 | 16108 | Geekbench 7.1.0 Linux | [公开结果](https://browser.geekbench.com/v7/cpu/compare/727915?baseline=684680) |
| Intel Core Ultra 5 225 | 2492 | 11676 | Geekbench 7.0.0 Windows | [公开结果](https://browser.geekbench.com/v7/cpu/compare/457613?baseline=451232) |
| Intel Core Ultra 9 285 | 2825 | 20194 | Geekbench 7.0.0 Windows | [公开结果](https://beta.geekbench.com/v7/cpu/65903) |
| Intel Core Ultra 7 265 | 2842 | 21974 | Geekbench 7.1.0 Linux | [公开结果](https://browser.geekbench.com/v7/cpu/compare/730336?baseline=5431) |
| Intel Core Ultra 7 265F | 2629 | 21892 | Geekbench 7.1.0 Windows | [公开结果](https://browser.geekbench.com/v7/cpu/956564) |
| Intel Core Ultra 5 245KF | 2974 | 21097 | Geekbench 7.1.0 Linux | [公开结果](https://browser.geekbench.com/v7/cpu/495022) |

## 同口径参考型号

这些型号用于观察单核和多核分数的跨度。所列数值同样来自上表使用的 Geekbench 7 总榜，供后续讨论场景阈值。 [Geekbench Processor Benchmark Chart](https://browser.geekbench.com/processor-benchmarks)

| Geekbench 精确型号 | 单核分数 | 多核分数 | 型号页 |
| --- | ---: | ---: | --- |
| Intel Core i3-12100 | 1899 | 6823 | [Geekbench](https://browser.geekbench.com/processors/intel-core-i3-12100) |
| AMD Ryzen 5 5600 | 1925 | 9096 | [Geekbench](https://browser.geekbench.com/processors/amd-ryzen-5-5600) |
| AMD Ryzen 7 7700 | 2508 | 15240 | [Geekbench](https://browser.geekbench.com/processors/amd-ryzen-7-7700) |
| AMD Ryzen 7 9800X3D | 2965 | 18725 | [Geekbench](https://browser.geekbench.com/processors/amd-ryzen-7-9800x3d) |
| Intel Core i9-14900K | 2658 | 22001 | [Geekbench](https://browser.geekbench.com/processors/intel-core-i9-14900k) |

## 采集口径与使用限制

- **入榜条件**。Primate Labs 将用户提交的 Geekbench 7 结果按型号汇总，用来反映型号的平均表现。榜单要求每个型号至少有五条不同结果。官网总榜没有标明每款处理器的实际样本数、汇总统计量的具体算法、操作系统构成、内存和功耗设置。记录的样本口径是官网型号级汇总。 [Geekbench Processor Benchmark Chart](https://browser.geekbench.com/processor-benchmarks)
- **版本核对**。总榜明确标注 Geekbench 7。逐型号页的简介仍含 Geekbench 6 字样，页面的 Geekbench 7 校准说明和本次核对的分数与总榜一致，因此采集版本以总榜标注为准。Primate Labs 明确说明 Geekbench 7.0 与 7.1 的 CPU 分数可以比较。 [总榜](https://browser.geekbench.com/processor-benchmarks) · [型号页示例](https://browser.geekbench.com/processors/intel-core-i5-14600kf) · [Geekbench 7.1 发布说明](https://www.geekbench.com/blog/2026/09/geekbench-71/)
- **校准与固定值**。官方用 AMD Ryzen 7 7700 校准机定义 2500 分基准。同型号在社区总榜的这次观测值为单核 2508、多核 15240。总榜与型号页会随提交更新，本文件记录采集当天读到的整数。 [总榜与校准说明](https://browser.geekbench.com/processor-benchmarks) · [Ryzen 7 7700 型号页](https://browser.geekbench.com/processors/amd-ryzen-7-7700)
- **分数含义**。测试覆盖网页浏览、图像处理、媒体编解码等工作负载，也受内存表现影响。单核和多核各自用于衡量这组工作负载中的表现。整机游戏帧率和具体创作应用的耗时需要相应的实测数据。 [Geekbench 7 CPU 工作负载](https://www.geekbench.com/doc/geekbench7-cpu-workloads.pdf) · [Geekbench 7 发布说明](https://www.geekbench.com/blog/2026/07/geekbench-7/)

## 手动 DIY 整机分档

每项按原始指标的相邻节点线性插值，超过满分节点封顶 100。从零起算，办公对独立显存无要求，零独立显存对应 40 分。选用经核验的核显时，图形和显存以办公门槛计入，游戏和创作都需要选用独显。以下阈值是项目评分策略。

| 指标 | 办公 40 分 | 游戏 70 分 | 创作 90 分 | 满分节点 |
| --- | ---: | ---: | ---: | ---: |
| Geekbench 7 单核 | 1800 | 1900 | 2400 | 3200 |
| Geekbench 7 多核 | 6500 | 9000 | 15000 | 25000 |
| Blender 4.3.0 独显原始渲染分 | 500 | 1400 | 4500 | 8000 |
| 整机内存（GB） | 8 | 16 | 32 | 64 |
| 独立显存（GB） | 0 | 8 | 12 | 24 |
| SSD 容量（十进制 GB） | 256 | 512 | 1000 | 2000 |
| SSD 标称顺序读取（MB/s） | 400 | 1000 | 3000 | 7000 |
| SSD 标称顺序写入（MB/s） | 250 | 600 | 2000 | 6000 |

CPU 前三个档位参考 i3-12100、Ryzen 5 5600 和 Ryzen 7 7700 的榜单分。显卡游戏门槛原始分 1400 对应旧 F04 游戏门槛 40，创作门槛原始分 4500 是现有显卡评分参考节点，对应旧规范分 75。显卡分使用项目的 Blender 渲染参考，NVIDIA 样本用 OPTIX，AMD 样本用 HIP。SSD 读写使用商品的厂商标称值，容量独立参与分档。 [现有性能分映射](../performance-data.md) · [容量优先商品评分](../capacity-priority-scores.md)

整机等级分取各项最小值。低于 40 未达到办公档，40 至不足 70 为办公档，70 至不足 90 为游戏档，90 起为创作档。游戏档同时适合办公，创作档同时适合办公和游戏。配置缺件、兼容性未通过或缺少参与分档的数据时，DIY 显示对应状态。

同档加权参考分按 CPU 单核 15%、CPU 多核 20%、GPU 30%、内存 15%、显存 10%、SSD 10% 计算。内存组按容量分占 75%、现有速率及时序性能子分占 25% 计算。SSD 组按容量和读写性能各占 50% 计算，其中读写性能取读取分与写入分中较低的一项。内存性能子分缺失时，DIY 仍可显示整机等级和场景。

### 目录数据试算

以下配置均使用三星 990 PRO 1 TB，其源数据为 7450 MB/s 顺序读取、6900 MB/s 顺序写入。内存总量按 16 GB 商品件数合计。 [SSD 商品参数](../catalog-parameters.md)

| 配置 | 等级分 | 最高适用场景 | 限制分档的指标 |
| --- | ---: | --- | --- |
| i5-14600K 核显，16 GB | 40.00 | 办公 | 核显按办公能力计入 |
| i5-14600KF + RTX 4070 SUPER，16 GB | 70.00 | 游戏 | 内存容量 |
| i5-14600KF + RTX 4070 SUPER，32 GB | 90.00 | 创作 | 内存、独立显存、SSD 容量 |
| Ryzen 7 7800X3D + RX 7800 XT，32 GB | 76.72 | 游戏 | 独显 Blender 渲染分 |

现有创作样本使用 i5-14600K 和 16 GB 内存，按新门槛会归入游戏档。 [现有创作样本](../../data/performance-v1/scenarios.json)
