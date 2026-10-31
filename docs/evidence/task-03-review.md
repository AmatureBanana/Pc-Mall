# 任务03两轴审查

固定基点：用户确认的`1904f36e47ac659f146074ea33bdc4dd3a79c618`。初始实现`c8fe821`，限流修复`a7bb06b`，收尾修复`4347a9b`。命令：`git diff 1904f36e47ac659f146074ea33bdc4dd3a79c618...HEAD`。按implement要求应用code-review技能，两个全新上下文代理分别审查Standards与Spec，均只读，未运行测试或修改文件。

规范来源：`AGENTS.md`、`docs/agents/issue-tracker.md`、`docs/agents/domain.md`、`docs/agents/triage-labels.md`、`README.md`、`docs/authentication.md`及技能的代码气味基线。未发现`GLOSSARY.md`、相关ADR、`CODING_STANDARDS.md`或`CONTRIBUTING.md`。规格来源为任务03、母规格和与原指纹一致的报告F01章节；测试边界由用户事先确认。

## Standards

首轮未发现文档规范硬性违反。P3判断建议1项：限流预约用`List<Bucket>`并通过`getFirst()`定位账户额度，依赖账户/IP的隐含顺序。已改为`Permit(account, peer)`及具名键，成功登录明确仅清除账户失败。

复审结果：无残余可执行Standards发现，无文档规范违反。

## Spec

首轮P1发现1项：被限制的IP在检查封禁前创建新用户名键，轮换用户名能填满10000键的全局表，让其他IP正常登录也429；不符合F01-12关于重复失败的安全处理。已在创建任何键前检查既有账户/IP限制。

公开HTTP回归采用16键容量：真实127.0.0.1失败3次受限后轮换20个用户名，另一个实际绑定127.0.0.2的客户端使用正确凭据登录。红灯为429，修复后200；完整认证测试10/10通过。没有mock限流内部实现，没有以X-Forwarded-For模拟不同IP。容量默认仍10000，可通过`LOGIN_MAX_KEYS`配置。

复审结果：无残余可执行Spec发现，P1已修复。

汇总：Standards首轮1项可选判断建议、最终0项；Spec首轮1项P1、最终0项。两轴分别报告，不相互抵消。最终完整测试另见[验证记录](task-03.md)。

## 收尾复审

首次完整后端测试发现无初始化凭据时，方法级事务也提前尝试连接数据库，影响既有故障注入503测试；改为短路后再用TransactionTemplate创建事务，保留有配置时的行锁及原子写入。故障测试与认证测试11/11通过。

浏览器补充持有一份真实已认证身份响应，退出后再释放，初轮错误恢复了账户显示；增加身份/后台请求修订检查后，旧响应不再覆盖新状态，认证浏览器3/3通过。

两位原审查代理分别复查`a7bb06b..4347a9b`：Standards=0，Spec=0。只读，无修改或测试；未用审查代替最终执行验证。
