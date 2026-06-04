# 技术架构

<!-- AI生成，可根据团队规范更新 -->

## 架构总览

```mermaid
flowchart TD
    TUI["TUI\nLanterna"] --> Runtime["AgentRuntime"]
    Web["Web\nJDK HttpServer + SSE"] --> Runtime
    Telegram["Telegram\nLong polling"] --> Runtime

    Runtime --> Coordinator["AgentRunCoordinator\nfollow-up / steer / stop"]
    Runtime --> PlanMode["PlanModeCoordinator\n/plan /start /cancel"]
    Runtime --> Team["AgentTeamRunner\n/team"]
    Runtime --> Background["BackgroundTaskManager"]

    Coordinator --> AgentLoop["AgentLoop"]
    PlanMode --> PlanRunner["PlanRunner"]
    Team --> PlanRunner
    PlanRunner --> ChildAgents["临时子 Agent"]

    AgentLoop --> Context["ContextPipeline\nStage + ContextBuilder"]
    AgentLoop --> Hooks["HookRegistry"]
    AgentLoop --> Tools["ToolRegistry + ParallelToolExecutor"]
    AgentLoop --> Session["SessionStore JSONL"]
    AgentLoop --> Events["AgentEventBus"]
    AgentLoop --> LLM["StreamingChatClient\nOpenAI-compatible SSE"]
```

## 分层说明

| 分层 | 职责 | 主要类/文件 |
| --- | --- | --- |
| UI | 用户输入、事件渲染、命令分流 | `TuiMain`、`WebMain`、`TelegramMain`、`SlashCommandRegistry` |
| app/runtime | Runtime 创建、入口互斥、普通 run / Team / Plan 调度 | `AgentRuntimeFactory`、`AgentRuntime`、`AgentRunCoordinator`、`PlanModeCoordinator` |
| app capabilities | 具体工具、MCP、Skill、Memory、HITL、Todo、Background、Plan、Team | `app/tool/*`、`app/mcp/*`、`app/memory/*`、`app/plan/*`、`app/team/*` |
| core | AgentLoop、Context、Tool、Hook、Event、Session、Stage 抽象 | `AgentLoop`、`ContextPipeline`、`ToolRegistry`、`HookRegistry`、`AgentEventBus` |
| llm | OpenAI-compatible provider 配置、请求、SSE 解析 | `OpenAiCompatibleChatClient`、`OpenAiCompatibleStreamParser`、`ProviderStreamEvent` |
| resources | Prompt 和 Web 静态资源 | `src/main/resources/prompts/`、`src/main/resources/web/` |

## 技术栈

| 类目 | 技术 | 版本 | 用途 |
| --- | --- | --- | --- |
| 语言/运行时 | Java | 21 | 主开发语言 |
| 构建工具 | Maven | 由 `pom.xml` 定义 | 构建、测试、运行 exec main |
| JSON | Jackson Databind | 2.17.2 | 配置、Session、Tool 参数、Web API、MCP JSON-RPC |
| HTTP | OkHttp | 4.12.0 | LLM SSE、Web fetch/search、Telegram API |
| TUI | Lanterna | 3.1.2 | 终端界面 |
| Web | JDK HttpServer | JDK 内置 | Web Chat、REST API、SSE |
| 测试 | JUnit 5 | 5.10.3 | 单元测试 |
| 测试 | MockWebServer | 4.12.0 | HTTP/LLM/Telegram 测试 |

## 关键架构决策

| 决策 | 当前实现 | 原因 |
| --- | --- | --- |
| 只保留流式 LLM 主路径 | `StreamingChatClient` + SSE parser | TUI/Web/Telegram 都消费流式事件，避免维护流式和非流式两套路径 |
| Context 压缩使用 Stage | `ContextCompressionStage` | 压缩是请求前必经安全步骤，不应依赖可选 Hook |
| 可选能力走 Hook / Extension | `HookRegistry`、`RuntimeExtensionRegistry` | 避免 `AgentLoop` 堆业务 if-else |
| Tool 统一抽象 | `ToolRegistry`、`ToolHandler`、`ToolResult` | 本地工具、MCP 工具、扩展工具统一给 LLM 暴露 |
| 高影响工具走 HITL | `ToolApprovalHook` 审批 `bash/write/edit` | 工具执行前可见、可拒绝，拒绝仍保持 tool_result 协议闭环 |
| Session 保存原始历史 | `JsonlSessionStore` | 压缩只影响请求，不污染可审计历史 |
| Plan / Team 共用 DAG runner | `PlanRunner` | 复用依赖调度、并发执行、失败停止等逻辑 |

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
    Registry --> Local["LocalToolExecutor\nbuiltin/developer/todo/background"]
    Registry --> MCP["McpToolExecutor\nlocal/http/stdio MCP"]
    Local --> Result["ToolResult"]
    MCP --> Result
    Result --> Offload["BEFORE_TOOL_RESULT_APPEND\nToolResultOffloadHook"]
    Offload --> Session["SessionStore role=tool"]
```

## Plan / Team 架构

```mermaid
flowchart TD
    PlanCmd["/plan <task>"] --> Planner["PlanPlannerAgent\nLLM 生成 JSON DAG"]
    Planner --> Validate["Java 校验 id/type/deps/cycle"]
    Validate --> Pending["PlanModeCoordinator pendingPlan"]
    Pending --> Start["/start"]
    Start --> Runner["PlanRunner"]
    Runner --> PlanTask["PlanTaskExecutor\n复用主 ToolRegistry + HookRegistry"]
    PlanTask --> MainAgent["结果交回主 Agent 整理"]

    TeamCmd["/team <task>"] --> Factory["TeamPlanFactory\n固定 DAG"]
    Factory --> TeamRunner["AgentTeamRunner + PlanRunner"]
    TeamRunner --> TeamAgent["TeamAgentFactory\n只读工具 + 空 HookRegistry"]
    TeamAgent --> MainAgent
```

## 当前边界

- Aster 是教学版 MVP，不是生产级多租户 Agent 平台。
- Web 前端当前使用静态资源和原生 JS，没有前端构建链路。
- 长期记忆当前是 Markdown 存储，不是向量检索系统。
- 后台任务当前只支持明确 handler，例如 `reminder`、`todo_scan`、`memory_extract`，不支持任意到点自动执行 Agent。
- Team 当前是只读探索；会修改文件的是普通 Agent 或 Plan 子 Agent，并且高影响工具需要 HITL。
