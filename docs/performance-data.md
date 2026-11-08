# 性能参考、评分维护与受控样本

任务08，版本 `performance-v1` / 数据包 `performance-demo-v1`，2026-11-03。

本文保留任务08初始包记录。后续用户追加全商品标签，并以独立V16及AMD包补齐两款AMD参考；当前展示与扩展说明见[全商品性能标签](product-performance-tags.md)。V17 后，商城内存和SSD改用[容量优先综合评分](capacity-priority-scores.md)。下文的容量节点和“未纳入初始包”的缺分描述只对应初始版本。

## 固定策略与有效性

`PerformanceScores` 是后端统一的确定性入口，使用 BigDecimal、线性插值，结果按 HALF_UP 保留6位小数。CPU原始节点0/50/200/450/800、GPU原始节点0/500/2000/4500/8000均对应0/25/50/75/100；RAM节点0/8/16/32 GB对应0/50/80/100，SSD节点0/256/512/1000 GB对应0/40/75/100。输入达到满分后封顶100，负数拒绝，缺值返回空分。SSD 1TB=1000GB，1GiB=1.073741824GB，1TiB=1099.511627776GB；RAM 读取商品标称容量，当前在售内存按单根计量。

CPU/GPU的有效参考固定Blender 4.3.0、Windows及明确后端：CPU用CPU，NVIDIA用OPTIX，AMD用HIP。每份提交完整覆盖classroom/junkshop/monster，先求单次完整提交的总分，再取提交总分的中位数。后台维护采用至少3个可追溯独立UUID的准入要求，并要求来源、快照日期/SHA-256、采样条件与型号映射依据齐全；管理员负责核验录入的外部证据。服务端不在线抓取来源URL，也不把来源格式校验冒称为自动验证第三方事实。

`PerformanceReferences.read(productId)` 提供后续推荐复用的公开业务入口。公开商品详情在CPU/GPU/MEMORY/SSD时增加 `performance: {state,score,mappingVersion,reference,note}`，`score`始终显式返回十进制字符串或null。状态为VALID、MISSING、INVALID、STALE；其他类别不附性能对象。只有VALID有规范分，不能以零替代缺失。容量分从当前结构化参数计算并检查来源，SSD还检查原始单位与规范GB一致。原始容量仍在商品参数中保留。

CPU型号精确匹配品牌与型号，Intel K/KF不能互换。GPU保留准确商品型号、boardVariant和vram，允许有依据的GPU型号级参考：FE或AMD公版后缀不当作基准设备名。记录绑定商品品牌、型号和GPU变体/显存快照；这些信息改变后为STALE，需要重新核验。价格、库存等无关变更不会让参考分失效，但仍参与商品revision并发保护。

此分数是本项目内部的渲染/容量策略，不是Blender发布的等级，不代表游戏FPS或整机通用性能。GPU设备名不能证明板卡厂商和变体，页面及matchNote明确为型号级参考；已确认的场景权重没有在线编辑入口。

## 管理接口与页面

入口沿用 `/#admin/products` 的CPU/GPU商品编辑页。评分表单独立于商品资料表单；评分写入保留未保存资料，发生并发冲突时保留评分输入并提供“重新载入评分”。重新载入评分不会替旧商品资料草稿推进版本，避免覆盖其他管理员修改。

| 方法与路径 | 行为 |
| --- | --- |
| GET `/api/admin/products/{id}/performance` | 返回 `{data:{productId,revision,reference,performance}}`，包括下架商品；首次无参考为MISSING、reference为空对象 |
| POST `/api/admin/products/{id}/performance` | 完整维护参考或设置valid=false撤销有效性，成功返回同一结构 |
| GET `/api/admin/products/{id}/audit` | 复用最近100条商品审计，评分动作是PERFORMANCE |

POST字段：`revision`整数、`valid`布尔、`rawScore`非负十进制字符串、`sourceUrl`、`snapshotDate`（YYYY-MM-DD）、`snapshotSha256`、`benchmarkVersion`、`mappingVersion`、`deviceName`、`computeType`、`operatingSystem`、`scenes`字符串数组、`sampleCount`整数、`sampleIds`唯一UUID数组、`sampleConditions`、`matchNote`、`reason`。有效记录要求完整且符合固定口径；撤销可以保留待核验的来源信息，必须填写原因。拒绝未知字段，不能直接提交规范分或权重。无效记录仍只能产生空分。

