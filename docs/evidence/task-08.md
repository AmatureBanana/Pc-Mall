# 任务08验证记录

日期：2026-11-03（Asia/Shanghai）。任务：[08 性能评分维护与可信样本数据](../../.scratch/pc-mall/issues/08-performance-data.md)。实施基点 `23d6bf798314250c7b89bc3cd6b85d561039c394`（Task-07），当前分支Test；按用户要求以Task-08创建一个本地提交，保留用户已有 `.idea/jsLibraryMappings.xml`。

## 交付与测试边界

任务05已resolved，沿用母规格已确认的公开HTTP、公开确定性规则入口、真实独立MySQL和浏览器可观察行为边界。SQL仅用于测试夹具、故障注入与清理；持久化/审计结果通过HTTP核验。不访问演示库测试、不回退内存数据库，没有修改既有迁移或新增依赖。

交付固定评分入口、V14/V15迁移、ADMIN评分维护与原子审计、公开详情的有效/缺失/无效/失配状态、独立后台评分表单、受控原始数据包和三场景候选。具体接口、字段、精度、匹配边界及重现方式见[性能数据契约](../performance-data.md)。

## 资料搜寻与原始证据

实施期间使用独立上下文的 `gpt-6-luna`、推理 `max` 子智能体采集Blender官方快照及追加AMD HIP证据，主代理核验原始记录并实现可复现提取脚本。Intel与NVIDIA厂商事实延续计划期相同配置子智能体取得的第一方规格；来源链接进入性能数据契约，研究报告见[Blender报告](../research/task-08-blender.md)。

原ZIP SHA-256为 `7e0875527aea34d96e99c933d1e82c62a20dac0a343720d252b12c21228812dd`，大小102160171字节。最终固定4.3.0/Windows、构建及脚本/启动器校验值、单计算设备和30秒完整三场景，CPU20线程单插槽。K/KF精确分开；一份KF标签相同但启动器checksum不同的记录被排除。最终CPU K30、KF33、GPU395，共458份完整原样提交；逐场景复算1374组samples/min。没有直接使用查询页汇总值。

`python scripts/performance-data.py --verify`通过：压缩子集、原始内容摘要、references、scenarios、LICENSE与V15 SQL均匹配，场景值可从原始记录独立重算。另用Git索引内容逐一计算manifest覆盖文件的SHA-256，全部一致；`.gitattributes`防止检出时换行转换破坏证据字节。原LICENSE的结尾空行保持原样。

## 开发循环与专项验证

环境为JDK21、MySQL8.0.45、锁定Maven/前端依赖和本机Edge。通过已有脚本启动项目MySQL实例，未重建库。测试加载被忽略的 `.local/env.ps1`，仅使用pc_mall_test账户与schema。日志为 `.local/task-08-*.log`。

| 切片 | 红灯或发现 | 绿灯证据 |
| --- | --- | --- |
| 固定规则 | PerformanceScores缺失，规则测试编译失败：task-08-red-rules.log | CPU/GPU所有节点、区间中点、封顶、空/负值，RAM/SSD低端比例与单位换算：2/2 |
| 评分维护 | 新接口缺失，预期200实际500；初始清理表也尚未建成：task-08-red-api.log | 迁移后维护、公开新读取、审计及旧版本409通过；红灯夹具仅按本票t08_前缀在测试库清理 |
| AMD CPU | 厂商前缀匹配不完整，预期200实际400：task-08-red-amd.log | AMD精确型号匹配通过，不混用Intel K/KF |
| AMD GPU | 原实现将GPU统一为OPTIX，AMD HIP被拒绝：task-08-red-hip.log | 官方原始UUID证明HIP，补齐AMD公版型号后缀与后端，保留正常AMD商品无可信分状态 |
| 页面 | 缺少“性能评分维护”区域：task-08-red-browser.log | 评分维护、原因、草稿保留、并发409后输入保留、重新载入、撤销与旧资料冲突：1/1 |

后端专项命令：`./mvnw.cmd '-Dtest=PerformanceScoresTest,PerformanceAdministrationTest,PerformanceDataTest' test`；日志task-08-api-final.log，11/11通过。覆盖MISSING显式null、撤销INVALID、型号变化STALE、非法来源/版本/后端/场景/UUID及溢出、拒绝客户端规范分、401/403/CSRF、协调并发同revision恰一成功、真实MySQL审计CHECK故障后的业务/版本/审计整体回滚，以及种子分值与三场景SKU/容量/售价/库存。

Edge专项由 `.local/task-08-browser.ps1` 启动临时test profile服务与临时管理员，运行 `npm.cmd test -- tests/performance-administration.spec.ts`；task-08-green-browser.log为1/1通过。临时服务和管理员均在finally清理。截图已查看，评分表单、来源、采样条件和结果布局正常，见[后台截图](task-08-performance.png)。

## 最终完整验收

执行 `./scripts/test.ps1`，日志 `.local/task-08-full-tests.log`，退出码0：

| 检查 | 结果 |
| --- | --- |
| 隔离真实MySQL后端全套 | 58/58，失败0、错误0、跳过0 |
| Vue/TypeScript、Vite及同源JAR | 全部通过 |
| Edge浏览器全套 | 24/24，41.1秒 |
| 离线数据包复算与SHA-256 | 通过 |
| Standards / Spec独立审查 | 各0项实质问题，见[审查记录](task-08-review.md) |
| Git差异检查 | 通过；历史页面截图在回归后恢复，未混入本票 |

F07-T15覆盖本票的后台评分维护、缺评分、变更读取与审计部分；推荐结果受评分变更影响的联合验收由21/22完成。三场景仅核验真实候选数据与冻结规则输入是否齐备，不宣称尚未实现的F03/F04引擎已通过，也不宣称实机性能、BIOS或内存超频已验证。
