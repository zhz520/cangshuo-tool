import { apiRequest } from './client'
import { isRecord, type ApiResult } from './types'

export const SITE_LINK_STATUSES = ['PENDING', 'APPROVED', 'REJECTED'] as const

export interface AdminSiteLink {
  id: number
  title: string
  url: string
  description: string
  status: string
  submittedBy: number | null
  createdAt: string
  updatedAt: string
}

export interface AdminSiteLinkPage {
  records: AdminSiteLink[]
  page: number
  pageSize: number
  total: number
}

function decodeLink(value: unknown): AdminSiteLink {
  if (!isRecord(value) || typeof value.id !== 'number' || !Number.isInteger(value.id) || value.id <= 0
    || typeof value.title !== 'string' || typeof value.url !== 'string'
    || typeof value.description !== 'string' || typeof value.status !== 'string'
    || !(SITE_LINK_STATUSES as readonly string[]).includes(value.status)
    || (value.submittedBy !== null && typeof value.submittedBy !== 'number')
    || typeof value.createdAt !== 'string' || typeof value.updatedAt !== 'string') {
    throw new Error('invalid admin site link')
  }
  return {
    id: value.id,
    title: value.title,
    url: value.url,
    description: value.description,
    status: value.status,
    submittedBy: value.submittedBy ?? null,
    createdAt: value.createdAt,
    updatedAt: value.updatedAt,
  }
}

function decodePage(value: unknown): AdminSiteLinkPage {
  if (!isRecord(value) || !Array.isArray(value.records) || typeof value.page !== 'number'
    || typeof value.pageSize !== 'number' || typeof value.total !== 'number') {
    throw new Error('invalid admin site link page')
  }
  return { records: value.records.map(decodeLink), page: value.page, pageSize: value.pageSize, total: value.total }
}

export function listAdminSiteLinks(query: { page: number; pageSize: number; keyword?: string; status?: string }): Promise<ApiResult<AdminSiteLinkPage>> {
  return apiRequest({ url: '/admin/site-links', method: 'GET', params: query }, decodePage)
}

export function updateAdminSiteLink(id: number, status: string): Promise<ApiResult<AdminSiteLink>> {
  return apiRequest({ url: `/admin/site-links/${id}`, method: 'PATCH', data: { status } }, decodeLink)
}
