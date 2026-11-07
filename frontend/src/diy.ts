import { categories, isProductIdentity, type ProductIdentity } from './catalog'

export const hardwareCategories = categories.filter(category => category.code !== 'ALL')
export type HardwareCategory = typeof hardwareCategories[number]['code']
export interface Selection { category: HardwareCategory; productId: number; source: 'DIY' }
export interface Workspace { version: 1; revision: number; selections: Selection[] }
export interface QuoteItem {
  category: HardwareCategory; productId: number; quantity: 1
  state: 'VALID' | 'NOT_FOUND' | 'CATEGORY_MISMATCH' | 'OFF_SALE' | 'OUT_OF_STOCK'
  message: string; product: ProductIdentity | null; amount: string | null
}
export interface Quote {
  revision: number; items: QuoteItem[]; total: string | null; validSubtotal: string; compatibility: 'NOT_CHECKED'
}
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
      || new Set(item.selections.map((selection: Selection) => selection.category)).size !== item.selections.length) {
      throw new Error('Invalid workspace')
    }
    // Restore references only. Client-saved prices, inventory or quote results are never trusted.
    return { workspace: { version: 1, revision: item.revision, selections: item.selections.map((selection: Selection) =>
      ({ category: selection.category, productId: selection.productId, source: selection.source })) }, warning: '' }
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
