const state = {
  view: "chat",
  busy: false,
  queuedCount: 0,
  currentSessionId: "",
  model: "",
  availableModels: [],
  visionImages: [],
  visionModel: "",
  visionAvailableModels: [],
  visionBusy: false,
  mcpServers: [],
  skills: [],
  sessions: [],
  sessionStatuses: {},
  schedules: [],
  ragSessions: [],
  currentRagSessionId: "",
  knowledgeBases: [],
  currentKbId: "default",
  ragDocuments: [],
  ragChatModel: "",
  ragAvailableChatModels: [],
  ragEmbeddingModel: "",
  ragTopK: 5,
  ragBusy: false,
  ragSources: [],
  rooms: [],
  currentRoomId: "",
  roomAgents: [],
  roomMembers: [],
  removedRoomMembers: [],
  availableRoomAgents: [],
  currentRoomAgentId: "",
  roomBusy: false,
  tools: new Map(),
  currentAssistant: null,
  currentReasoning: null,
  teamMembers: new Map(),
  approvals: new Map(),
  approvalMode: "manual",
  archiveSelections: new Set(),
};
const TOOL_PREVIEW_CHAR_LIMIT = 1200;
const TOOL_PREVIEW_LINE_LIMIT = 12;

const $ = (selector) => document.querySelector(selector);
const appShell = $(".app-shell");
const messagesEl = $("#messages");
const promptEl = $("#prompt");
const sendButton = $("#sendButton");
const attachImageButton = $("#attachImageButton");
const visionImageInput = $("#visionImageInput");
const visionImageTray = $("#visionImageTray");
const stopButton = $("#stopButton");
const chatViewButton = $("#chatViewButton");
const roomViewButton = $("#roomViewButton");
const ragViewButton = $("#ragViewButton");
const archiveViewButton = $("#archiveViewButton");
const chatLeftPanel = $("#chatLeftPanel");
const roomLeftPanel = $("#roomLeftPanel");
const ragLeftPanel = $("#ragLeftPanel");
const chatRightPanel = $("#chatRightPanel");
const roomRightPanel = $("#roomRightPanel");
const ragRightPanel = $("#ragRightPanel");
const roomEditorDetails = $("#roomEditorDetails");
const newSessionButton = $("#newSessionButton");
const toggleMcpButton = $("#toggleMcpButton");
const toggleSkillButton = $("#toggleSkillButton");
const mcpCapabilityPanel = $("#mcpCapabilityPanel");
const skillCapabilityPanel = $("#skillCapabilityPanel");
const sessionList = $("#sessionList");
const newRoomButton = $("#newRoomButton");
const roomList = $("#roomList");
const newRoomAgentButton = $("#newRoomAgentButton");
const roomAgentList = $("#roomAgentList");
const roomAgentForm = $("#roomAgentForm");
const roomAgentId = $("#roomAgentId");
const roomAgentName = $("#roomAgentName");
const roomAgentRole = $("#roomAgentRole");
const roomAgentModel = $("#roomAgentModel");
const roomAgentAliases = $("#roomAgentAliases");
const roomAgentTools = $("#roomAgentTools");
const roomAgentDescription = $("#roomAgentDescription");
const roomAgentPrompt = $("#roomAgentPrompt");
const roomAgentEnabled = $("#roomAgentEnabled");
const archiveRoomAgentButton = $("#archiveRoomAgentButton");
const refreshRoomMembersButton = $("#refreshRoomMembersButton");
const roomMemberList = $("#roomMemberList");
const roomMemberSelect = $("#roomMemberSelect");
const addRoomMemberButton = $("#addRoomMemberButton");
const connectionState = $("#connectionState");
const modelSelect = $("#modelSelect");
const inputTokens = $("#inputTokens");
const cacheTokens = $("#cacheTokens");
const missTokens = $("#missTokens");
const outputTokens = $("#outputTokens");
const totalTokens = $("#totalTokens");
const contextUsedPercent = $("#contextUsedPercent");
const contextUsedTokens = $("#contextUsedTokens");
const contextTotalTokens = $("#contextTotalTokens");
const approvalManualButton = $("#approvalManualButton");
const approvalAutoButton = $("#approvalAutoButton");
const mcpServerList = $("#mcpServerList");
const skillList = $("#skillList");
const todoForm = $("#todoForm");
const todoContent = $("#todoContent");
const todoDueAt = $("#todoDueAt");
const newTodoButton = $("#newTodoButton");
const refreshTodosButton = $("#refreshTodosButton");
const todoList = $("#todoList");
const scheduleForm = $("#scheduleForm");
const scheduleName = $("#scheduleName");
const scheduleContent = $("#scheduleContent");
const scheduleType = $("#scheduleType");
const scheduleDailyFields = $("#scheduleDailyFields");
const scheduleOnceFields = $("#scheduleOnceFields");
const scheduleIntervalFields = $("#scheduleIntervalFields");
const scheduleDailyTime = $("#scheduleDailyTime");
const scheduleTimezone = $("#scheduleTimezone");
const scheduleRunAt = $("#scheduleRunAt");
const scheduleIntervalSeconds = $("#scheduleIntervalSeconds");
const newScheduleButton = $("#newScheduleButton");
const refreshSchedulesButton = $("#refreshSchedulesButton");
const scheduleList = $("#scheduleList");
const newRagSessionButton = $("#newRagSessionButton");
const ragSessionList = $("#ragSessionList");
const newRagKbButton = $("#newRagKbButton");
const ragKbSelect = $("#ragKbSelect");
const ragFileInput = $("#ragFileInput");
const uploadRagDocumentButton = $("#uploadRagDocumentButton");
const ragDocumentList = $("#ragDocumentList");
const ragChatModel = $("#ragChatModel");
const ragEmbeddingModel = $("#ragEmbeddingModel");
const ragTopK = $("#ragTopK");
const ragSourceList = $("#ragSourceList");

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
  if (role === "agent") return "AGENT";
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
  block.approve.addEventListener("click", () => resolveApproval(payload.approvalId, true, payload.sessionId));
  block.deny.addEventListener("click", () => resolveApproval(payload.approvalId, false, payload.sessionId));
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

async function resolveApproval(approvalId, approved, sessionId = state.currentSessionId) {
  if (!approvalId) return;
  try {
    await postJson(approved ? "/api/approvals/approve" : "/api/approvals/deny", { id: approvalId, sessionId });
  } catch (error) {
    addMessage("error", error.message);
  }
}

async function approveAllPendingApprovals(sessionId = state.currentSessionId, silent = false) {
  if (!sessionId) return;
  try {
    const payload = await postJson("/api/approvals/approve", { id: "", sessionId });
    if (!silent) addMessage("system", `已批量通过待审批工具：${payload.count || 0}`);
    if (payload.status) applyStatus(payload.status);
  } catch (error) {
    if (!silent) addMessage("error", error.message);
  }
}

function setApprovalMode(mode) {
  state.approvalMode = mode === "auto" ? "auto" : "manual";
  window.localStorage.setItem("asterApprovalMode", state.approvalMode);
  renderApprovalMode();
  if (state.approvalMode === "auto") {
    approveAllPendingApprovals(state.currentSessionId, true);
  }
}

function renderApprovalMode() {
  const auto = state.approvalMode === "auto";
  approvalManualButton.classList.toggle("active", !auto);
  approvalAutoButton.classList.toggle("active", auto);
  approvalManualButton.setAttribute("aria-pressed", String(!auto));
  approvalAutoButton.setAttribute("aria-pressed", String(auto));
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
  } else if ("busy" in data || "model" in data || "sessionId" in data || "queuedCount" in data) {
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
  if (Object.prototype.hasOwnProperty.call(status, "currentSessionId")) {
    state.currentSessionId = status.currentSessionId || "";
  } else {
    state.currentSessionId = status.sessionId || status.sessionName || state.currentSessionId;
  }
  state.sessionStatuses = status.sessionStatuses || state.sessionStatuses;
  const current = sessionRuntimeStatus(state.currentSessionId);
  state.busy = Boolean(current.busy ?? status.busy);
  state.queuedCount = Number(current.queuedCount ?? status.queuedCount ?? 0);
  if (Object.prototype.hasOwnProperty.call(status, "model")) {
    state.model = status.model || "";
  }
  state.availableModels = Array.isArray(status.availableModels) ? status.availableModels : state.availableModels;
  state.visionModel = status.multimodalModel || state.visionModel;
  state.visionAvailableModels = Array.isArray(status.multimodalAvailableModels)
    ? status.multimodalAvailableModels
    : state.visionAvailableModels;
  state.mcpServers = Array.isArray(status.mcpServers) ? status.mcpServers : state.mcpServers;
  state.skills = Array.isArray(status.skills) ? status.skills : state.skills;
  renderModelSelect();
  renderVisionImageTray();
  renderRoomAgentModelOptions();
  renderMcpServers();
  renderSkills();
  if (state.approvalMode === "auto" && Array.isArray(status.pendingApprovals) && status.pendingApprovals.length) {
    approveAllPendingApprovals(state.currentSessionId, true);
  }
  sendButton.disabled = false;
  renderSessions();
}

