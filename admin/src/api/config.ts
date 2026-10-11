import { apiRequest } from './client'
import { isRecord, type ApiResult } from './types'

export const CONFIG_KEYS = ['APP_LATEST_VERSION_CODE', 'APP_LATEST_VERSION_NAME', 'APP_UPDATE_URL', 'APP_UPDATE_NOTES'] as const

export type ConfigKey = (typeof CONFIG_KEYS)[number]

export interface ConfigEntry {
  key: string
  value: string
}

function decodeEntry(value: unknown): ConfigEntry {
  if (!isRecord(value) || typeof value.key !== 'string' || typeof value.value !== 'string') {
    throw new Error('invalid config entry')
  }
  return { key: value.key, value: value.value }
}

export function listAdminConfig(): Promise<ApiResult<ConfigEntry[]>> {
  return apiRequest({ url: '/admin/config', method: 'GET' }, value => {
    if (!Array.isArray(value)) throw new Error('invalid config list')
    return value.map(decodeEntry)
  })
}

export function updateAdminConfig(key: ConfigKey, value: string): Promise<ApiResult<ConfigEntry>> {
  return apiRequest({ url: `/admin/config/${key}`, method: 'PUT', data: { value } }, decodeEntry)
}
