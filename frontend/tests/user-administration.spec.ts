import { test, expect, type APIRequestContext, type Page } from '@playwright/test'

async function write(request: APIRequestContext, path: string, data: unknown) {
  const csrf = (await (await request.get('/api/auth/csrf')).json()).data
  return request.post(path, { headers: { [csrf.headerName]: csrf.token }, data })
}
async function login(page: Page, username: string, password: string) {
  await page.goto('/#login')
  const form = page.getByRole('dialog', { name: '统一登录' })
  await form.getByLabel('用户名', { exact: true }).fill(username)
  await form.getByLabel('密码', { exact: true }).fill(password)
  await form.getByRole('button', { name: '登录', exact: true }).click()
}
async function loginAdmin(page: Page) {
  const username = process.env.BROWSER_ADMIN_USERNAME, password = process.env.BROWSER_ADMIN_PASSWORD
  if (!username || !password) throw new Error('Run with the isolated browser test administrator.')
  await login(page, username, password)
  const admin = page.getByRole('dialog', { name: '管理后台' })
  await admin.getByRole('navigation').getByRole('button', { name: '用户管理' }).click()
  return admin
}

test('administrator filters, views, disables and enables an ordinary user while existing sessions immediately lose access', async ({ page, browser }) => {
  const context = await browser.newContext()
  const dormant = await browser.newContext()
  const owner = await context.newPage()
  const username = `web06_${Date.now()}`, password = 'BrowserUserAdmin123!'
  try {
    expect((await write(owner.request, '/api/auth/register', { username, nickname: '浏览器普通用户', password, confirmPassword: password })).status()).toBe(201)
    await login(owner, username, password)
    await expect(owner.getByRole('button', { name: '个人资料', exact: true })).toBeVisible()
    await expect(owner.getByRole('button', { name: '管理后台', exact: true })).toHaveCount(0)
    await owner.goto('/#admin/users')
    await expect(owner.getByRole('dialog', { name: '管理后台' }).getByRole('alert')).toContainText('没有管理员权限')
    expect((await owner.request.get('/api/admin/users')).status()).toBe(403)
    await owner.goto('/#profile')
    await expect(owner.getByRole('dialog', { name: '个人资料与账户安全' })).toContainText(username)
    expect((await write(dormant.request, '/api/auth/login', { username, password })).status()).toBe(200)

    const admin = await loginAdmin(page)
    await expect(admin.getByLabel('查询用户名')).toBeVisible()
    await admin.getByLabel('查询用户名').fill(username)
    await admin.getByLabel('筛选账户状态').selectOption('ACTIVE')
    await admin.getByRole('button', { name: '查询', exact: true }).click()
    await expect(admin.getByRole('row').filter({ hasText: username })).toContainText('已启用')
    await page.screenshot({ path: '../.local/task-06-list.png', fullPage: true, animations: 'disabled' })
    await admin.getByRole('button', { name: '查看用户', exact: true }).click()
    await expect(admin.getByRole('definition').filter({ hasText: username })).toBeVisible()
    await expect(admin.getByRole('textbox')).toHaveCount(0)
    await admin.getByRole('button', { name: '禁用账户', exact: true }).click()
    const confirmation = admin.getByRole('group', { name: '确认账户状态变更' })
    await expect(confirmation).toContainText(username)
    await confirmation.getByRole('button', { name: '取消', exact: true }).click()
    await expect(admin.getByRole('button', { name: '禁用账户', exact: true })).toBeVisible()
    await admin.getByRole('button', { name: '禁用账户', exact: true }).click()
    await confirmation.getByRole('button', { name: '确认禁用', exact: true }).click()
    await expect(admin.getByRole('status')).toContainText('账户已禁用')
    expect((await owner.request.get('/api/account/profile')).status()).toBe(401)
    await owner.reload()
    await expect(owner.getByRole('dialog', { name: '个人资料与账户安全' }).getByRole('alert')).toContainText('请重新登录')
    await login(owner, username, password)
    await expect(owner.getByRole('dialog', { name: '统一登录' }).getByRole('alert')).toContainText('账户不可用')
    await page.screenshot({ path: '../.local/task-06-disabled.png', fullPage: true, animations: 'disabled' })
    await admin.getByRole('button', { name: '返回用户列表' }).click()
    await expect(admin.getByRole('row').filter({ hasText: username })).toHaveCount(0)
    await admin.getByLabel('筛选账户状态').selectOption('DISABLED')
    await admin.getByRole('button', { name: '查询', exact: true }).click()
    await expect(admin.getByRole('row').filter({ hasText: username })).toContainText('已禁用')
    await admin.getByRole('button', { name: '查看用户', exact: true }).click()
    await admin.getByRole('button', { name: '启用账户', exact: true }).click()
    await confirmation.getByRole('button', { name: '确认启用', exact: true }).click()
    await expect(admin.getByRole('status')).toContainText('账户已启用')
    expect((await dormant.request.get('/api/account/profile')).status()).toBe(401)
    await login(owner, username, password)
    await expect(owner.getByRole('button', { name: '个人资料', exact: true })).toBeVisible()
    const result = await page.request.get(`/api/admin/users?username=${username}`)
    const user = (await result.json()).data.items[0]
    expect(user.status).toBe('ACTIVE')
    expect(user.nickname).toBe('浏览器普通用户')
    const audit = (await (await page.request.get(`/api/admin/users/${user.id}/audit`)).json()).data
    expect(audit).toHaveLength(2)
    expect(audit[0].afterValue.status).toBe('ACTIVE')
    expect(audit[1].afterValue.status).toBe('DISABLED')
  } finally {
    await context.close()
    await dormant.close()
  }
})

