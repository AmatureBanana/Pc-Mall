-- catalog-parameters-v1; verified 2026-10-30 against manufacturer sources.
-- Field-by-field provenance and unknown values: docs/catalog-parameters.md.
-- Existing IDs, internal sales SKUs, prices and stock are preserved.
CREATE TABLE product_specifications (
    product_id BIGINT PRIMARY KEY,
    description TEXT NOT NULL,
    parameters JSON NOT NULL,
    data_version VARCHAR(40) NOT NULL,
    verified_on DATE NOT NULL,
    CONSTRAINT fk_specification_product FOREIGN KEY (product_id) REFERENCES products(id),
    CONSTRAINT ck_parameters_array CHECK (JSON_TYPE(parameters) = 'ARRAY')
);

INSERT INTO product_specifications (product_id, description, parameters, data_version, verified_on)
SELECT id, 'Intel 第 14 代桌面处理器，14 核 20 线程，LGA1700 插槽；KF 型号无核显，需要独立显卡。',
'[
  {
    "code": "socket",
    "label": "CPU 插槽",
    "value": "LGA1700",
    "unit": "",
    "sourceUrl": "https://www.intel.com/content/www/us/en/products/sku/236778/intel-core-i5-processor-14600kf-24m-cache-up-to-5-30-ghz/specifications.html",
    "note": ""
  },
  {
    "code": "cores",
    "label": "核心数",
    "value": 14,
    "unit": "核",
    "sourceUrl": "https://www.intel.com/content/www/us/en/products/sku/236778/intel-core-i5-processor-14600kf-24m-cache-up-to-5-30-ghz/specifications.html",
    "note": ""
  },
  {
    "code": "threads",
    "label": "线程数",
    "value": 20,
    "unit": "线程",
    "sourceUrl": "https://www.intel.com/content/www/us/en/products/sku/236778/intel-core-i5-processor-14600kf-24m-cache-up-to-5-30-ghz/specifications.html",
    "note": ""
  },
  {
    "code": "integratedGraphics",
    "label": "内置核显",
    "value": false,
    "unit": "",
    "sourceUrl": "https://www.intel.com/content/www/us/en/support/articles/000027144/processors/intel-core-processors.html",
    "note": ""
  },
  {
    "code": "basePower",
    "label": "处理器基础功耗",
    "value": 125,
    "unit": "W",
    "sourceUrl": "https://www.intel.com/content/www/us/en/products/sku/236778/intel-core-i5-processor-14600kf-24m-cache-up-to-5-30-ghz/specifications.html",
    "note": ""
  },
  {
    "code": "peakPowerEstimate",
    "label": "CPU 峰值功耗估算",
    "value": 181,
    "unit": "W",
    "sourceUrl": "https://www.intel.com/content/www/us/en/products/sku/236778/intel-core-i5-processor-14600kf-24m-cache-up-to-5-30-ghz/specifications.html",
    "note": "采用厂商 Maximum Turbo Power，非基础功耗。"
  },
  {
    "code": "memoryGenerations",
    "label": "支持内存代际",
    "value": [
      "DDR4",
      "DDR5"
    ],
    "unit": "",
    "sourceUrl": "https://www.intel.com/content/www/us/en/products/sku/236778/intel-core-i5-processor-14600kf-24m-cache-up-to-5-30-ghz/specifications.html",
    "note": ""
  }
]', 'catalog-parameters-v1', '2026-10-30'
FROM products WHERE sku = 'INTEL-I5-14600KF';

