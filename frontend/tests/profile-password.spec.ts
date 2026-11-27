import { test, expect, type Page } from '@playwright/test'
import { randomUUID } from 'node:crypto'

const password = 'OriginalPassword123!'

async function register(page: Page) {
  const username = `prof_${randomUUID().replaceAll('-', '').slice(0, 12)}`
  const csrf = (await (await page.request.get('/api/auth/csrf')).json()).data
  const response = await page.request.post('/api/auth/register', {
    headers: { [csrf.headerName]: csrf.token },
    data: { username, password, confirmPassword: password, nickname: '资料测试用户' },
  })
  expect(response.status()).toBe(201)
  return username
}

async function login(page: Page, username: string, secret = password) {
  await page.goto('/#login')
  const form = page.getByRole('dialog', { name: '统一登录' })
  await form.getByLabel('用户名', { exact: true }).fill(username)
  await form.getByLabel('密码', { exact: true }).fill(secret)
  await form.getByRole('button', { name: '登录', exact: true }).click()
  await expect(page.getByRole('button', { name: '用户菜单', exact: true })).toBeVisible()
}

test('profile shows the owner, saves Unicode nicknames and restores the saved value after reload', async ({ page }) => {
  const username = await register(page)
  await login(page, username)
  await page.getByRole('button', { name: '用户菜单', exact: true }).click()
  await page.getByRole('navigation', { name: '用户功能' }).getByRole('link', { name: '个人中心', exact: true }).click()
  const profile = page.getByRole('main', { name: '个人中心' })
  await expect(profile.getByText(username, { exact: true })).toBeVisible()
  await expect(profile.getByLabel('昵称', { exact: true })).toHaveValue('资料测试用户')
  await profile.getByLabel('昵称', { exact: true }).fill('名'.repeat(31))
  await profile.getByRole('button', { name: '保存昵称' }).click()
  await expect(profile.getByRole('alert')).toContainText('最多 30')
  await profile.getByLabel('昵称', { exact: true }).fill('😀'.repeat(30))
  await profile.getByRole('button', { name: '保存昵称' }).click()
  await expect(profile.getByRole('status')).toContainText('昵称已保存')
  await expect(page.locator('.account-name')).toContainText('😀'.repeat(30))
  await page.reload()
  await expect(profile.getByLabel('昵称', { exact: true })).toHaveValue('😀'.repeat(30))
  await profile.getByLabel('昵称', { exact: true }).fill('装机小伙伴')
  await profile.getByRole('button', { name: '保存昵称' }).click()
  await expect(profile.getByRole('status')).toContainText('昵称已保存')
  await page.screenshot({ path: '../.local/task-04-profile.png', fullPage: true, animations: 'disabled' })
})

test('password change rejects mistakes then signs out both independent browser sessions', async ({ page, browser }) => {
  const username = await register(page)
  const otherContext = await browser.newContext({ baseURL: process.env.BROWSER_BASE_URL })
  try {
    const other = await otherContext.newPage()
    await login(page, username)
    await login(other, username)
    await page.getByRole('button', { name: '用户菜单', exact: true }).click()
    await page.getByRole('navigation', { name: '用户功能' }).getByRole('link', { name: '账号与安全', exact: true }).click()
    const profile = page.getByRole('main', { name: '账号与安全' })
    await profile.getByLabel('旧密码', { exact: true }).fill('WrongPassword123!')
    await profile.getByLabel('新密码', { exact: true }).fill('ChangedPassword456!')
    await profile.getByLabel('确认新密码', { exact: true }).fill('ChangedPassword456!')
    await profile.getByRole('button', { name: '修改密码并重新登录' }).click()
    await expect(profile.getByRole('alert')).toContainText('旧密码不正确')
    await expect(other.getByRole('button', { name: '用户菜单', exact: true })).toBeVisible()
    await profile.getByLabel('旧密码', { exact: true }).fill(password)
    await profile.getByLabel('确认新密码', { exact: true }).fill('DifferentPassword!')
    await profile.getByRole('button', { name: '修改密码并重新登录' }).click()
    await expect(profile.getByRole('alert')).toContainText('两次输入的新密码不一致')
    await profile.getByLabel('确认新密码', { exact: true }).fill('ChangedPassword456!')
    await profile.getByRole('button', { name: '修改密码并重新登录' }).click()
    const form = page.getByRole('dialog', { name: '统一登录' })
    await expect(form).toBeVisible()
    await expect(form.getByRole('status')).toContainText('密码修改成功')
    await expect(form.getByLabel('密码', { exact: true })).toHaveValue('')
    await other.reload()
    await expect(other.getByRole('button', { name: '游客用户菜单', exact: true })).toBeVisible()
    expect((await other.request.get('/api/account/profile')).status()).toBe(401)
    await login(page, username, 'ChangedPassword456!')
    await page.getByRole('button', { name: '用户菜单', exact: true }).click()
    await page.getByRole('navigation', { name: '用户功能' }).getByRole('link', { name: '账号与安全', exact: true }).click()
    await expect(profile.getByLabel('旧密码', { exact: true })).toHaveValue('')
  } finally { await otherContext.close() }
})

