// Validate the actual API decoder without adding dependencies or using invented UI data.
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { createRequire } from 'node:module'
import vm from 'node:vm'
const require = createRequire(new URL('../admin/package.json', import.meta.url))
const ts = require('typescript')
const module = { exports: {} }
vm.runInNewContext(ts.transpileModule(readFileSync(new URL('../admin/src/api/statistics.ts', import.meta.url), 'utf8'), {
  compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022 },
}).outputText, { module, exports: module.exports, require(name) {
  if (name === './client') return { apiRequest: () => {} }
  if (name === './types') return { isRecord: value => value !== null && typeof value === 'object' && !Array.isArray(value) }
  throw new Error(name)
} })
const { decodeStatistics, SUMMARY_KEYS } = module.exports
function fixture(days = 7) {
  const to = Date.parse('2026-01-01'), from = to - (days - 1) * 86_400_000
  return { days, from: new Date(from).toISOString().slice(0, 10), to: '2026-01-01', timezone: 'Asia/Shanghai',
    generatedAt: '2025-12-31T16:01:00Z', collectionSince: '2025-12-31T16:00:00Z',
    summary: Object.fromEntries(SUMMARY_KEYS.map(key => [key, 0])),
    daily: Array.from({ length: days }, (_, i) => ({ date: new Date(from + i * 86_400_000).toISOString().slice(0, 10), registrations: 0, opens: 0, activeUsers: 0, feedback: 0, submissions: 0 })),
    ranking: [], categories: [], modes: [], traffic: { since: '2025-12-31T16:00:00Z', requests: 0, clientErrors: 0, serverErrors: 0, averageMs: 0, activityWriteFailures: 0 },
  }
}
let checks = 0
for (const days of [7, 30, 90]) { assert.equal(decodeStatistics(fixture(days)).daily.length, days); checks++ }
for (const mutate of [
  v => { v.summary.users = -1 }, v => { delete v.summary.activeMembers }, v => { v.summary.users = '12' },
  v => { v.summary.users = Number.MAX_SAFE_INTEGER + 1 }, v => { v.daily[0].opens = 1.5 },
  v => { v.daily.pop() }, v => { v.daily[1].date = v.daily[0].date }, v => { v.to = '2026-02-30' },
  v => { v.days = 14 }, v => { v.timezone = 'UTC' }, v => { v.traffic.averageMs = Infinity },
  v => { v.traffic.requests = -1 }, v => { v.collectionSince = 'not-a-date' }, v => { v.generatedAt = null },
  v => { v.ranking = [{ code: 'x', name: 'x', category: 'x', status: 'UNKNOWN', opens: 0, allTimeOpens: 0 }] },
  v => { v.categories = [{ code: 'x', name: 'x', count: null, opens: 0 }] },
]) { const data = fixture(); mutate(data); assert.throws(() => decodeStatistics(data)); checks++ }
const values = fixture()
values.summary.users = 1000
values.ranking.push({ code: 'radix', name: '进制转换', category: '计算工具', status: 'ENABLED', opens: 3, allTimeOpens: 9 })
values.categories.push({ code: 'CALC', name: '计算工具', count: 2, opens: 9 })
assert.equal(decodeStatistics(values).ranking[0].allTimeOpens, 9); checks++
assert.equal(decodeStatistics(values).summary.pendingSites, 0); checks++
console.log(`${checks}/${checks} statistics decoder checks passed`)
