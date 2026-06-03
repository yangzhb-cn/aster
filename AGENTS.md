# AGENTS.md

本文件是给后续 Codex / AI 编码 Agent 的项目规则。进入本仓库后，先读本文件，再读 `README.md` 和相关源码。

## 项目定位

Aster 是一个教学版 Java Agent MVP，用来演示 AgentLoop、流式 LLM、上下文压缩、工具调用、HITL 工具审批、MCP、Skill、Session 持久化、长期记忆、后台任务和 TUI/Web/IM 的最小可运行架构。

它不是完整产品。做改动时优先保持结构清晰、代码少、教学可读，不要为了“以后可能需要”提前堆复杂抽象。

## 技术栈

- Java 21
- Maven
- Jackson
- OkHttp
- Lanterna
- JUnit 5 + MockWebServer

常用命令：

```bash
mvn test
mvn -q exec:java
```

默认模型配置走 DeepSeek OpenAI-compatible 协议：

```bash
export DEEPSEEK_API_KEY=你的 key
```

## 当前分层

依赖方向必须保持：

```text
ui
 ↓
app/runtime
 ↓
core
 ↓
llm
```

目录职责：

```text
src/main/java/com/aster/
├── llm/        模型 API 适配层
├── core/       Agent 主流程和抽象契约
├── app/        具体能力实现和运行时装配
└── ui/         表现层，目前是 TUI
```

关键规则：

- `llm` 不知道 AgentLoop、Tool、TUI。
- `core` 不反向依赖 `app`，只能依赖 `llm` 和自己的抽象。
- `app` 实现具体能力，例如内置工具、MCP、Skill、长期记忆、后台任务。
- `ui` 只消费事件和调用 runtime，不直接拼装核心能力。
- 不再使用旧包名 `dev.agentmvp`，新代码统一放在 `com.aster` 下。

## 核心包边界

### `llm/`

负责模型 API 适配。

- `StreamingChatClient` 是流式模型客户端抽象。
- `OpenAiCompatibleChatClient` 负责 OpenAI-compatible SSE 请求。
- `OpenAiCompatibleStreamParser` 把供应商 SSE 转成 `ProviderStreamEvent`。
- DeepSeek 只是当前默认 provider，后续 Kimi、Anthropic、Google 等供应商应该通过各自 provider/parser 适配，再输出统一事件。

不要新增非流式主路径。Aster 当前只保留流式 LLM 调用。

### `core/agent`

`AgentLoop` 是主循环，只负责编排：

```text
用户输入
  -> 写入 SessionStore
  -> ContextPipeline 构造上下文
  -> BEFORE_LLM_REQUEST Hook
  -> 流式请求 LLM
  -> 解析 assistant/tool_calls
  -> BEFORE_TOOL_CALL Hook
  -> 并行执行工具
  -> BEFORE_TOOL_RESULT_APPEND Hook
  -> 写回 assistant/tool 消息
  -> AFTER_RUN Hook
```

不要把具体 app 能力硬塞进 `AgentLoop` 构造器。新能力优先通过 Hook、Stage、Tool、EventHandler 或 runtime 装配接入。

### `core/event`

Event 表示“发生了什么”，用于 TUI、后续 Web SSE、日志、审计。

```text
ProviderStreamEvent
  -> AgentLoop
  -> AgentEvent
  -> AgentEventBus
  -> AgentEventEnvelope
  -> TUI / Web / Log
```

新增 UI、Web、日志时，实现 `AgentEventHandler`，不要改 AgentLoop 的展示逻辑。

### `core/hook`

Hook 表示“在主流程某个点插入扩展逻辑”。

当前 HookPoint：

| HookPoint | 用途 |
| --- | --- |
| `BEFORE_LLM_REQUEST` | 注入带当前时间的 `<system-reminder>`、改写本轮 messages/tools。 |
| `BEFORE_TOOL_CALL` | 工具权限、高危险工具审查、HITL。 |
| `BEFORE_TOOL_RESULT_APPEND` | 工具结果写入上下文前裁剪、脱敏、外部卸载。 |
| `AFTER_RUN` | 每轮对话结束后提交后台任务，例如长期记忆抽取。 |

