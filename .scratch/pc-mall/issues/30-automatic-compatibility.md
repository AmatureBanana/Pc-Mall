# 30: 移除手动重检入口并自动检测配置变更

Status: resolved

## Acceptance criteria

- [x] 删除配置修订建议中的「配置或参数变化后需重检」与「重新检测」按钮。
- [x] 添加、替换、移除配件及切换核显/独显后，自动检测当前配置；清除旧结论、阻止旧响应覆盖。
- [x] 保留检测失败后的重试入口，验证真实硬件变更引发和消除冲突，不提交 Git。

## Answer

2026-11-08：删除建议区整行手动重检提示与按钮。复核 `useDiyWorkspace.change` 已统一在配置变更后立即刷新报价和兼容检测，继续沿用请求取消/过期保护。保留失败重试及聚焦/可见/定时参数重验。新增真实硬件浏览器回归验证换 AMD CPU 自动出现插槽冲突、换回 Intel 自动消除冲突、重新选择核显自动通过、移除主板立即失去通过状态，完全无需手动重检。

专项 Edge24/24、类型检查、Vite与同源JAR构建通过；`git diff --check`通过。日志 `.local/auto-compatibility-tests.log`，截图[建议区](../../../docs/evidence/diy-auto-compatibility.png)。原有证据图恢复，未提交Git。
