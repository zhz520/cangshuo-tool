import { onScopeDispose, ref } from 'vue'
import { ApiRequestError } from '@/api/client'

/** Each refresh owns its result; a superseded or unmounted request cannot update the page. */
export function useAdminRequest(messageFor: (error: unknown) => string) {
  const loading = ref(false)
  const errorMessage = ref<string | null>(null)
  const traceId = ref<string | null>(null)
  let version = 0
  let disposed = false
  onScopeDispose(() => { disposed = true; version++ })

  async function run<T>(request: () => Promise<T>, apply: (result: T) => void): Promise<void> {
    if (disposed) return
    const current = ++version
    loading.value = true
    errorMessage.value = null
    traceId.value = null
    try {
      const result = await request()
      if (!disposed && current === version) apply(result)
    } catch (error: unknown) {
      if (!disposed && current === version) {
        errorMessage.value = messageFor(error)
        traceId.value = error instanceof ApiRequestError ? error.traceId ?? null : null
      }
    } finally {
      if (!disposed && current === version) loading.value = false
    }
  }
  return { loading, errorMessage, traceId, run }
}
