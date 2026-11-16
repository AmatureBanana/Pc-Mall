-- The original AMD product page exposes TDP only. Use the model-specific stock
-- PPT limit documented by the original tester as a conservative power estimate,
-- not as measured consumption or a value calculated from TDP. Preserve admin edits.
UPDATE products p JOIN product_specifications s ON s.product_id=p.id
SET s.parameters=JSON_SET(s.parameters,
        REPLACE(JSON_UNQUOTE(JSON_SEARCH(s.parameters,'one','peakPowerEstimate',NULL,'$[*].code')),'.code','.value'),162,
        REPLACE(JSON_UNQUOTE(JSON_SEARCH(s.parameters,'one','peakPowerEstimate',NULL,'$[*].code')),'.code','.sourceUrl'),
        'https://skatterbencher.com/2023/04/05/skatterbencher-60-amd-ryzen-7-7800x3d-overclocked-to-5400-mhz/',
        REPLACE(JSON_UNQUOTE(JSON_SEARCH(s.parameters,'one','peakPowerEstimate',NULL,'$[*].code')),'.code','.note'),
        '默认PPT限额162W作为保守功耗估算；一手测试记录，非AMD产品页标称峰值，非实测持续功耗，不由120W TDP换算；不覆盖手动超频或提高功率限制。'),
    s.data_version='catalog-7800x3d-ppt-v1',s.verified_on='2026-11-16',p.revision=p.revision+1
WHERE p.sku='AMD-R7-7800X3D'
  AND JSON_TYPE(JSON_EXTRACT(s.parameters,
      REPLACE(JSON_UNQUOTE(JSON_SEARCH(s.parameters,'one','peakPowerEstimate',NULL,'$[*].code')),'.code','.value')))='NULL';
