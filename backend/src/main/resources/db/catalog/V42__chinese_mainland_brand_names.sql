-- Store the Chinese names for the mainland assortment imported by V41.
-- Keep the model, SKU, specifications and inventory intact.
UPDATE products p
JOIN JSON_TABLE('[
    ["Great Wall","长城"], ["DeepCool","九州风神"], ["Phanteks","追风者"], ["Montech","君主科技"],
    ["Thermalright","利民"], ["KLEVV","科赋"], ["Corsair","海盗船"]
]', '$[*]' COLUMNS (
    original_brand VARCHAR(60) PATH '$[0]',
    chinese_brand VARCHAR(60) PATH '$[1]'
)) alias ON p.brand = alias.original_brand
LEFT JOIN product_specifications s ON s.product_id = p.id
SET p.brand = alias.chinese_brand,
    p.name = CASE
        WHEN LEFT(p.name, CHAR_LENGTH(alias.original_brand) + 1) = CONCAT(alias.original_brand, ' ')
        THEN CONCAT(alias.chinese_brand, SUBSTRING(p.name, CHAR_LENGTH(alias.original_brand) + 1))
        ELSE p.name
    END,
    s.description = CASE
        WHEN LEFT(s.description, CHAR_LENGTH(alias.original_brand) + 1) = CONCAT(alias.original_brand, ' ')
        THEN CONCAT(alias.chinese_brand, SUBSTRING(s.description, CHAR_LENGTH(alias.original_brand) + 1))
        ELSE s.description
    END,
    p.revision = p.revision + 1
WHERE p.data_version = 'catalog-mainland-2026-v1';
