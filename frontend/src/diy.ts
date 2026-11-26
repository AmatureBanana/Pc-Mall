import { categories, formatPrice, isProductIdentity, type ProductIdentity } from './catalog'

export const hardwareCategories = categories.filter(category => category.code !== 'ALL')
export type HardwareCategory = typeof hardwareCategories[number]['code']
export const hardwareDisplayOrder: readonly HardwareCategory[] = ['CPU', 'COOLER', 'MOTHERBOARD', 'MEMORY', 'GPU', 'SSD', 'PSU', 'CASE']
export function sortHardwareItems<T extends { category: string }>(items: readonly T[]): T[] {
  return [...items].sort((a, b) => hardwareDisplayOrder.findIndex(code => code === a.category)
    - hardwareDisplayOrder.findIndex(code => code === b.category))
}
export const hardwareLabels: Record<HardwareCategory, string> = {
  CPU: 'CPU', MOTHERBOARD: '主板', GPU: '显卡', MEMORY: '内存',
  SSD: '硬盘', PSU: '电源', CASE: '机箱', COOLER: 'CPU 散热器',
}
export interface Selection { category: HardwareCategory; productId: number; source: 'DIY' | 'RECOMMENDATION' }
export interface Workspace {
  version: 1; revision: number; selections: Selection[]; integratedGraphicsCpuId?: number; memoryQuantity?: number
  manuallyEdited?: true; origin?: 'RECOMMENDATION'
}
export const maxDiyMemoryQuantity = 4
export interface QuoteItem {
  category: HardwareCategory; productId: number; quantity: number
  state: 'VALID' | 'NOT_FOUND' | 'CATEGORY_MISMATCH' | 'OFF_SALE' | 'OUT_OF_STOCK'
  message: string; product: ProductIdentity | null; amount: string | null
}
export interface Quote {
  revision: number; items: QuoteItem[]; total: string | null; validSubtotal: string; compatibility: 'NOT_CHECKED'
  assessment?: DiyAssessment
}
const diyScenarios = ['BELOW_OFFICE', 'OFFICE', 'GAMING', 'CREATION'] as const
export interface DiyAssessment {
  score: string | null; weightedScore: string | null; scenario: typeof diyScenarios[number] | null
  bottlenecks: string[]; missing: string[]
}
export const isManualWorkspace = (workspace: Workspace) => workspace.manuallyEdited === true
  || workspace.selections.some(item => item.source === 'DIY')
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
  memorySlots: number | null
  checks: RuleCheck[]; missing: MissingPart[]; unexecutedRules: { rule: string; reason: string }[]
  unexecutedExtensions: { rule: string; reason: string }[]
}
const ruleStates: RuleState[] = ['PASS', 'FAIL', 'UNKNOWN', 'PENDING', 'NOT_APPLICABLE']
const missingStates = ['NOT_SELECTED', 'NOT_FOUND', 'OFF_SALE']
const itemStates: QuoteItem['state'][] = ['VALID', 'NOT_FOUND', 'CATEGORY_MISMATCH', 'OFF_SALE', 'OUT_OF_STOCK']
export const workspaceKey = 'pc-mall.diy.workspace.v1'
/**
 * A diagnostic-only record of what the user last saw. It is compared after sign-in to explain
 * changes; it is never read as a price, stock or compatibility answer.
 */
