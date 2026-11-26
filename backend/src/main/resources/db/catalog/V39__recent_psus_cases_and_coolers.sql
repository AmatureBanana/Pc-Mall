-- Recent consumer PSUs, cases and CPU air coolers. Sources and selection: docs/catalog-expansion.md.
-- Add catalog entries without replacing existing products or their specifications.
START TRANSACTION;

-- Each row ends with a category-specific array of parameters:
-- PSU [rated power, form factor, ATX version, length or null]
-- CASE [board formats, GPU clearance, cooler clearance, PSU clearance]
-- COOLER [supported sockets, overall height, fan diameter]
SET @recent_parts = '[
["RECENT-CORSAIR-RM650E-2025","PSU","Corsair","RM650e (2025)","650 W · ATX 3.1",559,"https://www.corsair.com/us/en/explorer/diy-builder/power-supply-units/corsair-rme-2025-vs-rme-2023-whats-new/",[650,"ATX","3.1",null]],
["RECENT-CORSAIR-RM850E-2025","PSU","Corsair","RM850e (2025)","850 W · ATX 3.1",709,"https://www.corsair.com/us/en/explorer/diy-builder/power-supply-units/corsair-rme-2025-vs-rme-2023-whats-new/",[850,"ATX","3.1",null]],
["RECENT-CORSAIR-RM1000E-2025","PSU","Corsair","RM1000e (2025)","1000 W · ATX 3.1",819,"https://www.corsair.com/us/en/explorer/diy-builder/power-supply-units/corsair-rme-2025-vs-rme-2023-whats-new/",[1000,"ATX","3.1",null]],
["RECENT-CORSAIR-RM750X-2024","PSU","Corsair","RM750x (2024)","750 W · ATX 3.1",759,"https://www.corsair.com/newsroom/press-release/corsair-updates-popular-rmx-series-low-noise-power-supplies-with-atx-3-1-compliance-and-native-atx-12v-2x6-connector",[750,"ATX","3.1",null]],
["RECENT-CORSAIR-RM850X-2024","PSU","Corsair","RM850x (2024)","850 W · ATX 3.1",839,"https://www.corsair.com/newsroom/press-release/corsair-updates-popular-rmx-series-low-noise-power-supplies-with-atx-3-1-compliance-and-native-atx-12v-2x6-connector",[850,"ATX","3.1",null]],
["RECENT-CORSAIR-RM1000X-2024","PSU","Corsair","RM1000x (2024)","1000 W · ATX 3.1",949,"https://www.corsair.com/newsroom/press-release/corsair-updates-popular-rmx-series-low-noise-power-supplies-with-atx-3-1-compliance-and-native-atx-12v-2x6-connector",[1000,"ATX","3.1",null]],
["RECENT-CORSAIR-RM750X-SHIFT-WHITE-2024","PSU","Corsair","RM750x SHIFT White (2024)","750 W · ATX 3.0",839,"https://www.corsair.com/newsroom/press-release/corsair-expands-side-mounted-connection-psu-family-with-new-white-rmx-shift",[750,"ATX","3.0",null]],
["RECENT-CORSAIR-RM850X-SHIFT-WHITE-2024","PSU","Corsair","RM850x SHIFT White (2024)","850 W · ATX 3.0",919,"https://www.corsair.com/newsroom/press-release/corsair-expands-side-mounted-connection-psu-family-with-new-white-rmx-shift",[850,"ATX","3.0",null]],
["RECENT-CORSAIR-RM1000X-SHIFT-WHITE-2024","PSU","Corsair","RM1000x SHIFT White (2024)","1000 W · ATX 3.0",1029,"https://www.corsair.com/newsroom/press-release/corsair-expands-side-mounted-connection-psu-family-with-new-white-rmx-shift",[1000,"ATX","3.0",null]],
["RECENT-CORSAIR-RM1200X-SHIFT-WHITE-2024","PSU","Corsair","RM1200x SHIFT White (2024)","1200 W · ATX 3.0",1179,"https://www.corsair.com/newsroom/press-release/corsair-expands-side-mounted-connection-psu-family-with-new-white-rmx-shift",[1200,"ATX","3.0",null]],
["RECENT-CORSAIR-RM750X-SHIFT-2025","PSU","Corsair","RM750x SHIFT (2025)","750 W · ATX 3.1",839,"https://www.corsair.com/newsroom/press-release/corsair-launches-second-generation-rmx-shift-psus-with-updated-cables-and-12v-2x6-gpu-support",[750,"ATX","3.1",null]],
["RECENT-CORSAIR-RM850X-SHIFT-2025","PSU","Corsair","RM850x SHIFT (2025)","850 W · ATX 3.1",919,"https://www.corsair.com/newsroom/press-release/corsair-launches-second-generation-rmx-shift-psus-with-updated-cables-and-12v-2x6-gpu-support",[850,"ATX","3.1",null]],
["RECENT-CORSAIR-RM1000X-SHIFT-2025","PSU","Corsair","RM1000x SHIFT (2025)","1000 W · ATX 3.1",1029,"https://www.corsair.com/newsroom/press-release/corsair-launches-second-generation-rmx-shift-psus-with-updated-cables-and-12v-2x6-gpu-support",[1000,"ATX","3.1",null]],
["RECENT-CORSAIR-HX1000I-SHIFT-2025","PSU","Corsair","HX1000i SHIFT (2025)","1000 W · ATX 3.1",1449,"https://www.corsair.com/newsroom/press-release/corsair-unveils-platinum-rated-hxi-shift-psus-with-icue-link-system-hub-and-pcie-5-1-support",[1000,"ATX","3.1",null]],
["RECENT-CORSAIR-HX1200I-SHIFT-2025","PSU","Corsair","HX1200i SHIFT (2025)","1200 W · ATX 3.1",1599,"https://www.corsair.com/newsroom/press-release/corsair-unveils-platinum-rated-hxi-shift-psus-with-icue-link-system-hub-and-pcie-5-1-support",[1200,"ATX","3.1",null]],
["RECENT-CORSAIR-HX1500I-SHIFT-2025","PSU","Corsair","HX1500i SHIFT (2025)","1500 W · ATX 3.1",1829,"https://www.corsair.com/newsroom/press-release/corsair-unveils-platinum-rated-hxi-shift-psus-with-icue-link-system-hub-and-pcie-5-1-support",[1500,"ATX","3.1",null]],
["RECENT-CORSAIR-SF750-2024","PSU","Corsair","SF750 (2024)","750 W · SFX",1039,"https://www.corsair.com/us/en/explorer/diy-builder/power-supply-units/sf750sf850sf1000-platinum-atx-31-everything-you-need-to-know/",[750,"SFX","3.1",null]],
["RECENT-CORSAIR-SF850-2024","PSU","Corsair","SF850 (2024)","850 W · SFX",1119,"https://www.corsair.com/us/en/explorer/diy-builder/power-supply-units/sf750sf850sf1000-platinum-atx-31-everything-you-need-to-know/",[850,"SFX","3.1",null]],
["RECENT-CORSAIR-SF1000-2024","PSU","Corsair","SF1000 (2024)","1000 W · SFX",1229,"https://www.corsair.com/us/en/explorer/diy-builder/power-supply-units/sf750sf850sf1000-platinum-atx-31-everything-you-need-to-know/",[1000,"SFX","3.1",null]],
["RECENT-CORSAIR-HX1200I-2025","PSU","Corsair","HX1200i (2025)","1200 W · ATX 3.1",1409,"https://www.corsair.com/newsroom/press-release/corsair-launches-upgraded-hxi-series-psus-with-enhanced-cables-and-dual-color-12v-2x6-gpu-connectors",[1200,"ATX","3.1",null]],
["RECENT-CORSAIR-HX1500I-2025","PSU","Corsair","HX1500i (2025)","1500 W · ATX 3.1",1639,"https://www.corsair.com/newsroom/press-release/corsair-launches-upgraded-hxi-series-psus-with-enhanced-cables-and-dual-color-12v-2x6-gpu-connectors",[1500,"ATX","3.1",null]],
["RECENT-BE-QUIET-PURE-POWER-13-M-550W","PSU","be quiet!","Pure Power 13 M 550W","550 W · ATX 3.1",499,"https://www.bequiet.com/en/press/34050",[550,"ATX","3.1",null]],
["RECENT-BE-QUIET-PURE-POWER-13-M-650W","PSU","be quiet!","Pure Power 13 M 650W","650 W · ATX 3.1",569,"https://www.bequiet.com/en/press/34050",[650,"ATX","3.1",null]],
["RECENT-BE-QUIET-PURE-POWER-13-M-750W","PSU","be quiet!","Pure Power 13 M 750W","750 W · ATX 3.1",649,"https://www.bequiet.com/en/press/34050",[750,"ATX","3.1",null]],
["RECENT-BE-QUIET-PURE-POWER-13-M-850W","PSU","be quiet!","Pure Power 13 M 850W","850 W · ATX 3.1",719,"https://www.bequiet.com/en/press/34050",[850,"ATX","3.1",null]],
["RECENT-BE-QUIET-PURE-POWER-13-M-1000W","PSU","be quiet!","Pure Power 13 M 1000W","1000 W · ATX 3.1",839,"https://www.bequiet.com/en/press/34050",[1000,"ATX","3.1",null]],
["RECENT-BE-QUIET-PURE-POWER-13-M-1200W","PSU","be quiet!","Pure Power 13 M 1200W","1200 W · ATX 3.1",1109,"https://www.bequiet.com/en/press/17208",[1200,"ATX","3.1",160]],
["RECENT-BE-QUIET-POWER-ZONE-2-750W","PSU","be quiet!","Power Zone 2 750W","750 W · ATX 3.1",859,"https://www.bequiet.com/en/powersupply/power-zone-2/5580",[750,"ATX","3.1",null]],
["RECENT-BE-QUIET-POWER-ZONE-2-850W","PSU","be quiet!","Power Zone 2 850W","850 W · ATX 3.1",939,"https://www.bequiet.com/en/powersupply/power-zone-2/5580",[850,"ATX","3.1",null]],
["RECENT-BE-QUIET-POWER-ZONE-2-1000W","PSU","be quiet!","Power Zone 2 1000W","1000 W · ATX 3.1",1049,"https://www.bequiet.com/en/powersupply/power-zone-2/5580",[1000,"ATX","3.1",null]],
["RECENT-BE-QUIET-POWER-ZONE-2-1200W","PSU","be quiet!","Power Zone 2 1200W","1200 W · ATX 3.1",1259,"https://www.bequiet.com/en/press/17208",[1200,"ATX","3.1",null]],
["RECENT-BE-QUIET-DARK-POWER-14-850W","PSU","be quiet!","Dark Power 14 850W","850 W · ATX 3.1",1329,"https://www.bequiet.com/en/press/34045",[850,"ATX","3.1",null]],
["RECENT-BE-QUIET-DARK-POWER-14-1000W","PSU","be quiet!","Dark Power 14 1000W","1000 W · ATX 3.1",1439,"https://www.bequiet.com/en/press/34045",[1000,"ATX","3.1",null]],
["RECENT-BE-QUIET-DARK-POWER-14-1200W","PSU","be quiet!","Dark Power 14 1200W","1200 W · ATX 3.1",1589,"https://www.bequiet.com/en/press/34045",[1200,"ATX","3.1",null]],
["RECENT-SEASONIC-CORE-GX-650-2024","PSU","Seasonic","CORE GX-650 (2024)","650 W · ATX 3.1",549,"https://seasonic.com/core-gx-atx-3-2024/",[650,"ATX","3.1",null]],
["RECENT-SEASONIC-CORE-GX-750-2024","PSU","Seasonic","CORE GX-750 (2024)","750 W · ATX 3.1",619,"https://seasonic.com/core-gx-atx-3-2024/",[750,"ATX","3.1",null]],
["RECENT-SEASONIC-CORE-GX-850-2024","PSU","Seasonic","CORE GX-850 (2024)","850 W · ATX 3.1",699,"https://seasonic.com/core-gx-atx-3-2024/",[850,"ATX","3.1",null]],
["RECENT-SEASONIC-CORE-GX-1000-2024","PSU","Seasonic","CORE GX-1000 (2024)","1000 W · ATX 3.1",809,"https://seasonic.com/core-gx-atx-3-2024/",[1000,"ATX","3.1",null]],
["RECENT-SEASONIC-CORE-GX-650-WHITE-2024","PSU","Seasonic","CORE GX-650 White (2024)","650 W · ATX 3.1",589,"https://seasonic.com/core-gx-white-atx-3-2024/",[650,"ATX","3.1",null]],
["RECENT-SEASONIC-CORE-GX-750-WHITE-2024","PSU","Seasonic","CORE GX-750 White (2024)","750 W · ATX 3.1",669,"https://seasonic.com/core-gx-white-atx-3-2024/",[750,"ATX","3.1",null]],
["RECENT-SEASONIC-CORE-GX-850-WHITE-2024","PSU","Seasonic","CORE GX-850 White (2024)","850 W · ATX 3.1",739,"https://seasonic.com/core-gx-white-atx-3-2024/",[850,"ATX","3.1",null]],
["RECENT-SEASONIC-CORE-GX-1000-WHITE-2024","PSU","Seasonic","CORE GX-1000 White (2024)","1000 W · ATX 3.1",859,"https://seasonic.com/core-gx-white-atx-3-2024/",[1000,"ATX","3.1",null]],
["RECENT-SEASONIC-FOCUS-GX-750-2024","PSU","Seasonic","FOCUS GX-750 (2024)","750 W · ATX 3.1",789,"https://seasonic.com/focus-gx-atx-3/",[750,"ATX","3.1",null]],
["RECENT-SEASONIC-FOCUS-GX-850-2024","PSU","Seasonic","FOCUS GX-850 (2024)","850 W · ATX 3.1",869,"https://seasonic.com/focus-gx-atx-3/",[850,"ATX","3.1",null]],
["RECENT-SEASONIC-FOCUS-GX-1000-2024","PSU","Seasonic","FOCUS GX-1000 (2024)","1000 W · ATX 3.1",979,"https://seasonic.com/focus-gx-atx-3/",[1000,"ATX","3.1",null]],
["RECENT-LIAN-LI-EDGE-GOLD-750W-BLACK","PSU","LIAN LI","EDGE Gold 750W Black","750 W · ATX 3.1",809,"https://lian-li.com/product/edge-gold/",[750,"ATX","3.1",182]],
["RECENT-LIAN-LI-EDGE-GOLD-850W-BLACK","PSU","LIAN LI","EDGE Gold 850W Black","850 W · ATX 3.1",879,"https://lian-li.com/product/edge-gold/",[850,"ATX","3.1",182]],
["RECENT-LIAN-LI-EDGE-GOLD-1000W-BLACK","PSU","LIAN LI","EDGE Gold 1000W Black","1000 W · ATX 3.1",999,"https://lian-li.com/product/edge-gold/",[1000,"ATX","3.1",182]],
["RECENT-LIAN-LI-EDGE-GOLD-1200W-BLACK","PSU","LIAN LI","EDGE Gold 1200W Black","1200 W · ATX 3.1",1149,"https://lian-li.com/product/edge-gold/",[1200,"ATX","3.1",182]],
["RECENT-BE-QUIET-LIGHT-BASE-900-FX-BLACK","CASE","be quiet!","Light Base 900 FX Black","E-ATX · 显卡限长 495 mm · 散热器限高 190 mm",1599,"https://www.bequiet.com/en/case/5290",[["E-ATX","ATX","Micro-ATX","Mini-ITX"],495,190,225]],
["RECENT-BE-QUIET-LIGHT-BASE-900-FX-WHITE","CASE","be quiet!","Light Base 900 FX White","E-ATX · 显卡限长 495 mm · 散热器限高 190 mm",1599,"https://www.bequiet.com/en/case/5290",[["E-ATX","ATX","Micro-ATX","Mini-ITX"],495,190,225]],
["RECENT-BE-QUIET-LIGHT-BASE-900-DX-BLACK","CASE","be quiet!","Light Base 900 DX Black","E-ATX · 显卡限长 495 mm · 散热器限高 190 mm",1249,"https://www.bequiet.com/en/case/5292",[["E-ATX","ATX","Micro-ATX","Mini-ITX"],495,190,225]],
["RECENT-BE-QUIET-LIGHT-BASE-900-DX-WHITE","CASE","be quiet!","Light Base 900 DX White","E-ATX · 显卡限长 495 mm · 散热器限高 190 mm",1249,"https://www.bequiet.com/en/case/5292",[["E-ATX","ATX","Micro-ATX","Mini-ITX"],495,190,225]],
["RECENT-BE-QUIET-LIGHT-BASE-600-LX-BLACK","CASE","be quiet!","Light Base 600 LX Black","ATX · 显卡限长 400 mm · 散热器限高 170 mm",949,"https://www.bequiet.com/en/case/5294",[["ATX","Micro-ATX","Mini-ITX"],400,170,199]],
["RECENT-BE-QUIET-LIGHT-BASE-600-LX-WHITE","CASE","be quiet!","Light Base 600 LX White","ATX · 显卡限长 400 mm · 散热器限高 170 mm",949,"https://www.bequiet.com/en/case/5294",[["ATX","Micro-ATX","Mini-ITX"],400,170,199]],
["RECENT-BE-QUIET-LIGHT-BASE-600-DX-BLACK","CASE","be quiet!","Light Base 600 DX Black","ATX · 显卡限长 400 mm · 散热器限高 170 mm",749,"https://www.bequiet.com/en/case/5296",[["ATX","Micro-ATX","Mini-ITX"],400,170,199]],
["RECENT-BE-QUIET-LIGHT-BASE-600-DX-WHITE","CASE","be quiet!","Light Base 600 DX White","ATX · 显卡限长 400 mm · 散热器限高 170 mm",749,"https://www.bequiet.com/en/case/5296",[["ATX","Micro-ATX","Mini-ITX"],400,170,199]],
["RECENT-BE-QUIET-LIGHT-BASE-500-BLACK","CASE","be quiet!","Light Base 500 Black","ATX · 显卡限长 400 mm · 散热器限高 185 mm",699,"https://www.bequiet.com/en/case/6691",[["ATX","Micro-ATX","Mini-ITX"],400,185,199]],
["RECENT-BE-QUIET-LIGHT-BASE-500-LX-BLACK","CASE","be quiet!","Light Base 500 LX Black","ATX · 显卡限长 400 mm · 散热器限高 185 mm",849,"https://www.bequiet.com/en/press/17378",[["ATX","Micro-ATX","Mini-ITX"],400,185,199]],
["RECENT-BE-QUIET-LIGHT-BASE-500-LX-WHITE","CASE","be quiet!","Light Base 500 LX White","ATX · 显卡限长 400 mm · 散热器限高 185 mm",849,"https://www.bequiet.com/en/press/17378",[["ATX","Micro-ATX","Mini-ITX"],400,185,199]],
["RECENT-BE-QUIET-PURE-BASE-501-LX-BLACK","CASE","be quiet!","Pure Base 501 LX Black","ATX · 显卡限长 368 mm · 散热器限高 178 mm",749,"https://www.bequiet.com/en/case/5755",[["ATX","Micro-ATX","Mini-ITX"],368,178,180]],
["RECENT-BE-QUIET-PURE-BASE-501-LX-WHITE","CASE","be quiet!","Pure Base 501 LX White","ATX · 显卡限长 368 mm · 散热器限高 178 mm",749,"https://www.bequiet.com/en/case/5755",[["ATX","Micro-ATX","Mini-ITX"],368,178,180]],
["RECENT-BE-QUIET-PURE-BASE-501-DX-BLACK","CASE","be quiet!","Pure Base 501 DX Black","ATX · 显卡限长 368 mm · 散热器限高 178 mm",649,"https://www.bequiet.com/en/case/5753",[["ATX","Micro-ATX","Mini-ITX"],368,178,180]],
["RECENT-BE-QUIET-PURE-BASE-501-DX-WHITE","CASE","be quiet!","Pure Base 501 DX White","ATX · 显卡限长 368 mm · 散热器限高 178 mm",649,"https://www.bequiet.com/en/case/5753",[["ATX","Micro-ATX","Mini-ITX"],368,178,180]],
["RECENT-BE-QUIET-PURE-BASE-501-AIRFLOW-WINDOW-BLACK","CASE","be quiet!","Pure Base 501 Airflow Window Black","ATX · 显卡限长 368 mm · 散热器限高 178 mm",599,"https://www.bequiet.com/en/case/5324",[["ATX","Micro-ATX","Mini-ITX"],368,178,180]],
["RECENT-BE-QUIET-PURE-BASE-501-AIRFLOW-WINDOW-WHITE","CASE","be quiet!","Pure Base 501 Airflow Window White","ATX · 显卡限长 368 mm · 散热器限高 178 mm",599,"https://www.bequiet.com/en/case/5324",[["ATX","Micro-ATX","Mini-ITX"],368,178,180]],
["RECENT-BE-QUIET-PURE-BASE-501-AIRFLOW-BLACK","CASE","be quiet!","Pure Base 501 Airflow Black","ATX · 显卡限长 368 mm · 散热器限高 178 mm",549,"https://www.bequiet.com/en/case/5322",[["ATX","Micro-ATX","Mini-ITX"],368,178,180]],
["RECENT-BE-QUIET-PURE-BASE-501-AIRFLOW-WHITE","CASE","be quiet!","Pure Base 501 Airflow White","ATX · 显卡限长 368 mm · 散热器限高 178 mm",549,"https://www.bequiet.com/en/case/5322",[["ATX","Micro-ATX","Mini-ITX"],368,178,180]],
["RECENT-BE-QUIET-PURE-BASE-501-BLACK","CASE","be quiet!","Pure Base 501 Black","ATX · 显卡限长 368 mm · 散热器限高 178 mm",529,"https://www.bequiet.com/en/case/5321",[["ATX","Micro-ATX","Mini-ITX"],368,178,180]],
["RECENT-LIAN-LI-LANCOOL-207-BLACK","CASE","LIAN LI","LANCOOL 207 Black","ATX · 显卡限长 375 mm · 散热器限高 180 mm",499,"https://lian-li.com/product/lancool-207/",[["ATX","Micro-ATX","Mini-ITX"],375,180,159]],
["RECENT-LIAN-LI-LANCOOL-207-WHITE","CASE","LIAN LI","LANCOOL 207 White","ATX · 显卡限长 375 mm · 散热器限高 180 mm",499,"https://lian-li.com/product/lancool-207/",[["ATX","Micro-ATX","Mini-ITX"],375,180,159]],
["RECENT-LIAN-LI-LANCOOL-217-BLACK","CASE","LIAN LI","LANCOOL 217 Black","E-ATX · 显卡限长 380 mm · 散热器限高 180 mm",769,"https://lian-li.com/product/lancool-217/",[["E-ATX","ATX","Micro-ATX","Mini-ITX"],380,180,219]],
["RECENT-LIAN-LI-LANCOOL-217-WHITE","CASE","LIAN LI","LANCOOL 217 White","E-ATX · 显卡限长 380 mm · 散热器限高 180 mm",769,"https://lian-li.com/product/lancool-217/",[["E-ATX","ATX","Micro-ATX","Mini-ITX"],380,180,219]],
["RECENT-LIAN-LI-O11-DYNAMIC-MINI-V2-BLACK","CASE","LIAN LI","O11 Dynamic Mini V2 Black","ATX · 显卡限长 400 mm · 散热器限高 160 mm",649,"https://lian-li.com/product/o11-dynamic-mini-v2/",[["ATX","Micro-ATX","Mini-ITX"],400,160,199]],
["RECENT-LIAN-LI-O11-DYNAMIC-MINI-V2-WHITE","CASE","LIAN LI","O11 Dynamic Mini V2 White","ATX · 显卡限长 400 mm · 散热器限高 160 mm",649,"https://lian-li.com/product/o11-dynamic-mini-v2/",[["ATX","Micro-ATX","Mini-ITX"],400,160,199]],
["RECENT-LIAN-LI-O11-DYNAMIC-MINI-V2-FLOW-BLACK","CASE","LIAN LI","O11 Dynamic Mini V2 Flow Black","ATX · 显卡限长 400 mm · 散热器限高 160 mm",819,"https://lian-li.com/product/o11-dynamic-mini-v2/",[["ATX","Micro-ATX","Mini-ITX"],400,160,199]],
["RECENT-LIAN-LI-O11-DYNAMIC-MINI-V2-FLOW-WHITE","CASE","LIAN LI","O11 Dynamic Mini V2 Flow White","ATX · 显卡限长 400 mm · 散热器限高 160 mm",819,"https://lian-li.com/product/o11-dynamic-mini-v2/",[["ATX","Micro-ATX","Mini-ITX"],400,160,199]],
["RECENT-LIAN-LI-VECTOR-V100-BLACK","CASE","LIAN LI","Vector V100 Black","ATX · 显卡限长 420 mm · 散热器限高 178 mm",469,"https://lian-li.com/product/vector-v100/",[["ATX","Micro-ATX","Mini-ITX"],420,178,219]],
["RECENT-LIAN-LI-VECTOR-V100-WHITE","CASE","LIAN LI","Vector V100 White","ATX · 显卡限长 420 mm · 散热器限高 178 mm",469,"https://lian-li.com/product/vector-v100/",[["ATX","Micro-ATX","Mini-ITX"],420,178,219]],
["RECENT-LIAN-LI-VECTOR-V100-ARGB-BLACK","CASE","LIAN LI","Vector V100 ARGB Black","ATX · 显卡限长 420 mm · 散热器限高 178 mm",559,"https://lian-li.com/product/vector-v100/",[["ATX","Micro-ATX","Mini-ITX"],420,178,219]],
["RECENT-LIAN-LI-VECTOR-V100-ARGB-WHITE","CASE","LIAN LI","Vector V100 ARGB White","ATX · 显卡限长 420 mm · 散热器限高 178 mm",559,"https://lian-li.com/product/vector-v100/",[["ATX","Micro-ATX","Mini-ITX"],420,178,219]],
["RECENT-FRACTAL-DESIGN-NORTH-XL-CHARCOAL-BLACK-MESH","CASE","Fractal Design","North XL Charcoal Black Mesh","E-ATX · 显卡限长 413 mm · 散热器限高 185 mm",1299,"https://www.fractal-design.com/app/uploads/2024/02/North-XL_Product-Sheet_EN.pdf",[["E-ATX","ATX","Micro-ATX","Mini-ITX"],413,185,290]],
["RECENT-FRACTAL-DESIGN-NORTH-XL-CHALK-WHITE-MESH","CASE","Fractal Design","North XL Chalk White Mesh","E-ATX · 显卡限长 413 mm · 散热器限高 185 mm",1299,"https://www.fractal-design.com/app/uploads/2024/02/North-XL_Product-Sheet_EN.pdf",[["E-ATX","ATX","Micro-ATX","Mini-ITX"],413,185,290]],
["RECENT-FRACTAL-DESIGN-NORTH-XL-CHARCOAL-BLACK-TG-DARK-TINT","CASE","Fractal Design","North XL Charcoal Black TG Dark Tint","E-ATX · 显卡限长 413 mm · 散热器限高 185 mm",1299,"https://www.fractal-design.com/app/uploads/2024/02/North-XL_Product-Sheet_EN.pdf",[["E-ATX","ATX","Micro-ATX","Mini-ITX"],413,185,290]],
["RECENT-FRACTAL-DESIGN-NORTH-XL-CHALK-WHITE-TG-CLEAR-TINT","CASE","Fractal Design","North XL Chalk White TG Clear Tint","E-ATX · 显卡限长 413 mm · 散热器限高 185 mm",1299,"https://www.fractal-design.com/app/uploads/2024/02/North-XL_Product-Sheet_EN.pdf",[["E-ATX","ATX","Micro-ATX","Mini-ITX"],413,185,290]],
["RECENT-FRACTAL-DESIGN-MOOD-BLACK","CASE","Fractal Design","Mood Black","Mini-ITX · 显卡限长 325 mm · 散热器限高 114 mm",1049,"https://www.fractal-design.com/products/cases/mood-series/mood/mood-light-gray/",[["Mini-ITX"],325,114,130]],
["RECENT-FRACTAL-DESIGN-MOOD-LIGHT-GRAY","CASE","Fractal Design","Mood Light Gray","Mini-ITX · 显卡限长 325 mm · 散热器限高 114 mm",1049,"https://www.fractal-design.com/products/cases/mood-series/mood/mood-light-gray/",[["Mini-ITX"],325,114,130]],
["RECENT-FRACTAL-DESIGN-ERA-2-CHARCOAL-GRAY","CASE","Fractal Design","Era 2 Charcoal Gray","Mini-ITX · 显卡限长 326 mm · 散热器限高 55 mm",1299,"https://support.fractal-design.com/support/solutions/articles/4000210379-era-2-cpu-cooler-gpu-support",[["Mini-ITX"],326,55,137]],
["RECENT-FRACTAL-DESIGN-ERA-2-SILVER","CASE","Fractal Design","Era 2 Silver","Mini-ITX · 显卡限长 326 mm · 散热器限高 55 mm",1299,"https://support.fractal-design.com/support/solutions/articles/4000210379-era-2-cpu-cooler-gpu-support",[["Mini-ITX"],326,55,137]],
["RECENT-FRACTAL-DESIGN-ERA-2-MIDNIGHT-BLUE","CASE","Fractal Design","Era 2 Midnight Blue","Mini-ITX · 显卡限长 326 mm · 散热器限高 55 mm",1299,"https://support.fractal-design.com/support/solutions/articles/4000210379-era-2-cpu-cooler-gpu-support",[["Mini-ITX"],326,55,137]],
["RECENT-FRACTAL-DESIGN-MESHIFY-3-AMBIENCE-PRO-RGB-BLACK-TG-LIGHT-TINT","CASE","Fractal Design","Meshify 3 Ambience Pro RGB Black TG Light Tint","E-ATX · 显卡限长 349 mm · 散热器限高 173 mm",1249,"https://www.fractal-design.com/products/cases/meshify-series/meshify-3/",[["E-ATX","ATX","Micro-ATX","Mini-ITX"],349,173,180]],
["RECENT-FRACTAL-DESIGN-MESHIFY-3-AMBIENCE-PRO-RGB-WHITE-TG-CLEAR-TINT","CASE","Fractal Design","Meshify 3 Ambience Pro RGB White TG Clear Tint","E-ATX · 显卡限长 349 mm · 散热器限高 173 mm",1249,"https://www.fractal-design.com/products/cases/meshify-series/meshify-3/",[["E-ATX","ATX","Micro-ATX","Mini-ITX"],349,173,180]],
["RECENT-FRACTAL-DESIGN-MESHIFY-3-XL-AMBIENCE-PRO-RGB-BLACK-TG-LIGHT-TINT","CASE","Fractal Design","Meshify 3 XL Ambience Pro RGB Black TG Light Tint","E-ATX · 显卡限长 512 mm · 散热器限高 182 mm",1599,"https://www.fractal-design.com/products/cases/meshify-series/meshify-3-xl/",[["E-ATX","ATX","Micro-ATX","Mini-ITX"],512,182,230]],
["RECENT-FRACTAL-DESIGN-MESHIFY-3-XL-AMBIENCE-PRO-RGB-WHITE-TG-CLEAR-TINT","CASE","Fractal Design","Meshify 3 XL Ambience Pro RGB White TG Clear Tint","E-ATX · 显卡限长 512 mm · 散热器限高 182 mm",1599,"https://www.fractal-design.com/products/cases/meshify-series/meshify-3-xl/",[["E-ATX","ATX","Micro-ATX","Mini-ITX"],512,182,230]],
["RECENT-FRACTAL-DESIGN-EPOCH-BLACK-TG-LIGHT-TINT","CASE","Fractal Design","Epoch Black TG Light Tint","ATX · 显卡限长 372 mm · 散热器限高 170 mm",669,"https://www.fractal-design.com/products/cases/epoch-series/epoch/epoch-black-tg-light-tint/",[["ATX","Micro-ATX","Mini-ITX"],372,170,255]],
["RECENT-FRACTAL-DESIGN-EPOCH-WHITE-TG-CLEAR-TINT","CASE","Fractal Design","Epoch White TG Clear Tint","ATX · 显卡限长 372 mm · 散热器限高 170 mm",669,"https://www.fractal-design.com/products/cases/epoch-series/epoch/epoch-white-tg-clear-tint/",[["ATX","Micro-ATX","Mini-ITX"],372,170,255]],
["RECENT-PHANTEKS-XT-PRO-BLACK","CASE","Phanteks","XT Pro Black","E-ATX · 显卡限长 415 mm · 散热器限高 184 mm",459,"https://phanteks.com/product/xt-pro-black/",[["E-ATX","ATX","Micro-ATX","Mini-ITX"],415,184,270]],
["RECENT-PHANTEKS-EVOLV-X2-BLACK","CASE","Phanteks","Evolv X2 Black","E-ATX · 显卡限长 380 mm · 散热器限高 170 mm",1049,"https://phanteks.com/product/evolv-x2-black/",[["E-ATX","ATX","Micro-ATX","Mini-ITX"],380,170,250]],
["RECENT-ARCTIC-FREEZER-36","COOLER","ARCTIC","Freezer 36","风冷散热器 · 高 159 mm · 120 mm 风扇",159,"https://www.arctic.de/en/freezer-36-Series",[["AM5","AM4","LGA1851","LGA1700"],159,120]],
["RECENT-ARCTIC-FREEZER-36-BLACK","COOLER","ARCTIC","Freezer 36 Black","风冷散热器 · 高 159 mm · 120 mm 风扇",189,"https://www.arctic.de/en/freezer-36-Series",[["AM5","AM4","LGA1851","LGA1700"],159,120]],
["RECENT-ARCTIC-FREEZER-36-CO","COOLER","ARCTIC","Freezer 36 CO","风冷散热器 · 高 159 mm · 120 mm 风扇",209,"https://www.arctic.de/en/freezer-36-Series",[["AM5","AM4","LGA1851","LGA1700"],159,120]],
["RECENT-ARCTIC-FREEZER-36-A-RGB-BLACK","COOLER","ARCTIC","Freezer 36 A-RGB Black","风冷散热器 · 高 159 mm · 120 mm 风扇",229,"https://www.arctic.de/en/freezer-36-Series",[["AM5","AM4","LGA1851","LGA1700"],159,120]],
["RECENT-ARCTIC-FREEZER-36-A-RGB-WHITE","COOLER","ARCTIC","Freezer 36 A-RGB White","风冷散热器 · 高 159 mm · 120 mm 风扇",239,"https://www.arctic.de/en/freezer-36-Series",[["AM5","AM4","LGA1851","LGA1700"],159,120]],
["RECENT-ARCTIC-FREEZER-8A","COOLER","ARCTIC","Freezer 8A","风冷散热器 · 高 136 mm · 100 mm 风扇",89,"https://www.arctic.de/en/blog/efficient-cooling-in-a-small-space",[["AM5","AM4"],136,100]],
["RECENT-ARCTIC-FREEZER-8A-CO","COOLER","ARCTIC","Freezer 8A CO","风冷散热器 · 高 136 mm · 100 mm 风扇",109,"https://www.arctic.de/en/blog/efficient-cooling-in-a-small-space",[["AM5","AM4"],136,100]],
["RECENT-ARCTIC-FREEZER-8I","COOLER","ARCTIC","Freezer 8i","风冷散热器 · 高 136 mm · 100 mm 风扇",89,"https://www.arctic.de/en/blog/efficient-cooling-in-a-small-space",[["LGA1851","LGA1700"],136,100]],
["RECENT-ARCTIC-FREEZER-8I-CO","COOLER","ARCTIC","Freezer 8i CO","风冷散热器 · 高 136 mm · 100 mm 风扇",109,"https://www.arctic.de/en/blog/efficient-cooling-in-a-small-space",[["LGA1851","LGA1700"],136,100]],
["RECENT-ARCTIC-FREEZER-36-S-BLACK","COOLER","ARCTIC","Freezer 36-S Black","风冷散热器 · 高 156 mm · 120 mm 风扇",149,"https://www.arctic.de/media/65/ea/24/1777885824/Spec_Sheet_Freezer_36-S_EN.pdf",[["AM5","AM4","LGA1851","LGA1700"],156,120]],
["RECENT-ARCTIC-FREEZER-36-S-A-RGB-BLACK","COOLER","ARCTIC","Freezer 36-S A-RGB Black","风冷散热器 · 高 156 mm · 120 mm 风扇",189,"https://www.arctic.de/media/e3/d8/1d/1777885824/Spec_Sheet_Freezer_36-S_A-RGB_EN.pdf",[["AM5","AM4","LGA1851","LGA1700"],156,120]],
["RECENT-ARCTIC-FREEZER-36-S-A-RGB-WHITE","COOLER","ARCTIC","Freezer 36-S A-RGB White","风冷散热器 · 高 156 mm · 120 mm 风扇",199,"https://www.arctic.de/media/e3/d8/1d/1777885824/Spec_Sheet_Freezer_36-S_A-RGB_EN.pdf",[["AM5","AM4","LGA1851","LGA1700"],156,120]],
["RECENT-BE-QUIET-PURE-ROCK-3-BLACK","COOLER","be quiet!","Pure Rock 3 Black","风冷散热器 · 高 154 mm · 120 mm 风扇",229,"https://www.bequiet.com/en/cpucooler/5591",[["AM5","AM4","LGA1851","LGA1700","LGA1200","LGA1151","LGA1150","LGA1155"],154,120]],
["RECENT-BE-QUIET-PURE-ROCK-3-LX-BLACK","COOLER","be quiet!","Pure Rock 3 LX Black","风冷散热器 · 高 154 mm · 120 mm 风扇",259,"https://www.bequiet.com/en/cpucooler/5592",[["AM5","AM4","LGA1851","LGA1700","LGA1200","LGA1151","LGA1150","LGA1155"],154,120]],
["RECENT-BE-QUIET-PURE-ROCK-PRO-3","COOLER","be quiet!","Pure Rock Pro 3","风冷散热器 · 高 155 mm · 120 mm 风扇",309,"https://www.bequiet.com/en/cpucooler/5599",[["AM5","AM4","LGA1851","LGA1700","LGA1200","LGA1151","LGA1150","LGA1155"],155,120]],
["RECENT-BE-QUIET-PURE-ROCK-PRO-3-BLACK","COOLER","be quiet!","Pure Rock Pro 3 Black","风冷散热器 · 高 155 mm · 120 mm 风扇",329,"https://www.bequiet.com/en/cpucooler/5600",[["AM5","AM4","LGA1851","LGA1700","LGA1200","LGA1151","LGA1150","LGA1155"],155,120]],
["RECENT-BE-QUIET-PURE-ROCK-PRO-3-LX","COOLER","be quiet!","Pure Rock Pro 3 LX","风冷散热器 · 高 155 mm · 120 mm 风扇",369,"https://www.bequiet.com/en/cpucooler/5601",[["AM5","AM4","LGA1851","LGA1700","LGA1200","LGA1151","LGA1150","LGA1155"],155,120]],
["RECENT-BE-QUIET-PURE-ROCK-SLIM-3","COOLER","be quiet!","Pure Rock Slim 3","风冷散热器 · 高 127 mm · 100 mm 风扇",159,"https://www.bequiet.com/en/cpucooler/6121",[["AM5","AM4","LGA1851","LGA1700"],127,100]],
["RECENT-BE-QUIET-DARK-ROCK-5","COOLER","be quiet!","Dark Rock 5","风冷散热器 · 高 161 mm · 120 mm 风扇",499,"https://www.bequiet.com/en/cpucooler/5110",[["AM5","AM4","LGA1851","LGA1700","LGA1200","LGA1151","LGA1150","LGA1155"],161,120]],
["RECENT-NOCTUA-NH-D15-G2","COOLER","Noctua","NH-D15 G2","风冷散热器 · 高 168 mm · 140 mm 风扇",1099,"https://www.noctua.at/en/products/nh-d15-g2/specifications",[["AM5","AM4","LGA1851","LGA1700","LGA1200","LGA1151","LGA1150","LGA1155"],168,140]],
["RECENT-NOCTUA-NH-D15-G2-HBC","COOLER","Noctua","NH-D15 G2 HBC","风冷散热器 · 高 168 mm · 140 mm 风扇",1099,"https://www.noctua.at/en/products/nh-d15-g2-hbc/specifications",[["AM5","AM4","LGA1851","LGA1700","LGA1200","LGA1151","LGA1150","LGA1155"],168,140]],
["RECENT-NOCTUA-NH-D15-G2-LBC","COOLER","Noctua","NH-D15 G2 LBC","风冷散热器 · 高 168 mm · 140 mm 风扇",1099,"https://www.noctua.at/en/products/nh-d15-g2-lbc/specifications",[["AM5","AM4","LGA1851","LGA1700","LGA1200","LGA1151","LGA1150","LGA1155"],168,140]],
["RECENT-NOCTUA-NH-D15-G2-CHROMAX-BLACK","COOLER","Noctua","NH-D15 G2 chromax.black","风冷散热器 · 高 168 mm · 140 mm 风扇",1199,"https://www.noctua.at/en/products/nh-d15-g2-chromax-black/specifications",[["AM5","AM4","LGA1851","LGA1700","LGA1200","LGA1151","LGA1150","LGA1155"],168,140]],
["RECENT-NOCTUA-NH-D12L-CHROMAX-BLACK","COOLER","Noctua","NH-D12L chromax.black","风冷散热器 · 高 145 mm · 120 mm 风扇",739,"https://www.noctua.at/en/products/nh-d12l-chromax-black/specifications",[["AM5","AM4","LGA1851","LGA1700","LGA1200","LGA1151","LGA1150","LGA1155"],145,120]],
["RECENT-NOCTUA-NH-L12SX77","COOLER","Noctua","NH-L12Sx77","风冷散热器 · 高 77 mm · 120 mm 风扇",549,"https://www.noctua.at/en/products/nh-l12sx77/specifications",[["AM5","AM4","LGA1851","LGA1700","LGA1200","LGA1151","LGA1150","LGA1155"],77,120]],
["RECENT-DEEPCOOL-AK400-G2","COOLER","DeepCool","AK400 G2","风冷散热器 · 高 156 mm · 120 mm 风扇",199,"https://www.deepcool.com/products/Cooling/cpuaircoolers/AK400-G2-Wood-Grain-Top-Cover-CPU-Cooler/2025/21908.shtml",[["AM5","AM4","LGA1851","LGA1700","LGA1200","LGA1151","LGA1150","LGA1155"],156,120]],
["RECENT-DEEPCOOL-AK500-G2","COOLER","DeepCool","AK500 G2","风冷散热器 · 高 159 mm · 120 mm 风扇",259,"https://www.deepcool.com/products/Cooling/cpuaircoolers/AK500-G2-Wood-Grain-Top-Cover-CPU-Cooler/2025/21986.shtml",[["AM5","AM4","LGA1851","LGA1700","LGA1200","LGA1151","LGA1150","LGA1155"],159,120]],
["RECENT-DEEPCOOL-AK400-G2-WH","COOLER","DeepCool","AK400 G2 WH","风冷散热器 · 高 156 mm · 120 mm 风扇",219,"https://www.deepcool.com/products/Cooling/cpuaircoolers/AK400-G2-WH-Wood-Grain-Top-Cover-CPU-Cooler/2025/21909.shtml",[["AM5","AM4","LGA1851","LGA1700","LGA1200","LGA1151","LGA1150","LGA1155"],156,120]],
["RECENT-DEEPCOOL-AK500-G2-WH","COOLER","DeepCool","AK500 G2 WH","风冷散热器 · 高 159 mm · 120 mm 风扇",279,"https://uk.deepcool.com/products/Cooling/cpuaircoolers/AK500-G2-WH-Wood-Grain-Top-Cover-CPU-Cooler/2025/21990.shtml",[["AM5","AM4","LGA1851","LGA1700","LGA1200","LGA1151","LGA1150","LGA1155"],159,120]],
["RECENT-DEEPCOOL-AK620-G2","COOLER","DeepCool","AK620 G2","风冷散热器 · 高 159 mm · 120 mm 风扇",319,"https://www.deepcool.com/products/Cooling/cpuaircoolers/AK620-G2-Wood-Grain-Top-Cover-CPU-Cooler/2025/21864.shtml",[["AM5","AM4","LGA1851","LGA1700","LGA1200","LGA1151","LGA1150","LGA1155"],159,120]],
["RECENT-DEEPCOOL-AK400-G2-DIGITAL-NYX","COOLER","DeepCool","AK400 G2 DIGITAL NYX","风冷散热器 · 高 156 mm · 120 mm 风扇",259,"https://www.deepcool.com/products/Cooling/cpuaircoolers/AK400-G2-DIGITAL-NYX-AI-Dynamic-Adjustment-CPU-Cooler-with-Digital-Display/2025/21907.shtml",[["AM5","AM4","LGA1851","LGA1700","LGA1200","LGA1151","LGA1150","LGA1155"],156,120]],
["RECENT-DEEPCOOL-AK500-G2-DIGITAL-NYX","COOLER","DeepCool","AK500 G2 DIGITAL NYX","风冷散热器 · 高 159 mm · 120 mm 风扇",319,"https://www.deepcool.com/products/Cooling/cpuaircoolers/AK500-G2-DIGITAL-NYX-AI-Dynamic-Adjustment-CPU-Cooler-with-Digital-Display/2025/21985.shtml",[["AM5","AM4","LGA1851","LGA1700","LGA1200","LGA1151","LGA1150","LGA1155"],159,120]],
["RECENT-DEEPCOOL-AK620-G2-DIGITAL-NYX","COOLER","DeepCool","AK620 G2 DIGITAL NYX","风冷散热器 · 高 159 mm · 120 mm 风扇",399,"https://www.deepcool.com/products/Cooling/cpuaircoolers/AK620-G2-DIGITAL-NYX-AI-Dynamic-Adjustment-CPU-Cooler-with-Digital-Display/2025/21894.shtml",[["AM5","AM4","LGA1851","LGA1700","LGA1200","LGA1151","LGA1150","LGA1155"],159,120]],
["RECENT-DEEPCOOL-AK400-DIGITAL-PRO","COOLER","DeepCool","AK400 DIGITAL PRO","风冷散热器 · 高 159 mm · 120 mm 风扇",229,"https://www.deepcool.com/products/Cooling/cpuaircoolers/AK400-Digital-Pro-Cooler-With-Multi-line-Display-1851-1700-AM5/2024/18680.shtml",[["AM5","AM4","LGA1851","LGA1700","LGA1200","LGA1151","LGA1150","LGA1155"],159,120]],
["RECENT-DEEPCOOL-AK400-DIGITAL-PRO-WH","COOLER","DeepCool","AK400 DIGITAL PRO WH","风冷散热器 · 高 159 mm · 120 mm 风扇",249,"https://www.deepcool.com/products/Cooling/cpuaircoolers/AK400-Digital-Pro-WH-Cooler-With-Multi-line-Display-1851-1700-AM5/2024/19628.shtml",[["AM5","AM4","LGA1851","LGA1700","LGA1200","LGA1151","LGA1150","LGA1155"],159,120]],
["RECENT-DEEPCOOL-AK500-DIGITAL-PRO","COOLER","DeepCool","AK500 DIGITAL PRO","风冷散热器 · 高 162 mm · 120 mm 风扇",279,"https://www.deepcool.com/products/Cooling/cpuaircoolers/AK500-Digital-Pro-Cooler-With-Multi-line-Display-1851-1700-AM5/2024/19791.shtml",[["AM5","AM4","LGA1851","LGA1700","LGA1200","LGA1151","LGA1150","LGA1155"],162,120]],
["RECENT-DEEPCOOL-AK500-DIGITAL-PRO-WH","COOLER","DeepCool","AK500 DIGITAL PRO WH","风冷散热器 · 高 162 mm · 120 mm 风扇",299,"https://www.deepcool.com/products/Cooling/cpuaircoolers/AK500-Digital-Pro-WH-Cooler-With-Multi-line-Display-1851-1700-AM5/2024/19793.shtml",[["AM5","AM4","LGA1851","LGA1700","LGA1200","LGA1151","LGA1150","LGA1155"],162,120]],
["RECENT-DEEPCOOL-AK620-DIGITAL-PRO","COOLER","DeepCool","AK620 DIGITAL PRO","风冷散热器 · 高 163 mm · 120 mm 风扇",349,"https://www.deepcool.com/products/Cooling/cpuaircoolers/AK620-Digital-Pro-WH-Cooler-With-Multi-line-Display-1851-1700-AM5/2024/19629.shtml",[["AM5","AM4","LGA1851","LGA1700","LGA1200","LGA1151","LGA1150","LGA1155"],163,120]],
["RECENT-DEEPCOOL-AK620-DIGITAL-PRO-WH","COOLER","DeepCool","AK620 DIGITAL PRO WH","风冷散热器 · 高 163 mm · 120 mm 风扇",369,"https://www.deepcool.com/products/Cooling/cpuaircoolers/AK620-Digital-Pro-WH-Cooler-With-Multi-line-Display-1851-1700-AM5/2024/19629.shtml",[["AM5","AM4","LGA1851","LGA1700","LGA1200","LGA1151","LGA1150","LGA1155"],163,120]],
["RECENT-DEEPCOOL-ASSASSIN-IV-VC-VISION","COOLER","DeepCool","ASSASSIN IV VC VISION","风冷散热器 · 高 172 mm · 140 mm 风扇",649,"https://www.deepcool.com/products/Cooling/cpuaircoolers/ASSASSIN-IV-VC-VISION-Digital-Air-Cooler-1851-1700-AM5/2024/18964.shtml",[["AM5","AM4","LGA1851","LGA1700","LGA1200","LGA1151","LGA1150","LGA1155"],172,140]],
["RECENT-COOLER-MASTER-HYPER-212-PRO","COOLER","Cooler Master","Hyper 212 Pro","风冷散热器 · 高 152 mm · 120 mm 风扇",229,"https://www.coolermaster.com/en-global/products/hyper-212-pro.html",[["AM5","AM4","LGA1851","LGA1700","LGA1200","LGA1151","LGA1150","LGA1155"],152,120]],
["RECENT-COOLER-MASTER-HYPER-612-APEX-BLACK","COOLER","Cooler Master","Hyper 612 APEX Black","风冷散热器 · 高 159 mm · 120 mm 风扇",379,"https://www.coolermaster.com/en-global/products/hyper-612-apex.html",[["AM5","AM4","LGA1851","LGA1700","LGA1200","LGA1151","LGA1150","LGA1155"],159,120]],
["RECENT-COOLER-MASTER-HYPER-612-APEX-WHITE","COOLER","Cooler Master","Hyper 612 APEX White","风冷散热器 · 高 159 mm · 120 mm 风扇",379,"https://www.coolermaster.com/en-global/products/hyper-612-apex.html",[["AM5","AM4","LGA1851","LGA1700","LGA1200","LGA1151","LGA1150","LGA1155"],159,120]],
["RECENT-SCYTHE-MUGEN-6","COOLER","Scythe","Mugen 6","风冷散热器 · 高 154 mm · 120 mm 风扇",299,"https://www.scytheus.com/news-1/introducing-mugen-6%3A-the-ultimate-cpu-cooler-series",[["AM5","AM4","LGA1700","LGA1200","LGA1151","LGA1150","LGA1155"],154,120]],
["RECENT-SCYTHE-MUGEN-6-DUAL-FAN-BLACK-EDITION","COOLER","Scythe","Mugen 6 Dual Fan Black Edition","风冷散热器 · 高 154 mm · 120 mm 风扇",379,"https://www.scytheus.com/news-1/introducing-mugen-6%3A-the-ultimate-cpu-cooler-series",[["AM5","AM4","LGA1700","LGA1200","LGA1151","LGA1150","LGA1155"],154,120]],
["RECENT-MONTECH-NX400-BLACK","COOLER","Montech","NX400 Black","风冷散热器 · 高 156 mm · 120 mm 风扇",159,"https://www.montechpc.com/nx400",[["AM5","AM4","LGA1851","LGA1700","LGA1200","LGA1151","LGA1150","LGA1155"],156,120]],
["RECENT-MONTECH-NX400-WHITE","COOLER","Montech","NX400 White","风冷散热器 · 高 156 mm · 120 mm 风扇",169,"https://www.montechpc.com/nx400",[["AM5","AM4","LGA1851","LGA1700","LGA1200","LGA1151","LGA1150","LGA1155"],156,120]],
["RECENT-MONTECH-NX400-ARGB-BLACK","COOLER","Montech","NX400 ARGB Black","风冷散热器 · 高 156 mm · 120 mm 风扇",189,"https://www.montechpc.com/nx400-argb",[["AM5","AM4","LGA1851","LGA1700","LGA1200","LGA1151","LGA1150","LGA1155"],156,120]],
["RECENT-MONTECH-NX400-ARGB-WHITE","COOLER","Montech","NX400 ARGB White","风冷散热器 · 高 156 mm · 120 mm 风扇",199,"https://www.montechpc.com/nx400-argb",[["AM5","AM4","LGA1851","LGA1700","LGA1200","LGA1151","LGA1150","LGA1155"],156,120]]
]';
SET @recent_parts_version = 'catalog-parts-2026-v1';

