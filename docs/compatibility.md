# 兼容分项诊断（C01–C05）

任务10交付统一兼容规则服务的首个切片：C01～C05。C06～C10 与整机完整准入由任务11接管。本票不改变冻结规则，也不开放整机交易。

## 接口

公开、只读业务语义、写请求形态的检测入口：

```
POST /api/compatibility/check
Content-Type: application/json
```

请求体只允许一个 `revision` 与八类中每类最多一个正整数商品 ID：

```json
{"revision":3,"CPU":1,"MOTHERBOARD":3,"MEMORY":7,"SSD":9,"PSU":11,"COOLER":8,"CASE":12}
```

- 匿名可用但遵循项目既有 CSRF 策略（先取 `GET /api/auth/csrf`，用返回的 `headerName` 发送令牌）。
- 响应 `Cache-Control: no-store`，不持久化工作区，不改变库存。
- 不接受 `quantity`、`price`、`parameters`、`category` 覆盖、`compatibility` 或 `corePassed` 等客户端声称字段，含则 400/`INVALID_CONFIGURATION`。
- 重复 JSON 键（例如两个 `CPU`）按严格模式解析并拒绝 400，而不静默合并为一个引用。
- 商品不存在、已下架、类别与键不符时不得被当作通过；类别不符为 400，不存在与下架进入 `missing`。

## 服务端权威数据

每条规则只读取数据库当前 `products` + `product_specifications.parameters`：

| 规则 | 检查 | 读取参数 |
| --- | --- | --- |
| C01 | CPU 与主板插槽 | CPU `socket`、MOTHERBOARD `socket` |
| C02 | 内存代际与实际条数 | MOTHERBOARD `memoryGenerations`、`memorySlots`；MEMORY `generation`、`stickCount` |
| C03 | CPU 与独立散热器插槽 | CPU `socket`；COOLER `supportedSockets` |
| C04 | 主板板型与机箱支持集合 | MOTHERBOARD `formFactor`；CASE `supportedMotherboardFormFactors` |
| C05 | SSD 规格、接口与协议 | MOTHERBOARD `ssdSlots`；SSD `formFactor`、`interface`、`protocol` |

C05 只匹配主板已核验的**有效安装组合**（V10 的 `ssdSlots`），不对旧的三组集合做笛卡尔积。因此「M.2 2280 物理位置存在、但该位置只核验为 SATA/AHCI」会被判为协议冲突而不是规格冲突。

## 分项状态

| 状态 | 含义 |
| --- | --- |
| `PASS` | 该规则所有必要参数均已核验且有来源，且互相满足 |
| `FAIL` | 已核验条件足以确认冲突，即使同一规则的其他参数仍未知；同一次响应中其他规则结果不清空 |
| `UNKNOWN` | 没有已确认冲突，但关键参数为 null、集合为空、格式无效或缺少来源，导致无法完成判断 |
| `PENDING` | 该规则所需配件尚未选择，或选择已不可用（不存在、已下架），不能据此宣称通过 |
| `NOT_APPLICABLE` | 本切片没有产生该状态；由任务11 在核显路径等场景使用 |

没有来源地址的参数不参与可信比较；参数值为 null、空集合或缺失同样不能当作通过。值本身可能正确，但没有可核验来源就不能决定 `PASS`。诊断复用后台商品字段的类型与词汇校验：文本字段不能是数组、对象、布尔值或空文本，条数及插槽数必须是合法正整数，支持集合与安装组合必须符合统一词汇。目录数据格式异常时，该字段视为未知，不以字符串或布尔值强制转换数值，不因字段畸形返回 500 或丢失其他规则结果。

C02 分别检查代际与条数：DDR4 套装配仅支持 DDR5 的主板，即使套装条数未知也为 `FAIL`；已知条数超出插槽数时，即使代际未知也为 `FAIL`。C05 用 SSD 已核验的规格、接口和协议共同筛选每一个有效安装组合；没有任何组合满足已知条件即为 `FAIL`，仍有可能匹配但必要参数未知时才为 `UNKNOWN`，三个必要参数均已核验并匹配才为 `PASS`。未知字段不能遮蔽已确认冲突，也不能使不同插槽的能力被拼接为可用组合。

`PENDING` 的原因不靠规则消息猜测：`missing` 中每项带 `state`，区分 `NOT_SELECTED`（从未选择）、`NOT_FOUND`（商品不存在）与 `OFF_SALE`（已下架），界面据此分别显示，不会把用户已选但失效的配件说成「尚未选择」。

## 响应

```json
{
  "data": {
    "revision": 3,
    "ruleVersion": "f03-core-c01-c05-v1",
    "checkedAt": "2026-11-07T11:30:00Z",
    "corePassed": false,
    "checks": [
      {"rule":"C01","state":"PASS","categories":["CPU","MOTHERBOARD"],"parameters":["socket"],
       "message":"CPU 与主板插槽一致：LGA1700。",
       "details":[{"category":"CPU","parameter":"socket","value":"LGA1700"},
                  {"category":"MOTHERBOARD","parameter":"socket","value":"LGA1700"}]}
    ],
    "missing": [{"category":"GPU","label":"独立显卡","state":"NOT_SELECTED","message":"尚未选择独立显卡。"}],
    "unexecutedRules": [{"rule":"C06","reason":"任务11接入"}]
  }
}
```

- `checks` 恒含 `C01`～`C05` 五条，顺序固定，供调用方按规则识别与稳定比较。
- `details` 保留规则实际读取的参数和值，未知值显式为 `null`，不下发内部对象或客户端输入。
- `unexecutedRules` 明确列出 C06～C10 未执行；不能把未执行的规则伪装为通过。
- `corePassed` 在本切片恒为 `false`：只有任务11 的十项核心检查全部适用并通过时才可能为 `true`。
- `missing` 只覆盖本切片参与的七类基础必需件；独显为条件必需，由任务11 处理。
- `checkedAt` 取服务端时钟，用于识别过期结果；客户端仍须以 `revision` 匹配当前编辑。

## 前端行为

`/#diy` 第三栏在报价下方展示「兼容性检查」。选择变化后报价与检测并行发起，两者都用现有取消/过期保护：只接受与当前配置修订匹配的响应，旧响应不能覆盖新配置。

- 按用户要求移除常驻 C01～C05 列表及缺件/版本展开入口；兼容性检查仅为 `FAIL` 分项显示红色小圆点和冲突名称，具体原因与对应解决建议统一放在「配置修订建议」中，冲突消除后两处对应内容自动隐藏。PASS、PENDING、UNKNOWN 不形成问题列表，总体状态仍如实显示。完整诊断及版本信息继续由后端接口返回。
- 检测失败保留当前编辑引用并显示原因与「重试兼容检测」，不展示上一次成功结论。
- 无冲突但有缺件时提示「尚未选齐」；有冲突时「发现冲突」；参数未知时「存在未知参数」。
- 页面不出现「核心通过」的肯定表述，也不提供加购或下单入口。

## 范围边界

- 本票不新增数据库结构或迁移：规则读取 V4/V10 已核验参数，测试夹具使用隔离的 `pc_mall_test` schema。
- 不实现 C06～C10、核显输出路径、功率估算、尺寸核对、综合五态汇总、自动重检策略或后台规则开关。
- 未采用外部规则引擎或模板代码；规则为按冻结规格原创实现。