export const seenKey = 'pc-mall.diy.seen.v1'
export interface SeenItem {
  category: HardwareCategory; productId: number; state: QuoteItem['state']; amount: string | null; stock?: number
}
export interface SeenSnapshot {
  version: 1; revision: number; ruleVersion: string; catalogVersion: string; items: SeenItem[]
}
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
      || item.manuallyEdited !== undefined && item.manuallyEdited !== true
      || item.origin !== undefined && item.origin !== 'RECOMMENDATION'
      || !item.selections.every((selection: Selection) => selection && isCategory(selection.category)
        && Number.isSafeInteger(selection.productId) && selection.productId > 0 && ['DIY', 'RECOMMENDATION'].includes(selection.source))
      || item.memoryQuantity !== undefined && (!Number.isInteger(item.memoryQuantity) || item.memoryQuantity < 1
        || item.memoryQuantity > maxDiyMemoryQuantity
        || !item.selections.some((selection: Selection) => selection.category === 'MEMORY'))
      || item.integratedGraphicsCpuId !== undefined && (!Number.isSafeInteger(item.integratedGraphicsCpuId)
        || !item.selections.some((selection: Selection) => selection.category === 'CPU' && selection.productId === item.integratedGraphicsCpuId)
        || item.selections.some((selection: Selection) => selection.category === 'GPU'))
      || new Set(item.selections.map((selection: Selection) => selection.category)).size !== item.selections.length) {
      throw new Error('Invalid workspace')
    }
    // Restore references only. Client-saved prices, inventory or quote results are never trusted.
    return { workspace: { version: 1, revision: item.revision, selections: item.selections.map((selection: Selection) =>
      ({ category: selection.category, productId: selection.productId, source: selection.source })),
      ...(item.memoryQuantity > 1 ? { memoryQuantity: item.memoryQuantity } : {}),
      ...(item.origin === 'RECOMMENDATION' || item.selections.some((selection: Selection) => selection.source === 'RECOMMENDATION')
        ? { origin: 'RECOMMENDATION' as const } : {}),
      ...(item.manuallyEdited === true ? { manuallyEdited: true as const } : {}),
      ...(item.integratedGraphicsCpuId === undefined ? {} : { integratedGraphicsCpuId: item.integratedGraphicsCpuId }) }, warning: '' }
  } catch {
    return { workspace: emptyWorkspace(), warning: '本标签页的临时配置无法恢复，原存储未删除；重新选择后将保存新的配置。' }
  }
}

export function saveWorkspace(workspace: Workspace): string {
  try { sessionStorage.setItem(workspaceKey, JSON.stringify(workspace)); return '' }
  catch { return '浏览器暂时无法保存临时配置；当前选择仍可编辑，刷新后可能无法恢复。' }
}

/** Stores the diagnostic snapshot only; a storage failure never blocks editing or restoration. */
export function seenSnapshot(revision: number, items: QuoteItem[], check: Compatibility): SeenSnapshot {
  return {
    version: 1, revision, ruleVersion: check.ruleVersion, catalogVersion: check.catalogVersion,
    items: items.flatMap(item => item.product ? [{ category: item.category, productId: item.productId,
      state: item.state, amount: item.amount, stock: item.product.stock }] : []),
  }
}

export function rememberSeen(quote: Quote, check: Compatibility): void {
  try { sessionStorage.setItem(seenKey, JSON.stringify(seenSnapshot(quote.revision, quote.items, check))) }
  catch { /* Diagnostics only; the workspace itself already reports its own save state. */ }
}

/** Reads the diagnostic snapshot; a damaged or absent snapshot simply yields no comparison. */
export function readSeen(): SeenSnapshot | null {
  try {
    const raw = sessionStorage.getItem(seenKey)
    return parseSeen(raw)
  } catch { return null }
}

/** Shared validation for last-seen and the frozen sign-in diagnostic context. */
export function parseSeen(raw: string | null): SeenSnapshot | null {
  try {
    if (raw === null) return null
    const item = JSON.parse(raw)
    if (!item || item.version !== 1 || !Number.isSafeInteger(item.revision) || item.revision < 0
      || typeof item.ruleVersion !== 'string' || !item.ruleVersion.trim() || typeof item.catalogVersion !== 'string'
      || !/^[a-f0-9]{64}$/.test(item.catalogVersion)
      || !Array.isArray(item.items) || item.items.length > 8
      || !item.items.every((entry: SeenItem) => entry && isCategory(entry.category)
        && Number.isSafeInteger(entry.productId) && entry.productId > 0 && itemStates.includes(entry.state)
        && (entry.stock === undefined || Number.isSafeInteger(entry.stock) && entry.stock >= 0)
        && (entry.state === 'VALID' ? isMoney(entry.amount) : entry.amount === null))
      || new Set(item.items.map((entry: SeenItem) => entry.category)).size !== item.items.length) return null
    return item as SeenSnapshot
  } catch { return null }
}

