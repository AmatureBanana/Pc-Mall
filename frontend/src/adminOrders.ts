import { request, write } from './auth'
import type { Order } from './cart'

export interface AdminOrder { buyer: { id: number; username: string; nickname: string }; order: Order }
export interface AdminOrderPage { items: AdminOrder[]; total: number; page: number; pageSize: number }
export const orderPage = async (query: URLSearchParams) => await request(`/api/admin/orders?${query}`) as AdminOrderPage
export const orderDetail = async (number: string) => await request(`/api/admin/orders/${encodeURIComponent(number)}`) as AdminOrder
export const completeOrder = async (number: string) => await write(`/api/admin/orders/${encodeURIComponent(number)}/complete`, {}) as AdminOrder
