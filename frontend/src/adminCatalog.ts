import { request, write } from './auth'
import type { Product } from './catalog'

export interface AdminProduct extends Product { status: 'ON_SALE' | 'OFF_SALE' }
export interface ParameterField {
  code: string; label: string; type: 'TEXT' | 'NUMBER' | 'INTEGER' | 'BOOLEAN' | 'SET' | 'SSD_SLOTS'
  unit: string; required: boolean; options: string[]
}
export interface Slot { formFactor: string; interface: string; protocol: string }
export interface AdminParameter {
  code: string; label: string; value: string | number | boolean | string[] | Slot[] | null
  unit: string; sourceUrl: string; note: string
}
export interface AdminDetail { product: AdminProduct; description: string; parameters: AdminParameter[]; revision: number; dataVersion: string }
export interface AdminPage { items: AdminProduct[]; total: number; page: number; size: number }

export async function catalogPage(query: URLSearchParams) {
  return await request(`/api/admin/products?${query}`) as AdminPage
}
export async function catalogDetail(id: number) { return await request(`/api/admin/products/${id}`) as AdminDetail }
export async function parameterSchema(category: string) { return await request(`/api/admin/products/schema?category=${encodeURIComponent(category)}`) as ParameterField[] }
export async function saveProduct(id: number | null, input: unknown) {
  return await write(`/api/admin/products${id === null ? '' : `/${id}`}`, input) as AdminDetail
}
export async function changeSaleStatus(detail: AdminDetail) {
  return await write(`/api/admin/products/${detail.product.id}/status`, {
    status: detail.product.status === 'ON_SALE' ? 'OFF_SALE' : 'ON_SALE', revision: detail.revision,
  }) as AdminDetail
}