export type ChangeKind = 'PRICE_CHANGED' | 'STOCK_CHANGED' | 'UNAVAILABLE' | 'AVAILABILITY_CHANGED'
export interface RestoredChange {
  kind: ChangeKind; category: HardwareCategory; productId: number; message: string
}
export interface Restoration {
  changes: RestoredChange[]
  /**
   * True when the compatibility fingerprint moved, i.e. a parameter or sale state changed somewhere.
   * The fingerprint is global, and a product revision also moves for price and stock edits, so this is
   * deliberately a workspace-level statement rather than a per-product one.
   */
  catalogChanged: boolean
  /** False when no comparable snapshot existed, so nothing can honestly be claimed. */
  comparable: boolean
}

/**
 * Explains what changed between the references the guest saw and the authoritative answer now.
 * Only the server's current quote and diagnosis are trusted; the snapshot merely names a difference.
 * A reference that is unavailable now is always reported, even when no snapshot can compare it.
 */
export function describeRestoration(seen: SeenSnapshot | null, quote: Quote, check: Compatibility): Restoration {
  const changes: RestoredChange[] = []
  // An empty workspace has nothing to restore, so nothing can honestly be claimed about it.
  if (quote.items.length === 0) return { changes, catalogChanged: false, comparable: false }
  const comparable = seen?.revision === quote.revision && seen.ruleVersion === check.ruleVersion
    && seen.items.length === quote.items.length
    && quote.items.every(item => seen.items.some(before => before.category === item.category && before.productId === item.productId))
  quote.items.forEach(item => {
    const label = hardwareLabels[item.category]
    if (item.state !== 'VALID') {
      changes.push({ kind: 'UNAVAILABLE', category: item.category, productId: item.productId,
        message: `${label}：${item.message}` })
      return
    }
    const before = comparable
      ? seen.items.find(entry => entry.category === item.category && entry.productId === item.productId) : undefined
    if (before && before.state !== item.state) {
      const states: Record<QuoteItem['state'], string> = {
        VALID: '可售', NOT_FOUND: '商品不存在', CATEGORY_MISMATCH: '类别不匹配',
        OFF_SALE: '已下架', OUT_OF_STOCK: '暂时缺货',
      }
      changes.push({ kind: 'AVAILABILITY_CHANGED', category: item.category, productId: item.productId,
        message: `${label}：商品状态由「${states[before.state]}」变为「${states[item.state]}」，已按当前库存与商品状态重验。` })
    }
    if (before?.state === 'VALID' && before.stock !== undefined && item.product && before.stock !== item.product.stock) {
      changes.push({ kind: 'STOCK_CHANGED', category: item.category, productId: item.productId,
        message: `${label}可售库存由 ${before.stock} 件变为 ${item.product.stock} 件` })
    }
    if (!before || before.amount === null || item.amount === null || before.amount === item.amount) return
    changes.push({ kind: 'PRICE_CHANGED', category: item.category, productId: item.productId,
      message: `${label}参考价由 ¥${formatPrice(before.amount)} 变为 ¥${formatPrice(item.amount)}` })
  })
  // The fingerprint covers the rule version, references, sale state and parameters but never the price,
  // and a mismatched rule version makes old and new fingerprints incomparable.
  const catalogChanged = comparable && seen.ruleVersion === check.ruleVersion
    && seen.catalogVersion !== check.catalogVersion
  return { changes, catalogChanged, comparable }
}

export async function loadQuote(workspace: Workspace, signal: AbortSignal, assessPerformance = false): Promise<Quote> {
  const query = new URLSearchParams({ revision: String(workspace.revision) })
  if (workspace.memoryQuantity !== undefined) query.set('memoryQuantity', String(workspace.memoryQuantity))
  workspace.selections.forEach(selection => query.set(selection.category, String(selection.productId)))
  if (assessPerformance && workspace.selections.length + Number(Boolean(workspace.integratedGraphicsCpuId)) === 8) {
    query.set('assessPerformance', '1')
    if (workspace.integratedGraphicsCpuId) query.set('integratedGraphicsCpuId', String(workspace.integratedGraphicsCpuId))
  }
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
      && item.quantity === (item.category === 'MEMORY' ? workspace.memoryQuantity ?? 1 : 1)
      && typeof item.message === 'string'
      && ['VALID', 'NOT_FOUND', 'CATEGORY_MISMATCH', 'OFF_SALE', 'OUT_OF_STOCK'].includes(item.state)
      && (item.state === 'NOT_FOUND' ? item.product === null : isProductIdentity(item.product) && item.product.id === item.productId)
      && (item.state === 'VALID' ? isMoney(item.amount) && item.product?.category === item.category : item.amount === null))
    || (quote.total === null) !== quote.items.some((item: QuoteItem) => item.state !== 'VALID')) {
    throw new Error('参考报价数据异常，已保留当前选择，请重试。')
  }
  return quote as Quote
}

