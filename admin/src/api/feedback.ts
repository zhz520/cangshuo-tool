import { apiRequest } from './client'
import { isRecord, type ApiResult } from './types'

export const FEEDBACK_STATUSES = ['PENDING', 'PROCESSING', 'RESOLVED'] as const

export interface AdminFeedback {
  id: number
  userId: number
  userEmail: string
  type: string
  content: string
  contact: string | null
  status: string
  reply: string | null
  repliedAt: string | null
  createdAt: string
  updatedAt: string
}

export interface AdminFeedbackPage {
  records: AdminFeedback[]
  page: number
  pageSize: number
  total: number
}

function decodeFeedback(value: unknown): AdminFeedback {
  if (!isRecord(value) || typeof value.id !== 'number' || !Number.isInteger(value.id) || value.id <= 0
    || typeof value.userId !== 'number' || typeof value.userEmail !== 'string'
    || typeof value.type !== 'string' || typeof value.content !== 'string'
    || (value.contact !== null && typeof value.contact !== 'string')
    || typeof value.status !== 'string' || !(FEEDBACK_STATUSES as readonly string[]).includes(value.status)
    || (value.reply !== null && typeof value.reply !== 'string')
    || (value.repliedAt !== null && typeof value.repliedAt !== 'string')
    || typeof value.createdAt !== 'string' || typeof value.updatedAt !== 'string') {
    throw new Error('invalid admin feedback')
  }
  return {
    id: value.id,
    userId: value.userId,
    userEmail: value.userEmail,
    type: value.type,
    content: value.content,
    contact: value.contact ?? null,
    status: value.status,
    reply: value.reply ?? null,
    repliedAt: value.repliedAt ?? null,
    createdAt: value.createdAt,
    updatedAt: value.updatedAt,
  }
}

function decodePage(value: unknown): AdminFeedbackPage {
  if (!isRecord(value) || !Array.isArray(value.records) || typeof value.page !== 'number'
    || typeof value.pageSize !== 'number' || typeof value.total !== 'number') {
    throw new Error('invalid admin feedback page')
  }
  return { records: value.records.map(decodeFeedback), page: value.page, pageSize: value.pageSize, total: value.total }
}

export function listAdminFeedback(query: { page: number; pageSize: number; keyword?: string; status?: string }): Promise<ApiResult<AdminFeedbackPage>> {
  return apiRequest({ url: '/admin/feedback', method: 'GET', params: query }, decodePage)
}

export function updateAdminFeedback(id: number, status: string, reply: string | null): Promise<ApiResult<AdminFeedback>> {
  return apiRequest({ url: `/admin/feedback/${id}`, method: 'PATCH', data: { status, reply } }, decodeFeedback)
}
