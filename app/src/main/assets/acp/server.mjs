import http from 'node:http';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { spawn } from 'node:child_process';
import { WebSocketServer } from 'ws';

const HERE = path.dirname(fileURLToPath(import.meta.url));
const UI_DIR = path.join(HERE, 'ui');
const PORT = Number.parseInt(process.env.ACP_PORT || '3210', 10);
const HOST = process.env.ACP_HOST || '127.0.0.1';
const CLIENT_INFO = { name: 'Sta ACP Workspace', version: '2.0.0' };
const CLIENT_CAPABILITIES = {
  fs: { readTextFile: false, writeTextFile: false },
  terminal: false,
  session: { configOptions: { boolean: {} }, notices: {}, compaction: {} },
  elicitation: { form: {}, url: {} },
  auth: { terminal: false },
};

const AGENTS = {
  claude: {
    command: process.env.ACP_CLAUDE_COMMAND || 'claude-agent-acp',
    args: readArgs('ACP_CLAUDE_ARGS'),
    env: { NO_BROWSER: '1', CLAUDE_AGENT_SDK_CLIENT_APP: 'Sta ACP Workspace' },
  },
  codex: {
    command: process.env.ACP_CODEX_COMMAND || 'codex-acp',
    args: readArgs('ACP_CODEX_ARGS'),
    env: { NO_BROWSER: '1' },
  },
};

function readArgs(name) {
  const value = process.env[name];
  if (!value) return [];
  try {
    const parsed = JSON.parse(value);
    return Array.isArray(parsed) ? parsed.map(String) : [];
  } catch {
    return [];
  }
}

function chooseCwd() {
  const candidates = [process.env.ACP_CWD, process.env.HOME, '/root', '/workspace', '/'];
  for (const candidate of candidates) {
    if (!candidate) continue;
    try {
      if (!fs.statSync(candidate).isDirectory()) continue;
      fs.accessSync(candidate, fs.constants.X_OK);
      return candidate;
    } catch {}
  }
  return '/';
}

const MIME = {
  '.html': 'text/html; charset=utf-8',
  '.js': 'text/javascript; charset=utf-8',
  '.css': 'text/css; charset=utf-8',
  '.svg': 'image/svg+xml',
  '.png': 'image/png',
  '.webp': 'image/webp',
  '.woff2': 'font/woff2',
  '.json': 'application/json; charset=utf-8',
  '.ico': 'image/x-icon',
};

class AgentBridge {
  constructor(id, spec) {
    this.id = id;
    this.spec = spec;
    this.child = null;
    this.sockets = new Set();
    this.initialized = false;
    this.initPromise = null;
    this.initResult = null;
    this.requestId = 0;
    this.pending = new Map();
    this.outbound = new Map();
    this.clientRequests = new Map();
    this.stdout = '';
    this.stderrRing = [];
    this.eventRing = [];
    this.generation = 0;
    this.startedAt = null;
    this.lastError = null;
    this.authStatus = null;
  }

  async ensure() {
    if (this.child && !this.child.killed) return;
    if (this.initPromise) return this.initPromise;
    const generation = ++this.generation;
    const cwd = chooseCwd();
    const command = this.spec.command;
    const args = this.spec.args;
    this.lastError = null;
    this.initialized = false;
    this.initResult = null;
    this.pending.clear();
    this.outbound.clear();
    this.stdout = '';
    this.startedAt = new Date().toISOString();
    const child = spawn(command, args, {
      cwd,
      env: {
        ...process.env,
        ...this.spec.env,
        TERM: process.env.TERM || 'xterm-256color',
        COLORTERM: 'truecolor',
      },
      stdio: ['pipe', 'pipe', 'pipe'],
      windowsHide: true,
    });
    this.child = child;
    child.stdin.on('error', (error) => this.onStderr(`stdin: ${error.message}`));
    child.stdout.setEncoding('utf8');
    child.stderr.setEncoding('utf8');
    child.stdout.on('data', (chunk) => this.onStdout(String(chunk)));
    child.stderr.on('data', (chunk) => this.onStderr(String(chunk)));
    child.on('error', (error) => {
      this.lastError = `无法启动 ${command}: ${error.message}`;
      this.failAll(new Error(this.lastError));
      this.broadcast({
        jsonrpc: '2.0',
        method: '$/bridge_error',
        params: { message: this.lastError, code: error.code || null },
      });
    });
    child.on('exit', (code, signal) => {
      if (generation !== this.generation) return;
      const message = `CLI 进程已退出（code=${code ?? 'null'}, signal=${signal ?? 'null'}）`;
      this.lastError = message;
      this.child = null;
      this.initialized = false;
      this.initPromise = null;
      this.initResult = null;
      this.authStatus = null;
      this.failAll(new Error(message));
      this.broadcast({
        jsonrpc: '2.0',
        method: '$/bridge_exit',
        params: { code, signal, message, stderr: this.stderrRing.slice(-8) },
      });
    });
  }

