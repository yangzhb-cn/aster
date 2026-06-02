# agent-small-mvp

一个最小 Agent MVP，用 Java 21 + Jackson + OkHttp + Lanterna 实现这些能力：

1. `AgentLoop`：驱动 LLM、工具调用、工具结果回灌。
2. `ContextBuilder`：按 user turn 做上下文压缩，避免切断 `tool_call` / `tool_result` 协议。
3. `MCP`：把 MCP 工具适配进统一 Tool 层，复用原有 `ToolRegistry.execute()`。
4. `TUI`：用 Lanterna 提供文本界面入口，替代原来的 CLI。

在这个基础上，还补了四个教学版内置工具：`read`、`write`、`bash`、`edit`。

## 技术栈

- Java 21
- Maven
- Jackson：JSON 序列化和反序列化
- OkHttp：LLM HTTP 请求和 MCP JSON-RPC 请求
- Lanterna：终端 TUI 界面
- JUnit 5 + MockWebServer：最小链路测试

## 文件结构

```text
.
├── pom.xml
├── README.md
└── src
    ├── main/java/dev/agentmvp
    │   ├── agent
    │   │   ├── AgentEventHandler.java
    │   │   ├── AgentLoop.java
    │   │   ├── AssistantMessageBuilder.java
    │   │   └── model
    │   │       └── AgentEvent.java
    │   ├── context
    │   │   ├── ContextBuilder.java
    │   │   ├── SimpleTokenEstimator.java
    │   │   ├── Summarizer.java
    │   │   ├── TokenEstimator.java
    │   │   ├── ToolProtocolValidator.java
    │   │   ├── TranscriptSummarizer.java
    │   │   └── model
    │   │       ├── ContextBuildResult.java
    │   │       ├── ContextOptions.java
    │   │       ├── Turn.java
    │   │       └── TurnType.java
    │   ├── llm
    │   │   ├── DeepSeekModels.java
    │   │   ├── DeepSeekProvider.java
    │   │   ├── OkHttpStreamingChatClient.java
    │   │   ├── OpenAiCompatibleChatClient.java
    │   │   ├── OpenAiCompatibleProviderDefinition.java
    │   │   ├── OpenAiCompatibleProviderFactory.java
    │   │   ├── StreamingChatClient.java
    │   │   └── model
    │   │       ├── ChatRequest.java
    │   │       ├── ChatStreamChunk.java
    │   │       ├── Message.java
    │   │       ├── OpenAiCompatibleProvider.java
    │   │       ├── ToolCall.java
    │   │       └── ToolCallDelta.java
    │   ├── mcp
    │   │   ├── McpClient.java
    │   │   ├── McpToolAdapter.java
    │   │   ├── McpToolExecutor.java
    │   │   ├── McpToolLoader.java
    │   │   ├── model
    │   │   │   ├── JsonRpcError.java
    │   │   │   ├── JsonRpcProtocol.java
    │   │   │   ├── JsonRpcRequest.java
    │   │   │   └── JsonRpcResponse.java
    │   │   └── server
    │   │       ├── LocalMcpHttpServer.java
    │   │       ├── LocalMcpServer.java
    │   │       ├── LocalMcpToolHandler.java
    │   │       ├── LocalMcpToolRegistry.java
    │   │       └── model
    │   │           ├── ContentBlock.java
    │   │           ├── LocalMcpTool.java
    │   │           ├── McpToolCallParams.java
    │   │           └── McpToolCallResult.java
    │   ├── session
    │   │   ├── InMemorySessionStore.java
    │   │   └── SessionStore.java
    │   ├── skill
    │   │   ├── SkillIndexRenderer.java
    │   │   ├── SkillRepository.java
    │   │   └── model
    │   │       ├── SkillDocument.java
    │   │       └── SkillMetadata.java
    │   ├── prompt
    │   │   ├── PromptLoader.java
    │   │   └── PromptPaths.java
    │   ├── runtime
    │   │   ├── AgentRuntime.java
    │   │   └── AgentRuntimeFactory.java
    │   ├── tui
    │   │   ├── AgentTuiWindow.java
    │   │   ├── TuiAgentEventHandler.java
    │   │   ├── TuiMain.java
    │   │   ├── model
    │   │   │   ├── AssistantBlock.java
    │   │   │   ├── ErrorBlock.java
    │   │   │   ├── ReasoningBlock.java
    │   │   │   ├── SystemBlock.java
    │   │   │   ├── ToolBlock.java
    │   │   │   ├── ToolStatus.java
    │   │   │   ├── UiBlock.java
    │   │   │   └── UserBlock.java
    │   │   └── render
    │   │       ├── MarkdownLine.java
    │   │       ├── MarkdownLineType.java
    │   │       └── MarkdownRenderer.java
    │   └── tool
    │       ├── LocalToolExecutor.java
    │       ├── ParallelToolExecutor.java
    │       ├── ToolExecutor.java
    │       ├── ToolHandler.java
    │       ├── ToolRegistry.java
    │       ├── builtin
    │       │   ├── AbstractBuiltinTool.java
    │       │   ├── BashTool.java
    │       │   ├── BuiltinTool.java
    │       │   ├── BuiltinTools.java
    │       │   ├── EditTool.java
    │       │   ├── LoadSkillTool.java
    │       │   ├── ReadTool.java
    │       │   ├── WriteTool.java
    │       │   └── model
    │       │       ├── BashToolParams.java
    │       │       ├── EditToolParams.java
    │       │       ├── LoadSkillToolParams.java
    │       │       ├── ReadToolParams.java
    │       │       ├── ToolParamReader.java
    │       │       └── WriteToolParams.java
    │       └── model
    │           ├── Tool.java
    │           ├── ToolContent.java
    │           ├── ToolResult.java
    │           └── ToolSource.java
    ├── main/resources
    │   └── prompts
    │       ├── context-summary.md
    │       └── system.md
    └── test/java/dev/agentmvp
        ├── AgentLoopTest.java
        ├── BuiltinToolsTest.java
        ├── ContextBuilderTest.java
        ├── DeepSeekProviderTest.java
        ├── LocalMcpServerTest.java
        ├── McpClientTest.java
        ├── PromptLoaderTest.java
        ├── TranscriptSummarizerTest.java
        └── SkillRepositoryTest.java
```

