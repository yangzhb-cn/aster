# Aster

Aster 是一个用于学习 Agent 架构的 Java 21 最小实现。

项目重点不是做完整产品，而是把 Agent 的核心链路拆清楚：流式 LLM、AgentLoop、上下文压缩、工具调用、MCP 适配、Skill 加载、Session 持久化、Hook 扩展、后台任务、长期记忆、TUI、Web 和 IM 展示。

## 技术栈

- Java 21
- Maven
- Jackson
- OkHttp
- Lanterna
- JUnit 5 + MockWebServer

## 分层依赖

```text
ui
 ↓
app/runtime
 ↓
core
 ↓
llm
```

规则很简单：

- `llm` 只负责模型 API 适配，不知道 AgentLoop、Tool、TUI。
- `core` 只放 Agent 主流程和抽象契约，不反向依赖 `app`。
- `app` 放具体能力实现，例如内置工具、MCP、Skill、长期记忆、后台任务。
- `ui` 只做表现层，通过事件消费 Agent 输出。

## 包结构

```text
src/main/java/com/aster/
├── llm/                    模型 API 适配层
│   ├── StreamingChatClient
│   ├── OpenAiCompatibleChatClient
│   ├── OpenAiCompatibleStreamParser
│   ├── DeepSeekProvider
│   └── model/              ChatRequest / Message / ToolCall / ProviderStreamEvent / TokenUsage
│
├── core/                   Agent 核心生命周期
│   ├── agent/              AgentLoop / AssistantMessageBuilder / run 控制信号
│   ├── event/              AgentEventBus / AgentEventHandler / AgentEventEnvelope
│   ├── hook/               HookPoint / HookHandler / HookRegistry / AgentHookPoints
│   ├── stage/              Stage / StagePipeline / 内置必经流程
│   ├── context/            ContextBuilder / ContextPipeline / ToolProtocolValidator
│   ├── session/            SessionStore / JsonlSessionStore / SessionReplayer / SessionIndex
│   └── tool/               ToolHandler / ToolExecutor / ToolRegistry / ParallelToolExecutor
│
├── app/                    具体能力实现和运行时装配
│   ├── runtime/            AgentRuntimeFactory / AgentRunCoordinator，把 core + app + llm 装配起来
│   ├── extension/          RuntimeExtension，把可选 Tool / Hook / EventHandler 注册进运行时
│   ├── tool/builtin/       read / write / bash / edit 四个固定底座工具
│   ├── tool/developer/     ls / glob / grep / subagent / web_fetch / web_search 扩展工具
│   ├── tool/result/        大工具结果 JSONL 外部卸载
│   ├── mcp/                MCP client/server/tool adapter
│   ├── skill/              Skill 扫描、索引、加载
│   ├── memory/             长期记忆抽取、Markdown 存储、提醒段落渲染
│   ├── background/         后台任务框架
│   ├── notification/       后台任务通知出口
│   └── prompt/             resources/prompts/*.md 加载
│
└── ui/
    ├── tui/                Lanterna 终端 UI，command/ 注册斜杠命令
    ├── web/                JDK HttpServer + SSE Web Chat
    └── im/telegram/        Telegram Bot long polling 入口
```

## 主流程

```text
TuiMain / WebMain / TelegramMain
  -> AgentRuntimeFactory
     -> BuiltinTools 注册 read/write/bash/edit
     -> RuntimeExtensionRegistry 注册 load_skill、开发者工具、MCP、system-reminder、长期记忆抽取、工具结果卸载、steer
  -> AgentRunCoordinator
     -> 空闲输入立即执行
     -> 运行中普通输入进入 follow-up 队列
     -> /steer 写入当前 run 控制信号
     -> /stop 请求当前 run 在安全点停止
  -> AgentLoop
     -> SessionStore 记录用户输入
     -> ContextPipeline 构造本轮 LLM 上下文
        -> LoadSessionMessagesStage 读取完整 session 历史
        -> ContextCompressionStage 压缩旧 turn 并校验工具协议
     -> HookRegistry.BEFORE_LLM_REQUEST 临时注入 Skill 索引 / 旧对话摘要 / 长期记忆 / steer 引导
     -> StreamingChatClient 发起 SSE 请求
     -> OpenAiCompatibleStreamParser 转成 ProviderStreamEvent
     -> AgentLoop 转成 AgentEvent
     -> TuiAgentEventHandler / WebAgentEventHandler 流式刷新界面
     -> TelegramAgentEventHandler 合并最终回答后发回 Telegram
     -> ParallelToolExecutor 并行执行多个 tool_call
     -> HookRegistry.BEFORE_TOOL_RESULT_APPEND 卸载大工具结果
     -> SessionStore 写回 assistant/tool 消息
     -> HookRegistry.AFTER_RUN 提交后台记忆抽取任务
```

## 扩展注册

新增能力优先通过注册进入现有系统，而不是修改 AgentLoop：

```text
固定底座工具：
  read / write / bash / edit

RuntimeExtension：
  SkillToolExtension      -> 注册 load_skill
  DeveloperToolExtension  -> 注册 ls/glob/grep/subagent/web_fetch/web_search
  McpToolExtension        -> 加载 workspace/mcp.json 并注册 MCP tools
  SteerExtension          -> 注册运行中引导 Hook
  SystemReminderExtension -> 注册请求前 <system-reminder> 注入 Hook
  MemoryExtension         -> 注册长期记忆抽取 Hook
  ToolResultExtension     -> 注册大工具结果卸载 Hook

SlashCommand：
  /exit
  /session ...
  /steer ...
  /stop
```

