import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { createRequire } from 'node:module'
import vm from 'node:vm'

const require = createRequire(new URL('../admin/package.json', import.meta.url))
const { effectScope } = require('vue')
const ts = require('typescript')
class ApiRequestError extends Error { constructor(message, traceId) { super(message); this.traceId = traceId } }
const source = readFileSync(new URL('../admin/src/composables/useAdminRequest.ts', import.meta.url), 'utf8')
const compiled = ts.transpileModule(source, { compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022 } }).outputText
const module = { exports: {} }
vm.runInNewContext(compiled, { module, exports: module.exports, require: name => name === '@/api/client' ? { ApiRequestError } : require(name) })
function deferred() { let resolve, reject; const promise = new Promise((yes, no) => { resolve = yes; reject = no }); return { promise, resolve, reject } }
let passed = 0
async function check(name, test) {
  const scope = effectScope()
  const state = scope.run(() => module.exports.useAdminRequest(error => error.message))
  try { await test(state, scope); console.log(`PASS ${++passed}: ${name}`) } finally { scope.stop() }
}
await check('Newest filter result wins over stale success and stale failure', async state => {
  const first = deferred(), second = deferred(), latest = deferred()
  let rows = []
  const apply = value => { rows = value }
  const requests = [state.run(() => first.promise, apply), state.run(() => second.promise, apply), state.run(() => latest.promise, apply)]
  latest.resolve(['latest']); await requests[2]
  first.resolve(['old']); second.reject(new ApiRequestError('old error', 'old-trace'))
  await Promise.all(requests)
  assert.deepEqual(rows, ['latest']); assert.equal(state.errorMessage.value, null); assert.equal(state.loading.value, false)
})
await check('Old completion cannot clear a newer loading state', async state => {
  const first = deferred(), second = deferred()
  const old = state.run(() => first.promise, () => assert.fail('stale apply'))
  const latest = state.run(() => second.promise, () => {})
  first.resolve([]); await old; assert.equal(state.loading.value, true)
  second.resolve([]); await latest; assert.equal(state.loading.value, false)
})
await check('Failed refresh preserves previous rows and exposes trace; retry clears error', async state => {
  let rows = ['previous']
  await state.run(() => Promise.reject(new ApiRequestError('offline', 'example-trace')), value => { rows = value })
  assert.deepEqual(rows, ['previous']); assert.equal(state.errorMessage.value, 'offline'); assert.equal(state.traceId.value, 'example-trace')
  await state.run(() => Promise.resolve(['fresh']), value => { rows = value })
  assert.deepEqual(rows, ['fresh']); assert.equal(state.errorMessage.value, null); assert.equal(state.traceId.value, null)
})
await check('Unmounted page ignores pending completion and rejects new work', async (state, scope) => {
  const pending = deferred()
  const request = state.run(() => pending.promise, () => assert.fail('disposed apply'))
  scope.stop(); pending.reject(new ApiRequestError('late error', 'late-trace')); await request
  await state.run(() => assert.fail('disposed request'), () => {})
  assert.equal(state.errorMessage.value, null)
})
console.log(`${passed}/${passed} latest-request checks passed`)