INSERT INTO products (sku, name, category, brand, model, summary, image_url, price, stock,
                      status, source_url, price_note, data_version)
SELECT part.sku, CONCAT(part.brand, ' ', part.model), part.category, part.brand, part.model,
       part.summary, CONCAT('/images/', LOWER(part.category), '.svg'), part.price, 8,
       'ON_SALE', part.source_url, 'CNY 演示价格与库存，非实时市场报价', @recent_parts_version
FROM JSON_TABLE(@recent_parts, '$[*]' COLUMNS (
    sku VARCHAR(80) PATH '$[0]',
    category VARCHAR(16) PATH '$[1]',
    brand VARCHAR(60) PATH '$[2]',
    model VARCHAR(100) PATH '$[3]',
    summary VARCHAR(240) PATH '$[4]',
    price DECIMAL(12,2) PATH '$[5]',
    source_url VARCHAR(1000) PATH '$[6]'
)) part
WHERE NOT EXISTS (
    SELECT 1 FROM products existing
    WHERE existing.sku = part.sku
       OR (existing.category = part.category
           AND (existing.name = CONCAT(part.brand, ' ', part.model)
                OR (existing.brand = part.brand AND existing.model = part.model)))
)
ORDER BY part.category, part.sku;

INSERT INTO product_specifications (product_id, description, parameters, data_version, verified_on)
SELECT product.id, CONCAT(product.name, '。', part.summary, '。'),
       CASE part.category
           WHEN 'PSU' THEN JSON_MERGE_PRESERVE(
               JSON_ARRAY(
                   JSON_OBJECT('code', 'ratedPower', 'label', '有效额定功率', 'unit', 'W', 'value', JSON_EXTRACT(part.specs, '$[0]'), 'sourceUrl', part.source_url),
                   JSON_OBJECT('code', 'formFactor', 'label', '电源板型', 'unit', '', 'value', JSON_EXTRACT(part.specs, '$[1]'), 'sourceUrl', part.source_url),
                   JSON_OBJECT('code', 'atxVersion', 'label', 'ATX 版本', 'unit', '', 'value', JSON_EXTRACT(part.specs, '$[2]'), 'sourceUrl', part.source_url)
               ),
               IF(JSON_TYPE(JSON_EXTRACT(part.specs, '$[3]')) = 'NULL', JSON_ARRAY(), JSON_ARRAY(
                   JSON_OBJECT('code', 'length', 'label', '电源长度', 'unit', 'mm', 'value', JSON_EXTRACT(part.specs, '$[3]'), 'sourceUrl', part.source_url)
               ))
           )
           WHEN 'CASE' THEN JSON_ARRAY(
               JSON_OBJECT('code', 'supportedMotherboardFormFactors', 'label', '支持主板板型', 'unit', '', 'value', JSON_EXTRACT(part.specs, '$[0]'), 'sourceUrl', part.source_url),
               JSON_OBJECT('code', 'maxGpuLength', 'label', '默认安装显卡限长', 'unit', 'mm', 'value', JSON_EXTRACT(part.specs, '$[1]'), 'sourceUrl', part.source_url),
               JSON_OBJECT('code', 'maxCoolerHeight', 'label', '默认安装散热器限高', 'unit', 'mm', 'value', JSON_EXTRACT(part.specs, '$[2]'), 'sourceUrl', part.source_url),
               JSON_OBJECT('code', 'maxPsuLength', 'label', '电源限长', 'unit', 'mm', 'value', JSON_EXTRACT(part.specs, '$[3]'), 'sourceUrl', part.source_url)
           )
           WHEN 'COOLER' THEN JSON_ARRAY(
               JSON_OBJECT('code', 'supportedSockets', 'label', '支持 CPU 插槽', 'unit', '', 'value', JSON_EXTRACT(part.specs, '$[0]'), 'sourceUrl', part.source_url),
               JSON_OBJECT('code', 'height', 'label', '散热器完整高度', 'unit', 'mm', 'value', JSON_EXTRACT(part.specs, '$[1]'), 'sourceUrl', part.source_url),
               JSON_OBJECT('code', 'fanDiameter', 'label', '风扇直径', 'unit', 'mm', 'value', JSON_EXTRACT(part.specs, '$[2]'), 'sourceUrl', part.source_url)
           )
       END,
       product.data_version, '2026-11-26'
FROM JSON_TABLE(@recent_parts, '$[*]' COLUMNS (
    sku VARCHAR(80) PATH '$[0]',
    category VARCHAR(16) PATH '$[1]',
    summary VARCHAR(240) PATH '$[4]',
    source_url VARCHAR(1000) PATH '$[6]',
    specs JSON PATH '$[7]'
)) part
JOIN products product ON product.sku = part.sku AND product.data_version = @recent_parts_version
WHERE NOT EXISTS (
    SELECT 1 FROM product_specifications existing WHERE existing.product_id = product.id
);

COMMIT;
