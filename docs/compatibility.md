# 统一兼容检测与完整核心准入

任务11补齐的规则经 2026-11-08 用户调整后保留 C01～C06、C08～C10 九项，移除 C07 图形输出判断，显卡一栏仍必须明确选择核显或独显。提供 F02/F04/F05/F06 复用的 `CompatibilityService.check(revision, selections)`。调用方传当前商品引用，重要业务节点必须重新调用；客户端旧结果、参数和通过标识不构成准入依据。任务11不实现购物车、推荐或订单。

## HTTP 接口

`POST /api/compatibility/check` 匿名可用，沿用 Session＋CSRF 策略。先取 `GET /api/auth/csrf`，在其指定请求头中发送令牌。请求只允许修订号、每类一个真实商品 ID，以及可选的核显绑定 CPU 引用 `integratedGraphicsCpuId`：

```json
{"revision":3,"CPU":1,"MOTHERBOARD":3,"GPU":5,"MEMORY":7,"SSD":9,"PSU":11,"COOLER":8,"CASE":12}
```

选择核显时提交与 `CPU` 商品 ID 相同的 `integratedGraphicsCpuId`，并省略 `GPU`。该字段仅表示明确选择 CPU 自带核显，不能代替服务端核显能力核验，也不包含价格或参数。

`revision` 是非负安全整数；商品 ID 是正安全整数。还可传 `memoryQuantity`（1～4，默认 1；大于 1 时须选内存），C02 按实装总根数核验插槽。响应中的 `memorySlots` 是所选主板已核验的内存插槽数，无法核验时为 `null`。重复键、错误类别、类别与实际商品不符、客户端参数/金额/通过标识/规则开关或规则版本覆盖均拒绝 400/`INVALID_CONFIGURATION`。后台没有停用或绕过核心规则的入口。接口不改变库存或保存工作区，响应带 `Cache-Control: no-store`。

正常完成返回 HTTP 200 的 `data`：

```json
{
  "revision": 3,
  "ruleVersion": "f03-core-graphics-choice-v3",
  "checkedAt": "2026-11-08T03:00:00Z",
  "catalogVersion": "<64位SHA-256参数快照指纹>",
  "status": "CORE_PASSED",
  "complete": true,
  "corePassed": true,
  "checks": ["此处省略九项完整规则对象"],
  "missing": [],
  "unexecutedRules": [],
  "unexecutedExtensions": ["E01～E06，每项含rule与未执行原因"]
}
```

示例中的数组为说明性省略；实际 `checks` 按 C01～C06、C08～C10 顺序各返回一次，每项保留 `rule`、`state`、`categories`、实际读取的 `parameters`、可读 `message` 和逐项 `details`（类别、参数、值）。缺件列表保留中文标签、原因状态及说明，区分未选 `NOT_SELECTED` 与不存在 `NOT_FOUND`；显卡栏未明确选择核显或独显时返回 GPU 缺件。

检测运行失败返回 HTTP 503，`data.status=ERROR`、`corePassed=false`、请求修订/规则版本/时间，不带伪造成功结果或数据库内部信息。无可靠快照时 `catalogVersion=null`。输入错误仍是 400，不作为系统检测错误。

## 权威规则与分层

服务端在只读、REPEATABLE_READ 事务中按 ID 读取当前 `products` 与 `product_specifications.parameters`，不缓存结果。配件不存在才不能读取；下架与缺货并不抹除技术参数，因此核心兼容可能通过，但报价和下游交易仍必须独立核验可售性、库存、价格和权限。

| 规则 | 条件与读取参数 |
| --- | --- |
| C01 | CPU `socket` = 主板 `socket` |
| C02 | 内存 `generation` 属于主板 `memoryGenerations`，实际 `stickCount` ≤ `memorySlots` |
| C03 | CPU `socket` 属于独立散热器 `supportedSockets` |
| C04 | 主板 `formFactor` 属于机箱 `supportedMotherboardFormFactors` |
| C05 | SSD `formFactor`/`interface`/`protocol` 同时匹配一个主板 `ssdSlots` 有效安装组合，禁止拼接不同插槽能力 |
| C06 | 独显 `installationInterface` 属于主板 `gpuInterfaces`；PCIe 代际差异不自动失败 |
| C08 | PSU `ratedPower` ≥ `ceil(1.30 × (CPU peakPowerEstimate + GPU boardPowerEstimate + 80))`，无独显 GPU 项为0；使用精确十进制，禁止以 TDP 替代峰值 |
| C09 | GPU `length` ≤ CASE `maxGpuLength` |
| C10 | COOLER `height` ≤ CASE `maxCoolerHeight` |

功率用 W，尺寸用 mm，空间规则对应默认安装有效限制；相等通过。无独显时 C06/C09 为 `NOT_APPLICABLE`；已选独显不存在时 C06/C08/C09 为 `PENDING`，不能当作未选择而把功耗计0；失效独显引用仍使整体不完整。

