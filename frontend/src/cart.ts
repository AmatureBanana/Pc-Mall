import { computed, ref } from 'vue'
import { request, write, AuthError } from './auth'
import { hardwareCategories, maxDiyMemoryQuantity, saveWorkspace, type HardwareCategory, type Workspace } from './diy'

export type CartState = 'VALID' | 'OFF_SALE' | 'OUT_OF_STOCK' | 'INSUFFICIENT_STOCK'
export interface CartItem {
  id: number; productId: number; sku: string; name: string; category: string; categoryLabel: string
  brand: string; model: string; imageUrl: string; quantity: number; price: string; stock: number
  state: CartState; message: string; amount: string
}
export interface Cart {
  revision: number; items: CartItem[]; total: string; validTotal: string
  validCount: number; lineCount: number
  configurations: { id: number; items: CartItem[]; amount: string; valid: boolean; editToken: string; state: string; reasons: string[] }[]
}
export const cartStates: CartState[] = ['VALID', 'OFF_SALE', 'OUT_OF_STOCK', 'INSUFFICIENT_STOCK']
export interface CheckoutPreview {
  revision: number; status: 'CONFIRMATION_REQUIRED' | 'RECONFIRMATION_REQUIRED' | 'READY'; quoteToken: string
  items: CartItem[]; configurations: Cart['configurations']; total: string
  skuDemand: { productId: number; quantity: number }[]
}
export interface Order {
  orderNumber: string; status: 'PENDING_PAYMENT' | 'PAID' | 'CANCELLED' | 'COMPLETED'; currency: 'CNY'
  total: string; createdAt: string; paymentDeadline: string
  items: { productId: number; sku: string; name: string; model: string; category: string; price: string
    quantity: number; amount: string; configurationId: number | null }[]
  configurations: { id: number; amount: string }[]
}
/**
 * The per-line cart quantity bound, mirroring the server rule and the V19 check constraint.
 * It bounds a single-item line, independently of aggregate stock demand.
 */
export const cartQuantity = { min: 1, max: 99 } as const
const isMoney = (value: unknown): value is string => typeof value === 'string' && /^\d+\.\d{2}$/.test(value)

function isItem(value: unknown): value is CartItem {
  if (!value || typeof value !== 'object') return false
  const item = value as Record<string, unknown>
  return ['sku', 'name', 'category', 'categoryLabel', 'brand', 'model', 'imageUrl', 'message'].every(key =>
    typeof item[key] === 'string' && (item[key] as string) !== '')
    && Number.isSafeInteger(item.id) && Number(item.id) > 0
    && Number.isSafeInteger(item.productId) && Number(item.productId) > 0
    && Number.isSafeInteger(item.quantity) && Number(item.quantity) >= cartQuantity.min && Number(item.quantity) <= cartQuantity.max
    && Number.isSafeInteger(item.stock) && Number(item.stock) >= 0
    && isMoney(item.price) && isMoney(item.amount)
    && cartStates.includes(item.state as CartState)
}

function validConfigurationMembers(items: { category: string; productId: number; quantity: number }[]): boolean {
  const categories = new Set(items.map(item => item.category))
  return items.length >= 7 && items.length <= 8 && categories.size === items.length
    && new Set(items.map(item => item.productId)).size === items.length
    && hardwareCategories.every(({ code }) => code === 'GPU' || categories.has(code))
    && items.every(item => hardwareCategories.some(({ code }) => code === item.category)
      && (item.category === 'MEMORY' ? item.quantity <= maxDiyMemoryQuantity : item.quantity === 1))
}

function isConfiguration(value: unknown): value is Cart['configurations'][number] {
  if (!value || typeof value !== 'object') return false
  const group = value as Record<string, unknown>
  if (!Number.isSafeInteger(group.id) || Number(group.id) <= 0 || !Array.isArray(group.items)
      || !group.items.every(isItem)
      || !isMoney(group.amount) || typeof group.valid !== 'boolean'
      || typeof group.editToken !== 'string' || !/^[a-f0-9]{64}$/.test(group.editToken)
      || ![...cartStates, 'INCOMPATIBLE', 'UNVERIFIED'].includes(group.state as CartState)
      || !Array.isArray(group.reasons) || !group.reasons.every(reason => typeof reason === 'string')) return false
  return validConfigurationMembers(group.items)
}