## 架构

```text
TuiMain
    ↓
AgentTuiWindow
    ↓
AgentRuntimeFactory
    ↓
SessionStore
    ↓
ContextBuilder
    ↓
StreamingChatClient
    ↓
AgentLoop
    ↓
ParallelToolExecutor
    ↓
ToolRegistry
    ├── LocalToolExecutor
    └── McpToolExecutor
            ↓
         McpClient
            ↓
     LocalMcpHttpServer
            ↓
      LocalMcpServer
```

### 1. AgentLoop

`AgentLoop` 是主循环，负责：

- 接收用户输入并写入 `SessionStore`
- 调用 `ContextBuilder` 构造本轮要发给 LLM 的上下文
- 调用 `StreamingChatClient`，用 SSE 接收 `delta`
- 把 `delta.content` 作为 `AssistantToken` 发给 TUI
- 把 DeepSeek `delta.reasoning_content` 作为 `ReasoningToken` 发给 TUI
- 用 `AssistantMessageBuilder` 把流式 delta 组装成完整 assistant message，并保存 `reasoning_content`
- 如果 assistant 没有 `tool_calls`，结束本轮
- 如果 assistant 返回多个 `tool_calls`，用 `ParallelToolExecutor` 并行执行
- 把每个工具结果写成对应 `tool_call_id` 的 `role=tool` 消息
- 继续下一轮 LLM 请求，直到得到最终回答

代码入口：

```text
src/main/java/dev/agentmvp/agent/AgentLoop.java
```

### 1.1 TUI 入口

当前项目只保留 Lanterna TUI 入口，不再保留 CLI 入口。

TUI 入口负责：

- 创建 Lanterna `Screen`
- 创建手绘的 `AgentTuiWindow`
- 渲染最小终端界面：标题、结构化消息块、状态栏、输入线
- 通过 `AgentRuntimeFactory` 创建 Agent 运行时
- 把 `AgentEvent` 渲染成 `UiBlock`
- assistant 正文支持轻量 Markdown 渲染：标题、引用、分隔线、代码块、表格、编号列表、`-` 列表转 bullet、行内 code、粗体标记清理
- supplementary-plane emoji 会在显示层转成终端兼容 fallback，避免 Lanterna 把它显示成 `??`
- 工具输出过长时默认折叠，可以用 `ctrl+o` 展开/折叠
- 用户按 Enter 后，先回显用户输入，再在后台线程调用 `AgentRuntime.run()`
- 当前只支持一个斜杠命令：`/exit`
- 历史消息支持应用内滚动：`↑/↓` 滚一行，`PageUp/PageDown` 滚一页，`End` 回到底部

