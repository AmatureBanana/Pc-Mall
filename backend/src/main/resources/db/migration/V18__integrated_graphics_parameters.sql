-- Add source-backed integrated graphics details to the existing CPU catalog items.
-- Append only when the product identity matches and none of the optional iGPU fields exist.
-- Keep the existing parameters and product rows; this does not create GPU products.
UPDATE products p JOIN product_specifications s ON s.product_id = p.id
SET s.parameters = JSON_ARRAY_APPEND(s.parameters,
        '$', JSON_OBJECT('code','igpuModel','label','核显型号','value','Intel UHD Graphics 770','unit','',
                         'sourceUrl','https://www.intel.com/content/www/us/en/products/sku/236799/intel-core-i5-processor-14600k-24m-cache-up-to-5-30-ghz/specifications.html',
                         'note','Intel ARK SKU236799 的 GPU Name。'),
        '$', JSON_OBJECT('code','igpuExecutionUnits','label','核显执行单元数','value',32,'unit','EU',
                         'sourceUrl','https://www.intel.com/content/www/us/en/products/sku/236799/intel-core-i5-processor-14600k-24m-cache-up-to-5-30-ghz/specifications.html',
                         'note','Intel ARK Execution Units。'),
        '$', JSON_OBJECT('code','igpuBaseFrequency','label','核显基础频率','value',300,'unit','MHz',
                         'sourceUrl','https://www.intel.com/content/www/us/en/products/sku/236799/intel-core-i5-processor-14600k-24m-cache-up-to-5-30-ghz/specifications.html',
                         'note','Intel ARK Graphics Base Frequency。'),
        '$', JSON_OBJECT('code','igpuMaxFrequency','label','核显最大频率','value',1550,'unit','MHz',
                         'sourceUrl','https://www.intel.com/content/www/us/en/products/sku/236799/intel-core-i5-processor-14600k-24m-cache-up-to-5-30-ghz/specifications.html',
                         'note','Intel ARK Graphics Max Dynamic Frequency 1.55 GHz，换算为 1550 MHz。'),
        '$', JSON_OBJECT('code','igpuOutputInterfaces','label','核显显示接口','value','eDP 1.4b、DisplayPort 1.4a、HDMI 2.1','unit','',
                         'sourceUrl','https://www.intel.com/content/www/us/en/products/sku/236799/intel-core-i5-processor-14600k-24m-cache-up-to-5-30-ghz/specifications.html',
                         'note','处理器支持的显示接口类型；主板是否提供对应接口及实际能力取决于主板实现。'),
        '$', JSON_OBJECT('code','igpuMaxResolutionHdmi','label','核显 HDMI 最大分辨率','value','4096 × 2160 @ 60 Hz','unit','',
                         'sourceUrl','https://www.intel.com/content/www/us/en/products/sku/236799/intel-core-i5-processor-14600k-24m-cache-up-to-5-30-ghz/specifications.html',
                         'note','Intel ARK Max Resolution (HDMI)；实际能力还受主板接口实现影响。'),
        '$', JSON_OBJECT('code','igpuMaxResolutionDisplayPort','label','核显 DisplayPort 最大分辨率','value','7680 × 4320 @ 60 Hz','unit','',
                         'sourceUrl','https://www.intel.com/content/www/us/en/products/sku/236799/intel-core-i5-processor-14600k-24m-cache-up-to-5-30-ghz/specifications.html',
                         'note','Intel ARK Max Resolution (DP)；实际能力还受主板接口实现影响。'),
        '$', JSON_OBJECT('code','igpuMaxResolutionEdp','label','核显 eDP 最大分辨率','value','5120 × 3200 @ 120 Hz','unit','',
                         'sourceUrl','https://www.intel.com/content/www/us/en/products/sku/236799/intel-core-i5-processor-14600k-24m-cache-up-to-5-30-ghz/specifications.html',
                         'note','Intel ARK Max Resolution (eDP - Integrated Flat Panel)。'),
        '$', JSON_OBJECT('code','igpuMaxDisplays','label','核显最大显示器数','value',4,'unit','台',
                         'sourceUrl','https://www.intel.com/content/www/us/en/products/sku/236799/intel-core-i5-processor-14600k-24m-cache-up-to-5-30-ghz/specifications.html',
                         'note','Intel ARK # of Displays Supported；实际连接数量取决于主板实现。')),
    s.data_version = 'catalog-integrated-graphics-v1', s.verified_on = '2026-11-08', p.revision = p.revision + 1
