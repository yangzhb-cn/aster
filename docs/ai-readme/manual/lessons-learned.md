# 历史经验

> 本文档由用户和 AI 共同维护。AI 与新成员写代码前先看这里，避免重复踩坑。

## 踩坑记录

<!-- 待沉淀: 问题 -> 原因 -> 方案。建议优先补充 tool_call 协议、上下文压缩、HITL、Plan/Team、Web/TUI 渲染、Room 多 Agent 聊天相关经验。 -->

| 问题 | 原因 | 方案 | 对应代码 |
| --- | --- | --- | --- |
| Agent 控制功能容易改动过多 | 一开始容易把 stop、steer、follow-up、事件、UI、session 全都揉进 AgentLoop | 控制状态放 core，调度放 app/runtime，输入分流放 ui，状态展示走 event；优先复用现有 AgentLoop 安全点 | `core/agent/control`, `AgentRunCoordinator` |
| steer 不是普通用户消息 | 运行中引导如果写入 session，可能插到 tool_call/tool_result 中间，破坏协议 | steer 通过 LLM 请求前 Hook 临时注入，不写入 session | `SteerExtension`, `BeforeLlmRequestContext` |
| 长期记忆和压缩摘要不应混入 system prompt 或永久历史 | 动态内容如果写进 session 或 system prompt，会污染审计历史，也难以恢复 | 统一放进最后一条 user 消息开头的 `<system-reminder>`，只参与本轮请求 | `SystemReminderInjectHook` |
| tool_call 协议很容易被破坏 | assistant 的 tool_calls 必须紧跟匹配的 role=tool 结果；中间不能插普通 user | 工具失败、审批拒绝、大结果卸载也要写回合法 tool 结果；上下文压缩按消息边界处理 | `AgentLoop`, `ContextBuilder` |
| MCP 工具名直接暴露原名容易冲突 | MCP server 的工具名可能和本地工具、扩展工具同名；如果直接改名又可能导致远端 `tools/call` 找不到原工具 | LLM 可见名加 `mcp_` 前缀，`Tool.remoteName` 保留远端原名；调用 MCP server 时用 `remoteName` | `Tool`, `McpClient` |
| MCP 加载失败不应拖垮整个 Runtime | 一个 MCP server 配置错误或进程启动失败，如果直接抛出会导致 Web/TUI 主入口不可用 | 记录 server 级 loaded/failed 状态；成功 server 继续注册，失败 server 在 Web 侧栏列出错误摘要 | `McpToolExecutor`, `McpToolExtension`, `WebServer` |
| Skill 注入不需要改核心代码 | Skill 是运行时资源，不是 Java 功能；为导入 Skill 改 AgentLoop 或工具注册会增加不必要复杂度 | 复制完整目录到 `workspace/skills`，重启 runtime 扫描；Web 只展示 name/description，完整内容由 `load_skill` 渐进读取 | `SkillRepository`, `SystemReminderInjectHook`, `LoadSkillTool` |
| 每轮 LLM 都全量 replay JSONL 会浪费 | 工具调用多轮时，每轮 `ContextPipeline.build()` 都会 `loadMessages()` 并回放完整事件日志 | 主 runtime 用 `ContextWindowCache` 维护运行态窗口；append 成功后增量更新，完整历史仍留在 JSONL | `ContextWindowCache`, `ContextWindowSnapshotSessionStore` |
| 恢复会话不能丢上下文压缩进度 | 只靠内存缓存时，重启后要么重新摘要全部历史，要么丢失上一轮摘要进度 | 用 `ContextWindowSnapshot` 保存 runningSummary、recentTurns、last seq/hash；启动时快照有效就恢复，并通过 `loadMessageRecordsAfter(lastSeq)` 只补齐新增 JSONL 消息 | `JsonContextWindowSnapshotStore`, `SessionMessageRecord` |
| Session 物理删除不能只删 JSONL | ContextWindowSnapshot 是 session 的运行态缓存；如果只删 `sessions/*.jsonl`，`context-windows/*.json` 会残留成孤儿文件 | 普通归档保留快照，归档中心物理删除 session 时同步删除该 session 的所有 context window 快照 | `JsonContextWindowSnapshotStore.deleteSession`, `WebServer.deleteArchiveItem` |
| Chat 模型切换不应破坏上下文快照 | `deepseek-v4-flash` / `deepseek-v4-pro` 是用户运行态选择，如果参与 snapshot 校验会导致恢复会话时无谓 full replay | 模型切换放在 `AgentRuntime`，`AgentLoop` 每次请求前读取；snapshot 只校验 session、prompt、摘要器、last seq/hash | `AgentRuntime`, `AgentLoop`, `AgentRuntimeFactory` |
| 多 Agent 模型不能全部写成全局常量 | Chat、Team、Room、Plan 的生命周期不同：Chat 是 session 状态，Team 是单次运行，Room 是 Agent 配置，Plan 是 planner/worker 策略 | Chat 保存在 `AgentRuntime`；Team 启动时快照模型；Room Agent 保存 `model`；Plan 固定 planner=pro、worker=flash | `AgentRuntime`, `TeamRunRequest`, `RoomAgentProfile`, `PlanPlannerAgent` |
| LLM 摘要不能复用 AgentLoop | 摘要如果走普通 AgentLoop，可能触发工具、Hook、Session 写入或递归压缩 | `LlmSummarizer` 只调用底层 `StreamingChatClient`，请求不带 tools/thinking，不写 session；失败回退 `TranscriptSummarizer` | `LlmSummarizer`, `TranscriptSummarizer` |
| Web 审批接口曾出现 `java.time.Instant` 序列化错误 | 当前 ObjectMapper 没有注册 `JavaTimeModule` | 对 Web DTO 和持久化状态优先使用 ISO 字符串时间，或显式注册模块；项目里 Todo/Room 使用字符串时间 | `ToolApprovalRequest`, `TodoItem`, `HubMessage` |
| TUI Markdown 表格渲染异常 | 表格宽度、中文字符宽度和换行处理如果按普通字符硬切，边框会错位 | 表格渲染要按列计算宽度，长内容截断/换行，不能只按原始 Markdown 输出 | `ui/tui` Markdown 渲染 |
| Web Enter 发送失效 | textarea 默认 Enter 换行，没有拦截 keydown 提交表单 | Enter 调用 `requestSubmit()`，Shift+Enter 保留换行 | `src/main/resources/web/assets/app.js` |
| Web 工具结果太长撑爆页面 | 工具调用和工具结果分散展示，长结果没有折叠或截断 | 工具调用与结果合成一个块，默认完成后折叠，长内容按行数和字符数截断 | `app.js` tool block |
| Web 空启动不应自动造 default | 自动创建 `default` session / 默认聊天室会让用户以为有历史会话，也会污染 workspace 运行数据 | Web 启动只恢复已有活跃 session；没有 session 时显示引导，首次发送消息或点击 `+` 才创建；Room 也不自动创建默认房间 | `WebMain`, `WebServer`, `app.js` |
| LLM 会用 `bash sleep` 实现提醒 | 提示词和长期记忆可能让模型以为定时任务应该靠 shell 等待 | 简单提醒用 `background_task reminder`；需要 Agent 到点做事用 `schedule`；HITL 直接拒绝伪定时 bash | `ToolApprovalHook`, `BackgroundTaskTool`, `ScheduleTool` |
| 定时 Agent 任务不应塞进后台 handler | 后台任务 handler 适合系统动作；“每天 12 点让 Agent 总结新闻”本质是自动提交一条新 user 消息 | `schedule` 保存 nextRunAt，到点后调用 `AgentRunCoordinator.submit()`，后续复用普通 AgentLoop | `app/schedule`, `AgentRuntimeFactory` |
| Web 可视化 schedule 不能另建状态源 | 如果 Web 面板自己写一套定时任务 JSON，会和 Agent 的 `schedule` 工具分裂，触发和取消逻辑也会不一致 | Web API 复用当前 session 的 `ScheduledUserMessageManager`；右栏只做创建/展示/取消，调度仍由原 scheduler 执行 | `AgentRuntime`, `WebServer.handleSchedules`, `app.js` |
| 后台任务扫描配置名容易误导 | 旧环境变量 `SCHEDULE_INTERVAL_SECONDS` 实际控制 background scan，不控制 schedule | 新增 `BACKGROUND_TASK_SCAN_INTERVAL_SECONDS`，旧变量只保留兼容 fallback；schedule 自己按 nextRunAt 精准唤醒 | `BackgroundTaskScheduler`, `.env.example` |
| `background_task` 描述不贴合提醒任务会误导模型 | 工具描述偏“后台执行”，模型不知道简单提醒和 Agent 自动化任务的边界 | 工具描述明确：background 处理 handler 和 reminder；schedule 处理自动化 user 消息 | `BackgroundTaskTool`, `ScheduleTool` |
| Telegram IM 看不到 bash 执行内容 | 只展示工具开始/结束，缺少参数和结果摘要 | IM 事件处理要展示工具名、参数摘要、执行耗时和必要输出，但仍要截断长文本 | `ui/im/telegram` |
| HITL 审批要有 call id 但也支持批量 | 用户审批时可能想按单个工具处理，也可能想全部通过/拒绝 | `/approve <id>`、`/deny <id>` 处理单个；不带 id 表示全部处理 | `ToolApprovalManager`, UI/IM commands |
| Web “默认通过”不应改 HITL 核心默认策略 | 有时用户希望当前 Web 会话自动批准工具，但 TUI/IM 仍应保持显式审批 | Web 入口保存本地审批模式；选择默认通过时自动调用已有 approve API，不改变 `ToolApprovalHook` 的保护工具列表 | `app.js`, `ToolApprovalManager` |
| Team 子 Agent 工具事件太多 | 多个 reader/reviewer 并行 read/grep 会产生大量工具事件，UI 会被刷屏 | Team 只转发成员正文和关键状态，不展示每个工具调用 | `TeamAgentFactory.teamEventBus` |
| Team 探索完不应直接结束 | 用户要的是“主 Agent -> Team -> 主 Agent”，不是 Team 自己给终稿 | Team 完整材料交回主 Agent，由主 Agent 基于材料整理最终回答 | `AgentRuntime.runTeam` |
| Team max tool rounds 过低会过早失败 | 代码探索类任务工具调用次数多，8 轮不够 | Team/Agent 工具轮数调到 100，减少探索中断 | `MAX_TOOL_ROUNDS` |
| Plan 和 Team 容易混淆 | Team 是固定 DAG 探索；Plan 是动态 DAG 编排和执行 | `/team` 做探索；`/plan` 先列 DAG，用户 `/start` 后按依赖执行 | `app/team`, `app/plan` |
| Plan 执行需要依赖解析 | 动态 DAG 里节点有先后依赖和资源冲突，不能简单顺序或全并发 | 解析 dependencies；可并行的节点并行，写文件/命令类节点用锁或串行保护 | `PlanModeCoordinator`, `PlanTaskExecutor` |
| Session 删除不能真删审计文件 | 用户想删除 UI 会话，但项目需要保留 JSONL 审计历史 | Session 删除做 `archived=true`；displayName 和 sessionId 分离 | `SessionIndex`, `SessionRecord` |
| Todo 用 JSON 比 JSONL 更适合 MVP | Web 右侧便签需要直接展示和编辑当前状态，不只是审计事件 | 第一版 Todo 用 JSON 保存当前列表；需要审计时再补事件流 | `JsonTodoStore` |
| 8080 被占用但浏览器还能看到页面 | 本机 8080 可能是 nginx 或旧服务，浏览器页面不代表当前 Java 进程 | 用 `lsof` 查监听进程；Web 默认 8081；旧 screen/java 进程要显式停止 | `aster2web` |
| 关闭浏览器不会停止服务 | Web 服务是独立进程，浏览器 tab 只是客户端 | 用脚本提示、`screen -S ... -X quit` 或 `kill <pid>` 停止服务 | `aster2web`, `aster2im` |
| Room 如果同步等 Agent 回复，用户会以为消息没发出去 | HTTP 请求要等待 LLM 完成，被 @ Agent 多时延迟明显 | 前端先乐观展示用户消息，后端返回后只追加 Agent 回复；后续可改 SSE/WebSocket | `app.js sendRoomMessage` |
| Room 工具限制要保存侧和运行侧都做 | 只在 ToolRegistry 屏蔽，Web 配置里仍显示危险工具会误导用户 | Agent 配置保存时剔除 forbidden tools，运行时再二次过滤 | `JsonRoomAgentRegistry`, `RoomToolRegistryFactory` |
| Room Agent 不能写死角色 | 用户后续不一定做研发主题，可能是任意领域聊天室 | Agent 的 name、role、description、system prompt 都外部配置并可在 Web CRUD | `RoomAgentProfile`, `RoomAgentPromptStore` |
| Room 共享消息不能等同于 Agent 私有上下文 | 后续加入的 Agent 要知道房间消息，但每个 Agent 又要有独立历史 | 房间 hub message 单独 JSONL；Agent 私有 session 单独 JSONL；通过 `RoomContextInjectHook` 临时注入 | `RoomHub`, `RoomAgentSessionFactory`, `RoomContextInjectHook` |
| Room `@all` 并行回复不能按完成时间写入 | 并行 Agent 完成时间不稳定，如果谁先完成谁先写，聊天室顺序每次可能不同 | 成员关系保存 `orderIndex`，本次回复保存 `replyIndex`；并行执行后按顺序统一写回 | `RoomMembership`, `RoomCoordinator` |
| 从聊天室移除 Agent 不等于删除 Agent | 全局 Agent 可复用到多个聊天室，移除只是离开当前房间 | 归档 `roomId + agentId` 成员关系；恢复时 generation + 1，旧私有上下文不再使用 | `RoomMembershipStore`, `RoomAgentSessionFactory` |
| Archive 批量删除必须复用单删校验 | 批量操作更容易绕过“只能删除已归档对象”的约束 | 批量接口只收 `{type,id}` 列表，逐个调用原来的物理删除逻辑 | `WebServer.handleArchives` |
| Web 多 session 并行不能只改后端 | A/B runtime 并行后，如果前端继续把所有 SSE 渲染到当前窗口，会出现 A 的 token 串到 B | HTTP 请求带 `sessionId`，后端用 `WebSessionRuntimePool` 路由；前端按 `meta.sessionName` 过滤渲染，非当前 session 只更新状态 | `WebSessionRuntimePool`, `app.js` |
| ai-readme 生成文档会随着代码快速过时 | 功能连续新增后，入口能力、架构图、核心流程可能还停留在旧版本 | 每次涉及代码改动、架构变化、功能新增或经验沉淀，都要评估是否同步 `docs/ai-readme/README.md`、`generated/`、`manual/` | `docs/ai-readme/*`, `AGENTS.md` |

