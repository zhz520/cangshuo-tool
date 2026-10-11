// Exercises the actual Vue state controller with deterministic API responses.
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { createRequire } from 'node:module'
import vm from 'node:vm'

const require = createRequire(new URL('../admin/package.json', import.meta.url))
const { effectScope } = require('vue')
const ts = require('typescript')
const source = readFileSync(new URL('../admin/src/composables/useFeedbackWorkspace.ts', import.meta.url), 'utf8')
const compiled = ts.transpileModule(source, { compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022 } }).outputText
class ApiRequestError extends Error {
  constructor(message, traceId, code, status) { super(message); Object.assign(this, { traceId, code, status }) }
}
const module = { exports: {} }
vm.runInNewContext(compiled, { module, exports: module.exports, require(name) {
  if (name === '@/api/client') return { ApiRequestError }
  if (name === '@/api/feedback') return { FEEDBACK_STATUSES: ['PENDING', 'PROCESSING', 'RESOLVED'] }
  return require(name)
} })
const entry = (id = 1, status = 'PENDING', reply = null) => ({ id, userId: 1, userEmail: 'sample@example.invalid', type: 'SUGGESTION', content: '示例建议', contact: null, status, reply, repliedAt: reply ? '2026-10-06T01:00:00Z' : null, createdAt: '2026-10-06T01:00:00Z', updatedAt: '2026-10-06T01:00:00Z' })
const result = (records, total = records.length, page = 1) => ({ data: { records, total, page, pageSize: 20 }, traceId: 'a'.repeat(32) })
function deferred() { let resolve, reject; const promise = new Promise((yes, no) => { resolve = yes; reject = no }); return { promise, resolve, reject } }
function workspace(api) {
  const scope = effectScope()
  const state = scope.run(() => module.exports.useFeedbackWorkspace(api))
  return { state, stop: () => scope.stop() }
}
let passed = 0
async function check(name, run) { await run(); passed++; console.log(`PASS ${passed}: ${name}`) }