INSERT INTO product_specifications (product_id, description, parameters, data_version, verified_on)
SELECT id, 'AMD Ryzen 7000 系列桌面处理器，8 核 16 线程，AM5 插槽，内置 AMD Radeon Graphics。',
'[
  {
    "code": "socket",
    "label": "CPU 插槽",
    "value": "AM5",
    "unit": "",
    "sourceUrl": "https://www.amd.com/en/products/processors/desktops/ryzen/7000-series/amd-ryzen-7-7800x3d.html",
    "note": ""
  },
  {
    "code": "cores",
    "label": "核心数",
    "value": 8,
    "unit": "核",
    "sourceUrl": "https://www.amd.com/en/products/processors/desktops/ryzen/7000-series/amd-ryzen-7-7800x3d.html",
    "note": ""
  },
  {
    "code": "threads",
    "label": "线程数",
    "value": 16,
    "unit": "线程",
    "sourceUrl": "https://www.amd.com/en/products/processors/desktops/ryzen/7000-series/amd-ryzen-7-7800x3d.html",
    "note": ""
  },
  {
    "code": "integratedGraphics",
    "label": "内置核显",
    "value": true,
    "unit": "",
    "sourceUrl": "https://www.amd.com/en/products/processors/desktops/ryzen/7000-series/amd-ryzen-7-7800x3d.html",
    "note": ""
  },
  {
    "code": "tdp",
    "label": "默认 TDP",
    "value": 120,
    "unit": "W",
    "sourceUrl": "https://www.amd.com/en/products/processors/desktops/ryzen/7000-series/amd-ryzen-7-7800x3d.html",
    "note": "TDP 不等于峰值功耗。"
  },
  {
    "code": "peakPowerEstimate",
    "label": "CPU 峰值功耗估算",
    "value": null,
    "unit": "W",
    "sourceUrl": "https://www.amd.com/en/products/processors/desktops/ryzen/7000-series/amd-ryzen-7-7800x3d.html",
    "note": "当前厂商资料未核验峰值估算；不得用 120W TDP 代替。"
  },
  {
    "code": "memoryGenerations",
    "label": "支持内存代际",
    "value": [
      "DDR5"
    ],
    "unit": "",
    "sourceUrl": "https://www.amd.com/en/products/processors/desktops/ryzen/7000-series/amd-ryzen-7-7800x3d.html",
    "note": ""
  }
]', 'catalog-parameters-v1', '2026-10-30'
FROM products WHERE sku = 'AMD-R7-7800X3D';

