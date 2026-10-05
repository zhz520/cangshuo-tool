import { apiRequest } from './client'
import { isRecord, type ApiResult } from './types'

export const TOOL_MODES = ['LOCAL', 'SERVER', 'HYBRID', 'WEB'] as const
export const TOOL_STATUSES = ['ENABLED', 'DISABLED', 'MAINTENANCE'] as const

export interface AdminTool {
  id: number
  code: string
  name: string
  description: string
  categoryCode: string
  categoryName: string
  icon: string | null
  keywords: string[]
  mode: string
  requiresLogin: boolean
  status: string
  version: number
  sortOrder: number
  featured: boolean
  configJson: string
  updatedAt: string
}

export interface AdminToolPage {
  records: AdminTool[]
  page: number
  pageSize: number
  total: number
}

export interface AdminCategory {
  code: string
  name: string
  sortOrder: number
  enabled: boolean
}

export interface AdminToolPayload {
  code?: string
  name: string
  description: string
  categoryCode: string
  icon: string | null
  keywords: string[]
  mode: string
  requiresLogin: boolean
  status: string
  version: number
  sortOrder: number
  featured: boolean
  configJson: string
}

function isMode(value: unknown): boolean {
  return typeof value === 'string' && (TOOL_MODES as readonly string[]).includes(value)
}

function isStatus(value: unknown): boolean {
  return typeof value === 'string' && (TOOL_STATUSES as readonly string[]).includes(value)
}

function decodeTool(value: unknown): AdminTool {
  if (!isRecord(value) || typeof value.id !== 'number' || !Number.isInteger(value.id) || value.id <= 0
    || typeof value.code !== 'string' || value.code.length < 1 || value.code.length > 64
    || typeof value.name !== 'string' || value.name.length < 1 || value.name.length > 128
    || typeof value.description !== 'string' || value.description.length > 500
    || typeof value.categoryCode !== 'string' || typeof value.categoryName !== 'string'
    || (value.icon !== null && typeof value.icon !== 'string')
    || !Array.isArray(value.keywords) || value.keywords.some(item => typeof item !== 'string')
    || !isMode(value.mode) || !isStatus(value.status)
    || typeof value.requiresLogin !== 'boolean' || typeof value.featured !== 'boolean'
    || typeof value.version !== 'number' || typeof value.sortOrder !== 'number'
    || typeof value.configJson !== 'string' || value.configJson.length > 10000
    || typeof value.updatedAt !== 'string') {
    throw new Error('invalid admin tool')
  }
  return {
    id: value.id,
    code: value.code,
    name: value.name,
    description: value.description,
    categoryCode: value.categoryCode,
    categoryName: value.categoryName,
    icon: value.icon ?? null,
    keywords: value.keywords as string[],
    mode: value.mode as string,
    requiresLogin: value.requiresLogin,
    status: value.status as string,
    version: value.version,
    sortOrder: value.sortOrder,
    featured: value.featured,
    configJson: value.configJson,
    updatedAt: value.updatedAt,
  }
}

function decodePage(value: unknown): AdminToolPage {
  if (!isRecord(value) || !Array.isArray(value.records) || typeof value.page !== 'number'
    || typeof value.pageSize !== 'number' || typeof value.total !== 'number') {
    throw new Error('invalid admin tool page')
  }
  return { records: value.records.map(decodeTool), page: value.page, pageSize: value.pageSize, total: value.total }
}

function decodeCategories(value: unknown): AdminCategory[] {
  if (!Array.isArray(value)) throw new Error('invalid admin categories')
  return value.map(item => {
    if (!isRecord(item) || typeof item.code !== 'string' || typeof item.name !== 'string'
      || typeof item.sortOrder !== 'number' || typeof item.enabled !== 'boolean') {
      throw new Error('invalid admin category')
    }
    return { code: item.code, name: item.name, sortOrder: item.sortOrder, enabled: item.enabled }
  })
}

export interface AdminToolQuery {
  page: number
  pageSize: number
  keyword?: string
  categoryCode?: string
  status?: string
}

export function listAdminTools(query: AdminToolQuery): Promise<ApiResult<AdminToolPage>> {
  return apiRequest({ url: '/admin/tools', method: 'GET', params: query }, decodePage)
}

export function listAdminCategories(): Promise<ApiResult<AdminCategory[]>> {
  return apiRequest({ url: '/admin/tools/categories', method: 'GET' }, decodeCategories)
}

export function createAdminTool(payload: AdminToolPayload): Promise<ApiResult<AdminTool>> {
  return apiRequest({ url: '/admin/tools', method: 'POST', data: payload }, decodeTool)
}

export function updateAdminTool(code: string, payload: AdminToolPayload): Promise<ApiResult<AdminTool>> {
  return apiRequest({ url: `/admin/tools/${encodeURIComponent(code)}`, method: 'PUT', data: payload }, decodeTool)
}

export function updateAdminToolStatus(code: string, status: string): Promise<ApiResult<AdminTool>> {
  return apiRequest({ url: `/admin/tools/${encodeURIComponent(code)}/status`, method: 'PATCH', data: { status } }, decodeTool)
}

export function deleteAdminTool(code: string): Promise<ApiResult<null>> {
  return apiRequest({ url: `/admin/tools/${encodeURIComponent(code)}`, method: 'DELETE' }, () => null)
}
