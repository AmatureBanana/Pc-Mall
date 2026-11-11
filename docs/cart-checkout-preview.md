# 整组编辑、实时诊断与预结算

任务15复用 V19/V20/V22 和现有 Session＋CSRF，不新增表、依赖、订单或库存预占。配置仍为七类核显或八类独显，每类一个 SKU，内存1～4根，其他各1件。

## 整组编辑

`GET /api/cart` 的每个 `configurations` 组包含 `editToken`、`state`、`reasons`。`editToken` 绑定该组当前明细、价格、库存、商品修订、参数及 F03 规则版本，不能作为库存或价格承诺。

`POST /api/cart/configurations/{id}` 接收既有配置输入 `selections`、可选 `integratedGraphicsCpuId`、`memoryQuantity`，加上编辑开始时的 `revision` 和 `editToken`。账户只取自 Session；不存在和他人组均为404。拒绝未知字段、重复 JSON 字段、非法数量和组内拆改。

购物车行锁串行化本账户写入，所有涉及商品按 ID 升序共享锁定，再建立一致读取快照。购物车修订或原组商品快照已变化时409 `CART_CONFLICT`；目标配置已属于另一组时409 `CONFIGURATION_DUPLICATE`。当前商品类别、可售性、全车需求和 F03 重新核验后，在同一事务内替换签名及全部明细，并保留原组 ID。任何失败回滚全部写入，原组不变。

浏览器从“返回 DIY 编辑”载入销售引用、数量和明确的显卡选择。取消不发替换请求；保存需主动确认。编辑结束后恢复原先的 DIY 工作区。网络结果未知时要求打开购物车核对后重新编辑，不能把网络断开描述为确定回滚。身份变化清除编辑上下文和在途请求。

## 实时诊断

读取使用当前价格、可售库存和参数。单品继续区分 `VALID`、`OFF_SALE`、`OUT_OF_STOCK`、`INSUFFICIENT_STOCK`；配置组增加 `INCOMPATIBLE` 和 `UNVERIFIED`。组的 `reasons` 同时保留库存与技术诊断，包含相关规则编号和原因；无法读取可靠参数时不放行。失效明细和组不会自动移除。

全车读取的库存提示按全车同 SKU 需求；`total` 包含全部当前金额，`validTotal` 仅包括完整有效组与有效单品。该提示不代替本次选中项的结算判断。

## 混合预结算

`POST /api/cart/checkout-preview`：

```json
{"revision":2,"itemIds":[11],"configurationIds":[5]}
```

只接受本人单品行 ID 和完整组 ID，不接受组内商品选择、客户端价格、数量或所属账户。两数组必须提供，至少选择一项；重复、非正整数或额外字段400。过期修订409 `CART_CONFLICT`；不存在/他人购物项404；选中项缺货、下架、兼容冲突或无法验证409 `CART_SELECTION_INVALID`。

服务端在同一事务内锁定购物车及商品后重新读取，仅按本次选择聚合同 SKU 需求并复验整组 F03。未选中项不参与有效性判断，跨项总需求不套单品99件限制。金额由 `BigDecimal` 计算，以两位小数字符串返回。

响应 `data` 包含 `revision`、`status`、`quoteToken`、服务端 `items`、完整 `configurations`、`total` 及 `skuDemand`（`productId`、`quantity`）。首次为 `CONFIRMATION_REQUIRED`。用户核对明细和变价提示后，带上相同选择及 `quoteToken` 再次请求：当前快照相同返回 `READY`；报价、商品或参数变化返回 `RECONFIRMATION_REQUIRED` 及新报价，必须再次确认。响应均 `Cache-Control: no-store`。

`quoteToken` 是绑定用户、修订、选中服务端购物项、数量、现价、商品修订和配置参数快照的比较指纹。它不是授权凭证，不持久保存，不锁价、不扣减或预占库存。`READY` 也仅表示本次预检查通过。后续 F06 必须重新加载本人购物项、核对修订与指纹、重验现价/参数/F03/同 SKU 需求，并在订单事务内预占；不能把客户端回传的明细或 `READY` 当成成交依据。

测试与审查见[任务15验证](evidence/task-15.md)及[任务15审查](evidence/task-15-review.md)。
