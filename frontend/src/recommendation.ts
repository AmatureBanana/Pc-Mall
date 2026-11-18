import { AuthError, write } from './auth'
import { isProductIdentity } from './catalog'
import { isCompatibility, type QuoteItem, type Compatibility, type Workspace } from './diy'

export const recommendationScenarios = {
  OFFICE: { label: '办公', version: 'office-v1' },
  GAMING: { label: '游戏', version: 'gaming-v1' },
  CREATION: { label: '创作', version: 'creation-v1' },
} as const
export type Scenario = keyof typeof recommendationScenarios
export const scoreLabels = { cpu: 'CPU', gpu: '独显', memory: '内存', ssd: 'SSD' }
export const scoreComponents = Object.keys(scoreLabels) as (keyof typeof scoreLabels)[]

export interface RecommendationPlan {
  status: 'SUCCESS'; scenario: Scenario; scoringVersion: string; message: string
  budget: string; total: string; remaining: string; score: string
  scores: { cpu: string; gpu?: string; memory: string; ssd: string }
  weights: Record<typeof scoreComponents[number], number>
  items: QuoteItem[]; integratedGraphicsCpuId: number | null; memoryQuantity: number
  compatibility: Compatibility; reasons: string[]
}
const failureStatuses = ['NO_AVAILABLE_PRODUCT', 'INSUFFICIENT_DATA', 'BUDGET_INSUFFICIENT',
  'NO_COMPATIBLE_COMBINATION', 'NO_RESULT_FOUND', 'REVALIDATION_REQUIRED'] as const
export type Recommendation = RecommendationPlan | { status: typeof failureStatuses[number]; message: string }
const decimal = (value: unknown): value is string => typeof value === 'string' && /^\d+(\.\d+)?$/.test(value)
const score = (value: unknown): value is string => decimal(value) && Number(value) <= 100

export async function recommend(scenario: Scenario, budget: string, signal: AbortSignal): Promise<Recommendation> {
  let value: unknown
  try { value = await write('/api/recommendations', { scenario, budget }, 'POST', signal) }
  catch (cause) {
    if (signal.aborted || cause instanceof AuthError && ['INVALID_INPUT', 'SYSTEM_ERROR'].includes(cause.code ?? '')) throw cause
    throw new Error('推荐服务暂不可用，请稍后重试。')
  }
  if (!value || typeof value !== 'object') throw new Error('推荐结果格式异常，请重新推荐。')
  const result = value as Record<string, unknown>
  if (typeof result.message !== 'string') throw new Error('推荐结果缺少说明，请重新推荐。')
  if (result.scenario === scenario && failureStatuses.some(status => status === result.status)) return value as Recommendation
  const plan = value as RecommendationPlan
  const requiresGpu = scenario !== 'OFFICE'
  const weights = scoreComponents.map(key => plan.weights?.[key])
  if (plan.status !== 'SUCCESS' || plan.scenario !== scenario || plan.scoringVersion !== recommendationScenarios[scenario].version
    || ![plan.budget, plan.total, plan.remaining].every(decimal) || Number(plan.budget) !== Number(budget)
    || Number(plan.total) > Number(plan.budget)
    || ![plan.score, plan.scores?.cpu, plan.scores?.memory, plan.scores?.ssd].every(score)
    || (requiresGpu ? !score(plan.scores.gpu) : plan.scores.gpu !== undefined)
    || !weights.every(weight => Number.isInteger(weight) && weight >= 0)
    || weights.reduce((total, weight) => total + weight, 0) !== 100
    || (plan.weights.gpu > 0) !== requiresGpu
    || !Number.isInteger(plan.memoryQuantity) || plan.memoryQuantity < 1 || plan.memoryQuantity > 4
    || !Array.isArray(plan.items) || plan.items.length < 7 || plan.items.length > 8
    || !plan.items.every(item => item?.state === 'VALID' && isProductIdentity(item.product)
      && item.productId === item.product.id && item.category === item.product.category && decimal(item.amount)
      && 'status' in item.product && item.product.status === 'ON_SALE' && item.product.stock >= item.quantity
      && item.quantity === (item.category === 'MEMORY' ? plan.memoryQuantity : 1))
    || new Set(plan.items.map(item => item.category)).size !== plan.items.length
    || !['CPU', 'MOTHERBOARD', 'MEMORY', 'SSD', 'PSU', 'CASE', 'COOLER'].every(category => plan.items.some(item => item.category === category))
    || !Array.isArray(plan.reasons) || !plan.reasons.every(reason => typeof reason === 'string')) {
    throw new Error('推荐结果未通过完整性验证，请重新推荐。')
  }
  const cpu = plan.items.find(item => item.category === 'CPU')
  const gpu = plan.items.some(item => item.category === 'GPU')
  if (!cpu || (!gpu && (requiresGpu || plan.integratedGraphicsCpuId !== cpu.productId))
    || (gpu && plan.integratedGraphicsCpuId !== null)) {
    throw new Error('推荐显卡选择无效，请重新推荐。')
  }
  if (!isCompatibility(plan.compatibility, recommendedWorkspace(plan)) || !plan.compatibility.corePassed) {
    throw new Error('推荐方案的兼容检测未通过验证，请重新推荐。')
  }
  return plan
}

let pending: { plan: RecommendationPlan; addToCart: boolean } | null = null
export function queueRecommendation(plan: RecommendationPlan, addToCart: boolean) { pending = { plan, addToCart } }
export function takeRecommendation() { const handoff = pending; pending = null; return handoff }
export function recommendedWorkspace(plan: RecommendationPlan): Workspace {
  return { version: 1, revision: 0, origin: 'RECOMMENDATION',
    selections: plan.items.map(item => ({ category: item.category, productId: item.productId, source: 'RECOMMENDATION' })),
    ...(plan.memoryQuantity > 1 ? { memoryQuantity: plan.memoryQuantity } : {}),
    ...(plan.integratedGraphicsCpuId ? { integratedGraphicsCpuId: plan.integratedGraphicsCpuId } : {}) }
}
