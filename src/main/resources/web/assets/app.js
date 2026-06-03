const state = {
  busy: false,
  queuedCount: 0,
  currentSessionId: "",
  sessions: [],
  tools: new Map(),
  currentAssistant: null,
  currentReasoning: null,
  teamMembers: new Map(),
  approvals: new Map(),
};
const TOOL_PREVIEW_CHAR_LIMIT = 1200;
const TOOL_PREVIEW_LINE_LIMIT = 12;

const $ = (selector) => document.querySelector(selector);
const messagesEl = $("#messages");
const promptEl = $("#prompt");
const sendButton = $("#sendButton");
const stopButton = $("#stopButton");
const newSessionButton = $("#newSessionButton");
const sessionList = $("#sessionList");
const connectionState = $("#connectionState");
const modelName = $("#modelName");
const sessionName = $("#sessionName");
const inputTokens = $("#inputTokens");
const cacheTokens = $("#cacheTokens");
const missTokens = $("#missTokens");
const outputTokens = $("#outputTokens");
const totalTokens = $("#totalTokens");
const contextBefore = $("#contextBefore");
const contextAfter = $("#contextAfter");
const contextMax = $("#contextMax");
const contextCompressed = $("#contextCompressed");
const todoForm = $("#todoForm");
const todoContent = $("#todoContent");
const todoDueAt = $("#todoDueAt");
const refreshTodosButton = $("#refreshTodosButton");
const todoList = $("#todoList");

function escapeHtml(value) {
  return String(value ?? "")
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;");
}

function renderInlineMarkdown(value) {
  return escapeHtml(value)
    .replace(/`([^`]+)`/g, "<code>$1</code>")
    .replace(/\*\*([^*]+)\*\*/g, "<strong>$1</strong>")
    .replace(/\[([^\]]+)\]\((https?:\/\/[^)\s]+)\)/g, '<a href="$2" target="_blank" rel="noreferrer">$1</a>');
}

function renderMarkdown(value) {
  const lines = String(value ?? "").replace(/\r\n/g, "\n").split("\n");
  const html = [];
  let paragraph = [];
  let list = null;
  let code = null;
  let table = null;

  const flushParagraph = () => {
    if (!paragraph.length) return;
    html.push(`<p>${renderInlineMarkdown(paragraph.join(" "))}</p>`);
    paragraph = [];
  };
  const flushList = () => {
    if (!list) return;
    html.push(`<${list.type}>${list.items.map((item) => `<li>${renderInlineMarkdown(item)}</li>`).join("")}</${list.type}>`);
    list = null;
  };
  const flushTable = () => {
    if (!table) return;
    if (!table.header) {
      html.push(`<p>${renderInlineMarkdown(table.pending.join(" | "))}</p>`);
      table = null;
      return;
    }
    const head = table.header.map((cell) => `<th>${renderInlineMarkdown(cell)}</th>`).join("");
    const rows = table.rows.map((row) => `<tr>${row.map((cell) => `<td>${renderInlineMarkdown(cell)}</td>`).join("")}</tr>`).join("");
    html.push(`<div class="table-wrap"><table><thead><tr>${head}</tr></thead><tbody>${rows}</tbody></table></div>`);
    table = null;
  };
  const parseRow = (line) => {
    if (!line.includes("|")) return null;
    let text = line.trim();
    if (text.startsWith("|")) text = text.slice(1);
    if (text.endsWith("|")) text = text.slice(0, -1);
    const cells = text.split("|").map((cell) => cell.trim());
    return cells.length >= 2 ? cells : null;
  };
  const isSeparator = (cells) => cells?.every((cell) => /^:?-{3,}:?$/.test(cell));

  for (const line of lines) {
    const fence = line.match(/^```/);
    if (fence) {
      if (code) {
        html.push(`<pre><code>${escapeHtml(code.join("\n"))}</code></pre>`);
        code = null;
      } else {
        flushParagraph();
        flushList();
        flushTable();
        code = [];
      }
      continue;
    }
    if (code) {
      code.push(line);
      continue;
    }

    const trimmed = line.trim();
    if (!trimmed) {
      flushParagraph();
      flushList();
      flushTable();
      continue;
    }

    const row = parseRow(line);
    if (row && !table) {
      table = { pending: row, header: null, rows: [] };
      continue;
    }
    if (row && table?.pending) {
      if (isSeparator(row)) {
        table.header = table.pending;
        delete table.pending;
      } else {
        paragraph.push(table.pending.join(" | "));
        table = null;
        paragraph.push(trimmed);
      }
      continue;
    }
    if (row && table?.header) {
      table.rows.push(row);
      continue;
    }
    if (table) flushTable();

    const heading = trimmed.match(/^(#{1,4})\s+(.+)$/);
    if (heading) {
      flushParagraph();
      flushList();
      const level = Math.min(heading[1].length + 1, 4);
      html.push(`<h${level}>${renderInlineMarkdown(heading[2])}</h${level}>`);
      continue;
    }

    const ordered = line.match(/^\s*\d+\.\s+(.+)$/);
    const unordered = line.match(/^\s*[-*]\s+(.+)$/);
    if (ordered || unordered) {
      flushParagraph();
      const type = ordered ? "ol" : "ul";
      if (!list || list.type !== type) {
        flushList();
        list = { type, items: [] };
      }
      list.items.push((ordered || unordered)[1]);
      continue;
    }

    if (trimmed.startsWith(">")) {
      flushParagraph();
      flushList();
      html.push(`<blockquote>${renderInlineMarkdown(trimmed.replace(/^>\s?/, ""))}</blockquote>`);
      continue;
    }

    flushList();
    paragraph.push(trimmed);
  }

  flushParagraph();
  flushList();
  flushTable();
  if (code) html.push(`<pre><code>${escapeHtml(code.join("\n"))}</code></pre>`);
  return html.join("");
}

