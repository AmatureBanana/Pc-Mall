# 认证、个人资料与后台准入

任务03的接口均为同源 JSON。注册字段为 `username`、`password`、`confirmPassword`、可选 `nickname`；登录只收 `username`、`password`。未知字段（包括 `role`、`user_id`）、非字符串类型和不合法输入为400/`INVALID_INPUT`。用户名4～20位ASCII字母、数字或下划线，应用查询与MySQL唯一约束均不区分大小写；昵称最多30个Unicode字符，未填时使用用户名。密码8～64个Unicode字符且UTF-8至多72字节，拒绝未配对代理字符；使用Spring Security受管BCrypt默认cost 10。

| 接口 | 身份 / 行为 |
| --- | --- |
| `GET /api/auth/csrf` | 匿名可用，返回 `data: {token, headerName, parameterName}`，令牌保存在Session |
| `POST /api/auth/register` | 匿名＋CSRF；201，只建ACTIVE/USER，不自动登录；大小写重名409/`USERNAME_TAKEN` |
| `POST /api/auth/login` | 匿名＋CSRF；200返回本人ID、用户名、昵称和角色；错误/不存在/禁用统一401/`INVALID_CREDENTIALS`；限流429/`LOGIN_RATE_LIMITED` |
| `GET /api/auth/me` | 已认证；只返回服务端Session身份对应的摘要；客户端user_id不能切换身份 |
| `GET /api/account/profile` | 已认证；返回本人 `id`、`username`、`nickname`、`role`，不返回密码哈希、凭据版本或Session凭据 |
| `POST /api/account/profile` | 已认证＋CSRF；只接受字符串 `nickname`，200返回更新后的本人摘要；超过30个Unicode字符、未配对代理字符、缺失字段、非字符串或额外字段为400 |
| `POST /api/account/password` | 已认证＋CSRF；只接受 `oldPassword`、`newPassword`、`confirmPassword`；成功200并要求重新登录；旧密码错误400/`CURRENT_PASSWORD_INCORRECT`，其他输入错误400/`INVALID_INPUT` |
| `POST /api/auth/logout` | CSRF；200，当前Session失效、Cookie删除，重复退出保持已退出状态 |
| `GET /api/admin/overview` | ADMIN；返回三个导航模块，商品维护与普通用户启禁已接入，订单由后续任务交付 |

账号哈希不出现在任何业务响应中，注册成功响应只含提示。登录显式执行Session ID更换、CSRF令牌废弃与SecurityContext保存。浏览器每次写入前重新取令牌，以返回的headerName发送；保留Spring Security默认的Session令牌仓库与XOR掩码，不关闭CSRF。旧令牌不能跨登录使用；退出后的旧Cookie不能恢复身份。GET响应沿用Spring Security的no-store头，前端也显式禁用认证请求缓存。

Session空闲两小时失效。仅Cookie跟踪，不接受URL重写Session ID；HttpOnly=true、SameSite=Lax，本机HTTP下Secure=false，HTTPS部署设置`SESSION_COOKIE_SECURE=true`。不使用JWT、本地存储用户身份或客户端声明角色。每个已登录请求都会重查ACTIVE状态及凭据版本，禁用/删除或版本失配后使原Session失效；认证数据库失败返回503，不放行。

资料入口为商城顶部“个人资料”及 `/#profile`，适用于USER和ADMIN。ID、用户名及角色只读。昵称按Unicode码点计数，汉字和emoji各计一个字符；空串或不超过30字符的全空白昵称沿用注册规则，回退为用户名。客户端用户标识不能选择其他账户，资料和密码请求中的额外字段一律拒绝。未增加头像、联系方式、找回密码或管理员重置密码入口。

V8迁移给已有账户增加默认0的 `credential_version`，不改写既有密码。认证主体保留实际校验密码时读取的账户ID和版本；继承Spring Security的User以保持认证后擦除密码。修改昵称及密码都在事务内锁定账户行，再校验Session版本。改密验证旧密码、8～64个Unicode字符且UTF-8至多72字节的新密码以及确认值，原子提交BCrypt哈希和版本递增，然后结束发起会话。其他旧会话在下一次请求进入业务逻辑前被拒绝并清理；已在途的旧登录不能得到有效的新版本会话，并发旧凭据写入不能覆盖新密码。不增加Session扫描表或分布式中间件。错误输入回滚变更并保持既有密码和有效会话可用。

公开商品GET保持匿名；未来公开检测和推荐写入口保留为`POST /api/compatibility/check`、`POST /api/recommendations`，仍须CSRF，这里仅建立权限边界，不提供这些业务。`/api/account/**`、`/api/cart/**`、`/api/orders/**`要求认证，`/api/admin/**`要求ADMIN，其他路径默认拒绝。访客访问私有读写为401；USER访问管理路径为403。认证写请求缺少/无效CSRF为403/`CSRF_INVALID`；私有能力在未登录或Session失效时优先明确返回401。

登录失败窗口、阈值、锁定时长可独立配置，默认5次/15分钟/锁15分钟。这是配置默认值，不是冻结业务规则。账号键按大小写归一化；IP键取实际连接地址，忽略客户端X-Forwarded-For。两个键分别限制，成功登录只清除该账户的失败，不抹掉IP历史；并发认证预留额度，避免同时请求绕过阈值。内存默认最多10000个活动键（`LOGIN_MAX_KEYS`可调，至少2），达到容量时返回429；已受限账户/IP在分配任何新键之前拒绝，避免受限IP轮换用户名占满全局表。单体重启清空限流状态，符合本版不引入Redis或分布式Session的范围。

初始管理员凭据只接受受控环境变量；不设默认管理员密码。未配置时正常启动，配置不完整或不符合字段限制时启动失败。数据库初始化行锁串行检查：已有任意ADMIN时不再创建或重置；没有ADMIN但目标用户名已有普通账户时拒绝提升并启动失败。`scripts/start-with-admin.ps1`用隐藏输入读取密码、仅注入子进程环境，结束后恢复原环境。不要把真实凭据加入命令行参数、迁移SQL或版本控制。

实现参考[Spring Security 6.5 CSRF文档](https://docs.spring.io/spring-security/reference/6.5/servlet/exploits/csrf.html)的Session令牌与`/csrf`端点，以及[会话持久化文档](https://docs.spring.io/spring-security/reference/6.5/servlet/authentication/session-management.html)的显式保存契约；2026-10-31核对，实际版本受Spring Boot 3.5.16 BOM约束。未复制外部商城业务代码或引入新第三方依赖，许可延续[data-sources.md](data-sources.md)。

任务04沿用上述依赖，认证主体扩展依据[DaoAuthenticationProvider文档](https://docs.spring.io/spring-security/reference/6.5/servlet/authentication/passwords/dao-authentication-provider.html)中认证返回原UserDetails主体的契约；2026-10-31核对。验证及原验收F01-T11/T12覆盖见[任务04证据](evidence/task-04.md)。