INSERT INTO product_specifications (product_id, description, parameters, data_version, verified_on)
SELECT id, 'Micro-ATX 主板，四条 DDR5 内存插槽，配备 HDMI 与 DisplayPort 输出；输出能力依赖所装 CPU 的核显。两个 M.2 插槽支持 PCIe NVMe SSD。',
'[
  {
    "code": "socket",
    "label": "CPU 插槽",
    "value": "LGA1700",
    "unit": "",
    "sourceUrl": "https://www.asus.com/us/motherboards-components/motherboards/tuf-gaming/tuf-gaming-b760m-plus-wifi/techspec/",
    "note": ""
  },
  {
    "code": "formFactor",
    "label": "主板板型",
    "value": "Micro-ATX",
    "unit": "",
    "sourceUrl": "https://www.asus.com/us/motherboards-components/motherboards/tuf-gaming/tuf-gaming-b760m-plus-wifi/techspec/",
    "note": ""
  },
  {
    "code": "memoryGenerations",
    "label": "支持内存代际",
    "value": [
      "DDR5"
    ],
    "unit": "",
    "sourceUrl": "https://www.asus.com/us/motherboards-components/motherboards/tuf-gaming/tuf-gaming-b760m-plus-wifi/techspec/",
    "note": ""
  },
  {
    "code": "memorySlots",
    "label": "内存插槽数",
    "value": 4,
    "unit": "个",
    "sourceUrl": "https://www.asus.com/us/motherboards-components/motherboards/tuf-gaming/tuf-gaming-b760m-plus-wifi/techspec/",
    "note": ""
  },
  {
    "code": "displayOutputs",
    "label": "主板视频输出",
    "value": [
      "HDMI",
      "DisplayPort"
    ],
    "unit": "",
    "sourceUrl": "https://www.asus.com/us/motherboards-components/motherboards/tuf-gaming/tuf-gaming-b760m-plus-wifi/techspec/",
    "note": "须配合具有有效核显的 CPU。"
  },
  {
    "code": "gpuInterfaces",
    "label": "显卡安装接口",
    "value": [
      "PCIe x16"
    ],
    "unit": "",
    "sourceUrl": "https://www.asus.com/us/motherboards-components/motherboards/tuf-gaming/tuf-gaming-b760m-plus-wifi/techspec/",
    "note": "指物理插槽规格，不保证所有 CPU 下的电气通道数。"
  },
  {
    "code": "ssdFormFactors",
    "label": "M.2 SSD 支持规格",
    "value": [
      "M.2 2242",
      "M.2 2260",
      "M.2 2280"
    ],
    "unit": "",
    "sourceUrl": "https://www.asus.com/us/motherboards-components/motherboards/tuf-gaming/tuf-gaming-b760m-plus-wifi/techspec/",
    "note": "集合仅适用于两个 M.2 插槽，不描述 SATA 端口。"
  },
  {
    "code": "ssdInterfaces",
    "label": "M.2 SSD 支持接口",
    "value": [
      "PCIe"
    ],
    "unit": "",
    "sourceUrl": "https://www.asus.com/us/motherboards-components/motherboards/tuf-gaming/tuf-gaming-b760m-plus-wifi/techspec/",
    "note": ""
  },
  {
    "code": "ssdProtocols",
    "label": "M.2 SSD 支持协议",
    "value": [
      "NVMe"
    ],
    "unit": "",
    "sourceUrl": "https://www.asus.com/us/motherboards-components/motherboards/tuf-gaming/tuf-gaming-b760m-plus-wifi/techspec/",
    "note": ""
  },
  {
    "code": "m2Slots",
    "label": "M.2 插槽数",
    "value": 2,
    "unit": "个",
    "sourceUrl": "https://www.asus.com/us/motherboards-components/motherboards/tuf-gaming/tuf-gaming-b760m-plus-wifi/techspec/",
    "note": ""
  }
]', 'catalog-parameters-v1', '2026-10-30'
FROM products WHERE sku = 'ASUS-TUF-B760M-PLUS-WIFI';

INSERT INTO product_specifications (product_id, description, parameters, data_version, verified_on)
SELECT id, 'Micro-ATX 主板，四条 DDR5 内存插槽，配备 HDMI 与 DisplayPort 输出；输出能力依赖所装 CPU 的核显。两个 M.2 插槽支持 PCIe NVMe SSD。',
'[
  {
    "code": "socket",
    "label": "CPU 插槽",
    "value": "AM5",
    "unit": "",
    "sourceUrl": "https://www.msi.com/Motherboard/MAG-B650M-MORTAR-WIFI/Specification",
    "note": ""
  },
  {
    "code": "formFactor",
    "label": "主板板型",
    "value": "Micro-ATX",
    "unit": "",
    "sourceUrl": "https://www.msi.com/Motherboard/MAG-B650M-MORTAR-WIFI/Specification",
    "note": ""
  },
  {
    "code": "memoryGenerations",
    "label": "支持内存代际",
    "value": [
      "DDR5"
    ],
    "unit": "",
    "sourceUrl": "https://www.msi.com/Motherboard/MAG-B650M-MORTAR-WIFI/Specification",
    "note": ""
  },
  {
    "code": "memorySlots",
    "label": "内存插槽数",
    "value": 4,
    "unit": "个",
    "sourceUrl": "https://www.msi.com/Motherboard/MAG-B650M-MORTAR-WIFI/Specification",
    "note": ""
  },
  {
    "code": "displayOutputs",
    "label": "主板视频输出",
    "value": [
      "HDMI",
      "DisplayPort"
    ],
    "unit": "",
    "sourceUrl": "https://www.msi.com/Motherboard/MAG-B650M-MORTAR-WIFI/Specification",
    "note": "须配合具有有效核显的 CPU。"
  },
  {
    "code": "gpuInterfaces",
    "label": "显卡安装接口",
    "value": [
      "PCIe x16"
    ],
    "unit": "",
    "sourceUrl": "https://www.msi.com/Motherboard/MAG-B650M-MORTAR-WIFI/Specification",
    "note": "指物理插槽规格，不保证所有 CPU 下的电气通道数。"
  },
  {
    "code": "ssdFormFactors",
    "label": "M.2 SSD 支持规格",
    "value": [
      "M.2 2260",
      "M.2 2280"
    ],
    "unit": "",
    "sourceUrl": "https://www.msi.com/Motherboard/MAG-B650M-MORTAR-WIFI/Specification",
    "note": "集合仅适用于两个 M.2 插槽，不描述 SATA 端口。"
  },
  {
    "code": "ssdInterfaces",
    "label": "M.2 SSD 支持接口",
    "value": [
      "PCIe"
    ],
    "unit": "",
    "sourceUrl": "https://www.msi.com/Motherboard/MAG-B650M-MORTAR-WIFI/Specification",
    "note": ""
  },
  {
    "code": "ssdProtocols",
    "label": "M.2 SSD 支持协议",
    "value": [
      "NVMe"
    ],
    "unit": "",
    "sourceUrl": "https://www.msi.com/Motherboard/MAG-B650M-MORTAR-WIFI/Specification",
    "note": ""
  },
  {
    "code": "m2Slots",
    "label": "M.2 插槽数",
    "value": 2,
    "unit": "个",
    "sourceUrl": "https://www.msi.com/Motherboard/MAG-B650M-MORTAR-WIFI/Specification",
    "note": ""
  }
]', 'catalog-parameters-v1', '2026-10-30'
FROM products WHERE sku = 'MSI-MAG-B650M-MORTAR-WIFI';

