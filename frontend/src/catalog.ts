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
export async function loadProducts(signal: AbortSignal): Promise<Product[]> {
  const response = await fetch('/api/products', { signal, headers: { Accept: 'application/json' } })
  if (!response.ok) throw new Error('商品服务暂时不可用，请稍后重试。')
  const body: unknown = await response.json()
  if (!body || typeof body !== 'object' || !('data' in body)
      || !Array.isArray(body.data) || !body.data.every(isProduct)) {
    throw new Error('商品数据格式异常，请稍后重试。')
  }
  return body.data
}
const currency = new Intl.NumberFormat('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
export const formatPrice = (price: string) => currency.format(Number(price))
