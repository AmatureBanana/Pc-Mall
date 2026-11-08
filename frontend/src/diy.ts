import { categories, isProductIdentity, type ProductIdentity } from './catalog'

export const hardwareCategories = categories.filter(category => category.code !== 'ALL')
export type HardwareCategory = typeof hardwareCategories[number]['code']
export interface Selection { category: HardwareCategory; productId: number; source: 'DIY' }
export interface Workspace { version: 1; revision: number; selections: Selection[]; integratedGraphicsCpuId?: number }
export interface QuoteItem {
  category: HardwareCategory; productId: number; quantity: 1
  state: 'VALID' | 'NOT_FOUND' | 'CATEGORY_MISMATCH' | 'OFF_SALE' | 'OUT_OF_STOCK'
  message: string; product: ProductIdentity | null; amount: string | null
}
export interface Quote {
  revision: number; items: QuoteItem[]; total: string | null; validSubtotal: string; compatibility: 'NOT_CHECKED'
}
export type RuleState = 'PASS' | 'FAIL' | 'UNKNOWN' | 'PENDING' | 'NOT_APPLICABLE'
export interface RuleDetail { category: HardwareCategory; parameter: string; value: string | null }
export interface RuleCheck {
  rule: string; state: RuleState; categories: HardwareCategory[]; parameters: string[]
  message: string; details: RuleDetail[]
}
export interface MissingPart { category: HardwareCategory; label: string; state: 'NOT_SELECTED' | 'NOT_FOUND' | 'OFF_SALE'; message: string }
export interface Compatibility {
  revision: number; ruleVersion: string; checkedAt: string; catalogVersion: string
  status: 'CORE_PASSED' | 'INCOMPATIBLE' | 'INCOMPLETE' | 'UNVERIFIED' | 'ERROR'
  complete: boolean; corePassed: boolean
  checks: RuleCheck[]; missing: MissingPart[]; unexecutedRules: { rule: string; reason: string }[]
  unexecutedExtensions: { rule: string; reason: string }[]
}
const ruleStates: RuleState[] = ['PASS', 'FAIL', 'UNKNOWN', 'PENDING', 'NOT_APPLICABLE']
const missingStates = ['NOT_SELECTED', 'NOT_FOUND', 'OFF_SALE']
export const workspaceKey = 'pc-mall.diy.workspace.v1'
export const emptyWorkspace = (): Workspace => ({ version: 1, revision: 0, selections: [] })
const isCategory = (value: unknown): value is HardwareCategory => hardwareCategories.some(category => category.code === value)
const isMoney = (value: unknown): value is string => typeof value === 'string' && /^\d+\.\d{2}$/.test(value)

export function restoreWorkspace(): { workspace: Workspace; warning: string } {
  try {
    const raw = sessionStorage.getItem(workspaceKey)
    if (raw === null) return { workspace: emptyWorkspace(), warning: '' }
    const item = JSON.parse(raw)
    if (!item || item.version !== 1 || !Number.isSafeInteger(item.revision) || item.revision < 0
      || item.revision >= Number.MAX_SAFE_INTEGER || !Array.isArray(item.selections) || item.selections.length > 8
      || !item.selections.every((selection: Selection) => selection && isCategory(selection.category)
        && Number.isSafeInteger(selection.productId) && selection.productId > 0 && selection.source === 'DIY')
      || item.integratedGraphicsCpuId !== undefined && (!Number.isSafeInteger(item.integratedGraphicsCpuId)
        || !item.selections.some((selection: Selection) => selection.category === 'CPU' && selection.productId === item.integratedGraphicsCpuId)
        || item.selections.some((selection: Selection) => selection.category === 'GPU'))
      || new Set(item.selections.map((selection: Selection) => selection.category)).size !== item.selections.length) {
      throw new Error('Invalid workspace')
    }
    // Restore references only. Client-saved prices, inventory or quote results are never trusted.
    return { workspace: { version: 1, revision: item.revision, selections: item.selections.map((selection: Selection) =>
      ({ category: selection.category, productId: selection.productId, source: selection.source })),
      ...(item.integratedGraphicsCpuId === undefined ? {} : { integratedGraphicsCpuId: item.integratedGraphicsCpuId }) }, warning: '' }
  } catch {
    return { workspace: emptyWorkspace(), warning: '本标签页的临时配置无法恢复，原存储未删除；重新选择后将保存新的配置。' }
  }
}

export function saveWorkspace(workspace: Workspace): string {
  try { sessionStorage.setItem(workspaceKey, JSON.stringify(workspace)); return '' }
  catch { return '浏览器暂时无法保存临时配置；当前选择仍可编辑，刷新后可能无法恢复。' }
}

