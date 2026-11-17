# 本人订单查询与模拟支付

任务18复用任务16、17的统一订单服务与成交快照，身份始终来自认证Session。

## 本人查询

- `GET /api/orders?page=1`：返回 `data: {items, page, pageSize, total, counts}`，默认每页20笔，可传 `pageSize=10/20/50`，按创建时间、订单号倒序。页码须为正整数；超过末页返回空列表。每笔包含订单号、状态、CNY成交金额、创建时间、支付截止、支付/取消时间及不可变单品和配置组快照，直接供订单卡片展示。
- 支持可选 `status`、`keyword`、`startDate`、`endDate`。状态仅接受四种订单状态；关键词最多100字符，对订单号和成交商品名称作字面包含匹配，`%`、`_`不作为通配符。日期使用ISO格式，按页面的Asia/Shanghai日历转换UTC边界，结束日期包含整日；无效日期范围返回400。所有筛选始终限定本人。
- `counts` 包含 `ALL`、`PENDING_PAYMENT`、`PAID`、`COMPLETED`、`CANCELLED`，统计关键词和日期范围内的本人订单；状态切换不改变这些标签数量。`total` 为包含状态筛选的结果数。
- `GET /api/orders/{number}`：返回本人订单详情，包括不可变单品和配置组快照、成交单价、小计和归属。
- 列表及详情只读取本人订单与成交快照，不联查当前商品覆盖历史。缩略图沿用已有品类示意图；所有响应禁止缓存。
- 未登录返回401；其他用户的订单与不存在的订单均返回404 / `ORDER_NOT_FOUND`。查询参数不能改变Session所属用户。

## 模拟支付

`POST /api/orders/{number}/pay`，请求体为空或 `{}`，须携带当前Session的CSRF凭证。客户端金额、用户、时间、状态等字段一律拒绝为400 / `INVALID_PAYMENT_INPUT`。

在同一MySQL事务中锁定本人订单，取得锁之后读取服务端Clock。只有 `PENDING_PAYMENT` 且 `now < paymentDeadline` 才转换为 `PAID`，同时保存UTC支付时间 `paidAt`（微秒精度）。金额沿用订单的CNY成交总额，不调用外部支付或再次改变可售库存、预占数量及购物车。

订单号及Session身份绑定同一笔支付操作，没有可变支付内容。已为 `PAID` 的重复请求返回该订单及原支付时间，即使重试已超过原支付截止，也不会重新执行支付。同一订单的并发请求由订单行锁串行化。

- 恰好截止或过期：409 / `ORDER_PAYMENT_EXPIRED`，不依赖超时扫描。
- `CANCELLED` 或 `COMPLETED`：409 / `ORDER_NOT_PAYABLE`。
- 持久化故障：沿用503 / `CART_UNAVAILABLE`，状态与支付时间一同回滚，原订单可安全重试。
- 前端明确提示站内模拟与成交金额，确认后提交；不明确的网络结果提示重新读取或重试同一订单，不能直接展示支付成功。

V25只新增可空 `mall_order.paid_at`，不修改既有迁移与成交字段。任务19已交付[本人取消与自动扫描](order-cancellation-timeout.md)，同样先锁定订单并遵守状态边界；详情和列表另返回取消原因、时间。管理员完成由后续任务交付。
