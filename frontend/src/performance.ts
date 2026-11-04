import { request, write } from './auth'

const scoreFormat = new Intl.NumberFormat('en-US', { minimumFractionDigits: 1, maximumFractionDigits: 1 })

export function formatPerformanceScore(score: string): string {
  return scoreFormat.format(Number(score))
}

export interface PerformanceTag {
  code: string; label: string; kind: 'SCORE' | 'CAPABILITY' | 'UNVERIFIED'; sourceUrl: string; note: string
}
export function isPerformanceTag(value: unknown): value is PerformanceTag {
  if (!value || typeof value !== 'object') return false
  const item = value as Record<string, unknown>
  return typeof item.code === 'string' && item.code !== '' && typeof item.label === 'string' && item.label !== ''
    && ['SCORE', 'CAPABILITY', 'UNVERIFIED'].includes(String(item.kind)) && typeof item.note === 'string'
    && typeof item.sourceUrl === 'string' && (item.kind === 'UNVERIFIED' || /^https?:\/\//.test(item.sourceUrl))
}

export interface PerformanceScore {
  state: 'VALID' | 'MISSING' | 'INVALID' | 'STALE'
  score: string | null
  performanceScore?: string
  mappingVersion: string
  reference: Record<string, unknown> | null
  note: string
}
export interface PerformanceView {
  productId: number; revision: number; reference: Record<string, unknown>; performance: PerformanceScore
}
export async function performanceReference(id: number) {
  return await request(`/api/admin/products/${id}/performance`) as PerformanceView
}
export async function savePerformance(id: number, input: unknown) {
  return await write(`/api/admin/products/${id}/performance`, input) as PerformanceView
}