function addMessage(role, text = "") {
  const node = document.createElement("article");
  node.className = `message ${role}`;
  node.innerHTML = `
    <div class="message-header">${escapeHtml(displayRole(role))}</div>
    <div class="message-body"></div>
  `;
  const message = { node, text: "" };
  messagesEl.append(node);
  updateMessage(message, text);
  scrollToBottom();
  return message;
}

function displayRole(role) {
  if (role === "assistant" || role === "system") return "Aster";
  if (role === "team") return "TEAM";
  if (role === "thinking") return "Thinking";
  if (role === "user") return "USER";
  if (role === "error") return "ERROR";
  return role;
}

function updateMessage(message, text) {
  message.text = text;
  message.node.querySelector(".message-body").innerHTML = renderMarkdown(text);
  scrollToBottom();
}

function ensureTeamMemberMessage(payload = {}, initialText = "") {
  const taskId = payload.taskId || "";
  const role = payload.role || "member";
  const key = `${taskId}:${role}`;
  let block = state.teamMembers.get(key);
  if (!block) {
    block = addMessage("team", initialText);
    block.node.querySelector(".message-header").textContent = `TEAM ${role}${taskId ? `(${taskId})` : ""}`;
    state.teamMembers.set(key, block);
  }
  return block;
}

function scrollToBottom() {
  const surface = messagesEl.closest(".chat-surface");
  surface.scrollTop = surface.scrollHeight;
}

