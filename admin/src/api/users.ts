import { apiRequest } from './client'
import { isRecord, type ApiResult } from './types'

export interface AdminUser {
  id: number
  email: string
  nickname: string
  enabled: boolean
  lastLoginAt: string | null
  createdAt: string
  updatedAt: string
  favoriteCount: number
  recentCount: number
}

export interface AdminUserPage {
  records: AdminUser[]
  page: number
  pageSize: number
  total: number
}

export interface AdminUserSession {
  sessionId: number
  sessionCodeMasked: string
  createdAt: string
  expiresAt: string
  revokedAt: string | null
  lastActiveAt: string
}

export interface AdminUserSyncEntry {
  entityType: string
  entityKey: string
  updatedAtMs: number
  deleted: boolean
}

function decodeUser(value: unknown): AdminUser {
  if (!isRecord(value) || typeof value.id !== 'number' || !Number.isInteger(value.id) || value.id <= 0
    || typeof value.email !== 'string' || value.email.length < 3 || value.email.length > 128
    || typeof value.nickname !== 'string' || value.nickname.length > 64
    || typeof value.enabled !== 'boolean'
    || (value.lastLoginAt !== null && typeof value.lastLoginAt !== 'string')
    || typeof value.createdAt !== 'string' || typeof value.updatedAt !== 'string'
    || typeof value.favoriteCount !== 'number' || typeof value.recentCount !== 'number') {
    throw new Error('invalid admin user')
  }
  return {
    id: value.id,
    email: value.email,
    nickname: value.nickname,
    enabled: value.enabled,
    lastLoginAt: value.lastLoginAt ?? null,
    createdAt: value.createdAt,
    updatedAt: value.updatedAt,
    favoriteCount: value.favoriteCount,
    recentCount: value.recentCount,
  }
}

function decodePage(value: unknown): AdminUserPage {
  if (!isRecord(value) || !Array.isArray(value.records) || typeof value.page !== 'number'
    || typeof value.pageSize !== 'number' || typeof value.total !== 'number') {
    throw new Error('invalid admin user page')
  }
  return { records: value.records.map(decodeUser), page: value.page, pageSize: value.pageSize, total: value.total }
}

function decodeSessions(value: unknown): AdminUserSession[] {
  if (!Array.isArray(value)) throw new Error('invalid admin user sessions')
  return value.map(item => {
    if (!isRecord(item) || typeof item.sessionId !== 'number' || typeof item.sessionCodeMasked !== 'string'
      || typeof item.createdAt !== 'string' || typeof item.expiresAt !== 'string'
      || (item.revokedAt !== null && typeof item.revokedAt !== 'string')
      || typeof item.lastActiveAt !== 'string') {
      throw new Error('invalid admin user session')
    }
    return { sessionId: item.sessionId, sessionCodeMasked: item.sessionCodeMasked, createdAt: item.createdAt,
      expiresAt: item.expiresAt, revokedAt: item.revokedAt ?? null, lastActiveAt: item.lastActiveAt }
  })
}

function decodeSyncEntries(value: unknown): AdminUserSyncEntry[] {
  if (!Array.isArray(value)) throw new Error('invalid admin user sync entries')
  return value.map(item => {
    if (!isRecord(item) || typeof item.entityType !== 'string' || typeof item.entityKey !== 'string'
      || typeof item.updatedAtMs !== 'number' || typeof item.deleted !== 'boolean') {
      throw new Error('invalid admin user sync entry')
    }
    return { entityType: item.entityType, entityKey: item.entityKey, updatedAtMs: item.updatedAtMs, deleted: item.deleted }
  })
}

export function listAdminUsers(query: { page: number; pageSize: number; keyword?: string; enabled?: boolean }): Promise<ApiResult<AdminUserPage>> {
  return apiRequest({ url: '/admin/users', method: 'GET', params: query }, decodePage)
}

export function getAdminUser(id: number): Promise<ApiResult<AdminUser>> {
  return apiRequest({ url: `/admin/users/${id}`, method: 'GET' }, decodeUser)
}

export function updateAdminUserStatus(id: number, enabled: boolean): Promise<ApiResult<AdminUser>> {
  return apiRequest({ url: `/admin/users/${id}/status`, method: 'PATCH', data: { enabled } }, decodeUser)
}

export function listAdminUserSessions(id: number): Promise<ApiResult<AdminUserSession[]>> {
  return apiRequest({ url: `/admin/users/${id}/sessions`, method: 'GET' }, decodeSessions)
}

export function listAdminUserSync(id: number, limit = 20): Promise<ApiResult<AdminUserSyncEntry[]>> {
  return apiRequest({ url: `/admin/users/${id}/sync`, method: 'GET', params: { limit } }, decodeSyncEntries)
}
