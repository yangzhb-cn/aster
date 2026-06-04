# Aster

Aster 是 Agent MVP，从 0-1 实现 Agent 主循环、流式 LLM、工具调用、上下文压缩、Session 持久化、HITL 审批、后台任务、自动化用户消息 schedule、动态 DAG Plan 和多智能体 agent team，以及TUI/Web/Telegram 入口。

详细架构和开发规则见 [AGENTS.md](./AGENTS.md)。

## 技术栈

- Java 21
- Maven
- Jackson
- OkHttp
- Lanterna
- JUnit 5 + MockWebServer

## 核心分层

```text
ui
 ↓
app/runtime
 ↓
core
 ↓
llm
```

- `llm`：OpenAI-compatible SSE 适配。
- `core`：AgentLoop、Event、Hook、Stage、Context、Session、Tool 抽象。
- `app`：内置工具、扩展工具、MCP、Skill、长期记忆、后台任务、schedule、Plan/Team、运行时装配。
- `ui`：TUI、Web Chat、Telegram IM。

## 主要能力

- 流式 AgentLoop：`LLM -> tool_calls -> tool results -> LLM`。
- 工具：`read/write/bash/edit/load_skill`，以及 `ls/glob/grep/subagent/web_fetch/web_search/todo/background_task/schedule`。
- HITL：`bash/write/edit` 执行前需要人工审批。
- 上下文：运行态维护旧对话摘要和最近 turn，摘要注入 `<system-reminder>`。
- Session：`workspace/sessions/*.jsonl` 持久化完整原始历史。
- Web/TUI/IM：共用 `AgentRuntime` 和 `AgentEvent`。
- `/team`：固定 DAG 的只读并行探索。
- `/plan`：动态生成 DAG，用户 `/start` 后按依赖并发执行。
- `schedule`：到点后自动向当前 session 提交 user 消息，适合每天/每周/定期让 Agent 做事。

## 启动

先复制环境变量示例：

```bash
cp .env.example .env
```

编辑 `.env`，至少填写：

```bash
DEEPSEEK_API_KEY=你的 key
```

启动 TUI：

```bash
./aster2tui
```

启动 Web：

```bash
./aster2web
```

默认打开：`http://localhost:8081`

启动 Telegram：

```bash
./aster2im
```

Telegram 需要在 `.env` 里填写：

```bash
TELEGRAM_BOT_TOKEN=你的 bot token
TELEGRAM_ALLOWED_CHAT_IDS=你的 chatId
```

也可以直接用 Maven：

```bash
mvn -q exec:java
mvn -q -Dexec.mainClass=com.aster.ui.web.WebMain exec:java
mvn -q -Dexec.mainClass=com.aster.ui.im.telegram.TelegramMain exec:java
```

## 常用环境变量

- `DEEPSEEK_API_KEY`：默认模型 API Key。
- `OPENAI_COMPATIBLE_*`：覆盖 provider、base URL、API Key、model。
- `ASTER_WEB_PORT`：Web 端口，`aster2web` 默认 `8081`。
- `ASTER_SESSION`：Web 启动 session。
- `TELEGRAM_BOT_TOKEN` / `TELEGRAM_ALLOWED_CHAT_IDS`：Telegram 配置。
- `OWNER_ID`：`aster2im` 的简化 chatId 配置。
- `TAVILY_API_KEY`：`web_search` 工具需要。
- `BACKGROUND_TASK_SCAN_INTERVAL_SECONDS`：后台任务扫描间隔，默认 `10`。
- `SCHEDULE_INTERVAL_SECONDS`：旧变量名，仍作为后台任务扫描间隔的兼容 fallback；新配置优先用 `BACKGROUND_TASK_SCAN_INTERVAL_SECONDS`。

## 斜杠命令

- `/session ...`：会话 CRUD。
- `/steer <message>`：运行中引导当前 Agent。
- `/stop`：请求当前 run 停止。
- `/team <任务>`：启动固定 DAG Agent Team 探索。
- `/plan <任务>`：生成动态 DAG 草案。
- `/start`：执行当前 Plan。
- `/plan cancel`：取消当前 Plan。
- `/approve [id]` / `/deny [id] [reason]`：处理工具审批；不带 id 表示处理全部。

## Workspace

运行时数据写入 `workspace/`：

```text
workspace/
├── sessions/
├── tasks/
├── schedules/
├── todos/
├── skills/
├── artifacts/tool-results/
├── memory/
└── im/
```

`workspace/` 和真实 `.env` 都不会提交。

## 测试

```bash
mvn test
```