相关文件：

```text
src/main/java/dev/agentmvp/tui/TuiMain.java
src/main/java/dev/agentmvp/tui/AgentTuiWindow.java
src/main/java/dev/agentmvp/tui/TuiAgentEventHandler.java
src/main/java/dev/agentmvp/tui/model/UiBlock.java
src/main/java/dev/agentmvp/tui/model/ToolBlock.java
src/main/java/dev/agentmvp/tui/model/ReasoningBlock.java
src/main/java/dev/agentmvp/tui/render/MarkdownRenderer.java
src/main/java/dev/agentmvp/runtime/AgentRuntimeFactory.java
src/main/java/dev/agentmvp/runtime/AgentRuntime.java
```

### 2. SessionStore

`SessionStore` 保存完整原始历史，不做有损压缩。

当前 MVP 只有内存实现：

```text
src/main/java/dev/agentmvp/session/InMemorySessionStore.java
```

这层的职责是保存事实来源。真正发给 LLM 的上下文由 `ContextBuilder` 重新构造。

### 3. ContextBuilder + 压缩

`ContextBuilder` 负责把完整 session 转成安全、可发送的 message 列表。

处理流程：

```text
完整 session messages
    ↓
按 user turn 切分
    ↓
估算 token
    ↓
超过阈值则压缩旧 turn
    ↓
摘要重建成干净 user message
    ↓
最近 N 个 turn 原样保留
    ↓
ToolProtocolValidator 校验
```

关键点：

- 不按 token 或数组下标硬切。
- 一个 user turn 包含后续 assistant、assistant tool_calls、tool results。
- 旧 turn 被压缩后不再保留原始 message 结构。
- 摘要 message 必须重新创建，不能从旧 assistant message 复制，避免残留 `tool_calls`。
- 最终发送前必须校验工具调用协议。

相关文件：

```text
src/main/java/dev/agentmvp/context/ContextBuilder.java
src/main/java/dev/agentmvp/context/ToolProtocolValidator.java
```

### 4. Tool 层

Tool 层是 Agent 的统一工具抽象。

核心模型：

```text
Tool
ToolCall
ToolResult
ToolContent
ToolExecutor
ToolRegistry
```

`ToolRegistry` 只关心：

```text
工具叫什么
工具 schema 是什么
LLM 调用了哪个工具
工具返回什么结果
```

它不关心工具来自本地代码还是 MCP Server。

### 5. 内置工具

教学版内置了四个基础工具。如果项目里存在 `skills` 目录，还会额外注册一个
Skill 适配工具 `load_skill`：

```text
read  - 读取文件内容，文本支持分页，图片返回 data URL
write - 创建或覆盖文件，并自动创建父目录
bash  - 执行 bash 命令，输出只保留最后 2000 行 / 50KB
edit  - 通过精确文本替换编辑文件，支持多个不重叠区域
load_skill - 按 Skill name 读取完整 SKILL.md
```

实现方式按“每个工具一个类”拆开：

```text
BuiltinTool
    ↑
AbstractBuiltinTool
    ├── ReadTool
    ├── WriteTool
    ├── BashTool
    ├── EditTool
    └── LoadSkillTool
```

注册流程：

```text
AgentRuntimeFactory
    ↓
BuiltinTools.registerAll()
    ↓
BuiltinTool.registerTo()
    ↓
ToolRegistry.registerLocal()
    ├── ToolRegistry.register()      暴露工具定义给 LLM
    └── LocalToolExecutor.register() 登记 Java 处理函数
```

这样做的原因是：以后加新工具时，只需要新增一个 `BuiltinTool` 实现类，
再放进 `BuiltinTools.defaultTools()`，不需要把所有工具逻辑塞进一个大类。

路径类工具不做工作目录限制：相对路径按传入的工作目录解析，绝对路径原样使用。

### 6. Skill 适配层

这里做的是 Skill 适配，不是完整 Skill 系统。

Skill 按本地目录规范组织：

```text
skills
└── web-access
    ├── SKILL.md
    ├── references
    │   └── zhihu.md
    └── scripts
        └── extract.js
```

`SKILL.md` 顶部必须有 `name` 和 `description`：

```markdown
---
name: web-access
description: 网页访问经验。用于判断什么时候用 web_fetch，什么时候切换浏览器工具。
---

# web-access

这里写完整 Skill 说明。
```

