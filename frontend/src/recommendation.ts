import { write } from './auth'
import { isProductIdentity } from './catalog'
import type { QuoteItem, Compatibility, Workspace } from './diy'

export interface OfficePlan {
  status: 'SUCCESS'; scenario: 'OFFICE'; scoringVersion: 'office-v1'; message: string
  budget: string; total: string; remaining: string; score: string
  scores: { cpu: string; memory: string; ssd: string }
  items: QuoteItem[]; integratedGraphicsCpuId: number | null; memoryQuantity: number
  compatibility: Compatibility; reasons: string[]
}
export type Recommendation = OfficePlan | { status: 'NO_AVAILABLE_PRODUCT' | 'INSUFFICIENT_DATA' | 'NO_RESULT_FOUND' | 'REVALIDATION_REQUIRED'; message: string }
const decimal = (value: unknown): value is string => typeof value === 'string' && /^\d+(\.\d+)?$/.test(value)

export async function recommendOffice(budget: string, signal: AbortSignal): Promise<Recommendation> {
  let value: unknown
  try { value = await write('/api/recommendations', { scenario: 'OFFICE', budget }, 'POST', signal) }
  catch (cause) { if (signal.aborted) throw cause; throw new Error('推荐服务暂不可用，请稍后重试。') }
  if (!value || typeof value !== 'object') throw new Error('推荐结果格式异常，请重新推荐。')
  const result = value as Record<string, unknown>
  if (typeof result.message !== 'string') throw new Error('推荐结果缺少说明，请重新推荐。')
  if (['NO_AVAILABLE_PRODUCT', 'INSUFFICIENT_DATA', 'NO_RESULT_FOUND', 'REVALIDATION_REQUIRED'].includes(String(result.status))) return value as Recommendation
  const plan = value as OfficePlan
  if (plan.status !== 'SUCCESS' || plan.scenario !== 'OFFICE' || plan.scoringVersion !== 'office-v1'
    || ![plan.budget, plan.total, plan.remaining, plan.score, plan.scores?.cpu, plan.scores?.memory, plan.scores?.ssd].every(decimal)
    || Number(plan.total) > Number(plan.budget) || Number(plan.score) > 100
    || !plan.compatibility?.complete || !plan.compatibility.corePassed
    || typeof plan.compatibility.ruleVersion !== 'string' || !Array.isArray(plan.compatibility.unexecutedExtensions)
    || !Number.isInteger(plan.memoryQuantity) || plan.memoryQuantity < 1 || plan.memoryQuantity > 4
    || !Array.isArray(plan.items) || plan.items.length < 7 || plan.items.length > 8
    || !plan.items.every(item => item.state === 'VALID' && isProductIdentity(item.product)
      && item.productId === item.product.id && item.category === item.product.category && decimal(item.amount)
      && item.quantity === (item.category === 'MEMORY' ? plan.memoryQuantity : 1))
    || new Set(plan.items.map(item => item.category)).size !== plan.items.length
    || !['CPU', 'MOTHERBOARD', 'MEMORY', 'SSD', 'PSU', 'CASE', 'COOLER'].every(category => plan.items.some(item => item.category === category))
    || !Array.isArray(plan.reasons) || !plan.reasons.every(reason => typeof reason === 'string')) {
    throw new Error('推荐结果未通过完整性验证，请重新推荐。')
  }
  const cpu = plan.items.find(item => item.category === 'CPU')
  const gpu = plan.items.some(item => item.category === 'GPU')
  if (!cpu || (!gpu && plan.integratedGraphicsCpuId !== cpu.productId) || (gpu && plan.integratedGraphicsCpuId !== null)) {
    throw new Error('推荐显卡选择无效，请重新推荐。')
  }
  return plan
}

let pending: OfficePlan | null = null
export function queueRecommendation(plan: OfficePlan) { pending = plan }
export function takeRecommendation() { const plan = pending; pending = null; return plan }
export function recommendedWorkspace(plan: OfficePlan): Workspace {
  return { version: 1, revision: 0, selections: plan.items.map(item => ({ category: item.category, productId: item.productId, source: 'RECOMMENDATION' })),
    ...(plan.memoryQuantity > 1 ? { memoryQuantity: plan.memoryQuantity } : {}),
    ...(plan.integratedGraphicsCpuId ? { integratedGraphicsCpuId: plan.integratedGraphicsCpuId } : {}) }
}