INSERT INTO product_specifications (product_id, description, parameters, data_version, verified_on)
SELECT id, 'NVIDIA GeForce RTX 4070 SUPER Founders Edition 公版显卡，12GB GDDR6X 显存；尺寸与功耗仅对应 FE 版本。',
'[
  {
    "code": "boardVariant",
    "label": "显卡版本",
    "value": "Founders Edition",
    "unit": "",
    "sourceUrl": "https://www.nvidia.com/en-us/geforce/graphics-cards/40-series/rtx-4070-family/",
    "note": ""
  },
  {
    "code": "vram",
    "label": "显存容量",
    "value": 12,
    "unit": "GB",
    "sourceUrl": "https://www.nvidia.com/en-us/geforce/graphics-cards/40-series/rtx-4070-family/",
    "note": ""
  },
  {
    "code": "installationInterface",
    "label": "安装接口",
    "value": "PCIe x16",
    "unit": "",
    "sourceUrl": "https://www.nvidia.com/en-us/geforce/graphics-cards/40-series/rtx-4070-family/",
    "note": ""
  },
  {
    "code": "pcieGeneration",
    "label": "PCIe 代际",
    "value": "4.0",
    "unit": "",
    "sourceUrl": "https://www.nvidia.com/en-us/geforce/graphics-cards/40-series/rtx-4070-family/",
    "note": ""
  },
  {
    "code": "boardPowerEstimate",
    "label": "GPU 总板功耗估算",
    "value": 220,
    "unit": "W",
    "sourceUrl": "https://www.nvidia.com/en-us/geforce/graphics-cards/40-series/rtx-4070-family/",
    "note": "采用厂商 Total Graphics Power。"
  },
  {
    "code": "length",
    "label": "显卡长度",
    "value": 244,
    "unit": "mm",
    "sourceUrl": "https://www.nvidia.com/en-us/geforce/graphics-cards/40-series/rtx-4070-family/",
    "note": ""
  },
  {
    "code": "displayOutputs",
    "label": "显卡视频输出",
    "value": [
      "HDMI",
      "DisplayPort"
    ],
    "unit": "",
    "sourceUrl": "https://www.nvidia.com/en-us/geforce/graphics-cards/40-series/rtx-4070-family/",
    "note": ""
  }
]', 'catalog-parameters-v1', '2026-10-30'
FROM products WHERE sku = 'NVIDIA-RTX4070SUPER-FE';

