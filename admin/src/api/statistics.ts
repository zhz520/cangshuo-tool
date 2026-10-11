import { apiRequest } from './client'
import { isRecord } from './types'

export const SUMMARY_KEYS = ['users', 'enabledUsers', 'disabledUsers', 'activeMembers', 'expiredMembers', 'newUsers',
  'tools', 'enabledTools', 'disabledTools', 'maintenanceTools', 'feedbackTotal', 'pendingFeedback', 'processingFeedback',
  'resolvedFeedback', 'sites', 'pendingSites', 'approvedSites', 'rejectedSites', 'storedFiles', 'storedBytes',
  'syncedFavorites', 'syncedRecents', 'allTimeOpens', 'windowOpens', 'todayOpens', 'activeUsers', 'dailyActiveUsers',
  'weeklyActiveUsers', 'aiAttempts'] as const
export type SummaryKey = typeof SUMMARY_KEYS[number]
export type DailyKey = 'opens' | 'registrations' | 'activeUsers' | 'feedback' | 'submissions'
export interface DailyStatistics { date: string; registrations: number; opens: number; activeUsers: number; feedback: number; submissions: number }
export interface ToolRank { code: string; name: string; category: string; status: string; opens: number; allTimeOpens: number }
export interface Distribution { code: string; name: string; count: number; opens: number }
export interface AdminStatistics {
  days: number; from: string; to: string; timezone: string; generatedAt: string; collectionSince: string
  summary: Record<SummaryKey, number>; daily: DailyStatistics[]; ranking: ToolRank[]
  categories: Distribution[]; modes: Distribution[]
  traffic: { since: string; requests: number; clientErrors: number; serverErrors: number; averageMs: number; activityWriteFailures: number }
}
const dailyKeys: DailyKey[] = ['opens', 'registrations', 'activeUsers', 'feedback', 'submissions']
function count(value: unknown): number {
  if (typeof value !== 'number' || !Number.isSafeInteger(value) || value < 0) throw new Error('invalid statistics count')
  return value
}
function label(value: unknown, max = 128): string {
  if (typeof value !== 'string' || !value.length || value.length > max) throw new Error('invalid statistics label')
  return value
}
function day(value: unknown): string {
  if (typeof value !== 'string' || !/^\d{4}-\d{2}-\d{2}$/.test(value)
    || !Number.isFinite(Date.parse(value)) || new Date(value).toISOString().slice(0, 10) !== value) throw new Error('invalid statistics date')
  return value
}
function instant(value: unknown): string {
  if (typeof value !== 'string' || !/^\d{4}-\d{2}-\d{2}T.+Z$/.test(value) || !Number.isFinite(Date.parse(value))) throw new Error('invalid statistics instant')
  return value
}
function distribution(value: unknown): Distribution[] {
  if (!Array.isArray(value) || value.length > 300) throw new Error('invalid statistics distribution')
  return value.map(row => {
    if (!isRecord(row)) throw new Error('invalid distribution row')
    return { code: label(row.code, 64), name: label(row.name), count: count(row.count), opens: count(row.opens) }
  })
}
export function decodeStatistics(value: unknown): AdminStatistics {
  if (!isRecord(value) || ![7, 30, 90].includes(value.days as number) || value.timezone !== 'Asia/Shanghai'
    || !isRecord(value.summary) || !Array.isArray(value.daily) || value.daily.length !== value.days
    || !Array.isArray(value.ranking) || value.ranking.length > 20 || !isRecord(value.traffic)) throw new Error('invalid statistics')
  const from = day(value.from), to = day(value.to), days = value.days as number
  if ((Date.parse(to) - Date.parse(from)) / 86_400_000 + 1 !== days) throw new Error('invalid statistics range')
  const summary = Object.fromEntries(SUMMARY_KEYS.map(key => [key, count((value.summary as Record<string, unknown>)[key])])) as Record<SummaryKey, number>
  const daily = value.daily.map((row, i) => {
    if (!isRecord(row) || day(row.date) !== new Date(Date.parse(from) + i * 86_400_000).toISOString().slice(0, 10)) throw new Error('invalid daily row')
    return { date: row.date, ...Object.fromEntries(dailyKeys.map(key => [key, count(row[key])])) } as DailyStatistics
  })
  const ranking = value.ranking.map(row => {
    if (!isRecord(row) || !['ENABLED', 'DISABLED', 'MAINTENANCE'].includes(row.status as string)) throw new Error('invalid statistics rank')
    return { code: label(row.code, 64), name: label(row.name), category: label(row.category), status: row.status as string,
      opens: count(row.opens), allTimeOpens: count(row.allTimeOpens) }
  })
  const t = value.traffic
  if (typeof t.averageMs !== 'number' || !Number.isFinite(t.averageMs) || t.averageMs < 0) throw new Error('invalid latency')
  return { days, from, to, timezone: value.timezone, generatedAt: instant(value.generatedAt), collectionSince: instant(value.collectionSince),
    summary, daily, ranking, categories: distribution(value.categories), modes: distribution(value.modes),
    traffic: { since: instant(t.since), requests: count(t.requests), clientErrors: count(t.clientErrors), serverErrors: count(t.serverErrors),
      averageMs: t.averageMs, activityWriteFailures: count(t.activityWriteFailures) } }
}
export function getStatistics(days: number) {
  return apiRequest({ method: 'GET', url: '/admin/statistics', params: { days } }, decodeStatistics)
}