function isCart(value: unknown): value is Cart {
  if (!value || typeof value !== 'object') return false
  const cart = value as Record<string, unknown>
  return Number.isSafeInteger(cart.revision) && Number(cart.revision) >= 0
    && Array.isArray(cart.items) && cart.items.every(isItem)
    && Array.isArray(cart.configurations) && cart.configurations.every(isConfiguration)
    && isMoney(cart.total) && isMoney(cart.validTotal)
    && Number.isSafeInteger(cart.validCount) && Number.isSafeInteger(cart.lineCount)
}

/** Rejects a malformed cart instead of rendering a partial amount as if it were the real total. */
function parse(value: unknown): Cart {
  if (!isCart(value)) throw new Error('购物车数据格式异常，请稍后重试。')
  return value
}

/** Identifies one intended add, including retries after an unknown transport outcome. */
export function requestKey(): string {
  return `cart-${Array.from(crypto.getRandomValues(new Uint8Array(16)), value => value.toString(16).padStart(2, '0')).join('')}`
}

export async function loadCart(signal?: AbortSignal): Promise<Cart> {
  return parse(await request('/api/cart', { signal }))
}

export async function addToCart(productId: number, quantity = 1, key = requestKey(), signal?: AbortSignal): Promise<Cart> {
  return parse(await write('/api/cart/items', { productId, quantity, requestKey: key }, 'POST', signal))
}

export async function setCartQuantity(itemId: number, quantity: number, signal?: AbortSignal): Promise<Cart> {
  return parse(await write(`/api/cart/items/${itemId}/quantity`, { quantity }, 'POST', signal))
}

/** DELETE carries the same CSRF token as a write; the shared request helper supplies it. */
export async function removeCartItem(itemId: number, signal?: AbortSignal): Promise<Cart> {
  return parse(await write(`/api/cart/items/${itemId}`, undefined, 'DELETE', signal))
}

/**
 * One shared cart view for the header badge, the detail dialog and the cart dialog, so every
 * surface shows the same server answer instead of a locally adjusted guess.
 */
const cart = ref<Cart | null>(null)
const loading = ref(false)
const error = ref('')
const notice = ref('')
type OrderInput = { revision: number; itemIds: number[]; configurationIds: number[]; quoteToken: string; requestKey: string }
const orderAttempt = ref<{ input: OrderInput; uncertain: boolean } | null>(null)
const orderBusy = ref(false)
const order = ref<Order | null>(null)
const editing = ref<{ id: number; revision: number; editToken: string; workspace: Workspace; previousWorkspace?: Workspace } | null>(null)
type Scope = { identity: number; order: number; signal: AbortSignal }
type AddAttempt = { key: string; inflight: Promise<boolean> | null }
let identity = 0
let sequence = 0
let acceptedOrder = 0
let latestAction = 0
let controller = new AbortController()
let inflight: { scope: Scope; promise: Promise<void> } | null = null
const uncertainAdds = new Map<string, AddAttempt>()

function scope(): Scope { return { identity, order: ++sequence, signal: controller.signal } }
function current(request: Scope) { return request.identity === identity && !request.signal.aborted }

/** Forget both the view and work belonging to the previous identity, including CSRF waits. */
function clear() {
  identity++
  controller.abort()
  controller = new AbortController()
  inflight = null
  uncertainAdds.clear()
  orderAttempt.value = null
  orderBusy.value = false
  order.value = null
  finishEdit()
  acceptedOrder = latestAction = 0
  cart.value = null
  loading.value = false
  error.value = notice.value = ''
}

/** Revisions order committed writes; request order breaks ties for current price/stock reads. */
function accept(request: Scope, value: Cart): boolean {
  if (!current(request)) return false
  if (cart.value && (value.revision < cart.value.revision
      || (value.revision === cart.value.revision && request.order < acceptedOrder))) return false
  cart.value = value
  acceptedOrder = request.order
  return true
}

function success(request: Scope, value: Cart, message = '') {
  if (!current(request)) return
  accept(request, value)
  if (request.order === latestAction) { error.value = ''; notice.value = message }
}

