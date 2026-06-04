# AI README - Codex 项目上下文入口

## 项目总览

Aster 是一个教学版 Java Agent Runtime MVP，用于演示流式 LLM、AgentLoop、Tool Calling、上下文压缩、Session 持久化、HITL 工具审批、MCP、Skill、长期记忆、后台任务、自动化用户消息 schedule、TUI/Web/Telegram 多入口、固定 Agent Team、动态 DAG Plan、Web 多 Agent 聊天室和归档中心的最小可运行架构。

```mermaid
flowchart LR
    UI["ui: TUI / Web / Telegram"] --> Runtime["app/runtime: AgentRuntime"]
    Runtime --> Core["core: AgentLoop / Context / Tool / Event"]
    Core --> LLM["llm: OpenAI-compatible SSE"]
    Runtime --> App["app: tools / MCP / memory / background / schedule / plan / team / room"]
```

## 生成信息

- 生成时间：2026-06-04 14:24
- 生成分支：master
- 最近同步：2026-06-05，补充入口能力矩阵、Room、Archive、Web 多 session 并行 runtime、schedule/background 分离和文档维护规则

## 入口功能矩阵

| 功能 | TUI | Web | Telegram IM | 说明 |
| --- | --- | --- | --- | --- |
| 普通 Agent 对话 | 已实现 | 已实现 | 已实现 | 三个入口都通过 `AgentRuntime.submit()` 进入同一主链路。 |
| 流式响应展示 | 已实现 | 已实现 | 部分实现 | TUI/Web 展示 token 流；Telegram 缓存 token，最终一次性发送回答，避免刷屏。 |
| 工具调用可视化 | 已实现 | 已实现 | 部分实现 | TUI/Web 展示工具状态；Web 合并调用和结果并支持折叠；Telegram 展示工具开始/完成，长内容截断。 |
| HITL 工具审批 | 已实现 | 已实现 | 已实现 | `/approve [id]`、`/deny [id] [reason]`；Web 使用审批块按钮。 |
| `/stop` 停止 | 已实现 | 已实现 | 已实现 | 停止普通 run、取消审批、取消待执行 Plan；Room 同步回复当前还不是完整可中断流。 |
| `/steer` 运行中引导 | 已实现 | API 已有，页面未展示 | 未实现 | TUI 有 `/steer` 命令；Web 有 `/api/steer`，当前页面没有入口；Telegram 未接命令。 |
| follow-up 排队 | 已实现 | 已实现 | 已实现 | 忙碌时普通输入进入 `AgentRunCoordinator` 队列。 |
| Session CRUD | 部分实现 | 已实现 | 部分实现 | TUI 支持 list/new/use/delete/current；Web 支持列表、新建、切换、重命名、归档、历史读取；Telegram 支持当前 session 和新建。 |
| 多 session 并行运行 | 未实现 | 已实现 | 已实现 | Web 用 `WebSessionRuntimePool` 保留每个 session 的 `AgentRuntime`，切换会话不打断旧会话；Telegram 每个 chat 也持有独立 runtime。 |
| Token/Context 状态 | 已实现 | 已实现 | 未实现 | TUI footer 和 Web 右栏展示；Telegram 不展示指标面板。 |
| Todo 便签 | 通过工具可用 | 已实现 | 通过工具可用 | Web 有右侧便签 CRUD；普通 Agent 可用 todo 工具；TUI/IM 没有专门面板。 |
| 后台任务通知 | 已实现 | 已实现 | 已实现 | 通过各入口的 `NotificationSink` 展示长期记忆抽取、Todo 扫描等通知。 |
| 自动化用户消息 schedule | 通过工具可用 | 通过工具可用 | 通过工具可用 | `schedule` 到点后向当前 session 提交 user 消息；不是后台 handler，后续仍走普通 Agent 链路。 |
| `/team` 固定 DAG 探索 | 已实现 | 已实现 | 已实现 | 三个入口都能触发；Team 子 Agent 工具调用不展示，避免刷屏。 |
| `/plan` 动态 DAG | 已实现 | 已实现 | 已实现 | 支持 `/plan` 生成、`/start` 执行、`/plan cancel` 取消。 |
| Web 多 Agent 聊天室 | 未实现 | 已实现 | 未实现 | 只有 Web 有 Room 页面、房间 CRUD、成员管理、Agent CRUD、`@name`/`@all` 触发。 |
| Room Agent 配置管理 | 未实现 | 已实现 | 未实现 | Agent 的 name、role、alias、工具白名单、system prompt 在 Web 中维护；加入/移出聊天室由成员关系管理。 |
| 归档中心 | 未实现 | 已实现 | 未实现 | Web Archive 页面集中恢复、单个物理删除或批量物理删除已归档 session、todo、room、room-agent。 |

## 快速导航

### AI 生成文档（generated/）

- [x] [项目结构](./generated/project-structure.md) - 目录树、模块划分；了解代码组织时使用 (2026-06-04 14:24)
- [x] [技术架构](./generated/architecture.md) - 分层架构、技术栈；了解技术选型时使用 (2026-06-04 14:24)
- [x] [开发指南](./generated/development-guide.md) - 环境搭建、构建/启动命令；上手时使用 (2026-06-04 14:24)
- [x] [核心流程](./generated/core-flows.md) - 主要业务调用链；理解系统时使用 (2026-06-04 14:24)

### AI 与人工共同维护文档（manual/）

- [ ] [业务知识](./manual/business-knowledge.md) - 项目背景、领域术语、业务规则；对话中形成的共识要持续沉淀
- [ ] [历史经验](./manual/lessons-learned.md) - 踩坑记录、设计取舍和修复经验；写代码前必读

## 使用建议

- 涉及架构、跨模块修改或不熟悉的目录时，先读 `generated/` 对应文档。
- 涉及业务术语、项目背景、历史坑点时，先读 `manual/`；用户和 AI 对话过程中形成的新规则、新取舍和踩坑经验，应同步沉淀到这里。
- 涉及代码改动、架构变化、功能新增、经验沉淀或用户指出文档过时时，交付前都要评估是否需要同步更新 `docs/ai-readme/`；不需要更新时在最终说明里写清楚原因。
- 这些文档由代码和现有配置推断生成；不确定的内容不会写成事实。