启动时只扫描轻量索引：

```text
SkillRepository.scan("skills")
    ↓
读取每个 SKILL.md 的 name / description
    ↓
SkillIndexRenderer.render()
    ↓
作为 system message 写入 SessionStore
```

注入 system prompt 的只是索引和规则：

```text
当前可用 Skills：

- web-access：网页访问经验。用于判断什么时候用 web_fetch，什么时候切换浏览器工具。

Skill 使用规则：

- 如果你认为某个 Skill 对当前任务有帮助，先调用 load_skill(name) 读取完整 SKILL.md。
- 不要凭空猜测 Skill 的完整内容。
- references 和 scripts 不会自动加载，需要时再用 read 或 bash 工具访问。
```

完整加载流程：

```text
LLM 看到 Skill 索引
    ↓
判断某个 Skill 有用
    ↓
调用 load_skill("web-access")
    ↓
LoadSkillTool
    ↓
SkillRepository.load(name)
    ↓
返回完整 SKILL.md
    ↓
LLM 再按 SKILL.md 说明决定是否 read references 或 bash scripts
```

这个适配层只解决渐进式加载：

```text
Skill 索引常驻上下文
SKILL.md 按需加载
references 用 read 按需读取
scripts 用 bash 按需执行
```

它没有实现安装、版本、权限、生命周期、自动引用解析，也不会自动执行脚本。

### 7. MCP 适配

MCP 不单独重建一套工具层，只作为 Tool 层的适配器。

流程：

```text
MCP tools/list
    ↓
McpToolAdapter
    ↓
ToolRegistry.register(Tool)

LLM tool_call
    ↓
ToolRegistry.execute(ToolCall)
    ↓
McpToolExecutor
    ↓
McpClient tools/call
    ↓
McpToolAdapter
    ↓
ToolResult
```

相关文件：

```text
src/main/java/dev/agentmvp/mcp/McpClient.java
src/main/java/dev/agentmvp/mcp/McpToolAdapter.java
src/main/java/dev/agentmvp/mcp/McpToolExecutor.java
src/main/java/dev/agentmvp/mcp/McpToolLoader.java
```

### 8. 本地 MCP Server

本地 MCP Server 是 MCP 的另一侧：它不是 Agent，也不调用 LLM，只负责通过 JSON-RPC 暴露本地工具。

当前 MVP 支持三个方法：

```text
initialize
tools/list
tools/call
```

流程：

```text
McpClient initialize
    ↓
LocalMcpHttpServer /mcp
    ↓
LocalMcpServer.handle()
    ↓
返回 protocolVersion + tools 能力

McpClient tools/list
    ↓
LocalMcpToolRegistry.listTools()
    ↓
返回工具 name、description、inputSchema

McpClient tools/call
    ↓
LocalMcpToolRegistry.handler(name)
    ↓
执行本地 Java 函数
    ↓
返回 MCP content blocks
```

相关文件：

```text
src/main/java/dev/agentmvp/mcp/server/LocalMcpHttpServer.java
src/main/java/dev/agentmvp/mcp/server/LocalMcpServer.java
src/main/java/dev/agentmvp/mcp/server/LocalMcpToolRegistry.java
src/main/java/dev/agentmvp/mcp/server/model/LocalMcpTool.java
src/main/java/dev/agentmvp/mcp/server/model/McpToolCallResult.java
```

### 9. LLM Streaming Client

`StreamingChatClient` 是模型调用接口。当前项目只保留流式路径，不再保留非流式调用。

当前提供一个 OpenAI-compatible SSE 客户端。DeepSeek 只是一个默认供应商实现，
后续接 Kimi、Step 等服务时，实现 `OpenAiCompatibleProviderDefinition` 即可。

```text
src/main/java/dev/agentmvp/llm/OkHttpStreamingChatClient.java
src/main/java/dev/agentmvp/llm/StreamingChatClient.java
src/main/java/dev/agentmvp/llm/model/OpenAiCompatibleProvider.java
src/main/java/dev/agentmvp/llm/OpenAiCompatibleProviderDefinition.java
src/main/java/dev/agentmvp/llm/OpenAiCompatibleProviderFactory.java
src/main/java/dev/agentmvp/llm/OpenAiCompatibleChatClient.java
src/main/java/dev/agentmvp/llm/DeepSeekProvider.java
```

默认 DeepSeek 配置：