后续想加逻辑，优先注册 Hook。不要在 AgentLoop 里散落 if-else。

### `core/stage`

Stage 是主流程必经步骤，不靠外部注册决定是否执行。

当前内置 Stage：

- `LoadSessionMessagesStage`：读取完整 session 历史。
- `ContextCompressionStage`：压缩旧上下文并校验工具协议。
- `StagePipeline`：多个 Stage 的固定流水线抽象。

上下文压缩属于 Stage，不属于可选 Hook。因为它是每轮请求 LLM 前的安全必经流程。

### `core/context`

上下文处理必须按消息边界做，不能切断工具协议。

关键规则：

- 按 user turn 切分消息。
- 保留当前最后一个 user turn，以及它之前最近 3 个已完成 user turn。
- 压缩更旧的 turn。
- 压缩摘要不作为单独 message 注入，由请求前 Hook 放入最后 user 消息开头的 `<system-reminder>`。
- 当前日期、当前时间、当前时区也由请求前 Hook 放入同一个 `<system-reminder>`，只参与本轮请求。
- 最终发送前必须校验 `assistant.tool_calls` 与 `role=tool` 的 `tool_call_id` 成对。
- 压缩后的 assistant message 不允许残留已经失配的 `tool_calls` 字段。

不要为了省 token 用字符串硬切 conversationHistory。

### `core/tool`

工具层只保留抽象和调度：

- `ToolRegistry`
- `ToolExecutor`
- `ToolHandler`
- `ParallelToolExecutor`
- `ToolResult`

内置工具和 MCP 工具都要适配到统一 Tool 抽象。LLM 一次返回多个 tool_call 时，允许并行执行，但写回 session 时仍要按原工具调用顺序配对。

### `core/session`

Session 是可回溯、可分支、可恢复、可审计的原始对话历史。

规则：

- SessionStore 保存完整原始消息，不保存压缩后的“临时上下文”。
- 压缩只影响本轮发给 LLM 的 request messages。
- JSONL 是当前对话历史持久化格式，文件名就是稳定的 sessionId。
- `workspace/sessions/index.json` 只保存 session 元信息：displayName、createdAt、updatedAt、archived。
- 新 sessionId 使用 `sess_yyyyMMdd_HHmmss_uid` 形式；displayName 只用于 UI 展示，重命名不能改 JSONL 文件名。
- 删除会话必须做软删除：把 `archived` 置为 true，不删除 JSONL 审计文件。
- 新增会话能力时不要破坏已有 session replay。

## App 能力层

### Runtime Extension

运行时可扩展能力放在 `app/extension/`，用于把能力注册到现有系统里，不直接改 AgentLoop。

当前默认扩展：

- `SkillToolExtension`：注册 `load_skill` 工具。
- `DeveloperToolExtension`：注册 `ls`、`glob`、`grep`、`subagent`、`web_fetch`、`web_search`。
- `BackgroundTaskToolExtension`：注册 `background_task` 后台任务管理工具。
- `TodoToolExtension`：注册 `todo` Web 便签待办工具。
- `McpToolExtension`：读取 `workspace/mcp.json` 并注册 MCP tools。
- `ToolApprovalExtension`：注册 `bash`、`write`、`edit` 工具调用人工审批 Hook。
- `SteerExtension`：注册运行中引导 Hook。
- `SystemReminderExtension`：注册请求前当前时间 + `<system-reminder>` 注入 Hook。
- `MemoryExtension`：注册长期记忆抽取 Hook。
- `ToolResultExtension`：注册大工具结果卸载 Hook。

规则：

- `AgentRuntimeFactory` 只负责创建基础对象、注册四个底座工具、应用 RuntimeExtension。
- 新增非底座工具、Hook、EventHandler 时，优先新增 `AsterRuntimeExtension` 实现。
- 不要为了新增一个可选能力继续堆大 `AgentRuntimeFactory`。
- RuntimeExtension 只做注册，不替代 `AgentLoop`、`ContextPipeline`、`SessionStore`。

