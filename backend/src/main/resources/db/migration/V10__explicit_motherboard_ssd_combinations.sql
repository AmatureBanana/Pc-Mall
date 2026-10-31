-- Preserve V4's verified facts and date. Both named boards have only PCIe/NVMe M.2 combinations.
-- Do not apply this conversion to future mixed-interface boards.
UPDATE product_specifications s JOIN products p ON p.id=s.product_id
SET s.parameters=JSON_ARRAY_APPEND(s.parameters, '$', JSON_OBJECT(
    'code','ssdSlots','label','SSD 有效安装组合','unit','',
    'value',JSON_ARRAY(
        JSON_OBJECT('formFactor','M.2 2242','interface','PCIe','protocol','NVMe'),
        JSON_OBJECT('formFactor','M.2 2260','interface','PCIe','protocol','NVMe'),
        JSON_OBJECT('formFactor','M.2 2280','interface','PCIe','protocol','NVMe')),
    'sourceUrl',p.source_url,'note','支持的有效组合；不表示每种规格有独立插槽。M.2 插槽总数仍为2。')),
    s.data_version='catalog-slots-v2'
WHERE p.sku='ASUS-TUF-B760M-PLUS-WIFI';

UPDATE product_specifications s JOIN products p ON p.id=s.product_id
SET s.parameters=JSON_ARRAY_APPEND(s.parameters, '$', JSON_OBJECT(
    'code','ssdSlots','label','SSD 有效安装组合','unit','',
    'value',JSON_ARRAY(
        JSON_OBJECT('formFactor','M.2 2260','interface','PCIe','protocol','NVMe'),
        JSON_OBJECT('formFactor','M.2 2280','interface','PCIe','protocol','NVMe')),
    'sourceUrl',p.source_url,'note','支持的有效组合；不表示每种规格有独立插槽。M.2 插槽总数仍为2。')),
    s.data_version='catalog-slots-v2'
WHERE p.sku='MSI-MAG-B650M-MORTAR-WIFI';