WHERE p.sku = 'INTEL-I5-14600K' AND p.category = 'CPU' AND p.brand = 'Intel' AND p.model = 'i5-14600K'
  AND JSON_CONTAINS(s.parameters, JSON_ARRAY(JSON_OBJECT('code','integratedGraphics','value',true)))
  AND JSON_SEARCH(s.parameters,'one','igpuModel',NULL,'$[*].code') IS NULL
  AND JSON_SEARCH(s.parameters,'one','igpuExecutionUnits',NULL,'$[*].code') IS NULL
  AND JSON_SEARCH(s.parameters,'one','igpuBaseFrequency',NULL,'$[*].code') IS NULL
  AND JSON_SEARCH(s.parameters,'one','igpuMaxFrequency',NULL,'$[*].code') IS NULL
  AND JSON_SEARCH(s.parameters,'one','igpuOutputInterfaces',NULL,'$[*].code') IS NULL
  AND JSON_SEARCH(s.parameters,'one','igpuMaxResolutionHdmi',NULL,'$[*].code') IS NULL
  AND JSON_SEARCH(s.parameters,'one','igpuMaxResolutionDisplayPort',NULL,'$[*].code') IS NULL
  AND JSON_SEARCH(s.parameters,'one','igpuMaxResolutionEdp',NULL,'$[*].code') IS NULL
  AND JSON_SEARCH(s.parameters,'one','igpuMaxDisplays',NULL,'$[*].code') IS NULL;

UPDATE products p JOIN product_specifications s ON s.product_id = p.id
SET s.parameters = JSON_ARRAY_APPEND(s.parameters,
        '$', JSON_OBJECT('code','igpuModel','label','核显型号','value','AMD Radeon Graphics','unit','',
                         'sourceUrl','https://www.amd.com/en/products/processors/desktops/ryzen/7000-series/amd-ryzen-7-7800x3d.html',
                         'note','AMD 产品规格 Graphics Model。'),
        '$', JSON_OBJECT('code','igpuCores','label','核显图形核心数','value',2,'unit','个',
                         'sourceUrl','https://www.amd.com/en/products/processors/desktops/ryzen/7000-series/amd-ryzen-7-7800x3d.html',
                         'note','AMD 产品规格 Graphics Core Count。'),
        '$', JSON_OBJECT('code','igpuFrequency','label','核显图形频率','value',2200,'unit','MHz',
                         'sourceUrl','https://www.amd.com/en/products/processors/desktops/ryzen/7000-series/amd-ryzen-7-7800x3d.html',
                         'note','AMD 产品规格 Graphics Frequency；未改写为未标注的最大频率。'),
        '$', JSON_OBJECT('code','igpuOutputInterfaces','label','核显显示接口','value','USB Type-C DisplayPort Alternate Mode','unit','',
                         'sourceUrl','https://www.amd.com/en/products/processors/desktops/ryzen/7000-series/amd-ryzen-7-7800x3d.html',
                         'note','处理器规格列出该显示模式；主板需实际提供兼容的 USB-C 接口。')),
    s.data_version = 'catalog-integrated-graphics-v1', s.verified_on = '2026-11-08', p.revision = p.revision + 1
WHERE p.sku = 'AMD-R7-7800X3D' AND p.category = 'CPU' AND p.brand = 'AMD' AND p.model = 'Ryzen 7 7800X3D'
  AND JSON_CONTAINS(s.parameters, JSON_ARRAY(JSON_OBJECT('code','integratedGraphics','value',true)))
  AND JSON_SEARCH(s.parameters,'one','igpuModel',NULL,'$[*].code') IS NULL
  AND JSON_SEARCH(s.parameters,'one','igpuCores',NULL,'$[*].code') IS NULL
  AND JSON_SEARCH(s.parameters,'one','igpuFrequency',NULL,'$[*].code') IS NULL
  AND JSON_SEARCH(s.parameters,'one','igpuOutputInterfaces',NULL,'$[*].code') IS NULL;
