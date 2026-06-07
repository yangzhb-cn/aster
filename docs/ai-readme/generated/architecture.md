# 技术架构

<!-- AI生成，可根据团队规范更新 -->

## 架构总览

```mermaid
flowchart TD
    TUI["TUI\nLanterna"] --> Runtime["AgentRuntime"]
    Web["Web\nJDK HttpServer + SSE"] --> WebPool["WebSessionRuntimePool\nsessionId -> AgentRuntime"]
    WebPool --> Runtime
    Web --> Rag["RagChatService\nKnowledge SSE"]
    Telegram["Telegram\nLong polling"] --> Runtime

    Runtime --> Coordinator["AgentRunCoordinator\nfollow-up / steer / stop"]
    Runtime --> PlanMode["PlanModeCoordinator\n/plan /start /cancel"]
    Runtime --> Team["AgentTeamRunner\n/team"]
    Runtime --> Room["RoomCoordinator\nWeb Room / @Agent"]
    Runtime --> Background["BackgroundTaskManager"]
    Runtime --> Schedule["ScheduledUserMessageManager\nschedule -> user input"]
    Runtime --> Archive["Archive APIs\nrestore / physical delete"]
    Runtime --> Snapshot["ContextWindowSnapshotStore\nworkspace/context-windows"]

    Coordinator --> AgentLoop["AgentLoop"]
    PlanMode --> PlanRunner["PlanRunner"]
    Team --> PlanRunner
    PlanRunner --> ChildAgents["临时子 Agent"]
    Room --> RoomAgents["RoomAgentRunner\n独立私有 session"]
    Room --> RoomHub["RoomHub\n共享 hub messages"]
    Rag --> RagStore["RagStore\nworkspace/rag"]
    Rag --> RagRetriever["VectorRetriever\nOllama embedding"]

    Snapshot --> Context["ContextPipeline\nContextWindowCache + ContextBuilder"]
    AgentLoop --> Context
    AgentLoop --> Hooks["HookRegistry"]
    AgentLoop --> Tools["ToolRegistry + ParallelToolExecutor"]
    AgentLoop --> Session["SessionStore JSONL"]
    AgentLoop --> Events["AgentEventBus"]
    AgentLoop --> LLM["llm chat\nStreamingChatClient + SSE"]
    Rag --> LLM
    RagRetriever --> ModelCaps
    LLM --> ModelCaps["llm capabilities\nchat / embedding / speech / image"]
    Context --> Summary["LlmSummarizer\n无工具流式摘要"]
    Summary --> LLM
```

## 分层说明

| 分层 | 职责 | 主要类/文件 |
| --- | --- | --- |
| UI | 用户输入、事件渲染、命令分流 | `TuiMain`、`WebMain`、`WebSessionRuntimePool`、`TelegramMain`、`SlashCommandRegistry` |
| app/runtime | Runtime 创建、入口互斥、普通 run / Team / Plan / Room 调度 | `AgentRuntimeFactory`、`AgentRuntime`、`AgentRunCoordinator`、`PlanModeCoordinator` |
| app capabilities | 具体工具、MCP、Skill、Memory、HITL、Todo、Background、Schedule、Plan、Team、Room、RAG | `app/tool/*`、`app/mcp/*`、`app/memory/*`、`app/schedule/*`、`app/plan/*`、`app/team/*`、`app/room/*`、`app/rag/*` |
| core | AgentLoop、Context、Tool、Hook、Event、Session、Stage 抽象 | `AgentLoop`、`ContextPipeline`、`ToolRegistry`、`HookRegistry`、`AgentEventBus` |
| llm | 模型能力层；chat 走 OpenAI-compatible SSE，embedding/语音/图片按能力拆接口 | `StreamingChatClient`、`OpenAiCompatibleChatClient`、`OllamaProvider`、`EmbeddingClient`、`SpeechToTextClient`、`ImageUnderstandingClient` |
| resources | Prompt 和 Web 静态资源 | `src/main/resources/prompts/`、`src/main/resources/web/` |

## 技术栈

| 类目 | 技术 | 版本 | 用途 |
| --- | --- | --- | --- |
| 语言/运行时 | Java | 21 | 主开发语言 |
| 构建工具 | Maven | 由 `pom.xml` 定义 | 构建、测试、运行 exec main |
| JSON | Jackson Databind | 2.17.2 | 配置、Session、Tool 参数、Web API、MCP JSON-RPC |
| HTTP | OkHttp | 4.12.0 | LLM SSE、Web fetch/search、Telegram API |
| PDF | Apache PDFBox | 2.0.35 | RAG PDF 文本解析 |
| TUI | Lanterna | 3.1.2 | 终端界面 |
| Web | JDK HttpServer | JDK 内置 | Web Chat、REST API、SSE |
| 测试 | JUnit 5 | 5.10.3 | 单元测试 |
| 测试 | MockWebServer | 4.12.0 | HTTP/LLM/Telegram 测试 |

## 关键架构决策

