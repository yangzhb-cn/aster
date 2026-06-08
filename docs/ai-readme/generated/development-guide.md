# 开发指南

<!-- AI生成，可根据团队规范更新 -->

## 环境准备

| 工具 | 版本 |
| --- | --- |
| Java | 21 |
| Maven | 使用本机 Maven；项目 `pom.xml` 配置 `maven.compiler.release=21` |
| 模型 API Key | `DEEPSEEK_API_KEY` 或 `OPENAI_COMPATIBLE_API_KEY`；Ollama 本地模型可不填 |

## 常用命令

| 操作 | 命令 |
| --- | --- |
| 复制环境变量示例 | `cp .env.example .env` |
| 启动 TUI | `./aster2tui` |
| 启动 Web | `./aster2web` |
| 启动 Telegram IM | `./aster2im` |
| Maven 启动默认 TUI | `mvn -q exec:java` |
| Maven 启动 Web | `mvn -q -Dexec.mainClass=com.aster.ui.web.WebMain exec:java` |
| Maven 启动 Telegram | `mvn -q -Dexec.mainClass=com.aster.ui.im.telegram.TelegramMain exec:java` |
| 运行测试 | `mvn test` |
| 构建打包 | `mvn package` |

## 配置文件清单

| 文件 | 作用 |
| --- | --- |
| `.env.example` | 本地环境变量示例 |
| `.env` | 本地真实密钥和端口配置；不要提交 |
| `pom.xml` | Java 21、依赖、Maven 插件配置 |
| `src/main/resources/prompts/agent/system.md` | 主 Agent system prompt |
| `src/main/resources/prompts/context/summary.md` | 上下文摘要 prompt |
| `src/main/resources/prompts/memory/*.md` | 长期记忆抽取和注入 prompt |
| `src/main/resources/prompts/plan/*.md` | 动态 Plan prompt |
| `src/main/resources/prompts/team/*.md` | Agent Team prompt |
| `src/main/resources/prompts/room/*.md` | Room Agent 包装 prompt 和默认 Agent 模板 |
| `src/main/resources/prompts/room/default-agents.json` | Web Room 首次启动导入的示例 Agent 清单 |
| `src/main/resources/prompts/rag/answer-system.md` | Knowledge RAG 回答 system prompt |
| `src/main/resources/web/index.html` | Web 页面 |
| `src/main/resources/web/assets/app.js` | Web 前端逻辑 |
| `src/main/resources/web/assets/app.css` | Web 样式 |
| `workspace/mcp.json` | MCP 配置；运行时本地文件，不提交 |
| `workspace/skills/*/SKILL.md` | 本地 Skill；运行时扫描 name/description，完整内容由 `load_skill` 读取，不提交 |

## 关键环境变量

| 变量 | 作用 |
| --- | --- |
| `DEEPSEEK_API_KEY` | 默认 DeepSeek API Key |
| `OPENAI_COMPATIBLE_PROVIDER` | 覆盖 OpenAI-compatible provider 名称 |
| `OPENAI_COMPATIBLE_BASE_URL` | 覆盖模型 API base URL |
| `OPENAI_COMPATIBLE_API_KEY` | 覆盖模型 API key |
| `OPENAI_COMPATIBLE_MODEL` | 覆盖启动模型名；DeepSeek 当前支持运行中切换 `deepseek-v4-flash` / `deepseek-v4-pro` |
| `OLLAMA_BASE_URL` | Ollama 本地服务地址，默认 `http://localhost:11434` |
| `OLLAMA_CHAT_MODEL` | Ollama 默认 chat 模型，当前示例为 `qwen3:latest` |
| `OLLAMA_CHAT_MODELS` | Ollama 可切换 chat 模型列表，逗号分隔 |
| `OLLAMA_EMBEDDING_MODEL` | Ollama 默认 embedding 模型，当前示例为 `nomic-embed-text:v1.5` |
| `OLLAMA_MULTIMODAL_MODEL` | Web Chat 带图片时使用的 Ollama 多模态模型，当前示例为 `llava-llama3:latest` |
| `OLLAMA_MULTIMODAL_MODELS` | Web 图片理解可切换模型列表，逗号分隔 |
| `ASTER_WEB_PORT` | Web 端口，`aster2web` 默认 8081 |
| `ASTER_SESSION` | 可选 Web 启动 session 名称；为空时不创建 `default`，已有活跃 session 会恢复，完全空仓库等待用户新建或首条发送 |
| `TELEGRAM_BOT_TOKEN` | Telegram Bot token |
| `TELEGRAM_ALLOWED_CHAT_IDS` | Telegram chat 白名单，逗号分隔 |
| `OWNER_ID` | `aster2im` 兼容变量，可转换为 Telegram 白名单 |
| `TAVILY_API_KEY` | `web_search` 工具使用 |
| `BACKGROUND_TASK_SCAN_INTERVAL_SECONDS` | 后台任务扫描间隔，默认 10 秒 |
| `SCHEDULE_INTERVAL_SECONDS` | 旧后台扫描变量名，仍兼容；新配置优先使用 `BACKGROUND_TASK_SCAN_INTERVAL_SECONDS` |

