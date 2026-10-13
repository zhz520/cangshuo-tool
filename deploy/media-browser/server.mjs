import http from 'node:http';
import {chromium} from 'playwright';
import {allowedUrl, requestId, normalizeAweme} from './policy.mjs';
import {startProxy} from './proxy.mjs';

// Browser fallback inspired by hiyufan/shizhen (MIT). No login/captcha solver, arbitrary URL or CDP API.
const proxy = await startProxy();
const browser = await chromium.launch({headless: true, channel: 'chromium',
  chromiumSandbox: process.env.MEDIA_BROWSER_SANDBOX !== 'false',
  args: ['--disable-dev-shm-usage', '--disable-quic'], proxy: {server: proxy.url}});
const version = browser.version();
const context = await browser.newContext({locale: 'zh-CN', viewport: {width: 1280, height: 900},
  userAgent: `Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/${version} Safari/537.36`,
  acceptDownloads: false, serviceWorkers: 'block'});
await context.route('**/*', async route => {
  const request = route.request();
  if (!allowedUrl(request.url()) || request.resourceType() === 'media') await route.abort();
  else await route.continue();
});
await context.routeWebSocket('**/*', socket => socket.close());
// Acquire anonymous guest state once per process, without persisting cookies or signing into an account.
const warmup = await context.newPage();
try {
  await warmup.goto('https://www.douyin.com/', {waitUntil: 'domcontentloaded', timeout: 10_000});
  await warmup.waitForFunction(() => document.title.includes('抖音') && document.body?.innerText.length > 100,
    null, {timeout: 8000});
} catch { /* A restricted platform does not make the internal service unhealthy. */ }
finally { await warmup.close().catch(() => {}); }
let active = 0;

// Read only the targeted note's player props. Related-feed thumbnails and partial lazy-loaded DOM lists are excluded.
function pageMetadata(id) {
  const detail = document.querySelector('[data-e2e="note-detail"]');
  const root = detail?.querySelector('[data-e2e="player-container"]') || detail;
  if (!root) return null;
  for (const node of [root, ...root.querySelectorAll('div')].slice(0, 120)) {
    const key = Object.keys(node).find(name => name.startsWith('__reactFiber$'));
    for (let fiber = key && node[key], hop = 0; fiber && hop < 30; fiber = fiber.return, hop++) {
      const aweme = fiber.memoizedProps?.awemeInfo;
      if (!aweme || !Array.isArray(aweme.images)) continue;
      const actual = String(aweme.awemeId || aweme.aweme_id || aweme.id || '');
      if (actual && actual !== id) continue;
      if (aweme.images.length > 50) return {images: new Array(51).fill({}), aweme_id: id};
      return {aweme_id: actual || id, desc: aweme.desc || detail.querySelector('[data-e2e="note-desc"]')?.textContent || '',
        author: {nickname: aweme.author?.nickname || aweme.author?.nickName || ''},
        images: aweme.images.map(image => ({width: image.width, height: image.height,
          url_list: image.url_list || image.urlList, video: image.video ? {play_addr: image.video.play_addr, playAddr: image.video.playAddr} : null}))};
    }
  }
  return null;
}
function reply(res, status, body) {
  if (res.destroyed || res.writableEnded) return;
  res.writeHead(status, {'Content-Type': 'application/json; charset=utf-8', 'Cache-Control': 'no-store'});
  res.end(JSON.stringify(body));
}
const server = http.createServer(async (req, res) => {
  if (req.method === 'GET' && req.url === '/health') return reply(res, browser.isConnected() ? 200 : 503, {ready: browser.isConnected()});
  const id = req.method === 'GET' ? requestId(req.url) : '';
  if (!id) return reply(res, 400, {code: 10001});
  if (active >= 2) return reply(res, 503, {code: 10008});
  active++;
  let page;
  let timer;
  let status = 503, result = {code: 61009};
  try {
    page = await context.newPage();
    req.once('close', () => {if (!req.complete) page.close().catch(() => {});});
    const timeout = new Promise((_, reject) => {timer = setTimeout(() => reject(new Error('budget')), 21_000);});
    const operation = async () => {
      await page.goto('https://www.douyin.com/note/' + id, {waitUntil: 'domcontentloaded', timeout: 11_000});
      const final = new URL(page.url());
      if (final.origin !== 'https://www.douyin.com' || final.pathname.replace(/\/$/, '') !== '/note/' + id) return null;
      const stop = Date.now() + 10_000;
      while (Date.now() < stop) {
        try {
          const data = normalizeAweme(await page.evaluate(pageMetadata, id), id);
          if (data) return data;
        } catch { /* Redirect reloads may replace the execution context; keep within the original budget. */ }
        await page.waitForTimeout(200);
      }
      return null;
    };
    const aweme = await Promise.race([operation(), timeout]);
    if (aweme?.code === 61005) {status = 413; result = aweme;}
    else if (aweme) {status = 200; result = {aweme};}
  } catch { /* Fixed error response does not expose navigation URLs or platform state. */ }
  finally {
    clearTimeout(timer);
    await page?.close().catch(() => {});
    active--;
  }
  reply(res, status, result);
});
server.requestTimeout = 25_000;
server.headersTimeout = 5000;
server.maxConnections = 16;
server.listen(8080, '0.0.0.0');
const shutdown = async () => {server.close(); await context.close(); await browser.close(); await proxy.close(); process.exit(0);};
process.once('SIGTERM', shutdown); process.once('SIGINT', shutdown);