### 内置工具

内置工具放在 `app/tool/builtin/`：

- `read`
- `write`
- `bash`
- `edit`

这四个是固定底座工具，由 `BuiltinTools` 直接注册。不要把 `load_skill`、MCP 或后续新工具混回基础内置工具集合。

每个工具一个类，实现统一工具接口，最后统一注册到 `ToolRegistry`。不要把多个工具写进一个大类。
新增工具默认走 RuntimeExtension；只有确实属于 Agent 宿主底座能力时，才考虑放入 `app/tool/builtin/`。

路径类工具当前教学版不做复杂权限系统。后续若要做权限，走 `BEFORE_TOOL_CALL` Hook 或独立审批能力，不要在工具里散落临时判断。

### HITL 工具审批

HITL 审批放在 `app/hitl/`，通过 `ToolApprovalExtension` 注册到 `BEFORE_TOOL_CALL`。

当前默认审批工具：

- `bash`
- `write`
- `edit`

规则：

- 不要在 `BashTool`、`WriteTool`、`EditTool` 里硬编码审批逻辑。
- 审批请求通过 `ToolApprovalRequested` 事件发给 TUI/Web/Telegram，必须展示工具名、审批 id 和原始参数。
- 审批通过后继续执行原始 `tool_call_id` 和原始参数，不重新构造 tool_call。
- 审批拒绝后仍要写回一条 `role=tool` 错误结果，保持 tool_call/tool_result 协议完整。
- `/approve <id>` 批准单个工具；`/approve` 不带 id 表示批准全部待审批工具。
- `/deny <id> [reason]` 拒绝单个工具；`/deny` 不带 id 表示拒绝全部待审批工具。
- `/stop` 必须释放正在等待的审批，避免 Agent run 卡住。

### 开发者扩展工具

开发者扩展工具放在 `app/tool/developer/`，由 `DeveloperToolExtension` 通过 RuntimeExtension 注册。

当前工具：

- `ls`
- `glob`
- `grep`
- `subagent`
- `web_fetch`
- `web_search`

规则：

- 不要把这些工具放进 `BuiltinTools`；`BuiltinTools` 只保留 `read/write/bash/edit` 四个底座工具。
- 每个工具一个类，实现 `DeveloperTool`，共享逻辑放 `AbstractDeveloperTool`。
- `subagent` 可以创建内存版子 Agent，但子 Agent 不再注册 `subagent`，避免递归调用自身。
- `web_search` 使用 Tavily，需要 `TAVILY_API_KEY`；没有 key 时返回工具错误，不访问真实网络。

### 后台任务工具

后台任务管理工具放在 `app/tool/background/`，由 `BackgroundTaskToolExtension` 通过 RuntimeExtension 注册。

当前工具：

- `background_task`

规则：

- 工具只调用 `BackgroundTaskManager`，不要直接调用 `BackgroundTaskScheduler` 或 `BackgroundTaskExecutor`。
- `background_task` 只管理任务定义：创建 immediate/delay/interval、列出任务、取消任务。
- 真正执行什么由 `TaskAction.type` 对应的 `BackgroundTaskHandler` 决定；当前支持 `reminder`、`todo_scan`、`memory_extract`。
- `todo_scan` 是系统内部便签待办扫描动作，由 runtime 启动时确保存在，不要求 Agent 手动创建。
- 不要新增 `noop` 类任务；没有真实动作的任务不应该暴露给 Agent。
- 新增一种后台动作时，先新增 `BackgroundTaskHandler`，再让 `background_task` 创建对应 `TaskAction`。

### 便签待办

便签待办放在 `app/todo/`，Web 右栏、Agent `todo` 工具和后台扫描器共用同一个 `TodoStore`。

规则：

