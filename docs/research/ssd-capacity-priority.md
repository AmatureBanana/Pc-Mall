# 演示 SSD 容量与顺序速度核验

核验日期：2026-11-04。仅检查演示种子里的 SSD 型号及厂商标称值。

## 覆盖范围

V2 导入的 SSD 只有 `SAMSUNG-MZ-V9P1T0BW` 与 `WD-WDS100T2X0E`。后续 [V15 演示迁移](../../backend/src/main/resources/db/migration/V15__trusted_performance_demo.sql)只新增 CPU；V4 为 V2 的两款 SSD 补充分类参数；V10 更新两款主板的 SSD 插槽组合，都没有新增 SSD 商品 SKU。[V17 容量优先参数迁移](../../backend/src/main/resources/db/migration/V17__capacity_priority_parameters.sql)为同两款 SSD 增加顺序读写参数，也没有新增商品。

## 厂商核验结果

| 种子 SKU | 精确版本与容量 | 接口 | 顺序读取 / 写入（厂商标称上限） |
| --- | --- | --- | --- |
| `SAMSUNG-MZ-V9P1T0BW` | Samsung 990 PRO，MZ-V9P1T0BW，1TB（1,000GB 十进制）；M.2 2280，无散热片 | PCIe Gen 4.0 x4，NVMe 2.0 | 7,450 / 6,900 MB/s |
| `WD-WDS100T2X0E` | WD_BLACK SN850X，WDS100T2X0E，1TB（1 trillion bytes）；M.2 2280，无散热片 | PCIe Gen4，最多 4 lanes（16 GT/s）；产品页标 PCIe 4.0 x4 | 7,300 / 6,300 MB/s |

Samsung 的型号代码、容量和无散热片区分由[官方 990 PRO 系列手册 v4.0](https://download.semiconductor.samsung.com/resources/brochure/990_PRO_Series_Brouchure_Web_Version_4.0_250523.pdf)确认：`BW` 列为标准 990 PRO，`CW` 列为带散热片版。官方[990 PRO 数据表 Rev. 2.0](https://download.semiconductor.samsung.com/resources/data-sheet/samsung_nvme_ssd_990_pro_datasheet_rev.2.0.pdf)将 `MZ-V9P1T0BW` 列为 1TB / 1,000GB，并列出 PCIe Gen 4.0 x4、NVMe 2.0 及 1TB 顺序性能 7,450 / 6,900 MB/s。Rev. 2.0 修订日期为 2023 年 7 月；Samsung 支持页将下载文件标为 2025-12-02 修改。对应的[官方 SKU 页面](https://www.samsung.com/uk/memory-storage/nvme-ssd/990-pro-1tb-nvme-pcie-gen-4-mz-v9p1t0bw/)也显示 MZ-V9P1T0BW、1 TB 与相同速度。

WD 的[官方 SanDisk 产品页](https://www.sandisk.com/products/ssd/internal-ssd/wd-black-sn850x-nvme-ssd?sku=WDS100T2X0E)直接显示 WDS100T2X0E 的 1TB、M.2 2280、PCIe 4.0 x4、7,300 / 6,300 MB/s；[官方数据表](https://documents.sandisk.com/content/dam/asset-library/en_us/assets/public/western-digital/product/internal-drives/wd-black-ssd/data-sheet-wd-black-sn850x-nvme-ssd.pdf)日期为 2025 年 7 月，按 8TB、4TB、2TB、1TB 列出型号和性能。`WDS100T2X0E` 在无散热片型号行；1TB 列是 7,300 / 6,300 MB/s（2TB 列的写入为 6,600 MB/s，不应套给 1TB）。

## 条件与限制

- 两家公布的顺序速度均为“up to”标称峰值，不是对每台主机或持续写入的保证。容量均按十进制标称；格式化、系统保留空间会使可用容量更低。
- Samsung Rev. 2.0 的性能测试使用 IOMeter 1.1.0、SSD 作为次级盘，测试平台为 Ryzen 7 5800X、DDR4 3600 32GB、Windows 10 Pro 64-bit、ASRock X570 Taichi。性能会随固件、硬件与配置变化；顺序写入在 Intelligent TurboWrite 激活时测得，且只在特定传输大小范围内工作。
- WD/SanDisk 表示速度基于内部测试，可能随主机、使用条件、容量等变化；其 `MB/s` 定义为 1 MB/s = 1,000,000 bytes/s。Gen4 需要兼容主板，SSD 可向下兼容 PCIe Gen3。

## 对现有种子记录的提示

V4 已正确记录两款 SKU 的 1TB（规范容量 1000 GB）、PCIe Gen4、NVMe 与 x4 通道；其描述也已区分无散热片版本。V4 未保存顺序读写速度，V17 为这两款商品补齐了速度字段，分别引用 Samsung Rev. 2.0 和 WD 2025 年 7 月数据表。V4 对其余已有参数的来源引用保持原样。
