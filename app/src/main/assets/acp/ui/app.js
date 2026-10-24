(() => {
  'use strict';

  const $ = (id) => document.getElementById(id);
  const app = $('app');
  const sidebar = $('sidebar');
  const thread = $('thread');
  const messagesEl = $('messages');
  const emptyState = $('emptyState');
  const promptEl = $('prompt');
  const sendButton = $('sendButton');
  const connection = $('connection');
  const sessionList = $('sessionList');
  const sheetLayer = $('sheetLayer');
  const sheetBody = $('sheetBody');
  const sheetTitle = $('sheetTitle');
  const sheetKicker = $('sheetKicker');
  const toastEl = $('toast');
  const banner = $('banner');
  const bannerText = $('bannerText');
  const bannerAction = $('bannerAction');
  const fileInput = $('fileInput');

  const SESSIONS_KEY = 'sta.acp.sessions.v2';
  const LEGACY_SESSIONS_KEY = 'sta.acp.sessions.v1';
  const AGENT_KEY = 'sta.acp.agent';
  const CWD_KEY = (agent) => `sta.acp.cwd.${agent}`;
  const THEME_KEY = (agent) => `sta.acp.theme.${agent}`;

  const CRAB_SVG = `<svg class="crab-svg" viewBox="0 0 36 24" shape-rendering="crispEdges" aria-hidden="true"><g fill="currentColor"><rect x="0" y="6" width="10" height="8"/><rect x="0" y="4" width="5" height="2" fill="#E58B6C"/><rect x="0" y="14" width="5" height="2" fill="#E58B6C"/><rect x="28" y="6" width="8" height="8"/><rect x="31" y="4" width="5" height="2" fill="#E58B6C"/><rect x="31" y="14" width="5" height="2" fill="#E58B6C"/><rect x="8" y="2" width="20" height="15"/><rect x="11" y="0" width="14" height="2"/><rect x="10" y="16" width="4" height="4"/><rect x="16" y="16" width="4" height="4"/><rect x="22" y="16" width="4" height="4"/></g><g fill="#E58B6C"><rect x="12" y="3" width="12" height="2"/></g><g fill="#2A1A15"><rect x="13" y="7" width="2" height="3"/><rect x="21" y="7" width="2" height="3"/><rect x="16" y="12" width="4" height="1"/></g><g fill="#F7F6F1"><rect x="13" y="7" width="1" height="1"/><rect x="21" y="7" width="1" height="1"/></g></svg>`;

  const MARKS = {
    claude: `<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M21 10.95h3v3.1h-3v3.03h-1.49V20H18v-2.92h-1.49V20H15v-2.92H9V20H7.49v-2.92H6V20H4.49v-2.92H3V14.05H0v-3.1h3V5h18v5.95ZM6 10.95h1.49V8.1H6v2.85Zm10.51 0H18V8.1h-1.49v2.85Z" fill="currentColor"/></svg>`,
    codex: `<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M9.06 3.34a4.58 4.58 0 0 1 2.29-.31c1 .11 1.89.54 2.67 1.27a.1.1 0 0 0 .08.03 4.55 4.55 0 0 1 3.05.27l.05.02.11.06a4.58 4.58 0 0 1 2.19 2.4c.21.51.31 1.04.31 1.59 0 .42-.04.83-.13 1.23a.12.12 0 0 0 .03.11 4.47 4.47 0 0 1 1.18 2.17c.29 1.43 0 2.71-.88 3.85l-.14.17a4.55 4.55 0 0 1-2.2 1.39.12.12 0 0 0-.08.08c-.19.55-.38 1.02-.74 1.49a4.5 4.5 0 0 1-3.71 1.84 4.5 4.5 0 0 1-3.16-1.3.1.1 0 0 0-.1-.02c-.39.12-.78.14-1.2.13a4.44 4.44 0 0 1-1.95-.46 4.54 4.54 0 0 1-1.61-1.34c-.15-.2-.3-.39-.41-.62a5.8 5.8 0 0 1-.37-.96 4.58 4.58 0 0 1-.01-2.3.12.12 0 0 0 .01-.05.08.08 0 0 0-.03-.05 4.47 4.47 0 0 1-1.03-1.65 3.9 3.9 0 0 1-.25-1.19 5.19 5.19 0 0 1 .14-1.6c.34-1.11.98-1.98 1.93-2.62.21-.14.41-.25.6-.33.22-.09.43-.16.65-.23a.1.1 0 0 0 .06-.06 4.5 4.5 0 0 1 .83-1.62 4.54 4.54 0 0 1 1.84-1.39Zm3.48 10.57a.64.64 0 1 0 0 1.27h3.64a.64.64 0 1 0 0-1.27h-3.64ZM8.46 9.23a.64.64 0 0 0-1.1.63l1.27 2.22-1.27 2.14a.64.64 0 0 0 1.1.65l1.45-2.46a.64.64 0 0 0 0-.64L8.46 9.23Z" fill="currentColor"/></svg>`,
  };

  const AGENT_COPY = {
    claude: {
      name: 'Claude Code',
      sub: 'local workspace',
      emptyTitle: '今天想构建什么？',
      emptyCopy: 'Claude Code 会在你的 Linux 环境里读取文件、运行命令，并保留完整会话。',
      placeholder: 'Describe a task or ask a question',
      suggestions: [
        ['i-search', '解释这个项目'],
        ['i-git', '检查最近的改动'],
        ['i-terminal', '修复失败的测试'],
        ['i-edit', '添加一个小功能'],
      ],
    },
    codex: {
      name: 'Codex',
      sub: 'local workspace',
      emptyTitle: 'What should we build?',
      emptyCopy: 'Codex runs in your Linux workspace, with the same tools and context as the CLI.',
      placeholder: 'Do anything',
      suggestions: [
        ['i-git', 'Review my current changes'],
        ['i-terminal', 'Fix a failing test'],
        ['i-search', 'Explain this codebase'],
        ['i-edit', 'Build a small feature'],
      ],
    },
  };

  const TOOL_KIND = {
    read: '读取文件', edit: '编辑文件', delete: '删除文件', move: '移动文件',
    search: '搜索', execute: '运行命令', think: '思考', fetch: '获取网页',
    switch_mode: '切换模式', other: '工具',
  };
  const TOOL_ICON = {
    read: 'i-file', edit: 'i-edit', delete: 'i-trash', move: 'i-move',
    search: 'i-search', execute: 'i-terminal', think: 'i-brain', fetch: 'i-globe',
    switch_mode: 'i-shield', other: 'i-code',
  };
  const MODE_ICON = {
    'read-only': 'i-lock', 'workspace-write': 'i-edit', 'agent': 'i-shield',
    'agent-full-access': 'i-alert', default: 'i-shield', acceptEdits: 'i-edit',
    plan: 'i-brain', auto: 'i-shield', bypassPermissions: 'i-alert',
  };

  function safeStorageGet(key) {
    try { return localStorage.getItem(key); } catch { return null; }
  }
  function safeStorageSet(key, value) {
    try { localStorage.setItem(key, value); } catch {}
  }

  function loadSessions() {
    try {
      const raw = safeStorageGet(SESSIONS_KEY) || safeStorageGet(LEGACY_SESSIONS_KEY) || '[]';
      const parsed = JSON.parse(raw);
      if (!Array.isArray(parsed)) return [];
      return parsed.filter((item) => item && item.id).map((item) => ({
        id: String(item.id),
        agent: item.agent === 'codex' ? 'codex' : 'claude',
        title: item.title || '新会话',
        cwd: item.cwd || '/root',
        updated: Number(item.updated) || Date.now(),
      }));
    } catch {
      return [];
    }
  }

  const state = {
    agent: safeStorageGet(AGENT_KEY) === 'codex' ? 'codex' : 'claude',
    theme: 'light',
    ws: null,
    connecting: false,
    connectPromise: null,
    manualClose: false,
    initialized: false,
    capabilities: {},
    agentInfo: null,
    authMethods: [],
    authStatus: null,
    sessionId: null,
    sessionTitle: '新会话',
    cwd: '/root',
    sessions: loadSessions(),
    sessionQuery: '',
    draftPromise: null,
    messages: [],
    tools: new Map(),
    plan: [],
    changes: [],
    usage: null,
    configs: [],
    legacyModes: null,
    busy: false,
    cancelling: false,
    assistantId: null,
    rpcId: 1,
    pending: new Map(),
    attachments: [],
    commands: [],
    slashStart: -1,
    slashIndex: 0,
    compose: false,
    permissionCount: 0,
    activeElicitation: null,
    sheetKind: null,
    sheetOnClose: null,
    reconnectAttempts: 0,
    lastStderr: '',
    stickBottom: true,
  };

  const messageNodes = new Map();
  let syncScheduled = false;
  let syncScroll = false;

  function agentCopy() { return AGENT_COPY[state.agent]; }
  function esc(value) {
    return String(value ?? '')
      .replaceAll('&', '&amp;').replaceAll('<', '&lt;').replaceAll('>', '&gt;')
      .replaceAll('"', '&quot;').replaceAll("'", '&#39;');
  }
  function icon(name, cls = '') {
    return `<svg class="icon ${cls}"><use href="#${name}"/></svg>`;
  }
  function fmtDate(value) {
    const n = typeof value === 'number' ? value : Date.parse(value || '');
    return Number.isFinite(n) ? n : 0;
  }
  function relativeTime(value) {
    const n = fmtDate(value);
    if (!n) return '';
    const diff = Math.max(0, Date.now() - n);
    if (diff < 60000) return '刚刚';
    if (diff < 3600000) return `${Math.floor(diff / 60000)} 分钟前`;
    if (diff < 86400000) return `${Math.floor(diff / 3600000)} 小时前`;
    if (diff < 604800000) return `${Math.floor(diff / 86400000)} 天前`;
    return new Date(n).toLocaleDateString('zh-CN', { month: 'numeric', day: 'numeric' });
  }
  function fmtTokens(value) {
    const n = Number(value) || 0;
    if (n >= 1000000) return `${(n / 1000000).toFixed(1)}M`;
    if (n >= 1000) return `${(n / 1000).toFixed(n >= 10000 ? 0 : 1)}k`;
    return String(n);
  }
  function isAbsolutePath(value) {
    return value.startsWith('/') || /^[A-Za-z]:[\\/]/.test(value);
  }
  function basename(path) {
    if (!path) return '';
    const value = String(path).replace(/\/+$/, '');
    return value.split('/').filter(Boolean).pop() || value || '/';
  }
  function idFor(prefix) {
    return `${prefix}-${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 7)}`;
  }

  // ---------- Markdown ----------
  function inline(text) {
    const codeTokens = [];
    let out = String(text).replace(/`([^`]+)`/g, (_, code) => {
      const token = `\u0000IC${codeTokens.length}\u0000`;
      codeTokens.push(`<code>${esc(code)}</code>`);
      return token;
    });
    out = esc(out);
    out = out.replace(/\[([^\]]+)\]\((https?:\/\/[^)\s]+)\)/g, '<a href="$2" target="_blank" rel="noreferrer">$1</a>');
    out = out.replace(/(^|[\s(])((?:https?:\/\/)[^\s<)]+)/g, (m, pre, url) => `${pre}<a href="${url}" target="_blank" rel="noreferrer">${url}</a>`);
    out = out.replace(/\*\*([^*]+)\*\*/g, '<strong>$1</strong>');
    out = out.replace(/__([^_]+)__/g, '<strong>$1</strong>');
    out = out.replace(/(^|[\s(])\*([^*\n]+)\*/g, '$1<em>$2</em>');
    out = out.replace(/(^|[\s(])_([^_\n]+)_/g, '$1<em>$2</em>');
    out = out.replace(/~~([^~]+)~~/g, '<del>$1</del>');
    codeTokens.forEach((code, i) => { out = out.replace(`\u0000IC${i}\u0000`, code); });
    return out;
  }

  function markdown(source) {
    const text = String(source ?? '');
    if (!text) return '';
    const lines = text.split('\n');
    const out = [];
    let i = 0;
    let paragraph = [];
    let list = null;
    const flushParagraph = () => {
      if (!paragraph.length) return;
      out.push(`<p>${paragraph.map(inline).join('<br>')}</p>`);
      paragraph = [];
    };
    const flushList = () => {
      if (!list) return;
      out.push(`<${list.tag}>${list.items.join('')}</${list.tag}>`);
      list = null;
    };
    while (i < lines.length) {
      const line = lines[i];
      if (/^```/.test(line)) {
        flushParagraph(); flushList();
        const lang = line.slice(3).trim();
        const body = [];
        i += 1;
        while (i < lines.length && !/^```/.test(lines[i])) { body.push(lines[i]); i += 1; }
        if (i < lines.length) i += 1;
        out.push(renderCodeBlock(body.join('\n'), lang));
        continue;
      }
      if (/^\s*$/.test(line)) { flushParagraph(); flushList(); i += 1; continue; }
      const heading = line.match(/^(#{1,4})\s+(.+)$/);
      if (heading) {
        flushParagraph(); flushList();
        const level = Math.min(4, heading[1].length);
        out.push(`<h${level}>${inline(heading[2])}</h${level}>`);
        i += 1; continue;
      }
      if (/^(?:---+|\*\*\*+|___+)\s*$/.test(line)) { flushParagraph(); flushList(); out.push('<hr>'); i += 1; continue; }
      if (/^>\s?/.test(line)) {
        flushParagraph(); flushList();
        const quote = [];
        while (i < lines.length && /^>\s?/.test(lines[i])) { quote.push(lines[i].replace(/^>\s?/, '')); i += 1; }
        out.push(`<blockquote>${quote.map(inline).join('<br>')}</blockquote>`);
        continue;
      }
      if (line.includes('|') && i + 1 < lines.length && /^\s*\|?[\s:|-]+\|[\s:|-]*$/.test(lines[i + 1])) {
        flushParagraph(); flushList();
        const rows = [line];
        i += 2;
        while (i < lines.length && lines[i].includes('|') && !/^\s*$/.test(lines[i])) { rows.push(lines[i]); i += 1; }
        const split = (row) => row.trim().replace(/^\|/, '').replace(/\|$/, '').split('|').map((cell) => cell.trim());
        const head = split(rows[0]);
        out.push(`<table><thead><tr>${head.map((cell) => `<th>${inline(cell)}</th>`).join('')}</tr></thead><tbody>${rows.slice(1).map((row) => `<tr>${split(row).map((cell) => `<td>${inline(cell)}</td>`).join('')}</tr>`).join('')}</tbody></table>`);
        continue;
      }
      const item = line.match(/^(\s*)([-*+]|\d+\.)\s+(.+)$/);
      if (item) {
        flushParagraph();
        const tag = /\d+\./.test(item[2]) ? 'ol' : 'ul';
        if (!list || list.tag !== tag) { flushList(); list = { tag, items: [] }; }
        let body = item[3];
        const task = body.match(/^\[([ xX])\]\s+(.+)$/);
        if (task) {
          const checked = task[1].toLowerCase() === 'x';
          body = `<span class="task-box ${checked ? 'checked' : ''}">${checked ? icon('i-check') : ''}</span><span>${inline(task[2])}</span>`;
          list.items.push(`<li class="task-item">${body}</li>`);
        } else {
          list.items.push(`<li>${inline(body)}</li>`);
        }
        i += 1; continue;
      }
      paragraph.push(line);
      i += 1;
    }
    flushParagraph(); flushList();
    return out.join('');
  }

  function renderCodeBlock(code, lang) {
    const language = lang || 'text';
    return `<div class="code-block"><div class="code-head"><span>${esc(language)}</span><button class="copy-btn" data-copy-code title="复制">${icon('i-copy')}</button></div><pre class="code-pre"><code>${esc(code)}</code></pre></div>`;
  }

  // ---------- Diffs ----------
  function lineDiff(oldText, newText) {
    const oldLines = String(oldText ?? '').split('\n');
    const newLines = String(newText ?? '').split('\n');
    if (oldLines.length > 500 || newLines.length > 500) {
      const max = Math.max(oldLines.length, newLines.length);
      const rows = [];
      for (let i = 0; i < max; i += 1) {
        if (oldLines[i] === newLines[i]) rows.push({ type: 'ctx', text: oldLines[i] || '', oldNo: i + 1, newNo: i + 1 });
        else {
          if (oldLines[i] !== undefined) rows.push({ type: 'del', text: oldLines[i], oldNo: i + 1 });
          if (newLines[i] !== undefined) rows.push({ type: 'add', text: newLines[i], newNo: i + 1 });
        }
      }
      return rows;
    }
    const n = oldLines.length;
    const m = newLines.length;
    const dp = Array.from({ length: n + 1 }, () => new Uint16Array(m + 1));
    for (let a = n - 1; a >= 0; a -= 1) {
      for (let b = m - 1; b >= 0; b -= 1) {
        dp[a][b] = oldLines[a] === newLines[b] ? dp[a + 1][b + 1] + 1 : Math.max(dp[a + 1][b], dp[a][b + 1]);
      }
    }
    const rows = [];
    let a = 0; let b = 0;
    while (a < n && b < m) {
      if (oldLines[a] === newLines[b]) {
        rows.push({ type: 'ctx', text: oldLines[a], oldNo: a + 1, newNo: b + 1 }); a += 1; b += 1;
      } else if (dp[a + 1][b] >= dp[a][b + 1]) {
        rows.push({ type: 'del', text: oldLines[a], oldNo: a + 1 }); a += 1;
      } else {
        rows.push({ type: 'add', text: newLines[b], newNo: b + 1 }); b += 1;
      }
    }
    while (a < n) { rows.push({ type: 'del', text: oldLines[a], oldNo: a + 1 }); a += 1; }
    while (b < m) { rows.push({ type: 'add', text: newLines[b], newNo: b + 1 }); b += 1; }
    return rows;
  }

  function diffStats(rows) {
    return {
      add: rows.filter((row) => row.type === 'add').length,
      del: rows.filter((row) => row.type === 'del').length,
    };
  }

  function renderDiff(path, oldText, newText) {
    const rows = lineDiff(oldText, newText);
    const stats = diffStats(rows);
    const body = rows.map((row) => {
      const sign = row.type === 'add' ? '+' : row.type === 'del' ? '-' : ' ';
      return `<span class="diff-line ${row.type}"><span class="ln">${row.oldNo || row.newNo || ''}</span><span class="sign">${sign}</span><span class="code">${esc(row.text)}</span></span>`;
    }).join('');
    return `<div class="diff-card"><div class="diff-head">${icon('i-file')}<span class="file-name">${esc(path || '文件')}</span><span class="diff-stats"><span class="add">+${stats.add}</span><span class="del">−${stats.del}</span></span></div><div class="diff-body">${body}</div></div>`;
  }

  function collectChanges(tool) {
    for (const entry of tool.content || []) {
      if (entry?.type !== 'diff') continue;
      const rows = lineDiff(entry.oldText || '', entry.newText || '');
      const stats = diffStats(rows);
      const path = entry.path || '文件';
      const existing = state.changes.find((change) => change.path === path);
      if (existing) Object.assign(existing, stats);
      else state.changes.push({ path, ...stats });
    }
  }

  // ---------- Theme / agent UI ----------
  function readTheme(agent) {
    return safeStorageGet(THEME_KEY(agent)) || (agent === 'claude' ? 'light' : 'dark');
  }
  function applyTheme(theme, persist = true) {
    state.theme = theme === 'dark' ? 'dark' : 'light';
    app.dataset.theme = state.theme;
    document.documentElement.style.colorScheme = state.theme;
    const meta = document.querySelector('meta[name="theme-color"]');
    if (meta) meta.content = state.theme === 'dark' ? (state.agent === 'codex' ? '#191919' : '#1f1e1c') : (state.agent === 'codex' ? '#ffffff' : '#f7f6f1');
    try {
      const canvas = getComputedStyle(app).getPropertyValue('--canvas').trim();
      if (canvas && window.StaHost?.setChrome) window.StaHost.setChrome(canvas);
    } catch {}
    $('themeLabel').textContent = state.theme === 'dark' ? '浅色外观' : '深色外观';
    const use = $('themeButton').querySelector('use');
    use.setAttribute('href', state.theme === 'dark' ? '#i-sun' : '#i-moon');
    if (persist) safeStorageSet(THEME_KEY(state.agent), state.theme);
  }

  function updateAgentUi() {
    const copy = agentCopy();
    app.dataset.agent = state.agent;
    $('brandMark').innerHTML = MARKS[state.agent];
    $('brandName').textContent = copy.name;
    $('brandSub').textContent = copy.sub;
    $('agentLabel').textContent = copy.name;
    $('emptyArt').innerHTML = state.agent === 'claude' ? CRAB_SVG : MARKS[state.agent];
    $('emptyTitle').textContent = copy.emptyTitle;
    $('emptyCopy').textContent = copy.emptyCopy;
    promptEl.placeholder = copy.placeholder;
    $('suggestions').innerHTML = copy.suggestions.map(([iconName, text]) => `<button class="suggestion" data-suggestion="${esc(text)}">${icon(iconName)}<span>${esc(text)}</span></button>`).join('');
    document.querySelectorAll('.agent-choice').forEach((button) => {
      const active = button.dataset.agent === state.agent;
      button.classList.toggle('active', active);
      button.setAttribute('aria-selected', String(active));
    });
    $('cwdLabel').textContent = basename(state.cwd);
    $('cwdChip').title = state.cwd;
    $('sessionLabel').textContent = state.sessionTitle || '新会话';
    applyTheme(readTheme(state.agent), false);
    updateAuthUi();
    renderConfigChips();
    renderSessions();
    updateComposer();
  }

  function setConnection(text, kind) {
    connection.className = `connection ${kind || ''}`;
    connection.querySelector('span').textContent = text;
    connection.title = `ACP ${text}`;
  }

  function updateAuthUi() {
    const status = state.authStatus;
    const label = status?.label || status?.email || '';
    const kind = status?.kind || 'unknown';
    const ok = kind && !['none', 'unknown', 'not_logged_in', 'error'].includes(kind);
    const root = $('accountState');
    root.classList.toggle('ok', !!ok);
    root.classList.toggle('bad', kind === 'none' || kind === 'not_logged_in' || kind === 'error');
    const name = state.agent === 'claude' ? 'Claude Code' : 'Codex';
    if (!status) {
      $('accountLabel').textContent = '检查账号…';
      $('accountSub').textContent = 'CLI 登录状态';
    } else if (ok) {
      $('accountLabel').textContent = label || `${name} 已登录`;
      $('accountSub').textContent = status.email && status.email !== label ? status.email : 'CLI 账号已就绪';
    } else {
      $('accountLabel').textContent = `${name} 未登录`;
      $('accountSub').textContent = label || '点击完成登录';
    }
  }

  // ---------- Sessions ----------
  function saveSessions() {
    safeStorageSet(SESSIONS_KEY, JSON.stringify(state.sessions.slice(0, 60)));
  }

  function currentSession() {
    return state.sessions.find((session) => session.id === state.sessionId && session.agent === state.agent) || null;
  }

  function upsertSession(patch = {}) {
    if (!state.sessionId) return;
    let item = currentSession();
    if (!item) {
      item = { id: state.sessionId, agent: state.agent, title: patch.title || state.sessionTitle || '新会话', cwd: patch.cwd || state.cwd, updated: Date.now() };
      state.sessions.unshift(item);
    }
    Object.assign(item, patch, { updated: patch.updated || Date.now() });
    if (patch.title) state.sessionTitle = patch.title;
    state.sessions.sort((a, b) => b.updated - a.updated);
    saveSessions();
    renderSessions();
    $('sessionLabel').textContent = state.sessionTitle || '新会话';
  }

  function sessionDay(value) {
    const date = new Date(fmtDate(value) || Date.now());
    const today = new Date();
    const start = new Date(today.getFullYear(), today.getMonth(), today.getDate()).getTime();
    const day = new Date(date.getFullYear(), date.getMonth(), date.getDate()).getTime();
    if (day === start) return '今天';
    if (day === start - 86400000) return '昨天';
    if (day > start - 7 * 86400000) return '本周';
    return '更早';
  }

  function renderSessions() {
    const query = state.sessionQuery.trim().toLowerCase();
    const items = state.sessions
      .filter((session) => session.agent === state.agent)
      .filter((session) => !query || `${session.title || ''} ${session.cwd || ''}`.toLowerCase().includes(query))
      .sort((a, b) => b.updated - a.updated);
    $('sessionCount').textContent = items.length ? String(items.length) : '';
    if (!items.length) {
      sessionList.innerHTML = `<div class="sidebar-empty">${query ? '没有匹配的会话。' : '还没有会话。发送第一条消息，CLI 会在这里保留上下文。'}</div>`;
      return;
    }
    const groups = new Map();
    for (const session of items) {
      const day = sessionDay(session.updated);
      if (!groups.has(day)) groups.set(day, []);
      groups.get(day).push(session);
    }
    sessionList.innerHTML = [...groups.entries()].map(([day, rows]) => `
      <div class="session-date">${esc(day)}</div>
      ${rows.map((session) => {
        const active = session.id === state.sessionId;
        const running = active && state.busy;
        return `<button class="session-item ${active ? 'active' : ''} ${running ? 'running' : ''}" data-session-id="${esc(session.id)}">
          <span class="session-row"><span class="session-dot"></span><strong>${esc(session.title || '未命名会话')}</strong>${active ? '<span class="session-active-pill">当前</span>' : ''}</span>
          <span class="session-meta"><span>${icon('i-folder')}${esc(basename(session.cwd) || 'root')}</span><span>·</span><span>${esc(relativeTime(session.updated))}</span></span>
        </button>`;
      }).join('')}
    `).join('');
  }

  async function refreshSessions() {
    if (!state.capabilities?.sessionCapabilities?.list) return;
    try {
      const result = await rpc('session/list', {});
      const rows = Array.isArray(result?.sessions) ? result.sessions : [];
      for (const row of rows) {
        const id = row.sessionId || row.id;
        if (!id) continue;
        let item = state.sessions.find((session) => session.id === id && session.agent === state.agent);
        if (!item) {
          item = { id, agent: state.agent, title: row.title || '未命名会话', cwd: row.cwd || '/root', updated: fmtDate(row.updatedAt) || Date.now() };
          state.sessions.push(item);
        } else {
          item.title = row.title || item.title;
          item.cwd = row.cwd || item.cwd;
          item.updated = fmtDate(row.updatedAt) || item.updated;
        }
      }
      state.sessions.sort((a, b) => b.updated - a.updated);
      saveSessions();
      renderSessions();
    } catch {
      // A list failure must not keep the workspace from opening.
    }
  }

  // ---------- Config ----------
  function normalizeOptions(value) {
    if (Array.isArray(value)) return value.map((option) => ({ ...option, id: option.id || option.configId }));
    if (value && typeof value === 'object') return Object.entries(value).map(([id, option]) => ({ ...option, id: option.id || option.configId || id }));
    return [];
  }

  function optionValue(option) {
    return option?.valueId ?? option?.value ?? option?.id ?? '';
  }

  function flattenOptionValues(options) {
    const out = [];
    for (const option of options || []) {
      if (option && Array.isArray(option.options)) {
        out.push({ group: option.name || option.group || '其他', options: option.options });
      } else if (option) out.push(option);
    }
    return out;
  }

  function findConfig(category, id) {
    return state.configs.find((config) => config.category === category || config.id === id) || null;
  }
  function optionLabel(config) {
    if (!config) return '';
    const values = flattenOptionValues(config.options);
    for (const value of values) {
      if (value.options) continue;
      if (String(optionValue(value)) === String(config.currentValue)) return value.name || value.label || String(config.currentValue);
    }
    return String(config.currentValue ?? '');
  }
  function optionDescription(config) {
    if (!config) return '';
    const values = flattenOptionValues(config.options);
    for (const value of values) {
      if (value.options) continue;
      if (String(optionValue(value)) === String(config.currentValue)) return value.description || '';
    }
    return config.description || '';
  }
  function modelDisplayName(config) {
    const label = optionLabel(config);
    const description = optionDescription(config);
    if (/^default\b/i.test(label) && description) return description;
    return label || 'Model';
  }
  function applySessionConfig(result, replace = false) {
    if (!result) return;
    if (result.modes) state.legacyModes = result.modes;
    if (Array.isArray(result.configOptions) || result.configOptions && typeof result.configOptions === 'object') {
      state.configs = normalizeOptions(result.configOptions);
    } else if (replace) {
      state.configs = [];
    }
    if (!findConfig('model', 'model') && result.models?.availableModels?.length) {
      state.configs.push({
        id: 'model', name: 'Model', category: 'model', type: 'select',
        currentValue: result.models.currentModelId || result.models.availableModels[0].modelId,
        options: result.models.availableModels.map((model) => ({
          value: model.modelId || model.id,
          name: model.name || model.displayName || model.modelId || model.id,
          description: model.description,
        })),
      });
    }
    renderConfigChips();
  }

  function renderConfigChips() {
    const mode = findConfig('mode', 'mode');
    const model = findConfig('model', 'model');
    const effort = findConfig('thought_level', 'effort');
    const legacyMode = state.legacyModes?.availableModes?.find((item) => item.id === state.legacyModes.currentModeId);
    const modeName = mode ? optionLabel(mode) : legacyMode?.name || '';
    const modeValue = mode ? String(mode.currentValue) : state.legacyModes?.currentModeId || '';
    $('modeChip').hidden = false;
    $('modeLabel').textContent = modeName || (state.sessionId ? '权限' : '选择权限');
    $('modeChip').classList.toggle('danger', ['bypassPermissions', 'agent-full-access'].includes(modeValue));
    $('modeIcon').innerHTML = icon(MODE_ICON[modeValue] || 'i-shield');
    $('modeChip').title = mode?.description || legacyMode?.description || '会话权限模式';
    $('modelChip').hidden = false;
    $('modelLabel').textContent = model ? modelDisplayName(model) : (state.sessionId ? '未配置模型' : '选择模型');
    $('effortLabel').textContent = effort ? optionLabel(effort) : '';
    $('effortLabel').hidden = !effort;
    $('modelChip').title = model?.description || '模型';
    updateContextMeter();
  }

  function updateContextMeter() {
    const usage = state.usage;
    if (!usage) {
      $('contextMeter').textContent = '';
      return;
    }
    const used = Number(usage.used) || 0;
    const size = Number(usage.size) || 0;
    const percent = size ? Math.round((used / size) * 100) : 0;
    $('contextMeter').textContent = `${fmtTokens(used)}${size ? ` / ${fmtTokens(size)}` : ''}${size ? ` · ${percent}%` : ''}`;
  }

  async function setConfigOption(configId, value, type) {
    if (!state.sessionId) return;
    const before = JSON.stringify(state.configs);
    const target = state.configs.find((config) => config.id === configId);
    if (target) target.currentValue = value;
    renderConfigChips();
    try {
      const params = { sessionId: state.sessionId, configId, value };
      if (type === 'boolean') params.type = 'boolean';
      const result = await rpc('session/set_config_option', params);
      if (result?.configOptions) state.configs = normalizeOptions(result.configOptions);
      renderConfigChips();
      return true;
    } catch (error) {
      state.configs = JSON.parse(before);
      renderConfigChips();
      toast(error.message || '切换配置失败');
      return false;
    }
  }

  async function setLegacyMode(modeId) {
    if (!state.sessionId) return false;
    try {
      await rpc('session/set_mode', { sessionId: state.sessionId, modeId });
      if (state.legacyModes) state.legacyModes.currentModeId = modeId;
      renderConfigChips();
      return true;
    } catch (error) {
      toast(error.message || '切换模式失败');
      return false;
    }
  }

  // ---------- Messages ----------
  function clearConversation() {
    state.messages = [];
    state.tools.clear();
    state.plan = [];
    state.changes = [];
    state.usage = null;
    state.assistantId = null;
    state.permissionCount = 0;
    state.stickBottom = true;
    messageNodes.clear();
    messagesEl.innerHTML = '';
    emptyState.style.display = '';
    $('jumpLatest').hidden = true;
    updateContextMeter();
  }

  function findToolItem(toolCallId) {
    if (state.tools.has(toolCallId)) return state.tools.get(toolCallId);
    const item = { id: `tool-${toolCallId}`, type: 'tool', tool: { toolCallId, status: 'pending' } };
    state.messages.push(item);
    state.tools.set(toolCallId, item);
    return item;
  }

  function finishStreaming() {
    for (const message of state.messages) {
      if (message.role === 'assistant') message.streaming = false;
    }
  }

  function findAssistant(messageId) {
    if (messageId) {
      let item = state.messages.find((message) => message.id === messageId && message.role === 'assistant');
      if (!item) {
        item = { id: messageId, role: 'assistant', text: '', thinking: '', streaming: true };
        state.messages.push(item);
      }
      state.assistantId = messageId;
      return item;
    }
    let item = state.messages.find((message) => message.id === state.assistantId && message.role === 'assistant');
    if (!item) {
      item = { id: state.assistantId || idFor('a'), role: 'assistant', text: '', thinking: '', streaming: true };
      state.assistantId = item.id;
      state.messages.push(item);
    }
    return item;
  }

  function appendMessage(item) {
    state.messages.push(item);
    syncMessages();
  }

  function appendNotice(title, description, severity = 'info') {
    appendMessage({ id: idFor('notice'), type: 'notice', title, description, severity });
  }

  function appendError(message) {
    appendMessage({ id: idFor('error'), type: 'error', text: message });
  }

  function syncMessages() {
    const stick = state.stickBottom;
    const seen = new Set();
    for (const item of state.messages) {
      if (!item?.id) continue;
      seen.add(item.id);
      let node = messageNodes.get(item.id);
      if (!node) {
        node = createMessageNode(item);
        messageNodes.set(item.id, node);
        messagesEl.appendChild(node);
      }
      updateMessageNode(node, item);
    }
    for (const [id, node] of messageNodes) {
      if (!seen.has(id)) { node.remove(); messageNodes.delete(id); }
    }
    emptyState.style.display = state.messages.length ? 'none' : '';
    if (stick) {
      requestAnimationFrame(() => {
        thread.scrollTop = thread.scrollHeight;
        updateJumpLatest();
      });
    } else {
      updateJumpLatest();
    }
  }

  function scheduleSync(shouldScroll = false) {
    syncScroll = syncScroll || shouldScroll;
    if (syncScheduled) return;
    syncScheduled = true;
    requestAnimationFrame(() => {
      syncScheduled = false;
      const scroll = syncScroll;
      syncScroll = false;
      syncMessages();
      if (scroll) scrollBottom();
    });
  }

  function createMessageNode(item) {
    const node = document.createElement('article');
    node.dataset.messageId = item.id;
    if (item.type === 'tool') node.className = 'message tool-message';
    else if (item.type === 'notice') node.className = 'message notice-message';
    else if (item.type === 'error') node.className = 'message error-message';
    else if (item.type === 'plan') node.className = 'message plan-message';
    else if (item.type === 'compaction') node.className = 'message compaction-message';
    else node.className = `message ${item.role || 'assistant'}`;
    if (item.role === 'assistant') {
      node.innerHTML = `<details class="thinking" hidden><summary>${icon('i-brain')}<span>思考</span>${icon('i-chevron-right', 'chev')}</summary><div class="thinking-body"></div></details><div class="message-body"></div>`;
      node._thinking = node.querySelector('.thinking');
      node._thinkingBody = node.querySelector('.thinking-body');
      node._body = node.querySelector('.message-body');
    } else if (item.role === 'user') {
      node.innerHTML = '<div class="message-body"></div>';
      node._body = node.querySelector('.message-body');
    }
    return node;
  }

  function updateMessageNode(node, item) {
    if (item.type === 'tool') {
      const wasOpen = node.querySelector('.tool-card')?.dataset.userOpen === 'true';
      const signature = JSON.stringify(item.tool);
      if (node._signature === signature) return;
      node._signature = signature;
      node.innerHTML = renderToolCard(item.tool, wasOpen);
      return;
    }
    if (item.type === 'notice') {
      const sig = `${item.severity}|${item.title}|${item.description}`;
      if (node._signature === sig) return;
      node._signature = sig;
      const iconName = item.severity === 'error' ? 'i-alert' : item.severity === 'warning' ? 'i-alert' : 'i-info';
      node.innerHTML = `<div class="notice-card ${esc(item.severity || '')}">${icon(iconName)}<div><strong>${esc(item.title || '')}</strong>${item.description ? `<div>${esc(item.description)}</div>` : ''}</div></div>`;
      return;
    }
    if (item.type === 'error') {
      if (node._signature === item.text) return;
      node._signature = item.text;
      node.innerHTML = `<div class="error-card"><div class="error-head">${icon('i-alert')}请求失败</div><div class="error-body">${esc(item.text)}</div></div>`;
      return;
    }
    if (item.type === 'plan') {
      const sig = JSON.stringify(item.entries);
      if (node._signature === sig) return;
      node._signature = sig;
      node.innerHTML = renderPlanCard(item.entries);
      return;
    }
    if (item.type === 'compaction') {
      node.innerHTML = `<div class="compaction-rule"><span>${esc(item.text || '上下文已压缩')}</span></div>`;
      return;
    }
    if (item.role === 'user') {
      const sig = `${item.text}|${(item.attachments || []).map((a) => a.name).join(',')}`;
      if (node._signature === sig) return;
      node._signature = sig;
      const images = (item.attachments || []).map((attachment) => `<img src="${attachment.url}" alt="${esc(attachment.name)}">`).join('');
      node._body.innerHTML = `${images ? `<div class="user-images">${images}</div>` : ''}${item.text ? esc(item.text).replace(/\n/g, '<br>') : ''}`;
      return;
    }
    if (item.role === 'assistant') {
      const streamingClass = item.streaming ? 'streaming' : '';
      node.classList.toggle('streaming', !!item.streaming);
      if (item.thinking) {
        node._thinking.hidden = false;
        node._thinking.classList.toggle('active', !!item.streaming);
        if (node._thinkingBody.textContent !== item.thinking) node._thinkingBody.textContent = item.thinking;
        if (!node._thinking.dataset.userOpened) node._thinking.open = false;
      } else {
        node._thinking.hidden = true;
      }
      if (node._text !== item.text) {
        node._text = item.text;
        const html = item.text ? markdown(item.text) : (item.streaming ? '<p class="thinking-dots"><i></i><i></i><i></i></p>' : '');
        node._body.innerHTML = html;
      } else if (!item.text && item.streaming && !node._body.querySelector('.thinking-dots')) {
        node._body.innerHTML = '<p class="thinking-dots"><i></i><i></i><i></i></p>';
      } else if (!item.text && !item.streaming) {
        node._body.innerHTML = '';
      }
      node.dataset.streaming = streamingClass;
    }
  }

  function renderToolCard(tool, wasOpen = false) {
    const status = tool.status || 'pending';
    const statusClass = status === 'in_progress' ? 'running' : status === 'completed' ? 'completed' : status === 'failed' ? 'failed' : status === 'cancelled' ? 'cancelled' : 'pending';
    const stateText = ({ pending: '等待', in_progress: '运行中', completed: '完成', failed: '失败', cancelled: '已取消' })[status] || status;
    const stateIcon = status === 'in_progress' ? '<span class="spinner"></span>' : status === 'completed' ? icon('i-check-circle') : status === 'failed' ? icon('i-x') : status === 'cancelled' ? icon('i-x') : '<span class="status-dot"></span>';
    const content = renderToolContent(tool);
    const permission = tool.permission ? renderPermissionCard(tool) : '';
    const hasBody = !!content || !!permission;
    const activePermission = !!tool.permission && !tool.permission.decision;
    const open = wasOpen || activePermission || status === 'in_progress' || status === 'failed';
    const title = tool.title || tool.name || TOOL_KIND[tool.kind] || '工具调用';
    const sub = toolSubtitle(tool);
    const locations = (tool.locations || []).map((location) => location.path).filter(Boolean);
    return `<div class="tool-card ${statusClass} ${open ? 'open' : ''}" data-tool-card="${esc(tool.toolCallId || '')}" data-user-open="${wasOpen}">
      <button class="tool-head" data-tool-toggle aria-expanded="${open}">
        <span class="tool-icon">${icon(TOOL_ICON[tool.kind] || 'i-code')}</span>
        <span class="tool-copy"><span class="tool-title">${esc(title)}</span><span class="tool-sub">${esc(sub)}</span></span>
        <span class="tool-state ${statusClass}">${stateIcon}<span>${stateText}</span></span>
        ${hasBody ? icon('i-chevron-right', 'tool-chevron') : ''}
      </button>
      ${hasBody ? `<div class="tool-body">${content}${locations.length ? `<div class="tool-location">${icon('i-folder')}<span>${esc(locations[0])}</span></div>` : ''}${permission}</div>` : ''}
    </div>`;
  }

  function toolSubtitle(tool) {
    if (tool.kind === 'execute') {
      const command = tool.rawInput?.command || tool.rawInput?.cmd || tool.rawInput?.args?.join?.(' ');
      if (command) return String(command).split('\n')[0].slice(0, 150);
    }
    const location = tool.locations?.[0]?.path;
    if (location) return location;
    if (tool.rawInput?.file_path) return tool.rawInput.file_path;
    if (tool.rawInput?.path) return tool.rawInput.path;
    return TOOL_KIND[tool.kind] || tool.kind || 'other';
  }

  function renderToolContent(tool) {
    const chunks = [];
    const content = Array.isArray(tool.content) ? tool.content : [];
    for (const entry of content) {
      if (!entry) continue;
      if (entry.type === 'diff') {
        chunks.push(`<div class="tool-section">${renderDiff(entry.path, entry.oldText, entry.newText)}</div>`);
      } else if (entry.type === 'content') {
        const value = entry.content;
        if (value?.type === 'text') {
          if (tool.kind === 'execute') {
            const command = tool.rawInput?.command || tool.rawInput?.cmd;
            chunks.push(terminalBlock(`${command ? `$ ${command}\n\n` : ''}${value.text || ''}`, '输出'));
          } else chunks.push(`<div class="tool-section"><pre class="tool-text">${esc(value.text || '')}</pre></div>`);
        } else if (value?.type === 'image') {
          chunks.push(`<div class="tool-section"><span class="muted">图片输出</span></div>`);
        } else if (value?.type === 'resource_link') {
          chunks.push(`<div class="tool-section"><a href="${esc(value.uri || '#')}" target="_blank" rel="noreferrer">${esc(value.name || value.uri || '资源')}</a></div>`);
        }
      } else if (entry.type === 'resource_link') {
        chunks.push(`<div class="tool-section"><a href="${esc(entry.uri || '#')}" target="_blank" rel="noreferrer">${esc(entry.name || entry.uri || '资源')}</a></div>`);
      } else if (entry.type === 'terminal') {
        chunks.push(`<div class="tool-section">${terminalBlock(entry.text || entry.output || '终端输出', entry.title || '终端')}</div>`);
      }
    }
    if (!content.length) {
      const command = tool.rawInput?.command || tool.rawInput?.cmd;
      if (command) chunks.push(`<div class="tool-section">${terminalBlock(`$ ${command}\n`, '命令')}</div>`);
      if (tool.rawOutput != null) chunks.push(`<div class="tool-section"><pre class="tool-text mono">${esc(typeof tool.rawOutput === 'string' ? tool.rawOutput : JSON.stringify(tool.rawOutput, null, 2))}</pre></div>`);
    }
    return chunks.join('');
  }

  function terminalBlock(text, label = '终端') {
    return `<div class="terminal-block"><div class="terminal-head"><span class="dots"><i></i><i></i><i></i></span><span>${esc(label)}</span><button class="copy-btn" data-copy-code title="复制">${icon('i-copy')}</button></div><pre class="terminal-pre">${esc(text)}</pre></div>`;
  }

  function renderPermissionCard(tool) {
    const permission = tool.permission;
    const params = permission?.params || {};
    const options = Array.isArray(params.options) ? params.options : [];
    const input = params.toolCall?.rawInput ?? tool.rawInput;
    const preview = input == null ? '' : (typeof input === 'string' ? input : JSON.stringify(input, null, 2));
    if (permission.decision) return '';
    const ordered = [...options].sort((a, b) => optionRank(a) - optionRank(b));
    const buttons = ordered.length ? ordered.map((option, index) => {
      const kind = option.kind || '';
      const cls = index === 0 && kind.startsWith('allow') ? 'primary' : kind === 'allow_always' ? 'allow-soft' : kind.startsWith('reject') ? 'deny' : '';
      return `<button class="${cls}" data-permission-option="${esc(option.optionId)}" data-permission-kind="${esc(kind)}">${esc(option.name || option.optionId)}</button>`;
    }).join('') : '<button data-permission-option="" data-permission-kind="reject_once">取消</button>';
    return `<div class="permission-card" data-permission-id="${esc(permission.requestId)}">
      <div class="permission-kicker">${icon('i-hand')}需要你的确认</div>
      <div class="permission-title">${esc(tool.title || params.toolCall?.title || '允许这个操作吗？')}</div>
      <p class="permission-desc">${esc(tool.kind ? `${TOOL_KIND[tool.kind] || tool.kind} · ` : '')}${state.agent === 'codex' ? 'Codex' : 'Claude Code'} 正在请求继续执行。</p>
      ${preview ? `<pre class="permission-preview">${esc(preview.slice(0, 5000))}</pre>` : ''}
      <div class="permission-actions">${buttons}</div>
    </div>`;
  }

  function optionRank(option) {
    const kind = option.kind || '';
    if (kind === 'allow_once') return 0;
    if (kind === 'allow_always') return 1;
    if (kind === 'reject_once') return 2;
    if (kind === 'reject_always') return 3;
    return 4;
  }

  function renderPlanCard(entries) {
    return `<div class="plan-card"><div class="plan-head">${icon('i-shield')}执行计划</div>${entries.map((entry) => {
      const status = entry.status || 'pending';
      return `<div class="plan-row ${esc(status)}"><span class="plan-dot">${icon('i-check')}</span><span>${esc(entry.content || '')}</span></div>`;
    }).join('')}</div>`;
  }

  // ---------- Scroll / composer ----------
  function nearBottom() {
    return thread.scrollHeight - thread.scrollTop - thread.clientHeight < 130;
  }
  function scrollBottom(force = false) {
    if (force || nearBottom()) {
      state.stickBottom = true;
      requestAnimationFrame(() => {
        thread.scrollTop = thread.scrollHeight;
        updateJumpLatest();
      });
    }
  }
  function updateJumpLatest() {
    const show = state.messages.length > 0 && !nearBottom();
    $('jumpLatest').hidden = !show;
  }

  function updateComposer() {
    const hasText = !!promptEl.value.trim() || state.attachments.length > 0;
    sendButton.classList.toggle('busy', state.busy);
    sendButton.disabled = !state.busy && !hasText;
    $('workingLine').hidden = !state.busy;
    $('workingText').textContent = state.cancelling
      ? '正在停止…'
      : state.permissionCount
        ? '等待你的确认'
        : 'Agent 正在工作…';
    $('composerHint').textContent = state.busy ? '点击停止可中止当前任务' : 'Enter 发送 · Shift+Enter 换行';
    $('workingStop').hidden = !state.busy;
    updateContextMeter();
  }

  function focusPrompt() {
    requestAnimationFrame(() => promptEl.focus());
  }

  function hydrateServerCwd() {
    fetch('/api/info').then((response) => response.json()).then((info) => {
      if (!safeStorageGet(CWD_KEY(state.agent)) && typeof info?.cwd === 'string' && info.cwd.startsWith('/')) {
        state.cwd = info.cwd;
        $('cwdLabel').textContent = basename(state.cwd);
        $('cwdChip').title = state.cwd;
      }
    }).catch(() => {});
  }

  // ---------- ACP connection ----------
  function send(value) {
    if (state.ws?.readyState === WebSocket.OPEN) {
      state.ws.send(JSON.stringify(value));
      return true;
    }
    return false;
  }

  function rpc(method, params, options = {}) {
    return new Promise((resolve, reject) => {
      if (!state.ws || state.ws.readyState !== WebSocket.OPEN) {
        reject(new Error('ACP 连接尚未建立'));
        return;
      }
      const id = state.rpcId++;
      const key = String(id);
      const timeout = options.timeout === undefined ? 45000 : options.timeout;
      const entry = { resolve, reject, timer: null };
      if (timeout > 0) {
        entry.timer = setTimeout(() => {
          if (state.pending.has(key)) {
            state.pending.delete(key);
            reject(new Error(`ACP 请求超时：${method}`));
          }
        }, timeout);
      }
      state.pending.set(key, entry);
      send({ jsonrpc: '2.0', id, method, params });
    });
  }

  function rejectPending(error) {
    for (const { reject, timer } of state.pending.values()) {
      if (timer) clearTimeout(timer);
      reject(error);
    }
    state.pending.clear();
  }

  async function connect({ restore = false } = {}) {
    if (state.connecting && state.connectPromise) return state.connectPromise;
    if (state.ws?.readyState === WebSocket.OPEN && state.initialized) return true;
    state.connecting = true;
    state.manualClose = false;
    setConnection('连接中', 'connecting');
    $('banner').hidden = true;
    state.connectPromise = new Promise((resolve) => {
      let settled = false;
      const finish = (ok) => {
        if (settled) return;
        settled = true;
        state.connecting = false;
        state.connectPromise = null;
        resolve(ok);
      };
      let ws;
      try {
        ws = new WebSocket(`ws://${location.host}/acp?agent=${encodeURIComponent(state.agent)}`);
      } catch (error) {
        setConnection('连接失败', 'error');
        showBanner(error.message || '无法连接 ACP', '重试', () => connect());
        finish(false);
        return;
      }
      state.ws = ws;
      const timer = setTimeout(() => {
        try { ws.close(); } catch {}
        setConnection('连接失败', 'error');
        finish(false);
      }, 20000);
      ws.onopen = async () => {
        if (state.ws !== ws) return;
        clearTimeout(timer);
        try {
          const result = await rpc('initialize', {
            protocolVersion: 1,
            clientCapabilities: {
              fs: { readTextFile: false, writeTextFile: false },
              terminal: false,
              session: { configOptions: { boolean: {} }, notices: {}, compaction: {} },
              elicitation: { form: {}, url: {} },
              auth: { terminal: false },
            },
            clientInfo: { name: 'Sta ACP Workspace', version: '2.0.0' },
          }, { timeout: 30000 });
          state.initialized = true;
          state.capabilities = result?.agentCapabilities || {};
          state.agentInfo = result?.agentInfo || null;
          state.authMethods = Array.isArray(result?.authMethods) ? result.authMethods : [];
          setConnection('已连接', 'connected');
          state.reconnectAttempts = 0;
          $('banner').hidden = true;
          await refreshSessions();
          if (restore && state.sessionId) await restoreSession();
          finish(true);
          focusPrompt();
        } catch (error) {
          setConnection('连接失败', 'error');
          showBanner(error.message || 'CLI 初始化失败', '重试', () => connect({ restore }));
          finish(false);
        }
      };
      ws.onmessage = (event) => {
        if (state.ws !== ws) return;
        try { handleMessage(JSON.parse(event.data)); } catch (error) { console.debug('[acp]', error); }
      };
      ws.onerror = () => { if (state.ws === ws) setConnection('连接失败', 'error'); };
      ws.onclose = () => {
        clearTimeout(timer);
        if (state.manualClose || state.ws !== ws) return;
        state.initialized = false;
        state.ws = null;
        setConnection('已断开', 'error');
        rejectPending(new Error('与 CLI 的连接已中断'));
        state.busy = false;
        state.cancelling = false;
        updateComposer();
        if (state.reconnectAttempts < 2) {
          state.reconnectAttempts += 1;
          setTimeout(() => connect({ restore: true }), 1400);
        } else {
          showBanner('与 CLI 的连接已断开。CLI 可能仍在后台运行。', '重连', () => {
            state.reconnectAttempts = 0;
            connect({ restore: true });
          });
        }
        finish(false);
      };
    });
    return state.connectPromise;
  }

  function disconnect() {
    state.manualClose = true;
    try { state.ws?.close(); } catch {}
    state.ws = null;
    state.initialized = false;
    state.connecting = false;
    state.connectPromise = null;
    rejectPending(new Error('连接已关闭'));
  }

  async function restoreSession() {
    if (!state.sessionId) return;
    const session = currentSession();
    const cwd = session?.cwd || state.cwd;
    const snapshot = state.messages.slice();
    try {
      clearConversation();
      state.sessionId = session?.id || state.sessionId;
      const result = await rpc('session/load', {
        sessionId: state.sessionId,
        cwd,
        mcpServers: [],
      }, { timeout: 45000 });
      applySessionConfig(result, true);
      finishStreaming();
      state.sessionTitle = session?.title || '已恢复会话';
      upsertSession({ title: state.sessionTitle, cwd });
      syncMessages();
    } catch {
      state.messages = snapshot;
      syncMessages();
      state.sessionId = session?.id || state.sessionId;
    }
  }

  // ---------- Agent messages ----------
  function handleMessage(message) {
    if (message.method && message.id !== undefined) {
      handleAgentRequest(message);
      return;
    }
    if (message.method) {
      handleNotification(message);
      return;
    }
    if (message.id !== undefined && state.pending.has(String(message.id))) {
      const pending = state.pending.get(String(message.id));
      state.pending.delete(String(message.id));
      if (pending.timer) clearTimeout(pending.timer);
      if (message.error) {
        const error = new Error(message.error.message || 'ACP 请求失败');
        error.code = message.error.code;
        error.data = message.error.data;
        pending.reject(error);
      } else {
        pending.resolve(message.result);
      }
    }
  }

  function handleNotification(message) {
    if (message.method === 'session/update') {
      if (message.params?.sessionId === state.sessionId) handleUpdate(message.params.update);
      return;
    }
    if (message.method === '_auth/status_update') {
      if (message.params?.authStatus) {
        state.authStatus = message.params.authStatus;
        updateAuthUi();
      }
      return;
    }
    if (message.method === 'elicitation/complete') {
      if (state.activeElicitation && message.params?.elicitationId === state.activeElicitation.params?.elicitationId) {
        state.activeElicitation = null;
        closeSheet(false);
      }
      return;
    }
    if (message.method === '$/bridge_ready') {
      if (message.params?.lastError) state.lastStderr = message.params.lastError;
      return;
    }
    if (message.method === '$/bridge_error') {
      const text = message.params?.message || 'CLI 启动失败';
      state.lastStderr = text;
      appendError(text);
      showBanner(text, '查看诊断', () => openLogsSheet());
      return;
    }
    if (message.method === '$/bridge_exit') {
      const text = message.params?.message || 'CLI 进程已退出';
      state.lastStderr = text;
      state.busy = false;
      state.cancelling = false;
      updateComposer();
      showBanner(text, '重启 CLI', () => restartBridge());
      appendNotice('CLI 已退出', (message.params?.stderr || []).slice(-3).join('\n') || text, 'error');
      return;
    }
    if (message.method === '$/bridge_log') {
      state.lastStderr = message.params?.text || state.lastStderr;
      return;
    }
  }

  function handleUpdate(update) {
    if (!update) return;
    switch (update.sessionUpdate) {
      case 'agent_message_chunk': {
        if (update.content?.type === 'text') {
          const item = findAssistant(update.messageId);
          item.text += update.content.text || '';
          item.streaming = true;
          scheduleSync(true);
        } else if (update.content?.type === 'image') {
          const item = findAssistant(update.messageId);
          item.images = item.images || [];
          item.images.push(update.content);
          scheduleSync(true);
        }
        break;
      }
      case 'agent_thought_chunk': {
        if (update.content?.type === 'text') {
          const item = findAssistant(update.messageId);
          item.thinking += update.content.text || '';
          item.streaming = true;
          scheduleSync(true);
        }
        break;
      }
      case 'user_message_chunk': {
        if (update.content?.type === 'text') {
          const id = update.messageId || idFor('u');
          let item = state.messages.find((message) => message.id === id);
          if (!item) {
            item = { id, role: 'user', text: '', attachments: [] };
            state.messages.push(item);
          }
          item.text += update.content.text || '';
          scheduleSync(true);
        }
        break;
      }
      case 'tool_call':
      case 'tool_call_update': {
        const item = findToolItem(update.toolCallId || idFor('tool'));
        Object.assign(item.tool, update);
        if (item.tool.status === 'completed' || item.tool.status === 'failed') collectChanges(item.tool);
        syncMessages();
        if (item.tool.status === 'failed') scrollBottom();
        break;
      }
      case 'plan': {
        state.plan = Array.isArray(update.entries) ? update.entries : [];
        const existing = state.messages.find((message) => message.type === 'plan');
        if (state.plan.length) {
          if (existing) existing.entries = state.plan;
          else state.messages.push({ id: 'plan', type: 'plan', entries: state.plan });
        } else if (existing) {
          state.messages = state.messages.filter((message) => message.type !== 'plan');
        }
        syncMessages();
        break;
      }
      case 'plan_update': {
        if (update.plan?.type === 'markdown') {
          const item = findAssistant(`plan-${update.plan.planId || 'main'}`);
          item.text += update.plan.content || '';
          item.streaming = true;
          syncMessages();
        }
        break;
      }
      case 'usage_update':
        state.usage = update;
        updateContextMeter();
        if (state.sheetKind === 'details') openDetailsSheet();
        break;
      case 'available_commands_update':
        state.commands = Array.isArray(update.availableCommands) ? update.availableCommands : [];
        break;
      case 'current_mode_update': {
        const mode = findConfig('mode', 'mode');
        if (mode) mode.currentValue = update.currentModeId;
        else if (state.legacyModes) state.legacyModes.currentModeId = update.currentModeId;
        renderConfigChips();
        break;
      }
      case 'config_option_update':
        state.configs = normalizeOptions(update.configOptions || []);
        renderConfigChips();
        break;
      case 'session_info_update':
        if (update.title) {
          state.sessionTitle = update.title;
          upsertSession({ title: update.title });
        }
        if (update.updatedAt) upsertSession({ updated: fmtDate(update.updatedAt) });
        break;
      case 'notice':
        appendNotice(update.title || '提示', update.description || '', update.severity || 'info');
        break;
      case 'compaction_update':
        appendMessage({ id: idFor('compaction'), type: 'compaction', text: update.status === 'completed' ? '上下文已压缩' : '正在压缩上下文…' });
        break;
      default:
        break;
    }
    if (state.usage) {
      const used = Number(state.usage.used) || 0;
      const size = Number(state.usage.size) || 0;
      $('contextMeter').textContent = `${fmtTokens(used)}${size ? ` / ${fmtTokens(size)} · ${Math.round((used / size) * 100)}%` : ''}`;
    }
  }

  function handleAgentRequest(message) {
    if (message.method === 'session/request_permission') {
      const params = message.params || {};
      const toolCall = params.toolCall || {};
      const item = findToolItem(toolCall.toolCallId || idFor('tool'));
      Object.assign(item.tool, toolCall);
      item.tool.permission = { requestId: message.id, params, decision: null };
      state.permissionCount += 1;
      syncMessages();
      scrollBottom();
      updateComposer();
      return;
    }
    if (message.method === 'elicitation/create') {
      showElicitation(message);
      return;
    }
    if (message.method?.startsWith('fs/') || message.method?.startsWith('terminal/')) {
      sendRpcError(message.id, -32601, '此客户端不提供 filesystem/terminal 能力');
      return;
    }
    sendRpcError(message.id, -32601, `不支持的客户端方法：${message.method}`);
  }

  function sendRpcResult(id, result) {
    send({ jsonrpc: '2.0', id, result });
  }
  function sendRpcError(id, code, message) {
    send({ jsonrpc: '2.0', id, error: { code, message } });
  }

  function respondPermission(requestId, optionId, optionKind) {
    const item = [...state.tools.values()].find((entry) => String(entry.tool.permission?.requestId) === String(requestId));
    if (!item) return;
    const permission = item.tool.permission;
    if (optionId) sendRpcResult(permission.requestId, { outcome: { outcome: 'selected', optionId } });
    else sendRpcResult(permission.requestId, { outcome: { outcome: 'cancelled' } });
    permission.decision = {
      optionId: optionId || null,
      kind: optionKind || (optionId ? 'allow_once' : 'reject_once'),
      label: optionId ? '已允许' : '已取消',
    };
    state.permissionCount = Math.max(0, state.permissionCount - 1);
    syncMessages();
    updateComposer();
  }

  // ---------- Session actions ----------
  async function newSession({ silent = false, draft = false } = {}) {
    if (state.busy) {
      if (!silent) toast('当前任务运行中');
      return null;
    }
    if (!state.ws || state.ws.readyState !== WebSocket.OPEN || !state.initialized) {
      const ok = await connect();
      if (!ok) return null;
    }
    const cwd = state.cwd || '/root';
    if (!isAbsolutePath(cwd)) {
      toast('工作目录必须是绝对路径');
      return null;
    }
    state.sessionId = null;
    state.sessionTitle = '新会话';
    clearConversation();
    $('sessionLabel').textContent = '新会话';
    try {
      const result = await rpc('session/new', { cwd, mcpServers: [] }, { timeout: 60000 });
      state.sessionId = result?.sessionId || null;
      if (!state.sessionId) throw new Error('CLI 没有返回 sessionId');
      applySessionConfig(result, true);
      state.sessionTitle = '新会话';
      if (!draft) upsertSession({ title: '新会话', cwd });
      syncMessages();
      emptyState.style.display = '';
      focusPrompt();
      return state.sessionId;
    } catch (error) {
      if (/auth|login|登录|认证/i.test(error.message || '')) {
        state.authStatus = { kind: 'none', label: error.message };
        updateAuthUi();
        openAuthSheet(error.message);
      } else if (!silent) {
        toast(error.message || '创建会话失败');
        appendError(error.message || '创建会话失败');
      }
      return null;
    }
  }

  async function ensureDraftSession() {
    if (state.sessionId) return true;
    if (state.draftPromise) return state.draftPromise;
    state.draftPromise = newSession({ silent: true, draft: true })
      .then((id) => !!id)
      .finally(() => { state.draftPromise = null; });
    return state.draftPromise;
  }

  async function loadSession(sessionId) {
    if (state.busy) { toast('当前任务运行中'); return; }
    const session = state.sessions.find((item) => item.id === sessionId && item.agent === state.agent);
    if (!session) return;
    if (sessionId === state.sessionId) { closeSidebar(); return; }
    if (!state.ws || state.ws.readyState !== WebSocket.OPEN || !state.initialized) {
      const ok = await connect();
      if (!ok) return;
    }
    state.sessionId = sessionId;
    state.sessionTitle = session.title || '会话';
    state.cwd = session.cwd || state.cwd;
    $('cwdLabel').textContent = basename(state.cwd);
    $('cwdChip').title = state.cwd;
    clearConversation();
    updateComposer();
    try {
      let result;
      if (state.capabilities.loadSession) {
        result = await rpc('session/load', { sessionId, cwd: state.cwd, mcpServers: [] }, { timeout: 60000 });
      } else if (state.capabilities.sessionCapabilities?.resume) {
        result = await rpc('session/resume', { sessionId, cwd: state.cwd, mcpServers: [] }, { timeout: 60000 });
      } else {
        toast('当前 Agent 不支持恢复历史会话');
        state.sessionId = null;
        return;
      }
      applySessionConfig(result, true);
      finishStreaming();
      state.sessionTitle = session.title || '会话';
      upsertSession({ title: state.sessionTitle, cwd: state.cwd });
      $('sessionLabel').textContent = state.sessionTitle;
      closeSidebar();
      syncMessages();
    } catch (error) {
      state.sessionId = null;
      toast(error.message || '恢复会话失败');
      closeSidebar();
    }
  }

  async function sendPrompt() {
    if (state.busy) { cancelPrompt(); return; }
    const text = promptEl.value.trim();
    if (!text && !state.attachments.length) return;
    if (!state.ws || state.ws.readyState !== WebSocket.OPEN || !state.initialized) {
      const ok = await connect();
      if (!ok) return;
    }
    if (!state.sessionId) {
      const id = await newSession({ silent: true });
      if (!id) return;
    }
    if (state.attachments.length && state.capabilities.promptCapabilities?.image === false) {
      toast('当前 CLI 不支持图片输入');
      return;
    }
    const attachments = state.attachments.slice();
    const blocks = [];
    if (text) blocks.push({ type: 'text', text });
    for (const attachment of attachments) blocks.push({ type: 'image', data: attachment.data, mimeType: attachment.mime });
    const local = { id: idFor('u'), role: 'user', text, attachments, blocks };
    state.messages.push(local);
    state.attachments = [];
    renderAttachments();
    promptEl.value = '';
    autoSizePrompt();
    state.busy = true;
    state.cancelling = false;
    state.assistantId = null;
    state.permissionCount = 0;
    updateComposer();
    syncMessages();
    scrollBottom(true);
    if (!text) {
      // ACP requires at least one prompt block; an image-only prompt is valid.
    }
    const title = text ? text.replace(/\s+/g, ' ').slice(0, 48) : '图片任务';
    if (!currentSession() || currentSession()?.title === '新会话') {
      upsertSession({ title, cwd: state.cwd });
      $('sessionLabel').textContent = title;
    }
    try {
      const result = await rpc('session/prompt', { sessionId: state.sessionId, prompt: blocks }, { timeout: 0 });
      const assistant = state.messages.find((message) => message.role === 'assistant' && message.streaming);
      if (assistant) assistant.streaming = false;
      if (result?.stopReason === 'cancelled') {
        appendNotice('已停止', '本次任务已取消。', 'warning');
      }
    } catch (error) {
      const assistant = state.messages.find((message) => message.role === 'assistant' && message.streaming);
      if (assistant) assistant.streaming = false;
      if (/auth|login|登录|认证/i.test(error.message || '')) {
        state.authStatus = { kind: 'none', label: error.message };
        updateAuthUi();
        openAuthSheet(error.message);
      } else {
        appendError(error.message || '请求失败');
      }
    } finally {
      state.busy = false;
      state.cancelling = false;
      state.permissionCount = 0;
      finishStreaming();
      updateComposer();
      syncMessages();
      scrollBottom();
    }
  }

  function cancelPrompt() {
    if (!state.sessionId || !state.busy) return;
    state.cancelling = true;
    send({ jsonrpc: '2.0', method: 'session/cancel', params: { sessionId: state.sessionId } });
    updateComposer();
    if (state.activeElicitation) {
      sendElicitationResponse('cancel');
    }
    setTimeout(() => {
      if (state.cancelling) {
        state.busy = false;
        state.cancelling = false;
        state.permissionCount = 0;
        updateComposer();
        appendNotice('已停止', '停止请求已发送。', 'warning');
      }
    }, 5000);
  }

  async function restartBridge() {
    try {
      await rpc('$/bridge/restart', {}, { timeout: 30000 });
      state.initialized = true;
      setConnection('已连接', 'connected');
      toast('CLI 已重启');
      await restoreSession();
    } catch (error) {
      showBanner(error.message || '重启失败', '重试', restartBridge);
    }
  }

  // ---------- Attachments ----------
  function renderAttachments() {
    const root = $('attachments');
    root.hidden = !state.attachments.length;
    root.innerHTML = state.attachments.map((attachment, index) => `<div class="attachment"><img src="${attachment.url}" alt="${esc(attachment.name)}"><button data-remove-attachment="${index}" aria-label="移除">${icon('i-x')}</button></div>`).join('');
  }

  function addAttachment(file) {
    return new Promise((resolve) => {
      if (state.attachments.length >= 3) { toast('最多添加 3 张图片'); resolve(false); return; }
      if (!file.type.startsWith('image/')) { toast('目前只支持图片附件'); resolve(false); return; }
      if (file.size > 8 * 1024 * 1024) { toast('单张图片不能超过 8 MB'); resolve(false); return; }
      const reader = new FileReader();
      reader.onload = () => {
        const url = String(reader.result || '');
        const comma = url.indexOf(',');
        state.attachments.push({ name: file.name, mime: file.type, data: comma >= 0 ? url.slice(comma + 1) : '', url });
        renderAttachments();
        updateComposer();
        resolve(true);
      };
      reader.onerror = () => resolve(false);
      reader.readAsDataURL(file);
    });
  }

  // ---------- Slash commands ----------
  function updateSlashMenu() {
    const menu = $('slashMenu');
    const value = promptEl.value;
    const cursor = promptEl.selectionStart || value.length;
    const before = value.slice(0, cursor);
    const match = before.match(/(^|\n)\/([^\s]*)$/);
    if (!match || !state.commands.length) {
      menu.hidden = true;
      state.slashStart = -1;
      return;
    }
    const filter = match[2].toLowerCase();
    const matches = state.commands.filter((command) => String(command.name || '').toLowerCase().startsWith(filter)).slice(0, 8);
    if (!matches.length) { menu.hidden = true; state.slashStart = -1; return; }
    state.slashStart = cursor - filter.length - 1;
    state.slashIndex = Math.min(state.slashIndex, matches.length - 1);
    menu.innerHTML = matches.map((command, index) => `<button class="slash-item ${index === state.slashIndex ? 'active' : ''}" data-command="${esc(command.name)}"><span class="slash-name">/${esc(command.name)}</span><span class="slash-desc">${esc(command.description || command.input?.hint || '')}</span></button>`).join('');
    menu.hidden = false;
  }

  function chooseCommand(name) {
    const value = promptEl.value;
    const cursor = promptEl.selectionStart || value.length;
    const before = value.slice(0, state.slashStart >= 0 ? state.slashStart : cursor);
    const after = value.slice(cursor);
    promptEl.value = `${before}/${name} ${after}`;
    promptEl.selectionStart = promptEl.selectionEnd = before.length + name.length + 2;
    $('slashMenu').hidden = true;
    state.slashStart = -1;
    autoSizePrompt();
    updateComposer();
    promptEl.focus();
  }

  // ---------- Sheets ----------
  function openSheet(kind, kicker, title, html, onClose = null) {
    state.sheetKind = kind;
    state.sheetOnClose = onClose;
    $('sheetKicker').textContent = kicker || '';
    sheetTitle.textContent = title || '';
    sheetBody.innerHTML = html;
    sheetBody.scrollTop = 0;
    sheetLayer.hidden = false;
  }

  function closeSheet(notify = true) {
    if (sheetLayer.hidden) return;
    const onClose = state.sheetOnClose;
    state.sheetKind = null;
    state.sheetOnClose = null;
    sheetLayer.hidden = true;
    if (notify && onClose) onClose();
  }

  function optionValues(config) {
    const flat = [];
    for (const value of flattenOptionValues(config?.options)) {
      if (value.options) {
        for (const inner of value.options) flat.push({ ...inner, group: value.group });
      } else flat.push(value);
    }
    return flat;
  }

  async function openModelSheet() {
    if (!state.sessionId && !(await ensureDraftSession())) return;
    const model = findConfig('model', 'model');
    const effort = findConfig('thought_level', 'effort');
    const fast = findConfig('model_config', 'fast');
    const others = state.configs.filter((config) => !['model', 'mode', 'thought_level', 'model_config', 'agent', 'collaboration_mode'].includes(config.category || config.id));
    const modelOptions = model ? optionValues(model) : [];
    const effortOptions = effort ? optionValues(effort) : [];
    let html = '';
    if (model) {
      html += `<section class="sheet-section"><div class="sheet-label">模型</div><div class="opt-list">${modelOptions.map((option) => `<button class="opt-row ${String(optionValue(option)) === String(model.currentValue) ? 'active' : ''}" data-model-value="${esc(optionValue(option))}"><span class="opt-check">${icon('i-check')}</span><span class="opt-copy"><span class="opt-title">${esc(option.name || optionValue(option))}</span>${option.description ? `<span class="opt-desc">${esc(option.description)}</span>` : ''}</span></button>`).join('')}</div></section>`;
    }
    if (effort) {
      html += `<section class="sheet-section"><div class="sheet-label">思考强度</div><div class="seg">${effortOptions.map((option) => `<button class="${String(optionValue(option)) === String(effort.currentValue) ? 'active' : ''}" data-effort-value="${esc(optionValue(option))}">${esc(option.name || optionValue(option))}</button>`).join('')}</div></section>`;
    }
    if (fast) {
      const values = optionValues(fast);
      html += `<section class="sheet-section"><div class="sheet-label">${esc(fast.name || 'Fast mode')}</div>${fast.type === 'boolean' ? `<button class="opt-row ${fast.currentValue ? 'active' : ''}" data-fast-toggle="true"><span class="opt-check">${icon('i-check')}</span><span class="opt-copy"><span class="opt-title">${fast.currentValue ? '已开启' : '已关闭'}</span>${fast.description ? `<span class="opt-desc">${esc(fast.description)}</span>` : ''}</span></button>` : `<div class="seg">${values.map((option) => `<button class="${String(optionValue(option)) === String(fast.currentValue) ? 'active' : ''}" data-fast-value="${esc(optionValue(option))}">${esc(option.name || optionValue(option))}</button>`).join('')}</div>`}</section>`;
    }
    if (others.length) {
      html += `<section class="sheet-section"><div class="sheet-label">更多设置</div>${others.map((config) => `<div class="field"><label>${esc(config.name || config.id)}</label><select data-other-config="${esc(config.id)}">${optionValues(config).map((option) => `<option value="${esc(optionValue(option))}" ${String(optionValue(option)) === String(config.currentValue) ? 'selected' : ''}>${esc(option.name || optionValue(option))}</option>`).join('')}</select></div>`).join('')}</section>`;
    }
    if (!html) html = `<div class="sheet-section"><p class="sheet-note">CLI 还没有返回模型配置。先发送一条消息，或在账号管理器中完成登录。</p></div>`;
    html += `<div class="sheet-section"><p class="sheet-note">模型与思考强度由本地 CLI 的当前账号实时提供。</p></div>`;
    openSheet('model', 'SESSION CONFIG', '模型与思考', html);
    sheetBody.querySelectorAll('[data-model-value]').forEach((button) => {
      button.addEventListener('click', async () => {
        const ok = await setConfigOption(model.id || 'model', button.dataset.modelValue);
        if (ok) closeSheet();
      });
    });
    sheetBody.querySelectorAll('[data-effort-value]').forEach((button) => {
      button.addEventListener('click', async () => {
        const ok = await setConfigOption(effort.id || 'effort', button.dataset.effortValue);
        if (ok) closeSheet();
      });
    });
    sheetBody.querySelectorAll('[data-fast-value]').forEach((button) => {
      button.addEventListener('click', async () => {
        const ok = await setConfigOption(fast.id || 'fast', button.dataset.fastValue);
        if (ok) closeSheet();
      });
    });
    sheetBody.querySelector('[data-fast-toggle]')?.addEventListener('click', async () => {
      const ok = await setConfigOption(fast.id || 'fast', !fast.currentValue, 'boolean');
      if (ok) closeSheet();
    });
    sheetBody.querySelectorAll('[data-other-config]').forEach((select) => {
      select.addEventListener('change', async () => {
        const config = state.configs.find((item) => item.id === select.dataset.otherConfig);
        const ok = await setConfigOption(select.dataset.otherConfig, select.value, config?.type === 'boolean' ? 'boolean' : undefined);
        if (ok) closeSheet();
      });
    });
  }

  async function openModeSheet() {
    if (!state.sessionId && !(await ensureDraftSession())) return;
    const mode = findConfig('mode', 'mode');
    const collaboration = findConfig('collaboration_mode', 'collaboration_mode');
    const modes = mode ? optionValues(mode) : (state.legacyModes?.availableModes || []);
    let html = '';
    if (modes.length) {
      html += `<section class="sheet-section"><div class="sheet-label">权限模式</div><div class="opt-list">${modes.map((option) => {
        const value = optionValue(option);
        const active = String(value) === String(mode?.currentValue ?? state.legacyModes?.currentModeId);
        const risky = ['bypassPermissions', 'agent-full-access'].includes(String(value));
        return `<button class="opt-row ${active ? 'active' : ''}" data-mode-value="${esc(value)}"><span class="opt-check">${icon('i-check')}</span><span class="opt-copy"><span class="opt-title ${risky ? 'risk' : ''}">${esc(option.name || value)}</span>${option.description ? `<span class="opt-desc">${esc(option.description)}</span>` : ''}</span></button>`;
      }).join('')}</div></section>`;
    }
    if (collaboration) {
      html += `<section class="sheet-section"><div class="sheet-label">协作模式</div><div class="seg">${optionValues(collaboration).map((option) => `<button class="${String(optionValue(option)) === String(collaboration.currentValue) ? 'active' : ''}" data-collab-value="${esc(optionValue(option))}">${esc(option.name || optionValue(option))}</button>`).join('')}</div></section>`;
    }
    if (!html) html = `<div class="sheet-section"><p class="sheet-note">CLI 还没有返回权限模式。</p></div>`;
    html += `<div class="sheet-section"><p class="sheet-note">权限模式会立即应用到当前 CLI 会话。高风险模式会跳过部分确认。</p></div>`;
    openSheet('mode', 'PERMISSIONS', '权限与协作', html);
    sheetBody.querySelectorAll('[data-mode-value]').forEach((button) => {
      button.addEventListener('click', async () => {
        const value = button.dataset.modeValue;
        const ok = mode ? await setConfigOption(mode.id || 'mode', value) : await setLegacyMode(value);
        if (ok) { closeSheet(); toast('权限模式已更新'); }
      });
    });
    sheetBody.querySelectorAll('[data-collab-value]').forEach((button) => {
      button.addEventListener('click', async () => {
        const ok = await setConfigOption(collaboration.id || 'collaboration_mode', button.dataset.collabValue);
        if (ok) { closeSheet(); toast('协作模式已更新'); }
      });
    });
  }

  function openCwdSheet() {
    const html = `<section class="sheet-section"><div class="sheet-label">工作目录</div><p class="sheet-note">CLI 会在这个绝对路径下读取文件、运行命令。修改后会开启一个新会话。</p><input class="sheet-input" id="cwdInput" value="${esc(state.cwd)}" spellcheck="false"><div class="seg" style="margin-top:10px">${['/root', '/workspace', '/tmp'].map((path) => `<button data-quick-cwd="${esc(path)}">${esc(path)}</button>`).join('')}</div><div class="sheet-actions"><button class="btn" data-close-sheet>取消</button><button class="btn primary" id="saveCwd">保存并新建</button></div></section>`;
    openSheet('cwd', 'WORKSPACE', '工作目录', html);
    const input = $('cwdInput');
    sheetBody.querySelectorAll('[data-quick-cwd]').forEach((button) => button.addEventListener('click', () => { input.value = button.dataset.quickCwd; }));
    $('saveCwd').addEventListener('click', async () => {
      const value = input.value.trim();
      if (!isAbsolutePath(value)) { toast('请输入绝对路径，例如 /root'); return; }
      state.cwd = value;
      safeStorageSet(CWD_KEY(state.agent), value);
      $('cwdLabel').textContent = basename(value);
      $('cwdChip').title = value;
      closeSheet(false);
      if (state.messages.length || state.sessionId) await newSession();
      else focusPrompt();
    });
  }

  function openAuthSheet(reason = '') {
    const status = state.authStatus;
    const name = state.agent === 'claude' ? 'Claude Code' : 'Codex';
    const isBad = !status || ['none', 'not_logged_in', 'error'].includes(status.kind);
    const methods = state.authMethods || [];
    let html = `<section class="sheet-section"><div class="auth-box"><div class="auth-title">${icon(isBad ? 'i-lock' : 'i-check-circle')}${isBad ? `${name} 尚未登录` : `${name} 已就绪`}</div><p class="auth-copy">${esc(reason || status?.label || '使用本地 CLI 的登录状态。登录信息不会上传到 Sta。')}</p>${isBad ? `<p class="auth-copy">在 Linux 终端完成登录，或使用下面的账号方式。账号管理器切换账号后，重新打开本页即可。</p>` : ''}</div></section>`;
    if (methods.length) {
      html += `<section class="sheet-section"><div class="sheet-label">登录方式</div>${methods.map((method) => `<button class="auth-method" data-auth-method="${esc(method.id)}"><span>${icon('i-lock')}</span><span><strong>${esc(method.name || method.id)}</strong><small>${esc(method.description || '')}</small></span>${icon('i-chevron-right')}</button>`).join('')}</section>`;
    }
    const command = state.agent === 'claude' ? 'claude' : 'codex login';
    html += `<section class="sheet-section"><div class="sheet-label">终端登录</div><div class="terminal-block"><div class="terminal-head"><span class="dots"><i></i><i></i><i></i></span><span>Linux terminal</span><button class="copy-btn" data-copy-text="${esc(command)}" title="复制">${icon('i-copy')}</button></div><pre class="terminal-pre">$ ${esc(command)}</pre></div><p class="sheet-note" style="margin-top:8px">账号和模型都由本地 CLI 管理。登录后回到这里，重新发起会话即可。</p></section>`;
    openSheet('auth', 'ACCOUNT', '账号与登录', html);
    sheetBody.querySelectorAll('[data-auth-method]').forEach((button) => button.addEventListener('click', () => {
      const method = methods.find((item) => item.id === button.dataset.authMethod);
      if (method?.id === 'api-key') openApiKeySheet();
      else authenticateMethod(method?.id);
    }));
  }

  function openApiKeySheet() {
    const html = `<section class="sheet-section"><p class="sheet-note">输入 OpenAI API Key。密钥只会写入本地 CLI 的登录状态，不会保存到浏览器。</p><div class="field"><label for="apiKeyInput">API Key</label><input id="apiKeyInput" type="password" autocomplete="off" placeholder="sk-..."></div><div class="sheet-actions"><button class="btn" data-close-sheet>取消</button><button class="btn primary" id="saveApiKey">登录</button></div></section>`;
    openSheet('auth-key', 'ACCOUNT', '使用 API Key', html);
    $('saveApiKey').addEventListener('click', async () => {
      const apiKey = $('apiKeyInput').value.trim();
      if (!apiKey) { toast('请输入 API Key'); return; }
      const button = $('saveApiKey');
      button.disabled = true;
      button.textContent = '登录中…';
      try {
        await rpc('authenticate', { methodId: 'api-key', _meta: { 'api-key': { apiKey } } }, { timeout: 120000 });
        state.authStatus = { kind: 'api-key', label: 'API Key 已登录' };
        updateAuthUi();
        closeSheet(false);
        toast('登录成功');
      } catch (error) {
        button.disabled = false;
        button.textContent = '登录';
        toast(error.message || '登录失败');
      }
    });
  }

  async function authenticateMethod(methodId) {
    if (!methodId) return;
    const previous = sheetBody.innerHTML;
    sheetBody.innerHTML = `<section class="sheet-section"><div class="auth-box"><div class="auth-title">${icon('i-lock')}等待登录完成…</div><p class="auth-copy">CLI 已发起登录。如果出现浏览器链接，请完成授权后回到这里。</p></div></section>`;
    try {
      await rpc('authenticate', { methodId }, { timeout: 300000 });
      state.authStatus = { kind: methodId, label: '登录成功' };
      updateAuthUi();
      closeSheet(false);
      toast('登录成功');
    } catch (error) {
      sheetBody.innerHTML = previous;
      toast(error.message || '登录未完成');
    }
  }

  function openDetailsSheet() {
    const session = currentSession();
    const model = findConfig('model', 'model');
    const effort = findConfig('thought_level', 'effort');
    const mode = findConfig('mode', 'mode');
    const usage = state.usage;
    const used = Number(usage?.used) || 0;
    const size = Number(usage?.size) || 0;
    const percent = size ? Math.min(100, Math.round((used / size) * 100)) : 0;
    const changes = state.changes;
    const plan = state.plan || [];
    const html = `
      <section class="sheet-section"><dl class="detail-grid">
        <dt>Agent</dt><dd>${esc(state.agent === 'claude' ? 'Claude Code' : 'Codex')}</dd>
        <dt>会话</dt><dd>${esc(state.sessionTitle || '新会话')}</dd>
        <dt>目录</dt><dd>${esc(state.cwd)} <button class="copy-btn" data-copy-text="${esc(state.cwd)}" title="复制">${icon('i-copy')}</button></dd>
        <dt>Session ID</dt><dd>${state.sessionId ? `${esc(state.sessionId)} <button class="copy-btn" data-copy-text="${esc(state.sessionId)}" title="复制">${icon('i-copy')}</button>` : '尚未创建'}</dd>
        <dt>模型</dt><dd>${esc(model ? modelDisplayName(model) : '未提供')}${effort ? ` · ${esc(optionLabel(effort))}` : ''}</dd>
        <dt>权限</dt><dd>${esc(mode ? optionLabel(mode) : state.legacyModes?.currentModeId || '未提供')}</dd>
      </dl></section>
      <section class="sheet-section"><div class="sheet-label">上下文</div><div class="sheet-note">${usage ? `${fmtTokens(used)} / ${fmtTokens(size)} tokens` : '等待 CLI 返回用量'}</div><div class="usage-bar"><span style="width:${percent}%"></span></div><div class="sheet-note">${usage ? `${percent}% 已使用` : ''}</div></section>
      <section class="sheet-section"><div class="sheet-label">计划</div>${plan.length ? plan.map((entry) => `<div class="plan-row ${esc(entry.status || 'pending')}"><span class="plan-dot">${icon('i-check')}</span><span>${esc(entry.content || '')}</span></div>`).join('') : '<p class="sheet-note">还没有计划</p>'}</section>
      <section class="sheet-section"><div class="sheet-label">文件改动</div>${changes.length ? changes.map((change) => `<div class="change-row">${icon('i-file')}<strong>${esc(change.path)}</strong><span>+${change.add} −${change.del}</span></div>`).join('') : '<p class="sheet-note">还没有文件改动</p>'}</section>
      <section class="sheet-section"><div class="sheet-actions"><button class="btn" data-close-sheet>关闭</button><button class="btn" id="detailsLogs">诊断日志</button></div></section>`;
    openSheet('details', 'SESSION', '会话详情', html);
    $('detailsLogs')?.addEventListener('click', openLogsSheet);
  }

  async function openLogsSheet() {
    openSheet('logs', 'DIAGNOSTICS', '诊断日志', `<section class="sheet-section"><p class="sheet-note">正在读取 CLI 日志…</p></section>`);
    try {
      const response = await fetch(`/api/logs?agent=${encodeURIComponent(state.agent)}`);
      const data = await response.json();
      const text = [
        `agent: ${data.agent}`,
        `running: ${data.running}`,
        `initialized: ${data.initialized}`,
        `startedAt: ${data.startedAt || '-'}`,
        data.last_error || data.lastError ? `lastError: ${data.lastError || data.last_error}` : '',
        '',
        ...(data.stderr || []),
      ].filter(Boolean).join('\n');
      const html = `<section class="sheet-section"><dl class="detail-grid"><dt>状态</dt><dd>${data.running ? '运行中' : '未运行'} ${data.initialized ? '· 已初始化' : ''}</dd><dt>启动时间</dt><dd>${esc(data.startedAt || '-')}</dd></dl></section><section class="sheet-section"><div class="sheet-label">CLI stderr</div><pre class="log-box">${esc(text || '没有日志')}</pre><div class="sheet-actions"><button class="btn small" data-copy-text="${esc(text)}">复制日志</button><button class="btn small" id="logsRefresh">刷新</button></div></section>`;
      sheetBody.innerHTML = html;
      $('logsRefresh')?.addEventListener('click', openLogsSheet);
    } catch (error) {
      sheetBody.innerHTML = `<section class="sheet-section"><p class="sheet-note">${esc(error.message || '无法读取日志')}</p></section>`;
    }
  }

  function showElicitation(message) {
    state.activeElicitation = message;
    const params = message.params || {};
    if (params.mode === 'url') {
      const url = String(params.url || '');
      const safeUrl = /^https?:\/\//.test(url) ? url : '';
      const html = `<section class="sheet-section"><p class="auth-copy">${esc(params.message || '请在浏览器中完成操作，然后回到这里。')}</p>${safeUrl ? `<a class="btn primary" href="${esc(safeUrl)}" target="_blank" rel="noreferrer" style="display:inline-flex;align-items:center;gap:7px">打开登录页面 ${icon('i-external')}</a>` : `<pre class="permission-preview">${esc(url)}</pre>`}<div class="sheet-actions"><button class="btn" data-elicit-action="cancel">取消</button><button class="btn primary" data-elicit-action="accept">我已打开</button></div></section>`;
      openSheet('elicitation', 'ACTION REQUIRED', '需要你的操作', html, () => sendElicitationResponse('cancel'));
      sheetBody.querySelectorAll('[data-elicit-action]').forEach((button) => button.addEventListener('click', () => sendElicitationResponse(button.dataset.elicitAction)));
      return;
    }
    const schema = params.requestedSchema || {};
    const properties = schema.properties || {};
    const required = new Set(schema.required || []);
    let fields = '';
    for (const [key, property] of Object.entries(properties)) {
      const label = property.title || key;
      const desc = property.description ? `<span class="opt-desc">${esc(property.description)}</span>` : '';
      const req = required.has(key) ? ' required' : '';
      if (property.type === 'boolean') {
        fields += `<label class="check-row"><input type="checkbox" data-elicit-field="${esc(key)}" data-field-type="boolean"> ${esc(label)}${desc}</label>`;
      } else if (property.type === 'array') {
        const items = property.items?.enum || property.items?.oneOf || [];
        if (items.length) {
          fields += `<div class="field"><label>${esc(label)}</label><div class="opt-list">${items.map((item, index) => `<label class="check-row"><input type="checkbox" data-elicit-field="${esc(key)}" data-field-type="array" value="${esc(item.const ?? item.value ?? item)}"> ${esc(item.title || item.name || item.const || item)}</label>`).join('')}</div>${desc}</div>`;
        } else {
          fields += `<div class="field"><label>${esc(label)}</label><input type="text" data-elicit-field="${esc(key)}" data-field-type="text" placeholder="用逗号分隔"${req}>${desc}</div>`;
        }
      } else if (property.enum || property.oneOf) {
        const options = property.enum ? property.enum.map((value) => ({ value, title: value })) : property.oneOf || [];
        fields += `<div class="field"><label>${esc(label)}</label><select data-elicit-field="${esc(key)}" data-field-type="select"${req}>${options.map((option) => `<option value="${esc(option.const ?? option.value ?? option)}">${esc(option.title || option.name || option.const || option)}</option>`).join('')}</select>${desc}</div>`;
      } else {
        const type = property.type === 'number' || property.type === 'integer' ? 'number' : 'text';
        fields += `<div class="field"><label>${esc(label)}</label><input type="${type}" data-elicit-field="${esc(key)}" data-field-type="${type}"${req}>${desc}</div>`;
      }
    }
    const html = `<section class="sheet-section"><p class="sheet-note">${esc(params.message || 'Agent 需要你的输入才能继续。')}</p><form id="elicitForm">${fields}</form><div class="sheet-actions"><button class="btn" data-elicit-action="cancel">取消</button><button class="btn danger" data-elicit-action="decline">拒绝</button><button class="btn primary" data-elicit-action="accept">继续</button></div></section>`;
    openSheet('elicitation', 'ACTION REQUIRED', '需要你的输入', html, () => sendElicitationResponse('cancel'));
    sheetBody.querySelectorAll('[data-elicit-action]').forEach((button) => button.addEventListener('click', () => {
      if (button.dataset.elicitAction === 'accept') {
        const content = {};
        let valid = true;
        for (const [key, property] of Object.entries(properties)) {
          const fieldsFor = [...sheetBody.querySelectorAll(`[data-elicit-field="${CSS.escape(key)}"]`)];
          if (property.type === 'array') {
            if (fieldsFor[0]?.type === 'checkbox') content[key] = fieldsFor.filter((el) => el.checked).map((el) => el.value);
            else content[key] = fieldsFor[0]?.value ? fieldsFor[0].value.split(',').map((item) => item.trim()).filter(Boolean) : [];
          } else if (property.type === 'boolean') {
            content[key] = !!fieldsFor[0]?.checked;
          } else {
            const el = fieldsFor[0];
            if (required.has(key) && !el?.value) { valid = false; el?.focus(); break; }
            if (el?.value !== '') content[key] = property.type === 'number' || property.type === 'integer' ? Number(el.value) : el.value;
          }
        }
        if (!valid) { toast('请填写必填字段'); return; }
        sendElicitationResponse('accept', content);
      } else sendElicitationResponse(button.dataset.elicitAction);
    }));
  }

  function sendElicitationResponse(action, content = null) {
    const request = state.activeElicitation;
    if (!request) return;
    state.activeElicitation = null;
    const result = { action };
    if (action === 'accept' && content) result.content = content;
    sendRpcResult(request.id, result);
    closeSheet(false);
  }

  // ---------- Menu / pickers ----------
  function toggleMoreMenu(force) {
    const menu = $('moreMenu');
    menu.hidden = force === undefined ? !menu.hidden : !force;
  }

  function handleSheetClick(event) {
    const copy = event.target.closest('[data-copy-text]');
    if (copy) { copyText(copy.dataset.copyText); return; }
    if (event.target.closest('[data-close-sheet]')) closeSheet();
  }

  function copyText(text) {
    const value = String(text ?? '');
    if (navigator.clipboard?.writeText) {
      navigator.clipboard.writeText(value).then(() => toast('已复制')).catch(() => fallbackCopy(value));
    } else fallbackCopy(value);
  }
  function fallbackCopy(value) {
    const area = document.createElement('textarea');
    area.value = value;
    area.style.position = 'fixed';
    area.style.opacity = '0';
    document.body.appendChild(area);
    area.select();
    try { document.execCommand('copy'); toast('已复制'); } catch { toast('复制失败'); }
    area.remove();
  }

  function showBanner(text, actionLabel = '', action = null) {
    bannerText.textContent = text;
    banner.classList.toggle('error', /失败|错误|断开|退出|error/i.test(text));
    banner.hidden = false;
    bannerAction.hidden = !actionLabel;
    bannerAction.textContent = actionLabel || '';
    bannerAction.onclick = action ? () => { banner.hidden = true; action(); } : null;
  }

  function toast(text) {
    toastEl.textContent = text;
    toastEl.hidden = false;
    clearTimeout(toast._timer);
    toast._timer = setTimeout(() => { toastEl.hidden = true; }, 3300);
  }

  function closeSidebar() { app.classList.remove('sidebar-open'); }
  function openSidebar() { app.classList.add('sidebar-open'); }

  function autoSizePrompt() {
    promptEl.style.height = 'auto';
    promptEl.style.height = `${Math.min(200, promptEl.scrollHeight)}px`;
  }

  // ---------- Events ----------
  $('newSession').addEventListener('click', () => { closeSidebar(); newSession(); });
  $('newSessionTop').addEventListener('click', () => newSession());
  $('menuButton').addEventListener('click', openSidebar);
  $('historyButton').addEventListener('click', openSidebar);
  $('sessionSearch').addEventListener('input', (event) => { state.sessionQuery = event.target.value || ''; renderSessions(); });
  $('sidebarClose').addEventListener('click', closeSidebar);
  $('scrim').addEventListener('click', closeSidebar);
  $('sheetClose').addEventListener('click', () => closeSheet());
  document.querySelector('.sheet-scrim')?.addEventListener('click', () => closeSheet());
  $('themeButton').addEventListener('click', () => { applyTheme(state.theme === 'dark' ? 'light' : 'dark'); });
  $('cwdChip').addEventListener('click', openCwdSheet);
  $('modeChip').addEventListener('click', openModeSheet);
  $('modelChip').addEventListener('click', openModelSheet);
  $('accountRow').addEventListener('click', () => openAuthSheet());
  $('moreButton').addEventListener('click', (event) => { event.stopPropagation(); toggleMoreMenu(); });
  $('moreMenu').addEventListener('click', (event) => {
    const button = event.target.closest('[data-menu]');
    if (!button) return;
    toggleMoreMenu(false);
    switch (button.dataset.menu) {
      case 'details': openDetailsSheet(); break;
      case 'cwd': openCwdSheet(); break;
      case 'account': openAuthSheet(); break;
      case 'theme': applyTheme(state.theme === 'dark' ? 'light' : 'dark'); break;
      case 'reconnect': disconnect(); state.reconnectAttempts = 0; connect({ restore: true }); break;
      case 'logs': openLogsSheet(); break;
    }
  });
  document.addEventListener('click', (event) => {
    if (!event.target.closest('#moreMenu') && !event.target.closest('#moreButton')) toggleMoreMenu(false);
    if (sheetLayer.hidden === false && event.target.closest('[data-close-sheet]')) closeSheet();
  });
  document.addEventListener('keydown', (event) => {
    if (event.key === 'Escape') {
      if (!sheetLayer.hidden) closeSheet();
      else if (! $('moreMenu').hidden) toggleMoreMenu(false);
      else closeSidebar();
    }
    if ((event.metaKey || event.ctrlKey) && event.key.toLowerCase() === 'n') {
      event.preventDefault();
      newSession();
    }
    if ((event.metaKey || event.ctrlKey) && event.key.toLowerCase() === 'b') {
      event.preventDefault();
      app.classList.contains('sidebar-open') ? closeSidebar() : openSidebar();
    }
  });

  sessionList.addEventListener('click', (event) => {
    const button = event.target.closest('[data-session-id]');
    if (button) loadSession(button.dataset.sessionId);
  });

  $('suggestions').addEventListener('click', (event) => {
    const button = event.target.closest('[data-suggestion]');
    if (!button) return;
    promptEl.value = button.dataset.suggestion;
    autoSizePrompt();
    updateComposer();
    focusPrompt();
  });

  messagesEl.addEventListener('click', (event) => {
    const copyCode = event.target.closest('[data-copy-code]');
    if (copyCode) {
      const block = copyCode.closest('.terminal-block, .code-block');
      const pre = block?.querySelector('pre');
      if (pre) copyText(pre.textContent || '');
      return;
    }
    const toggle = event.target.closest('[data-tool-toggle]');
    if (toggle) {
      const card = toggle.closest('.tool-card');
      if (!card) return;
      card.classList.toggle('open');
      card.dataset.userOpen = String(card.classList.contains('open'));
      toggle.setAttribute('aria-expanded', String(card.classList.contains('open')));
      return;
    }
    const permission = event.target.closest('[data-permission-option]');
    if (permission) {
      const card = permission.closest('.permission-card');
      if (!card) return;
      respondPermission(card.dataset.permissionId, permission.dataset.permissionOption, permission.dataset.permissionKind);
      return;
    }
  });

  $('jumpLatest').addEventListener('click', () => scrollBottom(true));
  thread.addEventListener('scroll', () => {
    state.stickBottom = nearBottom();
    updateJumpLatest();
  }, { passive: true });

  promptEl.addEventListener('input', () => {
    autoSizePrompt();
    updateComposer();
    updateSlashMenu();
  });
  promptEl.addEventListener('compositionstart', () => { state.compose = true; });
  promptEl.addEventListener('compositionend', () => { state.compose = false; });
  promptEl.addEventListener('keydown', (event) => {
    const menu = $('slashMenu');
    if (!menu.hidden) {
      const items = [...menu.querySelectorAll('[data-command]')];
      if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
        event.preventDefault();
        state.slashIndex = (state.slashIndex + (event.key === 'ArrowDown' ? 1 : -1) + items.length) % items.length;
        items.forEach((item, index) => item.classList.toggle('active', index === state.slashIndex));
        return;
      }
      if (event.key === 'Enter' && !event.shiftKey && !event.isComposing && !state.compose) {
        event.preventDefault();
        const active = items[state.slashIndex] || items[0];
        if (active) chooseCommand(active.dataset.command);
        return;
      }
      if (event.key === 'Escape') { menu.hidden = true; return; }
    }
    if (event.key === 'Enter' && !event.shiftKey && !event.isComposing && !state.compose) {
      event.preventDefault();
      sendPrompt();
    }
  });
  $('slashMenu').addEventListener('mousedown', (event) => event.preventDefault());
  $('slashMenu').addEventListener('click', (event) => {
    const item = event.target.closest('[data-command]');
    if (item) chooseCommand(item.dataset.command);
  });

  sendButton.addEventListener('click', () => { if (state.busy) cancelPrompt(); else sendPrompt(); });
  $('workingStop').addEventListener('click', cancelPrompt);
  $('attachButton').addEventListener('click', () => fileInput.click());
  fileInput.addEventListener('change', async () => {
    for (const file of [...fileInput.files || []]) await addAttachment(file);
    fileInput.value = '';
  });
  $('attachments').addEventListener('click', (event) => {
    const button = event.target.closest('[data-remove-attachment]');
    if (!button) return;
    state.attachments.splice(Number(button.dataset.removeAttachment), 1);
    renderAttachments();
    updateComposer();
  });
  sheetBody.addEventListener('click', handleSheetClick);
  bannerAction.addEventListener('click', () => { banner.hidden = true; });

  document.querySelectorAll('.agent-choice').forEach((button) => {
    button.addEventListener('click', async () => {
      if (state.busy) { toast('当前任务运行中'); return; }
      const agent = button.dataset.agent;
      if (!agent || agent === state.agent) { closeSidebar(); return; }
      disconnect();
      state.agent = agent;
      safeStorageSet(AGENT_KEY, agent);
      state.busy = false;
      state.cancelling = false;
      state.sessionId = null;
      state.sessionTitle = '新会话';
      state.cwd = safeStorageGet(CWD_KEY(agent)) || '/root';
      state.theme = readTheme(agent);
      state.authStatus = null;
      state.authMethods = [];
      state.configs = [];
      state.legacyModes = null;
      state.commands = [];
      state.usage = null;
      clearConversation();
      updateAgentUi();
      hydrateServerCwd();
      closeSidebar();
      await connect();
    });
  });

  // Init
  state.cwd = safeStorageGet(CWD_KEY(state.agent)) || '/root';
  state.theme = readTheme(state.agent);
  $('cwdLabel').textContent = basename(state.cwd);
  $('cwdChip').title = state.cwd;
  updateAgentUi();
  renderAttachments();
  autoSizePrompt();
  updateComposer();
  hydrateServerCwd();
  connect();
})();
