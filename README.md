# agent-small-mvp

一个用于学习 Agent 架构的 Java 21 最小实现。

当前项目重点不是做完整产品，而是把 Agent 的核心链路拆清楚：

- 流式 LLM 调用
- Agent 主循环
- 上下文压缩
- 工具调用与并行执行
- MCP 工具适配
- JSONL session 持久化
- Skill 渐进式加载
- Hook 流程扩展
- 后台任务与长期记忆抽取
- TUI 事件展示

## 技术栈

- Java 21
- Maven
- Jackson
- OkHttp
- Lanterna
- JUnit 5 + MockWebServer

## 当前架构

```text
TuiMain
    ↓
AgentTuiWindow
    ↓
AgentRuntimeFactory
    ↓
AgentRuntime
    ├── AgentLoop
    │   ├── SessionStore
    │   │       └── JsonlSessionStore
    │   ├── ContextBuilder
    │   │       └── 按 user turn 压缩上下文，避免切断 tool_call / tool_result
    │   ├── StreamingChatClient
    │   │       ↓
    │   │   OpenAiCompatibleStreamParser
    │   │       ↓
    │   │   ProviderStreamEvent
    │   ├── ParallelToolExecutor
    │   │       ↓
    │   │   ToolRegistry
    │   │       ├── Builtin Tools: read / write / bash / edit / load_skill
    │   │       └── McpToolExecutor
    │   │               ↓
    │   │            McpClient
    │   ├── HookRegistry
    │   │       ├── BEFORE_LLM_REQUEST          注入长期记忆 / 后续可过滤工具
    │   │       ├── BEFORE_TOOL_CALL            工具权限 / 后续 HITL 审批
    │   │       ├── BEFORE_TOOL_RESULT_APPEND   大工具结果卸载到 JSONL
    │   │       └── AFTER_RUN                   提交长期记忆抽取后台任务
    │   └── AgentEventBus
    │           ↓
    │       AgentEventEnvelope
    │           ↓
    │       AgentEventHandler
    │           ├── TuiAgentEventHandler
    │           ├── Web SSE handler    后续可接
    │           └── Log handler        后续可接
    └── BackgroundTaskManager
            ├── BackgroundTaskStore
            │       ├── workspace/tasks/tasks.jsonl
            │       └── workspace/tasks/runs.jsonl
            ├── BackgroundTaskScheduler
            ├── BackgroundTaskExecutor
            │       └── BackgroundTaskHandler
            │               └── NoopTaskHandler
            └── BackgroundTaskEventBus
                    ↓
                NotificationSink
                    ↓
                TUI status line
```

## 事件分层

项目里有两层事件，职责不同：

```text
OpenAI-compatible providers
    ↓ OpenAiCompatibleStreamParser
ProviderStreamEvent
    ↓ AgentLoop 转换
AgentEvent
    ↓ AgentEventBus 包装 metadata
AgentEventEnvelope
    ↓
TUI / Web / Log
```

Anthropic、Google 这类非 OpenAI-compatible 供应商后续可以新增各自 Parser，但输出仍然是同一套 `ProviderStreamEvent`。

### ProviderStreamEvent

LLM 接入层事件。它屏蔽不同供应商的原始 SSE 格式。

当前 OpenAI-compatible parser 会把原始 `choices/delta` 转成：

- `TextDelta`
- `ReasoningDelta`
- `ToolCallDeltaPart`
- `UsageDelta`
- `Done`

### AgentEventEnvelope

Agent 对外事件。`AgentLoop` 只发布语义事件，`AgentEventBus` 统一补 metadata：

```text
eventId
runId
sessionName
sequence
timestamp
```

这样 TUI、Web、日志都可以消费同一套事件流。

Hook 不走事件消费链。Hook 是流程插槽，注册在 `HookRegistry`，用于改写、阻断或追加主流程动作。

## 模块职责

```text
agent/      AgentLoop、AgentEvent、AgentEventBus
hook/       HookPoint、HookRegistry、HookHandler、AgentHookPoints
llm/        OpenAI-compatible 流式客户端和 ProviderStreamEvent
context/    上下文构造、turn 切分、摘要压缩、工具协议校验
tool/       统一工具层、内置工具、并行工具执行、工具结果卸载
mcp/        MCP JSON-RPC client、本地 MCP server、stdio/http transport
session/    JSONL session 持久化、恢复、分支基础结构
skill/      Skill 索引扫描和 load_skill 渐进式加载
tui/        Lanterna TUI、Markdown 渲染、工具折叠、usage 展示
background/ 后台任务定义、调度、执行、运行记录
notification/ 后台通知出口，TUI 当前只更新底部状态栏
runtime/    启动装配，把 prompt、tool、mcp、skill、session、llm 串起来
```

## 运行

默认使用 DeepSeek `deepseek-v4-flash`，走 OpenAI-compatible 协议。

```bash
export DEEPSEEK_API_KEY=你的 key
mvn -q exec:java
```

也可以用通用 OpenAI-compatible 配置覆盖：

```bash
export OPENAI_COMPATIBLE_PROVIDER=my-provider
export OPENAI_COMPATIBLE_BASE_URL=https://example.com
export OPENAI_COMPATIBLE_API_KEY=你的 key
export OPENAI_COMPATIBLE_MODEL=my-model
mvn -q exec:java
```

## Workspace

运行时数据默认放在当前项目的 `workspace/` 下：

```text
workspace/
├── mcp.json.example
├── sessions/
├── tasks/
│   ├── tasks.jsonl
│   └── runs.jsonl
├── skills/
├── artifacts/
│   └── tool-results/
└── memory/
```

说明：

- `workspace/sessions/*.jsonl` 保存 session 历史。
- `workspace/tasks/tasks.jsonl` 保存后台任务定义。
- `workspace/tasks/runs.jsonl` 保存后台任务执行记录。
- `workspace/skills/*/SKILL.md` 会被扫描成 Skill 索引。
- `workspace/mcp.json` 可配置本地或 HTTP MCP Server。
- 大工具结果会卸载到 `workspace/artifacts/tool-results/*.jsonl`。

## 测试

```bash
mvn test
```

当前测试覆盖 AgentLoop、上下文压缩、LLM provider/parser、MCP、内置工具、Skill、Session、后台任务、Prompt 和 TUI Markdown 渲染。

## 当前边界

这是教学版 MVP，目前没有实现：

- 浏览器工具
- web_fetch
- 真实 tokenizer
- 完整 MCP 生命周期和鉴权
- 长期记忆
- Web UI
- 后台任务具体业务 handler
