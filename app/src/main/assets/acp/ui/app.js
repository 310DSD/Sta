(() => {
  'use strict';

  const $ = (id) => document.getElementById(id);
  const app = $('app');
  const sidebar = $('sidebar');
  const scrim = $('scrim');
  const thread = $('thread');
  const messagesEl = $('messages');
  const emptyState = $('emptyState');
  const promptEl = $('prompt');
  const sendButton = $('sendButton');
  const stopButton = $('stopButton');
  const connection = $('connection');
  const sessionList = $('sessionList');
  const permissionLayer = $('permissionLayer');
  const permissionTitle = $('permissionTitle');
  const permissionSub = $('permissionSub');
  const permissionInput = $('permissionInput');
  const permissionActions = $('permissionActions');
  const toastEl = $('toast');
  const modeSelect = $('modeSelect');
  const modelSelect = $('modelSelect');
  const configSelects = $('configSelects');

  const META_KEY = 'sta.acp.sessions.v1';
  const THEME_KEY = 'sta.acp.theme';
  const AGENT_KEY = 'sta.acp.agent';

  const state = {
    agent: localStorage.getItem(AGENT_KEY) === 'codex' ? 'codex' : 'claude',
    ws: null,
    ready: null,
    rpcId: 1,
    pending: new Map(),
    capabilities: {},
    sessionId: null,
    cwd: localStorage.getItem('sta.acp.cwd') || '/root',
    messages: [],
    tools: new Map(),
    plan: [],
    changes: [],
    usage: null,
    modes: null,
    configs: [],
    busy: false,
    permission: null,
    assistantId: null,
    sessions: loadSessions(),
    compose: false,
  };

  function loadSessions() {
    try {
      const parsed = JSON.parse(localStorage.getItem(META_KEY) || '[]');
      return Array.isArray(parsed) ? parsed : [];
    } catch {
      return [];
    }
  }

  function saveSessions() {
    localStorage.setItem(META_KEY, JSON.stringify(state.sessions.slice(0, 30)));
  }

  function currentSession() {
    return state.sessions.find((s) => s.id === state.sessionId && s.agent === state.agent) || null;
  }

  function upsertSession(patch) {
    if (!state.sessionId) return;
    let item = currentSession();
    if (!item) {
      item = {
        id: state.sessionId,
        agent: state.agent,
        title: '新会话',
        cwd: state.cwd,
        updated: Date.now(),
      };
      state.sessions.unshift(item);
    }
    Object.assign(item, patch, { updated: Date.now() });
    state.sessions.sort((a, b) => b.updated - a.updated);
    saveSessions();
    renderSessions();
  }

  function setConnection(text, kind) {
    connection.className = `connection ${kind || ''}`;
    connection.querySelector('span').textContent = text;
  }

  function toast(text) {
    toastEl.textContent = text;
    toastEl.hidden = false;
    clearTimeout(toast._timer);
    toast._timer = setTimeout(() => { toastEl.hidden = true; }, 3600);
  }

  function escapeHtml(value) {
    return String(value)
      .replaceAll('&', '&amp;')
      .replaceAll('<', '&lt;')
      .replaceAll('>', '&gt;')
      .replaceAll('"', '&quot;')
      .replaceAll("'", '&#39;');
  }

  function markdown(text) {
    if (!text) return '';
    const blocks = [];
    let source = escapeHtml(text).replace(/```([\w-]*)\n([\s\S]*?)```/g, (_, lang, body) => {
      const token = `@@CODE${blocks.length}@@`;
      blocks.push(`<pre><code>${body.replace(/\n$/, '')}</code></pre>`);
      return token;
    });
    source = source
      .replace(/^### (.+)$/gm, '<h3>$1</h3>')
      .replace(/^## (.+)$/gm, '<h2>$1</h2>')
      .replace(/^# (.+)$/gm, '<h2>$1</h2>')
      .replace(/^&gt; (.+)$/gm, '<blockquote>$1</blockquote>')
      .replace(/`([^`]+)`/g, '<code>$1</code>')
      .replace(/\*\*([^*]+)\*\*/g, '<strong>$1</strong>')
      .replace(/\*([^*]+)\*/g, '<em>$1</em>')
      .replace(/\[([^\]]+)\]\((https?:[^)]+)\)/g, '<a href="$2" target="_blank" rel="noreferrer">$1</a>');
    const lines = source.split('\n');
    let html = '';
    let inList = false;
    for (const line of lines) {
      if (/^[-*] /.test(line)) {
        if (!inList) { html += '<ul>'; inList = true; }
        html += `<li>${line.slice(2)}</li>`;
      } else {
        if (inList) { html += '</ul>'; inList = false; }
        if (line.trim() === '') html += '';
        else if (/^<(h2|h3|blockquote|pre|ul)/.test(line)) html += line;
        else html += `<p>${line}</p>`;
      }
    }
    if (inList) html += '</ul>';
    blocks.forEach((block, index) => { html = html.replace(`@@CODE${index}@@`, block); });
    return html;
  }

  function toolKindIcon(kind) {
    return ({ read: '◌', edit: '✎', delete: '⌫', move: '↗', search: '⌕', execute: '›_', think: '∴', fetch: '↧' })[kind] || '•';
  }

  function toolStatusText(status) {
    return ({ pending: '等待', in_progress: '运行中', completed: '完成', failed: '失败', cancelled: '已取消' })[status] || status || '等待';
  }

  function renderTool(tool) {
    const content = renderToolContent(tool);
    return `<div class="tool-card" data-tool="${escapeHtml(tool.toolCallId)}">
      <div class="tool-head">
        <div class="tool-icon">${toolKindIcon(tool.kind)}</div>
        <div class="tool-copy"><div class="tool-title">${escapeHtml(tool.title || tool.name || '工具调用')}</div>
        <div class="tool-meta">${escapeHtml(tool.kind || 'other')}${tool.locations?.length ? ' · ' + escapeHtml(tool.locations[0].path || '') : ''}</div></div>
        <span class="tool-status ${escapeHtml(tool.status || 'pending')}">${escapeHtml(toolStatusText(tool.status))}</span>
      </div>
      ${content ? `<div class="tool-content">${content}</div>` : ''}
    </div>`;
  }

  function renderToolContent(tool) {
    if (Array.isArray(tool.content) && tool.content.length) {
      return tool.content.map((entry) => {
        if (entry.type === 'diff') {
          return renderDiff(entry.oldText || '', entry.newText || '');
        }
        if (entry.type === 'content' && entry.content) {
          if (entry.content.type === 'text') return escapeHtml(entry.content.text || '');
          if (entry.content.type === 'image') return '<span class="muted">图片输出</span>';
        }
        return '';
      }).join('');
    }
    if (tool.rawOutput != null) return escapeHtml(JSON.stringify(tool.rawOutput, null, 2));
    if (tool.rawInput != null) return escapeHtml(JSON.stringify(tool.rawInput, null, 2));
    return '';
  }

  function renderDiff(oldText, newText) {
    const oldLines = String(oldText || '').split('\n');
    const newLines = String(newText || '').split('\n');
    const out = [];
    const max = Math.max(oldLines.length, newLines.length);
    for (let i = 0; i < max; i += 1) {
      if (oldLines[i] === newLines[i]) {
        out.push(`<span class="diff-line">  ${escapeHtml(oldLines[i] || '')}</span>`);
      } else {
        if (oldLines[i] !== undefined) out.push(`<span class="diff-line diff-del">- ${escapeHtml(oldLines[i])}</span>`);
        if (newLines[i] !== undefined) out.push(`<span class="diff-line diff-add">+ ${escapeHtml(newLines[i])}</span>`);
      }
    }
    return out.join('\n');
  }

  function renderMessages() {
    if (!state.messages.length) {
      messagesEl.innerHTML = '';
      emptyState.classList.remove('hidden');
      return;
    }
    emptyState.classList.add('hidden');
    const nearBottom = thread.scrollHeight - thread.scrollTop - thread.clientHeight < 120;
    messagesEl.innerHTML = state.messages.map((item) => {
      if (item.type === 'tool') return renderTool(item.tool);
      const thinking = item.thinking ? `<details class="thinking"><summary>思考过程</summary>${markdown(item.thinking)}</details>` : '';
      return `<article class="message ${item.role}" data-message="${escapeHtml(item.id)}">
        <div class="message-role">${item.role === 'user' ? '你' : 'Agent'}</div>
        ${thinking}
        <div class="message-body">${markdown(item.text)}</div>
      </article>`;
    }).join('');
    if (nearBottom) requestAnimationFrame(() => { thread.scrollTop = thread.scrollHeight; });
    updateControls();
    updateInspector();
  }

  function renderSessions() {
    const items = state.sessions.filter((s) => s.agent === state.agent);
    sessionList.innerHTML = items.length ? items.map((session) => `
      <button class="session-item ${session.id === state.sessionId ? 'active' : ''}" data-session="${escapeHtml(session.id)}">
        <strong>${escapeHtml(session.title || '新会话')}</strong>
        <small>${escapeHtml(session.cwd || '')}</small>
      </button>`).join('') : '<div class="inspector-empty">还没有会话</div>';
    sessionList.querySelectorAll('[data-session]').forEach((button) => {
      button.addEventListener('click', () => loadSession(button.dataset.session));
    });
  }

  function updateInspector() {
    const plan = state.plan || [];
    $('planList').innerHTML = plan.length ? plan.map((entry) => `
      <div class="plan-row ${escapeHtml(entry.status || 'pending')}"><i></i><span>${escapeHtml(entry.content || '')}</span></div>`).join('') : '<div class="inspector-empty">还没有计划</div>';
    const changes = state.changes || [];
    $('changeList').innerHTML = changes.length ? changes.map((change) => `
      <div class="change-row"><strong>${escapeHtml(change.path || '')}</strong><span>${escapeHtml(change.summary || '已修改')}</span></div>`).join('') : '<div class="inspector-empty">还没有文件改动</div>';
    const usage = state.usage;
    $('usageCard').innerHTML = usage
      ? `<div>${usage.used ?? 0} / ${usage.size ?? 0} tokens</div><div class="usage-bar"><span style="width:${Math.min(100, ((usage.used || 0) / Math.max(1, usage.size || 1)) * 100)}%"></span></div>${usage.cost ? `<div style="margin-top:6px">$${escapeHtml(String(usage.cost.amount ?? usage.cost))}</div>` : ''}`
      : '等待会话';
  }

  function updateControls() {
    stopButton.disabled = !state.busy;
    sendButton.disabled = !state.ws || state.ws.readyState !== WebSocket.OPEN || !promptEl.value.trim();
    $('composerHint').textContent = state.busy ? 'Agent 正在工作…' : 'Enter 发送 · Shift+Enter 换行';
  }

  function updateAgentUi() {
    app.dataset.agent = state.agent;
    $('agentLabel').textContent = state.agent === 'codex' ? 'Codex' : 'Claude Code';
    $('emptyTitle').textContent = state.agent === 'codex' ? '把任务交给 Codex' : '从一个想法开始';
    $('emptyCopy').textContent = state.agent === 'codex'
      ? '通过官方 Codex app-server 运行，保留原生会话与工具。'
      : '通过官方 Claude Code 运行，保留原生系统提示与工具。';
    $('emptyMark').textContent = state.agent === 'codex' ? '⌘' : '✳';
    document.querySelectorAll('.agent-option').forEach((button) => {
      button.classList.toggle('active', button.dataset.agent === state.agent);
    });
    renderSessions();
  }

  function setMessages(items) {
    state.messages = items;
    renderMessages();
  }

  function appendUser(text) {
    state.messages.push({ id: `u-${Date.now()}`, role: 'user', text });
    renderMessages();
  }

  function appendSystem(text) {
    state.messages.push({ id: `s-${Date.now()}`, role: 'assistant', text });
    renderMessages();
  }

  function findAssistant(messageId) {
    if (messageId) {
      const existing = state.messages.find((m) => m.id === messageId);
      if (existing) return existing;
      const created = { id: messageId, role: 'assistant', text: '', thinking: '' };
      state.assistantId = messageId;
      state.messages.push(created);
      return created;
    }
    let found = state.messages.find((m) => m.id === state.assistantId);
    if (!found) {
      found = { id: state.assistantId || `a-${Date.now()}`, role: 'assistant', text: '', thinking: '' };
      state.assistantId = found.id;
      state.messages.push(found);
    }
    return found;
  }

  function handleUpdate(update) {
    if (!update) return;
    switch (update.sessionUpdate) {
      case 'agent_message_chunk':
        if (update.content?.type === 'text') {
          const msg = findAssistant(update.messageId);
          msg.text += update.content.text || '';
          renderMessages();
        }
        break;
      case 'agent_thought_chunk':
        if (update.content?.type === 'text') {
          const msg = findAssistant(update.messageId);
          msg.thinking += update.content.text || '';
          renderMessages();
        }
        break;
      case 'user_message_chunk':
        if (update.content?.type === 'text') {
          const id = update.messageId || `u-${Date.now()}`;
          let msg = state.messages.find((m) => m.id === id);
          if (!msg) { msg = { id, role: 'user', text: '' }; state.messages.push(msg); }
          msg.text += update.content.text || '';
          renderMessages();
        }
        break;
      case 'tool_call':
      case 'tool_call_update': {
        const id = update.toolCallId;
        let item = state.messages.find((m) => m.type === 'tool' && m.tool.toolCallId === id);
        if (!item) {
          item = { id: `tool-${id}`, type: 'tool', tool: { toolCallId: id } };
          state.messages.push(item);
        }
        const tool = item.tool;
        for (const key of ['title', 'name', 'kind', 'status', 'content', 'rawInput', 'rawOutput', 'locations']) {
          if (update[key] !== undefined) tool[key] = update[key];
        }
        if (tool.status === 'completed' || tool.status === 'failed') {
          for (const entry of tool.content || []) {
            if (entry.type === 'diff' && entry.path) {
              const existing = state.changes.find((c) => c.path === entry.path);
              const summary = `+${String(entry.newText || '').split('\n').length} 行`;
              if (existing) existing.summary = summary;
              else state.changes.push({ path: entry.path, summary });
            }
          }
        }
        renderMessages();
        break;
      }
      case 'plan':
        state.plan = update.entries || [];
        updateInspector();
        break;
      case 'usage_update':
        state.usage = update;
        $('contextMeter').textContent = `${update.used ?? 0} / ${update.size ?? 0}`;
        updateInspector();
        break;
      case 'available_commands_update':
        state.commands = update.availableCommands || [];
        break;
      case 'current_mode_update':
        state.modes = { ...(state.modes || {}), currentModeId: update.currentModeId };
        renderConfig();
        break;
      case 'config_option_update':
        state.configs = normalizeConfigs(update.configOptions || []);
        renderConfig();
        break;
      case 'session_info_update':
        if (update.title) upsertSession({ title: update.title });
        break;
      case 'notice':
        toast(update.title || update.description || 'Agent notice');
        break;
      default:
        break;
    }
  }

  function normalizeConfigs(value) {
    if (Array.isArray(value)) return value;
    if (value && typeof value === 'object') return Object.entries(value).map(([id, config]) => ({ id, ...config }));
    return [];
  }

  function renderConfig() {
    const modes = state.modes?.availableModes || [];
    modeSelect.hidden = modes.length < 2;
    modeSelect.innerHTML = modes.map((mode) => `<option value="${escapeHtml(mode.id)}" ${mode.id === state.modes?.currentModeId ? 'selected' : ''}>${escapeHtml(mode.name || mode.id)}</option>`).join('');
    modelSelect.hidden = true;
    configSelects.innerHTML = '';
    for (const config of state.configs) {
      const options = config.options || [];
      if (!options.length) continue;
      const select = document.createElement('select');
      select.title = config.name || config.id;
      select.innerHTML = options.map((option) => {
        const value = option.valueId ?? option.value ?? option.id;
        return `<option value="${escapeHtml(value)}" ${value === config.currentValue ? 'selected' : ''}>${escapeHtml(option.name || option.label || value)}</option>`;
      }).join('');
      select.addEventListener('change', () => setConfig(config.id || config.configId, select.value));
      configSelects.appendChild(select);
    }
  }

  async function setMode(modeId) {
    if (!state.sessionId) return;
    try { await rpc('session/set_mode', { sessionId: state.sessionId, modeId }); }
    catch (error) { toast(error.message || '切换模式失败'); }
  }

  async function setConfig(configId, value) {
    if (!state.sessionId) return;
    try {
      const result = await rpc('session/set_config_option', { sessionId: state.sessionId, configId, value });
      state.configs = normalizeConfigs(result?.configOptions || state.configs);
      renderConfig();
    } catch (error) { toast(error.message || '切换配置失败'); }
  }

  function handleRequest(message) {
    if (message.method === 'session/request_permission') {
      showPermission(message);
      return;
    }
    if (message.method?.startsWith('fs/') || message.method?.startsWith('terminal/')) {
      respondError(message.id, -32601, 'Client does not provide filesystem or terminal capabilities');
      return;
    }
    respondError(message.id, -32601, `Unsupported client method: ${message.method}`);
  }

  function showPermission(message) {
    state.permission = message;
    const params = message.params || {};
    const tool = params.toolCall || {};
    const existing = state.messages.find((m) => m.type === 'tool' && m.tool.toolCallId === tool.toolCallId)?.tool;
    permissionTitle.textContent = tool.title || existing?.title || '允许这个操作吗？';
    permissionSub.textContent = tool.kind ? `工具类型：${tool.kind}` : 'Agent 需要你的确认才能继续。';
    const input = tool.rawInput ?? existing?.rawInput;
    permissionInput.hidden = input == null;
    permissionInput.textContent = input == null ? '' : JSON.stringify(input, null, 2);
    permissionActions.innerHTML = '';
    for (const option of params.options || []) {
      const button = document.createElement('button');
      button.textContent = option.name || option.optionId;
      button.className = option.kind?.startsWith('allow') ? 'allow' : option.kind?.startsWith('reject') ? 'deny' : '';
      button.addEventListener('click', () => respondPermission(message.id, option.optionId));
      permissionActions.appendChild(button);
    }
    if (!(params.options || []).length) {
      const button = document.createElement('button');
      button.textContent = '取消';
      button.addEventListener('click', () => respondPermission(message.id, null));
      permissionActions.appendChild(button);
    }
    permissionLayer.hidden = false;
  }

  function respondPermission(id, optionId) {
    if (optionId) {
      sendRpcResponse(id, { outcome: { outcome: 'selected', optionId } });
    } else {
      sendRpcResponse(id, { outcome: { outcome: 'cancelled' } });
    }
    permissionLayer.hidden = true;
    state.permission = null;
  }

  function sendRpcResponse(id, result) {
    send({ jsonrpc: '2.0', id, result });
  }

  function respondError(id, code, message) {
    send({ jsonrpc: '2.0', id, error: { code, message } });
  }

  function send(value) {
    if (state.ws?.readyState === WebSocket.OPEN) state.ws.send(JSON.stringify(value));
  }

  function rpc(method, params) {
    return new Promise((resolve, reject) => {
      if (!state.ws || state.ws.readyState !== WebSocket.OPEN) {
        reject(new Error('ACP 连接尚未建立'));
        return;
      }
      const id = state.rpcId++;
      state.pending.set(id, { resolve, reject });
      send({ jsonrpc: '2.0', id, method, params });
    });
  }

  function handleMessage(message) {
    if (message.method && message.id !== undefined) {
      handleRequest(message);
      return;
    }
    if (message.method === 'session/update') {
      if (message.params?.sessionId === state.sessionId) handleUpdate(message.params.update);
      return;
    }
    if (message.method === '$/bridge_error') {
      toast(message.params?.message || 'ACP bridge 错误');
      return;
    }
    if (message.method === '$/bridge_exit') {
      setConnection('已断开', 'error');
      state.busy = false;
      updateControls();
      return;
    }
    if (message.method === '$/stderr') {
      console.debug('[acp]', message.params?.text || '');
      return;
    }
    if (message.id !== undefined && state.pending.has(message.id)) {
      const pending = state.pending.get(message.id);
      state.pending.delete(message.id);
      if (message.error) pending.reject(new Error(message.error.message || 'ACP 请求失败'));
      else pending.resolve(message.result);
    }
  }

  async function connect() {
    if (state.ws) {
      try { state.ws.close(); } catch {}
      state.ws = null;
    }
    setConnection('连接中', '');
    const ws = new WebSocket(`ws://${location.host}/acp?agent=${encodeURIComponent(state.agent)}`);
    state.ws = ws;
    state.ready = new Promise((resolve, reject) => {
      const timer = setTimeout(() => reject(new Error('连接超时')), 10000);
      ws.onopen = async () => {
        clearTimeout(timer);
        try {
          const result = await rpc('initialize', {
            protocolVersion: 1,
            clientCapabilities: { fs: { readTextFile: false, writeTextFile: false }, terminal: false },
            clientInfo: { name: 'Sta ACP', version: '0.1.0' },
          });
          state.capabilities = result?.agentCapabilities || {};
          setConnection('已连接', 'connected');
          if (state.sessionId) await restoreSession();
          resolve();
        } catch (error) {
          setConnection('连接失败', 'error');
          reject(error);
        }
      };
      ws.onmessage = (event) => {
        try { handleMessage(JSON.parse(event.data)); } catch (error) { console.debug(error); }
      };
      ws.onerror = () => setConnection('连接失败', 'error');
      ws.onclose = () => {
        clearTimeout(timer);
        if (!messageIsClosing()) setConnection('已断开', 'error');
        state.busy = false;
        updateControls();
      };
    });
    try { await state.ready; } catch (error) { toast(error.message || '无法连接 ACP'); }
  }

  function messageIsClosing() {
    return false;
  }

  async function restoreSession() {
    const session = currentSession();
    if (!session) return;
    if (!state.capabilities.loadSession && !state.capabilities.sessionCapabilities?.resume) {
      state.sessionId = null;
      return;
    }
    setMessages([]);
    try {
      if (state.capabilities.loadSession) {
        const result = await rpc('session/load', { sessionId: session.id, cwd: session.cwd || state.cwd, mcpServers: [] });
        applySessionConfig(result);
      } else if (state.capabilities.sessionCapabilities?.resume) {
        const result = await rpc('session/resume', { sessionId: session.id, cwd: session.cwd || state.cwd, mcpServers: [] });
        applySessionConfig(result);
      }
      upsertSession({ title: session.title || '已恢复会话', cwd: session.cwd || state.cwd });
    } catch (error) {
      state.sessionId = null;
      toast('无法恢复会话，请新建一个');
    }
  }

  function applySessionConfig(result) {
    if (!result) return;
    state.modes = result.modes || null;
    state.configs = normalizeConfigs(result.configOptions || []);
    renderConfig();
  }

  async function newSession() {
    if (!state.ws || state.ws.readyState !== WebSocket.OPEN) await connect();
    if (!state.ws || state.ws.readyState !== WebSocket.OPEN) return null;
    let cwd = ($('cwd').value || '').trim();
    if (!cwd.startsWith('/')) {
      toast('工作目录必须是绝对路径');
      return null;
    }
    localStorage.setItem('sta.acp.cwd', cwd);
    state.cwd = cwd;
    state.busy = false;
    updateControls();
    try {
      const result = await rpc('session/new', { cwd, mcpServers: [] });
      state.sessionId = result?.sessionId || null;
      state.messages = [];
      state.tools.clear();
      state.plan = [];
      state.changes = [];
      state.usage = null;
      state.assistantId = null;
      applySessionConfig(result);
      upsertSession({ title: '新会话', cwd });
      renderMessages();
      updateInspector();
      $('sessionLabel').textContent = '新会话';
      return state.sessionId;
    } catch (error) {
      toast(error.message || '创建会话失败，请先在账号管理器登录 CLI');
      return null;
    }
  }

  async function loadSession(sessionId) {
    if (state.busy) { toast('当前任务运行中'); return; }
    const session = state.sessions.find((s) => s.id === sessionId && s.agent === state.agent);
    if (!session) return;
    if (sessionId === state.sessionId) { closeSidebar(); return; }
    state.sessionId = null;
    state.cwd = session.cwd || state.cwd;
    $('cwd').value = state.cwd;
    state.messages = [];
    state.plan = [];
    state.changes = [];
    state.usage = null;
    renderMessages();
    if (!state.ws || state.ws.readyState !== WebSocket.OPEN) await connect();
    state.sessionId = sessionId;
    try {
      if (state.capabilities.loadSession) {
        const result = await rpc('session/load', { sessionId, cwd: state.cwd, mcpServers: [] });
        applySessionConfig(result);
      } else if (state.capabilities.sessionCapabilities?.resume) {
        const result = await rpc('session/resume', { sessionId, cwd: state.cwd, mcpServers: [] });
        applySessionConfig(result);
      } else {
        toast('当前 Agent 不支持恢复历史');
      }
      $('sessionLabel').textContent = session.title || '会话';
      upsertSession({ title: session.title || '会话', cwd: state.cwd });
    } catch (error) {
      state.sessionId = null;
      toast('恢复会话失败');
    }
    closeSidebar();
    renderSessions();
  }

  async function sendPrompt() {
    const text = promptEl.value.trim();
    if (!text || state.busy) return;
    if (!state.sessionId) {
      const id = await newSession();
      if (!id) return;
    }
    promptEl.value = '';
    promptEl.style.height = 'auto';
    appendUser(text);
    state.busy = true;
    state.assistantId = null;
    updateControls();
    if ((currentSession()?.title || '新会话') === '新会话') {
      const title = text.replace(/\s+/g, ' ').slice(0, 34);
      upsertSession({ title });
      $('sessionLabel').textContent = title;
    }
    try {
      await rpc('session/prompt', { sessionId: state.sessionId, prompt: [{ type: 'text', text }] });
    } catch (error) {
      appendSystem(`请求失败：${error.message || '未知错误'}`);
    } finally {
      state.busy = false;
      updateControls();
    }
  }

  function cancel() {
    if (!state.sessionId) return;
    send({ jsonrpc: '2.0', method: 'session/cancel', params: { sessionId: state.sessionId } });
    for (const item of state.messages) {
      if (item.type === 'tool' && ['pending', 'in_progress'].includes(item.tool.status)) item.tool.status = 'cancelled';
    }
    if (state.permission) respondPermission(state.permission.id, null);
    state.busy = false;
    updateControls();
    renderMessages();
  }

  function closeSidebar() { app.classList.remove('sidebar-open'); }
  function openSidebar() { app.classList.add('sidebar-open'); }

  // Events
  $('newSession').addEventListener('click', () => { closeSidebar(); newSession(); });
  $('startButton').addEventListener('click', () => newSession());
  sendButton.addEventListener('click', sendPrompt);
  stopButton.addEventListener('click', cancel);
  $('menuButton').addEventListener('click', openSidebar);
  $('sidebarClose').addEventListener('click', closeSidebar);
  scrim.addEventListener('click', closeSidebar);
  $('inspectorClose').addEventListener('click', () => app.classList.remove('inspector-open'));
  promptEl.addEventListener('input', () => {
    promptEl.style.height = 'auto';
    promptEl.style.height = `${Math.min(170, promptEl.scrollHeight)}px`;
    updateControls();
  });
  promptEl.addEventListener('compositionstart', () => { state.compose = true; });
  promptEl.addEventListener('compositionend', () => { state.compose = false; });
  promptEl.addEventListener('keydown', (event) => {
    if (event.key === 'Enter' && !event.shiftKey && !state.compose && !event.isComposing) {
      event.preventDefault();
      sendPrompt();
    }
  });
  modeSelect.addEventListener('change', () => setMode(modeSelect.value));
  $('themeButton').addEventListener('click', () => {
    const light = app.dataset.theme === 'light';
    app.dataset.theme = light ? 'dark' : 'light';
    localStorage.setItem(THEME_KEY, app.dataset.theme);
  });
  document.querySelectorAll('.agent-option').forEach((button) => {
    button.addEventListener('click', async () => {
      if (state.busy) { toast('当前任务运行中'); return; }
      const agent = button.dataset.agent;
      if (agent === state.agent) return;
      state.agent = agent;
      localStorage.setItem(AGENT_KEY, agent);
      state.sessionId = null;
      state.messages = [];
      state.plan = [];
      state.changes = [];
      state.usage = null;
      state.assistantId = null;
      updateAgentUi();
      renderMessages();
      updateInspector();
      await connect();
    });
  });

  // Init
  app.dataset.theme = localStorage.getItem(THEME_KEY) || 'dark';
  $('cwd').value = state.cwd;
  updateAgentUi();
  renderMessages();
  updateInspector();
  updateControls();
  connect();
})();