## 开发循环

```mermaid
flowchart LR
    Env["cp .env.example .env\n填写 key"] --> Start["启动 TUI/Web/IM"]
    Start --> Change["修改 Java / prompt / web asset"]
    Change --> Test["mvn test"]
    Test --> Run["重新启动入口验证"]
    Run --> Docs["必要时更新 README / AGENTS / ai-readme"]
```

## 调试与验证

### Web

- 默认入口：`http://localhost:8081`
- 默认脚本：`./aster2web`
- 当前页面包含 Chat、Room、Knowledge、Archive 四个视图；Room 和 Knowledge 是 Web 独有入口。
- Web 空启动不会自动创建 `default` session 或默认聊天室；没有会话时，点击 `+` 或直接发送第一条消息会创建新会话。
- Chat 视图可上传图片并直接提问；有图片时顶部模型下拉切到 Ollama 多模态模型，后端走 `/api/vision/chat` SSE，第一版不写普通 session。
- Chat 右栏包含审批模式、Todo 和 Schedule 面板；Todo/Schedule 新建表单和已有条目默认折叠，Schedule 面板创建的是当前 session 的自动化用户消息，例如每日整理长期记忆。
- Knowledge 页面支持 RAG session、知识库、文档上传和流式问答；顶部模型下拉在该视图切换 RAG chat 模型；PDF 解析依赖 PDFBox，embedding 默认走本地 Ollama `nomic-embed-text:v1.5`，回答阶段走 DeepSeek/OpenAI-compatible SSE。
- 如果端口被占用，`aster2web` 会打印占用进程，并提示：
  - `screen -S aster2web -X quit`
  - `ASTER_WEB_PORT=8082 ./aster2web`

### TUI

- 默认入口：`./aster2tui`
- 常用命令：
  - `/session ...`
  - `/steer <message>`
  - `/stop`
  - `/team [--model 模型名] <任务>`
  - `/plan <任务>`
  - `/start`
  - `/approve [id]`
  - `/deny [id] [reason]`

### Telegram

- 默认入口：`./aster2im`
- 需要 `.env` 中配置 `TELEGRAM_BOT_TOKEN` 和 `TELEGRAM_ALLOWED_CHAT_IDS`。
- 如果只配置 `OWNER_ID`，脚本会把它转换为 `TELEGRAM_ALLOWED_CHAT_IDS`。

### Runtime 数据

```text
workspace/
├── sessions/                 # Session JSONL 和 index.json
├── context-windows/          # 上下文窗口快照 JSON，恢复压缩进度用
├── tasks/                    # 后台任务 JSONL
├── todos/                    # Todo JSON
├── skills/                   # 本地 Skill
├── artifacts/tool-results/   # 大工具结果
├── memory/                   # 长期记忆 Markdown
├── im/                       # Telegram session 映射
├── rooms/                    # Web Room、成员关系、Agent 配置、hub message 和私有 session
└── rag/                      # Knowledge 知识库、文档、chunk、向量索引和 RAG session
```

## 修改规则

- 新增 Java 类要放在所属包的合理子包下。
- 新增非底座工具优先走 `app/extension/AsterRuntimeExtension`。
- 新增可选流程逻辑优先走 Hook；主流程必经步骤才考虑 Stage。
- 修改 Agent 主链路后至少运行 `mvn test`。
- 纯文档修改可以不跑测试，但交付时要说明。
- 涉及代码改动、架构变化、功能新增或经验沉淀时，同步评估 `docs/ai-readme/README.md`、`generated/` 和 `manual/` 是否需要更新。
