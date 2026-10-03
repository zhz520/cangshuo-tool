import { apiRequest } from './client'
import { isRecord } from './types'

export interface HealthData { status: 'UP' }

export function getHealth() {
  return apiRequest<HealthData>({ method: 'GET', url: '/health' }, value => {
    if (!isRecord(value) || value.status !== 'UP') throw new Error('Invalid health response')
    return { status: 'UP' }
  })
}