function addToolBlock(payload = {}) {
  const node = document.createElement("article");
  node.className = "message tool";
  node.innerHTML = `
    <div class="message-header">TOOL</div>
    <div class="message-body">
      <div class="tool-card running">
        <div class="tool-top">
          <strong class="tool-title"></strong>
          <div class="tool-actions">
            <span class="tool-status">running</span>
            <button class="tool-toggle" type="button" title="折叠工具详情" aria-label="折叠工具详情">-</button>
          </div>
        </div>
        <div class="tool-details">
          <div class="tool-meta"></div>
          <pre class="tool-args" hidden><code></code></pre>
          <div class="tool-output" hidden>
            <span class="tool-section-label">result</span>
            <pre><code></code></pre>
            <span class="tool-truncated" hidden></span>
          </div>
        </div>
      </div>
    </div>
  `;
  const block = {
    node,
    card: node.querySelector(".tool-card"),
    title: node.querySelector(".tool-title"),
    status: node.querySelector(".tool-status"),
    toggle: node.querySelector(".tool-toggle"),
    details: node.querySelector(".tool-details"),
    meta: node.querySelector(".tool-meta"),
    args: node.querySelector(".tool-args"),
    argsCode: node.querySelector(".tool-args code"),
    output: node.querySelector(".tool-output"),
    outputCode: node.querySelector(".tool-output code"),
    truncated: node.querySelector(".tool-truncated"),
    collapsed: false,
    userToggled: false,
    payload: {},
  };
  block.toggle.addEventListener("click", () => setToolCollapsed(block, !block.collapsed, true));
  messagesEl.append(node);
  updateToolBlock(block, payload, "running");
  scrollToBottom();
  return block;
}

function addApprovalBlock(payload = {}) {
  const node = document.createElement("article");
  node.className = "message approval";
  node.innerHTML = `
    <div class="message-header">APPROVAL</div>
    <div class="message-body">
      <div class="approval-card pending">
        <div class="approval-top">
          <strong class="approval-title"></strong>
          <span class="approval-status">pending</span>
        </div>
        <div class="approval-meta"></div>
        <pre class="approval-args"><code></code></pre>
        <div class="approval-actions">
          <button class="approval-approve" type="button">Approve</button>
          <button class="approval-deny" type="button">Deny</button>
        </div>
      </div>
    </div>
  `;
  const block = {
    node,
    card: node.querySelector(".approval-card"),
    title: node.querySelector(".approval-title"),
    status: node.querySelector(".approval-status"),
    meta: node.querySelector(".approval-meta"),
    argsCode: node.querySelector(".approval-args code"),
    approve: node.querySelector(".approval-approve"),
    deny: node.querySelector(".approval-deny"),
  };
  block.approve.addEventListener("click", () => resolveApproval(payload.approvalId, true));
  block.deny.addEventListener("click", () => resolveApproval(payload.approvalId, false));
  updateApprovalBlock(block, payload);
  messagesEl.append(node);
  scrollToBottom();
  return block;
}

function updateApprovalBlock(block, payload = {}, resolved = null) {
  block.title.textContent = `审批工具：${payload.toolName || "tool"}`;
  block.meta.textContent = `id=${payload.approvalId || ""} · call=${payload.toolCallId || ""}`;
  block.argsCode.textContent = previewText(formatToolArguments(payload.argumentsJson)).text;
  if (resolved) {
    block.card.className = `approval-card ${resolved.approved ? "approved" : "denied"}`;
    block.status.textContent = resolved.approved ? "approved" : "denied";
    block.approve.disabled = true;
    block.deny.disabled = true;
  }
}

async function resolveApproval(approvalId, approved) {
  if (!approvalId) return;
  try {
    await postJson(approved ? "/api/approvals/approve" : "/api/approvals/deny", { id: approvalId });
  } catch (error) {
    addMessage("error", error.message);
  }
}

function updateToolBlock(block, payload = {}, status = "running") {
  block.payload = {
    ...block.payload,
    ...Object.fromEntries(Object.entries(payload).filter(([, value]) => value !== undefined && value !== null && value !== "")),
  };
  const merged = block.payload;
  const title = formatToolTitle(merged.toolName, merged.argumentsJson);
  block.title.textContent = title || merged.toolName || "tool";
  block.status.textContent = formatToolStatus(status, merged.elapsedMillis);
  block.card.className = `tool-card ${status}`;
  if (block.collapsed) block.card.classList.add("collapsed");
  block.meta.textContent = merged.toolCallId ? `id=${merged.toolCallId}` : "";

  renderToolPreview(block.args, block.argsCode, null, formatToolArguments(merged.argumentsJson));
  renderToolPreview(block.output, block.outputCode, block.truncated, merged.text || "");
  if (!block.userToggled) {
    setToolCollapsed(block, status !== "running", false);
  }
  scrollToBottom();
}

