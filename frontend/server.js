const http = require('node:http');
const fs = require('node:fs/promises');
const path = require('node:path');
const { URL } = require('node:url');
const { Jieba } = require('@node-rs/jieba');
const { dict } = require('@node-rs/jieba/dict');

const PORT = Number(process.env.PORT || 3000);
const HOST = process.env.HOST || '127.0.0.1';
const BACKEND_URL = process.env.BACKEND_URL || 'http://localhost/search';
const BACKEND_TIMEOUT_MS = Number(process.env.BACKEND_TIMEOUT_MS || 60000);
const PUBLIC_DIR = path.join(__dirname, 'public');

const jieba = Jieba.withDict(dict);

const CONTENT_TYPES = {
  '.html': 'text/html; charset=utf-8',
  '.css': 'text/css; charset=utf-8',
  '.js': 'text/javascript; charset=utf-8',
  '.json': 'application/json; charset=utf-8',
  '.svg': 'image/svg+xml',
  '.png': 'image/png',
  '.ico': 'image/x-icon',
};

function sendJson(response, statusCode, payload) {
  const body = Buffer.from(JSON.stringify(payload), 'utf8');
  response.writeHead(statusCode, {
    'Content-Type': 'application/json; charset=utf-8',
    'Content-Length': body.length,
    'Cache-Control': 'no-store',
    'X-Content-Type-Options': 'nosniff',
  });
  response.end(body);
}

function segmentQuery(query) {
  return [...new Set(
    jieba.cutForSearch(query, true)
      .map((token) => token.trim())
      .filter((token) => token.length > 0),
  )];
}

async function searchBackend(query) {
  const target = new URL(BACKEND_URL);
  target.searchParams.set('query', query);

  const response = await fetch(target, {
    headers: { Accept: 'application/json' },
    signal: AbortSignal.timeout(BACKEND_TIMEOUT_MS),
  });
  const text = await response.text();

  if (!response.ok) {
    throw new Error(`后端返回 HTTP ${response.status}: ${text.slice(0, 300)}`);
  }

  let payload;
  try {
    payload = JSON.parse(text);
  } catch {
    throw new Error('后端响应不是合法 JSON');
  }

  if (!Array.isArray(payload)) {
    throw new Error('后端响应格式错误：期望数组');
  }
  return payload;
}

async function handleSearch(requestUrl, response) {
  const query = (requestUrl.searchParams.get('query') || '').trim();
  if (!query) {
    sendJson(response, 400, { message: '请输入查询关键词' });
    return;
  }
  if (query.length > 200) {
    sendJson(response, 400, { message: '查询关键词不能超过 200 个字符' });
    return;
  }

  const startedAt = Date.now();
  try {
    const results = await searchBackend(query);
    const tokens = segmentQuery(query);
    sendJson(response, 200, {
      query,
      tokens,
      total: results.length,
      elapsedMs: Date.now() - startedAt,
      results,
    });
  } catch (error) {
    console.error(`[search] ${query}: ${error.message}`);
    sendJson(response, 502, {
      message: error.name === 'TimeoutError'
        ? '后端请求超时'
        : `搜索失败：${error.message}`,
    });
  }
}

async function serveStatic(requestUrl, response) {
  if (requestUrl.pathname === '/favicon.ico') {
    response.writeHead(204);
    response.end();
    return;
  }

  let relativePath;
  try {
    relativePath = decodeURIComponent(requestUrl.pathname);
  } catch {
    response.writeHead(400);
    response.end('Bad Request');
    return;
  }

  if (relativePath === '/') {
    relativePath = '/index.html';
  }

  const filePath = path.resolve(PUBLIC_DIR, `.${relativePath}`);
  if (filePath !== PUBLIC_DIR && !filePath.startsWith(PUBLIC_DIR + path.sep)) {
    response.writeHead(403);
    response.end('Forbidden');
    return;
  }

  try {
    const content = await fs.readFile(filePath);
    response.writeHead(200, {
      'Content-Type': CONTENT_TYPES[path.extname(filePath).toLowerCase()]
        || 'application/octet-stream',
      'Content-Length': content.length,
      'Cache-Control': 'no-cache',
      'X-Content-Type-Options': 'nosniff',
    });
    response.end(content);
  } catch (error) {
    if (error.code === 'ENOENT') {
      response.writeHead(404, { 'Content-Type': 'text/plain; charset=utf-8' });
      response.end('Not Found');
      return;
    }
    console.error(`[static] ${requestUrl.pathname}: ${error.message}`);
    response.writeHead(500, { 'Content-Type': 'text/plain; charset=utf-8' });
    response.end('Internal Server Error');
  }
}

const server = http.createServer(async (request, response) => {
  const requestUrl = new URL(
    request.url,
    `http://${request.headers.host || `${HOST}:${PORT}`}`,
  );

  try {
    if (request.method !== 'GET') {
      response.writeHead(405, { Allow: 'GET' });
      response.end('Method Not Allowed');
      return;
    }

    if (requestUrl.pathname === '/api/search') {
      await handleSearch(requestUrl, response);
      return;
    }

    if (requestUrl.pathname === '/api/health') {
      sendJson(response, 200, {
        status: 'ok',
        port: PORT,
        backend: BACKEND_URL,
        jieba: 'node-rs',
      });
      return;
    }

    await serveStatic(requestUrl, response);
  } catch (error) {
    console.error(`[request] ${request.method} ${request.url}: ${error.stack}`);
    if (!response.headersSent) {
      sendJson(response, 500, { message: '服务内部错误' });
    } else {
      response.end();
    }
  }
});

server.listen(PORT, HOST, () => {
  console.log(`Frontend server: http://${HOST}:${PORT}`);
  console.log(`Backend URL: ${BACKEND_URL}`);
});

process.on('SIGINT', () => {
  server.close(() => process.exit(0));
});