/** Old responses must not replace either another account's data or a later action's feedback. */
function failure(request: Scope, cause: unknown, fallback: string) {
  if (!current(request) || request.order !== latestAction) return
  if (cause instanceof AuthError && cause.status === 401) {
    clear()
    error.value = '登录已过期或尚未登录，请重新登录后使用购物车。'
  } else error.value = cause instanceof Error ? cause.message : fallback
}

async function refreshCart(force = false): Promise<void> {
  if (!force && inflight && current(inflight.scope)) return inflight.promise
  const request = scope()
  const pending = { scope: request, promise: Promise.resolve() }
  inflight = pending
  loading.value = true
  pending.promise = (async () => {
    try {
      const value = await loadCart(request.signal)
      if (accept(request, value) && request.order >= latestAction) error.value = ''
    } catch (cause) {
      if (!current(request) || request.order < latestAction) return
      // A signed-out visitor has no cart at all; that is not a failure worth showing.
      if (cause instanceof AuthError && cause.status === 401) clear()
      else error.value = cause instanceof Error ? cause.message : '购物车暂时不可用，请重试。'
    } finally {
      // An earlier refresh must not release a newer identity's (or forced refresh's) busy state.
      if (inflight === pending) { loading.value = false; inflight = null }
    }
  })()
  return pending.promise
}

function beginAction() {
  const request = scope()
  latestAction = request.order
  error.value = notice.value = ''
  return request
}

async function add(productId: number, quantity = 1): Promise<boolean> {
  // An uncertain outcome is retried only by another user action, with exactly the same content.
  // Identity changes clear this map; differing products/quantities never borrow the same key.
  const binding = `${productId}:${quantity}`
  const previous = uncertainAdds.get(binding)
  if (previous?.inflight) return previous.inflight
  const attempt = previous ?? { key: requestKey(), inflight: null }
  const retry = previous !== undefined
  uncertainAdds.set(binding, attempt)
  const request = beginAction()
  attempt.inflight = (async () => {
    try {
      const value = await addToCart(productId, quantity, attempt.key, request.signal)
      if (!current(request)) return false
      uncertainAdds.delete(binding)
      // Idempotency returns the first snapshot. A fresh independent read reconciles subsequent
      // changes, even if a pre-add refresh is still outstanding and would otherwise be reused.
      if (retry) {
        await refreshCart(true)
        if (current(request) && request.order === latestAction) {
          notice.value = '已加入购物车。'
          if (error.value) error.value = '已加入购物车，但最新购物车刷新失败，请刷新价格与库存。'
        }
      } else success(request, value, '已加入购物车。')
      return current(request)
    } catch (cause) {
      if (!current(request)) return false
      // Only a refused first attempt proves this operation did not commit. Refusing a retry
      // (for example stale CSRF) says nothing about an earlier unknown result, so retain its key.
      const refused = cause instanceof AuthError && cause.status >= 400 && cause.status < 500
      if (refused && !retry) uncertainAdds.delete(binding)
      const feedback = refused && (!retry || cause.status === 401) ? cause : new Error(refused
        ? `${cause.message}首次加购结果仍未确认，请再次点击加入购物车重试同一次操作。`
        : '加入购物车结果暂未确认，请再次点击加入购物车重试同一次操作。')
      failure(request, feedback, '加入购物车失败，请重试。')
      return false
    } finally { attempt.inflight = null }
  })()
  return attempt.inflight
}

async function update(itemId: number, quantity: number): Promise<boolean> {
  const request = beginAction()
  try {
    const value = await setCartQuantity(itemId, quantity, request.signal)
    if (!current(request)) return false
    success(request, value)
    return true
  } catch (cause) { failure(request, cause, '数量修改失败，请重试。'); return false }
}

async function remove(itemId: number): Promise<boolean> {
  const request = beginAction()
  try {
    const value = await removeCartItem(itemId, request.signal)
    if (!current(request)) return false
    success(request, value, '已从购物车移除。')
    return true
  } catch (cause) { failure(request, cause, '移除失败，请重试。'); return false }
}

