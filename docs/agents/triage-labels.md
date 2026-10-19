# Triage Labels

本仓库使用以下五个标准分诊角色及同名状态值。

| 标准角色 | 本地状态值 | 含义 |
| --- | --- | --- |
| needs-triage | needs-triage | 等待维护者评估 |
| needs-info | needs-info | 等待补充信息 |
| ready-for-agent | ready-for-agent | 规格充分，可由代理自主实施 |
| ready-for-human | ready-for-human | 需要人工实施 |
| wontfix | wontfix | 决定不处理 |

当技能要求应用某个分诊标签时，
使用上表中的本地状态值更新任务文件的 `Status:` 行。

以后调整词汇时，修改“本地状态值”列，
并同步更新已有任务中的相关状态。
