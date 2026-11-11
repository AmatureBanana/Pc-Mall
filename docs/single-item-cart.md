# 单品购物车契约

任务13提供本人持久化单品购物车：登录用户从商品详情加购真实 SKU，同 SKU 合并，可查看、修改数量与删除，退出重登后仍在。读取与写入都**不预占、不扣减库存，也不锁价**；预占与成交由后续订单任务负责。

## 数据与身份

V19 新增三张表：

| 表 | 含义 |
| --- | --- |
| `shopping_cart` | 每名账户一条逻辑购物车主记录（`account_id` 唯一），持有 `revision` |
| `shopping_cart_item` | 单品行，`(cart_id, product_id)` 唯一，`quantity` 约束在 1～99 |
| `cart_request` | 加购幂等记录，主键 `(account_id, request_key)`，保存内容指纹与首次结果 |

**归属只来自 Session**。所有接口从认证主体取账户 ID，请求中任何 `user_id`/`accountId` 字段都被拒绝（400，`INVALID_CART_INPUT`），不会被静默忽略。他人购物项按“不存在”应答（404，`CART_ITEM_NOT_FOUND`），不向陌生人确认其存在。

## 接口

全部要求已登录；匿名与失效会话为 401，响应带 `Cache-Control: no-store`。写请求沿用 Session＋CSRF 策略，缺少令牌为 403/`CSRF_INVALID`。购物车成功与错误响应都带 `no-store`；401/403 由共享安全链返回，不带业务缓存头但同样不缓存私有内容。

| 路径 | 请求 | 说明 |
| --- | --- | --- |
| `GET /api/cart` | — | 读取本人购物车；无购物车时返回 `revision: 0` 与空 `items` |
| `POST /api/cart/items` | `{productId, quantity, requestKey}` | 加购并合并同 SKU；`quantity` 为 1～99 |
| `POST /api/cart/items/{itemId}/quantity` | `{quantity}` | 设置**绝对值**而非增减量；1～99 且不超过当前可售量 |
| `DELETE /api/cart/items/{itemId}` | — | 删除单品行 |

`data` 结构：

```json
{
  "revision": 3,
  "items": [{
    "id": 11, "productId": 1, "sku": "INTEL-I5-14600KF", "name": "Intel Core i5-14600KF",
    "category": "CPU", "categoryLabel": "CPU", "brand": "Intel", "model": "i5-14600KF",
    "imageUrl": "/images/intel-cpu.svg", "quantity": 2,
    "price": "1499.00", "stock": 12,
    "state": "VALID", "message": "按件计价，金额为服务端当前售价。",
    "amount": "2998.00"
  }],
  "total": "2998.00", "validTotal": "2998.00", "validCount": 1, "lineCount": 1
}
```

- 商品名称、SKU、类别、品牌、型号、图片、售价与可售量都从当前 `products` 读取，不使用客户端金额或库存。金额用 `BigDecimal` 精确计算并以两位小数金额字符串返回。
- `state` 为 `VALID`、`OFF_SALE`、`OUT_OF_STOCK` 或 `INSUFFICIENT_STOCK`。失效行**保留**并附原因，`total` 仍按当前售价合计、`validTotal` 只合计 `VALID` 行，便于用户自行修复或移除。
- 读取不预占库存：`products.stock`、`products.reserved_stock` 与 `stock_reservations*` 均不变。

## 数量、库存与错误

| 情况 | 结果 |
| --- | --- |
| 商品不存在 | 404 `PRODUCT_NOT_FOUND` |
| 商品已下架 | 409 `PRODUCT_NOT_SELECTABLE` |
| 加购当前可售量为 0 的商品 | 409 `PRODUCT_NOT_SELECTABLE` |
| 合并后超过 99 | 409 `CART_QUANTITY_LIMIT` |
| 合并后或改量后超过当前可售库存 | 409 `CART_STOCK_LIMIT` |
| `quantity` 不在 1～99、非整数、商品 ID 非正整数、未知或伪造的所属字段 | 400 `INVALID_CART_INPUT` |
| 请求标识格式无效 | 400 `INVALID_CART_INPUT` |
| 购物项不存在或属于他人 | 404 `CART_ITEM_NOT_FOUND` |
| 幂等键已用于不同内容 | 409 `IDEMPOTENCY_KEY_REUSED` |
| 幂等记录存在但首次结果尚未落库 | 409 `CART_REQUEST_INCOMPLETE` |
| 购物车行在锁内未能写入 | 409 `CART_CONFLICT` |
| 数据库故障 | 503 `CART_UNAVAILABLE` |

`CART_REQUEST_INCOMPLETE` 理论上只在进程崩溃、`cart_request` 提交而结果未落库时出现，正常事务会整体回滚；保留该分支是为了不把未完成的重试当成成功加购。`CART_CONFLICT` 由购物车行锁保护，正常路径不可达，仅作为防止静默提交的兜底。