- 当前状态保存到 `workspace/todos/todos.json`，不要用 JSONL 保存当前清单。
- `app/tool/todo/TodoTool` 只管理清单：list/add/update/complete/archive。
- `TodoScanTaskHandler` 只处理到期提醒：扫描 `status=PENDING` 且 `dueAt <= now` 的待办，推送通知并标记 completed。
- 第一版不让后台 Agent 自动执行复杂任务；如果要做自动执行，新增独立 handler，不要塞进 `TodoScanTaskHandler`。

### 工具结果外部卸载

大工具结果不要直接塞满上下文。

当前规则：

- 大结果写入 `workspace/artifacts/tool-results/*.jsonl`。
- 上下文中只保留绝对路径、recordId 和预览文本。
- 这件事通过 `ToolResultOffloadHook` 接在 `BEFORE_TOOL_RESULT_APPEND`。

### MCP

MCP 适配放在 `app/mcp/`。

支持方向：

- 本地 MCP Server。
- HTTP MCP。
- stdio MCP。
- 通过 JSON-RPC 协议适配为普通 Tool。

是否安装具体 MCP 由 `workspace/mcp.json` 决定。项目本身只提供适配框架，不内置第三方 MCP。
MCP 工具注册入口是 `McpToolExtension`，不要把 MCP 加载逻辑重新写回 `AgentRuntimeFactory`。

### Skill

Skill 是渐进式加载，不是真正运行时插件系统。

规则：

- `workspace/skills/*/SKILL.md` 会被扫描。
- 只把 Skill 的 name/description 注入请求前 `<system-reminder>`。
- 需要完整内容时，由 LLM 调用 `load_skill` 加载。
- 不要启动时把所有 Skill 全文塞进上下文。
- `load_skill` 由 `SkillToolExtension` 注册，不属于四个基础内置工具。

### Prompt

Prompt 放在 `src/main/resources/prompts/`，由 `PromptLoader` 读取。

包括：

- system prompt
- 上下文摘要 prompt
- 长期记忆提醒 prompt
- 长期记忆抽取 prompt

不要把长 prompt 硬编码进 Java 类。

### 长期记忆

长期记忆第一版用 Markdown 存储，并在每轮 LLM 请求前由 `SystemReminderExtension` 临时注入最后一条 user 消息开头的 `<system-reminder>` 块。
这个提醒块同时承载当前时间、Skill 索引、旧对话摘要和长期记忆，只进入本次模型请求，不写入 SessionStore。

只允许四类长期记忆：

- 用户画像
- 行为偏好
- 项目动态
- 外部指针

其他类型不要写入长期记忆。

每轮对话结束后，通过 `AFTER_RUN` Hook 提交后台任务，由后台单独跑一个记忆抽取 Agent。

### 后台任务

后台任务放在 `app/background/`，用于不阻塞主对话的异步动作。

规则：

- 后台任务完成后不要打断当前主回答。
- TUI 当前只更新底部状态栏。
- 任务记录写入 `workspace/tasks/*.jsonl`，保留审计痕迹。
- `BackgroundTaskScheduler` 按 `SCHEDULE_INTERVAL_SECONDS` 周期扫描任务清单和运行记录，默认 10 秒。
- `immediate` 创建后会触发一次即时扫描；`delay` 到 `createdAt + delaySeconds` 后执行一次；`interval` 按上次完成时间加 `intervalSeconds` 重复执行。
- runtime 启动时会确保存在一个 `todo_scan` interval 后台任务，用来扫描 `workspace/todos/todos.json`。
- 后续定时提醒、记忆抽取、索引构建等都应该作为后台任务 handler 扩展。

## UI 规则

当前 UI 包括 Lanterna TUI、JDK HttpServer Web Chat 和 Telegram IM。

目录：

- `ui/tui/`：终端界面。
- `ui/web/`：Web Chat、SSE 事件桥接和静态资源服务。
- `ui/im/telegram/`：Telegram Bot long polling 入口。

规则：

