// Serves the static export in target/site — the `npm run start` target. `next start` refuses `output: 'export'`,
// and Playwright's webServer needs an HTTP origin to smoke-test against, so this stdlib-only script serves the
// built export instead of adding a static-server npm dependency.
import {createServer} from 'node:http';
import {existsSync, readFileSync, statSync} from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';

const ROOT = path.join(path.dirname(fileURLToPath(import.meta.url)), '..', 'target', 'site');
const PORT = Number(process.env.PORT ?? 40201);

const MIME_TYPES = {
  '.html': 'text/html; charset=utf-8',
  '.css': 'text/css; charset=utf-8',
  '.js': 'text/javascript; charset=utf-8',
  '.json': 'application/json',
  '.txt': 'text/plain; charset=utf-8',
  '.md': 'text/markdown; charset=utf-8',
  '.ico': 'image/x-icon',
  '.svg': 'image/svg+xml',
  '.png': 'image/png',
  '.woff2': 'font/woff2',
};

function resolveFile(urlPath) {
  const decoded = decodeURIComponent(urlPath.split('?')[0]);
  const resolved = path.normalize(path.join(ROOT, decoded));
  if (resolved !== ROOT && !resolved.startsWith(ROOT + path.sep)) return null;
  const candidates = decoded.endsWith('/')
    ? [path.join(resolved, 'index.html')]
    : [resolved, `${resolved}.html`, path.join(resolved, 'index.html')];
  for (const candidate of candidates) {
    if (existsSync(candidate) && statSync(candidate).isFile()) return candidate;
  }
  return null;
}

const server = createServer((request, response) => {
  const file = resolveFile(request.url ?? '/');
  if (file) {
    response.writeHead(200, {'content-type': MIME_TYPES[path.extname(file)] ?? 'application/octet-stream'});
    response.end(readFileSync(file));
    return;
  }
  const notFoundPage = path.join(ROOT, '404.html');
  if (existsSync(notFoundPage)) {
    response.writeHead(404, {'content-type': MIME_TYPES['.html']});
    response.end(readFileSync(notFoundPage));
    return;
  }
  response.writeHead(404, {'content-type': MIME_TYPES['.txt']});
  response.end('Not found');
});

server.listen(PORT, () => {
  console.log(`Serving ${ROOT} at http://127.0.0.1:${PORT}/`);
});
