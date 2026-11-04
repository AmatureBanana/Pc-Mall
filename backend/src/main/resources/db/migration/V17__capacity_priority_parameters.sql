-- Append only to matching catalog variants. Never overwrite or complete a manually entered profile.
UPDATE products p JOIN product_specifications s ON s.product_id = p.id
JOIN (SELECT 'https://www.kingston.com/datasheets/KF560C36BBEK2-32.pdf' AS source_url,
             '金士顿数据表 4810622A；6000 MT/s EXPO #0 / XMP #1（1.35 V），不是 JEDEC 默认档。' AS profile_note) seed
SET s.parameters = JSON_ARRAY_APPEND(s.parameters,
        '$', JSON_OBJECT('code','timingDataRate','label','时序对应速率','value',6000,'unit','MT/s','sourceUrl',seed.source_url,'note',seed.profile_note),
        '$', JSON_OBJECT('code','casLatency','label','CAS 延迟（CL）','value',36,'unit','周期','sourceUrl',seed.source_url,'note',seed.profile_note),
        '$', JSON_OBJECT('code','trcd','label','tRCD','value',38,'unit','周期','sourceUrl',seed.source_url,'note',seed.profile_note),
        '$', JSON_OBJECT('code','trp','label','tRP','value',38,'unit','周期','sourceUrl',seed.source_url,'note',seed.profile_note)),
    s.data_version = 'catalog-capacity-priority-v1', p.revision = p.revision + 1
WHERE p.sku = 'KINGSTON-KF560C36BBEK2-32' AND p.category = 'MEMORY' AND p.brand = 'Kingston' AND p.model = 'KF560C36BBEK2-32'
  AND JSON_CONTAINS(s.parameters, JSON_ARRAY(JSON_OBJECT('code','totalCapacity','value',32,'unit','GB'),
      JSON_OBJECT('code','generation','value','DDR5'), JSON_OBJECT('code','dataRate','value',6000,'unit','MT/s')))
  AND JSON_SEARCH(s.parameters,'one','timingDataRate',NULL,'$[*].code') IS NULL
  AND JSON_SEARCH(s.parameters,'one','casLatency',NULL,'$[*].code') IS NULL
  AND JSON_SEARCH(s.parameters,'one','trcd',NULL,'$[*].code') IS NULL
  AND JSON_SEARCH(s.parameters,'one','trp',NULL,'$[*].code') IS NULL;

UPDATE products p JOIN product_specifications s ON s.product_id = p.id
JOIN (
    SELECT 'SAMSUNG-MZ-V9P1T0BW' AS sku, 'Samsung' AS brand, 'MZ-V9P1T0BW' AS model, 7450 AS read_rate, 6900 AS write_rate,
           'https://download.semiconductor.samsung.com/resources/data-sheet/samsung_nvme_ssd_990_pro_datasheet_rev.2.0.pdf' AS source_url,
           '990 PRO 1TB 无散热片；数据表 Rev. 2.0 的最高顺序速度，写入采用 TurboWrite 条件。' AS rating_note
    UNION ALL
    SELECT 'WD-WDS100T2X0E','Western Digital','WDS100T2X0E',7300,6300,
           'https://documents.sandisk.com/content/dam/asset-library/en_us/assets/public/western-digital/product/internal-drives/wd-black-ssd/data-sheet-wd-black-sn850x-nvme-ssd.pdf',
           'SN850X 1TB 无散热片；2025 年 7 月数据表列出的最高顺序速度，实际受主机等条件影响。'
) seed ON p.sku = seed.sku AND p.brand = seed.brand AND p.model = seed.model
SET s.parameters = JSON_ARRAY_APPEND(s.parameters,
        '$', JSON_OBJECT('code','sequentialRead','label','标称顺序读取','value',seed.read_rate,'unit','MB/s','sourceUrl',seed.source_url,'note',seed.rating_note),
        '$', JSON_OBJECT('code','sequentialWrite','label','标称顺序写入','value',seed.write_rate,'unit','MB/s','sourceUrl',seed.source_url,'note',seed.rating_note)),
    s.data_version = 'catalog-capacity-priority-v1', p.revision = p.revision + 1
WHERE p.category = 'SSD'
  AND JSON_CONTAINS(s.parameters, JSON_ARRAY(JSON_OBJECT('code','capacity','value',1000,'unit','GB'),
      JSON_OBJECT('code','interface','value','PCIe'), JSON_OBJECT('code','pcieGeneration','value','4.0')))
  AND JSON_SEARCH(s.parameters,'one','sequentialRead',NULL,'$[*].code') IS NULL
  AND JSON_SEARCH(s.parameters,'one','sequentialWrite',NULL,'$[*].code') IS NULL;
