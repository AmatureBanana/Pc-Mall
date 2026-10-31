export const categories = [
  { code: 'ALL', label: '全部商品', icon: 'grid' },
  { code: 'CPU', label: 'CPU', icon: 'cpu' },
  { code: 'MOTHERBOARD', label: '主板', icon: 'board' },
  { code: 'GPU', label: '独显', icon: 'gpu' },
  { code: 'MEMORY', label: '内存', icon: 'memory' },
  { code: 'SSD', label: 'SSD', icon: 'ssd' },
  { code: 'PSU', label: '电源', icon: 'psu' },
  { code: 'CASE', label: '机箱', icon: 'case' },
  { code: 'COOLER', label: '独立散热器', icon: 'cooler' },
] as const
export type Category = typeof categories[number]['code']
export interface Product {
  id: number; sku: string; name: string; category: Exclude<Category, 'ALL'>; categoryLabel: string
  brand: string; model: string; summary: string; imageUrl: string; price: string; stock: number
  sourceUrl: string; priceNote: string
}
export interface ProductSlot { formFactor: string; interface: string; protocol: string }
export interface ProductParameter {
  code: string; label: string; value: string | number | boolean | string[] | ProductSlot[] | null
  unit: string; sourceUrl: string; note: string
}
export interface ProductDetail {
  product: Product; description: string; parameters: ProductParameter[]; dataVersion: string
}
function isProduct(value: unknown): value is Product {
  if (!value || typeof value !== 'object') return false
  const item = value as Record<string, unknown>
  const textFields = ['sku', 'name', 'categoryLabel', 'brand', 'model', 'summary', 'imageUrl', 'sourceUrl', 'priceNote']
  return Number.isSafeInteger(item.id) && Number(item.id) > 0
    && textFields.every(key => typeof item[key] === 'string' && item[key] !== '')
    && categories.some(category => category.code !== 'ALL' && category.code === item.category)
    && typeof item.price === 'string' && /^\d+\.\d{2}$/.test(item.price)
    && Number.isSafeInteger(item.stock) && Number(item.stock) >= 0
}
export async function loadProducts(signal: AbortSignal, category: Category = 'ALL', keyword = ''): Promise<Product[]> {
  const query = new URLSearchParams()
  if (category !== 'ALL') query.set('category', category)
  if (keyword) query.set('keyword', keyword)
  const response = await fetch(`/api/products${query.size ? `?${query}` : ''}`, { signal, headers: { Accept: 'application/json' } })
  if (!response.ok) throw new Error('商品服务暂时不可用，请稍后重试。')
  const body: unknown = await response.json()
  if (!body || typeof body !== 'object' || !('data' in body)
      || !Array.isArray(body.data) || !body.data.every(isProduct)) {
    throw new Error('商品数据格式异常，请稍后重试。')
  }
  return body.data
}
export class ProductRequestError extends Error {
  constructor(message: string, readonly retryable: boolean) { super(message) }
}
function isParameter(value: unknown): value is ProductParameter {
  if (!value || typeof value !== 'object') return false
  const item = value as Record<string, unknown>
  return ['code', 'label'].every(key => typeof item[key] === 'string' && item[key] !== '')
    && typeof item.sourceUrl === 'string' && (item.value === null || item.sourceUrl !== '')
    && typeof item.unit === 'string' && typeof item.note === 'string'
    && (item.value === null || typeof item.value === 'string' || typeof item.value === 'boolean'
      || (typeof item.value === 'number' && Number.isFinite(item.value))
      || (Array.isArray(item.value) && item.value.every(entry => typeof entry === 'string'
        || item.code === 'ssdSlots' && entry && typeof entry === 'object'
          && ['formFactor', 'interface', 'protocol'].every(key => typeof entry[key] === 'string'))))
}
export async function loadProduct(id: string, signal: AbortSignal): Promise<ProductDetail> {
  const response = await fetch(`/api/products/${encodeURIComponent(id)}`, { signal, headers: { Accept: 'application/json' } })
  if (!response.ok) {
    const messages: Record<number, string> = {
      400: '商品 ID 无效，请返回列表重新选择。',
      404: '商品不存在，请返回列表重新选择。',
      409: '商品已下架，请返回列表重新选择。',
    }
    throw new ProductRequestError(messages[response.status] ?? '商品详情暂时不可用，请稍后重试。', response.status >= 500)
  }
  const body: unknown = await response.json()
  if (!body || typeof body !== 'object' || !('data' in body) || !body.data || typeof body.data !== 'object') {
    throw new Error('商品详情数据格式异常，请稍后重试。')
  }
  const detail = body.data as Record<string, unknown>
  if (!isProduct(detail.product) || detail.product.id !== Number(id)
      || typeof detail.description !== 'string' || !detail.description
      || typeof detail.dataVersion !== 'string' || !detail.dataVersion
      || !Array.isArray(detail.parameters) || !detail.parameters.length || !detail.parameters.every(isParameter)) {
    throw new Error('商品详情数据格式异常，请稍后重试。')
  }
  return detail as unknown as ProductDetail
}
export function formatParameter(parameter: ProductParameter): string {
  const value = parameter.value
  if (value === null) return '未核验'
  if (typeof value === 'boolean') return value ? '有' : '无'
  return `${Array.isArray(value) ? value.map(entry => typeof entry === 'string' ? entry : `${entry.formFactor} · ${entry.interface} · ${entry.protocol}`).join('、') || '无' : value}${parameter.unit ? ` ${parameter.unit}` : ''}`
}
export function imageFallback(event: Event) {
  const image = event.target as HTMLImageElement
  if (!image.src.endsWith('/images/fallback.svg')) image.src = '/images/fallback.svg'
}
const currency = new Intl.NumberFormat('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
export const formatPrice = (price: string) => currency.format(Number(price))