匿名401，USER或缺CSRF为403；字段、条件或非CPU/GPU为400，商品不存在404，旧revision为409/PRODUCT_CONFLICT，数据库或审计故障为503。写入锁定商品、检查revision、保存参考与身份快照、递增商品revision，并在同一事务写入PERFORMANCE审计。任一步骤失败整体回滚。V14建存储表，V15追加受控种子；不修改已执行迁移。

## 原始证据与重现

官方来源：[Open Data下载/CC0](https://opendata.blender.org/download/)、[计分定义](https://opendata.blender.org/about/#benchmark-score)。捕获地址是可变的 `opendata-latest.zip`，因此归档身份由日期、长度及SHA-256共同确定，而非只凭URL：

- 捕获日期：2026-11-03；服务器Last-Modified：2026-11-03 00:01:23 UTC。
- 原ZIP长度102160171字节，SHA-256 `7e0875527aea34d96e99c933d1e82c62a20dac0a343720d252b12c21228812dd`。
- 原JSONL成员 `opendata-2026-11-03-000001+0000.jsonl`；不将约1.9GB解压文件或全量ZIP提交仓库。
- 仓库 `data/performance-v1` 包含458份原样完整提交的压缩JSONL、references、三场景SKU清单、LICENSE与带校验值的manifest。UUID可定位官方 `/benchmarks/{UUID}/`。

受控提取还固定Blender build `2b18cad88b13`、launcher 3.1.0与script 3.1.1的校验值、三个scene校验值、单计算设备、64位Windows和每场景30秒；CPU限定20线程、单插槽。逐条核对 `number_of_samples × 60 / time_for_samples` 与原始samples_per_minute，允许浮点序列化误差不超过1e-8。GPU不按宿主CPU筛选，社区频率、驱动和散热并未统一；保留原始系统字段并明确这些限制。发现一份KF提交虽然launcher标签相同但二进制checksum不同，已排除，详见研究报告。

| 精确设备 | 完整提交 | 原始总分中位数 | 内部规范分 |
| --- | ---: | ---: | ---: |
| Intel Core i5-14600K | 30 | 299.540210799599 | 59.954021 |
| Intel Core i5-14600KF | 33 | 294.048432257315 | 59.404843 |
| NVIDIA GeForce RTX 4070 SUPER | 395 | 6062.096305380457 | 86.157831 |

离线核验（不联网、不改文件）：

```powershell
python scripts/performance-data.py --verify
```

从已保存的同一官方ZIP重建（校验值不符立即失败，不自动升级快照）：

```powershell
python scripts/performance-data.py --write --snapshot .local/task-08-data/opendata-captured-2026-11-03.zip
```

脚本生成证据子集、references、manifest和V15；已有迁移上线后不可重写它，未来版本须新建迁移及数据包。CI/验收仅用verify核对提交产物。

数据是Blender Foundation Open Data的CC0归档，保留原始LICENSE.txt和来源归因；厂商参数为逐字段事实引用，不复制图片或长文、不主张厂商资料开放许可。

## 三场景样本与来源

新增 [Intel i5-14600K ARK SKU236799](https://www.intel.com/content/www/us/en/products/sku/236799/intel-core-i5-processor-14600k-24m-cache-up-to-5-30-ghz/specifications.html)：LGA1700、14核20线程、UHD770、DDR4/DDR5、基础125W、最大睿频181W。原始FCLGA1700规范为LGA1700；最大睿频值用于峰值估算，未拿TDP替代。售价1599 CNY、库存10是本项目演示值。

RTX4070 SUPER FE对应 [NVIDIA官方规格](https://www.nvidia.com/en-us/geforce/graphics-cards/40-series/rtx-4070-family/)及[官方指南](https://www.nvidia.com/content/geforce-gtx/GEFORCE_RTX_4070_SUPER_User_Guide_Rev1.pdf)：12GB、220W、244mm；Blender只作型号参考。其余商品沿用 [参数核验记录](catalog-parameters.md)。

三场景均复用ASUS DDR5版B760M、Kingston 16 GB 单根、Samsung 990 PRO 1TB、RM750e 750W、CC560及AK400；办公用14600K核显，游戏用14600KF+4070SUPER，创作用14600K+4070SUPER。演示总价分别为 5203.50、10102.50、10202.50 CNY，具体SKU见scenarios.json。

按冻结功率估算，办公340W、独显方案626W，现有750W满足模型；显卡244mm/机箱370mm、散热155mm/机箱163mm等核心输入齐备。这里只交付后续F03/F04的候选数据，未运行尚未实现的兼容或推荐引擎，不宣称实机、BIOS、内存超频或散热表现已验证。AMD等未纳入受控包的商品仍无有效参考分；故意缺失或伪造数据仅在测试夹具中出现。