  failAll(error) {
    for (const { reject } of this.pending.values()) reject(error);
    this.pending.clear();
    for (const { reject } of this.clientRequests.values()) reject(error);
    this.clientRequests.clear();
    this.outbound.clear();
  }

  onStdout(chunk) {
    this.stdout += chunk;
    for (;;) {
      const newline = this.stdout.indexOf('\n');
      if (newline < 0) break;
      const line = this.stdout.slice(0, newline).trim();
      this.stdout = this.stdout.slice(newline + 1);
      if (!line) continue;
      let message;
      try {
        message = JSON.parse(line);
      } catch {
        this.onStderr(`[stdout] ${line}`);
        continue;
      }
      this.onAgentMessage(message);
    }
  }

  onStderr(text) {
    const normalized = text.replace(/\r/g, '').trim();
    if (!normalized) return;
    this.stderrRing.push(normalized.slice(0, 2000));
    if (this.stderrRing.length > 120) this.stderrRing.shift();
    this.eventRing.push({ at: Date.now(), kind: 'stderr', text: normalized.slice(0, 2000) });
    if (this.eventRing.length > 300) this.eventRing.shift();
    if (/\b(error|fatal|failed|exception|not found)\b/i.test(normalized)) {
      this.broadcast({
        jsonrpc: '2.0',
        method: '$/bridge_log',
        params: { level: 'error', text: normalized.slice(0, 4000) },
      });
    }
  }

  onAgentMessage(message) {
    this.eventRing.push({ at: Date.now(), kind: 'agent', message: summarize(message) });
    if (this.eventRing.length > 300) this.eventRing.shift();
    if (message.method === '_auth/status_update' && message.params?.authStatus) {
      this.authStatus = message.params.authStatus;
    }

    const key = String(message.id ?? '');
    if (message.id !== undefined && !message.method) {
      if (key === 'bridge-init') {
        if (message.error) {
          this.initPromise = null;
          this.initResult = null;
          this.initialized = false;
        } else {
          this.initResult = message.result || {};
          this.initialized = true;
        }
      }
      const pending = this.pending.get(key);
      if (pending) {
        this.pending.delete(key);
        pending.resolve(message);
        return;
      }
      const origin = this.outbound.get(key);
      if (origin) {
        this.outbound.delete(key);
        send(origin, message);
        return;
      }
      this.broadcast(message);
      return;
    }

    if (message.id !== undefined && message.method) {
      this.clientRequests.set(key, message);
      this.broadcast(message);
      return;
    }

    this.broadcast(message);
  }

  broadcast(message) {
    for (const socket of this.sockets) send(socket, message);
  }

  attach(socket) {
    this.sockets.add(socket);
    send(socket, {
      jsonrpc: '2.0',
      method: '$/bridge_ready',
      params: {
        agent: this.id,
        running: !!this.child,
        initialized: this.initialized,
        startedAt: this.startedAt,
        lastError: this.lastError,
      },
    });
    if (this.authStatus) {
      send(socket, {
        jsonrpc: '2.0',
        method: '_auth/status_update',
        params: { authStatus: this.authStatus },
      });
    }
    for (const request of this.clientRequests.values()) send(socket, request);
  }