function renderModelSelect() {
  const ragMode = state.view === "rag";
  const visionMode = state.view === "chat" && (state.visionImages.length > 0 || state.visionBusy);
  const models = ragMode
    ? (state.ragAvailableChatModels.length ? state.ragAvailableChatModels : (state.ragChatModel ? [state.ragChatModel] : []))
    : (visionMode
      ? (state.visionAvailableModels.length ? state.visionAvailableModels : (state.visionModel ? [state.visionModel] : []))
      : (state.availableModels.length ? state.availableModels : (state.model ? [state.model] : [])));
  const selectedModel = ragMode ? state.ragChatModel : (visionMode ? state.visionModel : state.model);
  const mode = ragMode ? "rag" : (visionMode ? "vision" : "chat");
  const signature = `${mode}:${models.join("|")}`;
  modelSelect.setAttribute("aria-label", ragMode ? "切换 RAG 模型" : (visionMode ? "切换图片理解模型" : "切换 Chat 模型"));
  if (!models.length) {
    modelSelect.innerHTML = '<option value="">选择模型</option>';
    modelSelect.dataset.signature = "";
    modelSelect.value = "";
    return;
  }
  if (modelSelect.dataset.signature !== signature) {
    modelSelect.innerHTML = models
      .map((model) => `<option value="${escapeHtml(model)}">${escapeHtml(model)}</option>`)
      .join("");
    modelSelect.dataset.signature = signature;
  }
  modelSelect.value = selectedModel || models[0] || "";
}

function renderRoomAgentModelOptions() {
  if (!roomAgentModel) return;
  const models = state.availableModels.length ? state.availableModels : [state.model || "deepseek-v4-flash"];
  const signature = models.join("|");
  if (roomAgentModel.dataset.signature !== signature) {
    roomAgentModel.innerHTML = models
      .map((model) => `<option value="${escapeHtml(model)}">${escapeHtml(model)}</option>`)
      .join("");
    roomAgentModel.dataset.signature = signature;
  }
  if (!roomAgentModel.value && models.length) {
    roomAgentModel.value = state.model || models[0];
  }
}

function setMetric(node, value) {
  node.textContent = value === undefined || value === null ? "-" : String(value);
}

function compactNumber(value) {
  const number = Number(value);
  if (!Number.isFinite(number)) return "-";
  if (Math.abs(number) >= 1000) {
    const rounded = number / 1000;
    return `${Number.isInteger(rounded) ? rounded.toFixed(0) : rounded.toFixed(1)}k`;
  }
  return String(number);
}

function applyUsage(usage = {}) {
  setMetric(inputTokens, usage.inputTokens);
  setMetric(cacheTokens, usage.inputCacheTokens);
  setMetric(missTokens, usage.inputCacheMissTokens);
  setMetric(outputTokens, usage.outputTokens);
  setMetric(totalTokens, usage.totalTokens);
}

function applyContext(payload = {}) {
  const used = Number(payload.afterTokens ?? payload.beforeTokens);
  const max = Number(payload.maxContextTokens);
  if (Number.isFinite(used) && Number.isFinite(max) && max > 0) {
    contextUsedPercent.textContent = `${Math.min(100, Math.round((used / max) * 100))}%`;
    contextUsedTokens.textContent = compactNumber(used);
    contextTotalTokens.textContent = compactNumber(max);
    return;
  }
  contextUsedPercent.textContent = "-";
  contextUsedTokens.textContent = "-";
  contextTotalTokens.textContent = "-";
}

function renderMcpServers() {
  if (!mcpServerList) return;
  mcpServerList.innerHTML = "";
  if (!state.mcpServers.length) {
    const empty = document.createElement("div");
    empty.className = "mcp-empty";
    empty.textContent = "未加载 MCP";
    mcpServerList.append(empty);
    return;
  }
  for (const server of state.mcpServers) {
    const row = document.createElement("div");
    row.className = `mcp-row ${server.loaded ? "loaded" : "failed"}`;
    const detail = server.loaded
      ? "loaded"
      : (server.errorMessage || "load failed");
    row.innerHTML = `
      <strong>${escapeHtml(server.serverId || "mcp")}</strong>
      <small>${escapeHtml(detail)}</small>
    `;
    mcpServerList.append(row);
  }
}

function renderSkills() {
  if (!skillList) return;
  skillList.innerHTML = "";
  if (!state.skills.length) {
    const empty = document.createElement("div");
    empty.className = "mcp-empty";
    empty.textContent = "未加载 Skill";
    skillList.append(empty);
    return;
  }
  for (const skill of state.skills) {
    const row = document.createElement("div");
    row.className = "mcp-row skill-row loaded";
    row.innerHTML = `
      <strong>${escapeHtml(skill.name || "skill")}</strong>
    `;
    skillList.append(row);
  }
}

function toggleCapability(panel, button) {
  if (!panel || !button) return;
  const open = panel.classList.toggle("hidden");
  button.classList.toggle("active", !open);
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
  if (Object.prototype.hasOwnProperty.call(payload, "currentSessionId")) {
    state.currentSessionId = payload.currentSessionId || "";
  }
  state.sessions = Array.isArray(payload.sessions) ? payload.sessions : state.sessions;
  renderSessions();
}

function renderSessions() {
  sessionList.innerHTML = "";
  if (!state.sessions.length) {
    const empty = document.createElement("div");
    empty.className = "session-empty";
    empty.textContent = "暂无会话。输入消息发送后会自动创建，也可以点 + 新建。";
    sessionList.append(empty);
    renderNoSessionHint();
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
        <small class="session-runtime-status"></small>
      </button>
      <div class="session-actions">
        <button class="session-action" type="button" data-action="rename" title="重命名" aria-label="重命名">R</button>
        <button class="session-action danger" type="button" data-action="archive" title="归档" aria-label="归档">x</button>
      </div>
    `;
    row.querySelector("strong").textContent = session.displayName || session.id;
    row.querySelector("span").textContent = `${session.id} · ${formatSessionTime(session.updatedAt)}`;
    const badge = row.querySelector(".session-runtime-status");
    const label = formatSessionRuntimeStatus(sessionRuntimeStatus(session.id));
    badge.textContent = label;
    badge.hidden = !label;
    sessionList.append(row);
  }
}

function renderNoSessionHint() {
  if (state.view !== "chat" || state.currentSessionId || state.sessions.length) return;
  messagesEl.innerHTML = "";
  state.currentAssistant = null;
  state.currentReasoning = null;
  state.tools.clear();
  addMessage("system", "暂无会话。输入消息并发送会自动创建，也可以点击左侧 + 新建。");
}

async function loadRagStatus() {
  try {
    applyRagPayload(await requestJson("/api/rag/status"));
    if (state.currentKbId && state.currentKbId !== "default") {
      await loadRagDocuments();
    }
  } catch (error) {
    addMessage("error", error.message);
  }
}

function applyRagPayload(payload = {}) {
  if (Array.isArray(payload.sessions)) state.ragSessions = payload.sessions;
  if (Object.prototype.hasOwnProperty.call(payload, "currentSessionId")) {
    state.currentRagSessionId = payload.currentSessionId || "";
  }
  if (Array.isArray(payload.knowledgeBases)) {
    state.knowledgeBases = payload.knowledgeBases;
    if (!state.currentKbId && state.knowledgeBases.length) {
      state.currentKbId = state.knowledgeBases[0].kbId;
    }
  }
  if (Array.isArray(payload.ragAvailableChatModels)) state.ragAvailableChatModels = payload.ragAvailableChatModels;
  if (Array.isArray(payload.documents)) state.ragDocuments = payload.documents;
  if (Object.prototype.hasOwnProperty.call(payload, "ragChatModel")) {
    const defaultRagModel = payload.ragChatModel || "";
    if (!state.ragChatModel || (state.ragAvailableChatModels.length && !state.ragAvailableChatModels.includes(state.ragChatModel))) {
      state.ragChatModel = defaultRagModel;
    }
  }
  if (Object.prototype.hasOwnProperty.call(payload, "ragEmbeddingModel")) state.ragEmbeddingModel = payload.ragEmbeddingModel || "";
  if (Object.prototype.hasOwnProperty.call(payload, "ragTopK")) state.ragTopK = payload.ragTopK || 5;
  renderRagSessions();
  renderKnowledgeBases();
  renderRagDocuments();
  renderRagStatus();
}

function renderRagSessions() {
  ragSessionList.innerHTML = "";
  if (!state.ragSessions.length) {
    const empty = document.createElement("div");
    empty.className = "session-empty";
    empty.textContent = "暂无知识库问答。输入问题发送后会自动创建，也可以点 + 新建。";
    ragSessionList.append(empty);
    renderNoRagSessionHint();
    return;
  }
  for (const session of state.ragSessions) {
    const row = document.createElement("article");
    row.className = `session-row${session.id === state.currentRagSessionId ? " active" : ""}`;
    row.dataset.id = session.id;
    row.innerHTML = `
      <button class="session-main" type="button" data-action="use" title="切换 RAG 会话">
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
    ragSessionList.append(row);
  }
}

function renderNoRagSessionHint() {
  if (state.view !== "rag" || state.currentRagSessionId || state.ragSessions.length) return;
  messagesEl.innerHTML = "";
  addMessage("system", "暂无知识库问答。上传文档后输入问题，系统会自动创建 RAG 会话并流式回答。");
}

