# Issue tracker: Local Markdown

本仓库的任务和规格以 Markdown 文件保存在 `.scratch/`。

## 文件约定

- 每个功能一个目录：`.scratch/<feature-slug>/`。
- 规格文件：`.scratch/<feature-slug>/spec.md`。
- 实施任务：`.scratch/<feature-slug>/issues/<NN>-<slug>.md`。
- 任务从 `01` 开始编号，每个任务独立成文件。
- 分诊状态写在任务文件顶部附近的 `Status:` 行中；
  状态名称见 `triage-labels.md`。
- 评论和讨论记录追加在文件底部的 `## Comments` 下。

## 技能要求“发布到任务跟踪器”时

在 `.scratch/<feature-slug>/` 下创建对应文件，
按需创建目录，并遵循上述规格和任务文件约定。

## 技能要求“获取相关任务”时

读取指定路径的任务文件。
如果只提供编号，结合当前功能目录定位；
存在多个匹配时先澄清。

## Wayfinding 操作

供 `/wayfinder` 使用。

- 路线图：`.scratch/<effort>/map.md`，
  包含 Notes、Decisions-so-far 和 Fog。
- 子任务：`.scratch/<effort>/issues/NN-<slug>.md`，
  从 `01` 开始编号，在正文中记录待解决的问题。
- 类型：使用 `Type:` 行记录
  `research`、`prototype`、`grilling` 或 `task`。
- 执行状态：由 wayfinder 管理的任务使用
  `Status: claimed` 和 `Status: resolved`。
  这两种执行状态与分诊标签的含义分别解释。
- 阻塞关系：在文件顶部附近使用 `Blocked by: NN, NN`；
  所有依赖任务均为 `resolved` 后，任务才解除阻塞。
- 可执行任务：按编号扫描，选择尚未解决、未被领取、
  且没有未解决依赖的第一个任务。
- 领取：开始工作前设置 `Status: claimed` 并保存。
- 解决：在 `## Answer` 下追加答案，
  设置 `Status: resolved`，并在路线图的
  Decisions-so-far 中追加摘要及任务文件链接。