事件定义仍属于 `core/event`，扩展可以增加 `AgentEventHandler` 监听事件，但不替代核心事件协议。

## Event / Hook / Stage

### Event

Event 是“发生了什么”，用于 UI、Web SSE、日志、审计。

```text
ProviderStreamEvent
  -> AgentLoop
  -> AgentEvent
  -> AgentEventBus
  -> AgentEventEnvelope
  -> TUI / Web / Log
```

当前关键事件包括：

- `AssistantToken`：assistant 正文流式增量。
- `ReasoningToken`：DeepSeek `reasoning_content` 增量。
- `ToolCallStart` / `ToolCallDone`：工具调用状态。
- `UsageReported`：输入、缓存、输出 token 统计。
- `ContextBuilt`：上下文压缩前后 token 估算。
- `RunQueued` / `SteerReceived` / `StopRequested` / `RunStopped`：运行中控制状态通知。
- `Done`：一次用户请求结束。

### Hook

Hook 是“在某个主流程点插入扩展逻辑”，用于改写、阻断或追加动作。

| HookPoint | 当前用途 |
|---|---|
| `BEFORE_LLM_REQUEST` | 把 Skill 索引、旧对话摘要和长期记忆临时注入最后一条 user 消息开头的 `<system-reminder>` 块。 |
| `BEFORE_TOOL_CALL` | 预留给工具权限、高危工具审查、HITL。 |
| `BEFORE_TOOL_RESULT_APPEND` | 大工具结果卸载到 `workspace/artifacts/tool-results/*.jsonl`。 |
| `AFTER_RUN` | 每轮对话结束后提交长期记忆抽取后台任务。 |

### Stage

Stage 是 Agent 主流程必经步骤，不靠外部注册决定是否执行。

| Stage | 当前用途 |
|---|---|
| `LoadSessionMessagesStage` | 从 session 读取完整历史。 |
| `ContextCompressionStage` | 按 user turn 压缩旧对话，保留当前 turn 和它之前最近 3 个已完成 turn，并校验 tool_call/tool_result 协议。 |
| `ContextPipeline` | 串起上下文相关 Stage，产出本轮请求 LLM 的 `ContextBuildResult`。 |

## Workspace

运行时数据默认写到当前项目的 `workspace/`：

```text
workspace/
├── mcp.json.example
├── sessions/                         session index.json + *.jsonl
├── im/                               Telegram chat-session 映射
├── tasks/                            后台任务 JSONL
├── skills/                           本地 Skill 目录
├── artifacts/tool-results/           大工具结果 JSONL
└── memory/                           长期记忆 Markdown
```

Session 规则：

- `workspace/sessions/<sessionId>.jsonl` 保存完整原始事件历史。
- `workspace/sessions/index.json` 保存 `displayName`、创建/更新时间和 `archived` 状态。
- 新 sessionId 采用 `sess_yyyyMMdd_HHmmss_uid` 形式；displayName 只用于 UI 展示。
- 删除会话只把索引里的 `archived` 置为 `true`，不删除 JSONL 审计文件。

## 运行

默认使用 DeepSeek `deepseek-v4-flash`，走 OpenAI-compatible 协议。

```bash
export DEEPSEEK_API_KEY=你的 key
mvn -q exec:java
```

也可以直接使用项目根目录的启动脚本。脚本会自动读取 `.env`：

```bash
./aster2tui
```

启动 Web Chat：

```bash
export DEEPSEEK_API_KEY=你的 key
mvn -q -Dexec.mainClass=com.aster.ui.web.WebMain exec:java
```

或：

```bash
./aster2web
```

默认监听 `http://localhost:8080`。可以用 `ASTER_WEB_PORT` 和 `ASTER_SESSION` 覆盖端口和 session。
`aster2web` 脚本默认使用 `8081`，方便和本地调试页面保持一致。
Web 左侧提供会话新建、切换、重命名、归档；右侧只展示 token/context 状态。

启动 Telegram Bot：

```bash
export DEEPSEEK_API_KEY=你的 key
export TELEGRAM_BOT_TOKEN=你的 bot token
export TELEGRAM_ALLOWED_CHAT_IDS=你的chatId
mvn -q -Dexec.mainClass=com.aster.ui.im.telegram.TelegramMain exec:java
```

或：

```bash
./aster2im
```

如果变量写在 `.env`，可以直接使用上面的脚本；手动运行 Maven 时先执行 `set -a; . ./.env; set +a`。
`aster2im` 支持把 `.env` 里的 `OWNER_ID` 当作默认 `TELEGRAM_ALLOWED_CHAT_IDS` 使用。
Telegram 当前使用 long polling，不需要公网 webhook。`TELEGRAM_ALLOWED_CHAT_IDS` 必填，用逗号分隔多个 chatId。
每个 Telegram chat 会映射到一个 Aster session，当前映射保存在 `workspace/im/telegram-sessions.json`。

也可以用通用 OpenAI-compatible 配置覆盖：

```bash
export OPENAI_COMPATIBLE_PROVIDER=my-provider
export OPENAI_COMPATIBLE_BASE_URL=https://example.com
export OPENAI_COMPATIBLE_API_KEY=你的 key
export OPENAI_COMPATIBLE_MODEL=my-model
mvn -q exec:java
```

## 测试

```bash
mvn test
```

当前测试覆盖 AgentLoop、上下文压缩、DeepSeek/OpenAI-compatible parser、MCP、本地 MCP Server、内置工具、开发者扩展工具、Skill、Session、后台任务、长期记忆、Prompt 和 TUI Markdown 渲染。
