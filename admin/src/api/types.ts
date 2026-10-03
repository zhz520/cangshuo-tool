export interface ApiEnvelope<T> {
  code: number
  message: string
  data: T | null
  traceId: string
}

export interface ApiResult<T> {
  data: T
  traceId: string
}

export function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value)
}
