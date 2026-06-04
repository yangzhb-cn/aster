# AGENTS.md

本文件是 Codex / AI 编码 Agent 进入 Aster 仓库后的项目入口。先读本文件；涉及架构、跨模块修改或不熟悉的目录时，再读 `docs/ai-readme/README.md`。

## 项目概述

Aster 是一个教学版 Java Agent Runtime MVP，用来演示 AgentLoop、OpenAI-compatible SSE 流式 LLM、Tool Calling、上下文压缩、Session JSONL、HITL 工具审批、MCP、Skill、长期记忆、后台任务、自动化用户消息 schedule、动态 DAG Plan、固定 DAG Agent Team、Web 多 Agent 聊天室，以及 TUI/Web/Telegram 多入口。

它不是生产级多租户平台。做改动时优先保持结构清晰、代码少、教学可读，不要为了“以后可能需要”提前堆复杂抽象。

## 开发命令

- 准备配置：`cp .env.example .env`
- 启动 TUI：`./aster2tui`
- 启动 Web：`./aster2web`
- 启动 Telegram：`./aster2im`
- Maven 启动默认入口：`mvn -q exec:java`
- Maven 启动 Web：`mvn -q -Dexec.mainClass=com.aster.ui.web.WebMain exec:java`
- Maven 启动 Telegram：`mvn -q -Dexec.mainClass=com.aster.ui.im.telegram.TelegramMain exec:java`
- 运行测试：`mvn test`
- 构建打包：`mvn package`

真实 `.env`、`workspace/`、`.firecrawl/` 和用户运行数据不要提交；`.env.example` 是可提交示例。

## 关键目录

- `src/main/java/com/aster/llm/`：OpenAI-compatible SSE 和 provider 适配。
- `src/main/java/com/aster/core/`：AgentLoop、Context、Tool、Hook、Event、Session、Stage 抽象。
- `src/main/java/com/aster/app/runtime/`：运行时装配、普通 run 调度、Plan 模式协调。
- `src/main/java/com/aster/app/extension/`：可选能力注册入口。
- `src/main/java/com/aster/app/tool/`：内置工具、开发者工具、Todo/后台/schedule 工具、工具结果卸载。
- `src/main/java/com/aster/app/hitl/`：HITL 工具审批。
- `src/main/java/com/aster/app/background/`：后台任务、延时提醒、Todo/记忆维护扫描。
- `src/main/java/com/aster/app/schedule/`：自动化用户消息，到点后向当前 session 提交 user 消息。
- `src/main/java/com/aster/app/plan/`：动态 DAG Plan。
- `src/main/java/com/aster/app/team/`：固定 DAG Agent Team。
- `src/main/java/com/aster/app/room/`：Web 多 Agent 聊天室，包含房间消息、Agent 配置、mention 解析和房间上下文注入。
- `src/main/java/com/aster/ui/`：TUI、Web、Telegram 表现层。
- `src/main/resources/prompts/`：外部化 prompt。
- `src/test/java/com/aster/`：JUnit 5 测试。
- `docs/ai-readme/`：AI + 人类共用项目上下文文档。

## 边界约束