INSERT INTO product_specifications (product_id, description, parameters, data_version, verified_on)
SELECT id, 'AMD Radeon RX 7800 XT 公版显卡，16GB GDDR6 显存；267mm 长度对应 AMD 公版，不适用于其他厂商非公版。',
'[
  {
    "code": "boardVariant",
    "label": "显卡版本",
    "value": "AMD reference",
    "unit": "",
    "sourceUrl": "https://www.amd.com/en/products/graphics/desktops/radeon/7000-series/amd-radeon-rx-7800-xt.html",
    "note": ""
  },
  {
    "code": "vram",
    "label": "显存容量",
    "value": 16,
    "unit": "GB",
    "sourceUrl": "https://www.amd.com/en/products/graphics/desktops/radeon/7000-series/amd-radeon-rx-7800-xt.html",
    "note": ""
  },
  {
    "code": "installationInterface",
    "label": "安装接口",
    "value": "PCIe x16",
    "unit": "",
    "sourceUrl": "https://www.amd.com/content/dam/amd/en/documents/partner-hub/radeon/amd-radeon-rx-7000-series-quick-reference-competitive.pdf",
    "note": ""
  },
  {
    "code": "pcieGeneration",
    "label": "PCIe 代际",
    "value": "4.0",
    "unit": "",
    "sourceUrl": "https://www.amd.com/content/dam/amd/en/documents/partner-hub/radeon/amd-radeon-rx-7000-series-quick-reference-competitive.pdf",
    "note": ""
  },
  {
    "code": "boardPowerEstimate",
    "label": "GPU 总板功耗估算",
    "value": 263,
    "unit": "W",
    "sourceUrl": "https://www.amd.com/en/products/graphics/desktops/radeon/7000-series/amd-radeon-rx-7800-xt.html",
    "note": "采用厂商 Typical Board Power。"
  },
  {
    "code": "length",
    "label": "显卡长度",
    "value": 267,
    "unit": "mm",
    "sourceUrl": "https://www.amd.com/en/products/graphics/desktops/radeon/7000-series/amd-radeon-rx-7800-xt.html",
    "note": ""
  },
  {
    "code": "displayOutputs",
    "label": "显卡视频输出",
    "value": [
      "HDMI",
      "DisplayPort"
    ],
    "unit": "",
    "sourceUrl": "https://www.amd.com/en/products/graphics/desktops/radeon/7000-series/amd-radeon-rx-7800-xt.html",
    "note": ""
  }
]', 'catalog-parameters-v1', '2026-10-30'
FROM products WHERE sku = 'AMD-RX7800XT';

INSERT INTO product_specifications (product_id, description, parameters, data_version, verified_on)
SELECT id, 'Kingston FURY Beast DDR5 内存套装，两条 16GB 内存作为一个销售单位，总标称容量 32GB；6000MT/s 为 EXPO/XMP 配置速度。',
'[
  {
    "code": "generation",
    "label": "内存代际",
    "value": "DDR5",
    "unit": "",
    "sourceUrl": "https://www.kingston.com/datasheets/KF560C36BBEK2-32.pdf",
    "note": ""
  },
  {
    "code": "totalCapacity",
    "label": "套装总标称容量",
    "value": 32,
    "unit": "GB",
    "sourceUrl": "https://www.kingston.com/datasheets/KF560C36BBEK2-32.pdf",
    "note": ""
  },
  {
    "code": "stickCount",
    "label": "实际内存条数",
    "value": 2,
    "unit": "条",
    "sourceUrl": "https://www.kingston.com/datasheets/KF560C36BBEK2-32.pdf",
    "note": ""
  },
  {
    "code": "capacityPerStick",
    "label": "单条容量",
    "value": 16,
    "unit": "GB",
    "sourceUrl": "https://www.kingston.com/datasheets/KF560C36BBEK2-32.pdf",
    "note": ""
  },
  {
    "code": "dataRate",
    "label": "EXPO/XMP 数据速率",
    "value": 6000,
    "unit": "MT/s",
    "sourceUrl": "https://www.kingston.com/datasheets/KF560C36BBEK2-32.pdf",
    "note": ""
  },
  {
    "code": "jedecDataRate",
    "label": "默认 JEDEC 数据速率",
    "value": 4800,
    "unit": "MT/s",
    "sourceUrl": "https://www.kingston.com/datasheets/KF560C36BBEK2-32.pdf",
    "note": ""
  }
]', 'catalog-parameters-v1', '2026-10-30'
FROM products WHERE sku = 'KINGSTON-KF560C36BBEK2-32';

