# AMD 产品渲染参考分资料

采集日期：2026-11-03。范围仅为 AMD Ryzen 7 7800X3D CPU 与 AMD Radeon RX 7800 XT（商品为 AMD 公版）两件商品的 Blender 渲染参考数据。原始资料和复现脚本存放于 `.local/product-tags-research/`。

## 结果

| 商品型号 | Blender 精确设备名 / 后端 | Windows 4.3 候选提交 | 固定口径完整提交 | 中位原始总分（samples/min） | `performance-v1` 分 |
| --- | --- | ---: | ---: | ---: | ---: |
| AMD Ryzen 7 7800X3D | `AMD Ryzen 7 7800X3D 8-Core Processor` / `CPU` | 333 | 284 | 253.0658202397576625 | 55.306582 |
| AMD Radeon RX 7800 XT（AMD 公版商品） | `AMD Radeon RX 7800 XT` / `HIP` | 144 | 133 | 2441.3539960994234 | 54.413540 |

分数按仓库 `performance-v1` 线性节点换算并保留六位小数：CPU 原始分 200 对应 50 分、450 对应 75 分；GPU 原始分 2000 对应 50 分、4500 对应 75 分。原始分是 Blender Open Data 场景渲染 samples/min 汇总，不是游戏帧率或整机分数。

## 型号匹配与限制

- AMD 官方处理器规格页列出产品名 `AMD Ryzen 7 7800X3D`、8 核、16 线程和 AM5 插槽。本次快照的全部 333 份 Windows Blender 4.3 候选都使用精确设备名 `AMD Ryzen 7 7800X3D 8-Core Processor`；严格筛选只保留此设备名，不以 Ryzen 7 7800X 或其他 X3D 型号替代。CPU 严格样本另外要求单插槽和 16 线程。
- AMD 官方 RX 7800 XT 规格页列出准确型号 `AMD Radeon RX 7800 XT` 和 16 GB 显存。GPU 快照设备名与型号准确相同，采用获准的型号级映射。记录不能辨认板卡品牌、AMD 公版身份或显存容量，所以此分数可用于该 GPU 型号的参考，不能描述为 AMD 公版板卡的实测分。
- AMD 处理器规格页给出的 120 W 是 Default TDP，不是峰值功耗。本研究不把它填作峰值功耗，也不据此生成峰值功耗标签。
- 数据来自社区提交。严格筛选固定了 Blender 构建、启动器、脚本、操作系统、渲染设备及场景时限，但没有统一 CPU/GPU 频率、驱动、散热或其他系统条件。结果仅作项目内部渲染参考。

## 采集和筛选口径

来源为 Blender Foundation 官方 Open Data 快照，下载页说明快照每日更新并采用 CC0。固定对象是 2026-11-03 捕获的 ZIP，而非会变化的 `latest` URL：102,160,171 bytes，SHA-256 `7e0875527aea34d96e99c933d1e82c62a20dac0a343720d252b12c21228812dd`；JSONL 成员为 `opendata-2026-11-03-000001+0000.jsonl`。原压缩包位于 `.local/task-08-data/opendata-captured-2026-11-03.zip`，未将约 1.9 GB 的 JSONL 全量解压保存。

Windows 4.3 候选计数以快照中的完整 UUID 提交为单位。固定口径要求 schema v4、Blender 4.3.0 build `2b18cad88b13`、Windows 64-bit、benchmark launcher 3.1.0 checksum `5c65e95672826b04d3178ae02342fafcd2192987ab16ad54a5aee2346a254a40`、benchmark script 3.1.1 checksum `5a706e14279da13b2bed2dbb9e0a47c2588b9ea4f37cf8f9c7d79c8b7c634c1f`、每场景 30 秒，以及以下三个场景各一条：

| 场景 | checksum |
| --- | --- |
| `classroom` | `936f0a528f03eba291c67ea87743a0ebde58938fb87471b262721b89b46b875d` |
| `junkshop` | `f5515a21a1337b908212d8d76733530df83014464a24c585ab7b96000da0cdce` |
| `monster` | `8ff812a02f720433de1403dab447be9541eecca71cc919224a7790d017c1b2ce` |

CPU 仅接受一个精确 CPU 设备、16 个 CPU 线程及单插槽。GPU 仅接受一个精确 HIP 设备；按固定项目口径不筛选宿主 CPU。每条场景记录均核对 `number_of_samples × 60 ÷ time_for_samples` 与保存的 `samples_per_minute`，最大允许误差为 `1e-8`。每个 UUID 的三个场景总和作为该次完整提交分数，再对完整提交分数取中位数。CPU 333 份候选中，16 份未满足线程/插槽条件、33 份未通过固定启动器校验，得到 284 份完整提交；GPU 144 份候选中，11 份未通过固定启动器校验，得到 133 份完整提交。拒绝原因按筛选器遇到的首个不符合项计数。

## 可复核文件

下列 JSONL 保留快照中的完整原始 JSON 记录，不改写字段：

| 文件 | 内容 | 行数 / 大小 | SHA-256 |
| --- | --- | ---: | --- |
| `.local/product-tags-research/amd-targets.jsonl` | 两个目标合并后的 Windows Blender 4.3 型号候选 | 477 / 2,117,972 bytes | `8b8286955fc8a70175b1fa9a540b4715f8e18261ec4112e43526e73ccbfbba1a` |
| `.local/product-tags-research/amd-strict-complete.jsonl` | 固定口径下完整通过的原始提交 | 417 / 1,852,473 bytes | `739e0e0a8c722deb86b8f153855829f6d9108915f485218099940621b35a8b8a` |

`.local/product-tags-research/amd-analysis.json` 保存每件商品的精确设备名、候选与严格样本计数、全部严格样本 UUID 和原始分中位数。`.local/product-tags-research/research_amd.py` 可从同一 ZIP 流式重建候选集、核验快照 SHA-256、应用筛选条件、复算场景样本率并重新计算中位数。

## 第一方来源

- [AMD Ryzen 7 7800X3D 官方规格](https://www.amd.com/en/products/processors/desktops/ryzen/7000-series/amd-ryzen-7-7800X3d.html)
- [AMD Radeon RX 7800 XT 官方规格](https://www.amd.com/en/products/graphics/desktops/radeon/7000-series/amd-radeon-rx-7800-xt.html)
- [Blender Open Data 下载页与快照说明](https://opendata.blender.org/download/)
- [Blender Open Data 官方快照地址（latest，可变）](https://opendata.blender.org/snapshots/opendata-latest.zip)
- [Blender Open Data 分数说明](https://opendata.blender.org/about/#benchmark-score)
