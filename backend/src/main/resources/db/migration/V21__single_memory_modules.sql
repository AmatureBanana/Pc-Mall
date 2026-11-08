UPDATE product_categories SET label = '显卡' WHERE code = 'GPU';

-- Preserve SKU identity for saved references. Only single modules remain on sale.
UPDATE products p
LEFT JOIN product_specifications s ON s.product_id = p.id
SET p.status = 'OFF_SALE', p.revision = p.revision + 1
WHERE p.category = 'MEMORY' AND p.status = 'ON_SALE'
  AND (p.sku = 'KINGSTON-KF560C36BBEK2-32'
       OR COALESCE(JSON_CONTAINS(s.parameters, JSON_OBJECT('code','stickCount','value',1)), 0) = 0);

INSERT INTO products (sku, name, category, brand, model, summary, image_url, price, stock,
                      status, source_url, price_note, data_version)
SELECT 'KINGSTON-KF560C36BBE2-16', '金士顿 FURY Beast 16GB', 'MEMORY', 'Kingston',
       'KF560C36BBE2-16', 'DDR5 · 6000 MT/s · 单根', '/images/memory.svg', 449.50, 40, 'ON_SALE',
       'https://www.kingston.com/datasheets/KF560C36BBE2-16.pdf',
       'CNY 单根演示价格与库存，非实时市场报价', 'catalog-single-memory-v1'
WHERE NOT EXISTS (SELECT 1 FROM products WHERE sku = 'KINGSTON-KF560C36BBE2-16');

INSERT INTO product_specifications (product_id, description, parameters, data_version, verified_on)
SELECT p.id, '16 GB DDR5 DIMM，按单根销售。标称 6000 MT/s，JEDEC 默认 4800 MT/s。',
       JSON_ARRAY(
           JSON_OBJECT('code','generation','label','内存代际','value','DDR5','unit','','sourceUrl',p.source_url,'note','金士顿数据表 4811557A'),
           JSON_OBJECT('code','totalCapacity','label','内存容量','value',16,'unit','GB','sourceUrl',p.source_url,'note','金士顿数据表 4811557A'),
           JSON_OBJECT('code','stickCount','label','实际内存条数','value',1,'unit','条','sourceUrl',p.source_url,'note','金士顿数据表 4811557A'),
           JSON_OBJECT('code','capacityPerStick','label','单条容量','value',16,'unit','GB','sourceUrl',p.source_url,'note','金士顿数据表 4811557A'),
           JSON_OBJECT('code','dataRate','label','标称数据速率','value',6000,'unit','MT/s','sourceUrl',p.source_url,'note','金士顿数据表 4811557A'),
           JSON_OBJECT('code','jedecDataRate','label','默认数据速率','value',4800,'unit','MT/s','sourceUrl',p.source_url,'note','金士顿数据表 4811557A'),
           JSON_OBJECT('code','timingDataRate','label','时序对应速率','value',6000,'unit','MT/s','sourceUrl',p.source_url,'note','金士顿数据表 4811557A'),
           JSON_OBJECT('code','casLatency','label','CAS 延迟（CL）','value',36,'unit','周期','sourceUrl',p.source_url,'note','金士顿数据表 4811557A'),
           JSON_OBJECT('code','trcd','label','tRCD','value',44,'unit','周期','sourceUrl',p.source_url,'note','金士顿数据表 4811557A'),
           JSON_OBJECT('code','trp','label','tRP','value',44,'unit','周期','sourceUrl',p.source_url,'note','金士顿数据表 4811557A')
       ), p.data_version, '2026-11-09'
FROM products p
WHERE p.sku = 'KINGSTON-KF560C36BBE2-16' AND p.data_version = 'catalog-single-memory-v1';