INSERT INTO product_specifications (product_id, description, parameters, data_version, verified_on)
SELECT id, 'DeepCool AK400 黑色塔式风冷散热器，四根热管和 120mm 风扇；完整产品高度为 155mm。',
'[
  {
    "code": "supportedSockets",
    "label": "支持 CPU 插槽",
    "value": [
      "LGA1700",
      "LGA1200",
      "LGA1151",
      "LGA1150",
      "LGA1155",
      "AM5",
      "AM4"
    ],
    "unit": "",
    "sourceUrl": "https://www.deepcool.com/products/Cooling/cpuaircoolers/AK400-Performance-CPU-Cooler-1700-AM5/2021/15222.shtml",
    "note": ""
  },
  {
    "code": "height",
    "label": "散热器完整高度",
    "value": 155,
    "unit": "mm",
    "sourceUrl": "https://www.deepcool.com/products/Cooling/cpuaircoolers/AK400-Performance-CPU-Cooler-1700-AM5/2021/15222.shtml",
    "note": ""
  },
  {
    "code": "fanDiameter",
    "label": "风扇直径",
    "value": 120,
    "unit": "mm",
    "sourceUrl": "https://www.deepcool.com/products/Cooling/cpuaircoolers/AK400-Performance-CPU-Cooler-1700-AM5/2021/15222.shtml",
    "note": ""
  }
]', 'catalog-parameters-v1', '2026-10-30'
FROM products WHERE sku = 'DEEPCOOL-AK400';

INSERT INTO product_specifications (product_id, description, parameters, data_version, verified_on)
SELECT id, 'Samsung 990 PRO 1TB，MZ-V9P1T0BW 无散热片版本，M.2 2280 NVMe SSD。 按十进制容量 1TB＝1000GB 记录。',
'[
  {
    "code": "capacity",
    "label": "规范容量",
    "value": 1000,
    "unit": "GB",
    "sourceUrl": "https://download.semiconductor.samsung.com/resources/data-sheet/Samsung_NVMe_SSD_990_PRO_Datasheet_Rev.1.0_10119997003518.pdf",
    "note": "十进制：1 GB = 1000000000 bytes。"
  },
  {
    "code": "originalCapacity",
    "label": "厂商原始容量",
    "value": 1,
    "unit": "TB",
    "sourceUrl": "https://download.semiconductor.samsung.com/resources/data-sheet/Samsung_NVMe_SSD_990_PRO_Datasheet_Rev.1.0_10119997003518.pdf",
    "note": ""
  },
  {
    "code": "formFactor",
    "label": "SSD 物理规格",
    "value": "M.2 2280",
    "unit": "",
    "sourceUrl": "https://download.semiconductor.samsung.com/resources/data-sheet/Samsung_NVMe_SSD_990_PRO_Datasheet_Rev.1.0_10119997003518.pdf",
    "note": ""
  },
  {
    "code": "interface",
    "label": "SSD 接口",
    "value": "PCIe",
    "unit": "",
    "sourceUrl": "https://download.semiconductor.samsung.com/resources/data-sheet/Samsung_NVMe_SSD_990_PRO_Datasheet_Rev.1.0_10119997003518.pdf",
    "note": ""
  },
  {
    "code": "protocol",
    "label": "SSD 协议",
    "value": "NVMe",
    "unit": "",
    "sourceUrl": "https://download.semiconductor.samsung.com/resources/data-sheet/Samsung_NVMe_SSD_990_PRO_Datasheet_Rev.1.0_10119997003518.pdf",
    "note": ""
  },
  {
    "code": "pcieGeneration",
    "label": "PCIe 代际",
    "value": "4.0",
    "unit": "",
    "sourceUrl": "https://download.semiconductor.samsung.com/resources/data-sheet/Samsung_NVMe_SSD_990_PRO_Datasheet_Rev.1.0_10119997003518.pdf",
    "note": ""
  },
  {
    "code": "lanes",
    "label": "PCIe 通道数",
    "value": 4,
    "unit": "",
    "sourceUrl": "https://download.semiconductor.samsung.com/resources/data-sheet/Samsung_NVMe_SSD_990_PRO_Datasheet_Rev.1.0_10119997003518.pdf",
    "note": ""
  }
]', 'catalog-parameters-v1', '2026-10-30'
FROM products WHERE sku = 'SAMSUNG-MZ-V9P1T0BW';

