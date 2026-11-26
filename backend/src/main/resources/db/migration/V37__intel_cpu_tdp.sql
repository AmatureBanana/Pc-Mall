-- Intel documents Processor Base Power as TDP: https://www.intel.com/content/www/us/en/support/articles/000089297/processors.html
UPDATE products p JOIN product_specifications s ON s.product_id = p.id
JOIN (
    SELECT 'INTEL-I5-14600K' AS sku, 'i5-14600K' AS model,
           'https://www.intel.com/content/www/us/en/products/sku/236799/intel-core-i5-processor-14600k-24m-cache-up-to-5-30-ghz/specifications.html' AS source_url
    UNION ALL
    SELECT 'INTEL-I5-14600KF', 'i5-14600KF',
           'https://www.intel.com/content/www/us/en/products/sku/236778/intel-core-i5-processor-14600kf-24m-cache-up-to-5-30-ghz/specifications.html'
) seed ON p.sku = seed.sku AND p.model = seed.model
SET s.parameters = JSON_ARRAY_APPEND(s.parameters, '$', JSON_OBJECT(
        'code', 'tdp', 'label', 'TDP', 'value', 125, 'unit', 'W', 'sourceUrl', seed.source_url,
        'note', 'Intel Processor Base Power（基础功耗），按 Intel 官方说明归入 TDP。')),
    s.data_version = 'catalog-intel-tdp-v1', p.revision = p.revision + 1
WHERE p.category = 'CPU' AND p.brand = 'Intel'
  AND JSON_SEARCH(s.parameters, 'one', 'tdp', NULL, '$[*].code') IS NULL;