test('user administration distinguishes failures from empty results and requires a reload after a conflicting status edit', async ({ page }) => {
  const username = `err06_${Date.now()}`, password = 'BrowserUserAdmin123!'
  expect((await write(page.request, '/api/auth/register', { username, password, confirmPassword: password })).status()).toBe(201)
  await page.route('**/api/admin/users?**', route => route.fulfill({ status: 503, contentType: 'application/json',
    body: JSON.stringify({ code: 'AUTH_UNAVAILABLE', message: '账户服务暂时不可用，请稍后重试。' }) }), { times: 1 })
  const admin = await loginAdmin(page)
  await expect(admin.getByRole('alert')).toContainText('账户服务暂时不可用')
  await expect(admin.getByText('用户数据加载失败，请重新载入')).toBeVisible()
  await admin.getByRole('button', { name: '重新载入', exact: true }).click()
  await expect(admin.getByRole('alert')).toHaveCount(0)
  await admin.getByLabel('查询用户名').fill('NoSuchUser06')
  await admin.getByRole('button', { name: '查询', exact: true }).click()
  await expect(admin.getByText('没有符合条件的普通用户')).toBeVisible()
  await admin.getByLabel('查询用户名').fill(username)
  await admin.getByRole('button', { name: '查询', exact: true }).click()
  await admin.getByRole('button', { name: '查看用户', exact: true }).click()
  const user = (await (await page.request.get(`/api/admin/users?username=${username}`)).json()).data.items[0]
  expect((await write(page.request, `/api/admin/users/${user.id}/status`, { status: 'DISABLED', revision: 0 })).status()).toBe(200)
  await admin.getByRole('button', { name: '禁用账户', exact: true }).click()
  await admin.getByRole('button', { name: '确认禁用', exact: true }).click()
  await expect(admin.getByRole('alert')).toContainText('重新载入')
  await expect(admin.getByRole('status')).toHaveCount(0)
  await admin.getByRole('button', { name: '重新载入', exact: true }).click()
  await expect(admin.getByRole('button', { name: '启用账户', exact: true })).toBeVisible()
  await page.route('**/api/admin/users/*/status', route => route.fulfill({ status: 503, contentType: 'application/json',
    body: JSON.stringify({ code: 'AUTH_UNAVAILABLE', message: '账户服务暂时不可用，请稍后重试。' }) }), { times: 1 })
  await admin.getByRole('button', { name: '启用账户', exact: true }).click()
  await admin.getByRole('button', { name: '确认启用', exact: true }).click()
  await expect(admin.getByRole('alert')).toContainText('账户服务暂时不可用')
  await expect(admin.getByRole('status')).toHaveCount(0)
  await admin.getByRole('button', { name: '重新载入', exact: true }).click()
  await admin.getByRole('button', { name: '启用账户', exact: true }).click()
  await admin.getByRole('button', { name: '确认启用', exact: true }).click()
  await expect(admin.getByRole('status')).toContainText('账户已启用')
})
