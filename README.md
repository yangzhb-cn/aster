# Aster

Aster 是一个教学版 Java Agent Runtime MVP，用来拆开演示一个 Agent Harness 应该具备的核心能力：流式 LLM、AgentLoop、工具调用、上下文压缩、Session 持久化、HITL 工具审批、MCP、Skill、长期记忆、后台任务、自动化用户消息 schedule、Plan、Team、多 Agent 聊天室、RAG 知识库问答，以及 TUI / Web / Telegram 多入口。

它不是生产级多租户平台。项目目标是用尽量清晰的代码展示 Agent Runtime 的关键组成和边界。

详细项目规则、架构和历史经验见 [AGENTS.md](./AGENTS.md) 与 [docs/ai-readme/README.md](./docs/ai-readme/README.md)。

## 技术栈

- Java 21
- Maven
- Jackson
- OkHttp
- JDK HttpServer + SSE
- Lanterna
- JUnit 5 + MockWebServer

## 架构分层

```text
ui/tui | ui/web | ui/im
        ↓
app/runtime
        ↓
core/agent | core/context | core/tool | core/event | core/hook | core/session | core/stage
        ↓
llm
```

- `llm`：模型能力层；Text Chat 走 OpenAI-compatible SSE，Embedding / Speech / Image / Multimodal 按能力拆接口。
- `core`：AgentLoop、Context、Tool、Event、Hook、Session、Stage 等主流程和抽象。
- `app`：运行时装配、内置/扩展工具、MCP、Skill、HITL、Memory、Background、Schedule、Todo、Plan、Team、Room、RAG。
- `ui`：TUI、Web、Telegram 三个入口。
- `src/main/resources/prompts/`：外部化系统提示词、摘要提示词、Plan/Team/Room/RAG 提示词。

## 当前能力

### Agent 主链路

- OpenAI-compatible SSE 流式响应。
- 多轮工具调用：`LLM -> tool_calls -> tool results -> LLM`。
- `AgentEventBus` 驱动 TUI / Web / Telegram 展示运行状态。
- 忙碌时 follow-up 排队，支持 `/stop` 停止和 TUI `/steer` 运行中引导。

### 工具系统

- 四个基础内置工具：`read`、`write`、`bash`、`edit`。
- 扩展工具：`ls`、`glob`、`grep`、`subagent`、`web_fetch`、`web_search`、`load_skill`、`todo`、`background_task`、`schedule`。
- MCP 工具通过 `mcp_` 前缀暴露给 LLM，避免和本地工具重名。
- `bash/write/edit` 默认走 HITL 审批；Web 可在当前浏览器选择“需要审批 / 默认通过”。

### 上下文和 Session

- Session JSONL 保存完整原始历史：`workspace/sessions/*.jsonl`。
- `ContextWindowCache` 保存运行态窗口：旧对话摘要 + 最近完整 turn。
- `ContextWindowSnapshot` 保存上一轮上下文进度，恢复会话时只补齐 `lastSeq` 之后的新消息。
- 上下文压缩优先使用 `LlmSummarizer` 调用 LLM 生成摘要，失败时回退转写摘要。
- 当前时间、Skill 索引、长期记忆、旧对话摘要等动态内容通过 `<system-reminder>` 注入最后一条 user 消息开头。

### 自动化和记忆

- 长期记忆使用 Markdown 存储，运行前注入，运行后可异步抽取。
- `background_task` 用于系统后台任务、延时提醒、Todo 扫描和长期记忆抽取。
- `schedule` 用于“到点后自动提交一条 user 消息”，适合每天/每周让 Agent 做事。
- Web 右栏可视化 Todo 和 Schedule，表单与条目默认折叠。

### 多 Agent

- `/team`：固定 DAG 探索，当前按 3 个 reader + 2 个 reviewer 并行，只读分析后把完整材料交回主 Agent 整理。
- `/team --model deepseek-v4-pro <任务>`：本次 Team 使用指定模型；不指定时跟随当前 Chat 模型。
- `/plan`：动态 DAG 规划，先展示计划，用户 `/start` 后按依赖执行；支持 `/plan cancel`。
- Plan 策略：planner 使用 `deepseek-v4-pro`，worker 使用 `deepseek-v4-flash`。
- Web Room：多 Agent 聊天室，房间共享消息 + 每个 Agent 独立私有上下文；支持 Agent CRUD、成员管理、`@Agent` 和 `@all`。

### RAG 知识库问答

- Web Knowledge 页面支持 RAG session、知识库、文档上传和流式问答。
- 顶部模型下拉在 Knowledge 视图下切换 RAG chat 模型，当前支持 `deepseek-v4-flash` / `deepseek-v4-pro`。
- 第一版使用本地文件存储：`workspace/rag/knowledge-bases`、`documents`、`chunks`、`indexes`、`sessions`。
- PDF 使用 PDFBox 解析，Markdown/文本直接读取；分块使用 1200 字符窗口 + 200 字符重叠。
- Embedding 固定走 Ollama `/api/embed`，默认模型 `nomic-embed-text:v1.5`。
- 回答使用 Knowledge 自己的 DeepSeek/OpenAI-compatible chat 配置，不给 LLM 暴露工具；后端先检索，再把 chunk 拼入 prompt，最后通过 SSE 流式返回。

