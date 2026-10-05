const assert = require('node:assert/strict');
const { pathToFileURL } = require('node:url');
const path = require('node:path');
let count = 0;
const check = (value) => { assert.ok(value); count++; };
(async () => {
  const { createDeletionFlow, createRequest } = await import(pathToFileURL(path.join(__dirname, '../deploy/site/account/delete/flow.mjs')));
  const calls = [], states = [];
  let fail = false;
  const flow = createDeletionFlow(async (...args) => {
    calls.push(args.slice(0, 4)); if (fail) throw new Error('private server detail');
    return args[0] === 'auth/login' ? { data: { user: { email: 'a@example.test' }, accessToken: 'access', refreshToken: 'refresh' } } : { data: null };
  }, state => states.push(state));
  await flow.remove('password', true); check(calls.length === 0);
  await flow.login(' a@example.test ', 'password'); check(states.at(-1).email === 'a@example.test');
  check(calls[0][2].email === 'a@example.test');
  await flow.remove('password', false); check(calls.length === 1);
  await flow.remove('', true); check(calls.length === 1);
  fail = true; await flow.remove('password', true); check(calls.length === 2);
  check(states.at(-1).email === 'a@example.test');
  check(states.some(s => s.message.includes('请求未能确认')) && states.every(s => !s.message.includes('private')));
  fail = false; await flow.remove('password', true); check(calls[2][1] === 'DELETE' && calls[2][3] === 'access');
  check(states.at(-1).email === null); check(states.at(-1).message.includes('已删除'));
  await flow.login('a@example.test', 'password'); await flow.cancel();
  check(calls.at(-1)[0] === 'auth/logout' && states.at(-1).email === null);
  let resolve; const staleStates = [];
  const stale = createDeletionFlow(() => new Promise(r => resolve = r), s => staleStates.push(s));
  const pending = stale.login('a@example.test', 'password'); stale.clear();
  resolve({ data: { user: { email: 'a@example.test' }, accessToken: 'access', refreshToken: 'refresh' } });
  await pending; check(staleStates.at(-1).email === null);
  let seen;
  const valid = createRequest('https://toolapi.zhzgo.cn/api/v1', async (url, opts) => {
    seen = { url, opts }; return new Response(JSON.stringify({ code: 0, traceId: 'trace', data: null }));
  });
  await valid('auth/me', 'DELETE', { email: 'a@example.test', password: 'password' }, 'access', new AbortController().signal);
  check(seen.url.endsWith('/auth/me') && seen.opts.method === 'DELETE');
  check(seen.opts.credentials === 'omit' && seen.opts.redirect === 'error' && seen.opts.cache === 'no-store');
  check(seen.opts.headers.Authorization === 'Bearer access');
  for (const response of [new Response('x'.repeat(16385)), new Response(new Uint8Array([0xff])), new Response('{}'),
    new Response('{"code":0,"traceId":"a"} trailing'), new Response('{"code":1,"traceId":"a"}', { status: 401 }),
    new Response('{"code":1,"traceId":"a"}', { status: 429 })]) {
    await assert.rejects(createRequest('https://toolapi.zhzgo.cn/api/v1', async () => response)('auth/me', 'DELETE', {}, null, new AbortController().signal)); count++;
  }
  console.log(`Web account deletion checks: ${count}/${count} passed`);
})().catch(() => { console.error('Web account deletion checks failed'); process.exitCode = 1; });
