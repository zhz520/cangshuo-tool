import { apiRequest } from './client'
import { isRecord, type ApiResult } from './types'

export interface AdminRecommendation {
  id: number
  slotCode: string
  title: string
  subtitle: string
  toolCode: string | null
  linkUrl: string | null
  imageUrl: string | null
  sortOrder: number
  enabled: boolean
  startAt: string | null
  endAt: string | null
  active: boolean
  updatedAt: string
}

export interface AdminRecommendationPayload {
  slotCode?: string
  title: string
  subtitle: string
  toolCode: string | null
  linkUrl: string | null
  imageUrl: string | null
  sortOrder: number
  enabled: boolean
  startAt: string | null
  endAt: string | null
}

function decodeRecommendation(value: unknown): AdminRecommendation {
  if (!isRecord(value) || typeof value.id !== 'number' || !Number.isInteger(value.id) || value.id <= 0
    || typeof value.slotCode !== 'string' || !/^[a-z][a-z0-9_]{0,31}$/.test(value.slotCode)
    || typeof value.title !== 'string' || value.title.length < 1 || value.title.length > 64
    || typeof value.subtitle !== 'string' || value.subtitle.length > 128
    || (value.toolCode !== null && typeof value.toolCode !== 'string')
    || (value.linkUrl !== null && typeof value.linkUrl !== 'string')
    || (value.imageUrl !== null && typeof value.imageUrl !== 'string')
    || typeof value.sortOrder !== 'number' || !Number.isInteger(value.sortOrder)
    || typeof value.enabled !== 'boolean' || typeof value.active !== 'boolean'
    || (value.startAt !== null && typeof value.startAt !== 'string')
    || (value.endAt !== null && typeof value.endAt !== 'string')
    || typeof value.updatedAt !== 'string') {
    throw new Error('invalid admin recommendation')
  }
  return {
    id: value.id,
    slotCode: value.slotCode,
    title: value.title,
    subtitle: value.subtitle,
    toolCode: value.toolCode ?? null,
    linkUrl: value.linkUrl ?? null,
    imageUrl: value.imageUrl ?? null,
    sortOrder: value.sortOrder,
    enabled: value.enabled,
    startAt: value.startAt ?? null,
    endAt: value.endAt ?? null,
    active: value.active,
    updatedAt: value.updatedAt,
  }
}

function decodeList(value: unknown): AdminRecommendation[] {
  if (!Array.isArray(value)) throw new Error('invalid admin recommendation list')
  return value.map(decodeRecommendation)
}

export function listAdminRecommendations(): Promise<ApiResult<AdminRecommendation[]>> {
  return apiRequest({ url: '/admin/recommendations', method: 'GET' }, decodeList)
}

export function createAdminRecommendation(payload: AdminRecommendationPayload): Promise<ApiResult<AdminRecommendation>> {
  return apiRequest({ url: '/admin/recommendations', method: 'POST', data: payload }, decodeRecommendation)
}

export function updateAdminRecommendation(slotCode: string, payload: AdminRecommendationPayload): Promise<ApiResult<AdminRecommendation>> {
  return apiRequest({ url: `/admin/recommendations/${encodeURIComponent(slotCode)}`, method: 'PUT', data: payload }, decodeRecommendation)
}

export function updateAdminRecommendationStatus(slotCode: string, enabled: boolean): Promise<ApiResult<AdminRecommendation>> {
  return apiRequest({ url: `/admin/recommendations/${encodeURIComponent(slotCode)}/status`, method: 'PATCH', data: { enabled } }, decodeRecommendation)
}

export function deleteAdminRecommendation(slotCode: string): Promise<ApiResult<null>> {
  return apiRequest({ url: `/admin/recommendations/${encodeURIComponent(slotCode)}`, method: 'DELETE' }, () => null)
}