test('an anonymous profile link offers a clear path to login', async ({ page }) => {
  await page.goto('/#profile')
  const profile = page.getByRole('main', { name: '个人中心' })
  await expect(profile.getByRole('alert')).toContainText('请重新登录')
  await profile.getByRole('button', { name: '前往登录' }).click()
  await expect(page.getByRole('dialog', { name: '统一登录' })).toBeVisible()
})


test('owner uploads a persistent avatar, all account surfaces update and security hides the account ID', async ({ page, browser }) => {
  const username = await register(page)
  await login(page, username)
  await page.goto('/#profile')
  const profile = page.getByRole('main', { name: '个人中心' })
  const original = await page.request.get('/api/account/avatar')
  expect(original.headers()['content-type']).toContain('image/svg+xml')
  const png = Buffer.from('iVBORw0KGgoAAAANSUhEUgAAACAAAAAgCAIAAAD8GO2jAAAAO0lEQVR4nO3RQREAMAjEwKMaKrGyEVMJ4cMvK+CYCXVfZ9NZXY8HBvwBMhEyETIRMhEyETIRMhEyUcgHqC8BlFdZX0gAAAAASUVORK5CYII=', 'base64')
  const fileChooser = page.waitForEvent('filechooser')
  await profile.getByRole('button', { name: '上传头像', exact: true }).click()
  await (await fileChooser).setFiles({ name: 'avatar.png', mimeType: 'image/png', buffer: png })
  await expect(profile.getByRole('status')).toContainText('头像已保存')
  const saved = await page.request.get('/api/account/avatar')
  expect(saved.status()).toBe(200)
  expect(saved.headers()['content-type']).toContain('image/png')
  const bytes = await saved.body()
  expect(bytes.readUInt32BE(16)).toBe(256)
  expect(bytes.readUInt32BE(20)).toBe(256)
  await expect(page.locator('.user-avatar img')).toHaveCount(3)
  await page.reload()
  expect(await (await page.request.get('/api/account/avatar')).body()).toEqual(bytes)
  const csrf = (await (await page.request.get('/api/auth/csrf')).json()).data
  expect((await page.request.post('/api/account/avatar', { data: png })).status()).toBe(403)
  expect((await page.request.post('/api/account/avatar', { data: Buffer.from('<svg/>'), headers: { [csrf.headerName]: csrf.token } })).status()).toBe(400)
  expect((await page.request.post('/api/account/avatar', { data: Buffer.alloc(2 * 1024 * 1024 + 1), headers: { [csrf.headerName]: csrf.token } })).status()).toBe(400)
  expect(await (await page.request.get('/api/account/avatar')).body()).toEqual(bytes)
  const other = await browser.newContext({ baseURL: process.env.BROWSER_BASE_URL })
  try {
    expect((await other.request.get('/api/account/avatar')).status()).toBe(401)
    const another = await other.newPage()
    const otherName = await register(another)
    await login(another, otherName)
    expect((await other.request.get('/api/account/avatar')).headers()['content-type']).toContain('image/svg+xml')
  } finally { await other.close() }
  await page.getByRole('navigation', { name: '用户中心导航' }).getByRole('link', { name: '账号与安全' }).click()
  await expect(page.getByRole('main', { name: '账号与安全' })).not.toContainText('账户 ID')
  await page.getByRole('button', { name: '用户菜单', exact: true }).click()
  await page.getByRole('button', { name: '退出登录', exact: true }).click()
  await expect(page.getByRole('button', { name: '游客用户菜单', exact: true })).toBeVisible()
  await login(page, username)
  expect(await (await page.request.get('/api/account/avatar')).body()).toEqual(bytes)
})
