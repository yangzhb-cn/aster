# Agent 工作区

这个目录放运行时附加数据，不放 Java 源码。

- `mcp.json`：本地 MCP 配置，真实文件不提交。
- `mcp.json.example`：MCP 配置模板。
- `skills/`：本地 Skill 目录，启动时扫描 `skills/*/SKILL.md`。
- `sessions/`：JSONL session 事件日志，真实日志不提交。
- `tasks/`：后台任务定义和运行记录，真实日志不提交。
- `memory/`：Markdown 长期记忆目录，运行时会生成 `long-term-memory.md`，真实记忆内容不提交。