- TUI 只消费 `AgentEventEnvelope`，不要直接侵入 AgentLoop。
- Web 对话输入只通过 `AgentRuntime.submit/steer/stop`，session CRUD 通过 `SessionIndex` 管理元信息，通过 `JsonlSessionStore` 读取历史。
- Web 静态资源放在 `src/main/resources/web/`，不要引入前端构建链路。
- Telegram 只通过 `AgentRuntime.submit/stop/approve/deny` 输入，通过 `TelegramAgentEventHandler` 消费 `AgentEventEnvelope` 输出。
- Telegram 必须使用 `TELEGRAM_ALLOWED_CHAT_IDS` 白名单；chat 到 session 的当前映射保存在 `workspace/im/telegram-sessions.json`。
- 输出采用 Markdown-ish 渲染，支持标题、列表、表格、代码块、引用、分割线等基础格式。
- 终端渲染对 emoji 兼容性有限，默认不要依赖 emoji 表达语义。
- `/` 命令保持克制，新增命令前先确认是否真的必要。
- 斜杠命令放在 `ui/tui/command/`，实现 `SlashCommand` 并注册到 `SlashCommandRegistry`；不要继续在 `AgentTuiWindow` 里堆 if/switch。

## 代码风格

硬性规则：

- 新增注释用中文。
- 新增类要写类注释。
- 核心方法要写方法注释。
- 教学版代码要清楚，不要炫技。
- 新增 model 类放在所属包的 `model/` 子包下。
- 优先复用现有抽象，不随意新建平行体系。
- 不要保留旧逻辑和新逻辑两套并存，除非用户明确要求兼容。
- 不要新增非必要配置项。
- 不要引入新依赖，除非用户明确要求或现有技术栈无法完成。

修改文件时：

- 优先用 `rg` 搜索。
- 保持改动范围最小。
- 不要重排无关代码。
- 不要回滚用户已有改动。
- 修改架构后同步 README 或教程中对应结构说明。

## 测试要求

代码改动后至少运行：

```bash
mvn test
```

如果只改纯文档，可以不跑测试，但最终说明要写清楚“未运行测试，因为只修改文档”。

当前测试覆盖方向：

- AgentLoop
- 上下文压缩
- DeepSeek / OpenAI-compatible parser
- MCP client/server
- 内置工具
- Skill
- Session JSONL
- 后台任务
- 长期记忆
- Prompt
- TUI Markdown 渲染

## Workspace 数据

运行时数据默认放在项目根 `workspace/`：

```text
workspace/
├── mcp.json.example
├── sessions/                       # index.json + *.jsonl
├── im/                             # Telegram chat-session 映射
├── tasks/
├── skills/
├── artifacts/tool-results/
└── memory/
```

这些数据多数是本地运行产物，不要随意提交用户真实 session、任务记录、长期记忆或工具结果。

## 做新功能时的判断顺序

先判断新逻辑属于哪一层：

1. 模型供应商差异：放 `llm/`，输出统一 `ProviderStreamEvent`。
2. Agent 主流程必经步骤：放 `core/stage/`。
3. 主流程前后扩展：放 `core/hook/` 的 HookPoint，并通过 `app/extension/` 注册实现。
4. 外部可观察状态：发布 `AgentEvent`，由 UI/Web/日志消费。
5. 工具能力：四个底座工具放 `app/tool/builtin/`；其他工具通过 RuntimeExtension 注册到 `ToolRegistry`。
6. TUI 斜杠命令：放 `ui/tui/command/`，不要塞回 `AgentTuiWindow`。
7. Web 展示和 HTTP 输入：放 `ui/web/`，复用 `AgentRuntime` 和 `AgentEventHandler`。
8. IM 展示和输入：放 `ui/im/`，复用 `AgentRuntime` 和 `AgentEventHandler`。
9. 具体业务能力：放 `app/` 对应子包，并通过 RuntimeExtension 接入。
10. 展示变化：放 `ui/`，消费事件，不改 AgentLoop。

如果不确定，优先保持核心链路不变，把新逻辑做成 app 层能力，通过 Hook/Event/Tool 接入。