function renderKnowledgeBases() {
  ragKbSelect.innerHTML = "";
  if (!state.knowledgeBases.length) {
    const option = document.createElement("option");
    option.value = "";
    option.textContent = "暂无知识库";
    ragKbSelect.append(option);
    return;
  }
  for (const kb of state.knowledgeBases) {
    const option = document.createElement("option");
    option.value = kb.kbId;
    option.textContent = kb.name || kb.kbId;
    ragKbSelect.append(option);
  }
  if (!state.currentKbId || !state.knowledgeBases.some((kb) => kb.kbId === state.currentKbId)) {
    state.currentKbId = state.knowledgeBases[0].kbId;
  }
  ragKbSelect.value = state.currentKbId;
}

function renderRagDocuments() {
  ragDocumentList.innerHTML = "";
  if (!state.ragDocuments.length) {
    const empty = document.createElement("div");
    empty.className = "session-empty";
    empty.textContent = "暂无文档";
    ragDocumentList.append(empty);
    return;
  }
  for (const documentItem of state.ragDocuments) {
    const row = document.createElement("article");
    row.className = "session-row rag-document-row";
    row.innerHTML = `
      <div class="session-main">
        <strong></strong>
        <span></span>
      </div>
    `;
    row.querySelector("strong").textContent = documentItem.fileName || documentItem.docId;
    row.querySelector("span").textContent = `${documentItem.chunkCount || 0} chunks · ${formatSessionTime(documentItem.updatedAt)}`;
    ragDocumentList.append(row);
  }
}

function renderRagStatus() {
  if (ragChatModel) ragChatModel.textContent = state.ragChatModel || "-";
  if (ragEmbeddingModel) ragEmbeddingModel.textContent = state.ragEmbeddingModel || "-";
  if (ragTopK) ragTopK.textContent = String(state.ragTopK || 5);
  renderModelSelect();
  renderRagSources(state.ragSources);
}

function renderRagSources(hits = []) {
  if (!ragSourceList) return;
  ragSourceList.innerHTML = "";
  if (!hits.length) {
    const empty = document.createElement("div");
    empty.className = "session-empty";
    empty.textContent = "暂无引用";
    ragSourceList.append(empty);
    return;
  }
  for (const hit of hits) {
    const row = document.createElement("details");
    row.className = "rag-source-item";
    row.innerHTML = `
      <summary>${escapeHtml(hit.sourceName || "source")} · chunk ${escapeHtml(hit.chunkIndex ?? "-")} · ${Number(hit.score || 0).toFixed(3)}</summary>
      <p>${escapeHtml(previewText(hit.text || "").text)}</p>
    `;
    ragSourceList.append(row);
  }
}

function sessionRuntimeStatus(sessionId) {
  return state.sessionStatuses?.[sessionId] || { sessionId, active: false, busy: false, queuedCount: 0, pendingApprovalCount: 0 };
}

function formatSessionRuntimeStatus(status = {}) {
  if (Number(status.pendingApprovalCount || 0) > 0) return `approval ${status.pendingApprovalCount}`;
  if (status.pendingPlan) return "plan";
  if (Number(status.queuedCount || 0) > 0) return `queued ${status.queuedCount}`;
  if (status.busy) return "running";
  return "";
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
  if (!sessionId) {
    renderNoSessionHint();
    return;
  }
  try {
    const payload = await requestJson(`/api/sessions/${encodeURIComponent(sessionId)}/messages`);
    messagesEl.innerHTML = "";
    state.currentAssistant = null;
    state.currentReasoning = null;
    state.tools.clear();
    state.teamMembers.clear();
    state.approvals.clear();
    for (const message of payload.messages || []) {
      renderStoredMessage(message);
    }
  } catch (error) {
    addMessage("error", error.message);
  }
}

async function loadRagMessages(sessionId) {
  if (!sessionId) {
    renderNoRagSessionHint();
    return;
  }
  try {
    const payload = await requestJson(`/api/rag/sessions/${encodeURIComponent(sessionId)}/messages`);
    messagesEl.innerHTML = "";
    state.currentAssistant = null;
    state.ragSources = [];
    for (const message of payload.messages || []) {
      renderStoredRagMessage(message);
    }
    renderRagSources(state.ragSources);
  } catch (error) {
    addMessage("error", error.message);
  }
}

function renderStoredRagMessage(message = {}) {
  if (message.type === "user") {
    addMessage("user", message.content || "");
    return;
  }
  if (message.type === "assistant") {
    const hits = Array.isArray(message.hits) ? message.hits : [];
    state.ragSources = hits;
    addMessage("assistant", formatRagAnswer(message.content || "", hits));
    return;
  }
  addMessage("system", message.content || "");
}

async function createRagSession() {
  const displayName = window.prompt("RAG 会话名称", "");
  if (displayName === null) return;
  try {
    applyRagPayload(await postJson("/api/rag/sessions", { displayName }));
    await loadRagMessages(state.currentRagSessionId);
  } catch (error) {
    addMessage("error", error.message);
  }
}

async function switchRagSession(sessionId) {
  if (!sessionId || sessionId === state.currentRagSessionId) return;
  try {
    applyRagPayload(await postJson("/api/rag/sessions/use", { id: sessionId }));
    await loadRagMessages(sessionId);
  } catch (error) {
    addMessage("error", error.message);
  }
}

async function renameRagSession(sessionId) {
  const session = state.ragSessions.find((item) => item.id === sessionId);
  const displayName = window.prompt("RAG 会话名称", session?.displayName || "");
  if (displayName === null) return;
  try {
    applyRagPayload(await postJson("/api/rag/sessions/rename", { id: sessionId, displayName }));
  } catch (error) {
    addMessage("error", error.message);
  }
}

async function archiveRagSession(sessionId) {
  const session = state.ragSessions.find((item) => item.id === sessionId);
  if (!window.confirm(`归档 RAG 会话「${session?.displayName || sessionId}」？`)) return;
  try {
    applyRagPayload(await postJson("/api/rag/sessions/archive", { id: sessionId }));
    messagesEl.innerHTML = "";
    if (state.currentRagSessionId) {
      await loadRagMessages(state.currentRagSessionId);
    } else {
      renderNoRagSessionHint();
    }
  } catch (error) {
    addMessage("error", error.message);
  }
}

async function createKnowledgeBase() {
  const name = window.prompt("知识库名称", "");
  if (name === null) return;
  try {
    const payload = await postJson("/api/rag/kbs", { name });
    applyRagPayload(payload);
    state.currentKbId = payload.knowledgeBases?.[0]?.kbId || state.currentKbId;
    await loadRagDocuments();
  } catch (error) {
    addMessage("error", error.message);
  }
}

async function loadRagDocuments() {
  if (!state.currentKbId) return;
  try {
    const payload = await requestJson(`/api/rag/documents?kbId=${encodeURIComponent(state.currentKbId)}`);
    applyRagPayload(payload);
  } catch (error) {
    addMessage("error", error.message);
  }
}

async function uploadRagDocument() {
  const file = ragFileInput.files?.[0];
  if (!file) {
    addMessage("error", "请选择要上传的 PDF / Markdown / 文本文件");
    return;
  }
  uploadRagDocumentButton.disabled = true;
  try {
    const contentBase64 = await fileToBase64(file);
    const payload = await postJson("/api/rag/documents", {
      kbId: state.currentKbId || "default",
      fileName: file.name,
      contentType: file.type || "",
      contentBase64,
    });
    applyRagPayload(payload);
    ragFileInput.value = "";
    addMessage("system", `文档已入库：${file.name}`);
  } catch (error) {
    addMessage("error", error.message);
  } finally {
    uploadRagDocumentButton.disabled = false;
  }
}

async function fileToBase64(file) {
  const buffer = await file.arrayBuffer();
  let binary = "";
  const bytes = new Uint8Array(buffer);
  const batchSize = 0x8000;
  for (let index = 0; index < bytes.length; index += batchSize) {
    binary += String.fromCharCode(...bytes.subarray(index, index + batchSize));
  }
  return btoa(binary);
}

async function addVisionImages(files) {
  const imageFiles = Array.from(files || []).filter((file) => file.type.startsWith("image/"));
  if (!imageFiles.length) return;
  const slots = Math.max(0, 4 - state.visionImages.length);
  const selected = imageFiles.slice(0, slots);
  for (const file of selected) {
    const contentBase64 = await fileToBase64(file);
    state.visionImages.push({
      id: `vision_${Date.now()}_${Math.random().toString(16).slice(2)}`,
      fileName: file.name || "image",
      mimeType: file.type || "image/png",
      contentBase64,
    });
  }
  visionImageInput.value = "";
  renderVisionImageTray();
  renderModelSelect();
}

function removeVisionImage(id) {
  state.visionImages = state.visionImages.filter((image) => image.id !== id);
  renderVisionImageTray();
  renderModelSelect();
}

function renderVisionImageTray() {
  if (!visionImageTray) return;
  const visible = state.view === "chat" && state.visionImages.length > 0;
  visionImageTray.hidden = !visible;
  if (!visible) {
    visionImageTray.innerHTML = "";
    return;
  }
  visionImageTray.innerHTML = state.visionImages
    .map((image) => `
      <span class="vision-chip" title="${escapeHtml(image.fileName)}">
        <span>${escapeHtml(image.fileName)}</span>
        <button type="button" data-remove-vision="${escapeHtml(image.id)}" aria-label="移除图片">×</button>
      </span>
    `)
    .join("");
}

function formatVisionUserMessage(question, images) {
  const names = images.map((image) => `- ${image.fileName || "image"}`).join("\n");
  return `${question || "请描述这张图片。"}\n\n**图片**\n${names}`;
}