```text
baseUrl: https://api.deepseek.com
endpoint: /chat/completions
model: deepseek-v4-flash
api key env: DEEPSEEK_API_KEY
thinking: enabled
reasoning_effort: high
```

也可以用通用 OpenAI-compatible 环境变量覆盖：

```text
OPENAI_COMPATIBLE_PROVIDER
OPENAI_COMPATIBLE_BASE_URL
OPENAI_COMPATIBLE_API_KEY
OPENAI_COMPATIBLE_MODEL
```

流式处理要点：

```text
data: {"choices":[{"delta":{"content":"你"}}]}
data: {"choices":[{"delta":{"content":"好"}}]}
data: [DONE]
```

DeepSeek thinking mode 还会返回：

```text
data: {"choices":[{"delta":{"reasoning_content":"先理解用户意图"}}]}
```

当前处理方式：

- `delta.content` 转成 `AgentEvent.AssistantToken`
- `delta.reasoning_content` 转成 `AgentEvent.ReasoningToken`
- `delta.tool_calls` 继续由 `AssistantMessageBuilder` 组装完整 `ToolCall`
- assistant message 会保存 `reasoning_content`
- 如果 assistant 同时有 `reasoning_content` 和 `tool_calls`，后续请求会把这条 assistant message 原样带回 DeepSeek，避免 thinking + tool call 多轮协议断掉

工具事件：

```text
ToolCallStart(toolCallId, toolName, argumentsJson)
ToolCallDone(toolCallId, toolName, text, success, elapsedMillis)
```

TUI 会把它们渲染成 `ToolBlock`，而不是普通字符串。

测试中使用 fake streaming LLM，不依赖真实 API key。

## TUI 入口

当前 MVP 提供一个 Lanterna TUI 入口，默认通过 `mvn -q exec:java` 启动。

默认使用 DeepSeek `deepseek-v4-flash`：

```bash
export DEEPSEEK_API_KEY=你的 key
mvn -q exec:java
```

接其他 OpenAI-compatible 服务时，用通用环境变量覆盖：

```bash
export OPENAI_COMPATIBLE_PROVIDER=my-provider
export OPENAI_COMPATIBLE_BASE_URL=https://example.com
export OPENAI_COMPATIBLE_API_KEY=你的 key
export OPENAI_COMPATIBLE_MODEL=my-model
mvn -q exec:java
```

TUI 操作：

```text
Enter             - 发送输入
/exit             - 退出 TUI
ctrl+l            - 清空输出区
ctrl+o            - 展开/折叠工具输出
escape/ctrl+c/d   - 退出 TUI
↑/↓               - 历史消息滚动一行
PageUp/PageDown   - 历史消息滚动一页
End               - 回到底部
```

## 跑通方式

执行测试：

```bash
mvn test
```

当前测试覆盖：

- `ContextBuilderTest`：旧 turn 压缩后不会残留半截 `tool_call` 协议。
- `AgentLoopTest`：SSE delta 能组装成 assistant；多个工具调用后能写回 `tool` 消息；`reasoning_content` 会独立进入事件流并保存到 assistant message。
- `DeepSeekProviderTest`：DeepSeek 使用 OpenAI-compatible `/chat/completions` + `stream=true` 请求形状，并默认带 `thinking.type=enabled`、`reasoning_effort=high`。
- `McpClientTest`：通过 Mock MCP Server 跑通 `initialize`、`tools/list`、`tools/call`。
- `LocalMcpServerTest`：不用任何 MCP SDK，启动本地 HTTP JSON-RPC Server，再用 `McpClient` 调通本地工具。
- `BuiltinToolsTest`：四个内置工具统一注册到 `ToolRegistry`，并验证 read/write/edit/bash 的核心行为。
- `SkillRepositoryTest`：扫描 Skill 索引，渲染 system prompt，并按 name 加载完整 SKILL.md。
- `PromptLoaderTest`：从 jar classpath 读取内置 Markdown prompt。
- `TranscriptSummarizerTest`：验证上下文摘要 prompt 会进入摘要输入。

## 当前边界

这是最小 MVP，只实现四块：

- Agent loop
- 上下文压缩
- MCP 工具适配
- Skill 适配层
- Lanterna TUI 入口

没有实现：

- 长期记忆
- 完整 Skill 系统
- 浏览器工具
- web_fetch 安全规则
- 持久化 session
- 真实 tokenizer
- MCP stdio transport

这些都可以在当前结构上继续扩展，但不属于这个 MVP。