七类基础配件 CPU、主板、内存、SSD、电源、机箱、独立散热器始终必需，显卡栏也必须明确选择核显或独立显卡。核显选择通过 `integratedGraphicsCpuId` 绑定当前 CPU，由服务端重新核实该 CPU 的 `integratedGraphics=true` 及来源；只选择有核显 CPU 不等于已选择核显。核显绑定不能与 GPU 引用共存，不能指向其他 CPU（否则 400）。无核显 CPU 不能用核显选择获得通过，必须选择独显。该要求是必选项校验，不是图形输出路径检查；不读取主板/显卡视频输出作 C07 判断。已选独显继续执行 C06/C08/C09，失效引用仍正常报告。

分项保留 PASS、FAIL、UNKNOWN、PENDING、NOT_APPLICABLE。字段复用商品写入的类型、正数范围与词汇校验，必须有 HTTP(S) 来源；null、畸形或无来源字段为 UNKNOWN。有效空视频输出/扩展接口集合表示已确认没有能力，不能当作未知。C02/C05 的已知冲突继续优先于同项其他未知值；其他分项结果始终保留。

| 综合状态 | 规则 |
| --- | --- |
| CORE_PASSED | 七类基础部件及显卡选择完整、所有已选引用有效，九项实际执行，全部适用项 PASS，仅 C06/C09 可以不适用；唯一 `corePassed=true` |
| INCOMPATIBLE | 正常检测存在任一 FAIL，即使同时缺件/未知；不隐藏明细和缺件 |
| INCOMPLETE | 没有 FAIL，但必要配件不完整，优先于未知 |
| UNVERIFIED | 完整且无 FAIL，但有 UNKNOWN/PENDING 等未通过状态 |
| ERROR | 本次检测系统异常，不能使用旧成功代替 |

P1 E01～E06 未执行，明确列于 `unexecutedExtensions`，不假称验证了 BIOS、频率/QVL、供电线材、散热承载、特殊空间或通道共享。核心通过不等于全面兼容或交易可用。

## 新鲜度与页面行为

`catalogVersion` 对规则版本、规范类别顺序、商品引用、当前状态和原始技术参数（含来源）生成 SHA-256，参数变化即使修订号不变也产生不同指纹。`revision` 标识工作区选择，`ruleVersion` 标识规则定义，`checkedAt` 标识检测时间。没有把指纹设计成可跳过重检的授权令牌。

DIY 选择变化自动检测、刷新价格与库存、窗口激活、页面重新可见，以及可见时每30秒触发重新读取。请求发起即清除旧结论；取消控制器和请求身份检查阻止旧响应覆盖新请求，响应修订必须匹配当前配置。前端拒绝旧规则版本、缺失/重复核心规则、非法不适用项和不一致的通过汇总。参数变动在下一次重检中识别；不声称存在实时推送或任何锁价。

页面以服务端综合状态展示结果，只有核心实际通过才显示绿色成功。兼容区仅显示冲突名称；原因、解决建议、未知参数和检测记录放在配置修订建议区；页面移除缺件逐项列表、原核心通过范围提示和主板建议后的内存代际/条数后缀。缺件仍由接口返回，并通过综合状态表示。检测失败保留可编辑选择并支持重试；未开放加购或购买按钮。

## 登录恢复时的复用

游客登录恢复（[DIY 工作区](diy-workspace.md)）直接调用本接口，不新增入口或参数：同一修订、同一组商品引用提交一次，得到服务端当前参数下的完整九项结论。恢复不会把登录前的检测结果当成通过依据，也不存在可提交的“已通过”字段。真正登录失败或未提交即取消不会触发登录恢复重验，工作区仍可进行常规后台重检；关闭在途登录后，若迟到响应表明服务端已建立会话且用户仍在 DIY，则按当前数据重验但不改变导航。工作区引用保持原状；恢复期间的新编辑使旧响应失效，响应修订仍须匹配当前修订，规则版本仍须为当前值。

## 数据与验证

无需迁移：读取 V4/V10/V15 已有参数，未修改历史迁移、未知 AMD 峰值或厂商事实。没有新增依赖、外部代码或数据。验证记录见[任务11证据](evidence/task-11.md)，审查见[两轴审查](evidence/task-11-review.md)。


2026-11-08 用户后续调整的验证见[取消图形输出与显卡完整性判断](evidence/graphics-rule-removed.md)。此前任务11证据保留为历史实现记录，以当前版本 `f03-core-graphics-choice-v3` 为准。


2026-11-08 页面进一步精简：配置修订建议不再显示「配置或参数变化后需重检」及手动「重新检测」按钮。所有配置变更自动检测现有硬件冲突，失败后的重试入口保留；显卡必选及九项规则保持不变。
