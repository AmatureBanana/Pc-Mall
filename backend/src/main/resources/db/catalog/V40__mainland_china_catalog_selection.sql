-- Review scope and channel references: docs/catalog-expansion.md.
-- Retain the product records so existing cart and order references still resolve.
UPDATE products
SET status = 'OFF_SALE', revision = revision + 1
WHERE status = 'ON_SALE'
  AND (
      (data_version = 'catalog-expansion-2025-v1' AND sku IN (
          -- GeForce RTX 5090 32 GB, distinct from the models released for mainland China.
          'CATALOG-GPU-1F4B5A3A3F1F688F',
          'CATALOG-GPU-57D6060221AB57C3',
          'CATALOG-GPU-66E918E4A91FF6D7',
          'CATALOG-GPU-680D9E718FF89970',
          'CATALOG-GPU-884F65A64961601F',
          'CATALOG-GPU-9E5EEF1AF64AE578',
          'CATALOG-GPU-08B1017C593C6B48',
          'CATALOG-GPU-8138024F95F03EB6',
          'CATALOG-GPU-693F0F397A526B68',
          -- RTX 4090 non-D models and RTX 5070 Founders Edition.
          'CATALOG-GPU-696FA964B7BC4C16',
          'CATALOG-GPU-FF1B2C1104E316BC',
          'CATALOG-GPU-1C4790009104EA58',
          -- Sabrent Rocket 5, 2 TB and 4 TB.
          'CATALOG-SSD-3A9103AD6323B8A8',
          'CATALOG-SSD-2B4D2C6DCE732B91'
      ))
      OR (data_version = 'catalog-parts-2026-v1' AND sku IN (
          'RECENT-SCYTHE-MUGEN-6',
          'RECENT-SCYTHE-MUGEN-6-DUAL-FAN-BLACK-EDITION'
      ))
  );