function renderToolPreview(container, codeNode, truncatedNode, value) {
  const preview = previewText(value);
  container.hidden = preview.text.length === 0;
  codeNode.textContent = preview.text;
  if (!truncatedNode) return preview;
  truncatedNode.hidden = !preview.truncated;
  truncatedNode.textContent = preview.truncated
    ? `已截断 ${preview.lineCount} 行 / ${preview.charCount} 字符`
    : "";
  return preview;
}

function setToolCollapsed(block, collapsed, userToggled) {
  block.collapsed = collapsed;
  if (userToggled) block.userToggled = true;
  block.card.classList.toggle("collapsed", collapsed);
  block.toggle.textContent = collapsed ? "+" : "-";
  const label = collapsed ? "展开工具详情" : "折叠工具详情";
  block.toggle.title = label;
  block.toggle.setAttribute("aria-label", label);
}

function previewText(value) {
  const normalized = String(value ?? "").replace(/\r\n/g, "\n").replace(/\r/g, "\n");
  if (!normalized.trim()) {
    return { text: "", truncated: false, lineCount: 0, charCount: 0 };
  }

  const lines = normalized.split("\n");
  let truncated = lines.length > TOOL_PREVIEW_LINE_LIMIT;
  let text = lines.slice(0, TOOL_PREVIEW_LINE_LIMIT).join("\n");
  if (text.length > TOOL_PREVIEW_CHAR_LIMIT) {
    text = text.slice(0, TOOL_PREVIEW_CHAR_LIMIT);
    truncated = true;
  }
  return {
    text,
    truncated,
    lineCount: lines.length,
    charCount: normalized.length,
  };
}

function formatToolStatus(status, elapsedMillis) {
  if (status === "running") return "running";
  const took = Number.isFinite(Number(elapsedMillis)) ? ` · ${elapsedMillis}ms` : "";
  return `${status}${took}`;
}

function formatToolArguments(argumentsJson) {
  if (!argumentsJson || !String(argumentsJson).trim()) return "";
  try {
    return JSON.stringify(JSON.parse(argumentsJson), null, 2);
  } catch {
    return String(argumentsJson);
  }
}

function formatToolTitle(toolName, argumentsJson) {
  const args = parseToolArguments(argumentsJson);
  const normalized = String(toolName || "");
  const dot = normalized.lastIndexOf(".");
  const prefix = dot > 0 ? `${normalized.slice(0, dot)}.` : "";
  const baseName = dot > 0 ? normalized.slice(dot + 1) : normalized;
  if (baseName === "bash") return `${prefix}$ ${stringArg(args, "command", argumentsJson)}`;
  if (baseName === "read") return `${prefix}read ${stringArg(args, "path", argumentsJson)}`;
  if (baseName === "write") return `${prefix}write ${stringArg(args, "path", argumentsJson)}`;
  if (baseName === "edit") return `${prefix}edit ${stringArg(args, "path", argumentsJson)}`;
  if (baseName === "load_skill") return `${prefix}load_skill ${stringArg(args, "name", argumentsJson)}`;
  return `${toolName || "tool"} ${argumentsJson || ""}`.trim();
}

function parseToolArguments(argumentsJson) {
  if (!argumentsJson || !String(argumentsJson).trim()) return {};
  try {
    return JSON.parse(argumentsJson);
  } catch {
    return {};
  }
}

function stringArg(args, name, fallback = "") {
  const value = args?.[name];
  return value === undefined || value === null ? String(fallback || "") : String(value);
}

async function postJson(path, body = {}) {
  const data = await requestJson(path, {
    method: "POST",
    body,
  });
  if (data.status) {
    applyStatus(data.status);
  } else {
    applyStatus(data);
  }
  return data;
}

