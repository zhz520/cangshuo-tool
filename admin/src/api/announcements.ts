import { apiRequest } from './client'
import { isRecord, type ApiResult } from './types'

export const ANNOUNCEMENT_LEVELS = ['INFO', 'WARNING', 'CRITICAL'] as const

export interface AdminAnnouncement {
  id: number
  title: string
  body: string
  level: string
  published: boolean
  startAt: string | null
  endAt: string | null
  active: boolean
  createdAt: string
  updatedAt: string
}

export interface AdminAnnouncementPayload {
  title: string
  body: string
  level: string
  enabled: boolean
  startAt: string | null
  endAt: string | null
}

function decodeAnnouncement(value: unknown): AdminAnnouncement {
  if (!isRecord(value) || typeof value.id !== 'number' || !Number.isInteger(value.id) || value.id <= 0
    || typeof value.title !== 'string' || value.title.length < 1 || value.title.length > 128
    || typeof value.body !== 'string' || value.body.length < 1 || value.body.length > 2000
    || typeof value.level !== 'string' || !(ANNOUNCEMENT_LEVELS as readonly string[]).includes(value.level)
    || typeof value.published !== 'boolean' || typeof value.active !== 'boolean'
    || (value.startAt !== null && typeof value.startAt !== 'string')
    || (value.endAt !== null && typeof value.endAt !== 'string')
    || typeof value.createdAt !== 'string' || typeof value.updatedAt !== 'string') {
    throw new Error('invalid admin announcement')
  }
  return {
    id: value.id,
    title: value.title,
    body: value.body,
    level: value.level,
    published: value.published,
    startAt: value.startAt ?? null,
    endAt: value.endAt ?? null,
    active: value.active,
    createdAt: value.createdAt,
    updatedAt: value.updatedAt,
  }
}

function decodeList(value: unknown): AdminAnnouncement[] {
  if (!Array.isArray(value)) throw new Error('invalid admin announcement list')
  return value.map(decodeAnnouncement)
}

export function listAdminAnnouncements(): Promise<ApiResult<AdminAnnouncement[]>> {
  return apiRequest({ url: '/admin/announcements', method: 'GET' }, decodeList)
}

export function createAdminAnnouncement(payload: AdminAnnouncementPayload): Promise<ApiResult<AdminAnnouncement>> {
  return apiRequest({ url: '/admin/announcements', method: 'POST', data: payload }, decodeAnnouncement)
}

export function updateAdminAnnouncement(id: number, payload: AdminAnnouncementPayload): Promise<ApiResult<AdminAnnouncement>> {
  return apiRequest({ url: `/admin/announcements/${id}`, method: 'PUT', data: payload }, decodeAnnouncement)
}

export function updateAdminAnnouncementStatus(id: number, enabled: boolean): Promise<ApiResult<AdminAnnouncement>> {
  return apiRequest({ url: `/admin/announcements/${id}/status`, method: 'PATCH', data: { enabled } }, decodeAnnouncement)
}

export function deleteAdminAnnouncement(id: number): Promise<ApiResult<null>> {
  return apiRequest({ url: `/admin/announcements/${id}`, method: 'DELETE' }, () => null)
}