| 决策 | 当前实现 | 原因 |
| --- | --- | --- |
| 只保留流式 LLM 主路径 | `StreamingChatClient` + SSE parser | TUI/Web/Telegram 都消费流式事件，避免维护流式和非流式两套路径 |
| 模型路由按生命周期拆分 | `AgentRuntime.chatModel()`、`TeamRunRequest`、`RoomAgentProfile.model`、Plan 固定模型 | Chat 按 session 切换；Team 单次运行快照；Room Agent 按配置；Plan planner=pro、worker=flash |
| 模型供应商与能力拆分 | `ModelProviderDefinition`、`ModelCapability`、`EmbeddingClient` | DeepSeek 负责 chat；Ollama 可作为本地 chat/embedding provider；后续语音和图片模型不挤进 AgentLoop |
| Context 压缩使用运行态窗口 | `ContextWindowCache` + `ContextPipeline` | 请求上下文只保留旧摘要和最近完整 turn，避免每轮请求全量 replay |
| Context 快照恢复压缩进度 | `JsonContextWindowSnapshotStore` + `ContextWindowSnapshotSessionStore` | 快照保存到 `workspace/context-windows/*.json`；启动时先读快照，校验版本、session、prompt hash、summarizer、last seq/hash，有效则只用 `loadMessageRecordsAfter(lastSeq)` 补齐新增消息 |
| Context 摘要优先使用 LLM | `LlmSummarizer` + `TranscriptSummarizer` | 压缩时走无工具、无 thinking 的流式 LLM 语义摘要；失败或空摘要时回退确定性转写摘要 |
| 可选能力走 Hook / Extension | `HookRegistry`、`RuntimeExtensionRegistry` | 避免 `AgentLoop` 堆业务 if-else |
| Tool 统一抽象 | `ToolRegistry`、`ToolHandler`、`ToolResult` | 本地工具、MCP 工具、扩展工具统一给 LLM 暴露；MCP 工具可见名加 `mcp_` 前缀，远端调用保留原名 |
| 高影响工具走 HITL | `ToolApprovalHook` 审批 `bash/write/edit` | 工具执行前可见、可拒绝，拒绝仍保持 tool_result 协议闭环 |
| Session 保存原始历史 | `JsonlSessionStore` | JSONL 是事实源；压缩摘要和快照只影响请求上下文，不污染可审计历史 |
| Web 普通 session 可并行运行 | `WebSessionRuntimePool` | 每个 session 保留独立 `AgentRuntime`，切换会话不关闭旧 runtime；SSE 事件按 `sessionName` 分流 |
| Web 能力状态左栏折叠展示 | `/api/status` + `mcpServers` / `skills` | Chat 左栏底部用 `MCP` / `SKILL` 按钮展开 MCP server 状态和 Skill 列表，避免右栏信息过载 |
| Web 空启动不创建默认数据 | `WebServer.initialSession` + 首次发送创建 session | 不自动生成 `default` session 或默认聊天室；无会话时展示引导，首条消息或点击 `+` 才创建 |
| RAG 不走工具调用 | `RagChatService` + `VectorRetriever` | Knowledge 页面后端先检索，再把片段注入 prompt；LLM 只负责流式回答，不开放工具，chat provider 独立于普通 Agent 工具链 |
| Background 与 Schedule 分离 | `BackgroundTaskManager` + `ScheduledUserMessageManager` | 后台任务处理系统维护和简单提醒；schedule 到点提交 user 消息，让 Agent 按普通链路执行 |
| Plan / Team 共用 DAG runner | `PlanRunner` | 复用依赖调度、并发执行、失败停止等逻辑 |
| Room 共享消息与私有上下文分离 | `RoomHub` + `RoomAgentSessionFactory` + `RoomContextInjectHook` | 后加入 Agent 能看到房间上下文，同时每个 Agent 保持独立历史 |
| 归档先软删再物理删除 | `SessionIndex`、`TodoStore`、`RoomStore`、`RoomAgentRegistry` | 普通删除保留审计；物理删除只允许已归档对象 |

## 事件流架构

```mermaid
sequenceDiagram
    participant Provider as LLM Provider
    participant Parser as OpenAiCompatibleStreamParser
    participant Loop as AgentLoop
    participant Bus as AgentEventBus
    participant UI as TUI/Web/Telegram

    Provider-->>Parser: SSE delta
    Parser-->>Loop: ProviderStreamEvent
    Loop-->>Bus: AgentEvent
    Bus-->>UI: AgentEventEnvelope
    UI-->>UI: 按入口渲染或聚合
```

## Tool 架构

```mermaid
flowchart LR
    LLM["LLM tool_calls"] --> Loop["AgentLoop"]
    Loop --> Approval["BEFORE_TOOL_CALL\nHITL"]
    Approval --> Executor["ParallelToolExecutor"]
    Executor --> Registry["ToolRegistry"]
    Registry --> Local["LocalToolExecutor\nbuiltin/developer/todo/background/schedule"]
    Registry --> MCP["McpToolExecutor\nlocal/http/stdio MCP"]
    Local --> Result["ToolResult"]
    MCP --> Result
    Result --> Offload["BEFORE_TOOL_RESULT_APPEND\nToolResultOffloadHook"]
    Offload --> Session["SessionStore role=tool"]
```

