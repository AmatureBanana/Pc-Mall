# 后台订单查询与完成交易

任务20，F06-23、F07-21～24。管理员使用统一 Session 和 CSRF，入口为 `/#admin/orders`。普通用户不能访问后台接口；本人订单接口仍按所属用户隔离。

## 接口

| 方法与路径 | 行为 |
| --- | --- |
| `GET /api/admin/orders` | 跨用户分页查询，返回 `data: {items, total, page, pageSize}` |
| `GET /api/admin/orders/{number}` | 返回 `data: {buyer: {id, username, nickname}, order}` |
| `POST /api/admin/orders/{number}/complete` | 完成已支付订单，返回相同详情结构 |
| `GET /api/admin/orders/{number}/audit` | 返回该订单最近100条业务审计，供核验与追溯 |

查询条件：`orderNumber`（最长40字符）、`username`（最长20字符）为忽略首尾空白的字面子串；`%`、`_` 不作为通配符。`accountId` 为可选正整数，`status` 为可选 PENDING_PAYMENT、PAID、COMPLETED、CANCELLED。`page` 从1开始，`pageSize` 为10、20或50，按下单时间、订单号倒序。非法条件返回400；合法空结果返回200。列表和详情均使用 no-store。

`buyer` 为订单所属账户的当前用户名和昵称；商品名称、SKU、型号、类别、单价、数量、金额以及配置组归属均读取 F06 下单快照，不联查商品覆盖历史。列表、详情和完成响应中的 `order` 与本人订单使用同一读取逻辑。

完成只接受空请求或单个空 JSON 对象。客户端金额、明细、状态、时间、用户或操作者字段、畸形 JSON 及尾随内容返回400/INVALID_COMPLETION_INPUT。没有通用订单编辑、恢复或后台取消接口。

## 状态与事务

F06 `SingleItemOrders.complete` 复用支付、取消使用的订单行锁。仅 PAID 转 COMPLETED，记录服务端 UTC 微秒时间与 Session 操作者用户名；COMPLETED 重试返回原结果，其余状态返回409/ORDER_NOT_COMPLETABLE。不存在返回404/ORDER_NOT_FOUND。完成不调用库存预占、扣减或释放。

V29 增加订单内部数字ID（共享审计的对象标识）、`completed_at`、`completed_by` 和后台按时间查询索引；公开标识仍为订单号。`completedBy` 保留操作时用户名，审计同时保留操作者ID、用户名、动作 COMPLETE 和转换前后订单快照。订单更新和审计处于同一事务，任一步失败整体回滚，可重试；并发或重复完成最多产生一次转换和一条有效业务审计。本人读取也返回完成时间、操作者字段。

管理页面显示购买人、成交分组、数量、金额和状态，仅已支付订单显示完成按钮。提交前确认订单号与金额；失败保留错误提示并支持重新载入或重试，401/403 清除当前详情。网络响应丢失后可安全重复提交。

验证命令与适用验收编号见 [任务20验证记录](evidence/task-20.md)。本票没有新增依赖或引入外部代码。
