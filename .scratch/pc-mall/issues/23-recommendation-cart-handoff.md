# 23: 推荐到购物车的登录与复验联动

**What to build:** 推荐转DIY或加购贯通登录恢复、用户确认及当前价格库存兼容性重验。

Blocked by: [12：游客登录恢复与权威重验](12-guest-restoration.md)，[15：配置组修改、失效诊断与混合预结算](15-cart-checkout-preview.md)，[22：游戏、创作推荐及严谨失败分类](22-scenario-recommendation.md)

Status: ready-for-agent

**规格依据：** [电脑智能选配与商城联动系统实施规格](../spec.md)，版本 1.0，2026-10-28；原报告、追踪矩阵及来源指纹见母规格。保留原需求基线，本票不改冻结规则或增加 P1/P2 范围。

**主责需求：** F04-20、F05-23

**范围与交接：** 补齐推荐来源的游客恢复、DIY编辑与主动加购闭环。

复用12的恢复、15的配置修改/报价校验及22的推荐结构。来源只用于追溯，不是免验凭证；不新建推荐购物车或独立整机SKU。

## Acceptance criteria

- [ ] 三种推荐结果均可保留真实商品和来源转入DIY或发起主动加购。
- [ ] 游客加购先统一登录并恢复本次配置，重新读取当前商品与完整F03结果，不能自动入车。
- [ ] 登录后或已登录用户明确确认，满足当前结构、价格、库存与兼容性后按F05整组加入。
- [ ] 转DIY修改后旧推荐评分/解释失效，不继续声称满足旧预算或最优条件。
- [ ] 推荐后改价、缺货、下架、参数变更及登录过期均有重验反馈，不偷偷替换商品或沿用旧通过。
- [ ] 浏览器贯通三场景→DIY→登录恢复→确认入车，验证重复点击、源信息保留和无预占副作用。

## 原验收关联

F04-T23、F04-T24、F04-T25、F04-T26、F02-T13、F02-T18、F05-T24、SYS-T05、SYS-T06、SYS-T11。

原编号用于定位基线场景；部分跨模块场景由本票与关联任务共同核验，不表示场景已经执行或全部由本票独立完成。以原文优先级及预期为准，补充测试单独标识，不伪造冻结编号。

## 测试与完成证据

随本票交付对应的数据迁移、后端行为、用户界面和自动化验证；涉及持久化时使用明确隔离的真实 MySQL 测试库，核心确定性规则验证输入输出。记录执行命令、结果及适用原验收编号；失败、待执行或阻塞必须如实标记。所有阻塞任务完成后才能开始；本票完成后再更新执行状态，不以 ready-for-agent 表示已经实现。

## 复用参考

- [litemall 购物车业务参考](https://github.com/linlinjava/litemall/blob/a1ef964a718b7277925b19ea26afe78ea3a1d325/litemall-wx-api/src/main/java/org/linlinjava/litemall/wx/web/WxCartController.java)
- [Element Plus](https://github.com/element-plus/element-plus)
- [Spring Security 浏览器 CSRF 集成](https://docs.spring.io/spring-security/reference/servlet/exploits/csrf.html)

参考实现只能按本项目约束适配，不能因模板改变认证、库存、评分或兼容性准入。选择兼容的正式依赖版本并锁定；采用外部代码或数据时记录来源版本和对应许可归因，不将研究结果当成已运行验证。

## Comments