INSERT INTO product_specifications (product_id, description, parameters, data_version, verified_on)
SELECT id, 'WD_BLACK SN850X 1TB，WDS100T2X0E 无散热片版本，M.2 2280 NVMe SSD。 按十进制容量 1TB＝1000GB 记录。',
'[
  {
    "code": "capacity",
    "label": "规范容量",
    "value": 1000,
    "unit": "GB",
    "sourceUrl": "https://www.sandisk.com/products/ssd/internal-ssd/wd-black-sn850x-nvme-ssd?sku=WDS100T2X0E",
    "note": "十进制：1 GB = 1000000000 bytes。"
  },
  {
    "code": "originalCapacity",
    "label": "厂商原始容量",
    "value": 1,
    "unit": "TB",
    "sourceUrl": "https://www.sandisk.com/products/ssd/internal-ssd/wd-black-sn850x-nvme-ssd?sku=WDS100T2X0E",
    "note": ""
  },
  {
    "code": "formFactor",
    "label": "SSD 物理规格",
    "value": "M.2 2280",
    "unit": "",
    "sourceUrl": "https://www.sandisk.com/products/ssd/internal-ssd/wd-black-sn850x-nvme-ssd?sku=WDS100T2X0E",
    "note": ""
  },
  {
    "code": "interface",
    "label": "SSD 接口",
    "value": "PCIe",
    "unit": "",
    "sourceUrl": "https://www.sandisk.com/products/ssd/internal-ssd/wd-black-sn850x-nvme-ssd?sku=WDS100T2X0E",
    "note": ""
  },
  {
    "code": "protocol",
    "label": "SSD 协议",
    "value": "NVMe",
    "unit": "",
    "sourceUrl": "https://www.sandisk.com/products/ssd/internal-ssd/wd-black-sn850x-nvme-ssd?sku=WDS100T2X0E",
    "note": ""
  },
  {
    "code": "pcieGeneration",
    "label": "PCIe 代际",
    "value": "4.0",
    "unit": "",
    "sourceUrl": "https://www.sandisk.com/products/ssd/internal-ssd/wd-black-sn850x-nvme-ssd?sku=WDS100T2X0E",
    "note": ""
  },
  {
    "code": "lanes",
    "label": "PCIe 通道数",
    "value": 4,
    "unit": "",
    "sourceUrl": "https://www.sandisk.com/products/ssd/internal-ssd/wd-black-sn850x-nvme-ssd?sku=WDS100T2X0E",
    "note": ""
  }
]', 'catalog-parameters-v1', '2026-10-30'
FROM products WHERE sku = 'WD-WDS100T2X0E';