async function sendVisionMessage(text) {
  const images = state.visionImages.slice();
  const question = text || "请描述这张图片。";
  state.visionBusy = true;
  state.visionImages = [];
  renderVisionImageTray();
  renderModelSelect();
  addMessage("user", formatVisionUserMessage(question, images));
  const assistant = addMessage("assistant", "");
  let answer = "";
  sendButton.disabled = true;
  try {
    const response = await fetch("/api/vision/chat", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        text: question,
        model: state.visionModel,
        images: images.map((image) => ({
          fileName: image.fileName,
          mimeType: image.mimeType,
          contentBase64: image.contentBase64,
        })),
      }),
    });
    if (!response.ok || !response.body) {
      const data = await response.json().catch(() => ({}));
      throw new Error(data.error || `HTTP ${response.status}`);
    }
    await readRagStream(response.body, {
      started: (data) => {
        state.visionModel = data.model || state.visionModel;
        renderModelSelect();
      },
      token: (data) => {
        answer += data.text || "";
        updateMessage(assistant, answer);
      },
      done: (data) => {
        answer = data.answer || answer;
        state.visionModel = data.model || state.visionModel;
        updateMessage(assistant, answer);
      },
      error: (data) => {
        addMessage("error", data.error || "图片理解失败");
      },
    });
  } catch (error) {
    addMessage("error", error.message);
  } finally {
    state.visionBusy = false;
    renderModelSelect();
    sendButton.disabled = false;
  }
}

async function sendRagQuestion(question) {
  addMessage("user", question);
  const assistant = addMessage("assistant", "");
  let answer = "";
  state.ragBusy = true;
  state.ragSources = [];
  sendButton.disabled = true;
  try {
    const response = await fetch("/api/rag/query", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        sessionId: state.currentRagSessionId,
        kbId: state.currentKbId || "default",
        question,
        topK: state.ragTopK || 5,
        chatModel: state.ragChatModel,
      }),
    });
    if (!response.ok || !response.body) {
      const data = await response.json().catch(() => ({}));
      throw new Error(data.error || `HTTP ${response.status}`);
    }
    await readRagStream(response.body, {
      started: (data) => {
        state.currentRagSessionId = data.session?.id || state.currentRagSessionId;
        state.ragChatModel = data.chatModel || state.ragChatModel;
        state.ragEmbeddingModel = data.embeddingModel || state.ragEmbeddingModel;
        state.ragSources = Array.isArray(data.hits) ? data.hits : [];
        renderRagStatus();
        renderRagSources(state.ragSources);
        renderRagSessions();
      },
      token: (data) => {
        answer += data.text || "";
        updateMessage(assistant, answer);
      },
      done: async (data) => {
        answer = data.answer || answer;
        state.currentRagSessionId = data.session?.id || state.currentRagSessionId;
        state.ragChatModel = data.chatModel || state.ragChatModel;
        state.ragEmbeddingModel = data.embeddingModel || state.ragEmbeddingModel;
        state.ragSources = Array.isArray(data.hits) ? data.hits : state.ragSources;
        updateMessage(assistant, formatRagAnswer(answer, state.ragSources));
        renderRagStatus();
        renderRagSources(state.ragSources);
        await loadRagStatus();
      },
      error: (data) => {
        addMessage("error", data.error || "RAG 查询失败");
      },
    });
  } catch (error) {
    addMessage("error", error.message);
  } finally {
    state.ragBusy = false;
    sendButton.disabled = false;
  }
}

async function readRagStream(body, handlers) {
  const reader = body.getReader();
  const decoder = new TextDecoder();
  let buffer = "";
  while (true) {
    const { value, done } = await reader.read();
    if (done) break;
    buffer += decoder.decode(value, { stream: true });
    let index;
    while ((index = buffer.indexOf("\n\n")) >= 0) {
      const block = buffer.slice(0, index);
      buffer = buffer.slice(index + 2);
      handleSseBlock(block, handlers);
    }
  }
  if (buffer.trim()) handleSseBlock(buffer, handlers);
}

function handleSseBlock(block, handlers) {
  let eventName = "message";
  const dataLines = [];
  for (const line of block.split("\n")) {
    if (line.startsWith("event:")) {
      eventName = line.slice("event:".length).trim();
    } else if (line.startsWith("data:")) {
      dataLines.push(line.slice("data:".length).trim());
    }
  }
  const raw = dataLines.join("\n");
  const data = raw ? JSON.parse(raw) : {};
  handlers[eventName]?.(data);
}