async function requestJson(path, options = {}) {
  const response = await fetch(path, {
    method: options.method || "GET",
    headers: { "Content-Type": "application/json" },
    body: options.body ? JSON.stringify(options.body) : undefined,
  });
  const data = await response.json().catch(() => ({}));
  if (!response.ok) {
    throw new Error(data.error || `HTTP ${response.status}`);
  }
  return data;
}

async function refreshStatus() {
  try {
    applyStatus(await requestJson("/api/status"));
  } catch {
    // SSE 会继续负责连接状态；状态轮询失败时不打断输入。
  }
}

function applyStatus(status) {
  if (!status || !Object.keys(status).length) return;
  state.busy = Boolean(status.busy);
  state.queuedCount = Number(status.queuedCount || 0);
  state.currentSessionId = status.sessionId || status.sessionName || state.currentSessionId;
  modelName.textContent = status.model || "model";
  sessionName.textContent = status.displayName || status.sessionName || "session";
  sendButton.disabled = false;
}

function setMetric(node, value) {
  node.textContent = value === undefined || value === null ? "-" : String(value);
}

function applyUsage(usage = {}) {
  setMetric(inputTokens, usage.inputTokens);
  setMetric(cacheTokens, usage.inputCacheTokens);
  setMetric(missTokens, usage.inputCacheMissTokens);
  setMetric(outputTokens, usage.outputTokens);
  setMetric(totalTokens, usage.totalTokens);
}

function applyContext(payload = {}) {
  setMetric(contextBefore, payload.beforeTokens);
  setMetric(contextAfter, payload.afterTokens);
  setMetric(contextMax, payload.maxContextTokens);
  setMetric(contextCompressed, payload.compressed ? "yes" : "no");
}

async function loadSessions() {
  try {
    applySessionPayload(await requestJson("/api/sessions"));
  } catch (error) {
    addMessage("error", error.message);
  }
}

function applySessionPayload(payload = {}) {
  if (payload.status) applyStatus(payload.status);
  state.currentSessionId = payload.currentSessionId || state.currentSessionId;
  state.sessions = Array.isArray(payload.sessions) ? payload.sessions : state.sessions;
  renderSessions();
}

function renderSessions() {
  sessionList.innerHTML = "";
  if (!state.sessions.length) {
    const empty = document.createElement("div");
    empty.className = "session-empty";
    empty.textContent = "暂无会话";
    sessionList.append(empty);
    return;
  }

  for (const session of state.sessions) {
    const row = document.createElement("article");
    row.className = `session-row${session.id === state.currentSessionId ? " active" : ""}`;
    row.dataset.id = session.id;
    row.innerHTML = `
      <button class="session-main" type="button" data-action="use" title="切换会话">
        <strong></strong>
        <span></span>
      </button>
      <div class="session-actions">
        <button class="session-action" type="button" data-action="rename" title="重命名" aria-label="重命名">R</button>
        <button class="session-action danger" type="button" data-action="archive" title="归档" aria-label="归档">x</button>
      </div>
    `;
    row.querySelector("strong").textContent = session.displayName || session.id;
    row.querySelector("span").textContent = `${session.id} · ${formatSessionTime(session.updatedAt)}`;
    sessionList.append(row);
  }
}

function formatSessionTime(timestamp) {
  if (!timestamp) return "";
  const date = new Date(timestamp);
  if (Number.isNaN(date.getTime())) return timestamp;
  return new Intl.DateTimeFormat("zh-CN", {
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
  }).format(date);
}

async function loadSessionMessages(sessionId) {
  try {
    const payload = await requestJson(`/api/sessions/${encodeURIComponent(sessionId)}/messages`);
    messagesEl.innerHTML = "";
    state.currentAssistant = null;
    state.currentReasoning = null;
    state.tools.clear();
    for (const message of payload.messages || []) {
      renderStoredMessage(message);
    }
  } catch (error) {
    addMessage("error", error.message);
  }
}

