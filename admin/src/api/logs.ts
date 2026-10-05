import { apiRequest } from './client'
import { isRecord, type ApiResult } from './types'

export interface AdminOperationLog {
  id: number
  adminId: number | null
  adminUsername: string | null
  module: string
  operation: string
  requestUri: string
  requestMethod: string
  ip: string
  result: string
  createdAt: string
}

export interface AdminOperationLogPage {
  records: AdminOperationLog[]
  page: number
  pageSize: number
  total: number
}

function decodeLog(value: unknown): AdminOperationLog {
  if (!isRecord(value) || typeof value.id !== 'number' || !Number.isInteger(value.id) || value.id <= 0
    || (value.adminId !== null && typeof value.adminId !== 'number')
    || (value.adminUsername !== null && typeof value.adminUsername !== 'string')
    || typeof value.module !== 'string' || typeof value.operation !== 'string'
    || typeof value.requestUri !== 'string' || typeof value.requestMethod !== 'string'
    || typeof value.ip !== 'string' || typeof value.result !== 'string'
    || typeof value.createdAt !== 'string') {
    throw new Error('invalid admin operation log')
  }
  return {
    id: value.id,
    adminId: value.adminId ?? null,
    adminUsername: value.adminUsername ?? null,
    module: value.module,
    operation: value.operation,
    requestUri: value.requestUri,
    requestMethod: value.requestMethod,
    ip: value.ip,
    result: value.result,
    createdAt: value.createdAt,
  }
}

function decodePage(value: unknown): AdminOperationLogPage {
  if (!isRecord(value) || !Array.isArray(value.records) || typeof value.page !== 'number'
    || typeof value.pageSize !== 'number' || typeof value.total !== 'number') {
    throw new Error('invalid admin operation log page')
  }
  return { records: value.records.map(decodeLog), page: value.page, pageSize: value.pageSize, total: value.total }
}

export function listAdminOperationLogs(query: { page: number; pageSize: number; module?: string; result?: string; keyword?: string }): Promise<ApiResult<AdminOperationLogPage>> {
  return apiRequest({ url: '/admin/logs', method: 'GET', params: query }, decodePage)
}