  detach(socket) {
    this.sockets.delete(socket);
  }

  async initialize() {
    if (this.initResult) return this.initResult;
    if (this.initPromise) return this.initPromise;
    await this.ensure();
    const id = 'bridge-init';
    this.initPromise = new Promise((resolve, reject) => {
      this.pending.set(id, { resolve, reject });
      this.write({
        jsonrpc: '2.0',
        id,
        method: 'initialize',
        params: {
          protocolVersion: 1,
          clientCapabilities: CLIENT_CAPABILITIES,
          clientInfo: CLIENT_INFO,
        },
      });
    })
      .then((response) => {
        if (response.error) throw new Error(response.error.message || '初始化失败');
        this.initResult = response.result || {};
        this.initialized = true;
        this.initPromise = null;
        return this.initResult;
      })
      .catch((error) => {
        this.initPromise = null;
        this.initResult = null;
        this.initialized = false;
        throw error;
      });
    return this.initPromise;
  }

  write(message) {
    if (!this.child || this.child.killed || !this.child.stdin.writable) {
      throw new Error('CLI 进程尚未运行');
    }
    this.child.stdin.write(`${JSON.stringify(message)}\n`);
  }

  async forwardRequest(socket, message) {
    await this.ensure();
    const key = String(message.id);
    this.outbound.set(key, socket);
    this.write(message);
  }

  respondToAgent(message) {
    if (!this.child) return;
    const key = String(message.id ?? '');
    this.clientRequests.delete(key);
    this.write(message);
  }

  async logs() {
    return {
      agent: this.id,
      running: !!this.child,
      initialized: this.initialized,
      startedAt: this.startedAt,
      lastError: this.lastError,
      stderr: this.stderrRing.slice(-40),
      events: this.eventRing.slice(-80),
    };
  }

  stop() {
    if (!this.child) return;
    try {
      this.child.kill('SIGTERM');
    } catch {}
    const child = this.child;
    setTimeout(() => {
      try {
        child.kill('SIGKILL');
      } catch {}
    }, 1200).unref?.();
    this.child = null;
    this.initialized = false;
    this.initPromise = null;
    this.initResult = null;
    this.authStatus = null;
    this.failAll(new Error('CLI 已停止'));
  }
}

function summarize(message) {
  if (message.method === 'session/update') {
    const update = message.params?.update || {};
    return {
      method: message.method,
      sessionId: message.params?.sessionId,
      update: update.sessionUpdate || null,
      status: update.status || null,
      title: update.title || null,
    };
  }
  if (message.method) return { method: message.method, id: message.id ?? null };
  return { id: message.id ?? null, error: message.error?.message || null };
}

const bridges = new Map();
for (const [id, spec] of Object.entries(AGENTS)) bridges.set(id, new AgentBridge(id, spec));

function json(res, status, value) {
  res.writeHead(status, { 'content-type': 'application/json; charset=utf-8' });
  res.end(JSON.stringify(value));
}

const server = http.createServer((req, res) => {
  const requestUrl = new URL(req.url || '/', `http://${HOST}:${PORT}`);
  if (requestUrl.pathname === '/health') {
    const agents = {};
    for (const [id, bridge] of bridges) {
      agents[id] = {
        running: !!bridge.child,
        initialized: bridge.initialized,
        startedAt: bridge.startedAt,
        lastError: bridge.lastError,
      };
    }
    json(res, 200, { ok: true, service: 'sta-acp', version: 2, agents });
    return;
  }
  if (requestUrl.pathname === '/api/logs') {
    const id = requestUrl.searchParams.get('agent') || 'claude';
    const bridge = bridges.get(id);
    if (!bridge) {
      json(res, 404, { error: 'unknown agent' });
      return;
    }
    bridge.logs().then((logs) => json(res, 200, logs));
    return;
  }
  if (requestUrl.pathname === '/api/info') {
    json(res, 200, { version: 2, client: CLIENT_INFO, agents: Object.keys(AGENTS), cwd: chooseCwd() });
    return;
  }

  let relative = requestUrl.pathname === '/' ? 'index.html' : requestUrl.pathname.slice(1);
  try {
    relative = decodeURIComponent(relative);
  } catch {
    res.writeHead(400);
    res.end('Bad request');
    return;
  }
  const file = path.resolve(UI_DIR, relative);
  if (!file.startsWith(UI_DIR + path.sep) && file !== UI_DIR) {
    res.writeHead(403);
    res.end('Forbidden');
    return;
  }
  fs.readFile(file, (error, data) => {
    if (error) {
      res.writeHead(404);
      res.end('Not found');
      return;
    }
    res.writeHead(200, {
      'content-type': MIME[path.extname(file)] || 'application/octet-stream',
      'cache-control': 'no-cache',
    });
    res.end(data);
  });
});

