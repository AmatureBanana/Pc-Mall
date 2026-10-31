import { request, write } from './auth'

export type UserStatus = 'ACTIVE' | 'DISABLED'
export interface AdminUser {
  id: number; username: string; nickname: string; role: 'USER'; status: UserStatus
  revision: number; createdAt: string; updatedAt: string
}
export interface UserPage { items: AdminUser[]; total: number; page: number; size: number }

export async function userPage(query: URLSearchParams) {
  return await request(`/api/admin/users?${query}`) as UserPage
}
export async function userDetail(id: number) {
  return await request(`/api/admin/users/${id}`) as AdminUser
}
export async function changeUserStatus(user: AdminUser, status: UserStatus) {
  return await write(`/api/admin/users/${user.id}/status`, { status, revision: user.revision }) as AdminUser
}