## 已知风险

- 不要为了新增能力直接大改 `AgentLoop`。除非核心协议真的变化，否则优先通过 Hook、Event、Extension、Runtime 装配解决。
- 不要让模型靠 `bash sleep`、shell 脚本或长阻塞命令实现提醒、定时任务、后台任务。
- 不要把需要 Agent 到点执行的 schedule 做成 background handler；schedule 应提交 user 消息走普通 Agent 链路。
- 不要把动态提醒、长期记忆、旧对话摘要、Room 共享消息写进永久 session 历史。
- 不要把普通 user 消息插到 assistant tool_calls 和 role=tool 结果之间。
- 不要在 Team/Room 子 Agent 中开放写工具、bash、todo、background_task、schedule 或 subagent，除非用户明确改变边界。
- Web/IM 展示工具输出时必须截断长文本，避免 UI 被大结果撑爆。
- `workspace/`、`.env`、`.firecrawl/` 是运行数据或本地密钥相关内容，不要提交。
- 提示词外部化后，改代码时也要检查 `src/main/resources/prompts/` 是否需要同步更新。
- `manual/` 是 AI 与人工共同维护文档；实现过程中产生稳定经验时要回写，避免上下文丢失。

## 经验沉淀流程

```mermaid
flowchart LR
    Chat["用户与 AI 对话"] --> Problem["问题/决策/取舍"]
    Problem --> Cause["原因"]
    Cause --> Fix["方案"]
    Fix --> Rule["规则"]
    Rule --> Docs["更新 manual/ 或 AGENTS.md"]
```