- 依赖方向保持 `ui -> app/runtime -> core -> llm`。
- `llm` 不知道 AgentLoop、Tool、UI。
- `core` 不反向依赖 `app`；核心层只放抽象和主流程。
- `app` 实现具体能力，例如工具、MCP、Skill、HITL、Memory、Background、Schedule、Todo、Plan、Team、Room。
- `ui` 只调用 `AgentRuntime` 并消费 `AgentEventEnvelope`，不要直接拼装或侵入 `AgentLoop`。
- Web Chat 使用 `WebSessionRuntimePool` 让多个普通 session 并行运行；切换 session 不能关闭旧 runtime，消息、停止、审批等运行控制必须按 `sessionId` 路由。
- Web SSE 事件必须按 `AgentEventEnvelope.meta.sessionName` 过滤；非当前 session 的事件只能更新会话状态，不要渲染到当前聊天窗口。
- 新代码统一使用 `com.aster` 包名，不再使用旧包名。
- 新增注释用中文；新增类要写类注释；核心方法要写方法注释。
- 不要新增非流式 LLM 主路径；当前主路径是 SSE 流式调用。
- 上下文压缩必须按消息/turn 边界处理，不要字符串硬切 conversation history。
- 主 runtime 使用 `ContextWindowCache` 维护“旧对话摘要 + 最近完整 turn”；不要在每轮 LLM 请求前反复全量 replay JSONL。
- `SessionStore` 保存完整原始历史；压缩摘要、长期记忆、当前时间等动态内容只进入本轮请求上下文。
- `assistant.tool_calls` 与 `role=tool` 的 `tool_call_id` 必须保持配对；工具失败或审批拒绝也要写回合法 tool 结果。
- `read/write/bash/edit` 是固定底座工具；`load_skill`、MCP、`todo`、`background_task`、`schedule`、新工具和可选能力优先通过 `AsterRuntimeExtension` 注册。
- `bash/write/edit` 属于高影响工具，默认走 HITL 审批。
- `background_task` 只用于系统后台任务、延时提醒、Todo 扫描和长期记忆抽取；需要 Agent 到点自动执行的任务用 `schedule`，由它提交 user 消息进入普通 Agent 链路。
- Team 当前是只读探索，不注册写工具、bash、todo、background_task、schedule 或 subagent。
- Plan 子 Agent 复用主 ToolRegistry 和 HookRegistry，高影响工具仍走 HITL；`FILE_WRITE` 和 `COMMAND` 节点需要写锁串行。
- Room Agent 当前只开放只读/检索类工具；不要给聊天室 Agent 注册 `write/edit/bash/todo/background_task/schedule/subagent`。
- Room 共享消息只保存用户消息、Agent 最终回复和系统提示；工具过程、reasoning 和私有上下文不写入 hub message。
- Room Agent 有独立 JSONL session；房间共享消息通过 `RoomContextInjectHook` 临时注入最后一条 user 消息，不要直接落入 Agent 私有 session。
- Room 成员关系由 `members.json` 管理；从聊天室移除 Agent 只归档成员关系，恢复时递增 generation 并使用新的私有 session。
- Room `@all` 只触发当前聊天室成员；Agent 可并行执行，但回复必须按成员 `orderIndex` / `replyIndex` 稳定写回。
- Web Archive 支持已归档对象的恢复、单个物理删除和批量物理删除；物理删除仍只能作用于已归档对象。
- 修改核心链路、工具协议、Plan/Team、Session、后台任务、schedule 或 UI 事件映射后，至少运行 `mvn test`。
- 纯文档修改可以不跑测试，但最终说明要写清楚。

## Codex 上下文

详细项目上下文见 `docs/ai-readme/README.md`：

- 项目结构：`docs/ai-readme/generated/project-structure.md`
- 架构：`docs/ai-readme/generated/architecture.md`
- 开发指南：`docs/ai-readme/generated/development-guide.md`
- 流程：`docs/ai-readme/generated/core-flows.md`
- 业务（AI 与人工共同维护）：`docs/ai-readme/manual/business-knowledge.md`
- 踩坑（AI 与人工共同维护）：`docs/ai-readme/manual/lessons-learned.md`

执行任务前：

- 涉及架构、跨模块修改或不熟悉的目录时，先读对应 `docs/ai-readme/generated/` 文档。
- 涉及业务规则、术语、历史坑点时，先读对应 `docs/ai-readme/manual/` 文档；用户和 AI 对话中形成的稳定共识要沉淀回 manual。
- 涉及代码改动、架构变化、功能新增、入口能力变化、经验沉淀或用户指出文档过时时，交付前评估是否需要同步更新 `docs/ai-readme/README.md`、`generated/`、`manual/`。
- 不确定的信息不要补全为事实，使用 `<!-- TODO -->` 标记并在交付时说明。
- 维护本文件时，保留已有团队规则；详细分析优先放入 `docs/ai-readme/`，不要把所有内容塞回 `AGENTS.md`。
