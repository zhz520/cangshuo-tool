import { apiRequest } from './client'
import { isRecord, type ApiResult } from './types'

export interface AdminCategoryEntry {
  id: number
  code: string
  name: string
  icon: string | null
  description: string
  sortOrder: number
  enabled: boolean
  toolCount: number
  updatedAt: string
}

export interface AdminCategoryPayload {
  code?: string
  name: string
  description: string
  icon: string | null
  sortOrder: number
  enabled: boolean
}

function decodeCategory(value: unknown): AdminCategoryEntry {
  if (!isRecord(value) || typeof value.id !== 'number' || !Number.isInteger(value.id) || value.id <= 0
    || typeof value.code !== 'string' || !/^[A-Z][A-Z0-9_]{0,31}$/.test(value.code)
    || typeof value.name !== 'string' || value.name.length < 1 || value.name.length > 64
    || (value.icon !== null && typeof value.icon !== 'string')
    || typeof value.description !== 'string' || value.description.length > 500
    || typeof value.sortOrder !== 'number' || !Number.isInteger(value.sortOrder)
    || typeof value.enabled !== 'boolean' || typeof value.toolCount !== 'number'
    || typeof value.updatedAt !== 'string') {
    throw new Error('invalid admin category')
  }
  return {
    id: value.id,
    code: value.code,
    name: value.name,
    icon: value.icon ?? null,
    description: value.description,
    sortOrder: value.sortOrder,
    enabled: value.enabled,
    toolCount: value.toolCount,
    updatedAt: value.updatedAt,
  }
}

function decodeCategories(value: unknown): AdminCategoryEntry[] {
  if (!Array.isArray(value)) throw new Error('invalid admin category list')
  return value.map(decodeCategory)
}

export function listAdminCategoryEntries(): Promise<ApiResult<AdminCategoryEntry[]>> {
  return apiRequest({ url: '/admin/categories', method: 'GET' }, decodeCategories)
}

export function createAdminCategory(payload: AdminCategoryPayload): Promise<ApiResult<AdminCategoryEntry>> {
  return apiRequest({ url: '/admin/categories', method: 'POST', data: payload }, decodeCategory)
}

export function updateAdminCategory(code: string, payload: AdminCategoryPayload): Promise<ApiResult<AdminCategoryEntry>> {
  return apiRequest({ url: `/admin/categories/${encodeURIComponent(code)}`, method: 'PUT', data: payload }, decodeCategory)
}

export function updateAdminCategoryStatus(code: string, enabled: boolean): Promise<ApiResult<AdminCategoryEntry>> {
  return apiRequest({ url: `/admin/categories/${encodeURIComponent(code)}/status`, method: 'PATCH', data: { enabled } }, decodeCategory)
}

export function deleteAdminCategory(code: string): Promise<ApiResult<null>> {
  return apiRequest({ url: `/admin/categories/${encodeURIComponent(code)}`, method: 'DELETE' }, () => null)
}
