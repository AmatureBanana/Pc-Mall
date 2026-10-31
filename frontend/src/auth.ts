export interface Account {
  id: number; username: string; nickname: string; role: 'USER' | 'ADMIN'
}
export interface AdminModule { code: string; title: string; description: string; availability: string }
export class AuthError extends Error {
  constructor(message: string, readonly status: number) { super(message) }
}

export async function request(path: string, init?: RequestInit): Promise<unknown> {
  const response = await fetch(path, { credentials: 'same-origin', cache: 'no-store', ...init })
  const body: unknown = await response.json()
  if (!response.ok) {
    const message = body && typeof body === 'object' && 'message' in body && typeof body.message === 'string'
      ? body.message : '账户服务暂时不可用，请稍后重试。'
    throw new AuthError(message, response.status)
  }
  if (!body || typeof body !== 'object') throw new Error('账户服务返回格式异常。')
  return 'data' in body ? body.data : body
}

function account(value: unknown): Account {
  if (!value || typeof value !== 'object') throw new Error('账户信息格式异常。')
  const item = value as Record<string, unknown>
  if (!Number.isSafeInteger(item.id) || Number(item.id) <= 0 || typeof item.username !== 'string'
      || typeof item.nickname !== 'string' || (item.role !== 'USER' && item.role !== 'ADMIN')) {
    throw new Error('账户信息格式异常。')
  }
  return item as unknown as Account
}

export async function currentAccount() { return account(await request('/api/auth/me')) }
export async function currentProfile() { return account(await request('/api/account/profile')) }

export async function write(path: string, body: unknown) {
  const value = await request('/api/auth/csrf')
  if (!value || typeof value !== 'object' || !('token' in value) || typeof value.token !== 'string'
      || !('headerName' in value) || typeof value.headerName !== 'string') throw new Error('安全凭证获取失败，请重试。')
  return request(path, { method: 'POST',
    headers: { 'Content-Type': 'application/json', [value.headerName]: value.token }, body: JSON.stringify(body) })
}
export async function writeAuth(action: 'register' | 'login' | 'logout', body: unknown) {
  return write(`/api/auth/${action}`, body)
}
export async function updateNickname(nickname: string) {
  return account(await write('/api/account/profile', { nickname }))
}
export async function changePassword(oldPassword: string, newPassword: string, confirmPassword: string) {
  await write('/api/account/password', { oldPassword, newPassword, confirmPassword })
}
export async function loginAccount(username: string, password: string) {
  return account(await writeAuth('login', { username, password }))
}
export async function adminModules(): Promise<AdminModule[]> {
  const value = await request('/api/admin/overview')
  if (!Array.isArray(value) || !value.every(item => item && ['code', 'title', 'description', 'availability']
    .every(key => typeof item[key] === 'string'))) throw new Error('后台导航格式异常，请重试。')
  return value
}