export async function loadQuote(workspace: Workspace, signal: AbortSignal): Promise<Quote> {
  const query = new URLSearchParams({ revision: String(workspace.revision) })
  workspace.selections.forEach(selection => query.set(selection.category, String(selection.productId)))
  const response = await fetch(`/api/diy/quote?${query}`, { signal, cache: 'no-store', headers: { Accept: 'application/json' } })
  if (!response.ok) throw new Error('参考报价暂时不可用，已保留当前选择，请重试。')
  const body = await response.json()
  const quote = body?.data
  if (!quote || quote.revision !== workspace.revision || quote.compatibility !== 'NOT_CHECKED'
    || !(quote.total === null || isMoney(quote.total)) || !isMoney(quote.validSubtotal)
    || !Array.isArray(quote.items) || quote.items.length !== workspace.selections.length
    || new Set(quote.items.map((item: QuoteItem) => item?.category)).size !== quote.items.length
    || !quote.items.every((item: QuoteItem) => item && workspace.selections.some(selection =>
      selection.category === item.category && selection.productId === item.productId)
      && item.quantity === 1 && typeof item.message === 'string'
      && ['VALID', 'NOT_FOUND', 'CATEGORY_MISMATCH', 'OFF_SALE', 'OUT_OF_STOCK'].includes(item.state)
      && (item.state === 'NOT_FOUND' ? item.product === null : isProductIdentity(item.product) && item.product.id === item.productId)
      && (item.state === 'VALID' ? isMoney(item.amount) && item.product?.category === item.category : item.amount === null))
    || (quote.total === null) !== quote.items.some((item: QuoteItem) => item.state !== 'VALID')) {
    throw new Error('参考报价数据异常，已保留当前选择，请重试。')
  }
  return quote as Quote
}

/**
 * Sends only product references and the configuration revision. The server re-reads every
 * parameter itself, so nothing here can turn into a client-claimed pass.
 */
export async function loadCompatibility(workspace: Workspace, signal: AbortSignal): Promise<Compatibility> {
  const body: Record<string, number> = { revision: workspace.revision }
  if (workspace.integratedGraphicsCpuId) body.integratedGraphicsCpuId = workspace.integratedGraphicsCpuId
  workspace.selections.forEach(selection => { body[selection.category] = selection.productId })
  const csrf = await fetch('/api/auth/csrf', { signal, cache: 'no-store', headers: { Accept: 'application/json' } })
  if (!csrf.ok) throw new Error('兼容检测暂时不可用，已保留当前选择，请重试。')
  const token = (await csrf.json())?.data
  if (!token || typeof token.token !== 'string' || typeof token.headerName !== 'string') {
    throw new Error('页面安全凭证获取失败，请刷新后重试。')
  }
  const response = await fetch('/api/compatibility/check', { signal, cache: 'no-store', method: 'POST',
    headers: { 'Content-Type': 'application/json', Accept: 'application/json', [token.headerName]: token.token },
    body: JSON.stringify(body) })
  if (!response.ok) throw new Error('兼容检测暂时不可用，已保留当前选择，请重试。')
  const check = (await response.json())?.data
  if (!check || check.revision !== workspace.revision || typeof check.corePassed !== 'boolean'
      || check.ruleVersion !== 'f03-core-graphics-choice-v3'
      || typeof check.catalogVersion !== 'string' || !/^[a-f0-9]{64}$/.test(check.catalogVersion)
      || typeof check.checkedAt !== 'string' || !Number.isFinite(Date.parse(check.checkedAt))
      || !Array.isArray(check.checks) || check.checks.length !== 9
      || !check.checks.every((item: RuleCheck, index: number) => isRuleCheck(item)
        && item.rule === ['C01', 'C02', 'C03', 'C04', 'C05', 'C06', 'C08', 'C09', 'C10'][index]
        && (item.state !== 'NOT_APPLICABLE' || ['C06', 'C09'].includes(item.rule)
          && !workspace.selections.some(selection => selection.category === 'GPU')))
      || !Array.isArray(check.missing) || !check.missing.every(isMissingPart)
      || check.complete !== (check.missing.length === 0)
      || check.complete && !workspace.integratedGraphicsCpuId && !workspace.selections.some(item => item.category === 'GPU')
      || !Array.isArray(check.unexecutedRules) || check.unexecutedRules.length !== 0
      || !Array.isArray(check.unexecutedExtensions) || check.unexecutedExtensions.length !== 6
      || !check.unexecutedExtensions.every((item: { rule: string; reason: string }, index: number) => item
        && item.rule === `E0${index + 1}` && typeof item.reason === 'string' && item.reason !== '')
      || check.status !== (check.checks.some((item: RuleCheck) => item.state === 'FAIL') ? 'INCOMPATIBLE'
        : !check.complete ? 'INCOMPLETE'
        : check.checks.every((item: RuleCheck) => ['PASS', 'NOT_APPLICABLE'].includes(item.state)) ? 'CORE_PASSED' : 'UNVERIFIED')
      || check.corePassed !== (check.status === 'CORE_PASSED')) {
    throw new Error('兼容检测数据异常，已保留当前选择，请重试。')
  }
  return check as Compatibility
}

function isRuleCheck(item: RuleCheck): boolean {
  return Boolean(item) && /^C(0[1-9]|10)$/.test(item.rule) && ruleStates.includes(item.state)
    && Array.isArray(item.categories) && item.categories.length > 0
    && item.categories.every(category => hardwareCategories.some(entry => entry.code === category))
    && Array.isArray(item.parameters) && item.parameters.length > 0
    && item.parameters.every(parameter => typeof parameter === 'string')
    && typeof item.message === 'string' && item.message !== ''
    && Array.isArray(item.details) && item.details.every(detail => detail
      && hardwareCategories.some(entry => entry.code === detail.category)
      && item.categories.includes(detail.category)
      && item.parameters.includes(detail.parameter)
      && (detail.value === null || typeof detail.value === 'string'))
}

function isMissingPart(item: MissingPart): boolean {
  return Boolean(item) && hardwareCategories.some(entry => entry.code === item.category)
    && typeof item.label === 'string' && item.label !== ''
    && missingStates.includes(item.state) && typeof item.message === 'string' && item.message !== ''
}