### Web 能力

- 多 session 并行运行：切到 B 时，A 可以继续跑。
- 左侧 Session 列表、MCP / Skill 状态折叠展示。
- 顶部模型下拉切换 `deepseek-v4-flash` / `deepseek-v4-pro`。
- 普通 Chat 上传图片后走 Ollama 多模态模型流式回答，第一版不进入 AgentLoop 或 Session。
- 右侧 Token / Context 进度、审批模式、Todo、Schedule。
- 工具调用和工具结果合并为可折叠块，长内容截断。
- Archive 页面集中恢复或物理删除已归档的 session、todo、room、room-agent。
- Knowledge 页面使用独立 RAG session，支持文件入库和引用来源展示。

## 快速启动

复制环境变量示例：

```bash
cp .env.example .env
```

至少填写：

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

默认打开：

```text
http://localhost:8081
```

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

- `DEEPSEEK_API_KEY`：DeepSeek API Key。
- `OPENAI_COMPATIBLE_PROVIDER` / `OPENAI_COMPATIBLE_BASE_URL` / `OPENAI_COMPATIBLE_API_KEY` / `OPENAI_COMPATIBLE_MODEL`：OpenAI-compatible provider 覆盖配置。
- `OLLAMA_BASE_URL` / `OLLAMA_CHAT_MODEL` / `OLLAMA_CHAT_MODELS` / `OLLAMA_EMBEDDING_MODEL`：Ollama 本地模型配置；RAG embedding 使用 `OLLAMA_EMBEDDING_MODEL`，chat 切到 Ollama 时使用 chat 配置。
- `OLLAMA_MULTIMODAL_MODEL` / `OLLAMA_MULTIMODAL_MODELS`：Web Chat 带图片时使用的 Ollama 多模态模型，默认 `llava-llama3:latest`。
- `ASTER_WEB_PORT`：Web 端口，`aster2web` 默认 `8081`。
- `ASTER_SESSION`：可选启动 session；为空时 Web 不自动创建 `default`。
- `TELEGRAM_BOT_TOKEN` / `TELEGRAM_ALLOWED_CHAT_IDS`：Telegram Bot 配置。
- `OWNER_ID`：`aster2im` 的简化 chatId 配置。
- `TAVILY_API_KEY`：`web_search` 工具需要。
- `BACKGROUND_TASK_SCAN_INTERVAL_SECONDS`：后台任务扫描间隔，默认 `10` 秒。
- `SCHEDULE_INTERVAL_SECONDS`：旧变量名，仅作为后台任务扫描间隔兼容 fallback。

## 常用命令

```bash
mvn test
mvn package
node --check src/main/resources/web/assets/app.js
```

Web 服务如果端口占用：

```bash
lsof -nP -iTCP:8081 -sTCP:LISTEN
screen -S aster2web -X quit
kill <java-pid>
```

## 斜杠命令

- `/session ...`：会话操作。
- `/model [模型名]`：查看或切换当前 Chat 模型。
- `/steer <message>`：运行中引导当前 Agent。
- `/stop`：停止当前 run，并取消待审批工具。
- `/team [--model 模型名] <任务>`：启动固定 DAG Agent Team 探索。
- `/plan <任务>`：生成动态 DAG 草案。
- `/start`：执行当前 Plan。
- `/plan cancel`：取消当前 Plan。
- `/approve [id]` / `/deny [id] [reason]`：处理工具审批；不带 id 表示处理全部。

## Workspace 数据

运行时数据写入 `workspace/`，默认不提交：

```text
workspace/
├── sessions/             # Session JSONL
├── context-windows/      # 上下文窗口快照
├── artifacts/tool-results/
├── schedules/
├── todos/
├── tasks/
├── memory/
├── rooms/
├── skills/
├── im/
└── mcp.json
```

真实 `.env`、`workspace/`、`.firecrawl/` 都不要提交。

## 文档入口

- [docs/ai-readme/README.md](./docs/ai-readme/README.md)：AI + 人类共用项目上下文入口。
- [docs/ai-readme/generated/architecture.md](./docs/ai-readme/generated/architecture.md)：架构和模块边界。
- [docs/ai-readme/generated/core-flows.md](./docs/ai-readme/generated/core-flows.md)：核心流程。
- [docs/ai-readme/manual/business-knowledge.md](./docs/ai-readme/manual/business-knowledge.md)：业务规则和项目约定。
- [docs/ai-readme/manual/lessons-learned.md](./docs/ai-readme/manual/lessons-learned.md)：踩坑记录。