await check('Newest filter owns rows, count, loading and error', async () => {
  const requests = []
  const { state, stop } = workspace({ list(query) { const pending = deferred(); requests.push({ query, ...pending }); return pending.promise } })
  const initial = state.load(), pending = state.selectStatus('PENDING'), processing = state.selectStatus('PROCESSING')
  requests[2].resolve(result([entry(3, 'PROCESSING')], 7))
  await processing
  requests[0].resolve(result([entry(1)], 100))
  requests[1].reject(new ApiRequestError('Old failure', 'b'.repeat(32), 10008))
  await Promise.all([initial, pending])
  assert.equal(state.entries.value[0].id, 3)
  assert.equal(state.total.value, 7)
  assert.equal(state.loading.value, false)
  assert.equal(state.errorMessage.value, null)
  stop()
})
await check('Old completion cannot stop a newer pending request', async () => {
  const requests = []
  const { state, stop } = workspace({ list() { const pending = deferred(); requests.push(pending); return pending.promise } })
  const old = state.load(), latest = state.selectStatus('PENDING')
  requests[0].resolve(result([entry()]))
  await old
  assert.equal(state.loading.value, true)
  assert.equal(state.entries.value.length, 0)
  requests[1].resolve(result([]))
  await latest
  assert.equal(state.loaded.value, true)
  stop()
})
await check('Search resets page and trims query; status changes keep applied search', async () => {
  const queries = []
  const { state, stop } = workspace({ list: async query => { queries.push(query); return result([entry()], 80, query.page) } })
  await state.changePage(3)
  state.keyword.value = '  收藏  '
  await state.search()
  assert.equal(queries.at(-1).page, 1)
  assert.equal(queries.at(-1).keyword, '收藏')
  state.keyword.value = '尚未查询的文字'
  await state.selectStatus('RESOLVED')
  assert.equal(queries.at(-1).keyword, '收藏')
  assert.equal(queries.at(-1).status, 'RESOLVED')
  await state.reset()
  assert.equal(queries.at(-1).keyword, undefined)
  assert.equal(state.statusFilter.value, undefined)
  assert.equal(state.keyword.value, '')
  stop()
})
await check('Invalid search and page values never call API', async () => {
  let calls = 0
  const { state, stop } = workspace({ list: async () => { calls++; return result([]) } })
  state.keyword.value = 'x'.repeat(129)
  await state.search()
  state.keyword.value = 'a\tb'
  await state.search()
  await state.changePage(0)
  await state.changePage(1.5)
  assert.equal(calls, 0)
  assert.ok(state.errorMessage.value)
  stop()
})
await check('Failed refresh keeps last rows/count and exposes request identity', async () => {
  let fail = false
  const { state, stop } = workspace({ list: async () => { if (fail) throw new ApiRequestError('Offline', 'c'.repeat(32), 10008); return result([entry()], 12) } })
  await state.load()
  fail = true
  await state.load()
  assert.equal(state.entries.value[0].id, 1)
  assert.equal(state.total.value, 12)
  assert.equal(state.errorTraceId.value, 'c'.repeat(32))
  assert.ok(state.errorMessage.value)
  assert.equal(state.loading.value, false)
  stop()
})
await check('Last filtered row removal clamps page and loads the previous page', async () => {
  const pages = []
  const { state, stop } = workspace({ list: async query => { pages.push(query.page); return result(query.page === 2 ? [] : [entry()], 20, query.page) } })
  await state.changePage(2)
  assert.deepEqual(pages, [2, 1])
  assert.equal(state.page.value, 1)
  assert.equal(state.entries.value.length, 1)
  assert.equal(state.loading.value, false)
  stop()
})
await check('Save failure preserves drawer, status and reply for correction', async () => {
  const { state, stop } = workspace({ update: async () => { throw new ApiRequestError('Service unavailable', 'd'.repeat(32), 10008) } })
  state.open(entry())
  state.form.status = 'RESOLVED'
  state.form.reply = '保留这段回复'
  assert.equal(await state.save(), false)
  assert.equal(state.detailVisible.value, true)
  assert.equal(state.form.reply, '保留这段回复')
  assert.equal(state.form.status, 'RESOLVED')
  assert.equal(state.detailTraceId.value, 'd'.repeat(32))
  assert.equal(state.saving.value, false)
  stop()
})
await check('Duplicate saves and changing the edited row are blocked in flight', async () => {
  const pending = deferred()
  let writes = 0
  const { state, stop } = workspace({ list: async () => result([entry(1, 'RESOLVED')]), update: () => { writes++; return pending.promise } })
  state.open(entry())
  const first = state.save()
  assert.equal(await state.save(), false)
  state.open(entry(2))
  assert.equal(state.editing.value.id, 1)
  assert.equal(writes, 1)
  pending.resolve({ data: entry(1, 'RESOLVED') })
  assert.equal(await first, true)
  assert.equal(state.detailVisible.value, false)
  stop()
})
await check('Unchanged reply sends null; changing a reply sends its trimmed text', async () => {
  const writes = []
  const { state, stop } = workspace({ list: async () => result([entry()]), update: async (id, status, reply) => { writes.push({ id, status, reply }); return { data: entry(id, status, reply) } } })
  state.open(entry(1, 'PENDING', '已有回复'))
  state.form.status = 'PROCESSING'
  await state.save()
  assert.equal(writes[0].reply, null)
  state.open(entry(1, 'PROCESSING', '已有回复'))
  state.form.reply = '  新回复\n继续说明  '
  await state.save()
  assert.equal(writes[1].reply, '新回复\n继续说明')
  stop()
})
await check('Reply cannot be cleared, exceed limit, use control characters or invalid status', async () => {
  let writes = 0
  const { state, stop } = workspace({ update: async () => { writes++; return { data: entry() } } })
  state.open(entry(1, 'PENDING', '已有回复'))
  state.form.reply = ' '
  assert.equal(await state.save(), false)
  state.form.reply = 'x'.repeat(2001)
  assert.equal(await state.save(), false)
  state.form.reply = 'a\tb'
  assert.equal(await state.save(), false)
  state.form.reply = '合法内容'
  state.form.status = 'INVALID'
  assert.equal(await state.save(), false)
  assert.equal(writes, 0)
  assert.equal(state.detailVisible.value, true)
  stop()
})
await check('Blank optional reply and exactly 2000 characters are valid', async () => {
  const replies = []
  const { state, stop } = workspace({ list: async () => result([]), update: async (id, status, reply) => { replies.push(reply); return { data: entry(id, status, reply) } } })
  state.open(entry())
  state.form.reply = ' '
  assert.equal(await state.save(), true)
  state.open(entry())
  state.form.reply = 'x'.repeat(2000)
  assert.equal(await state.save(), true)
  assert.equal(replies[0], null)
  assert.equal(replies[1].length, 2000)
  stop()
})
await check('A pre-save list result cannot overwrite the successful write', async () => {
  const reads = []
  const { state, stop } = workspace({ list() { const pending = deferred(); reads.push(pending); return pending.promise }, update: async () => ({ data: entry(1, 'RESOLVED', '已完成') }) })
  state.entries.value = [entry()]
  const old = state.load()
  state.open(entry())
  const save = state.save()
  await Promise.resolve()
  reads[0].resolve(result([entry()]))
  await old
  assert.equal(state.entries.value[0].status, 'RESOLVED')
  reads[1].resolve(result([entry(1, 'RESOLVED', '已完成')]))
  assert.equal(await save, true)
  stop()
})
await check('Successful write followed by failed reload reports both outcomes correctly', async () => {
  const { state, stop } = workspace({ list: async () => { throw new ApiRequestError('Refresh failed') }, update: async () => ({ data: entry(1, 'RESOLVED') }) })
  state.entries.value = [entry()]
  state.open(entry())
  assert.equal(await state.save(), true)
  assert.equal(state.detailVisible.value, false)
  assert.equal(state.entries.value[0].status, 'RESOLVED')
  assert.ok(state.errorMessage.value)
  assert.equal(state.saving.value, false)
  stop()
})
await check('Disposing a page ignores its pending read and write completions', async () => {
  const read = deferred(), write = deferred()
  let reads = 0
  const { state, stop } = workspace({ list: () => { reads++; return read.promise }, update: () => write.promise })
  const reading = state.load()
  state.open(entry())
  const saving = state.save()
  stop()
  read.resolve(result([entry(9)]))
  write.resolve({ data: entry(1, 'RESOLVED') })
  await reading
  assert.equal(await saving, false)
  assert.equal(state.entries.value.length, 0)
  assert.equal(reads, 1)
})
console.log(`PASS: ${passed}/${passed} feedback workspace regressions`)
