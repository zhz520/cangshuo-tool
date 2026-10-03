import axios, { type AxiosRequestConfig } from 'axios'
import { isRecord, type ApiEnvelope, type ApiResult } from './types'

const client = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '/api/v1',
  timeout: 8000,
  headers: { Accept: 'application/json' },
})

export class ApiRequestError extends Error {
  constructor(message: string, readonly traceId?: string, readonly code?: number, readonly status?: number) {
    super(message)
    this.name = 'ApiRequestError'
  }
}

function parseEnvelope(value: unknown): ApiEnvelope<unknown> {
  if (!isRecord(value) || typeof value.code !== 'number' || !Number.isInteger(value.code)
    || typeof value.message !== 'string' || typeof value.traceId !== 'string'
    || !/^[0-9a-f]{32}$/.test(value.traceId) || !('data' in value)) {
    throw new ApiRequestError('服务响应格式异常，请稍后重试。')
  }
  return { code: value.code, message: value.message, data: value.data, traceId: value.traceId }
}

function errorMessage(code: number): string {
  switch (code) {
    case 10001: return '请求未被接受，请稍后重试。'
    case 10002: case 10003: case 10004: return '当前请求需要有效的身份认证。'
    case 10005: return '当前账号没有访问权限。'
    case 10006: return '请求的资源不存在。'
    case 10007: return '请求过于频繁，请稍后重试。'
    case 10008: return '平台服务暂时不可用，请稍后重试。'
    default: return '服务处理请求失败，请稍后重试。'
  }
}

function normalizeError(error: unknown): ApiRequestError {
  if (error instanceof ApiRequestError) return error
  if (axios.isAxiosError(error)) {
    if (error.response) {
      try {
        const envelope = parseEnvelope(error.response.data)
        return new ApiRequestError(errorMessage(envelope.code), envelope.traceId, envelope.code, error.response.status)
      } catch {
        return new ApiRequestError('服务响应异常，请稍后重试。', undefined, undefined, error.response.status)
      }
    }
    if (error.code === 'ECONNABORTED' || error.code === 'ETIMEDOUT') {
      return new ApiRequestError('连接服务超时，请稍后重试。')
    }
  }
  return new ApiRequestError('暂时无法连接平台服务，请稍后重试。')
}

export async function apiRequest<T>(config: AxiosRequestConfig, decode: (value: unknown) => T): Promise<ApiResult<T>> {
  try {
    const response = await client.request<unknown>(config)
    const envelope = parseEnvelope(response.data)
    if (envelope.code !== 0) {
      throw new ApiRequestError(errorMessage(envelope.code), envelope.traceId, envelope.code, response.status)
    }
    let data: T
    try {
      data = decode(envelope.data)
    } catch {
      throw new ApiRequestError('服务响应格式异常，请稍后重试。', envelope.traceId, undefined, response.status)
    }
    return { data, traceId: envelope.traceId }
  } catch (error: unknown) {
    throw normalizeError(error)
  }
}