async function addConfiguration(workspace: Workspace): Promise<boolean> {
  const request = beginAction()
  try {
    const value = parse(await write('/api/cart/configurations', {
      selections: workspace.selections.map(({ category, productId }) => ({ category, productId })),
      integratedGraphicsCpuId: workspace.integratedGraphicsCpuId,
      memoryQuantity: workspace.memoryQuantity,
    }, 'POST', request.signal))
    if (!current(request)) return false
    success(request, value, '装机方案已加入购物车。')
    return true
  } catch (cause) {
    const feedback = cause instanceof AuthError ? cause
      : new Error('方案入车结果暂未确认，请重试或打开购物车核对。')
    failure(request, feedback, '方案未能加入购物车，请重试。')
    return false
  }
}

async function removeConfiguration(id: number): Promise<boolean> {
  const request = beginAction()
  try {
    const value = parse(await write(`/api/cart/configurations/${id}`, undefined, 'DELETE', request.signal))
    if (!current(request)) return false
    success(request, value, '装机方案已移除。')
    return true
  } catch (cause) { failure(request, cause, '方案移除失败，请重试。'); return false }
}

function startEdit(group: Cart['configurations'][number]) {
  if (!cart.value) return
  const cpu = group.items.find(item => item.category === 'CPU')!
  editing.value = { id: group.id, revision: cart.value.revision, editToken: group.editToken,
    previousWorkspace: editing.value?.previousWorkspace,
    workspace: { version: 1, revision: 0, selections: group.items.map(item =>
      ({ category: item.category as HardwareCategory, productId: item.productId, source: 'DIY' })),
    memoryQuantity: group.items.find(item => item.category === 'MEMORY')!.quantity,
    ...(!group.items.some(item => item.category === 'GPU') ? { integratedGraphicsCpuId: cpu.productId } : {}) } }
  window.location.hash = 'diy'
}

async function replaceConfiguration(workspace: Workspace): Promise<boolean> {
  const edit = editing.value
  if (!edit) return false
  const request = beginAction()
  try {
    const value = parse(await write(`/api/cart/configurations/${edit.id}`, {
      selections: workspace.selections.map(({ category, productId }) => ({ category, productId })),
      integratedGraphicsCpuId: workspace.integratedGraphicsCpuId, memoryQuantity: workspace.memoryQuantity,
      revision: edit.revision, editToken: edit.editToken,
    }, 'POST', request.signal))
    if (!current(request)) return false
    success(request, value, '装机方案已整组替换。')
    finishEdit()
    window.location.hash = 'cart'
    return true
  } catch (cause) {
    failure(request, cause instanceof AuthError ? cause : new Error('整组修改结果暂未确认，请打开购物车核对后重新编辑。'), '整组修改失败，原组已保留。')
    return false
  }
}

function finishEdit() {
  if (editing.value?.previousWorkspace) saveWorkspace(editing.value.previousWorkspace)
  editing.value = null
}

function cancelEdit() { finishEdit(); window.location.hash = 'cart' }

async function checkoutPreview(itemIds: number[], configurationIds: number[], quoteToken?: string): Promise<CheckoutPreview | null> {
  if (!cart.value) return null
  const revision = cart.value.revision
  const request = beginAction()
  try {
    const value = await write('/api/cart/checkout-preview', { revision, itemIds, configurationIds, quoteToken }, 'POST', request.signal) as CheckoutPreview
    if (!current(request)) return null
    const sameIds = (rows: { id: number }[], ids: number[]) => rows.length === ids.length
      && new Set(rows.map(row => row.id)).size === ids.length && rows.every(row => ids.includes(row.id))
    if (!value || value.revision !== revision || !['CONFIRMATION_REQUIRED', 'RECONFIRMATION_REQUIRED', 'READY'].includes(value.status)
      || typeof value.quoteToken !== 'string' || !/^[a-f0-9]{64}$/.test(value.quoteToken)
      || !isMoney(value.total) || !Array.isArray(value.items) || !value.items.every(isItem)
      || !Array.isArray(value.configurations) || !value.configurations.every(isConfiguration)
      || !sameIds(value.items, itemIds) || !sameIds(value.configurations, configurationIds)
      || !Array.isArray(value.skuDemand) || !value.skuDemand.every(row => row && Number.isSafeInteger(row.productId)
        && row.productId > 0 && Number.isSafeInteger(row.quantity) && row.quantity > 0)) {
      throw new Error('预结算数据格式异常，尚未确认报价，请重试。')
    }
    return value
  } catch (cause) { failure(request, cause, '预结算失败，请重新核验。'); return null }
}