export function isDiyAssessment(assessment: DiyAssessment | undefined): assessment is DiyAssessment {
  const isScore = (value: unknown): value is string => typeof value === 'string' && /^\d+(\.\d+)?$/.test(value) && Number(value) <= 100
  return !!assessment && Array.isArray(assessment.bottlenecks) && assessment.bottlenecks.every(item => typeof item === 'string')
    && Array.isArray(assessment.missing) && assessment.missing.every(item => typeof item === 'string')
    && (assessment.weightedScore === null || isScore(assessment.weightedScore))
    && (assessment.score === null ? assessment.scenario === null && assessment.missing.length > 0
      : isScore(assessment.score) && assessment.scenario !== null && diyScenarios.includes(assessment.scenario))
}

/**
 * Sends product references, the memory quantity and the configuration revision. The server re-reads every
 * parameter itself, so nothing here can turn into a client-claimed pass.
 */
export async function loadCompatibility(workspace: Workspace, signal: AbortSignal): Promise<Compatibility> {
  const body: Record<string, number> = { revision: workspace.revision }
  if (workspace.memoryQuantity !== undefined) body.memoryQuantity = workspace.memoryQuantity
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
  if (!isCompatibility(check, workspace)) throw new Error('兼容检测数据异常，已保留当前选择，请重试。')
  return check
}

export function isCompatibility(value: unknown, workspace: Workspace): value is Compatibility {
  const check = value as Compatibility | null
  const hasGpu = workspace.selections.some(item => item.category === 'GPU')
  return !(!check || check.revision !== workspace.revision || typeof check.corePassed !== 'boolean'
      || !(check.memorySlots === null || Number.isSafeInteger(check.memorySlots) && check.memorySlots > 0)
      || check.ruleVersion !== 'f03-core-tdp-power-v4'
      || typeof check.catalogVersion !== 'string' || !/^[a-f0-9]{64}$/.test(check.catalogVersion)
      || typeof check.checkedAt !== 'string' || !Number.isFinite(Date.parse(check.checkedAt))
      || !Array.isArray(check.checks) || check.checks.length !== 9
      || !check.checks.every((item: RuleCheck, index: number) => isRuleCheck(item)
        && item.rule === ['C01', 'C02', 'C03', 'C04', 'C05', 'C06', 'C08', 'C09', 'C10'][index]
        && (item.state !== 'NOT_APPLICABLE' || ['C06', 'C09'].includes(item.rule)
          && !hasGpu))
      || !Array.isArray(check.missing) || !check.missing.every(isMissingPart)
      || check.complete !== (check.missing.length === 0)
      || check.complete && !workspace.integratedGraphicsCpuId && !hasGpu
      || !Array.isArray(check.unexecutedRules) || check.unexecutedRules.length !== 0
      || !Array.isArray(check.unexecutedExtensions) || check.unexecutedExtensions.length !== 6
      || !check.unexecutedExtensions.every((item: { rule: string; reason: string }, index: number) => item
        && item.rule === `E0${index + 1}` && typeof item.reason === 'string' && item.reason !== '')
      || check.status !== (check.checks.some((item: RuleCheck) => item.state === 'FAIL') ? 'INCOMPATIBLE'
        : !check.complete ? 'INCOMPLETE'
        : check.checks.every((item: RuleCheck) => ['PASS', 'NOT_APPLICABLE'].includes(item.state)) ? 'CORE_PASSED' : 'UNVERIFIED')
      || check.corePassed !== (check.status === 'CORE_PASSED'))
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
