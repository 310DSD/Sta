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

const AGENTS = {
  claude: {
    command: 'claude-agent-acp',
    args: [],
    env: { NO_BROWSER: '1', CLAUDE_AGENT_SDK_CLIENT_APP: 'Sta ACP' },
  },
  codex: {
    command: 'codex-acp',
    args: [],
    env: { NO_BROWSER: '1' },
  },
};

const MIME = {
  '.html': 'text/html; charset=utf-8',
  '.js': 'text/javascript; charset=utf-8',
  '.css': 'text/css; charset=utf-8',
  '.svg': 'image/svg+xml',
  '.png': 'image/png',
  '.ico': 'image/x-icon',
};

function json(res, status, value) {
  res.writeHead(status, { 'content-type': 'application/json; charset=utf-8' });
  res.end(JSON.stringify(value));
}

const server = http.createServer((req, res) => {
  const requestUrl = new URL(req.url || '/', `http://${HOST}:${PORT}`);
  if (requestUrl.pathname === '/health') {
    json(res, 200, { ok: true, service: 'sta-acp', agents: Object.keys(AGENTS) });
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
  if (!AGENTS[agentId]) {
    socket.destroy();
    return;
  }
  wss.handleUpgrade(request, socket, head, (ws) => handleAcpSocket(ws, agentId));
});

function send(ws, value) {
  if (ws.readyState === ws.OPEN) ws.send(JSON.stringify(value));
}

function handleAcpSocket(ws, agentId) {
  const spec = AGENTS[agentId];
  let closed = false;
  let child;
  try {
    child = spawn(spec.command, spec.args, {
      cwd: process.env.HOME || '/root',
      env: { ...process.env, ...spec.env },
      stdio: ['pipe', 'pipe', 'pipe'],
    });
  } catch (error) {
    send(ws, {
      jsonrpc: '2.0',
      method: '$/bridge_error',
      params: { message: `无法启动 ${spec.command}: ${error instanceof Error ? error.message : String(error)}` },
    });
    ws.close();
    return;
  }

  const stop = () => {
    if (closed) return;
    closed = true;
    try { child.kill('SIGTERM'); } catch {}
    setTimeout(() => { try { child.kill('SIGKILL'); } catch {} }, 1500).unref?.();
  };

  let output = '';
  child.stdout.setEncoding('utf8');
  child.stdout.on('data', (chunk) => {
    output += chunk;
    for (;;) {
      const newline = output.indexOf('\n');
      if (newline < 0) break;
      const line = output.slice(0, newline).trim();
      output = output.slice(newline + 1);
      if (!line) continue;
      try {
        send(ws, JSON.parse(line));
      } catch {
        send(ws, { jsonrpc: '2.0', method: '$/stderr', params: { text: line } });
      }
    }
  });
  child.stderr.setEncoding('utf8');
  child.stderr.on('data', (chunk) => {
    const text = String(chunk).trim();
    if (text) send(ws, { jsonrpc: '2.0', method: '$/stderr', params: { text } });
  });
  child.on('error', (error) => {
    send(ws, {
      jsonrpc: '2.0',
      method: '$/bridge_error',
      params: { message: `无法启动 ${spec.command}: ${error.message}` },
    });
  });
  child.on('exit', (code, signal) => {
    send(ws, { jsonrpc: '2.0', method: '$/bridge_exit', params: { code, signal } });
    try { ws.close(); } catch {}
  });

  ws.on('message', (data) => {
    if (closed) return;
    const text = data.toString();
    if (!text.trim()) return;
    try {
      JSON.parse(text);
    } catch {
      send(ws, { jsonrpc: '2.0', method: '$/bridge_error', params: { message: 'Invalid JSON from client' } });
      return;
    }
    child.stdin.write(text.endsWith('\n') ? text : `${text}\n`);
  });
  ws.on('close', stop);
  ws.on('error', stop);
}

server.listen(PORT, HOST, () => {
  console.log(`ACP GUI ready at http://${HOST}:${PORT}`);
});