function formatRagAnswer(answer, hits = []) {
  if (!hits.length) return answer;
  const sources = hits
    .map((hit, index) => `${index + 1}. ${hit.sourceName || "source"} / chunk ${hit.chunkIndex ?? "-"} / score ${Number(hit.score || 0).toFixed(3)}`)
    .join("\n");
  return `${answer}\n\n---\n\n**引用来源**\n\n${sources}`;
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

function setView(view) {
  state.view = view;
  const room = view === "room";
  const rag = view === "rag";
  const archive = view === "archive";
  chatViewButton.classList.toggle("active", view === "chat");
  roomViewButton.classList.toggle("active", room);
  ragViewButton.classList.toggle("active", rag);
  archiveViewButton.classList.toggle("active", archive);
  appShell.classList.toggle("archive-mode", archive);
  chatLeftPanel.classList.toggle("hidden", room || rag || archive);
  roomLeftPanel.classList.toggle("hidden", !room || archive);
  ragLeftPanel.classList.toggle("hidden", !rag || archive);
  chatRightPanel.classList.toggle("hidden", room || rag || archive);
  roomRightPanel.classList.toggle("hidden", !room || archive);
  ragRightPanel.classList.toggle("hidden", !rag || archive);
  attachImageButton.hidden = view !== "chat";
  promptEl.placeholder = room
    ? "输入房间消息，使用 @Agent 或 @all 触发回复"
    : (rag ? "输入知识库问题" : "输入任务或问题");
  messagesEl.innerHTML = "";
  state.currentAssistant = null;
  state.currentReasoning = null;
  state.tools.clear();
  renderVisionImageTray();
  renderModelSelect();
  if (archive) {
    loadArchives();
  } else if (room) {
    loadRooms();
    loadRoomAgents();
  } else if (rag) {
    loadRagStatus().then(() => {
      if (state.currentRagSessionId) {
        loadRagMessages(state.currentRagSessionId);
      } else {
        renderNoRagSessionHint();
      }
    });
  } else if (state.currentSessionId) {
    loadSessionMessages(state.currentSessionId);
  } else {
    renderNoSessionHint();
  }
}

async function loadArchives() {
  try {
    renderArchives(await requestJson("/api/archives"));
  } catch (error) {
    addMessage("error", error.message);
  }
}

function renderArchives(payload = {}) {
  messagesEl.innerHTML = "";
  state.archiveSelections.clear();
  const board = document.createElement("section");
  board.className = "archive-board";
  board.append(archiveToolbar());
  board.append(
    archiveSection("Sessions", payload.sessions || [], "session", (item) => ({
      title: item.displayName || item.id,
      meta: `${item.id || ""} · ${formatSessionTime(item.updatedAt)}`
    })),
    archiveSection("RAG Sessions", payload.ragSessions || [], "rag-session", (item) => ({
      title: item.displayName || item.id,
      meta: `${item.id || ""} · ${formatSessionTime(item.updatedAt)}`
    })),
    archiveSection("Todos", payload.todos || [], "todo", (item) => ({
      title: item.content || item.id,
      meta: `${item.status || ""} · ${item.priority || ""} · ${formatSessionTime(item.updatedAt)}`
    })),
    archiveSection("Rooms", payload.rooms || [], "room", (item) => ({
      title: item.name || item.roomId,
      meta: `${item.roomId || ""} · ${item.topic || "未设置主题"} · ${formatSessionTime(item.updatedAt)}`
    })),
    archiveSection("Room Agents", payload.roomAgents || [], "room-agent", (item) => ({
      title: item.name || item.agentId,
      meta: `${item.agentId || ""} · ${item.role || ""} · ${formatSessionTime(item.updatedAt)}`
    }))
  );
  messagesEl.append(board);
}

function archiveToolbar() {
  const toolbar = document.createElement("section");
  toolbar.className = "archive-toolbar";
  toolbar.innerHTML = `
    <div>
      <strong>Archive</strong>
      <span id="archiveSelectedCount">已选 0</span>
    </div>
    <button id="deleteSelectedArchivesButton" class="ghost-button danger" type="button" data-action="delete-selected" disabled>批量物理删除</button>
  `;
  return toolbar;
}

function archiveSection(title, items, type, formatter) {
  const section = document.createElement("section");
  section.className = "archive-section";
  section.innerHTML = `
    <div class="archive-section-title">${escapeHtml(title)} · ${items.length}</div>
    <div class="archive-list"></div>
  `;
  const list = section.querySelector(".archive-list");
  if (!items.length) {
    const empty = document.createElement("div");
    empty.className = "session-empty";
    empty.textContent = "暂无归档";
    list.append(empty);
    return section;
  }
  for (const item of items) {
    const id = archiveItemId(type, item);
    const view = formatter(item);
    const row = document.createElement("article");
    row.className = "archive-item";
    row.dataset.type = type;
    row.dataset.id = id;
    row.innerHTML = `
      <label class="archive-select" title="选择归档项">
        <input type="checkbox" data-action="select-archive" />
      </label>
      <div class="archive-main">
        <strong></strong>
        <span></span>
      </div>
      <div class="archive-actions">
        <button type="button" data-action="restore">恢复</button>
        <button class="ghost-button danger" type="button" data-action="delete">物理删除</button>
      </div>
    `;
    row.querySelector("strong").textContent = view.title;
    row.querySelector("span").textContent = view.meta;
    list.append(row);
  }
  return section;
}

function archiveKey(type, id) {
  return `${type}:${id}`;
}

function updateArchiveSelectionView() {
  const count = state.archiveSelections.size;
  const countEl = $("#archiveSelectedCount");
  const button = $("#deleteSelectedArchivesButton");
  if (countEl) countEl.textContent = `已选 ${count}`;
  if (button) button.disabled = count === 0;
}

function toggleArchiveSelection(type, id, checked) {
  const key = archiveKey(type, id);
  if (checked) {
    state.archiveSelections.add(key);
  } else {
    state.archiveSelections.delete(key);
  }
  updateArchiveSelectionView();
}

function archiveItemId(type, item) {
  if (type === "session" || type === "rag-session" || type === "todo") return item.id || "";
  if (type === "room") return item.roomId || "";
  if (type === "room-agent") return item.agentId || "";
  return "";
}

async function restoreArchive(type, id) {
  if (!id) return;
  try {
    await postJson("/api/archives/restore", { type, id });
    await afterArchiveChanged();
  } catch (error) {
    addMessage("error", error.message);
  }
}

async function deleteArchive(type, id) {
  if (!id) return;
  if (!window.confirm(`物理删除 ${type} ${id}？这个操作不可恢复。`)) return;
  try {
    await postJson("/api/archives/delete", { type, id });
    await afterArchiveChanged();
  } catch (error) {
    addMessage("error", error.message);
  }
}

async function deleteSelectedArchives() {
  const items = Array.from(state.archiveSelections)
    .map((key) => {
      const index = key.indexOf(":");
      return index < 0 ? null : { type: key.slice(0, index), id: key.slice(index + 1) };
    })
    .filter((item) => item && item.type && item.id);
  if (!items.length) return;
  if (!window.confirm(`物理删除选中的 ${items.length} 个归档项？这个操作不可恢复。`)) return;
  try {
    await postJson("/api/archives/delete-batch", { items });
    state.archiveSelections.clear();
    await afterArchiveChanged();
  } catch (error) {
    addMessage("error", error.message);
  }
}

async function afterArchiveChanged() {
  await loadArchives();
  await loadSessions();
  await loadRagStatus();
  await loadTodos();
  await loadSchedules();
  await loadRooms();
  await loadRoomAgents();
}

async function loadRooms() {
  try {
    const payload = await requestJson("/api/rooms");
    state.rooms = Array.isArray(payload.rooms) ? payload.rooms : [];
    if (!state.currentRoomId && state.rooms.length) {
      state.currentRoomId = state.rooms[0].roomId;
    }
    if (state.currentRoomId && !state.rooms.some((room) => room.roomId === state.currentRoomId)) {
      state.currentRoomId = state.rooms[0]?.roomId || "";
    }
    renderRooms();
    if (state.view === "room" && state.currentRoomId) {
      await loadRoomMessages(state.currentRoomId);
      await loadRoomMembers(state.currentRoomId);
    }
  } catch (error) {
    addMessage("error", error.message);
  }
}

function renderRooms() {
  roomList.innerHTML = "";
  if (!state.rooms.length) {
    const empty = document.createElement("div");
    empty.className = "session-empty";
    empty.textContent = "暂无房间";
    roomList.append(empty);
    return;
  }

  for (const room of state.rooms) {
    const row = document.createElement("article");
    row.className = `session-row${room.roomId === state.currentRoomId ? " active" : ""}`;
    row.dataset.id = room.roomId;
    row.innerHTML = `
      <button class="session-main" type="button" data-action="use" title="切换房间">
        <strong></strong>
        <span></span>
      </button>
      <div class="session-actions">
        <button class="session-action" type="button" data-action="rename" title="编辑" aria-label="编辑">R</button>
        <button class="session-action danger" type="button" data-action="archive" title="归档" aria-label="归档">x</button>
      </div>
    `;
    row.querySelector("strong").textContent = room.name || room.roomId;
    row.querySelector("span").textContent = `${room.topic || "未设置主题"} · ${formatSessionTime(room.updatedAt)}`;
    roomList.append(row);
  }
}

async function createRoom() {
  const name = window.prompt("聊天室名称", "");
  if (name === null) return;
  try {
    const payload = await postJson("/api/rooms", { name });
    state.rooms = payload.rooms || [];
    state.currentRoomId = state.rooms[0]?.roomId || "";
    renderRooms();
    await loadRoomMessages(state.currentRoomId);
    await loadRoomMembers(state.currentRoomId);
  } catch (error) {
    addMessage("error", error.message);
  }
}

async function switchRoom(roomId) {
  if (!roomId || roomId === state.currentRoomId) return;
  state.currentRoomId = roomId;
  renderRooms();
  await loadRoomMessages(roomId);
  await loadRoomMembers(roomId);
}

async function updateRoom(roomId) {
  const room = state.rooms.find((item) => item.roomId === roomId);
  const name = window.prompt("聊天室名称", room?.name || "");
  if (name === null) return;
  const topic = window.prompt("房间主题", room?.topic || "");
  if (topic === null) return;
  try {
    const payload = await postJson("/api/rooms/update", { roomId, name, topic });
    state.rooms = payload.rooms || [];
    renderRooms();
    await loadRoomMembers(roomId);
  } catch (error) {
    addMessage("error", error.message);
  }
}

async function archiveRoom(roomId) {
  const room = state.rooms.find((item) => item.roomId === roomId);
  if (!window.confirm(`归档聊天室「${room?.name || roomId}」？`)) return;
  try {
    const payload = await postJson("/api/rooms/archive", { roomId });
    state.rooms = payload.rooms || [];
    state.currentRoomId = state.rooms[0]?.roomId || "";
    renderRooms();
    messagesEl.innerHTML = "";
    if (state.currentRoomId) {
      await loadRoomMessages(state.currentRoomId);
      await loadRoomMembers(state.currentRoomId);
    } else {
      applyRoomMemberPayload({});
    }
  } catch (error) {
    addMessage("error", error.message);
  }
}

async function loadRoomMembers(roomId = state.currentRoomId) {
  if (!roomId) {
    applyRoomMemberPayload({});
    return;
  }
  try {
    const payload = await requestJson(`/api/rooms/${encodeURIComponent(roomId)}/members`);
    applyRoomMemberPayload(payload);
  } catch (error) {
    addMessage("error", error.message);
  }
}

function applyRoomMemberPayload(payload = {}) {
  state.roomMembers = Array.isArray(payload.members) ? payload.members : [];
  state.removedRoomMembers = Array.isArray(payload.removed) ? payload.removed : [];
  state.availableRoomAgents = Array.isArray(payload.availableAgents) ? payload.availableAgents : [];
  renderRoomMembers();
}

function renderRoomMembers() {
  roomMemberList.innerHTML = "";
  const members = state.roomMembers || [];
  const removed = state.removedRoomMembers || [];
  if (!members.length && !removed.length) {
    const empty = document.createElement("div");
    empty.className = "session-empty";
    empty.textContent = "暂无成员";
    roomMemberList.append(empty);
  }

  for (const member of members) {
    roomMemberList.append(roomMemberRow(member, "active"));
  }
  for (const member of removed) {
    roomMemberList.append(roomMemberRow(member, "removed"));
  }

  roomMemberSelect.innerHTML = "";
  if (!state.availableRoomAgents.length) {
    const option = document.createElement("option");
    option.value = "";
    option.textContent = "没有可加入的 Agent";
    roomMemberSelect.append(option);
    addRoomMemberButton.disabled = true;
    return;
  }
  for (const agent of state.availableRoomAgents) {
    const option = document.createElement("option");
    option.value = agent.agentId;
    option.textContent = `${agent.name || agent.agentId} · ${agent.role || ""}`;
    roomMemberSelect.append(option);
  }
  addRoomMemberButton.disabled = false;
}

function roomMemberRow(member, status) {
  const agent = member.agent || {};
  const row = document.createElement("article");
  row.className = `member-row ${status}`;
  row.dataset.agentId = member.agentId || agent.agentId || "";
  row.innerHTML = `
    <div class="member-main">
      <strong></strong>
      <span></span>
    </div>
    <div class="member-actions"></div>
  `;
  row.querySelector("strong").textContent = `${member.orderIndex ?? "-"} · @${agent.name || member.agentId || ""}`;
  row.querySelector("span").textContent = `${agent.role || ""} · g${member.generation || 1}${status === "removed" ? " · 已移除" : ""}`;
  const actions = row.querySelector(".member-actions");
  const button = document.createElement("button");
  button.type = "button";
  button.dataset.action = status === "removed" ? "restore-member" : "archive-member";
  button.className = status === "removed" ? "session-action" : "session-action danger";
  button.textContent = status === "removed" ? "↩" : "x";
  button.title = status === "removed" ? "恢复到聊天室" : "从聊天室移除";
  actions.append(button);
  return row;
}

async function addRoomMember() {
  const agentId = roomMemberSelect.value;
  if (!state.currentRoomId || !agentId) return;
  try {
    applyRoomMemberPayload(await postJson(`/api/rooms/${encodeURIComponent(state.currentRoomId)}/members`, { agentId }));
  } catch (error) {
    addMessage("error", error.message);
  }
}

async function archiveRoomMember(agentId) {
  if (!state.currentRoomId || !agentId) return;
  const member = state.roomMembers.find((item) => item.agentId === agentId);
  const name = member?.agent?.name || agentId;
  if (!window.confirm(`从当前聊天室移除「${name}」？恢复后会使用新的私有上下文。`)) return;
  try {
    applyRoomMemberPayload(await postJson(`/api/rooms/${encodeURIComponent(state.currentRoomId)}/members/archive`, { agentId }));
  } catch (error) {
    addMessage("error", error.message);
  }
}

async function restoreRoomMember(agentId) {
  if (!state.currentRoomId || !agentId) return;
  try {
    applyRoomMemberPayload(await postJson(`/api/rooms/${encodeURIComponent(state.currentRoomId)}/members/restore`, { agentId }));
  } catch (error) {
    addMessage("error", error.message);
  }
}

async function loadRoomMessages(roomId) {
  if (!roomId) return;
  try {
    const payload = await requestJson(`/api/rooms/${encodeURIComponent(roomId)}/messages`);
    messagesEl.innerHTML = "";
    for (const message of payload.messages || []) {
      renderRoomMessage(message);
    }
  } catch (error) {
    addMessage("error", error.message);
  }
}

function renderRoomMessage(message = {}) {
  const type = String(message.speakerType || "").toLowerCase();
  if (type === "agent") {
    const block = addMessage("agent", message.content || "");
    block.node.querySelector(".message-header").textContent = `${message.speakerName || "Agent"}${message.speakerRole ? ` · ${message.speakerRole}` : ""}`;
    return;
  }
  if (type === "system") {
    addMessage("system", message.content || "");
    return;
  }
  addMessage("user", message.content || "");
}

async function sendRoomMessage(text) {
  if (!state.currentRoomId) {
    addMessage("error", "请先创建聊天室");
    return;
  }
  renderRoomMessage({ speakerType: "USER", content: text });
  state.roomBusy = true;
  sendButton.disabled = true;
  try {
    const payload = await postJson(`/api/rooms/${encodeURIComponent(state.currentRoomId)}/messages`, { text });
    for (const message of payload.messages || []) {
      if (message.speakerType === "USER" && message.content === text) {
        continue;
      }
      renderRoomMessage(message);
    }
    if (payload.room) {
      const index = state.rooms.findIndex((room) => room.roomId === payload.room.roomId);
      if (index >= 0) state.rooms[index] = payload.room;
      renderRooms();
    }
  } catch (error) {
    addMessage("error", error.message);
  } finally {
    state.roomBusy = false;
    sendButton.disabled = false;
  }
}

async function loadRoomAgents() {
  try {
    const payload = await requestJson("/api/room-agents");
    state.roomAgents = Array.isArray(payload.agents) ? payload.agents : [];
    if (!state.currentRoomAgentId && state.roomAgents.length) {
      state.currentRoomAgentId = state.roomAgents[0].agentId;
      fillRoomAgentForm(state.roomAgents[0]);
    }
    if (state.currentRoomAgentId && !state.roomAgents.some((agent) => agent.agentId === state.currentRoomAgentId)) {
      state.currentRoomAgentId = state.roomAgents[0]?.agentId || "";
      fillRoomAgentForm(state.roomAgents[0] || null);
    }
    renderRoomAgents();
    if (state.view === "room" && state.currentRoomId) {
      await loadRoomMembers(state.currentRoomId);
    }
  } catch (error) {
    addMessage("error", error.message);
  }
}

function renderRoomAgents() {
  roomAgentList.innerHTML = "";
  if (!state.roomAgents.length) {
    const empty = document.createElement("div");
    empty.className = "session-empty";
    empty.textContent = "暂无 Agent";
    roomAgentList.append(empty);
    return;
  }

  for (const agent of state.roomAgents) {
    const row = document.createElement("article");
    row.className = `session-row agent-row${agent.agentId === state.currentRoomAgentId ? " active" : ""}${agent.enabled ? "" : " disabled"}`;
    row.dataset.id = agent.agentId;
    row.innerHTML = `
      <button class="session-main" type="button" data-action="select" title="编辑 Agent">
        <strong></strong>
        <span></span>
      </button>
    `;
    row.querySelector("strong").textContent = `@${agent.name || agent.agentId}`;
    row.querySelector("span").textContent = `${agent.role || ""} · ${agent.model || ""} · ${(agent.mentionAliases || []).map((alias) => "@" + alias).join(" ")}`;
    roomAgentList.append(row);
  }
}

function fillRoomAgentForm(agent = null) {
  renderRoomAgentModelOptions();
  roomAgentId.value = agent?.agentId || "";
  roomAgentName.value = agent?.name || "";
  roomAgentRole.value = agent?.role || "";
  roomAgentModel.value = agent?.model || state.model || "deepseek-v4-flash";
  roomAgentAliases.value = (agent?.mentionAliases || []).join(", ");
  roomAgentTools.value = (agent?.toolAllowlist || ["read", "ls", "glob", "grep", "web_fetch", "web_search"]).join(", ");
  roomAgentDescription.value = agent?.description || "";
  roomAgentPrompt.value = agent?.systemPrompt || "";
  roomAgentEnabled.checked = agent?.enabled ?? true;
  archiveRoomAgentButton.disabled = !agent?.agentId;
}

function roomAgentInput() {
  return {
    agentId: roomAgentId.value.trim(),
    name: roomAgentName.value.trim(),
    role: roomAgentRole.value.trim(),
    description: roomAgentDescription.value.trim(),
    systemPrompt: roomAgentPrompt.value.trim(),
    mentionAliases: splitCsv(roomAgentAliases.value),
    toolAllowlist: splitCsv(roomAgentTools.value),
    model: roomAgentModel.value,
    enabled: roomAgentEnabled.checked,
  };
}

function splitCsv(value) {
  return String(value || "")
    .split(",")
    .map((item) => item.trim())
    .filter(Boolean);
}

async function saveRoomAgent(event) {
  event.preventDefault();
  const input = roomAgentInput();
  if (!input.name) {
    addMessage("error", "Agent 名称不能为空");
    return;
  }
  try {
    const path = input.agentId ? "/api/room-agents/update" : "/api/room-agents";
    const payload = await postJson(path, input);
    state.roomAgents = payload.agents || [];
    state.currentRoomAgentId = input.agentId || state.roomAgents[0]?.agentId || "";
    if (!input.agentId) {
      fillRoomAgentForm(state.roomAgents[0] || null);
    }
    renderRoomAgents();
    if (state.currentRoomId) await loadRoomMembers(state.currentRoomId);
  } catch (error) {
    addMessage("error", error.message);
  }
}

async function archiveRoomAgent() {
  const agentId = roomAgentId.value.trim();
  const agent = state.roomAgents.find((item) => item.agentId === agentId);
  if (!agentId || !window.confirm(`归档 Agent「${agent?.name || agentId}」？`)) return;
  try {
    const payload = await postJson("/api/room-agents/archive", { agentId });
    state.roomAgents = payload.agents || [];
    state.currentRoomAgentId = state.roomAgents[0]?.agentId || "";
    fillRoomAgentForm(state.roomAgents[0] || null);
    renderRoomAgents();
    if (state.currentRoomId) await loadRoomMembers(state.currentRoomId);
  } catch (error) {
    addMessage("error", error.message);
  }
}

async function createSession() {
  const displayName = window.prompt("会话名称", "");
  if (displayName === null) return;
  try {
    const payload = await postJson("/api/sessions", { displayName });
    applySessionPayload(payload);
    await loadSessionMessages(payload.currentSessionId);
    await loadSchedules();
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
    await loadSchedules();
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
    if (payload.currentSessionId) {
      await loadSessionMessages(payload.currentSessionId);
    } else {
      renderNoSessionHint();
    }
    await loadSchedules();
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
    const content = todo.content || "";
    const meta = formatTodoMeta(todo);
    item.innerHTML = `
      <div class="todo-item-main">
        <label class="todo-check">
          <input type="checkbox" data-action="complete" ${todo.status === "COMPLETED" ? "checked" : ""} />
          <span>
            <strong title="${escapeHtml(content)}">${escapeHtml(compactRailText(content, 42))}</strong>
            ${meta ? `<small>${escapeHtml(meta)}</small>` : ""}
          </span>
        </label>
        <div class="rail-item-actions">
          <button class="rail-item-toggle" type="button" data-action="toggle" title="展开详情" aria-label="展开详情" aria-expanded="false">+</button>
          <button class="todo-archive" type="button" data-action="archive" title="归档" aria-label="归档">x</button>
        </div>
      </div>
      <div class="collapsible-item-body">
        <p>${escapeHtml(content)}</p>
        ${meta ? `<small>${escapeHtml(meta)}</small>` : ""}
      </div>
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

function compactRailText(value, limit = 72) {
  const text = String(value || "").replace(/\s+/g, " ").trim();
  return text.length > limit ? `${text.slice(0, limit)}...` : text;
}

function setTodoFormOpen(open) {
  todoForm.classList.toggle("hidden", !open);
  newTodoButton.classList.toggle("active", open);
  newTodoButton.textContent = open ? "-" : "+";
  newTodoButton.setAttribute("aria-expanded", String(open));
  if (open) todoContent.focus();
}

function setScheduleFormOpen(open) {
  scheduleForm.classList.toggle("hidden", !open);
  newScheduleButton.classList.toggle("active", open);
  newScheduleButton.textContent = open ? "-" : "+";
  newScheduleButton.setAttribute("aria-expanded", String(open));
  if (open) scheduleName.focus();
}

function toggleRailItem(item) {
  const expanded = !item.classList.contains("expanded");
  const button = item.querySelector('[data-action="toggle"]');
  item.classList.toggle("expanded", expanded);
  if (button) {
    button.textContent = expanded ? "-" : "+";
    button.setAttribute("aria-expanded", String(expanded));
    button.setAttribute("title", expanded ? "收起详情" : "展开详情");
    button.setAttribute("aria-label", expanded ? "收起详情" : "展开详情");
  }
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
    setTodoFormOpen(false);
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

async function loadSchedules() {
  try {
    const payload = await requestJson("/api/schedules");
    state.schedules = Array.isArray(payload.schedules) ? payload.schedules : [];
    renderSchedules();
  } catch (error) {
    addMessage("error", error.message);
  }
}

function renderSchedules() {
  scheduleList.innerHTML = "";
  if (!state.schedules.length) {
    const empty = document.createElement("div");
    empty.className = "todo-empty";
    empty.textContent = "暂无定时任务";
    scheduleList.append(empty);
    return;
  }
  for (const schedule of state.schedules) {
    const item = document.createElement("article");
    item.className = "schedule-item";
    item.dataset.id = schedule.id;
    const content = schedule.content || "";
    const meta = formatScheduleMeta(schedule);
    item.innerHTML = `
      <div class="schedule-item-head">
        <div class="schedule-main">
          <strong title="${escapeHtml(schedule.name || "定时任务")}">${escapeHtml(compactRailText(schedule.name || "定时任务", 28))}</strong>
          <small>${escapeHtml(meta)}</small>
        </div>
        <div class="rail-item-actions">
          <button class="rail-item-toggle" type="button" data-action="toggle" title="展开详情" aria-label="展开详情" aria-expanded="false">+</button>
          <button class="todo-archive" type="button" data-action="cancel" title="取消定时任务" aria-label="取消定时任务">x</button>
        </div>
      </div>
      <div class="collapsible-item-body">
        <p>${escapeHtml(content)}</p>
      </div>
    `;
    scheduleList.append(item);
  }
}

function formatScheduleMeta(schedule = {}) {
  const trigger = schedule.trigger || {};
  const parts = [formatScheduleTrigger(trigger)];
  if (schedule.nextRunAt) parts.push(`next ${formatDateTime(schedule.nextRunAt)}`);
  if (schedule.status) parts.push(schedule.status);
  return parts.filter(Boolean).join(" · ");
}

function formatScheduleTrigger(trigger = {}) {
  if (trigger.type === "daily") {
    return `每日 ${trigger.dailyTime || ""}${trigger.timezone ? ` ${trigger.timezone}` : ""}`.trim();
  }
  if (trigger.type === "once") {
    return `一次性 ${formatDateTime(trigger.runAt || "")}`.trim();
  }
  if (trigger.type === "interval") {
    return `每 ${trigger.intervalSeconds || "-"} 秒`;
  }
  return trigger.type || "schedule";
}

function updateScheduleFields() {
  const type = scheduleType.value;
  scheduleDailyFields.classList.toggle("hidden", type !== "daily");
  scheduleOnceFields.classList.toggle("hidden", type !== "once");
  scheduleIntervalFields.classList.toggle("hidden", type !== "interval");
}

function scheduleRunAtValue() {
  if (!scheduleRunAt.value) return "";
  const date = new Date(scheduleRunAt.value);
  return Number.isNaN(date.getTime()) ? "" : date.toISOString();
}

function scheduleInput() {
  const type = scheduleType.value;
  const input = {
    type,
    name: scheduleName.value.trim(),
    content: scheduleContent.value.trim(),
  };
  if (type === "daily") {
    input.dailyTime = scheduleDailyTime.value;
    input.timezone = scheduleTimezone.value.trim();
  } else if (type === "once") {
    input.runAt = scheduleRunAtValue();
  } else if (type === "interval") {
    input.intervalSeconds = Number(scheduleIntervalSeconds.value || 0);
  }
  return input;
}

async function createScheduleFromForm(event) {
  event.preventDefault();
  if (!state.currentSessionId) {
    addMessage("error", "请先创建或选择一个会话，再添加定时任务。");
    return;
  }
  const input = scheduleInput();
  if (!input.content) return;
  try {
    const payload = await requestJson("/api/schedules", {
      method: "POST",
      body: input,
    });
    state.schedules = payload.schedules || [];
    renderSchedules();
    scheduleName.value = "";
    scheduleContent.value = "";
    scheduleRunAt.value = "";
    scheduleIntervalSeconds.value = "";
    setScheduleFormOpen(false);
  } catch (error) {
    addMessage("error", error.message);
  }
}

async function cancelSchedule(scheduleId) {
  if (!scheduleId) return;
  const schedule = state.schedules.find((item) => item.id === scheduleId);
  if (!window.confirm(`取消定时任务「${schedule?.name || scheduleId}」？`)) return;
  try {
    const payload = await postJson("/api/schedules/cancel", { id: scheduleId });
    state.schedules = payload.schedules || [];
    renderSchedules();
  } catch (error) {
    addMessage("error", error.message);
  }
}

function eventSessionId(event = {}) {
  return event.meta?.sessionName || event.meta?.sessionId || "";
}

function mergeSessionRuntimeStatus(sessionId, patch = {}) {
  if (!sessionId) return;
  const previous = sessionRuntimeStatus(sessionId);
  state.sessionStatuses = {
    ...state.sessionStatuses,
    [sessionId]: {
      ...previous,
      sessionId,
      active: true,
      ...patch,
    },
  };
  if (sessionId === state.currentSessionId) {
    const current = sessionRuntimeStatus(sessionId);
    state.busy = Boolean(current.busy);
    state.queuedCount = Number(current.queuedCount || 0);
  }
  renderSessions();
}

function updateSessionRuntimeFromEvent(event = {}) {
  const sessionId = eventSessionId(event);
  if (!sessionId) return;
  const type = event.type;
  const payload = event.payload || {};
  if (["RunStarted", "TeamRunStarted", "PlanDraftStarted", "PlanExecutionStarted"].includes(type)) {
    mergeSessionRuntimeStatus(sessionId, { busy: true });
    return;
  }
  if (type === "RunQueued") {
    mergeSessionRuntimeStatus(sessionId, { busy: true, queuedCount: payload.queueSize || 0 });
    return;
  }
  if (type === "ToolApprovalRequested") {
    const previous = sessionRuntimeStatus(sessionId);
    mergeSessionRuntimeStatus(sessionId, { pendingApprovalCount: Number(previous.pendingApprovalCount || 0) + 1 });
    return;
  }
  if (type === "ToolApprovalResolved") {
    const previous = sessionRuntimeStatus(sessionId);
    mergeSessionRuntimeStatus(sessionId, { pendingApprovalCount: Math.max(0, Number(previous.pendingApprovalCount || 0) - 1) });
    return;
  }
  if (type === "PlanProposed") {
    mergeSessionRuntimeStatus(sessionId, { busy: false, pendingPlan: true });
    return;
  }
  if (["RunFailed", "RunStopped", "Done", "RunFinished", "TeamRunFinished", "PlanCanceled", "PlanFailed"].includes(type)) {
    mergeSessionRuntimeStatus(sessionId, { busy: false, pendingPlan: false, queuedCount: 0 });
  }
}

function handleAgentEvent(event) {
  const type = event.type;
  const sessionId = eventSessionId(event);
  const payload = {
    ...(event.payload || {}),
    sessionId,
  };

  updateSessionRuntimeFromEvent(event);
  if (sessionId && (sessionId !== state.currentSessionId || state.view !== "chat")) {
    if (["RunFailed", "RunStopped", "Done", "RunFinished", "TeamRunFinished", "PlanProposed", "PlanCanceled", "PlanFailed"].includes(type)) {
      refreshStatus();
      loadSessions();
    }
    return;
  }

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
    if (state.approvalMode === "auto") {
      resolveApproval(payload.approvalId, true, payload.sessionId);
    }
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
  if (type === "PlanDraftStarted") {
    addMessage("system", `Plan 生成中：${payload.task || ""}`);
    state.busy = true;
    return;
  }
  if (type === "PlanProposed") {
    addMessage("system", payload.planMarkdown || "Plan 已生成。输入 /start 执行。");
    state.busy = false;
    refreshStatus();
    return;
  }
  if (type === "PlanExecutionStarted") {
    addMessage("system", `Plan 开始执行：${payload.task || ""}`);
    state.busy = true;
    return;
  }
  if (type === "PlanTaskStarted") {
    addMessage("system", `Plan 节点开始：${payload.taskId || ""} ${payload.taskType || ""}\n${payload.description || ""}`);
    return;
  }
  if (type === "PlanTaskFinished") {
    const stateText = payload.success ? "完成" : "失败";
    addMessage(
      payload.success ? "system" : "error",
      `Plan 节点${stateText}：${payload.taskId || ""} · ${payload.elapsedMillis || 0}ms\n${previewText(payload.text || "").text}`
    );
    return;
  }
  if (type === "PlanExecutionFinished") {
    state.busy = false;
    addMessage(
      payload.success ? "system" : "error",
      payload.success ? "Plan 执行完成，正在交给主 Agent 整理。" : "Plan 执行失败，正在交给主 Agent 整理已有材料。"
    );
    refreshStatus();
    return;
  }
  if (type === "PlanCanceled") {
    state.busy = false;
    addMessage("system", `Plan 已取消：${payload.reason || ""}`);
    refreshStatus();
    return;
  }
  if (type === "PlanFailed") {
    state.busy = false;
    addMessage("error", `Plan 失败：${payload.task || ""}\n${payload.errorMessage || ""}`);
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
    loadSchedules();
  });
  source.onerror = () => {
    connectionState.textContent = "reconnecting";
    connectionState.className = "state-pill error";
  };
}

document.querySelector("#composer").addEventListener("submit", async (event) => {
  event.preventDefault();
  const text = promptEl.value.trim();
  const hasVisionImages = state.view === "chat" && state.visionImages.length > 0;
  if (!text && !hasVisionImages) return;
  promptEl.value = "";
  if (state.view === "room") {
    await sendRoomMessage(text);
    return;
  }
  if (state.view === "rag") {
    await sendRagQuestion(text);
    return;
  }
  if (hasVisionImages) {
    await sendVisionMessage(text);
    return;
  }
  addMessage("user", text);
  sendButton.disabled = true;
  try {
    const hadSession = Boolean(state.currentSessionId);
    await postJson("/api/messages", { sessionId: state.currentSessionId, text });
    if (!hadSession && state.currentSessionId) {
      await loadSessions();
      await loadSchedules();
    }
  } catch (error) {
    addMessage("error", error.message);
  } finally {
    sendButton.disabled = false;
  }
});

attachImageButton.addEventListener("click", () => {
  visionImageInput.click();
});

visionImageInput.addEventListener("change", async () => {
  try {
    await addVisionImages(visionImageInput.files);
  } catch (error) {
    addMessage("error", error.message);
  }
});

visionImageTray.addEventListener("click", (event) => {
  const button = event.target.closest("[data-remove-vision]");
  if (!button) return;
  removeVisionImage(button.dataset.removeVision);
});

stopButton.addEventListener("click", async () => {
  try {
    await postJson("/api/stop", { sessionId: state.currentSessionId });
  } catch (error) {
    addMessage("error", error.message);
  }
});

modelSelect.addEventListener("change", async () => {
  if (state.view === "rag") {
    state.ragChatModel = modelSelect.value;
    renderRagStatus();
    return;
  }
  if (state.view === "chat" && state.visionImages.length > 0) {
    state.visionModel = modelSelect.value;
    return;
  }
  try {
    await postJson("/api/model", {
      sessionId: state.currentSessionId,
      model: modelSelect.value,
    });
  } catch (error) {
    addMessage("error", error.message);
    refreshStatus();
  }
});

promptEl.addEventListener("input", () => {
  promptEl.style.height = "auto";
  promptEl.style.height = `${Math.min(promptEl.scrollHeight, 118)}px`;
});

promptEl.addEventListener("keydown", (event) => {
  if (event.key !== "Enter" || event.shiftKey) {
    return;
  }
  event.preventDefault();
  document.querySelector("#composer").requestSubmit();
});

newSessionButton.addEventListener("click", createSession);
toggleMcpButton.addEventListener("click", () => toggleCapability(mcpCapabilityPanel, toggleMcpButton));
toggleSkillButton.addEventListener("click", () => toggleCapability(skillCapabilityPanel, toggleSkillButton));
chatViewButton.addEventListener("click", () => setView("chat"));
roomViewButton.addEventListener("click", () => setView("room"));
ragViewButton.addEventListener("click", () => setView("rag"));
archiveViewButton.addEventListener("click", () => setView("archive"));
newRagSessionButton.addEventListener("click", createRagSession);
newRagKbButton.addEventListener("click", createKnowledgeBase);
ragKbSelect.addEventListener("change", async () => {
  state.currentKbId = ragKbSelect.value || "default";
  await loadRagDocuments();
});
uploadRagDocumentButton.addEventListener("click", uploadRagDocument);
newRoomButton.addEventListener("click", createRoom);
newRoomAgentButton.addEventListener("click", () => {
  state.currentRoomAgentId = "";
  fillRoomAgentForm(null);
  renderRoomAgents();
  roomEditorDetails.open = true;
});
roomAgentForm.addEventListener("submit", saveRoomAgent);
archiveRoomAgentButton.addEventListener("click", archiveRoomAgent);
refreshRoomMembersButton.addEventListener("click", () => loadRoomMembers());
addRoomMemberButton.addEventListener("click", addRoomMember);
approvalManualButton.addEventListener("click", () => setApprovalMode("manual"));
approvalAutoButton.addEventListener("click", () => setApprovalMode("auto"));
newTodoButton.addEventListener("click", () => setTodoFormOpen(todoForm.classList.contains("hidden")));
refreshTodosButton.addEventListener("click", loadTodos);
todoForm.addEventListener("submit", createTodoFromForm);
newScheduleButton.addEventListener("click", () => setScheduleFormOpen(scheduleForm.classList.contains("hidden")));
refreshSchedulesButton.addEventListener("click", loadSchedules);
scheduleType.addEventListener("change", updateScheduleFields);
scheduleForm.addEventListener("submit", createScheduleFromForm);

todoList.addEventListener("click", (event) => {
  const actionTarget = event.target.closest("[data-action]");
  const item = event.target.closest(".todo-item");
  if (!actionTarget || !item) return;
  if (actionTarget.dataset.action === "toggle") {
    toggleRailItem(item);
  } else if (actionTarget.dataset.action === "complete") {
    completeTodo(item.dataset.id);
  } else if (actionTarget.dataset.action === "archive") {
    archiveTodo(item.dataset.id);
  }
});

scheduleList.addEventListener("click", (event) => {
  const actionTarget = event.target.closest("[data-action]");
  const item = event.target.closest(".schedule-item");
  if (!actionTarget || !item) return;
  if (actionTarget.dataset.action === "toggle") {
    toggleRailItem(item);
  } else if (actionTarget.dataset.action === "cancel") {
    cancelSchedule(item.dataset.id);
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

ragSessionList.addEventListener("click", (event) => {
  const actionButton = event.target.closest("[data-action]");
  const row = event.target.closest(".session-row");
  if (!actionButton || !row) return;

  const sessionId = row.dataset.id;
  const action = actionButton.dataset.action;
  if (action === "use") {
    switchRagSession(sessionId);
  } else if (action === "rename") {
    renameRagSession(sessionId);
  } else if (action === "archive") {
    archiveRagSession(sessionId);
  }
});

roomList.addEventListener("click", (event) => {
  const actionButton = event.target.closest("[data-action]");
  const row = event.target.closest(".session-row");
  if (!actionButton || !row) return;

  const roomId = row.dataset.id;
  const action = actionButton.dataset.action;
  if (action === "use") {
    switchRoom(roomId);
  } else if (action === "rename") {
    updateRoom(roomId);
  } else if (action === "archive") {
    archiveRoom(roomId);
  }
});

roomAgentList.addEventListener("click", (event) => {
  const actionButton = event.target.closest("[data-action]");
  const row = event.target.closest(".session-row");
  if (!actionButton || !row) return;

  const agent = state.roomAgents.find((item) => item.agentId === row.dataset.id);
  state.currentRoomAgentId = agent?.agentId || "";
  fillRoomAgentForm(agent || null);
  renderRoomAgents();
  roomEditorDetails.open = true;
});

roomMemberList.addEventListener("click", (event) => {
  const actionButton = event.target.closest("[data-action]");
  const row = event.target.closest(".member-row");
  if (!actionButton || !row) return;

  if (actionButton.dataset.action === "archive-member") {
    archiveRoomMember(row.dataset.agentId);
  } else if (actionButton.dataset.action === "restore-member") {
    restoreRoomMember(row.dataset.agentId);
  }
});

messagesEl.addEventListener("click", (event) => {
  if (state.view !== "archive") return;
  const actionButton = event.target.closest("[data-action]");
  if (actionButton?.dataset.action === "delete-selected") {
    deleteSelectedArchives();
    return;
  }
  const row = event.target.closest(".archive-item");
  if (!actionButton || !row) return;

  const type = row.dataset.type;
  const id = row.dataset.id;
  if (actionButton.dataset.action === "select-archive") {
    toggleArchiveSelection(type, id, actionButton.checked);
  } else if (actionButton.dataset.action === "restore") {
    restoreArchive(type, id);
  } else if (actionButton.dataset.action === "delete") {
    deleteArchive(type, id);
  }
});

refreshStatus();
state.approvalMode = window.localStorage.getItem("asterApprovalMode") === "auto" ? "auto" : "manual";
renderApprovalMode();
loadSessions();
loadRagStatus();
loadTodos();
loadSchedules();
scheduleTimezone.value = Intl.DateTimeFormat().resolvedOptions().timeZone || "Asia/Shanghai";
updateScheduleFields();
connectEvents();
setInterval(refreshStatus, 3000);
setInterval(loadSessions, 5000);
setInterval(() => {
  if (state.view === "rag") loadRagStatus();
}, 5000);
setInterval(loadSchedules, 10000);
