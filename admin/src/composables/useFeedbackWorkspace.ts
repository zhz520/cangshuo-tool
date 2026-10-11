import { computed, onScopeDispose, reactive, ref } from 'vue'
import { ApiRequestError } from '@/api/client'
import { FEEDBACK_STATUSES, listAdminFeedback, updateAdminFeedback, type AdminFeedback } from '@/api/feedback'

export type FeedbackStatus = typeof FEEDBACK_STATUSES[number]
export const FEEDBACK_STATUS_LABELS: Record<FeedbackStatus, string> = { PENDING: '待处理', PROCESSING: '处理中', RESOLVED: '已解决' }
export const FEEDBACK_STATUS_TONES: Record<FeedbackStatus, 'warning' | 'info' | 'success'> = { PENDING: 'warning', PROCESSING: 'info', RESOLVED: 'success' }
export const FEEDBACK_TYPE_LABELS: Record<string, string> = { BUG: '问题', SUGGESTION: '建议', OTHER: '其他' }

interface FeedbackApi { list: typeof listAdminFeedback; update: typeof updateAdminFeedback }

function messageFor(error: unknown): string {
  if (error instanceof ApiRequestError) {
    switch (error.code) {
      case 10006: return '反馈不存在，可能已被移除。'
      case 10001: return '请求参数不合法，请检查状态与回复内容。'
      case 10005: return '当前账号没有管理权限。'
      case 10002: case 10003: case 10004: return '登录状态已失效，请重新登录。'
      default: return error.message
    }
  }
  return '暂时无法连接平台服务，请稍后重试。'
}

export function useFeedbackWorkspace(api: FeedbackApi = { list: listAdminFeedback, update: updateAdminFeedback }) {
  const entries = ref<AdminFeedback[]>([])
  const total = ref(0)
  const page = ref(1)
  const pageSize = 20
  const keyword = ref('')
  const appliedKeyword = ref('')
  const statusFilter = ref<FeedbackStatus | undefined>()
  const loading = ref(false)
  const loaded = ref(false)
  const errorMessage = ref<string | null>(null)
  const errorTraceId = ref<string | null>(null)
  const detailVisible = ref(false)
  const editing = ref<AdminFeedback | null>(null)
  const saving = ref(false)
  const detailError = ref<string | null>(null)
  const detailTraceId = ref<string | null>(null)
  const form = reactive({ status: 'PENDING' as FeedbackStatus, reply: '' })
  const hasFilters = computed(() => !!appliedKeyword.value || statusFilter.value !== undefined)
  const detailTitle = computed(() => editing.value ? `处理反馈 · #${editing.value.id}` : '处理反馈')
  let requestVersion = 0
  let disposed = false
  onScopeDispose(() => { disposed = true; requestVersion++ })

  async function load(): Promise<void> {
    if (disposed) return
    const version = ++requestVersion
    loading.value = true
    errorMessage.value = null
    errorTraceId.value = null
    try {
      const result = await api.list({ page: page.value, pageSize, keyword: appliedKeyword.value || undefined, status: statusFilter.value })
      if (disposed || version !== requestVersion) return
      // A status change can remove the last row of the current filtered page.
      const lastPage = Math.max(1, Math.ceil(result.data.total / pageSize))
      if (result.data.records.length === 0 && page.value > lastPage) {
        page.value = lastPage
        await load()
        return
      }
      entries.value = result.data.records
      total.value = result.data.total
      loaded.value = true
    } catch (error: unknown) {
      if (disposed || version !== requestVersion) return
      errorMessage.value = messageFor(error)
      errorTraceId.value = error instanceof ApiRequestError ? error.traceId ?? null : null
    } finally {
      if (!disposed && version === requestVersion) loading.value = false
    }
  }

  async function search(): Promise<void> {
    const value = keyword.value.trim()
    if (value.length > 128 || /[\u0000-\u001f\u007f-\u009f]/.test(value)) {
      errorMessage.value = '搜索词最多 128 字符，不能包含控制字符。'
      errorTraceId.value = null
      return
    }
    appliedKeyword.value = value
    page.value = 1
    await load()
  }

  async function selectStatus(status: FeedbackStatus | undefined): Promise<void> {
    if (saving.value || status === statusFilter.value) return
    statusFilter.value = status
    page.value = 1
    await load()
  }

  async function reset(): Promise<void> {
    keyword.value = ''
    appliedKeyword.value = ''
    statusFilter.value = undefined
    page.value = 1
    await load()
  }

  async function changePage(value: number): Promise<void> {
    if (saving.value || !Number.isInteger(value) || value < 1) return
    page.value = value
    await load()
  }

  function open(entry: AdminFeedback): void {
    if (saving.value) return
    editing.value = { ...entry }
    form.status = entry.status as FeedbackStatus
    form.reply = entry.reply ?? ''
    detailError.value = null
    detailTraceId.value = null
    detailVisible.value = true
  }

  async function save(): Promise<boolean> {
    if (!editing.value || saving.value || disposed) return false
    detailError.value = null
    detailTraceId.value = null
    const reply = form.reply.trim()
    if (!(FEEDBACK_STATUSES as readonly string[]).includes(form.status)) {
      detailError.value = '状态不在允许范围内。'
      return false
    }
    if (reply.length > 2000 || /[\u0000-\u0009\u000b-\u001f\u007f-\u009f]/.test(reply)) {
      detailError.value = '回复最多 2000 字符，可换行，不能包含其他控制字符。'
      return false
    }
    if (editing.value.reply && !reply) {
      detailError.value = '已有回复不能清空；仅更新状态时请保留回复内容。'
      return false
    }
    saving.value = true
    const id = editing.value.id
    // Omit an unchanged reply so a status-only edit does not rewrite repliedAt.
    const changedReply = reply && reply !== editing.value.reply ? reply : null
    try {
      const result = await api.update(id, form.status, changedReply)
      if (disposed) return false
      // Invalidate a list request started before this successful write.
      requestVersion++
      entries.value = entries.value.map(entry => entry.id === id ? result.data : entry)
      detailVisible.value = false
      await load()
      return !disposed
    } catch (error: unknown) {
      if (!disposed) {
        detailError.value = messageFor(error)
        detailTraceId.value = error instanceof ApiRequestError ? error.traceId ?? null : null
      }
      return false
    } finally {
      if (!disposed) saving.value = false
    }
  }

  return { entries, total, page, pageSize, keyword, statusFilter, loading, loaded, errorMessage, errorTraceId,
    detailVisible, editing, saving, detailError, detailTraceId, form, hasFilters, detailTitle,
    load, search, selectStatus, reset, changePage, open, save }
}
