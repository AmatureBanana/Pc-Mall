-- Desktop CPU aggregate scores from the Geekbench 7 chart captured on 2026-11-18.
-- Keep existing Blender references and any manually recorded benchmark parameters.
UPDATE products p JOIN product_specifications s ON s.product_id = p.id
JOIN (
    SELECT 'INTEL-I5-14600K' AS sku, 'Intel' AS brand, 'i5-14600K' AS model,
           2512 AS single_core, 17129 AS multi_core
    UNION ALL SELECT 'INTEL-I5-14600KF', 'Intel', 'i5-14600KF', 2458, 17209
    UNION ALL SELECT 'AMD-R7-7800X3D', 'AMD', 'Ryzen 7 7800X3D', 2437, 15609
) seed ON p.sku = seed.sku AND p.brand = seed.brand AND p.model = seed.model
CROSS JOIN (
    SELECT 'https://browser.geekbench.com/processor-benchmarks' AS source_url,
           'Geekbench 7 CPU 官方榜单社区提交平均分，采集于 2026-11-18；按同一榜单快照和准确型号记录。' AS reference_note
) benchmark
SET s.parameters = JSON_ARRAY_APPEND(s.parameters,
        '$', JSON_OBJECT('code','geekbench7SingleCore','label','Geekbench 7 单核','value',seed.single_core,'unit','分',
                         'sourceUrl',benchmark.source_url,'note',benchmark.reference_note),
        '$', JSON_OBJECT('code','geekbench7MultiCore','label','Geekbench 7 多核','value',seed.multi_core,'unit','分',
                         'sourceUrl',benchmark.source_url,'note',benchmark.reference_note)),
    s.data_version = 'catalog-geekbench7-v1', p.revision = p.revision + 1
WHERE p.category = 'CPU'
  AND JSON_SEARCH(s.parameters,'one','geekbench7SingleCore',NULL,'$[*].code') IS NULL
  AND JSON_SEARCH(s.parameters,'one','geekbench7MultiCore',NULL,'$[*].code') IS NULL;