function renderStoredMessage(message = {}) {
  const toolCalls = Array.isArray(message.toolCalls) ? message.toolCalls : [];
  if (message.role === "assistant" && toolCalls.length) {
    for (const toolCall of toolCalls) {
      const block = addToolBlock({
        toolCallId: toolCall.id,
        toolName: toolCall.name,
        argumentsJson: toolCall.argumentsJson,
      });
      if (toolCall.id) state.tools.set(toolCall.id, block);
      setToolCollapsed(block, true, false);
    }
    return;
  }

  if (message.role === "tool") {
    const toolCallId = message.toolCallId || "";
    let block = state.tools.get(toolCallId);
    if (!block) {
      block = addToolBlock({ toolCallId, toolName: "tool" });
      if (toolCallId) state.tools.set(toolCallId, block);
    }
    updateToolBlock(block, { toolCallId, text: message.content || "" }, "done");
    return;
  }

  addMessage(normalizeStoredRole(message.role), message.content || "");
}

function normalizeStoredRole(role) {
  if (role === "user" || role === "assistant") return role;
  return "system";
}

async function createSession() {
  const displayName = window.prompt("会话名称", "");
  if (displayName === null) return;
  try {
    const payload = await postJson("/api/sessions", { displayName });
    applySessionPayload(payload);
    await loadSessionMessages(payload.currentSessionId);
  } catch (error) {
    addMessage("error", error.message);
  }
}

async function switchSession(sessionId) {
  if (!sessionId || sessionId === state.currentSessionId) return;
  try {
    const payload = await postJson("/api/sessions/use", { id: sessionId });
    applySessionPayload(payload);
    await loadSessionMessages(sessionId);
  } catch (error) {
    addMessage("error", error.message);
  }
}

async function renameSession(sessionId) {
  const session = state.sessions.find((item) => item.id === sessionId);
  const displayName = window.prompt("会话名称", session?.displayName || "");
  if (displayName === null) return;
  try {
    applySessionPayload(await postJson("/api/sessions/rename", { id: sessionId, displayName }));
  } catch (error) {
    addMessage("error", error.message);
  }
}

async function archiveSession(sessionId) {
  const session = state.sessions.find((item) => item.id === sessionId);
  if (!window.confirm(`归档会话「${session?.displayName || sessionId}」？`)) return;
  try {
    const payload = await postJson("/api/sessions/archive", { id: sessionId });
    applySessionPayload(payload);
    await loadSessionMessages(payload.currentSessionId);
  } catch (error) {
    addMessage("error", error.message);
  }
}

async function loadTodos() {
  try {
    renderTodos((await requestJson("/api/todos")).todos || []);
  } catch (error) {
    addMessage("error", error.message);
  }
}

function renderTodos(todos) {
  todoList.innerHTML = "";
  if (!todos.length) {
    const empty = document.createElement("div");
    empty.className = "todo-empty";
    empty.textContent = "暂无待办";
    todoList.append(empty);
    return;
  }
  for (const todo of todos) {
    const item = document.createElement("article");
    item.className = `todo-item ${String(todo.status || "").toLowerCase()}`;
    item.dataset.id = todo.id;
    item.innerHTML = `
      <label class="todo-check">
        <input type="checkbox" data-action="complete" ${todo.status === "COMPLETED" ? "checked" : ""} />
        <span></span>
      </label>
      <button class="todo-archive" type="button" data-action="archive" title="归档" aria-label="归档">x</button>
    `;
    item.querySelector("span").innerHTML = `
      <strong>${escapeHtml(todo.content || "")}</strong>
      <small>${escapeHtml(formatTodoMeta(todo))}</small>
    `;
    todoList.append(item);
  }
}

function formatTodoMeta(todo) {
  const parts = [];
  if (todo.priority) parts.push(todo.priority);
  if (todo.dueAt) parts.push(formatDateTime(todo.dueAt));
  if (todo.result) parts.push(todo.result);
  return parts.join(" · ");
}

