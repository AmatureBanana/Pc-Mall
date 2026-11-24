# 任务 25 需求与验收追踪

使用实施规格所列 SHA-256 的原始报告与追踪矩阵。编号、原优先级、报告章节与关联均保留。执行依据见各场景对应行。[实施规格与基线指纹](../../.scratch/pc-mall/spec.md#来源基线与可追溯性) · [执行报告](task-25.md)

## 编号功能追踪

143 项 P0，另外保留 24 项 P1/P2 以便追踪。原矩阵的需求到验收关联按原样列出。按编号在下文查询用例结果和证据。公共商品、未编号约束及非功能要求见[补充追踪](task-25.md#公共商品与未编号约束)。

### F01 用户与权限

| 原功能编号 | 需求名称 | 原优先级 | 本版范围 | 报告章节 | 原矩阵关联验收 |
| --- | --- | --- | --- | --- | --- |
| F01-01 | 用户注册 | P0 | 适用 | 5.1.4 | F01-T01、F01-T02、F01-T03、F01-T16 |
| F01-02 | 用户登录 | P0 | 适用 | 5.1.4 | F01-T04、F01-T05、F01-T13 |
| F01-03 | 用户退出登录 | P0 | 适用 | 5.1.4 | F01-T06 |
| F01-04 | 登录状态验证与过期处理 | P0 | 适用 | 5.1.4 | F01-T07 |
| F01-05 | 角色访问权限控制 | P0 | 适用 | 5.1.4 | F01-T10 |
| F01-06 | 游客公共功能访问控制 | P0 | 适用 | 5.1.4 | F01-T08、F01-T09 |
| F01-07 | 查询个人资料 | P0 | 适用 | 5.1.4 | F01-T11 |
| F01-08 | 修改个人昵称 | P0 | 适用 | 5.1.4 | F01-T11 |
| F01-09 | 修改账户密码 | P0 | 适用 | 5.1.4 | F01-T12 |
| F01-10 | 账户正常、禁用状态校验 | P0 | 适用 | 5.1.4 | F01-T13 |
| F01-11 | 游客登录后的临时配置恢复 | P0 | 适用 | 5.1.4 | F01-T14、F01-T15 |
| F01-12 | 登录失败及安全异常处理 | P0 | 适用 | 5.1.4 | F01-T03、F01-T05、F01-T09、F01-T10 |
| F01-13 | 找回密码 | P1 | 未纳入 | 5.1.4 | 未单列专项编号 |
| F01-14 | 用户头像和扩展个人资料 | P1 | 未纳入 | 5.1.4 | 未单列专项编号 |
| F01-15 | 邮箱、手机号与第三方认证 | P2 | 未纳入 | 5.1.4 | 未单列专项编号 |

### F02 DIY 装机

| 原功能编号 | 需求名称 | 原优先级 | 本版范围 | 报告章节 | 原矩阵关联验收 |
| --- | --- | --- | --- | --- | --- |
| F02-01 | 进入 DIY 装机 | P0 | 适用 | 5.2.4 | F02-T01 |
| F02-02 | 硬件分类展示 | P0 | 适用 | 5.2.4 | F02-T07 |
| F02-03 | 候选商品查询 | P0 | 适用 | 5.2.4 | SYS-T01、F02-T10 |
| F02-04 | 选择硬件 | P0 | 适用 | 5.2.4 | F02-T02、F02-T03 |
| F02-05 | 查看硬件信息 | P0 | 适用 | 5.2.4 | F02-T07、SYS-T01 |
| F02-06 | 替换硬件 | P0 | 适用 | 5.2.4 | F02-T03、F02-T18 |
| F02-07 | 移除硬件 | P0 | 适用 | 5.2.4 | F02-T04、F02-T09 |
| F02-08 | 清空配置 | P0 | 适用 | 5.2.4 | F02-T17 |
| F02-09 | 实时统计总价 | P0 | 适用 | 5.2.4 | F02-T07、F02-T11 |
| F02-10 | 检查硬件完整性 | P0 | 适用 | 5.2.4 | F02-T05、F02-T06、F02-T09 |
| F02-11 | 检查图形输出条件 | P0 | 适用 | 5.2.4 | F02-T05、F02-T06 |
| F02-12 | 展示配置状态 | P0 | 适用 | 5.2.4 | F02-T08、F02-T09、F02-T16 |
| F02-13 | 调用兼容性检测 | P0 | 适用 | 5.2.4 | F02-T08 |
| F02-14 | 展示兼容性结果 | P0 | 适用 | 5.2.4 | F02-T08 |
| F02-15 | 临时配置维护 | P0 | 适用 | 5.2.4 | F02-T12 |
| F02-16 | 登录后配置恢复 | P0 | 适用 | 5.2.4 | F02-T13、F02-T14 |
| F02-17 | 整套配置购买资格检查 | P0 | 适用 | 5.2.4 | F02-T15、F02-T16 |
| F02-18 | 将配置交给购物车 | P0 | 适用 | 5.2.4 | F02-T15 |

### F03 兼容检测

| 原功能编号 | 需求名称 | 原优先级 | 本版范围 | 报告章节 | 原矩阵关联验收 |
| --- | --- | --- | --- | --- | --- |
| F03-01 | 配置数据接收 | P0 | 适用 | 5.3.4 | F03-T20、F03-T21 |
| F03-02 | 商品参数读取 | P0 | 适用 | 5.3.4 | F03-T18、F03-T24 |
| F03-03 | 配件完整性检查 | P0 | 适用 | 5.3.4 | F03-T11、F03-T17 |
| F03-04 | 核心规则检测 | P0 | 适用 | 5.3.4 | F03-T01、F03-T02、F03-T03、F03-T04、F03-T05、F03-T06、F03-T07、F03-T08、F03-T09、F03-T10、F03-T11、F03-T12、F03-T13、F03-T14、F03-T15、F03-T16 |
| F03-05 | 增量配置检测 | P0 | 适用 | 5.3.4 | F03-T17 |
| F03-06 | 自动触发检测 | P0 | 适用 | 5.3.4 | F03-T20 |
| F03-07 | 手动重新检测 | P0 | 适用 | 5.3.4 | 未单列专项编号 |
| F03-08 | 图形输出判断 | P0 | 适用 | 5.3.4 | F03-T10、F03-T11、F03-T12 |
| F03-09 | 单项状态判断 | P0 | 适用 | 5.3.4 | F03-T17、F03-T18、F03-T19 |
| F03-10 | 综合结果计算 | P0 | 适用 | 5.3.4 | F03-T11、F03-T17、F03-T18、F03-T19、F03-T23 |
| F03-11 | 冲突明细输出 | P0 | 适用 | 5.3.4 | F03-T02、F03-T03、F03-T07、F03-T08、F03-T09、F03-T15、F03-T16 |
| F03-12 | 缺失参数处理 | P0 | 适用 | 5.3.4 | F03-T18 |
| F03-13 | 结果可视化支持 | P0 | 适用 | 5.3.4 | F03-T11、F03-T18 |
| F03-14 | 检测异常处理 | P0 | 适用 | 5.3.4 | F03-T23 |
| F03-15 | 过期结果处理 | P0 | 适用 | 5.3.4 | F03-T20、F03-T21 |
| F03-16 | 推荐算法调用 | P0 | 适用 | 5.3.4 | F04-T10、F04-T11、F04-T12、F04-T13 |
| F03-17 | 整机提交前复核 | P0 | 适用 | 5.3.4 | F03-T24 |
| F03-18 | 游客检测支持 | P0 | 适用 | 5.3.4 | F03-T22 |
| F03-19 | 结果版本标识 | P0 | 适用 | 5.3.4 | F03-T20、F03-T21 |
| F03-20 | 扩展风险提醒 | P1 | 未纳入 | 5.3.4 | 未单列专项编号 |

### F04 智能推荐

| 原功能编号 | 需求名称 | 原优先级 | 本版范围 | 报告章节 | 原矩阵关联验收 |
| --- | --- | --- | --- | --- | --- |
| F04-01 | 游客和登录用户均可使用智能推荐 | P0 | 适用 | 5.4.4 | F04-T01 |
| F04-02 | 支持输入装机预算 | P0 | 适用 | 5.4.4 | F04-T02、F04-T03 |
| F04-03 | 支持选择办公、游戏、内容创作 | P0 | 适用 | 5.4.4 | F04-T02、F04-T06、F04-T07 |
| F04-04 | 按用途应用不同最低硬件要求 | P0 | 适用 | 5.4.4 | F04-T04、F04-T06、F04-T07、F04-T17 |
| F04-05 | 从统一商城商品数据中获取候选配件 | P0 | 适用 | 5.4.4 | F04-T15、F04-T23 |
| F04-06 | 筛选商品上架状态、库存与参数完整性 | P0 | 适用 | 5.4.4 | F04-T15、F04-T16 |
| F04-07 | 生成符合 F02 结构要求的完整装机配置 | P0 | 适用 | 5.4.4 | F04-T08、F04-T09 |
| F04-08 | 实施本模块的场景独显策略 | P0 | 适用 | 5.4.4 | F04-T04、F04-T05、F04-T06、F04-T07 |
| F04-09 | 调用 F03 统一兼容性检测服务 | P0 | 适用 | 5.4.4 | F04-T10、F04-T11、F04-T12、F04-T13 |
| F04-10 | 严格执行十项 P0 核心检查准入 | P0 | 适用 | 5.4.4 | F04-T10、F04-T11、F04-T12、F04-T13 |
| F04-11 | 读取并使用有效硬件性能评分 | P0 | 适用 | 5.4.4 | F04-T16 |
| F04-12 | 根据用途计算综合性能评分 | P0 | 适用 | 5.4.4 | F04-T17 |
| F04-13 | 在预算硬约束下搜索优化配置 | P0 | 适用 | 5.4.4 | F04-T18、F04-T19、F04-T20 |
| F04-14 | 使用确定性规则排序候选结果 | P0 | 适用 | 5.4.4 | F04-T21、F04-T22 |
| F04-15 | 展示完整推荐方案及价格明细 | P0 | 适用 | 5.4.4 | 未单列专项编号 |
| F04-16 | 展示基础推荐理由 | P0 | 适用 | 5.4.4 | 未单列专项编号 |
| F04-17 | 展示核心兼容结果及扩展风险提示 | P0 | 适用 | 5.4.4 | F04-T14 |
| F04-18 | 对无结果及异常分类处理 | P0 | 适用 | 5.4.4 | F04-T03、F04-T15、F04-T16、F04-T19、F04-T20 |
| F04-19 | 支持将推荐方案转入 F02 DIY 编辑 | P0 | 适用 | 5.4.4 | F04-T23 |
| F04-20 | 支持与 F05 购物车联动并遵守登录、重校验规则 | P0 | 适用 | 5.4.4 | F04-T24、F04-T25、F04-T26 |
| F04-21 | 多方案推荐 | P1 | 未纳入 | 5.4.4 | 未单列专项编号 |
| F04-22 | 推荐偏好设置 | P1 | 未纳入 | 5.4.4 | 未单列专项编号 |
| F04-23 | 历史推荐方案 | P1 | 未纳入 | 5.4.4 | 未单列专项编号 |
| F04-24 | 推荐方案对比 | P1 | 未纳入 | 5.4.4 | 未单列专项编号 |
| F04-25 | 详细推荐解释 | P1 | 未纳入 | 5.4.4 | 未单列专项编号 |
| F04-26 | 推荐规则管理 | P1 | 未纳入 | 5.4.4 | 未单列专项编号 |

### F05 购物车

| 原功能编号 | 需求名称 | 原优先级 | 本版范围 | 报告章节 | 原矩阵关联验收 |
| --- | --- | --- | --- | --- | --- |
| F05-01 | 购物车访问控制 | P0 | 适用 | 5.5.4 | F05-T01、F05-T02 |
| F05-02 | 购物车持久化 | P0 | 适用 | 5.5.4 | F05-T03 |
| F05-03 | 单品加入 | P0 | 适用 | 5.5.4 | F05-T04、F05-T05 |
| F05-04 | 单品合并 | P0 | 适用 | 5.5.4 | F05-T04 |
| F05-05 | 数量修改 | P0 | 适用 | 5.5.4 | F05-T05 |
| F05-06 | 单品删除 | P0 | 适用 | 5.5.4 | F05-T06 |
| F05-07 | 配置加入 | P0 | 适用 | 5.5.4 | F05-T07、F05-T08 |
| F05-08 | 核心兼容性复核 | P0 | 适用 | 5.5.4 | F05-T09 |
| F05-09 | 商品有效性复核 | P0 | 适用 | 5.5.4 | F05-T11、F05-T12 |
| F05-10 | 配置结构检查 | P0 | 适用 | 5.5.4 | F05-T10 |
| F05-11 | 配置分组展示 | P0 | 适用 | 5.5.4 | F05-T08、F05-T13 |
| F05-12 | 分组完整性 | P0 | 适用 | 5.5.4 | F05-T10、F05-T22 |
| F05-13 | 配置组删除 | P0 | 适用 | 5.5.4 | F05-T14 |
| F05-14 | 配置组修改 | P0 | 适用 | 5.5.4 | F05-T15、F05-T16 |
| F05-15 | 重复配置处理 | P0 | 适用 | 5.5.4 | F05-T17 |
| F05-16 | 混合购物车 | P0 | 适用 | 5.5.4 | F05-T13 |
| F05-17 | 库存汇总校验 | P0 | 适用 | 5.5.4 | F05-T18 |
| F05-18 | 价格更新提示 | P0 | 适用 | 5.5.4 | F05-T19 |
| F05-19 | 金额计算 | P0 | 适用 | 5.5.4 | F05-T20 |
| F05-20 | 失效状态处理 | P0 | 适用 | 5.5.4 | F05-T21 |
| F05-21 | 结算选择 | P0 | 适用 | 5.5.4 | F05-T22 |
| F05-22 | 结算交接 | P0 | 适用 | 5.5.4 | F05-T23 |
| F05-23 | 游客登录衔接 | P0 | 适用 | 5.5.4 | F05-T24 |
| F05-24 | 异常与重复请求处理 | P0 | 适用 | 5.5.4 | F05-T05、F05-T16、F05-T17、F05-T23 |
| F05-25 | 配置分组重命名 | P1 | 未纳入 | 5.5.4 | F05-T25 |
| F05-26 | 批量删除 | P1 | 未纳入 | 5.5.4 | F05-T26 |
| F05-27 | 同配置多套购买 | P1 | 未纳入 | 5.5.4 | F05-T27 |
| F05-28 | 价格变化明细 | P1 | 未纳入 | 5.5.4 | F05-T28 |

### F06 订单与模拟支付

| 原功能编号 | 需求名称 | 原优先级 | 本版范围 | 报告章节 | 原矩阵关联验收 |
| --- | --- | --- | --- | --- | --- |
| F06-01 | 交易身份验证 | P0 | 适用 | 5.6.4 | F06-T01、F06-T21、F06-T23、F06-T24 |
| F06-02 | 选中项目结算 | P0 | 适用 | 5.6.4 | F06-T02 |
| F06-03 | 混合购物结算 | P0 | 适用 | 5.6.4 | F06-T03 |
| F06-04 | 配置组完整结算 | P0 | 适用 | 5.6.4 | F06-T04 |
| F06-05 | 购物车数据验证 | P0 | 适用 | 5.6.4 | F06-T05 |
| F06-06 | 商品状态复核 | P0 | 适用 | 5.6.4 | F06-T06 |
| F06-07 | 商品价格确认 | P0 | 适用 | 5.6.4 | F06-T07 |
| F06-08 | 聚合库存检查 | P0 | 适用 | 5.6.4 | F06-T08、F06-T15 |
| F06-09 | 配置完整性复核 | P0 | 适用 | 5.6.4 | F06-T09 |
| F06-10 | 核心兼容性复核 | P0 | 适用 | 5.6.4 | F06-T10 |
| F06-11 | 订单金额计算 | P0 | 适用 | 5.6.4 | F06-T11 |
| F06-12 | 原子订单创建 | P0 | 适用 | 5.6.4 | F06-T12 |
| F06-13 | 订单基本信息 | P0 | 适用 | 5.6.4 | F06-T13 |
| F06-14 | 成交快照保存 | P0 | 适用 | 5.6.4 | F06-T14 |
| F06-15 | 库存预占 | P0 | 适用 | 5.6.4 | F06-T15 |
| F06-16 | 成功结算清理 | P0 | 适用 | 5.6.4 | F06-T16 |
| F06-17 | 失败结算保护 | P0 | 适用 | 5.6.4 | F06-T17 |
| F06-18 | 支付超时处理 | P0 | 适用 | 5.6.4 | F06-T18 |
| F06-19 | 模拟支付 | P0 | 适用 | 5.6.4 | F06-T19 |
| F06-20 | 支付幂等控制 | P0 | 适用 | 5.6.4 | F06-T20 |
| F06-21 | 用户取消订单 | P0 | 适用 | 5.6.4 | F06-T21 |
| F06-22 | 取消库存恢复 | P0 | 适用 | 5.6.4 | F06-T22 |
| F06-23 | 管理员完成订单 | P0 | 适用 | 5.6.4 | F06-T23 |
| F06-24 | 订单查询与详情 | P0 | 适用 | 5.6.4 | F06-T24 |
| F06-25 | 高级订单筛选 | P1 | 未纳入 | 5.6.4 | F06-T25 |
| F06-26 | 模拟支付凭证 | P1 | 未纳入 | 5.6.4 | F06-T26 |
| F06-27 | 订单状态时间线 | P1 | 未纳入 | 5.6.4 | F06-T27 |
| F06-28 | 支付倒计时交互 | P1 | 未纳入 | 5.6.4 | F06-T28 |

### F07 后台管理

| 原功能编号 | 需求名称 | 原优先级 | 本版范围 | 报告章节 | 原矩阵关联验收 |
| --- | --- | --- | --- | --- | --- |
| F07-01 | 统一后台登录 | P0 | 适用 | 5.7.4 | F07-T01 |
| F07-02 | 后台服务端授权 | P0 | 适用 | 5.7.4 | F07-T02 |
| F07-03 | 后台布局与反馈 | P0 | 适用 | 5.7.4 | F07-T03 |
| F07-04 | 商品分页查询 | P0 | 适用 | 5.7.4 | F07-T04 |
| F07-05 | 新增硬件商品 | P0 | 适用 | 5.7.4 | F07-T05 |
| F07-06 | 修改商品基础信息 | P0 | 适用 | 5.7.4 | F07-T06 |
| F07-07 | 维护专属参数 | P0 | 适用 | 5.7.4 | F07-T07 |
| F07-08 | 商品上架校验 | P0 | 适用 | 5.7.4 | F07-T08 |
| F07-09 | 商品上下架 | P0 | 适用 | 5.7.4 | F07-T09 |
| F07-10 | 查询商品详情 | P0 | 适用 | 5.7.4 | F07-T10 |
| F07-11 | 修改当前售价 | P0 | 适用 | 5.7.4 | F07-T11 |
| F07-12 | 查询可售库存 | P0 | 适用 | 5.7.4 | F07-T12 |
| F07-13 | 手动调整库存 | P0 | 适用 | 5.7.4 | F07-T13 |
| F07-14 | 库存事务协同 | P0 | 适用 | 5.7.4 | F07-T14 |
| F07-15 | 维护性能评分 | P0 | 适用 | 5.7.4 | F07-T15 |
| F07-16 | 保护核心兼容规则 | P0 | 适用 | 5.7.4 | F07-T16 |
| F07-17 | 查询普通用户 | P0 | 适用 | 5.7.4 | F07-T17 |
| F07-18 | 查询必要用户资料 | P0 | 适用 | 5.7.4 | F07-T18 |
| F07-19 | 启用禁用普通用户 | P0 | 适用 | 5.7.4 | F07-T19 |
| F07-20 | 保护角色与管理员账户 | P0 | 适用 | 5.7.4 | F07-T20 |
| F07-21 | 后台订单查询 | P0 | 适用 | 5.7.4 | F07-T21 |
| F07-22 | 查询订单成交快照 | P0 | 适用 | 5.7.4 | F07-T22 |
| F07-23 | 管理员完成订单 | P0 | 适用 | 5.7.4 | F07-T23 |
| F07-24 | 复用订单受控服务 | P0 | 适用 | 5.7.4 | F07-T24 |
| F07-25 | 统一数据变更生效 | P0 | 适用 | 5.7.4 | F07-T25 |
| F07-26 | 关键管理操作留痕 | P0 | 适用 | 5.7.4 | F07-T26 |
| F07-27 | 运营概览 | P1 | 未纳入 | 5.7.4 | F07-T27 |
| F07-28 | 批量导入商品参数 | P1 | 未纳入 | 5.7.4 | F07-T28 |
| F07-29 | 管理推荐场景权重 | P1 | 未纳入 | 5.7.4 | F07-T29 |
| F07-30 | 查询审计日志 | P1 | 未纳入 | 5.7.4 | F07-T30 |
| F07-31 | 上传商品图片 | P1 | 未纳入 | 5.7.4 | F07-T31 |
| F07-32 | 导出授权统计数据 | P1 | 未纳入 | 5.7.4 | F07-T32 |

## 原验收场景

保留 185 个原始编号。系统与 F04 由源文件保留「源文件未单列」优先级，本版按原预期中的功能范围核验。F04-T14 同时保留 P0 的范围提示和 P1 的风险缺口。

用户确认 F02-T05、F02-T06、F03-T10、F03-T11、F03-T12 本版采用任务 29 的规则。各行保留原预期，并在证据列写明本版验证条件。

`#方法名` 继承左侧最近的 Java 测试类。浏览器条目按测试标题或实际断言定位。任务 24 仅引用已运行的原始证据。

### SYS 系统场景

| 原验收编号 | 原优先级 | 报告章节 | 原场景与预期 | 本版范围 | 执行状态 | 证据或剩余条件 |
| --- | --- | --- | --- | --- | --- | --- |
| SYS-T01 | 源文件未单列 | 9.2 | 游客浏览和搜索商品；无须登录即可查看商城商品及其详情。 | 适用 P0 | 通过 | [product-catalog](../../frontend/tests/product-catalog.spec.ts) · 两条 SYS-T01，用 500 件正式目录重跑 |
| SYS-T02 | 源文件未单列 | 9.2 | 游客进行 DIY 选配；可选择、替换配件并查看配置和相关检查信息。 | 适用 P0 | 通过 | [diy-workspace](../../frontend/tests/diy-workspace.spec.ts) · arbitrary order, replacement, removal |
| SYS-T03 | 源文件未单列 | 9.2 | 游客按预算、用途请求推荐；返回符合支持规则的配置结果，或提供合理失败说明。 | 适用 P0 | 通过 | [office-recommendation](../../frontend/tests/office-recommendation.spec.ts) + [scenario-recommendation](../../frontend/tests/scenario-recommendation.spec.ts) · 真实办公、游戏、创作预算结果 |
| SYS-T04 | 源文件未单列 | 9.2 | 游客尝试加入购物车；要求登录，不允许未认证游客直接进行交易。 | 适用 P0 | 通过 | [configuration-cart](../../frontend/tests/configuration-cart.spec.ts) · guest signs in and explicitly confirms + [SingleItemCartTest](../../backend/src/test/java/cn/zhuangjimao/cart/SingleItemCartTest.java)`#guestAndForeignOwnerCannotReadOrChangeThisCart` |
| SYS-T05 | 源文件未单列 | 9.2 | 游客登录后继续装机；恢复本次配置并重新校验当前商品、价格、库存和兼容性；不自动加购。 | 适用 P0 | 通过 | [guest-restoration](../../frontend/tests/guest-restoration.spec.ts) · authoritative revalidation and no cart side effect |
| SYS-T06 | 源文件未单列 | 9.2 | 将整套配置加入购物车；保留各真实硬件商品及配置分组关系，不新增整机 SKU。 | 适用 P0 | 通过 | [configuration-cart](../../frontend/tests/configuration-cart.spec.ts) · guest signs in and explicitly confirms + [ConfigurationCartTest](../../backend/src/test/java/cn/zhuangjimao/cart/ConfigurationCartTest.java)`#sevenCategoryIntegratedBuildPersistsAcrossLoginAndCanOnlyBeRemovedAsAWhole` |
| SYS-T07 | 源文件未单列 | 9.2 | 购物车同时存在单品与配置；可以区分展示和正确汇总实际商品，进入合法的结算流程。 | 适用 P0 | 通过 | [mixed-order](../../frontend/tests/mixed-order.spec.ts) · 单品与两组同单，检查状态、组归属及金额 |
| SYS-T08 | 源文件未单列 | 9.2 | 创建订单并完成模拟支付；正确生成交易快照，状态由待支付转为已支付。 | 适用 P0 | 通过 | [mixed-order](../../frontend/tests/mixed-order.spec.ts) · 同一混合订单以成交金额支付并确认已支付 |
| SYS-T09 | 源文件未单列 | 9.2 | 取消待支付订单；状态合法转换为已取消，并按确认的库存策略正确处理。 | 适用 P0 | 通过 | [order-cancellation-timeout](../../frontend/tests/order-cancellation-timeout.spec.ts) · owner confirms cancellation + [OrderCancellationTimeoutTest](../../backend/src/test/java/cn/zhuangjimao/cart/OrderCancellationTimeoutTest.java)`#ownerCancellationReleasesStockOnceAndKeepsTheCartEmpty` |
| SYS-T10 | 源文件未单列 | 9.2 | 管理员完成已支付订单；状态合法转换为已完成，不涉及真实物流交付。 | 适用 P0 | 通过 | [mixed-order](../../frontend/tests/mixed-order.spec.ts) · 同一已支付订单收到后台完成确认，用户刷新可见已完成 |
| SYS-T11 | 源文件未单列 | 9.2 | 商品价格、库存或上下架状态变化；后续选配、推荐和下单读取有效数据，不能直接使用失效商品状态。 | 适用 P0 | 通过 | [DiyWorkspaceTest](../../backend/src/test/java/cn/zhuangjimao/catalog/DiyWorkspaceTest.java)`#freshQuoteReflectsPriceStockAndSaleChangesWithoutCachingOrLosingReferences` + [ScenarioRecommendationTest](../../backend/src/test/java/cn/zhuangjimao/catalog/ScenarioRecommendationTest.java)`#unavailableOrUnscoredCpuCannotBecomeASuccess` + [SingleItemOrderTest](../../backend/src/test/java/cn/zhuangjimao/cart/SingleItemOrderTest.java)`#changedQuotesStaleCartsForeignItemsAndForgedDetailsCannotCreateOrders` + [任务 24](task-24.md) F07-T25 |
| SYS-T12 | 源文件未单列 | 9.2 | 核心硬件不兼容或参数无法判断；不将有问题或未经验证的整机配置误当成合格方案。 | 适用 P0 | 通过 | [CompatibilityCheckTest](../../backend/src/test/java/cn/zhuangjimao/compatibility/CompatibilityCheckTest.java)`#aRealDatabaseConflictFailsOneRuleWithoutClearingTheOthers` + #unknownPowerNeverPassesAndParameterChangesInvalidateTheSameRevision + [ConfigurationCartTest](../../backend/src/test/java/cn/zhuangjimao/cart/ConfigurationCartTest.java)`#guestCsrfForgedPassingFieldsAndInvalidStructureCannotCreatePartialGroups` |
| SYS-T13 | 源文件未单列 | 9.2 | 非管理员尝试访问后台受保护能力；服务端拒绝无权操作。 | 适用 P0 | 通过 | [SessionAuthTest](../../backend/src/test/java/cn/zhuangjimao/auth/SessionAuthTest.java)`#initializedAdministratorUsesTheSameLoginAndHasNavigationWhileGuestsAreDenied` + [UserAdministrationTest](../../backend/src/test/java/cn/zhuangjimao/auth/UserAdministrationTest.java)`#authorizationCsrfProtectedFieldsAndAdministratorsCannotBeBypassed` + [order-administration](../../frontend/tests/order-administration.spec.ts) · 普通用户直接访问管理端接口返回 403 |

### F01 用户与权限场景

| 原验收编号 | 原优先级 | 报告章节 | 原场景与预期 | 本版范围 | 执行状态 | 证据或剩余条件 |
| --- | --- | --- | --- | --- | --- | --- |
| F01-T01 | P0 | 5.1.12 | 使用有效资料注册；成功创建普通用户账户 | 适用 P0 | 通过 | [SessionAuthTest](../../backend/src/test/java/cn/zhuangjimao/auth/SessionAuthTest.java)`#registrationCreatesOnlyAnOrdinaryUserAndRequiresSeparateLogin` |
| F01-T02 | P0 | 5.1.12 | 使用重复用户名注册（含大小写变体）；拒绝注册 | 适用 P0 | 通过 | [SessionAuthTest](../../backend/src/test/java/cn/zhuangjimao/auth/SessionAuthTest.java)`#simultaneousCaseVariantRegistrationsCreateExactlyOneAccount` |
| F01-T03 | P0 | 5.1.12 | 密码或其他注册字段不符合要求；拒绝注册并明确提示 | 适用 P0 | 通过 | [SessionAuthTest](../../backend/src/test/java/cn/zhuangjimao/auth/SessionAuthTest.java)`#registrationRejectsInvalidFieldsAndCannotAcceptAnAdministratorRole` |
| F01-T04 | P0 | 5.1.12 | 使用正确凭证登录；创建有效会话，可访问本人受保护功能 | 适用 P0 | 通过 | [SessionAuthTest](../../backend/src/test/java/cn/zhuangjimao/auth/SessionAuthTest.java)`#loginRotatesSessionAndCsrfAndLogoutRevokesTheOldCookie` + [profile-password](../../frontend/tests/profile-password.spec.ts) · 登录后读取本人资料 |
| F01-T05 | P0 | 5.1.12 | 使用错误凭证登录；拒绝登录，不泄露敏感认证细节 | 适用 P0 | 通过 | [SessionAuthTest](../../backend/src/test/java/cn/zhuangjimao/auth/SessionAuthTest.java)`#incorrectCredentialsHaveUniformFeedbackAndDisabledSessionsLoseAccess` |
| F01-T06 | P0 | 5.1.12 | 已登录用户退出；会话失效，不能继续访问受保护资源 | 适用 P0 | 通过 | [SessionAuthTest](../../backend/src/test/java/cn/zhuangjimao/auth/SessionAuthTest.java)`#loginRotatesSessionAndCsrfAndLogoutRevokesTheOldCookie` |
| F01-T07 | P0 | 5.1.12 | Session 过期；受保护接口识别为未登录 | 适用 P0 | 通过 | [SessionAuthTest](../../backend/src/test/java/cn/zhuangjimao/auth/SessionAuthTest.java)`#sessionCookieIsLocalHttpSafeAndExpiredSessionsCannotReadOrWritePrivateResources` |
| F01-T08 | P0 | 5.1.12 | 游客访问商品浏览、DIY、推荐；无需登录即可使用公开功能 | 适用 P0 | 通过 | [product-catalog](../../frontend/tests/product-catalog.spec.ts) + [diy-workspace](../../frontend/tests/diy-workspace.spec.ts) + [office-recommendation](../../frontend/tests/office-recommendation.spec.ts) · 游客 3 类公开入口 |
| F01-T09 | P0 | 5.1.12 | 游客访问购物车/下单；被认证拦截，不能操作 | 适用 P0 | 通过 | [SingleItemCartTest](../../backend/src/test/java/cn/zhuangjimao/cart/SingleItemCartTest.java)`#guestAndForeignOwnerCannotReadOrChangeThisCart` + [SingleItemOrderTest](../../backend/src/test/java/cn/zhuangjimao/cart/SingleItemOrderTest.java)`#changedQuotesStaleCartsForeignItemsAndForgedDetailsCannotCreateOrders` |
| F01-T10 | P0 | 5.1.12 | 普通用户访问管理员接口；拒绝访问 | 适用 P0 | 通过 | [UserAdministrationTest](../../backend/src/test/java/cn/zhuangjimao/auth/UserAdministrationTest.java)`#authorizationCsrfProtectedFieldsAndAdministratorsCannotBeBypassed` |
| F01-T11 | P0 | 5.1.12 | 用户查询或修改资料；只能操作本人信息 | 适用 P0 | 通过 | [ProfilePasswordTest](../../backend/src/test/java/cn/zhuangjimao/auth/ProfilePasswordTest.java)`#profileExposesOnlyTheSessionOwnersNecessaryFields` + #nicknameUpdatesPersistOnlyForTheOwnerAndEnforceUnicodeBoundariesAndProtectedFields |
| F01-T12 | P0 | 5.1.12 | 修改密码；必须验证旧密码，新密码生效且旧会话失效 | 适用 P0 | 通过 | [ProfilePasswordTest](../../backend/src/test/java/cn/zhuangjimao/auth/ProfilePasswordTest.java)`#changingPasswordChecksTheOldSecretAndRevokesAllOldSessionsWithoutAffectingOtherAccounts` + [profile-password](../../frontend/tests/profile-password.spec.ts) · 两个浏览器会话 |
| F01-T13 | P0 | 5.1.12 | 被禁用账户登录或访问受保护接口；禁止登录/访问 | 适用 P0 | 通过 | [UserAdministrationTest](../../backend/src/test/java/cn/zhuangjimao/auth/UserAdministrationTest.java)`#disablingAndEnablingIsAuditedAndRevokesAllOldSessionsWithoutLosingAccountData` |
| F01-T14 | P0 | 5.1.12 | 游客完成配置后登录；恢复本次临时配置 | 适用 P0 | 通过 | [guest-restoration](../../frontend/tests/guest-restoration.spec.ts) · authoritative revalidation and no cart side effect |
| F01-T15 | P0 | 5.1.12 | 登录恢复时商品价格、库存或状态变化；使用当前数据重新校验并提示，避免错误加购 | 适用 P0 | 通过 | [guest-restoration](../../frontend/tests/guest-restoration.spec.ts) · price and parameter changes + off-sale or out-of-stock + restocked references |
| F01-T16 | P0 | 5.1.12 | 公共注册请求尝试指定管理员；仍不能创建 ADMIN 账户 | 适用 P0 | 通过 | [SessionAuthTest](../../backend/src/test/java/cn/zhuangjimao/auth/SessionAuthTest.java)`#registrationRejectsInvalidFieldsAndCannotAcceptAnAdministratorRole` |

### F02 DIY 装机场景

| 原验收编号 | 原优先级 | 报告章节 | 原场景与预期 | 本版范围 | 执行状态 | 证据或剩余条件 |
| --- | --- | --- | --- | --- | --- | --- |
| F02-T01 | P0 | 5.2.12 | 游客进入 DIY 页面；无需登录即可进入并操作。 | 适用 P0 | 通过 | [diy-workspace](../../frontend/tests/diy-workspace.spec.ts) · F02 workspace |
| F02-T02 | P0 | 5.2.12 | 以非固定顺序选取不同类别商品；正常完成选择，未强制规定顺序。 | 适用 P0 | 通过 | [diy-workspace](../../frontend/tests/diy-workspace.spec.ts) · F02 workspace + [DiyWorkspaceTest](../../backend/src/test/java/cn/zhuangjimao/catalog/DiyWorkspaceTest.java)`#guestQuotesArbitraryOrderWithOneSalesUnitPerCategoryAndExactMoney` |
| F02-T03 | P0 | 5.2.12 | 同类别选择另一商品；原项被替换，配置中不存在重复类别商品。 | 适用 P0 | 通过 | [diy-workspace](../../frontend/tests/diy-workspace.spec.ts) · F02 workspace 的同类替换断言 |
| F02-T04 | P0 | 5.2.12 | 移除已选配件；配置清单、总价、完整性和相关检测状态正确更新。 | 适用 P0 | 通过 | [diy-workspace](../../frontend/tests/diy-workspace.spec.ts) · F02 workspace 的移除和总价断言 + [compatibility-diagnostics](../../frontend/tests/compatibility-diagnostics.spec.ts) · 自动更新 |
| F02-T05 | P0 | 5.2.12 | 选择七类基础硬件，CPU 和主板构成有效核显路径；可以认定配件组成完整；能否作为有效整机还需 F03 核心检查全部通过。 | 适用 P0（任务 29） | 通过 | 本版预期与证据。七类基础硬件已齐，明确选择绑定当前 CPU 的核显后完整并通过。[compatibility-diagnostics](../../frontend/tests/compatibility-diagnostics.spec.ts) · graphics must be explicitly chosen [任务 29](../../.scratch/pc-mall/issues/29-remove-graphics-output-check.md) |
| F02-T06 | P0 | 5.2.12 | CPU 无核显且未选独显；提示无有效图形输出路径，不可作为有效整机购买。 | 适用 P0（任务 29） | 通过 | 本版预期与证据。无核显 CPU 且未选独显，结果为 INCOMPLETE，禁止整组交易。[CompatibilityCheckTest](../../backend/src/test/java/cn/zhuangjimao/compatibility/CompatibilityCheckTest.java)`#anExplicitGraphicsChoiceIsRequiredWithoutCheckingTheDisplayOutputPath` + [compatibility-diagnostics](../../frontend/tests/compatibility-diagnostics.spec.ts) · 显卡缺项 [任务 29](../../.scratch/pc-mall/issues/29-remove-graphics-output-check.md) |
| F02-T07 | P0 | 5.2.12 | 八类硬件均选择有效商品；正确展示全部明细及计算后的参考总价。 | 适用 P0 | 通过 | [diy-workspace](../../frontend/tests/diy-workspace.spec.ts) · all eight sales units + [DiyWorkspaceTest](../../backend/src/test/java/cn/zhuangjimao/catalog/DiyWorkspaceTest.java)`#guestQuotesArbitraryOrderWithOneSalesUnitPerCategoryAndExactMoney` |
| F02-T08 | P0 | 5.2.12 | 选择不兼容 CPU 与主板；展示 F03 冲突结论，仍可编辑，不得当作有效整机加购。 | 适用 P0 | 通过 | [compatibility-diagnostics](../../frontend/tests/compatibility-diagnostics.spec.ts) · only confirmed conflicts become bullet points + [diy-cart-admission](../../frontend/tests/diy-cart-admission.spec.ts) · transfer stays disabled |
| F02-T09 | P0 | 5.2.12 | 从完整配置中移除必需硬件；立即显示配置不完整。 | 适用 P0 | 通过 | [compatibility-diagnostics](../../frontend/tests/compatibility-diagnostics.spec.ts) · unfinished selections + [CompatibilityCheckTest](../../backend/src/test/java/cn/zhuangjimao/compatibility/CompatibilityCheckTest.java)`#unselectedPartsArePendingAndAMissingProductIsReportedAsMissing` |
| F02-T10 | P0 | 5.2.12 | 已选商品被管理员下架；重新校验后标记异常，不当作可售。 | 适用 P0 | 通过 | [diy-workspace](../../frontend/tests/diy-workspace.spec.ts) · off-sale and out-of-stock quotation feedback |
| F02-T11 | P0 | 5.2.12 | 管理员更改已选商品价格；刷新／重新查询后使用新的有效售价重新计价。 | 适用 P0 | 通过 | [DiyWorkspaceTest](../../backend/src/test/java/cn/zhuangjimao/catalog/DiyWorkspaceTest.java)`#freshQuoteReflectsPriceStockAndSaleChangesWithoutCachingOrLosingReferences` |
| F02-T12 | P0 | 5.2.12 | 游客在当前标签页刷新 DIY 页面；在支持的本次临时状态内恢复所选配置。 | 适用 P0 | 通过 | [diy-workspace](../../frontend/tests/diy-workspace.spec.ts) · automatically saves only this tab references and survives reload |
| F02-T13 | P0 | 5.2.12 | 游客装机后登录成功；恢复临时配置，重新核验商品、价格、库存和兼容性，不自动加购。 | 适用 P0 | 通过 | [guest-restoration](../../frontend/tests/guest-restoration.spec.ts) · authoritative revalidation and no cart side effect |
| F02-T14 | P0 | 5.2.12 | 未登录游客尝试将配置加入购物车；要求登录，不能绕过身份限制进行交易。 | 适用 P0 | 通过 | [configuration-cart](../../frontend/tests/configuration-cart.spec.ts) · guest signs in and explicitly confirms + [ConfigurationCartTest](../../backend/src/test/java/cn/zhuangjimao/cart/ConfigurationCartTest.java)`#guestCsrfForgedPassingFieldsAndInvalidStructureCannotCreatePartialGroups` |
| F02-T15 | P0 | 5.2.12 | 已登录用户提交完整且 P0 检测通过的可售配置；向 F05 提交真实商品和配置分组关联，不生成整机 SKU。 | 适用 P0 | 通过 | [ConfigurationCartTest](../../backend/src/test/java/cn/zhuangjimao/cart/ConfigurationCartTest.java)`#sevenCategoryIntegratedBuildPersistsAcrossLoginAndCanOnlyBeRemovedAsAWhole` + [configuration-cart](../../frontend/tests/configuration-cart.spec.ts) · seven-part build |
| F02-T16 | P0 | 5.2.12 | 已登录用户尝试提交缺件或未通过关键检测的配置；阻止作为有效整机加购并给出原因。 | 适用 P0 | 通过 | [ConfigurationCartTest](../../backend/src/test/java/cn/zhuangjimao/cart/ConfigurationCartTest.java)`#guestCsrfForgedPassingFieldsAndInvalidStructureCannotCreatePartialGroups` + [diy-cart-admission](../../frontend/tests/diy-cart-admission.spec.ts) · transfer stays disabled |
| F02-T17 | P0 | 5.2.12 | 用户点击清空配置并确认；全部已选硬件被移除，工作区状态重置。 | 适用 P0 | 通过 | [diy-workspace](../../frontend/tests/diy-workspace.spec.ts) · F02 workspace 的清空确认 |
| F02-T18 | P0 | 5.2.12 | 用户修改由 F04 推荐生成的配置；当前配置重新更新，旧推荐评分／解释不再作为有效结果。 | 适用 P0 | 通过 | [diy-workspace](../../frontend/tests/diy-workspace.spec.ts) · DIY scene assessment starts when a recommended configuration is manually changed + [scenario-recommendation](../../frontend/tests/scenario-recommendation.spec.ts) · keeps its score until a DIY edit |

### F03 兼容检测场景

| 原验收编号 | 原优先级 | 报告章节 | 原场景与预期 | 本版范围 | 执行状态 | 证据或剩余条件 |
| --- | --- | --- | --- | --- | --- | --- |
| F03-T01 | P0 | 5.3.12 | CPU 与主板插槽相同。；C01 PASS。 | 适用 P0 | 通过 | [CompatibilityRulesTest](../../backend/src/test/java/cn/zhuangjimao/compatibility/CompatibilityRulesTest.java)`#c01PassesWhenCpuAndMotherboardSocketsMatch` |
| F03-T02 | P0 | 5.3.12 | CPU 与主板插槽不匹配。；C01 FAIL。 | 适用 P0 | 通过 | [CompatibilityRulesTest](../../backend/src/test/java/cn/zhuangjimao/compatibility/CompatibilityRulesTest.java)`#c01FailsOnAKnownMismatchAndReportsBothActualValues` |
| F03-T03 | P0 | 5.3.12 | DDR5 主板搭配 DDR4 内存。；C02 FAIL。 | 适用 P0 | 通过 | [CompatibilityRulesTest](../../backend/src/test/java/cn/zhuangjimao/compatibility/CompatibilityRulesTest.java)`#c02FailsWhenTheMotherboardDoesNotListTheMemoryGeneration` |
| F03-T04 | P0 | 5.3.12 | 双插槽主板搭配四条装内存 SKU。；C02 FAIL。 | 适用 P0 | 通过 | [CompatibilityRulesTest](../../backend/src/test/java/cn/zhuangjimao/compatibility/CompatibilityRulesTest.java)`#c02FailsWhenTheKitSticksExceedThePhysicalSlots` + [CompatibilityCheckTest](../../backend/src/test/java/cn/zhuangjimao/compatibility/CompatibilityCheckTest.java)`#requestedMemoryModulesMustFitVerifiedBoardSlots`。规则输入仍核对 4 对 2，当前商城按 4 根单条规格表达 |
| F03-T05 | P0 | 5.3.12 | 散热器支持当前 CPU 插槽。；C03 PASS。 | 适用 P0 | 通过 | [CompatibilityRulesTest](../../backend/src/test/java/cn/zhuangjimao/compatibility/CompatibilityRulesTest.java)`#c03PassesWhenTheCoolerListsTheCpuSocketAndFailsWhenItDoesNot` |
| F03-T06 | P0 | 5.3.12 | 散热器不支持当前 CPU 插槽。；C03 FAIL。 | 适用 P0 | 通过 | [CompatibilityRulesTest](../../backend/src/test/java/cn/zhuangjimao/compatibility/CompatibilityRulesTest.java)`#c03PassesWhenTheCoolerListsTheCpuSocketAndFailsWhenItDoesNot` |
| F03-T07 | P0 | 5.3.12 | 主板板型不在机箱支持集合内。；C04 FAIL。 | 适用 P0 | 通过 | [CompatibilityRulesTest](../../backend/src/test/java/cn/zhuangjimao/compatibility/CompatibilityRulesTest.java)`#c04PassesWhenTheCaseListsTheBoardFormFactorAndFailsWhenItDoesNot` |
| F03-T08 | P0 | 5.3.12 | NVMe SSD 搭配仅支持 SATA 的 M.2 插槽。；C05 FAIL。 | 适用 P0 | 通过 | [CompatibilityCheckTest](../../backend/src/test/java/cn/zhuangjimao/compatibility/CompatibilityCheckTest.java)`#anM2SlotThatOnlyLinksSataFailsNvmeOnProtocolRatherThanOnFormFactor` |
| F03-T09 | P0 | 5.3.12 | 独显安装接口无法被主板支持。；C06 FAIL。 | 适用 P0 | 通过 | [CompatibilityAdmissionRulesTest](../../backend/src/test/java/cn/zhuangjimao/compatibility/CompatibilityAdmissionRulesTest.java)`#c06ChecksTheInstallationInterfaceWithoutRejectingDifferentPcieGenerations` |
| F03-T10 | P0 | 5.3.12 | CPU 有核显、主板具有有效输出能力且不选独显。；C07 PASS。 | 适用 P0（任务 29） | 通过 | 本版预期与证据。显式选择核显即可补齐显卡栏。主板输出字段不参与兼容结果，九项核心检查无 C07。[CompatibilityCheckTest](../../backend/src/test/java/cn/zhuangjimao/compatibility/CompatibilityCheckTest.java)`#anExplicitGraphicsChoiceIsRequiredWithoutCheckingTheDisplayOutputPath` [任务 29](../../.scratch/pc-mall/issues/29-remove-graphics-output-check.md) |
| F03-T11 | P0 | 5.3.12 | CPU 无核显且没有独显。；C07 FAIL，整机不可提交；同时提示条件必需独显缺失。 | 适用 P0（任务 29） | 通过 | 本版预期与证据。无核显且未选独显，准确指出 GPU 缺项，核心不得通过。[CompatibilityCheckTest](../../backend/src/test/java/cn/zhuangjimao/compatibility/CompatibilityCheckTest.java)`#anExplicitGraphicsChoiceIsRequiredWithoutCheckingTheDisplayOutputPath` [任务 29](../../.scratch/pc-mall/issues/29-remove-graphics-output-check.md) |
| F03-T12 | P0 | 5.3.12 | CPU 无核显但选择了有效独显。；C07 PASS。 | 适用 P0（任务 29） | 通过 | 本版预期与证据。已选有效独显时完成显卡选择，九项核心检查通过并返回 CORE_PASSED。[CompatibilityCheckTest](../../backend/src/test/java/cn/zhuangjimao/compatibility/CompatibilityCheckTest.java)`#completeDiscreteBuildGetsTheOnlyCoreAdmissionAndVersionIdentity` + #guestReadsCurrentDatabaseParametersAndSeesEachFinishedRuleApart [任务 29](../../.scratch/pc-mall/issues/29-remove-graphics-output-check.md) |
| F03-T13 | P0 | 5.3.12 | 电源功率低于统一模型计算需求。；C08 FAIL。 | 适用 P0 | 通过 | [CompatibilityAdmissionRulesTest](../../backend/src/test/java/cn/zhuangjimao/compatibility/CompatibilityAdmissionRulesTest.java)`#c08UsesTheFrozenCeilingFormulaAndAllowsExactPowerIncludingNoDiscreteGpu` |
| F03-T14 | P0 | 5.3.12 | 电源功率恰好等于统一模型的规定需求。；C08 PASS。 | 适用 P0 | 通过 | [CompatibilityAdmissionRulesTest](../../backend/src/test/java/cn/zhuangjimao/compatibility/CompatibilityAdmissionRulesTest.java)`#c08UsesTheFrozenCeilingFormulaAndAllowsExactPowerIncludingNoDiscreteGpu` |
| F03-T15 | P0 | 5.3.12 | 显卡长度超过机箱限制。；C09 FAIL。 | 适用 P0 | 通过 | [CompatibilityAdmissionRulesTest](../../backend/src/test/java/cn/zhuangjimao/compatibility/CompatibilityAdmissionRulesTest.java)`#spatialRulesAllowEqualLimitsAndRejectEvenAFractionAboveThem` |
| F03-T16 | P0 | 5.3.12 | 散热器高度超过机箱限制。；C10 FAIL。 | 适用 P0 | 通过 | [CompatibilityAdmissionRulesTest](../../backend/src/test/java/cn/zhuangjimao/compatibility/CompatibilityAdmissionRulesTest.java)`#spatialRulesAllowEqualLimitsAndRejectEvenAFractionAboveThem` |
| F03-T17 | P0 | 5.3.12 | 缺少主板，其余已选。；INCOMPLETE（无更高优先级明确冲突时），且可执行项目照常检测。 | 适用 P0 | 通过 | [CompatibilityCheckTest](../../backend/src/test/java/cn/zhuangjimao/compatibility/CompatibilityCheckTest.java)`#unselectedPartsArePendingAndAMissingProductIsReportedAsMissing` |
| F03-T18 | P0 | 5.3.12 | CPU 插槽关键参数缺失。；对应规则 UNKNOWN，不得核心通过。 | 适用 P0 | 通过 | [CompatibilityRulesTest](../../backend/src/test/java/cn/zhuangjimao/compatibility/CompatibilityRulesTest.java)`#c01IsPendingWithoutBothPartsAndUnknownWithoutAVerifiedSocket` + [compatibility-diagnostics](../../frontend/tests/compatibility-diagnostics.spec.ts) · unknown parameter |
| F03-T19 | P0 | 5.3.12 | 无独显但核显路径有效。；独显相关规则 NOT_APPLICABLE，不阻止核心通过。 | 适用 P0 | 通过 | [CompatibilityCheckTest](../../backend/src/test/java/cn/zhuangjimao/compatibility/CompatibilityCheckTest.java)`#integratedBuildPassesWithoutGpuButAnIndependentCoolerIsStillRequired` |
| F03-T20 | P0 | 5.3.12 | 用户替换 CPU 后触发检查。；检测结果对应替换后的配置。 | 适用 P0 | 通过 | [compatibility-diagnostics](../../frontend/tests/compatibility-diagnostics.spec.ts) · configuration edits automatically update conflicts + [任务 24](task-24.md) 10 并发验证请求修订号 |
| F03-T21 | P0 | 5.3.12 | 较旧检测请求比新请求更晚返回。；旧结果不能覆盖当前显示。 | 适用 P0 | 通过 | [compatibility-diagnostics](../../frontend/tests/compatibility-diagnostics.spec.ts) · late diagnostic response cannot overwrite the newer configuration |
| F03-T22 | P0 | 5.3.12 | 游客执行兼容性检查。；无需登录，可正常检测。 | 适用 P0 | 通过 | [CompatibilityCheckTest](../../backend/src/test/java/cn/zhuangjimao/compatibility/CompatibilityCheckTest.java)`#guestReadsCurrentDatabaseParametersAndSeesEachFinishedRuleApart` |
| F03-T23 | P0 | 5.3.12 | 检测服务异常。；返回错误状态，不能作为通过配置提交。 | 适用 P0 | 通过 | [CompatibilityFailureTest](../../backend/src/test/java/cn/zhuangjimao/compatibility/CompatibilityFailureTest.java)`#anUnavailableDatabaseReturnsAnExplicitErrorForTheRequestedRevision` + [compatibility-diagnostics](../../frontend/tests/compatibility-diagnostics.spec.ts) · failed diagnostic never shows a stale pass |
| F03-T24 | P0 | 5.3.12 | 未登录用户试图以伪造的通过字段绕过 F05。；后端不得信任伪造字段或绕过登录权限。 | 适用 P0 | 通过 | [ConfigurationCartTest](../../backend/src/test/java/cn/zhuangjimao/cart/ConfigurationCartTest.java)`#guestCsrfForgedPassingFieldsAndInvalidStructureCannotCreatePartialGroups` + [CompatibilityCheckTest](../../backend/src/test/java/cn/zhuangjimao/compatibility/CompatibilityCheckTest.java)`#rejectsClientSuppliedParametersCategoriesAndPassFlags` |

### F04 智能推荐场景

| 原验收编号 | 原优先级 | 报告章节 | 原场景与预期 | 本版范围 | 执行状态 | 证据或剩余条件 |
| --- | --- | --- | --- | --- | --- | --- |
| F04-T01 | 源文件未单列 | 5.4.12 | 游客访问智能推荐；可以正常使用 | 适用 P0 | 通过 | [office-recommendation](../../frontend/tests/office-recommendation.spec.ts) · office budget creates a real scored plan |
| F04-T02 | 源文件未单列 | 5.4.12 | 输入合法预算和用途；系统接受并启动推荐 | 适用 P0 | 通过 | [ScenarioRecommendationTest](../../backend/src/test/java/cn/zhuangjimao/catalog/ScenarioRecommendationTest.java)`#gamingAndCreationReturnScoredPlansWithDedicatedGraphics` + #guestGetsARealRepeatableOfficePlanAndExactBudgetBoundary |
| F04-T03 | 源文件未单列 | 5.4.12 | 输入零、负数或非法预算；返回输入错误 | 适用 P0 | 通过 | [ScenarioRecommendationTest](../../backend/src/test/java/cn/zhuangjimao/catalog/ScenarioRecommendationTest.java)`#invalidInputAndProvenPriceLowerBoundNeverInventAPlan` |
| F04-T04 | 源文件未单列 | 5.4.12 | 办公用途，存在有效核显配置；可推荐不含独显的完整方案 | 适用 P0 | 通过 | [ScenarioRecommendationTest](../../backend/src/test/java/cn/zhuangjimao/catalog/ScenarioRecommendationTest.java)`#gpuStockAndScoreAffectOnlyScenariosThatRequireDedicatedGraphics` |
| F04-T05 | 源文件未单列 | 5.4.12 | 办公用途，CPU 无有效核显且无独显；不得成功推荐该配置 | 适用 P0 | 通过 | [ScenarioRecommendationTest](../../backend/src/test/java/cn/zhuangjimao/catalog/ScenarioRecommendationTest.java)`#gpuStockAndScoreAffectOnlyScenariosThatRequireDedicatedGraphics` |
| F04-T06 | 源文件未单列 | 5.4.12 | 游戏用途候选方案不含独显；不满足 F04 游戏推荐规则 | 适用 P0 | 通过 | [ScenarioRecommendationTest](../../backend/src/test/java/cn/zhuangjimao/catalog/ScenarioRecommendationTest.java)`#gpuStockAndScoreAffectOnlyScenariosThatRequireDedicatedGraphics` |
| F04-T07 | 源文件未单列 | 5.4.12 | 内容创作用途候选方案不含独显；不满足 F04 创作推荐规则 | 适用 P0 | 通过 | [ScenarioRecommendationTest](../../backend/src/test/java/cn/zhuangjimao/catalog/ScenarioRecommendationTest.java)`#gpuStockAndScoreAffectOnlyScenariosThatRequireDedicatedGraphics` |
| F04-T08 | 源文件未单列 | 5.4.12 | 缺少七类基础必需硬件之一；不得输出 SUCCESS | 适用 P0 | 通过 | [ScenarioRecommendationTest](../../backend/src/test/java/cn/zhuangjimao/catalog/ScenarioRecommendationTest.java)`#unavailableOrUnscoredCpuCannotBecomeASuccess` · 必需 CPU 类别无可售库存 |
| F04-T09 | 源文件未单列 | 5.4.12 | 某硬件类别选择多个销售 SKU；不得生成违反 F02 结构的配置 | 适用 P0 | 通过 | [ScenarioRecommendationTest](../../backend/src/test/java/cn/zhuangjimao/catalog/ScenarioRecommendationTest.java)`#gamingAndCreationReturnScoredPlansWithDedicatedGraphics` · 每类唯一真实 SKU 及现有库存 |
| F04-T10 | 源文件未单列 | 5.4.12 | CPU 与主板不兼容；不得返回该组合为成功推荐 | 适用 P0 | 通过 | [ScenarioRecommendationTest](../../backend/src/test/java/cn/zhuangjimao/catalog/ScenarioRecommendationTest.java)`#confirmedConflictMissingParametersAndUnprovenSearchAreDistinct` |
| F04-T11 | 源文件未单列 | 5.4.12 | SSD 接口或机箱尺寸等 F03 P0 规则不通过；不得返回成功推荐 | 适用 P0 | 通过 | [ScenarioRecommendationTest](../../backend/src/test/java/cn/zhuangjimao/catalog/ScenarioRecommendationTest.java)`#caseClearanceFailureNeverProducesASuccess` |
| F04-T12 | 源文件未单列 | 5.4.12 | F03 核心状态为 UNKNOWN 或 UNVERIFIED；不得视为兼容通过 | 适用 P0 | 通过 | [ScenarioRecommendationTest](../../backend/src/test/java/cn/zhuangjimao/catalog/ScenarioRecommendationTest.java)`#confirmedConflictMissingParametersAndUnprovenSearchAreDistinct` + #authoritativeCompatibilityReadErrorsAndStaleReferencesNeverReturnSuccess |
| F04-T13 | 源文件未单列 | 5.4.12 | 无独显且有效核显输出，相关独显检查为 NOT_APPLICABLE；不因不适用检测而误判失败 | 适用 P0 | 通过 | [ScenarioRecommendationTest](../../backend/src/test/java/cn/zhuangjimao/catalog/ScenarioRecommendationTest.java)`#gpuStockAndScoreAffectOnlyScenariosThatRequireDedicatedGraphics` + [CompatibilityCheckTest](../../backend/src/test/java/cn/zhuangjimao/compatibility/CompatibilityCheckTest.java)`#integratedBuildPassesWithoutGpuButAnIndependentCoolerIsStillRequired` + [office-recommendation](../../frontend/tests/office-recommendation.spec.ts) · 实际返回并选用核显 |
| F04-T14 | 源文件未单列 | 5.4.12 | 存在 F03 P1 扩展风险；依 F03 原规则提示，不伪造完全兼容 | P0 范围提示，P1 未纳入 | 通过 | [scenario-recommendation](../../frontend/tests/scenario-recommendation.spec.ts) · 验证「6 项扩展检测未执行」；实际 P1 风险识别未纳入 |
| F04-T15 | 源文件未单列 | 5.4.12 | 候选商品库存为零或已下架；不得进入成功推荐 | 适用 P0 | 通过 | [ScenarioRecommendationTest](../../backend/src/test/java/cn/zhuangjimao/catalog/ScenarioRecommendationTest.java)`#unavailableOrUnscoredCpuCannotBecomeASuccess` + #gpuStockAndScoreAffectOnlyScenariosThatRequireDedicatedGraphics |
| F04-T16 | 源文件未单列 | 5.4.12 | CPU/GPU 缺少必要性能分数；不得擅自赋予虚构分数 | 适用 P0 | 通过 | [ScenarioRecommendationTest](../../backend/src/test/java/cn/zhuangjimao/catalog/ScenarioRecommendationTest.java)`#unavailableOrUnscoredCpuCannotBecomeASuccess` + #gpuStockAndScoreAffectOnlyScenariosThatRequireDedicatedGraphics |
| F04-T17 | 源文件未单列 | 5.4.12 | 三种用途采用不同场景权重；各场景评分符合对应公式 | 适用 P0 | 通过 | [RecommendationRulesTest](../../backend/src/test/java/cn/zhuangjimao/catalog/RecommendationRulesTest.java)`#scenarioWeightsUseTheSameFixedScoresAndDifferentContributions` |
| F04-T18 | 源文件未单列 | 5.4.12 | 候选方案总价超出预算；不得输出为成功推荐 | 适用 P0 | 通过 | [ScenarioRecommendationTest](../../backend/src/test/java/cn/zhuangjimao/catalog/ScenarioRecommendationTest.java)`#gamingAndCreationReturnScoredPlansWithDedicatedGraphics` + #guestGetsARealRepeatableOfficePlanAndExactBudgetBoundary |
| F04-T19 | 源文件未单列 | 5.4.12 | 预算明确低于可证明的必要商品价格下界；返回预算不足 | 适用 P0 | 通过 | [ScenarioRecommendationTest](../../backend/src/test/java/cn/zhuangjimao/catalog/ScenarioRecommendationTest.java)`#invalidInputAndProvenPriceLowerBoundNeverInventAPlan` |
| F04-T20 | 源文件未单列 | 5.4.12 | 束搜索未找到结果，但尚未证明无可行配置；返回非确定性无结果状态 | 适用 P0 | 通过 | [ScenarioRecommendationTest](../../backend/src/test/java/cn/zhuangjimao/catalog/ScenarioRecommendationTest.java)`#confirmedConflictMissingParametersAndUnprovenSearchAreDistinct` |
| F04-T21 | 源文件未单列 | 5.4.12 | 多个有效方案综合评分接近；按价格及固定 ID 规则选择 | 适用 P0 | 通过 | [RecommendationRulesTest](../../backend/src/test/java/cn/zhuangjimao/catalog/RecommendationRulesTest.java)`#closeScoresPreferPriceAndThenNumericProductIdsIndependentOfInputOrder` + #everyProductIdBreaksTheTieBeforeMemoryQuantity |
| F04-T22 | 源文件未单列 | 5.4.12 | 相同数据与规则重复推荐；获得稳定、可复现的结果 | 适用 P0 | 通过 | [ScenarioRecommendationTest](../../backend/src/test/java/cn/zhuangjimao/catalog/ScenarioRecommendationTest.java)`#gamingAndCreationReturnScoredPlansWithDedicatedGraphics` + #guestGetsARealRepeatableOfficePlanAndExactBudgetBoundary + [任务 24](task-24.md)三场景负载 |
| F04-T23 | 源文件未单列 | 5.4.12 | 成功推荐转入 DIY；商品组合保留，复用 F02/F03 | 适用 P0 | 通过 | [recommendation-cart](../../frontend/tests/recommendation-cart.spec.ts) · 三场景真实商品引用进入 DIY 并完成复验 |
| F04-T24 | 源文件未单列 | 5.4.12 | 游客从推荐结果点击加入购物车；要求登录，登录后恢复并重校验 | 适用 P0 | 通过 | [recommendation-cart](../../frontend/tests/recommendation-cart.spec.ts) · 三场景游客登录恢复、证据与明确确认 |
| F04-T25 | 源文件未单列 | 5.4.12 | 生成推荐后管理员修改商品售价或库存；不得使用失效结果直接完成购物流程 | 适用 P0 | 通过 | [recommendation-cart](../../frontend/tests/recommendation-cart.spec.ts) · 恢复显示库存变化，确认时拒绝再次改价 + [任务 24](task-24.md) F07-T25 |
| F04-T26 | 源文件未单列 | 5.4.12 | 生成推荐但未加入购物车或下单；商品库存不发生扣减或预占 | 适用 P0 | 通过 | [ScenarioRecommendationTest](../../backend/src/test/java/cn/zhuangjimao/catalog/ScenarioRecommendationTest.java)`#guestGetsARealRepeatableOfficePlanAndExactBudgetBoundary` + [ConfigurationCartTest](../../backend/src/test/java/cn/zhuangjimao/cart/ConfigurationCartTest.java)`#recommendationRequiresTheCurrentQuoteAndKeepsItsOriginWithoutReservingStock` |

### F05 购物车场景

| 原验收编号 | 原优先级 | 报告章节 | 原场景与预期 | 本版范围 | 执行状态 | 证据或剩余条件 |
| --- | --- | --- | --- | --- | --- | --- |
| F05-T01 | P0 | 5.5.12 | 游客访问购物车或执行加购；拒绝操作并要求登录。 | 适用 P0 | 通过 | [SingleItemCartTest](../../backend/src/test/java/cn/zhuangjimao/cart/SingleItemCartTest.java)`#guestAndForeignOwnerCannotReadOrChangeThisCart` |
| F05-T02 | P0 | 5.5.12 | 用户 A 尝试修改用户 B 的购物项；后端拒绝访问，B 的数据不变。 | 适用 P0 | 通过 | [SingleItemCartTest](../../backend/src/test/java/cn/zhuangjimao/cart/SingleItemCartTest.java)`#guestAndForeignOwnerCannotReadOrChangeThisCart` |
| F05-T03 | P0 | 5.5.12 | 登录用户加入商品后退出并重新登录；原购物车数据仍存在。 | 适用 P0 | 通过 | [SingleItemCartTest](../../backend/src/test/java/cn/zhuangjimao/cart/SingleItemCartTest.java)`#cartSurvivesNewSessionAndKeepsInvalidLinesLabelled` |
| F05-T04 | P0 | 5.5.12 | 同 SKU 普通单品连续加入两次；合并为一条记录，数量正确。 | 适用 P0 | 通过 | [SingleItemCartTest](../../backend/src/test/java/cn/zhuangjimao/cart/SingleItemCartTest.java)`#sameSkuMergesIntoOneLineWithServerSideTotal` |
| F05-T05 | P0 | 5.5.12 | 修改数量为 0、100 或超过库存；拒绝非法数量，不破坏原记录。 | 适用 P0 | 通过 | [SingleItemCartTest](../../backend/src/test/java/cn/zhuangjimao/cart/SingleItemCartTest.java)`#quantityBoundsAndStockLimitKeepTheOriginalLine` + #ninetyNineIsAcceptedAndOneMoreIsRefused |
| F05-T06 | P0 | 5.5.12 | 删除一个普通单品；只删除目标记录，配置组不受影响。 | 适用 P0 | 通过 | [ConfigurationCartTest](../../backend/src/test/java/cn/zhuangjimao/cart/ConfigurationCartTest.java)`#singleItemsAndDifferentGroupsKeepTheirIdentityAndAggregateBeyondNinetyNine` · 删除单品后逐项比较两个既有配置组 |
| F05-T07 | P0 | 5.5.12 | 添加符合 F02/F03 条件的七类核显 DIY 配置；成功创建完整配置分组。 | 适用 P0 | 通过 | [configuration-cart](../../frontend/tests/configuration-cart.spec.ts) · seven-part build + [ConfigurationCartTest](../../backend/src/test/java/cn/zhuangjimao/cart/ConfigurationCartTest.java)`#sevenCategoryIntegratedBuildPersistsAcrossLoginAndCanOnlyBeRemovedAsAWhole` |
| F05-T08 | P0 | 5.5.12 | 添加符合 F02/F03/F04 条件的八类独显推荐配置；成功保留真实 SKU 与组内结构。 | 适用 P0 | 通过 | [recommendation-cart](../../frontend/tests/recommendation-cart.spec.ts) · 游戏与创作推荐，真实商品按原方案 ID 整组持久保存 |
| F05-T09 | P0 | 5.5.12 | 配置 F03 核心检测失败或无法确认；拒绝作为有效整机入车。 | 适用 P0 | 通过 | [ConfigurationCartTest](../../backend/src/test/java/cn/zhuangjimao/cart/ConfigurationCartTest.java)`#currentParametersAndPricesAreRevalidatedAndCannotChangeTheDuplicateIdentity` + [CartCheckoutPreviewTest](../../backend/src/test/java/cn/zhuangjimao/cart/CartCheckoutPreviewTest.java)`#currentParameterChangesDiagnoseAndRetainIncompatibleAndUnverifiedGroups` |
| F05-T10 | P0 | 5.5.12 | 缺少必要硬件或同类含多个销售 SKU；拒绝非法配置结构。 | 适用 P0 | 通过 | [ConfigurationCartTest](../../backend/src/test/java/cn/zhuangjimao/cart/ConfigurationCartTest.java)`#guestCsrfForgedPassingFieldsAndInvalidStructureCannotCreatePartialGroups` |
| F05-T11 | P0 | 5.5.12 | 配置中有下架商品；拒绝整个配置组入车。 | 适用 P0 | 通过 | [ConfigurationCartTest](../../backend/src/test/java/cn/zhuangjimao/cart/ConfigurationCartTest.java)`#duplicateConfigurationStillRevalidatesCurrentAvailabilityWithoutAddingDemand` + [configuration-cart](../../frontend/tests/configuration-cart.spec.ts) · stock invalidated after confirmation |
| F05-T12 | P0 | 5.5.12 | 配置中任一必要商品缺货；整组添加失败，不生成部分记录。 | 适用 P0 | 通过 | [configuration-cart](../../frontend/tests/configuration-cart.spec.ts) · stock invalidated after confirmation + [ConfigurationCartTest](../../backend/src/test/java/cn/zhuangjimao/cart/ConfigurationCartTest.java)`#eighthItemWriteFailureRollsBackTheWholeGroupAndAllowsRetry` |
| F05-T13 | P0 | 5.5.12 | 同一 SKU 同时存在于普通单品和配置组；两种记录独立存在，归属关系正确。 | 适用 P0 | 通过 | [ConfigurationCartTest](../../backend/src/test/java/cn/zhuangjimao/cart/ConfigurationCartTest.java)`#singleItemsAndDifferentGroupsKeepTheirIdentityAndAggregateBeyondNinetyNine` |
| F05-T14 | P0 | 5.5.12 | 删除配置组；组及其明细全部删除，其他记录不变。 | 适用 P0 | 通过 | [ConfigurationCartTest](../../backend/src/test/java/cn/zhuangjimao/cart/ConfigurationCartTest.java)`#sevenCategoryIntegratedBuildPersistsAcrossLoginAndCanOnlyBeRemovedAsAWhole` + [SingleItemCartTest](../../backend/src/test/java/cn/zhuangjimao/cart/SingleItemCartTest.java)`#configurationIsValidatedDeduplicatedAndRemovedAsAGroup` |
| F05-T15 | P0 | 5.5.12 | 返回 F02 成功修改配置；校验后完整替换原分组。 | 适用 P0 | 通过 | [CartCheckoutPreviewTest](../../backend/src/test/java/cn/zhuangjimao/cart/CartCheckoutPreviewTest.java)`#configurationReplacementIsAtomicAndRejectsStaleEditors` + [cart-checkout-preview](../../frontend/tests/cart-checkout-preview.spec.ts) · whole-group DIY editing |
| F05-T16 | P0 | 5.5.12 | 配置修改中发生冲突或用户取消；原组不被错误覆盖。 | 适用 P0 | 通过 | [CartCheckoutPreviewTest](../../backend/src/test/java/cn/zhuangjimao/cart/CartCheckoutPreviewTest.java)`#failedReplacementAndDuplicateTargetPreserveTheOriginalWholeGroup` + [cart-checkout-preview](../../frontend/tests/cart-checkout-preview.spec.ts) · cancelled and saved |
| F05-T17 | P0 | 5.5.12 | 完全相同配置连续点击添加或重复请求；不产生意外重复分组。 | 适用 P0 | 通过 | [ConfigurationCartTest](../../backend/src/test/java/cn/zhuangjimao/cart/ConfigurationCartTest.java)`#concurrentDuplicatesCreateOneGroupEvenWhenSubmittedInDifferentOrders` + [configuration-cart](../../frontend/tests/configuration-cart.spec.ts) · lost successful response |
| F05-T18 | P0 | 5.5.12 | 同 SKU 出现在多个购物项中；正确汇总相关库存需求。 | 适用 P0 | 通过 | [ConfigurationCartTest](../../backend/src/test/java/cn/zhuangjimao/cart/ConfigurationCartTest.java)`#singleItemsAndDifferentGroupsKeepTheirIdentityAndAggregateBeyondNinetyNine` + #aggregateDemandProtectsEveryAddAndQuantityChangeUnderConcurrency |
| F05-T19 | P0 | 5.5.12 | 商品加入后价格发生变化；显示当前价格，未确认不可结算。 | 适用 P0 | 通过 | [SingleItemCartTest](../../backend/src/test/java/cn/zhuangjimao/cart/SingleItemCartTest.java)`#priceChangeReachesNextReadWithoutTouchingStoredLine` + [cart-checkout-preview](../../frontend/tests/cart-checkout-preview.spec.ts) · concurrent price change |
| F05-T20 | P0 | 5.5.12 | 含小数金额、多商品数量及小计；单品小计、组小计及合计正确。 | 适用 P0 | 通过 | [CartCheckoutPreviewTest](../../backend/src/test/java/cn/zhuangjimao/cart/CartCheckoutPreviewTest.java)`#mixedPreviewAggregatesOnlySelectedSkuDemandAndRequiresFreshQuoteConfirmation` + [MixedOrderTest](../../backend/src/test/java/cn/zhuangjimao/cart/MixedOrderTest.java)`#ninetyNineSinglesAndTwoWholeGroupsAggregateStockAndKeepHistoricalOwnership` |
| F05-T21 | P0 | 5.5.12 | 入车商品后来缺货、下架或配置失效；保留记录，标记失效并阻止对应项目结算。 | 适用 P0 | 通过 | [SingleItemCartTest](../../backend/src/test/java/cn/zhuangjimao/cart/SingleItemCartTest.java)`#cartSurvivesNewSessionAndKeepsInvalidLinesLabelled` + [CartCheckoutPreviewTest](../../backend/src/test/java/cn/zhuangjimao/cart/CartCheckoutPreviewTest.java)`#currentParameterChangesDiagnoseAndRetainIncompatibleAndUnverifiedGroups` |
| F05-T22 | P0 | 5.5.12 | 仅选择部分购物项结算；单品可独立选，配置组不可部分选择。 | 适用 P0 | 通过 | [CartCheckoutPreviewTest](../../backend/src/test/java/cn/zhuangjimao/cart/CartCheckoutPreviewTest.java)`#mixedPreviewAggregatesOnlySelectedSkuDemandAndRequiresFreshQuoteConfirmation` + #previewRejectsForgedPartialForeignAndStaleSelectionsAndRequiresSessionAndCsrf |
| F05-T23 | P0 | 5.5.12 | F05→F06 交接时价格或库存发生变化；不能用旧数据创建订单；订单创建成功仅移除结算项，失败保留购物车。 | 适用 P0 | 通过 | [SingleItemOrderTest](../../backend/src/test/java/cn/zhuangjimao/cart/SingleItemOrderTest.java)`#changedQuotesStaleCartsForeignItemsAndForgedDetailsCannotCreateOrders` + #selectedSinglesBecomeOnePendingOrderWithImmutableSnapshotsAndUnselectedItemsRemain + [mixed-order](../../frontend/tests/mixed-order.spec.ts) |
| F05-T24 | P0 | 5.5.12 | 游客装机后登录；恢复配置并重验，经确认后才能入车。 | 适用 P0 | 通过 | [configuration-cart](../../frontend/tests/configuration-cart.spec.ts) · seven-part build + [guest-restoration](../../frontend/tests/guest-restoration.spec.ts) · authoritative revalidation and no cart side effect |
| F05-T25 | P1 | 5.5.12 | 为配置分组修改名称；名称保存，不改变实际 SKU 关系。 | P1 | 未纳入 | 原验收优先级超出本版范围 |
| F05-T26 | P1 | 5.5.12 | 同时选择多个购物项删除；仅移除选中项目，保持分组/归属一致。 | P1 | 未纳入 | 原验收优先级超出本版范围 |
| F05-T27 | P1 | 5.5.12 | 显式复制相同配置组；创建另一独立组，并重新校验库存需求。 | P1 | 未纳入 | 原验收优先级超出本版范围 |
| F05-T28 | P1 | 5.5.12 | 商品售价变化；展示参考价、现价及差额，不把参考价当成交价。 | P1 | 未纳入 | 原验收优先级超出本版范围 |

### F06 订单与模拟支付场景

| 原验收编号 | 原优先级 | 报告章节 | 原场景与预期 | 本版范围 | 执行状态 | 证据或剩余条件 |
| --- | --- | --- | --- | --- | --- | --- |
| F06-T01 | P0 | 5.6.12 | 游客尝试下单、访问个人订单；阻止未登录交易与订单访问，不生成订单。 | 适用 P0 | 通过 | [SingleItemOrderTest](../../backend/src/test/java/cn/zhuangjimao/cart/SingleItemOrderTest.java)`#changedQuotesStaleCartsForeignItemsAndForgedDetailsCannotCreateOrders` + [OrderPaymentHistoryTest](../../backend/src/test/java/cn/zhuangjimao/cart/OrderPaymentHistoryTest.java)`#historyIsPaginatedNewestFirstAndOnlyShowsTheSessionOwnersOrders` |
| F06-T02 | P0 | 5.6.12 | 购物车只选中部分项目结算；订单仅含选中项，未选项仍在购物车。 | 适用 P0 | 通过 | [SingleItemOrderTest](../../backend/src/test/java/cn/zhuangjimao/cart/SingleItemOrderTest.java)`#selectedSinglesBecomeOnePendingOrderWithImmutableSnapshotsAndUnselectedItemsRemain` |
| F06-T03 | P0 | 5.6.12 | 普通单品与多套配置同结算；形成一笔订单，全部商品和配置组归属正确。 | 适用 P0 | 通过 | [MixedOrderTest](../../backend/src/test/java/cn/zhuangjimao/cart/MixedOrderTest.java)`#ninetyNineSinglesAndTwoWholeGroupsAggregateStockAndKeepHistoricalOwnership` + [mixed-order](../../frontend/tests/mixed-order.spec.ts) |
| F06-T04 | P0 | 5.6.12 | 客户端尝试仅结算配置组内部分商品；服务器拒绝拆分配置组。 | 适用 P0 | 通过 | [MixedOrderTest](../../backend/src/test/java/cn/zhuangjimao/cart/MixedOrderTest.java)`#wholeGroupOwnershipIsRequiredAndUnselectedInvalidEntriesRemain` + [CartCheckoutPreviewTest](../../backend/src/test/java/cn/zhuangjimao/cart/CartCheckoutPreviewTest.java)`#previewRejectsForgedPartialForeignAndStaleSelectionsAndRequiresSessionAndCsrf` |
| F06-T05 | P0 | 5.6.12 | 结算期间购物车内容变更；拒绝基于过期选中项创建订单。 | 适用 P0 | 通过 | [SingleItemOrderTest](../../backend/src/test/java/cn/zhuangjimao/cart/SingleItemOrderTest.java)`#changedQuotesStaleCartsForeignItemsAndForgedDetailsCannotCreateOrders` + #concurrentQuantityEditEitherPrecedesCheckoutOrFindsTheAlreadyPurchasedLineMissing |
| F06-T06 | P0 | 5.6.12 | 提交后某商品变为下架；结算被拒绝，不预占库存。 | 适用 P0 | 通过 | [MixedOrderTest](../../backend/src/test/java/cn/zhuangjimao/cart/MixedOrderTest.java)`#anyInvalidGroupOrUnconfirmedChangeRejectsTheEntireSelectedOrder` · 报价后下架散热器并确认拒绝下单、库存不变 |
| F06-T07 | P0 | 5.6.12 | 商品价格在确认前后发生变化；每次变价都需重新确认，不静默下单。 | 适用 P0 | 通过 | [SingleItemOrderTest](../../backend/src/test/java/cn/zhuangjimao/cart/SingleItemOrderTest.java)`#waitsForCommittedCatalogWritesAndRejectsTheOldQuoteWithoutHoldingPartialStock` + [cart-checkout-preview](../../frontend/tests/cart-checkout-preview.spec.ts) · concurrent price change |
| F06-T08 | P0 | 5.6.12 | 同 SKU 出现在单品及多个配置组；库存校验聚合总需求量。 | 适用 P0 | 通过 | [MixedOrderTest](../../backend/src/test/java/cn/zhuangjimao/cart/MixedOrderTest.java)`#ninetyNineSinglesAndTwoWholeGroupsAggregateStockAndKeepHistoricalOwnership` + [mixed-order](../../frontend/tests/mixed-order.spec.ts) |
| F06-T09 | P0 | 5.6.12 | 尝试提交不完整的整机配置；不创建有效整机订单。 | 适用 P0 | 通过 | [MixedOrderTest](../../backend/src/test/java/cn/zhuangjimao/cart/MixedOrderTest.java)`#finalAdmissionRejectsMalformedGroupQuantitiesEvenWithAFreshQuoteToken` + #anyInvalidGroupOrUnconfirmedChangeRejectsTheEntireSelectedOrder |
| F06-T10 | P0 | 5.6.12 | 整机未通过 F03 核心检查；不允许整机结算。 | 适用 P0 | 通过 | [MixedOrderTest](../../backend/src/test/java/cn/zhuangjimao/cart/MixedOrderTest.java)`#finalValidationWaitsForAParameterWriterThenRejectsItsUnverifiableBuild` + #anyInvalidGroupOrUnconfirmedChangeRejectsTheEntireSelectedOrder |
| F06-T11 | P0 | 5.6.12 | 不同数量及小数金额；明细及订单金额计算精确、合计一致。 | 适用 P0 | 通过 | [MixedOrderTest](../../backend/src/test/java/cn/zhuangjimao/cart/MixedOrderTest.java)`#ninetyNineSinglesAndTwoWholeGroupsAggregateStockAndKeepHistoricalOwnership` + [mixed-order](../../frontend/tests/mixed-order.spec.ts) |
| F06-T12 | P0 | 5.6.12 | 订单创建事务中模拟错误；无半成品订单、错误库存或购物车清理。 | 适用 P0 | 通过 | [SingleItemOrderTest](../../backend/src/test/java/cn/zhuangjimao/cart/SingleItemOrderTest.java)`#faultsAtEveryWriteStageRollBackAllStockCartAndRetryEffects` + [MixedOrderTest](../../backend/src/test/java/cn/zhuangjimao/cart/MixedOrderTest.java)`#failuresDuringGroupWritesAndRemovalRollBackEverySelectedEntry` |
| F06-T13 | P0 | 5.6.12 | 正常创建订单；唯一订单号、待支付状态、金额、截止时间正确。 | 适用 P0 | 通过 | [SingleItemOrderTest](../../backend/src/test/java/cn/zhuangjimao/cart/SingleItemOrderTest.java)`#selectedSinglesBecomeOnePendingOrderWithImmutableSnapshotsAndUnselectedItemsRemain` + #simultaneousFirstSubmissionsWithOneKeyCreateOnlyOneOrder |
| F06-T14 | P0 | 5.6.12 | 创建后修改商品名称、价格、参数；历史订单快照和配置分组不改变。 | 适用 P0 | 通过 | [MixedOrderTest](../../backend/src/test/java/cn/zhuangjimao/cart/MixedOrderTest.java)`#ninetyNineSinglesAndTwoWholeGroupsAggregateStockAndKeepHistoricalOwnership` + [mixed-order](../../frontend/tests/mixed-order.spec.ts) · 改价下架后重读，复验已完成单的分组与明细 |
| F06-T15 | P0 | 5.6.12 | 两用户并发购买有限库存；成功预占数量不超过可售库存，不超卖。 | 适用 P0 | 通过 | [SingleItemOrderTest](../../backend/src/test/java/cn/zhuangjimao/cart/SingleItemOrderTest.java)`#simultaneousBuyersNeverOversellAndDuplicateSubmissionsReturnOneOrder` + [任务 24](task-24.md)单 SKU 并发证据 |
| F06-T16 | P0 | 5.6.12 | 只结算购物车部分项目并成功；仅移除本次成功结算项。 | 适用 P0 | 通过 | [SingleItemOrderTest](../../backend/src/test/java/cn/zhuangjimao/cart/SingleItemOrderTest.java)`#selectedSinglesBecomeOnePendingOrderWithImmutableSnapshotsAndUnselectedItemsRemain` |
| F06-T17 | P0 | 5.6.12 | 价格/库存/兼容性导致创建失败；购物车保留，订单与库存无残留副作用。 | 适用 P0 | 通过 | [MixedOrderTest](../../backend/src/test/java/cn/zhuangjimao/cart/MixedOrderTest.java)`#anyInvalidGroupOrUnconfirmedChangeRejectsTheEntireSelectedOrder` + [mixed-order](../../frontend/tests/mixed-order.spec.ts) · 聚合缺货并保留购物车 |
| F06-T18 | P0 | 5.6.12 | 待支付订单超过 30 分钟；不能支付；系统自动取消并恢复库存。 | 适用 P0 | 通过 | [OrderPaymentHistoryTest](../../backend/src/test/java/cn/zhuangjimao/cart/OrderPaymentHistoryTest.java)`#deadlineComparisonRejectsEqualityAndExpiryEvenBeforeAnyScannerRuns` + [OrderCancellationTimeoutTest](../../backend/src/test/java/cn/zhuangjimao/cart/OrderCancellationTimeoutTest.java)`#delayedScansCancelAtTheDeadlineAndDoNotReleasePaidOrFutureOrders` |
| F06-T19 | P0 | 5.6.12 | 有效待支付订单执行模拟支付；变为已支付，金额正确，不重复扣库存。 | 适用 P0 | 通过 | [OrderPaymentHistoryTest](../../backend/src/test/java/cn/zhuangjimao/cart/OrderPaymentHistoryTest.java)`#ownerPaysTheSnapshotAmountOnceWithoutChangingAvailableOrReservedStock` + [mixed-order](../../frontend/tests/mixed-order.spec.ts) |
| F06-T20 | P0 | 5.6.12 | 同订单重复提交支付；不重复产生支付效果、支付记录或库存变更。 | 适用 P0 | 通过 | [OrderPaymentHistoryTest](../../backend/src/test/java/cn/zhuangjimao/cart/OrderPaymentHistoryTest.java)`#simultaneousPaymentsReturnOneStableResultWithoutInventorySideEffects` |
| F06-T21 | P0 | 5.6.12 | 用户取消本人待支付订单；他人尝试取消；本人可取消；他人被拒绝。 | 适用 P0 | 通过 | [OrderCancellationTimeoutTest](../../backend/src/test/java/cn/zhuangjimao/cart/OrderCancellationTimeoutTest.java)`#ownerCancellationReleasesStockOnceAndKeepsTheCartEmpty` + #cancellationRejectsForeignOwnersCsrfForgedFieldsAndPaidOrCompletedStates |
| F06-T22 | P0 | 5.6.12 | 用户取消/超时取消竞争或重复执行；只释放一次库存，购物车不恢复。 | 适用 P0 | 通过 | [OrderCancellationTimeoutTest](../../backend/src/test/java/cn/zhuangjimao/cart/OrderCancellationTimeoutTest.java)`#realConcurrentPaymentsCancellationsAndScansProduceOneLegalResult` + #ownerCancellationReleasesStockOnceAndKeepsTheCartEmpty + [任务 24](task-24.md)取消与扫描竞争 |
| F06-T23 | P0 | 5.6.12 | 管理员完成已支付订单；非法操作者或状态尝试；合法流程成功，其余拒绝，库存不二次扣减。 | 适用 P0 | 通过 | [OrderAdministrationTest](../../backend/src/test/java/cn/zhuangjimao/cart/OrderAdministrationTest.java)`#onlyPaidOrdersCompleteWithOneAtomicAuditAndNoInventoryChange` + [order-administration](../../frontend/tests/order-administration.spec.ts) |
| F06-T24 | P0 | 5.6.12 | 用户及管理员查询订单；用户仅访问自己订单；管理员按权限查看订单。 | 适用 P0 | 通过 | [OrderPaymentHistoryTest](../../backend/src/test/java/cn/zhuangjimao/cart/OrderPaymentHistoryTest.java)`#historyIsPaginatedNewestFirstAndOnlyShowsTheSessionOwnersOrders` + [OrderAdministrationTest](../../backend/src/test/java/cn/zhuangjimao/cart/OrderAdministrationTest.java)`#administratorQueriesOtherBuyersSnapshotsWithPaginationAndProtectedAccess` |
| F06-T25 | P1 | 5.6.12 | 使用编号、状态、日期等过滤订单；返回符合筛选条件的准确结果。 | P1 | 未纳入 | 原验收优先级超出本版范围 |
| F06-T26 | P1 | 5.6.12 | 查询模拟支付凭证；凭证编号、时间和金额正确，不涉及真实资金。 | P1 | 未纳入 | 原验收优先级超出本版范围 |
| F06-T27 | P1 | 5.6.12 | 查询订单状态时间线；状态顺序与操作记录一致。 | P1 | 未纳入 | 原验收优先级超出本版范围 |
| F06-T28 | P1 | 5.6.12 | 查看支付倒计时；与服务端截止时间一致；到期后不可支付。 | P1 | 未纳入 | 原验收优先级超出本版范围 |

### F07 后台管理场景

| 原验收编号 | 原优先级 | 报告章节 | 原场景与预期 | 本版范围 | 执行状态 | 证据或剩余条件 |
| --- | --- | --- | --- | --- | --- | --- |
| F07-T01 | P0 | 5.7.12 | 管理员通过统一登录入口访问后台；成功访问后台 | 适用 P0 | 通过 | [SessionAuthTest](../../backend/src/test/java/cn/zhuangjimao/auth/SessionAuthTest.java)`#initializedAdministratorUsesTheSameLoginAndHasNavigationWhileGuestsAreDenied` + [session-auth](../../frontend/tests/session-auth.spec.ts) · administrator uses the unified login |
| F07-T02 | P0 | 5.7.12 | 普通用户调用商品管理接口；返回 403，不修改数据 | 适用 P0 | 通过 | [PriceStockTest](../../backend/src/test/java/cn/zhuangjimao/catalog/PriceStockTest.java)`#invalidInputsAndUnauthorizedWritersLeaveCurrentValuesUnchanged` + [order-administration](../../frontend/tests/order-administration.spec.ts) · 普通用户后台 403 |
| F07-T03 | P0 | 5.7.12 | 管理员访问各后台功能入口；导航正确，错误操作有明确反馈 | 适用 P0 | 通过 | [session-auth](../../frontend/tests/session-auth.spec.ts) · navigate every administration module + [order-administration](../../frontend/tests/order-administration.spec.ts) · 错误提示 |
| F07-T04 | P0 | 5.7.12 | 按 SKU、类别和状态查询商品；返回符合条件的分页商品 | 适用 P0 | 通过 | [CatalogAdministrationTest](../../backend/src/test/java/cn/zhuangjimao/catalog/CatalogAdministrationTest.java)`#administratorCreatesEightDraftCategoriesWithStableIdentityAndPagedFilters` |
| F07-T05 | P0 | 5.7.12 | 新增合法 SKU 商品；创建成功，默认下架 | 适用 P0 | 通过 | [CatalogAdministrationTest](../../backend/src/test/java/cn/zhuangjimao/catalog/CatalogAdministrationTest.java)`#administratorCreatesEightDraftCategoriesWithStableIdentityAndPagedFilters` + [catalog-administration](../../frontend/tests/catalog-administration.spec.ts) · draft |
| F07-T06 | P0 | 5.7.12 | 修改商品名称、型号及描述；更新成功，商品详情展示新数据 | 适用 P0 | 通过 | [CatalogAdministrationTest](../../backend/src/test/java/cn/zhuangjimao/catalog/CatalogAdministrationTest.java)`#editingPublishingAndOffSaleChangesAreValidatedAuditedAndVersioned` |
| F07-T07 | P0 | 5.7.12 | 输入非法 CPU 插槽或内存规格数据；参数校验拒绝非法输入 | 适用 P0 | 通过 | [CatalogAdministrationTest](../../backend/src/test/java/cn/zhuangjimao/catalog/CatalogAdministrationTest.java)`#eightCategoryParametersRejectBadTypesUnitsSetsAndMissingApplicableFields` |
| F07-T08 | P0 | 5.7.12 | 尝试上架缺少关键参数的商品；上架失败并提示具体原因 | 适用 P0 | 通过 | [CatalogAdministrationTest](../../backend/src/test/java/cn/zhuangjimao/catalog/CatalogAdministrationTest.java)`#editingPublishingAndOffSaleChangesAreValidatedAuditedAndVersioned` + [catalog-administration](../../frontend/tests/catalog-administration.spec.ts) · publishing diagnostics |
| F07-T09 | P0 | 5.7.12 | 下架购物车已有商品；历史记录保留，新交易受阻 | 适用 P0 | 通过 | [SingleItemCartTest](../../backend/src/test/java/cn/zhuangjimao/cart/SingleItemCartTest.java)`#cartSurvivesNewSessionAndKeepsInvalidLinesLabelled` + [MixedOrderTest](../../backend/src/test/java/cn/zhuangjimao/cart/MixedOrderTest.java)`#anyInvalidGroupOrUnconfirmedChangeRejectsTheEntireSelectedOrder` |
| F07-T10 | P0 | 5.7.12 | 查询商品完整详情；商品属性、参数、价格、库存正确 | 适用 P0 | 通过 | [ProductCatalogTest](../../backend/src/test/java/cn/zhuangjimao/catalog/ProductCatalogTest.java)`#everyListedProductHasDetailsAndUnitsAndSetsAreExplicit` + [CatalogAdministrationTest](../../backend/src/test/java/cn/zhuangjimao/catalog/CatalogAdministrationTest.java)`#eachCategoryCanBePublishedWithTypedParametersAndCapacityConversionsAreExact` |
| F07-T11 | P0 | 5.7.12 | 修改商品售价并查询已有订单；新商品价格生效，历史订单金额不变 | 适用 P0 | 通过 | [PriceStockTest](../../backend/src/test/java/cn/zhuangjimao/catalog/PriceStockTest.java)`#priceChangeIsVisiblePubliclyVersionedAndAuditedWithReason` + [mixed-order](../../frontend/tests/mixed-order.spec.ts) · 调价和历史复验 |
| F07-T12 | P0 | 5.7.12 | 查询商品可售库存；与 F06 当前有效库存口径一致 | 适用 P0 | 通过 | [PriceStockTest](../../backend/src/test/java/cn/zhuangjimao/catalog/PriceStockTest.java)`#reservationSurvivesAdministrationAndReleaseIsAtomicAndOnlyOnce` + [mixed-order](../../frontend/tests/mixed-order.spec.ts) |
| F07-T13 | P0 | 5.7.12 | 手动合法调整库存并填写原因；调整成功并记录操作 | 适用 P0 | 通过 | [PriceStockTest](../../backend/src/test/java/cn/zhuangjimao/catalog/PriceStockTest.java)`#concurrentDeltasAccumulateAndCannotMakeAvailableStockNegative` + #auditFailureRollsBackPriceStockAndRevisionAndPublicRevisionTracksChanges |
| F07-T14 | P0 | 5.7.12 | 下单与后台库存修改同时发生；不超卖、不产生负库存或丢失更新 | 适用 P0 | 通过 | [SingleItemOrderTest](../../backend/src/test/java/cn/zhuangjimao/cart/SingleItemOrderTest.java)`#administrationAndCheckoutCannotSpendTheSameAvailableStock` + [任务 24](task-24.md)库存竞争与审计 |
| F07-T15 | P0 | 5.7.12 | 修改有效 CPU 性能评分；后续 F04 推荐使用有效更新数据 | 适用 P0 | 通过 | [PerformanceAdministrationTest](../../backend/src/test/java/cn/zhuangjimao/catalog/PerformanceAdministrationTest.java)`#recommendationReadsTheUpdatedCpuScore` |
| F07-T16 | P0 | 5.7.12 | 尝试绕过 F03 核心兼容检测；后台无此权限或入口，核心检测仍生效 | 适用 P0 | 通过 | [CompatibilityCheckTest](../../backend/src/test/java/cn/zhuangjimao/compatibility/CompatibilityCheckTest.java)`#coreAdmissionDoesNotAssertSaleOrStockAndCoreRulesCannotBeDisabledByInput` + [MixedOrderTest](../../backend/src/test/java/cn/zhuangjimao/cart/MixedOrderTest.java)`#finalValidationWaitsForAParameterWriterThenRejectsItsUnverifiableBuild` |
| F07-T17 | P0 | 5.7.12 | 查询用户列表；分页和基本筛选正确 | 适用 P0 | 通过 | [UserAdministrationTest](../../backend/src/test/java/cn/zhuangjimao/auth/UserAdministrationTest.java)`#administratorQueriesOnlyOrdinaryUsersWithPaginationAndSafeDetails` |
| F07-T18 | P0 | 5.7.12 | 查看用户详情；不暴露密码哈希或认证凭证 | 适用 P0 | 通过 | [UserAdministrationTest](../../backend/src/test/java/cn/zhuangjimao/auth/UserAdministrationTest.java)`#administratorQueriesOnlyOrdinaryUsersWithPaginationAndSafeDetails` |
| F07-T19 | P0 | 5.7.12 | 禁用普通用户后继续访问受保护接口；操作被拒绝，历史数据不丢失 | 适用 P0 | 通过 | [UserAdministrationTest](../../backend/src/test/java/cn/zhuangjimao/auth/UserAdministrationTest.java)`#disablingAndEnablingIsAuditedAndRevokesAllOldSessionsWithoutLosingAccountData` + [OrderAdministrationTest](../../backend/src/test/java/cn/zhuangjimao/cart/OrderAdministrationTest.java)`#disablingABuyerPreservesTheirPaidOrder` |
| F07-T20 | P0 | 5.7.12 | 尝试禁用管理员自己或修改普通用户角色；操作被拒绝 | 适用 P0 | 通过 | [UserAdministrationTest](../../backend/src/test/java/cn/zhuangjimao/auth/UserAdministrationTest.java)`#authorizationCsrfProtectedFieldsAndAdministratorsCannotBeBypassed` |
| F07-T21 | P0 | 5.7.12 | 按状态和订单号查询订单；能正确查询四类状态订单 | 适用 P0 | 通过 | [OrderAdministrationTest](../../backend/src/test/java/cn/zhuangjimao/cart/OrderAdministrationTest.java)`#mixedGroupSnapshotsAndEveryStatusRemainQueryableAcrossBuyers` + [order-administration](../../frontend/tests/order-administration.spec.ts) · 四状态筛选及详情 |
| F07-T22 | P0 | 5.7.12 | 查看包含 DIY 分组的订单明细；商品、分组、数量和金额快照一致 | 适用 P0 | 通过 | [OrderAdministrationTest](../../backend/src/test/java/cn/zhuangjimao/cart/OrderAdministrationTest.java)`#mixedGroupSnapshotsAndEveryStatusRemainQueryableAcrossBuyers` + [mixed-order](../../frontend/tests/mixed-order.spec.ts) |
| F07-T23 | P0 | 5.7.12 | 管理员完成有效已支付订单；状态转换为已完成 | 适用 P0 | 通过 | [OrderAdministrationTest](../../backend/src/test/java/cn/zhuangjimao/cart/OrderAdministrationTest.java)`#onlyPaidOrdersCompleteWithOneAtomicAuditAndNoInventoryChange` + [mixed-order](../../frontend/tests/mixed-order.spec.ts) |
| F07-T24 | P0 | 5.7.12 | 对同一订单并发提交完成操作；最多一次有效转换，无重复库存处理 | 适用 P0 | 通过 | [OrderAdministrationTest](../../backend/src/test/java/cn/zhuangjimao/cart/OrderAdministrationTest.java)`#concurrentAdministratorsProduceOneCompletionAndAudit` + [任务 24](task-24.md)完成竞争 |
| F07-T25 | P0 | 5.7.12 | 修改商品价格和参数后重新访问推荐、购物车及订单；后续业务采用新数据，已有订单保持快照 | 适用 P0 | 通过 | [任务 24](task-24.md) F07-T25 · SSD 改价与参数维护，实测推荐、购物车重报价、成交历史及审计 |
| F07-T26 | P0 | 5.7.12 | 成功修改价格、库存或订单状态；能查到正确的操作审计记录 | 适用 P0 | 通过 | [PriceStockTest](../../backend/src/test/java/cn/zhuangjimao/catalog/PriceStockTest.java)`#priceChangeIsVisiblePubliclyVersionedAndAuditedWithReason` + #auditFailureRollsBackPriceStockAndRevisionAndPublicRevisionTracksChanges + [OrderAdministrationTest](../../backend/src/test/java/cn/zhuangjimao/cart/OrderAdministrationTest.java)`#onlyPaidOrdersCompleteWithOneAtomicAuditAndNoInventoryChange` + [任务 24](task-24.md)并发审计 |
| F07-T27 | P1 | 5.7.12 | 进入后台运营概览；用户、商品、订单统计与实际业务记录一致 | P1 | 未纳入 | 原验收优先级超出本版范围 |
| F07-T28 | P1 | 5.7.12 | 批量导入包含重复 SKU 的商品文件；整批拒绝，并返回具体错误 | P1 | 未纳入 | 原验收优先级超出本版范围 |
| F07-T29 | P1 | 5.7.12 | 修改游戏推荐权重；合法配置生效并产生新版本 | P1 | 未纳入 | 原验收优先级超出本版范围 |
| F07-T30 | P1 | 5.7.12 | 按操作人、时间、类型筛选日志；返回对应审计记录 | P1 | 未纳入 | 原验收优先级超出本版范围 |
| F07-T31 | P1 | 5.7.12 | 上传非法格式或超出限制的图片；拒绝上传，不保存恶意文件 | P1 | 未纳入 | 原验收优先级超出本版范围 |
| F07-T32 | P1 | 5.7.12 | 导出商品或订单数据；字段符合授权范围，内容正确 | P1 | 未纳入 | 原验收优先级超出本版范围 |
