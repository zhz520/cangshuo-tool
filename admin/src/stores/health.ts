import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { getHealth } from '@/api/health'
import { ApiRequestError } from '@/api/client'

export const useHealthStore = defineStore('health', () => {
  const state = ref<'idle' | 'loading' | 'success' | 'error'>('idle')
  const lastCheckedAt = ref<Date | null>(null)
  const elapsedMs = ref<number | null>(null)
  const traceId = ref<string | null>(null)
  const errorMessage = ref<string | null>(null)
  const isChecking = computed(() => state.value === 'loading')
  const isConnected = computed(() => state.value === 'success')
  const label = computed(() => ({ idle: '待检查', loading: '连接中', success: '服务在线', error: '连接异常' })[state.value])

  async function refresh() {
    if (isChecking.value) return
    state.value = 'loading'
    errorMessage.value = null
    traceId.value = null
    elapsedMs.value = null
    const startedAt = performance.now()
    try {
      const result = await getHealth()
      elapsedMs.value = Math.round(performance.now() - startedAt)
      traceId.value = result.traceId
      state.value = 'success'
    } catch (error: unknown) {
      errorMessage.value = error instanceof ApiRequestError ? error.message : '暂时无法连接平台服务，请稍后重试。'
      traceId.value = error instanceof ApiRequestError ? error.traceId ?? null : null
      state.value = 'error'
    } finally {
      lastCheckedAt.value = new Date()
    }
  }

  return { state, lastCheckedAt, elapsedMs, traceId, errorMessage, isChecking, isConnected, label, refresh }
})
