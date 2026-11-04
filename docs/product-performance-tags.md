# 全商品性能标签

用户追加需求：[任务26](../.scratch/pc-mall/issues/26-product-performance-tags.md)，2026-11-03。用户确认按类别显示分数或能力标签，并同时展示于商城商品卡片、搜索结果、详情、后台列表与详情。内存和SSD后续更新为[容量优先综合评分](capacity-priority-scores.md)。

## 展示与数据契约

| 类别 | 标签内容 | 依据与边界 |
| --- | --- | --- |
| CPU | 渲染参考分、核心数、线程数 | 固定Blender参考；不表示游戏FPS或整机性能 |
| GPU | 渲染参考分、显存、板卡功耗 | 型号级参考；功耗不是整机功耗，不冒称板卡变体实测 |
| 内存 | 容量优先综合评分、套装总容量、DDR代际 | 同容量比较标称速率和时序；不把标称档位当实测速度 |
| SSD | 容量优先综合评分、规范GB容量、协议 | 同容量比较厂商顺序读写；十进制1TB=1000GB，保留原始单位 |
| 主板 | 内存代际、内存插槽数、标称M.2槽数 | M.2数量来自m2Slots，不数支持组合；实际可用性受CPU等条件影响 |
| 电源 | 额定功率、ATX标准版本 | 不等同于整机功率适配或能效评分 |
| 机箱 | 显卡限长、散热器限高 | 标准安装净空，冷排等额外占位需另核验 |
| 散热器 | 完整高度、支持插槽 | 不采用裸塔高度，不宣称散热表现已验证 |

公开商品列表与公开详情的 `product` 增加 `performanceTags`。管理列表商品也包含该字段；管理详情在根层返回 `performanceTags`，内部商品身份字段不变。每个标签为 `{code,label,kind,sourceUrl,note}`；kind为SCORE、CAPABILITY、UNVERIFIED。正常记录有至少一个标签；缺失有效参考时显示“渲染评分待核验”或“综合评分待核验”，缺少任何有来源能力参数时显示“能力资料待核验”，不会补零分或沿用旧有效分。

标签由服务端当前结构化参数、字段单位/来源与统一评分入口生成，不存可随意编辑的标签副本，不解析展示摘要。商城和后台显示的有效评分四舍五入到小数点后一位，接口以 6 位小数返回规范分。参考撤销、身份失配或参数变化后下次读取立即反映。卡片使用紧凑标签，详情可打开来源并查看说明；后台显示已保存数据，未提交草稿不会预先改变有效标签。

列表在当前只读事务中先读商品，再以一条批量查询读取所有相关参数和参考；不逐商品查询。评分判定与原 `PerformanceReferences` 共用相同逻辑，公开与后台不建立平行评分规则。评分保存后只刷新标签，并保留商品草稿自己的并发版本。

## AMD参考扩展

资料搜寻按用户要求使用独立上下文的gpt-6-luna、推理max子智能体；主代理复核原始记录并生成包。来源见[AMD研究](research/product-performance-tags-amd.md)及[能力标签核查](research/product-performance-tags-capabilities.md)。

在任务08已捕获的同一Blender官方CC0快照中，增加 `data/performance-amd-v1` 独立包和V16迁移；原 `performance-v1` 包、V14/V15不重写。固定Blender4.3.0/build、Windows64bit、启动器3.1.0与脚本3.1.1校验值、单设备、30秒完整三场景；CPU为单插槽16线程，GPU为HIP且不筛宿主CPU。社区频率、驱动及散热未统一，原始字段保留。先对单次完整提交求总分，再取中位数。

| 商品 | 精确基准设备名 | 完整提交 | 原始中位数 | 内部6位分 |
| --- | --- | ---: | ---: | ---: |
| AMD Ryzen 7 7800X3D | AMD Ryzen 7 7800X3D 8-Core Processor | 284 | 253.065820239758 | 55.306582 |
| AMD Radeon RX 7800 XT 公版 | AMD Radeon RX 7800 XT / HIP | 133 | 2441.353996099423 | 54.413540 |

CPU允许AMD官方基准名的N-Core Processor后缀，但必须与已核验核心数一致，型号仍须完整精确匹配；错误核心数或7800X等近似型号拒绝。GPU继续采用已确认的型号级口径，不能说是AMD公版板卡单卡实测。CPU的120W为Default TDP，峰值功耗仍保留未知；没有为推荐准入填假值。

V16只在对应SKU缺参考时初始化，保留已有管理员参考。迁移推进对应商品revision，以便旧页面重新核验。当前875份完整提交由458份初始包加417份AMD包组成，均附UUID、原始JSONL、LICENSE和SHA-256。全量ZIP留在被忽略的.local，应用不联网依赖数据站。

```powershell
python scripts/performance-data.py --verify
python scripts/performance-amd-data.py --verify
```

AMD包重建：

```powershell
python scripts/performance-amd-data.py --write --snapshot .local/task-08-data/opendata-captured-2026-11-03.zip
```

仅同一SHA-256快照可重建。已执行迁移不可在线重写；后续改版使用新包与新迁移。两个数据包及生成SQL由Git字节规则保护，避免换行转换破坏校验。原始许可文件逐字节保留。
