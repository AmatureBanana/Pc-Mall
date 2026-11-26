-- Geekbench 7 CPU references for the expanded desktop catalogue, verified 2026-11-25.
-- A per-model chart aggregate is preferred. Where absent, link the exact public CPU test.
-- Preserve existing scores and all unrelated specifications.
UPDATE products p JOIN product_specifications s ON s.product_id = p.id
JOIN (
    SELECT 'CATALOG-CPU-58D04803B84E808E' AS sku, 'AMD' AS brand, 'Ryzen 7 9800X3D' AS model, 2965 AS single_core, 18719 AS multi_core, NULL AS run_url, NULL AS run_environment
    UNION ALL SELECT 'CATALOG-CPU-98057BB853A795B7', 'AMD', 'Ryzen 5 7600X', 2593, 13593, NULL, NULL
    UNION ALL SELECT 'CATALOG-CPU-CD0FCA0695AD3188', 'AMD', 'Ryzen 5 9600X', 2923, 14364, NULL, NULL
    UNION ALL SELECT 'CATALOG-CPU-C6F8FCAB09A307CF', 'AMD', 'Ryzen 7 7700X', 2501, 14957, NULL, NULL
    UNION ALL SELECT 'CATALOG-CPU-99AF179A541698B0', 'AMD', 'Ryzen 9 9950X3D', 3054, 26392, NULL, NULL
    UNION ALL SELECT 'CATALOG-CPU-38489795DF22E315', 'AMD', 'Ryzen 7 9700X', 3006, 17640, NULL, NULL
    UNION ALL SELECT 'CATALOG-CPU-0B5866A484C10E88', 'Intel', 'Core i7-14700K', 2578, 20807, NULL, NULL
    UNION ALL SELECT 'CATALOG-CPU-D53EDAE417F8B4AD', 'Intel', 'Core i9-14900K', 2658, 21983, NULL, NULL
    UNION ALL SELECT 'CATALOG-CPU-29459A886D5F9893', 'AMD', 'Ryzen 5 7600', 2476, 12920, NULL, NULL
    UNION ALL SELECT 'CATALOG-CPU-96800170AFF94313', 'AMD', 'Ryzen 9 7900X', 2608, 19212, NULL, NULL
    UNION ALL SELECT 'CATALOG-CPU-FD4E5EC79A6E5AF6', 'AMD', 'Ryzen 9 9900X', 3019, 22255, NULL, NULL
    UNION ALL SELECT 'CATALOG-CPU-425FE325E72BE7CB', 'Intel', 'Core Ultra 7 265K', 2737, 23508, NULL, NULL
    UNION ALL SELECT 'CATALOG-CPU-8B5BCBB398C9FF82', 'Intel', 'Core i5-14400F', 2066, 11366, NULL, NULL
    UNION ALL SELECT 'CATALOG-CPU-22534304D489FD33', 'AMD', 'Ryzen 9 9950X', 3070, 26148, NULL, NULL
    UNION ALL SELECT 'CATALOG-CPU-D86A3B2A170AF58B', 'Intel', 'Core Ultra 9 285K', 2805, 25598, NULL, NULL
    UNION ALL SELECT 'CATALOG-CPU-DD20D0156DEBE425', 'AMD', 'Ryzen 5 7600X3D', 2371, 11534, 'https://browser.geekbench.com/v7/cpu/812463', 'Geekbench 7.1.0 Linux'
    UNION ALL SELECT 'CATALOG-CPU-0C9D3601188D67D0', 'AMD', 'Ryzen 7 7700', 2509, 15223, NULL, NULL
    UNION ALL SELECT 'CATALOG-CPU-FB082654CBAB12FC', 'Intel', 'Core i5-13400F', 1995, 11088, NULL, NULL
    UNION ALL SELECT 'CATALOG-CPU-DB7FC5DC1293FFDA', 'AMD', 'Ryzen 5 8400F', 2236, 10331, 'https://browser.geekbench.com/v7/cpu/906853', 'Geekbench 7.1.0 Windows'
    UNION ALL SELECT 'CATALOG-CPU-AABDAE921C7254F5', 'AMD', 'Ryzen 5 8500G', 2145, 9149, 'https://browser.geekbench.com/v7/cpu/875358', 'Geekbench 7.1.0 Windows'
    UNION ALL SELECT 'CATALOG-CPU-D4D976D5AC2A6CCF', 'AMD', 'Ryzen 9 7950X', 2614, 22611, NULL, NULL
    UNION ALL SELECT 'CATALOG-CPU-C623C9F3237694C7', 'AMD', 'Ryzen 5 8600G', 2314, 11234, 'https://browser.geekbench.com/v7/cpu/958663', 'Geekbench 7.1.0 Linux'
    UNION ALL SELECT 'CATALOG-CPU-E81431E7F674F1FF', 'Intel', 'Core i7-14700KF', 2605, 21224, NULL, NULL
    UNION ALL SELECT 'CATALOG-CPU-6C0EFBBF46121D0B', 'Intel', 'Core Ultra 7 265KF', 2752, 23134, NULL, NULL
    UNION ALL SELECT 'CATALOG-CPU-B5CC885E79F8CC17', 'AMD', 'Ryzen 9 9900X3D', 2799, 20869, NULL, NULL
    UNION ALL SELECT 'CATALOG-CPU-B327575BC5AA97F9', 'AMD', 'Ryzen 9 7950X3D', 2537, 22214, NULL, NULL
    UNION ALL SELECT 'CATALOG-CPU-6C00B6EE49140AB0', 'AMD', 'Ryzen 7 8700G', 2269, 13662, NULL, NULL
    UNION ALL SELECT 'CATALOG-CPU-74CA758CE5239B81', 'Intel', 'Core i9-14900KF', 2667, 23008, NULL, NULL
    UNION ALL SELECT 'CATALOG-CPU-F4BEF9DDA4D34193', 'Intel', 'Core i7-13700K', 2479, 19249, NULL, NULL
    UNION ALL SELECT 'CATALOG-CPU-BD0E071A36815DA5', 'AMD', 'Ryzen 7 8700F', 2324, 13583, NULL, NULL
    UNION ALL SELECT 'CATALOG-CPU-A927DEE2EE053AA8', 'Intel', 'Core i9-14900KS', 2906, 26249, NULL, NULL
    UNION ALL SELECT 'CATALOG-CPU-C53FA2556C728FAB', 'Intel', 'Core i5-13600KF', 2355, 16550, NULL, NULL
    UNION ALL SELECT 'CATALOG-CPU-FAD1FA811FE9756C', 'Intel', 'Core i7-14700F', 2316, 18048, NULL, NULL
    UNION ALL SELECT 'CATALOG-CPU-92B196C0233BCE77', 'AMD', 'Ryzen 9 7900', 2536, 18485, NULL, NULL
    UNION ALL SELECT 'CATALOG-CPU-67824D9EDBA14F10', 'Intel', 'Core i9-13900K', 2610, 22629, NULL, NULL
    UNION ALL SELECT 'CATALOG-CPU-0F932008776E9557', 'Intel', 'Core i3-14100F', 2107, 7886, NULL, NULL
    UNION ALL SELECT 'CATALOG-CPU-D1B8BCC24A4A8359', 'Intel', 'Core i5-13600K', 2373, 15991, NULL, NULL
    UNION ALL SELECT 'CATALOG-CPU-C45B444DA24B95BA', 'Intel', 'Core Ultra 5 245K', 2617, 19492, 'https://browser.geekbench.com/v7/cpu/146096', 'Geekbench 7.0.0 Windows'
    UNION ALL SELECT 'CATALOG-CPU-60449E8388EDDE51', 'Intel', 'Core i7-13700KF', 2461, 18519, NULL, NULL
    UNION ALL SELECT 'CATALOG-CPU-AD20522CCAD613CF', 'Intel', 'Core i5-14400', 2018, 10840, NULL, NULL
    UNION ALL SELECT 'CATALOG-CPU-EE2929693094E53C', 'Intel', 'Core Ultra 5 225F', 2125, 11849, 'https://browser.geekbench.com/v7/cpu/921808', 'Geekbench 7.1.0 Windows'
    UNION ALL SELECT 'CATALOG-CPU-95928C461B275E06', 'Intel', 'Core i3-14100', 2090, 7195, NULL, NULL
    UNION ALL SELECT 'CATALOG-CPU-E88D2A95EB7C6A78', 'Intel', 'Core i3-13100F', 2052, 7700, NULL, NULL
    UNION ALL SELECT 'CATALOG-CPU-BFC60A66828779EB', 'Intel', 'Core i5-13400', 2052, 11258, NULL, NULL
    UNION ALL SELECT 'CATALOG-CPU-AF14709F991D0C21', 'Intel', 'Core i7-13700F', 2242, 15462, NULL, NULL
    UNION ALL SELECT 'CATALOG-CPU-75D1661C920772DC', 'Intel', 'Core i9-13900KF', 2507, 21311, NULL, NULL
    UNION ALL SELECT 'CATALOG-CPU-D8E9BDAAF557BBB5', 'AMD', 'Ryzen 5 9600', 2710, 13649, NULL, NULL
    UNION ALL SELECT 'CATALOG-CPU-94ECECCD554667C9', 'Intel', 'Core Ultra 5 235', 2661, 16108, 'https://browser.geekbench.com/v7/cpu/compare/727915?baseline=684680', 'Geekbench 7.1.0 Linux'
    UNION ALL SELECT 'CATALOG-CPU-520E67DA84C4CEF1', 'Intel', 'Core Ultra 5 225', 2492, 11676, 'https://browser.geekbench.com/v7/cpu/compare/457613?baseline=451232', 'Geekbench 7.0.0 Windows'
    UNION ALL SELECT 'CATALOG-CPU-CC552DB58F94C94F', 'Intel', 'Core i7-14700', 2397, 16954, NULL, NULL
    UNION ALL SELECT 'CATALOG-CPU-4D94B89B7DFCBD39', 'Intel', 'Core i5-13500', 2172, 13647, NULL, NULL
    UNION ALL SELECT 'CATALOG-CPU-60077518B1501ADC', 'Intel', 'Core Ultra 9 285', 2825, 20194, 'https://beta.geekbench.com/v7/cpu/65903', 'Geekbench 7.0.0 Windows'
    UNION ALL SELECT 'CATALOG-CPU-44D75A7CFB73769E', 'Intel', 'Core i5-14500', 2109, 12319, NULL, NULL
    UNION ALL SELECT 'CATALOG-CPU-FC7591B507CA4073', 'Intel', 'Core Ultra 7 265', 2842, 21974, 'https://browser.geekbench.com/v7/cpu/compare/730336?baseline=5431', 'Geekbench 7.1.0 Linux'
    UNION ALL SELECT 'CATALOG-CPU-E860627E4714AAD7', 'Intel', 'Core i9-13900KS', 2809, 24630, NULL, NULL
    UNION ALL SELECT 'CATALOG-CPU-DE56B224874C0106', 'Intel', 'Core Ultra 7 265F', 2629, 21892, 'https://browser.geekbench.com/v7/cpu/956564', 'Geekbench 7.1.0 Windows'
    UNION ALL SELECT 'CATALOG-CPU-E879E79914EFF58E', 'Intel', 'Core Ultra 5 245KF', 2974, 21097, 'https://browser.geekbench.com/v7/cpu/495022', 'Geekbench 7.1.0 Linux'
    UNION ALL SELECT 'CATALOG-CPU-F37DDD313A70A124', 'Intel', 'Core i3-13100', 1987, 7303, NULL, NULL
    UNION ALL SELECT 'CATALOG-CPU-E207E17FF73133D9', 'Intel', 'Core i7-13700', 2311, 15854, NULL, NULL
    UNION ALL SELECT 'CATALOG-CPU-77755851CF93D3BE', 'Intel', 'Core i9-14900F', 2567, 19633, NULL, NULL
) seed ON p.sku = seed.sku AND p.brand = seed.brand AND p.model = seed.model
CROSS JOIN (
    SELECT 'https://browser.geekbench.com/processor-benchmarks' AS chart_url,
           'Geekbench 7 官方处理器总榜型号汇总，2026-11-25 核对。' AS chart_note
) benchmark
SET s.parameters = JSON_ARRAY_APPEND(s.parameters,
        '$', JSON_OBJECT('code', 'geekbench7SingleCore', 'label', 'Geekbench 7 单核', 'value', seed.single_core, 'unit', '分',
            'sourceUrl', COALESCE(seed.run_url, benchmark.chart_url),
            'note', COALESCE(CONCAT(seed.run_environment, ' 单次公开测试，2026-11-25 核对。'), benchmark.chart_note)),
        '$', JSON_OBJECT('code', 'geekbench7MultiCore', 'label', 'Geekbench 7 多核', 'value', seed.multi_core, 'unit', '分',
            'sourceUrl', COALESCE(seed.run_url, benchmark.chart_url),
            'note', COALESCE(CONCAT(seed.run_environment, ' 单次公开测试，2026-11-25 核对。'), benchmark.chart_note))),
    s.data_version = 'catalog-geekbench7-v2', p.revision = p.revision + 1
WHERE p.category = 'CPU'
  AND JSON_SEARCH(s.parameters, 'one', 'geekbench7SingleCore', NULL, '$[*].code') IS NULL
  AND JSON_SEARCH(s.parameters, 'one', 'geekbench7MultiCore', NULL, '$[*].code') IS NULL;