任何拒绝都**保持原记录不变**：例如已有 60 件时再加 40 件被拒绝，行的数量仍是 60，不会被截断到 99 或库存上限。改量同样如此，页面会把输入框恢复为服务端保存的数量。

可修复与不可修复的失效行要分开处理：`INSUFFICIENT_STOCK` 可把数量改到当前可售量以内恢复有效；`OFF_SALE` 与 `OUT_OF_STOCK` 没有可改到的合法数量，服务端拒绝改量（409 `PRODUCT_NOT_SELECTABLE`），页面禁用该行输入并提示移除。

99 件上限是**单行**规则，后续任务在跨行汇总同 SKU 需求时不机械套用（见母规格 F05 与任务15）。

## 重复请求与并发

`requestKey` 为 8～64 位字母、数字或 `._:-`，由页面为每次有意加购生成。

- **同键同内容重试**：返回首次结果，不再加一次。
- **同键不同内容**：409 `IDEMPOTENCY_KEY_REUSED`，不会静默应用新内容。
- **同内容新键**：视为新的一次加购并合并数量。
- **键的归属**：指纹绑定账户、操作、商品与数量，`cart_request` 主键另含 `account_id`，因此两个账户用字面相同的键也是不同请求。

页面不会自动重复写请求。若网络断开、响应损坏或 5xx 导致结果未确认，本页保留该账户、商品和数量对应的原键；用户再次点击会重试同一次加购。成功后，或首次尝试收到明确的 4xx 拒绝后，下一次点击使用新键。如果首次结果未知，重试时的 4xx 拒绝（例如 CSRF 失效）不能证明首次未提交，页面仍保留原键并提示首次结果待确认。成功重试可能重放首次快照，因此页面不接纳该旧快照，而是独立读取当前购物车；读取失败时保留已有视图，明确提示加购已成功但刷新失败。退出、会话失效或账户切换（包括 401）会废弃旧身份的在途请求与未确认操作；本页状态不跨重新加载保存。

并发写在同一购物车行锁内串行化：4 个各加 1 件的并发请求得到数量 4，不丢失；同一键的并发重试只加一次。任务14进一步在写入验证时共享锁定商品行，与后台修改协调；配置组按商品 ID 升序取得锁，单品改量先当前读取本人单品行。购物车始终不修改商品行或预占库存。完整配置去重、聚合和事务约定见[配置组契约](configuration-cart.md)。

## 页面

- 商品详情弹窗：已登录且可售时显示“加入购物车”（每次 1 件）；未登录显示“登录后加入购物车”，跳到统一登录。
- 登录后，主导航搜索框右侧的“购物车”显示合计徽标，打开独立 `#cart` 页面。左侧分列单品和配置组，可勾选、更新单品数量或删除；右侧按勾选的可售项汇总服务端返回的金额。组内配件只能随组勾选、移除。徽标按单品件数加方案组数统计。页面参考合计不代表结算报价。
- 登录过期时提示重新登录，不把 401 显示成规则错误。
- 退出登录或会话失效会清空页面上的购物车视图，不残留他人数据。
- 重新获得焦点时，账号栏检查当前账户 ID；若其他标签页切换了账户，先废弃旧账户的购物车请求再读取新账户。购物车请求的身份代次同时保护读取、写入与 CSRF 等待阶段，旧响应不得灌入新账户视图，旧操作也不得在取到 CSRF 后发给新账户。
- 同一账户的迟到响应按购物车 `revision` 接纳，不以较旧快照覆盖新提交；相同修订按请求顺序接纳，仍允许刷新当前价格与库存。各行独立保留处理中状态，保存或拒绝数量后输入框取最新服务端快照，不缓存过时的已保存数量。

## DIY 方案入车

确认“转入购物车”后，`POST /api/cart/configurations` 接收 `selections`（每项为 `category`、`productId`）及可选的 `integratedGraphicsCpuId`、`memoryQuantity`（默认 1）。服务端使用 Session 身份和当前商品数据，复用 F03 核验整套配置（含内存插槽），并检查加上此组后的同 SKU 总需求。组内保存各 SKU 数量；同一购物车内类别、SKU 与数量均相同的配置只保留一组，单品与整组不互相合并。

购物车响应的 `configurations` 包含各组的 `id`、`items`、`amount` 与可售状态 `valid`；`DELETE /api/cart/configurations/{id}` 只允许删除本人整组。现价金额包含单品和配置组，不预占或扣减库存。打开购物车后关闭可继续编辑原 DIY 方案。

## 边界

`revision` 表示当前购物车已提交的变更次数，页面也用它防止迟到响应覆盖更新的购物车快照；商品价格、库存变化不增加该修订，因此相同修订仍可刷新这些信息。结算、报价确认、订单、支付与库存预占仍属于后续任务。不新增前端依赖，未复制参考项目代码或第三方数据。