const count = computed(() => cart.value
  ? cart.value.items.reduce((sum, item) => sum + item.quantity, cart.value.configurations.length) : 0)

function parseOrder(value: unknown): Order {
  const result = value as Order | null
  if (!result || typeof result.orderNumber !== 'string' || !/^O[a-f0-9]{32}$/.test(result.orderNumber)
    || !['PENDING_PAYMENT', 'PAID', 'CANCELLED', 'COMPLETED'].includes(result.status) || result.currency !== 'CNY'
    || !isMoney(result.total) || !Number.isFinite(Date.parse(result.createdAt)) || !Number.isFinite(Date.parse(result.paymentDeadline))
    || !Array.isArray(result.items) || result.items.length === 0 || !result.items.every(item => item
      && Number.isSafeInteger(item.productId) && item.productId > 0
      && ['sku', 'name', 'model', 'category'].every(key => typeof item[key as keyof typeof item] === 'string')
      && Number.isSafeInteger(item.quantity) && item.quantity > 0 && item.quantity <= cartQuantity.max
      && isMoney(item.price) && isMoney(item.amount)
      && (item.configurationId === null || Number.isSafeInteger(item.configurationId) && item.configurationId > 0))
    || !Array.isArray(result.configurations) || !result.configurations.every(group => group
      && Number.isSafeInteger(group.id) && group.id > 0 && isMoney(group.amount))
    || new Set(result.configurations.map(group => group.id)).size !== result.configurations.length
    || result.items.some(item => item.configurationId !== null && !result.configurations.some(group => group.id === item.configurationId))
    || !result.configurations.every(group => validConfigurationMembers(result.items.filter(item => item.configurationId === group.id)))) {
    throw new Error('订单数据格式异常，请重试核对结果。')
  }
  return result
}

/** Retain the exact request and key until an unknown transport outcome has been resolved. */
async function createOrder(quote?: CheckoutPreview): Promise<boolean> {
  if (orderBusy.value) return false
  if (!orderAttempt.value) {
    if (!quote || quote.status !== 'READY') return false
    orderAttempt.value = { input: { revision: quote.revision, itemIds: quote.items.map(item => item.id),
      configurationIds: quote.configurations.map(group => group.id), quoteToken: quote.quoteToken, requestKey: requestKey() }, uncertain: false }
  }
  const attempt = orderAttempt.value
  const retry = attempt.uncertain
  const request = beginAction()
  orderBusy.value = true
  try {
    const result = parseOrder(await write('/api/orders', attempt.input, 'POST', request.signal))
    if (!current(request)) return false
    order.value = result
    orderAttempt.value = null
    window.location.hash = `order/${result.orderNumber}`
    void refreshCart(true)
    return true
  } catch (cause) {
    if (!current(request)) return false
    const refused = cause instanceof AuthError && cause.status >= 400 && cause.status < 500
    // These domain failures occur only after the server checks the successful idempotency
    // record under the cart lock. Unlike CSRF/authentication refusal, they resolve uncertainty.
    const resolved = refused && ['CART_CONFLICT', 'PRICE_CONFIRMATION_REQUIRED', 'CART_SELECTION_INVALID', 'CART_ITEM_NOT_FOUND'].includes(cause.code ?? '')
    if (refused && (!retry || resolved)) orderAttempt.value = null
    else attempt.uncertain = true
    failure(request, refused && (!retry || resolved) ? cause : new Error('下单结果暂未确认，请点击“重试确认上次下单”，使用原请求核对同一笔订单。'), '下单失败，请重新核验报价。')
    return false
  } finally { if (current(request)) orderBusy.value = false }
}

async function loadOrder(number: string): Promise<void> {
  const operation = beginAction()
  order.value = null
  try {
    const result = parseOrder(await request(`/api/orders/${encodeURIComponent(number)}`, { signal: operation.signal }))
    if (current(operation) && operation.order === latestAction) order.value = result
  } catch (cause) { failure(operation, cause, '订单暂时无法读取，请重试。') }
}

export function useCart() {
  const refresh = () => refreshCart()
  return { cart, loading, error, notice, count, refresh, clear, add, update, remove, addConfiguration, removeConfiguration,
    editing, startEdit, cancelEdit, replaceConfiguration, checkoutPreview, orderAttempt, orderBusy, createOrder, order, loadOrder }
}