function formatDateTime(value) {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return new Intl.DateTimeFormat("zh-CN", {
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
  }).format(date);
}

function todoDueAtValue() {
  if (!todoDueAt.value) return "";
  const date = new Date(todoDueAt.value);
  return Number.isNaN(date.getTime()) ? "" : date.toISOString();
}

async function createTodoFromForm(event) {
  event.preventDefault();
  const content = todoContent.value.trim();
  if (!content) return;
  try {
    const payload = await requestJson("/api/todos", {
      method: "POST",
      body: {
        content,
        dueAt: todoDueAtValue(),
        priority: "medium",
      },
    });
    renderTodos(payload.todos || []);
    todoContent.value = "";
    todoDueAt.value = "";
  } catch (error) {
    addMessage("error", error.message);
  }
}

async function completeTodo(todoId) {
  try {
    renderTodos((await postJson("/api/todos/complete", { id: todoId, result: "Web 手动完成" })).todos || []);
  } catch (error) {
    addMessage("error", error.message);
  }
}

async function archiveTodo(todoId) {
  try {
    renderTodos((await postJson("/api/todos/archive", { id: todoId })).todos || []);
  } catch (error) {
    addMessage("error", error.message);
  }
}

function handleAgentEvent(event) {
  const type = event.type;
  const payload = event.payload || {};

  if (type === "RunStarted") {
    state.currentAssistant = null;
    state.currentReasoning = null;
    state.tools.clear();
    state.approvals.clear();
    state.teamMembers.clear();
    state.busy = true;
    return;
  }
  if (type === "RunQueued") {
    state.queuedCount = payload.queueSize || 0;
    addMessage("system", `已进入队列：${payload.userInput || ""}`);
    return;
  }
  if (type === "AssistantToken") {
    if (!state.currentAssistant) state.currentAssistant = addMessage("assistant", "");
    updateMessage(state.currentAssistant, state.currentAssistant.text + (payload.text || ""));
    return;
  }
  if (type === "ReasoningToken") {
    if (!state.currentReasoning) state.currentReasoning = addMessage("thinking", "");
    updateMessage(state.currentReasoning, state.currentReasoning.text + (payload.text || ""));
    return;
  }
  if (type === "MessageFinished" && payload.role === "assistant" && payload.hasToolCalls) {
    if (state.currentAssistant) {
      state.currentAssistant.node.remove();
      state.currentAssistant = null;
    }
    return;
  }
  if (type === "ToolApprovalRequested") {
    const block = addApprovalBlock(payload);
    if (payload.approvalId) state.approvals.set(payload.approvalId, block);
    return;
  }
  if (type === "ToolApprovalResolved") {
    const block = state.approvals.get(payload.approvalId);
    if (block) {
      updateApprovalBlock(block, payload, payload);
    } else {
      addMessage("system", `工具审批${payload.approved ? "通过" : "拒绝"}：${payload.toolName || ""}`);
    }
    return;
  }
  if (type === "TeamRunStarted") {
    state.teamMembers.clear();
    addMessage("system", `Agent Team 开始：${payload.task || ""}\nmode=${payload.mode || "explore"}`);
    state.busy = true;
    return;
  }
  if (type === "TeamMemberStarted") {
    ensureTeamMemberMessage(payload, payload.description || "");
    return;
  }
  if (type === "TeamMemberToken") {
    const block = ensureTeamMemberMessage(payload, "");
    updateMessage(block, block.text + (payload.text || ""));
    return;
  }
  if (type === "TeamMemberFinished") {
    const stateText = payload.success ? "完成" : "失败";
    addMessage(
      "system",
      `Team 成员${stateText}：${payload.taskId || ""} ${payload.role || ""} · ${payload.elapsedMillis || 0}ms\n${previewText(payload.text || "").text}`
    );
    return;
  }
  if (type === "TeamRunFinished") {
    state.busy = false;
    addMessage(
      payload.success ? "system" : "error",
      `Agent Team ${payload.success ? "完成" : "失败"} · ${payload.elapsedMillis || 0}ms\n${payload.summary || ""}`
    );
    refreshStatus();
    return;
  }
  if (type === "ToolCallStart") {
    const block = addToolBlock(payload);
    if (payload.toolCallId) state.tools.set(payload.toolCallId, block);
    return;
  }
  if (type === "ToolCallDone") {
    let block = state.tools.get(payload.toolCallId);
    if (!block) {
      block = addToolBlock(payload);
      if (payload.toolCallId) state.tools.set(payload.toolCallId, block);
    }
    updateToolBlock(block, payload, payload.success ? "done" : "failed");
    return;
  }
  if (type === "ContextBuilt") {
    applyContext(payload);
    return;
  }
  if (type === "UsageReported") {
    applyUsage(payload.usage || {});
    return;
  }
  if (type === "RunFailed") {
    addMessage("error", payload.errorMessage || "run failed");
    state.busy = false;
    return;
  }
  if (type === "RunStopped" || type === "Done" || type === "RunFinished") {
    state.busy = false;
    refreshStatus();
    loadSessions();
  }
}