MCP 工具有两层名称：给 LLM 看的 `Tool.name` 使用 `mcp_` 前缀避免和本地工具冲突；真正发给 MCP server 的 `tools/call.name` 使用 `Tool.remoteName`。MCP server 加载状态由 `McpToolExecutor` 记录，Web 只展示 server 级 loaded/failed 状态，不逐个展示工具清单。

## Plan / Team 架构

```mermaid
flowchart TD
    PlanCmd["/plan <task>"] --> Planner["PlanPlannerAgent\npro 生成 JSON DAG"]
    Planner --> Validate["Java 校验 id/type/deps/cycle"]
    Validate --> Pending["PlanModeCoordinator pendingPlan"]
    Pending --> Start["/start"]
    Start --> Runner["PlanRunner"]
    Runner --> PlanTask["PlanTaskExecutor\nflash 执行节点\n复用主 ToolRegistry + HookRegistry"]
    PlanTask --> MainAgent["结果交回主 Agent 整理"]

    TeamCmd["/team [--model m] <task>"] --> Factory["TeamPlanFactory\n固定 DAG"]
    Factory --> TeamRunner["AgentTeamRunner + PlanRunner"]
    TeamRunner --> TeamAgent["TeamAgentFactory\n单次快照模型\n只读工具 + 空 HookRegistry"]
    TeamAgent --> MainAgent
```

## Room 架构

```mermaid
flowchart TD
    WebRoom["Web Room 视图"] --> WebApi["WebServer\n/api/rooms /api/room-agents"]
    WebApi --> RuntimeRoom["AgentRuntime Room methods"]
    RuntimeRoom --> Store["RoomStore / RoomAgentRegistry"]
    RuntimeRoom --> Members["RoomMembershipStore\nmembers.json / generation"]
    RuntimeRoom --> Hub["RoomHub\n共享 JSONL 消息"]
    RuntimeRoom --> Coordinator["RoomCoordinator\n写 user hub message + 按成员解析 @"]
    Coordinator --> Runner["RoomAgentRunner"]
    Coordinator --> Parallel["@all 并行执行\n按 replyIndex 写回"]
    Runner --> PrivateSession["RoomAgentSessionFactory\n每个 room-agent-generation 独立 JSONL"]
    Runner --> Hook["RoomContextInjectHook\n注入最近 hub messages"]
    Runner --> Tools["RoomToolRegistryFactory\nread/ls/glob/grep/web_fetch/web_search/load_skill"]
    Runner --> Loop["AgentLoop\n读取 RoomAgentProfile.model\n+ noop event bus"]
    Loop --> HubReply["Agent 最终回复写回 RoomHub"]
```

## Archive 架构

```mermaid
flowchart LR
    WebArchive["Web Archive 视图"] --> ArchiveApi["/api/archives\nrestore / delete / delete-batch"]
    ArchiveApi --> Sessions["SessionIndex\nrestore/deletePermanently"]
    ArchiveApi --> Todos["TodoStore\nrestore/deletePermanently"]
    ArchiveApi --> Rooms["RoomStore + RoomHub + RoomAgentSessionCleaner"]
    ArchiveApi --> Agents["RoomAgentRegistry + RoomAgentPromptStore + RoomAgentSessionCleaner"]
    ArchiveApi --> RagSessions["RAG SessionIndex\nworkspace/rag/sessions"]
```

## 当前边界

- Aster 是教学版 MVP，不是生产级多租户 Agent 平台。
- Web 前端当前使用静态资源和原生 JS，没有前端构建链路。
- Web 普通 Chat 支持多个 session runtime 并行；运行中 session 不能归档或物理删除，直到它空闲。
- Web Room 当前是同步 HTTP 回复，不是 token 流式聊天室；Room Agent 事件总线使用 noop，页面只展示最终回复。
- Web Knowledge RAG 是 SSE 流式问答；第一版只有 PDF/文本/Markdown 上传、固定滑动分块、Ollama embedding、纯向量召回和来源展示，还没有 BM25、rerank、文档删除或多路召回。
- Room 当前只在 Web 入口实现；TUI 和 Telegram 没有 Room 页面或 Agent CRUD。
- Room `@all` 只触发当前聊天室成员。Agent 并行执行，回复按成员顺序写回，避免完成时间影响消息顺序。
- Archive Center 当前只在 Web 入口实现，集中处理已归档 session、todo、room、room-agent，并支持批量物理删除。
- 长期记忆当前是 Markdown 存储，不是向量检索系统。
- 后台任务当前只支持明确 handler，例如 `reminder`、`todo_scan`、`memory_extract`；需要 Agent 到点自动执行的任务使用 `schedule` 提交 user 消息。
- `schedule` 是每个 `AgentRuntime` 绑定当前 session 的自动化用户消息调度器，应用不运行时不会主动执行；Web 右栏面板和 Agent `schedule` 工具共用同一套 manager/store。
- Team 当前是只读探索；会修改文件的是普通 Agent 或 Plan 子 Agent，并且高影响工具需要 HITL。