const wss = new WebSocketServer({ noServer: true });

server.on('upgrade', (request, socket, head) => {
  const requestUrl = new URL(request.url || '/', `http://${request.headers.host || HOST}`);
  if (requestUrl.pathname !== '/acp') {
    socket.destroy();
    return;
  }
  const agentId = requestUrl.searchParams.get('agent') || 'claude';
  const bridge = bridges.get(agentId);
  if (!bridge) {
    socket.destroy();
    return;
  }
  wss.handleUpgrade(request, socket, head, (ws) => handleAcpSocket(ws, bridge));
});

function send(ws, value) {
  if (ws.readyState === ws.OPEN) ws.send(JSON.stringify(value));
}

function handleAcpSocket(ws, bridge) {
  bridge.attach(ws);
  ws.on('message', async (data) => {
    let message;
    try {
      message = JSON.parse(data.toString());
    } catch {
      send(ws, {
        jsonrpc: '2.0',
        method: '$/bridge_error',
        params: { message: '客户端发送了非法 JSON' },
      });
      return;
    }
    try {
      if (message.method === 'initialize' && message.id !== undefined) {
        const result = await bridge.initialize();
        send(ws, { jsonrpc: '2.0', id: message.id, result });
        return;
      }
      if (message.method === '$/bridge/ping') {
        send(ws, { jsonrpc: '2.0', id: message.id, result: { ok: true, at: Date.now() } });
        return;
      }
      if (message.method === '$/bridge/restart') {
        bridge.stop();
        await bridge.ensure();
        const result = await bridge.initialize();
        send(ws, { jsonrpc: '2.0', id: message.id, result });
        return;
      }
      if (message.method && message.id !== undefined) {
        await bridge.forwardRequest(ws, message);
        return;
      }
      if (message.id !== undefined && !message.method) {
        bridge.respondToAgent(message);
        return;
      }
      // A client notification such as session/cancel.
      await bridge.ensure();
      bridge.write(message);
    } catch (error) {
      send(ws, {
        jsonrpc: '2.0',
        id: message?.id,
        error: { code: -32000, message: error instanceof Error ? error.message : String(error) },
      });
    }
  });
  ws.on('close', () => bridge.detach(ws));
  ws.on('error', () => bridge.detach(ws));
}

const heartbeat = setInterval(() => {
  for (const ws of wss.clients) {
    if (ws.isAlive === false) {
      ws.terminate();
      continue;
    }
    ws.isAlive = false;
    try {
      ws.ping();
    } catch {}
  }
}, 25000);
heartbeat.unref?.();
wss.on('connection', (ws) => {
  ws.isAlive = true;
  ws.on('pong', () => {
    ws.isAlive = true;
  });
});

server.listen(PORT, HOST, () => {
  console.log(`ACP GUI ready at http://${HOST}:${PORT}`);
});

function shutdown() {
  clearInterval(heartbeat);
  for (const bridge of bridges.values()) bridge.stop();
  server.close(() => process.exit(0));
  setTimeout(() => process.exit(0), 1200).unref?.();
}
process.on('SIGTERM', shutdown);
process.on('SIGINT', shutdown);
