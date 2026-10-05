import { apiRequest } from './client'
import { isRecord, type ApiResult } from './types'

export interface AdminProfile {
  id: number
  username: string
  nickname: string
  roleCode: string
}

export interface AdminSession {
  accessToken: string
  tokenType: string
  expiresIn: number
  expiresAt: string
  admin: AdminProfile
}

function decodeProfile(value: unknown): AdminProfile {
  if (!isRecord(value) || typeof value.id !== 'number' || !Number.isInteger(value.id) || value.id <= 0
    || typeof value.username !== 'string' || value.username.length < 1 || value.username.length > 64
    || typeof value.nickname !== 'string' || value.nickname.length > 64
    || typeof value.roleCode !== 'string' || !/^[A-Z][A-Z0-9_]{2,31}$/.test(value.roleCode)) {
    throw new Error('invalid admin profile')
  }
  return { id: value.id, username: value.username, nickname: value.nickname, roleCode: value.roleCode }
}

function decodeSession(value: unknown): AdminSession {
  if (!isRecord(value) || typeof value.accessToken !== 'string' || value.accessToken.length < 32
    || typeof value.tokenType !== 'string' || typeof value.expiresIn !== 'number'
    || !Number.isInteger(value.expiresIn) || typeof value.expiresAt !== 'string') {
    throw new Error('invalid admin session')
  }
  return {
    accessToken: value.accessToken,
    tokenType: value.tokenType,
    expiresIn: value.expiresIn,
    expiresAt: value.expiresAt,
    admin: decodeProfile(value.admin),
  }
}

export function loginAdmin(username: string, password: string): Promise<ApiResult<AdminSession>> {
  return apiRequest({ url: '/admin/auth/login', method: 'POST', data: { username, password } }, decodeSession)
}

export function fetchAdminMe(): Promise<ApiResult<AdminProfile>> {
  return apiRequest({ url: '/admin/auth/me', method: 'GET' }, decodeProfile)
}

export function logoutAdmin(): Promise<ApiResult<null>> {
  return apiRequest({ url: '/admin/auth/logout', method: 'POST' }, () => null)
}
