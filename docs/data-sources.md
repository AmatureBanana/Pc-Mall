# 商品数据、图像与依赖来源

任务 02 新增的 `catalog-parameters-v1` 参数、描述与逐字段核验记录见 [catalog-parameters.md](catalog-parameters.md)。以下保留任务 01 的基础数据来源记录；后续分类规则须读取结构化参数，不能解析摘要。

数据版本：`catalog-demo-v1`；整理日期：2026-10-30。12 个真实型号用于公共商品展示，不是已验证可组装、可推荐的硬件库。显示摘要只摘录型号识别所需事实；后续任务须建立并逐项核验结构化技术参数。

所有 CNY 售价和库存是本项目自设的演示值，不引用厂商实时价格，不表示在售报价或可购买库存。SQL 中每行保存 `source_url`、`price_note` 与 `data_version`，接口和页面数据说明均保留此含义。

| 销售样本型号 | 厂商原始资料 | 本票采用的信息 |
| --- | --- | --- |
| Intel Core i5-14600KF | [Intel ARK](https://www.intel.com/content/www/us/en/products/sku/236778/intel-core-i5-processor-14600kf-24m-cache-up-to-5-30-ghz/specifications.html) | 品牌、型号、14代、LGA1700 |
| AMD Ryzen 7 7800X3D | [AMD](https://www.amd.com/en/products/processors/desktops/ryzen/7000-series/amd-ryzen-7-7800x3d.html) | 型号、系列、AM5 |
| ASUS TUF GAMING B760M-PLUS WIFI | [ASUS 官方手册](https://dlcdnets.asus.com/pub/ASUS/mb/LGA1700/TUF_GAMING_B760M-PLUS_WIFI/E21633_TUF_GAMING_B760M-PLUS_WIFI_UM_WEB.pdf) | 型号、Intel平台、M-ATX、DDR5；不是 D4 版本 |
| MSI MAG B650M MORTAR WIFI | [MSI](https://www.msi.com/Motherboard/MAG-B650M-MORTAR-WIFI/Specification) | 型号、AMD平台、M-ATX、DDR5 |
| NVIDIA GeForce RTX 4070 SUPER Founders Edition | [NVIDIA](https://www.nvidia.com/en-us/geforce/graphics-cards/40-series/rtx-4070-family/) | 型号、12GB；不借用其他厂商非公版型号 |
| AMD Radeon RX 7800 XT | [AMD](https://www.amd.com/en/products/graphics/desktops/radeon/7000-series/amd-radeon-rx-7800-xt.html) | 型号、16GB |
| Kingston KF560C36BBEK2-32 | [Kingston 数据表](https://www.kingston.com/datasheets/KF560C36BBEK2-32.pdf) | 总容量32GB、2条×16GB、DDR5、6000MT/s |
| DeepCool AK400 | [DeepCool](https://www.deepcool.com/products/Cooling/cpuaircoolers/AK400-Performance-CPU-Cooler-1700-AM5/2021/15222.shtml) | 型号、四热管、120mm风扇 |
| Samsung 990 PRO MZ-V9P1T0BW | [Samsung 数据表 Rev.1.0](https://download.semiconductor.samsung.com/resources/data-sheet/Samsung_NVMe_SSD_990_PRO_Datasheet_Rev.1.0_10119997003518.pdf) | 1TB＝1000GB、M.2 NVMe、PCIe4.0，不含散热片版本 |
| WD_BLACK SN850X WDS100T2X0E | [WD 官方数据表](https://documents.westerndigital.com/content/dam/doc-library/es_mx/assets/public/western-digital/product/internal-drives/wd-black-ssd/data-sheet-wd-black-sn850x-nvme-ssd.pdf) | 1TB＝1000GB、不含散热片型号、M.2 NVMe、PCIe4.0 |
| Corsair RM750e CP-9020295 | [Corsair](https://www.corsair.com/us/en/p/psu/cp-9020295-na/rme-series-rm750e-fully-modular-low-noise-atx-power-supply-cp-9020295-na) | 型号、750W、全模组；不混用旧款认证描述 |
| DeepCool CC560 | [DeepCool](https://www.deepcool.com/products/Cases/CC560-Mid-Tower-ATX-PC-Case/2021/15216.shtml) | 型号、中塔、黑色、ATX |

上述来源作为型号事实引用，未复制厂商图片、长篇说明或外部代码库。厂商名称与商标权归各自所有者，未暗示品牌授权或合作。

图片与图标是本项目原创 SVG：`scripts/build-illustrations.mjs` 可重新生成 `frontend/public/images`，图标在 `HardwareIcon.vue`。页面明确标注“品类示意”，同类商品共用图，不冒充具体型号照片。UI 构图依据用户附件；附件本身不进入仓库。

## 正式依赖及许可归因

| 来源 | 固定版本 / 固定方式 | 许可 |
| --- | --- | --- |
| [Spring Boot](https://github.com/spring-projects/spring-boot)（含 Spring Framework/Security 受管依赖） | 3.5.16 BOM，完整解析见构建 dependency tree | Apache-2.0 |
| [MyBatis Spring Boot Starter](https://github.com/mybatis/spring-boot-starter) | 3.0.5 | Apache-2.0 |
| [Flyway Community](https://github.com/flyway/flyway) | Spring Boot 3.5.16 BOM 管理的 core/mysql | Apache-2.0（所用开源模块） |
| [MySQL Connector/J](https://github.com/mysql/mysql-connector-j) | Spring Boot 3.5.16 BOM 管理 | GPL-2.0 with Universal FOSS Exception 1.0 |
| [Vue](https://github.com/vuejs/core) | 3.5.44 | MIT |
| [Element Plus](https://github.com/element-plus/element-plus) | 2.14.7 | MIT |
| [Vite](https://github.com/vitejs/vite)、[@vitejs/plugin-vue](https://github.com/vitejs/vite-plugin-vue) | 8.3.5 / 6.0.10 | MIT |
| [TypeScript](https://github.com/microsoft/TypeScript)、[vue-tsc](https://github.com/vuejs/language-tools) | 5.9.3 / 3.3.14 | Apache-2.0 / MIT |
| [Playwright](https://github.com/microsoft/playwright) | 1.64.1 | Apache-2.0 |
| [Apache Maven / Wrapper](https://github.com/apache/maven-wrapper) | Maven 3.9.11 / Wrapper 3.3.4 | Apache-2.0 |

全部第三方组件以依赖方式使用，未复制参考商城业务源码或硬件数据库。分发组件时保留各包内原有 LICENSE/NOTICE。MySQL Server 8.0.45（GPL-2.0）与 Edge 使用本机已有安装，不把它们的二进制或数据目录提交至本仓库。
