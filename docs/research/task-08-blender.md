# Blender Open Data 4.3.0 性能数据采集

采集日期：2026-11-03（UTC）
状态：官方原始快照已获取并完成目标设备首轮筛选
用途：为任务08评分参考提供可追溯的 Blender Open Data 原始记录。

## 快照与来源

官方[下载页](https://opendata.blender.org/download/)将数据描述为每日快照，许可为 CC0；归档包内同时含 `README.txt` 与 `LICENSE.txt`。本次获取的来源地址为[官方快照地址](https://opendata.blender.org/snapshots/opendata-latest.zip)。该 `latest` 地址会变化；可复核的固定对象是本地实际捕获的 ZIP、内部文件名和 SHA-256，而不是 URL 本身。

| 项目 | 实际记录 |
| --- | --- |
| 本地快照 | `D:\Codex\JavaWeb\.local\task-08-data\opendata-captured-2026-11-03.zip` |
| 获取对象 | `opendata-latest.zip`；ZIP 内 JSONL 文件名为 `opendata-2026-11-03-000001+0000.jsonl` |
| 归档大小 | 102,160,171 bytes |
| SHA-256 | `7E0875527AEA34D96E99C933D1E82C62A20DAC0A343720D252B12C21228812DD` |
| 本地文件创建时间 | 2026-11-03 10:07:37 UTC |
| 本地文件最后写入时间 | 2026-11-03 10:09:12 UTC |
| 官方 HEAD 响应 | HTTP 200；`Last-Modified: Tue, 03 Nov 2026 00:01:23 GMT`；`ETag: "6ae92453-616d72b"`；`Content-Length: 102160171` |
| 归档许可 | CC0（归档 `README.txt` 与 `LICENSE.txt`；亦见官方[下载页](https://opendata.blender.org/download/)） |

ZIP 中 JSONL 解压长度为 1,900,550,683 bytes。文件按行保存 JSON 文档；全量扫描 435,680 行，未将 1.9 GB 解压副本另存到磁盘。读取到归档末尾且解析成功。

## 原始结构与计分

快照外层记录字段为 `id`、`created_at`、`schema_version`、`data`。`id` 是提交 UUID；`data` 中包含场景结果，目标记录的行字段包括：

- `blender_version.version` / `label` / `build_hash`
- `timestamp`
- `system_info.system`
- `device_info.device_type` 与 `device_info.compute_devices[{name,type}]`
- `scene.label`、`scene.checksum`
- `stats.samples_per_minute`、`number_of_samples`、`time_for_samples`、`time_limit`、渲染耗时和峰值显存

官方[基准说明](https://opendata.blender.org/about/#benchmark-score)把 Score 定义为各基准场景 samples/min 估值的总和。本次按一份 UUID 提交内三条完整场景记录的 `stats.samples_per_minute` 求和；对所有目标场景行使用 `number_of_samples × 60 ÷ time_for_samples` 重算，结果与原始 `samples_per_minute` 全部吻合，共核验 1,527/1,527 行。总分单位为 samples/min。

快照包含多个 schema 版本；所有匹配的 Blender 4.3.0 目标行均为 `schema_version=v4`，`blender_version.version=4.3.0`，build hash 均为 `2b18cad88b13`。全快照中 Blender 4.3.0 有 84,479 条场景结果，实际出现的场景及 checksum 恰为以下三项；场景数来自本次原始记录扫描，并未从 About 页面较早的“7 benchmarks”表述推断。

| 场景 | checksum |
| --- | --- |
| `classroom` | `936f0a528f03eba291c67ea87743a0ebde58938fb87471b262721b89b46b875d` |
| `junkshop` | `f5515a21a1337b908212d8d76733530df83014464a24c585ab7b96000da0cdce` |
| `monster` | `8ff812a02f720433de1403dab447be9541eecca71cc919224a7790d017c1b2ce` |

匹配规则逐字段精确比较：`blender_version.version == "4.3.0"`、`system_info.system == "Windows"`、`device_info.device_type` 等于目标后端，并且 `device_info.compute_devices` 中存在精确的 `(name,type)` 对。例如 `Intel Core i5-14600K` 与 `Intel Core i5-14600KF` 分开匹配，没有用子串搜索。完整提交定义为同一外层 UUID 恰有上述三种不同场景 checksum 各一条，且三条均有数值型 `stats.samples_per_minute`。

## 首轮目标样本汇总

以下是按任务初始条件（型号、Blender 版本、Windows、计算后端、完整三场景）得到的首轮样本；中位数对每个 UUID 的三场景总分取中位数。所有目标场景行的 `time_limit` 均为 30 秒。

| 精确设备名 | 后端 | 完整 UUID 提交数 | 场景行数 | 提交时间范围（UTC） | 总分中位数（samples/min） |
| --- | --- | ---: | ---: | --- | ---: |
| `Intel Core i5-14600K` | `CPU` | 32 | 96 | 2024-11-21 至 2025-03-09 | 297.5977140160 |
| `Intel Core i5-14600KF` | `CPU` | 42 | 126 | 2024-11-20 至 2025-07-23 | 292.8886496428 |
| `NVIDIA GeForce RTX 4070 SUPER` | `OPTIX` | 435 | 1,305 | 2024-11-20 至 2026-10-08 | 6061.9207801962 |

这509份完整提交、1,527条场景行全部保存于下面的首轮原始子集。GPU汇总是用户已同意的型号级 RTX 4070 SUPER 参考，不代表 Founders Edition 单卡实测；GPU提交的宿主 CPU、系统配置及驱动环境来自社区记录，彼此不固定。

## 样例原始提交

下表每行对应快照中一个完整外层 JSONL 记录；原始 `id`、时间、构建版本、OS、后端、每场景统计及其他字段均保留在子集中。各样例的总分等于右侧三个场景分数之和。

| UUID / 设备与后端 | 时间范围（UTC） | Blender / OS | 场景 samples/min：classroom / junkshop / monster | 总分 samples/min |
| --- | --- | --- | --- | ---: |
| `02d37a4c-a2d4-40f6-8fe0-03852d23a41e`；`Intel Core i5-14600K` / `CPU` | 2024-11-21 10:51:49.725945 至 10:53:09.832532 | 4.3.0，build `2b18cad88b13`；Windows | 47.750098 / 75.803757 / 104.599582 | 228.1534372874 |
| `f9d3427f-bb36-4b43-acca-38c4f37275bc`；`Intel Core i5-14600KF` / `CPU` | 2024-11-20 17:40:15.277113 至 17:41:32.361764 | 4.3.0，build `2b18cad88b13`；Windows | 65.403271 / 89.199638 / 132.660632 | 287.2635415862 |
| `b5fe0e45-9baa-4ffb-8744-42c95ca28e66`；`NVIDIA GeForce RTX 4070 SUPER` / `OPTIX` | 2024-11-20 22:56:56.499872 至 22:58:14.443731 | 4.3.0，build `2b18cad88b13`；Windows | 1,578.779615 / 1,504.509861 / 3,103.664801 | 6,186.9542775041 |

## 原始子集与可复核文件

首轮目标原始子集直接保留匹配外层 JSONL 行（含 UUID 与完整 `data`），不重写或编造字段：

| 文件 | 路径 / SHA-256 |
| --- | --- |
| 目标原始记录 | `D:\Codex\JavaWeb\.local\task-08-data\targets.jsonl`（509 行，2,235,200 bytes；SHA-256 `C36DA71F10CE092CDF85912A2225E33EC33993B3CAD9BA121F412CCFE2028A64`） |
| 全量字段与场景摘要 | `D:\Codex\JavaWeb\.local\task-08-data\analysis-summary.json` |
| 首轮中位数与提交明细 | `D:\Codex\JavaWeb\.local\task-08-data\computed-summary.json` |
| 每型号样例完整提交 | `D:\Codex\JavaWeb\.local\task-08-data\complete-samples.json` |
| 快照扫描脚本 | `D:\Codex\JavaWeb\.local\task-08-data\analyze_snapshot.py` |
| 子集汇总脚本 | `D:\Codex\JavaWeb\.local\task-08-data\summarize_targets.py` |

### 首轮结果和最终固定口径的区别

上表是任务初始设备、版本、OS、后端和完整场景条件下的社区数据中位数。完整提交中的 launcher 版本分布为：CPU K 有30份 `3.1.0`、2份 `3.2.0`；CPU KF 有34份 `3.1.0`、8份 `3.2.0`；GPU 有3份 `3.0.0`、395份 `3.1.0`、36份 `3.2.0`、1份 `3.3.0`。CPU 目标记录全部使用 benchmark script `3.1.1`；GPU 有434份使用 `3.1.1`、1份使用 `3.1.2`。所有目标记录 build hash 均为 `2b18cad88b13`，每场景 time limit 均为30秒。

设备配置分布核查显示，CPU K 与 KF 提交全部为 `device_info.num_cpu_threads=20`、`system_info.num_cpu_sockets=1`；GPU的435份记录均只有一个 OPTIX compute device，宿主 CPU 线程数各异，434份为单插槽、1份为双插槽。本首轮中位数没有按 launcher 或 benchmark script 再筛选。

因此，前文509份提交及中位数是**首轮筛选结果**，不能直接用作最终种子值。主任务的可复现脚本还按 launcher `3.1.0`、script `3.1.1` 的固定 checksum、build `2b18cad88b13`、30秒 time limit、三个固定 scene checksum 校验完整记录；CPU 限定20线程及单插槽，GPU限定单设备 OPTIX。最终受控结果如下，数值与生成的 `data/performance-v1/references.json` 一致：

| 精确设备名 | 受控完整提交数 | 受控中位总分（samples/min） |
| --- | ---: | ---: |
| `Intel Core i5-14600K` | 30 | 299.540210799599 |
| `Intel Core i5-14600KF` | 33 | 294.048432257315 |
| `NVIDIA GeForce RTX 4070 SUPER` | 395 | 6062.096305380457 |

### KF 提交差一条的原因

仅按 `benchmark_launcher.label == "3.1.0"` 统计会得到34份 KF 候选。严格脚本再核对 launcher checksum 后，UUID `3a8cda51-5b68-41ed-88eb-e942d95ad223` 被排除：它的三条场景记录均标记 launcher `3.1.0`，但实际 `benchmark_launcher.checksum` 为 `c30caaff0b836e68705ce2caf5e2bc12b8fd00df4bff2c627a37bad3e200572a`，与固定的 `5c65e95672826b04d3178ae02342fafcd2192987ab16ad54a5aee2346a254a40` 不同。对这份 UUID 的其余受控条件复核均通过，包括三个场景及 checksum、stored `samples_per_minute` 的复算、Blender build、OS/backend、script label/checksum、20线程/单插槽及30秒限制；因此差异来自 launcher 构建校验，不是场景不完整或计分计算误差。

## AMD 设备类型契约证据

快照中另有一份完整的 Windows Blender 4.3.0 单设备提交：UUID `e1c4f119-d5ef-4141-8d45-5e0eb6252619`（schema v4，记录时间 2024-11-21 09:15:01–09:16:35 UTC）。三条场景行的 `device_info.device_type` 都是 `HIP`，`device_info.compute_devices` 均只有一项，设备名精确为 `AMD Radeon RX 7800 XT`，该项 `type` 也是 `HIP`。因此对该 AMD 设备，原始后端/`computeType` 应为 `HIP`，不能把 GPU 类型硬编码为 `OPTIX`。此记录只作为评分数据模型可维护其他 GPU 后端的证据；本任务的可信性能样本仍仅使用已批准的 NVIDIA 参考，AMD 评分继续缺失。

## 官方来源

- [Blender Open Data 下载页与每日快照说明](https://opendata.blender.org/download/)
- [本次获取所用官方快照地址（可变 latest URL）](https://opendata.blender.org/snapshots/opendata-latest.zip)
- [Blender Open Data 基准指标说明](https://opendata.blender.org/about/#benchmark-score)
- [Blender Open Data 官方源仓库](https://projects.blender.org/infrastructure/blender-open-data)