INSERT INTO product_specifications (product_id, description, parameters, data_version, verified_on)
SELECT id, 'Corsair RM750e，CP-9020295-NA 版本，750W 全模组 ATX 电源，ATX 3.1；不与旧款 RM750e 混用。',
'[
  {
    "code": "manufacturerPartNumber",
    "label": "厂商型号",
    "value": "CP-9020295-NA",
    "unit": "",
    "sourceUrl": "https://www.corsair.com/us/en/p/psu/cp-9020295-na/rme-series-rm750e-fully-modular-low-noise-atx-power-supply-cp-9020295-na",
    "note": ""
  },
  {
    "code": "ratedPower",
    "label": "电源额定功率",
    "value": 750,
    "unit": "W",
    "sourceUrl": "https://www.corsair.com/us/en/p/psu/cp-9020295-na/rme-series-rm750e-fully-modular-low-noise-atx-power-supply-cp-9020295-na",
    "note": ""
  },
  {
    "code": "formFactor",
    "label": "电源板型",
    "value": "ATX",
    "unit": "",
    "sourceUrl": "https://www.corsair.com/us/en/p/psu/cp-9020295-na/rme-series-rm750e-fully-modular-low-noise-atx-power-supply-cp-9020295-na",
    "note": ""
  },
  {
    "code": "atxVersion",
    "label": "ATX 标准版本",
    "value": "3.1",
    "unit": "",
    "sourceUrl": "https://www.corsair.com/us/en/p/psu/cp-9020295-na/rme-series-rm750e-fully-modular-low-noise-atx-power-supply-cp-9020295-na",
    "note": ""
  },
  {
    "code": "length",
    "label": "电源长度",
    "value": 140,
    "unit": "mm",
    "sourceUrl": "https://www.corsair.com/us/en/p/psu/cp-9020295-na/rme-series-rm750e-fully-modular-low-noise-atx-power-supply-cp-9020295-na",
    "note": ""
  }
]', 'catalog-parameters-v1', '2026-10-30'
FROM products WHERE sku = 'CORSAIR-CP-9020295';

INSERT INTO product_specifications (product_id, description, parameters, data_version, verified_on)
SELECT id, 'DeepCool CC560 黑色中塔机箱，支持 ATX、Micro-ATX 和 Mini-ITX 主板；尺寸限制按厂商标准安装条件记录。',
'[
  {
    "code": "supportedMotherboardFormFactors",
    "label": "支持主板板型",
    "value": [
      "ATX",
      "Micro-ATX",
      "Mini-ITX"
    ],
    "unit": "",
    "sourceUrl": "https://www.deepcool.com/products/Cases/CC560-Mid-Tower-ATX-PC-Case/2021/15216.shtml",
    "note": ""
  },
  {
    "code": "maxGpuLength",
    "label": "显卡有效长度上限",
    "value": 370,
    "unit": "mm",
    "sourceUrl": "https://www.deepcool.com/products/Cases/CC560-Mid-Tower-ATX-PC-Case/2021/15216.shtml",
    "note": "标准安装条件；前置冷排等额外占用需另行核验。"
  },
  {
    "code": "maxCoolerHeight",
    "label": "散热器有效高度上限",
    "value": 163,
    "unit": "mm",
    "sourceUrl": "https://www.deepcool.com/products/Cases/CC560-Mid-Tower-ATX-PC-Case/2021/15216.shtml",
    "note": ""
  },
  {
    "code": "maxPsuLength",
    "label": "电源长度上限",
    "value": 170,
    "unit": "mm",
    "sourceUrl": "https://www.deepcool.com/products/Cases/CC560-Mid-Tower-ATX-PC-Case/2021/15216.shtml",
    "note": ""
  }
]', 'catalog-parameters-v1', '2026-10-30'
FROM products WHERE sku = 'DEEPCOOL-CC560';

UPDATE products SET model = 'Radeon RX 7800 XT (AMD reference)', summary = 'AMD 公版 · Radeon · 16GB' WHERE sku = 'AMD-RX7800XT';
UPDATE products SET model = 'RM750e (CP-9020295-NA)' WHERE sku = 'CORSAIR-CP-9020295';
