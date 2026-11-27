UPDATE products
SET summary = '风冷散热器 · 120mm风扇',
    revision = revision + 1
WHERE sku = 'DEEPCOOL-AK400'
  AND category = 'COOLER'
  AND summary = '塔式风冷 · 四热管 · 120mm风扇';