function connectEvents() {
  const source = new EventSource("/api/events");
  source.addEventListener("hello", () => {
    connectionState.textContent = "connected";
    connectionState.className = "state-pill connected";
  });
  source.addEventListener("agent", (event) => {
    handleAgentEvent(JSON.parse(event.data));
  });
  source.addEventListener("notification", (event) => {
    const data = JSON.parse(event.data);
    addMessage("system", data.run?.message || data.type);
    loadTodos();
  });
  source.onerror = () => {
    connectionState.textContent = "reconnecting";
    connectionState.className = "state-pill error";
  };
}

document.querySelector("#composer").addEventListener("submit", async (event) => {
  event.preventDefault();
  const text = promptEl.value.trim();
  if (!text) return;
  promptEl.value = "";
  addMessage("user", text);
  sendButton.disabled = true;
  try {
    await postJson("/api/messages", { text });
  } catch (error) {
    addMessage("error", error.message);
  } finally {
    sendButton.disabled = false;
  }
});

stopButton.addEventListener("click", async () => {
  try {
    await postJson("/api/stop");
  } catch (error) {
    addMessage("error", error.message);
  }
});

promptEl.addEventListener("input", () => {
  promptEl.style.height = "auto";
  promptEl.style.height = `${Math.min(promptEl.scrollHeight, 150)}px`;
});

promptEl.addEventListener("keydown", (event) => {
  if (event.key !== "Enter" || event.shiftKey) {
    return;
  }
  event.preventDefault();
  document.querySelector("#composer").requestSubmit();
});

newSessionButton.addEventListener("click", createSession);
refreshTodosButton.addEventListener("click", loadTodos);
todoForm.addEventListener("submit", createTodoFromForm);

todoList.addEventListener("click", (event) => {
  const actionTarget = event.target.closest("[data-action]");
  const item = event.target.closest(".todo-item");
  if (!actionTarget || !item) return;
  if (actionTarget.dataset.action === "complete") {
    completeTodo(item.dataset.id);
  } else if (actionTarget.dataset.action === "archive") {
    archiveTodo(item.dataset.id);
  }
});

sessionList.addEventListener("click", (event) => {
  const actionButton = event.target.closest("[data-action]");
  const row = event.target.closest(".session-row");
  if (!actionButton || !row) return;

  const sessionId = row.dataset.id;
  const action = actionButton.dataset.action;
  if (action === "use") {
    switchSession(sessionId);
  } else if (action === "rename") {
    renameSession(sessionId);
  } else if (action === "archive") {
    archiveSession(sessionId);
  }
});

refreshStatus();
loadSessions();
loadTodos();
connectEvents();
setInterval(refreshStatus, 3000);
setInterval(loadSessions, 5000);
