import { test, expect } from '@playwright/test'

test('visitor registers, separately logs in, sees forbidden administration and logs out', async ({ page }) => {
  const username = `web_${Date.now().toString(36)}`
  await page.goto('/')
  await page.getByRole('button', { name: '注册', exact: true }).click()
  const registration = page.getByRole('dialog', { name: '注册账户' })
  await registration.getByLabel('用户名', { exact: true }).fill(username)
  await registration.getByLabel('昵称（选填）').fill('装机新朋友')
  await registration.getByLabel('密码', { exact: true }).fill('BrowserPassword123!')
  await registration.getByLabel('确认密码').fill('BrowserPassword123!')
  await registration.getByRole('button', { name: '创建账户' }).click()
  const login = page.getByRole('dialog', { name: '统一登录' })
  await expect(login).toBeVisible()
  await expect(login.getByRole('status')).toContainText('注册成功，请登录')
  await expect(page.getByRole('button', { name: '退出登录' })).toHaveCount(0)
  await login.getByLabel('密码', { exact: true }).fill('BrowserPassword123!')
  await login.getByRole('button', { name: '登录', exact: true }).click()
  await expect(page.getByText('装机新朋友')).toBeVisible()
  await page.reload()
  await expect(page.getByText('装机新朋友')).toBeVisible()
  await page.goto('/#admin')
  await expect(page.getByRole('dialog', { name: '管理后台' }).getByRole('alert')).toContainText('没有管理员权限')
  await page.getByRole('dialog', { name: '管理后台' }).getByRole('button', { name: 'Close this dialog' }).click()
  // Hold a real, previously authenticated response across logout to verify stale UI cannot restore it.
  let captured!: () => void
  let release!: () => void
  const pending = new Promise<void>(resolve => { release = resolve })
  const ready = new Promise<void>(resolve => { captured = resolve })
  await page.route('**/api/auth/me', async route => {
    const response = await route.fetch()
    captured()
    await pending
    await route.fulfill({ response })
  })
  await page.evaluate(() => window.dispatchEvent(new Event('focus')))
  await ready
  await page.getByRole('button', { name: '退出登录' }).click()
  await expect(page.getByRole('button', { name: '登录', exact: true })).toBeVisible()
  const finished = page.waitForEvent('requestfinished', request => request.url().endsWith('/api/auth/me'))
  release()
  await finished
  await page.evaluate(() => new Promise(resolve => requestAnimationFrame(() => requestAnimationFrame(resolve))))
  await expect(page.getByRole('button', { name: '登录', exact: true })).toBeVisible()
  expect((await page.request.get('/api/auth/me')).status()).toBe(401)
  await expect(page.locator('.product-card')).toHaveCount(12)
})

test('administrator uses the unified login and can navigate every administration module', async ({ page }) => {
  const username = process.env.BROWSER_ADMIN_USERNAME
  const password = process.env.BROWSER_ADMIN_PASSWORD
  if (!username || !password) throw new Error('Use scripts/test.ps1 to start an isolated server with ephemeral administrator credentials.')
  await page.goto('/#login')
  const login = page.getByRole('dialog', { name: '统一登录' })
  await login.getByLabel('用户名', { exact: true }).fill(username)
  await login.getByLabel('密码', { exact: true }).fill(password)
  await login.getByRole('button', { name: '登录', exact: true }).click()
  const admin = page.getByRole('dialog', { name: '管理后台' })
  await expect(admin.getByRole('heading', { name: '商城管理中心' })).toBeVisible()
  for (const title of ['商品管理', '用户管理', '订单管理']) {
    await admin.getByRole('navigation', { name: '后台模块' }).getByRole('button', { name: title }).click()
    await expect(admin.getByRole('heading', { name: title })).toBeVisible()
    if (title === '商品管理') await expect(admin.getByRole('button', { name: '新增商品' })).toBeVisible()
    else if (title === '用户管理') await expect(admin.getByLabel('查询用户名')).toBeVisible()
    else await expect(admin.getByRole('status')).toContainText('即将开放')
  }
  await admin.getByRole('navigation').getByRole('button', { name: '后台概览' }).click()
  await expect(admin.getByRole('heading', { name: '商城管理中心' })).toBeVisible()
  await page.screenshot({ path: '../.local/task-03-admin.png', fullPage: true, animations: 'disabled' })
})

test('login errors remain actionable and a lost session clearly requires signing in', async ({ page }) => {
  await page.goto('/#login')
  const login = page.getByRole('dialog', { name: '统一登录' })
  await login.getByLabel('用户名', { exact: true }).fill('absent_test_user')
  await login.getByLabel('密码', { exact: true }).fill('WrongPassword123!')
  await login.getByRole('button', { name: '登录', exact: true }).click()
  await expect(login.getByRole('alert')).toContainText('用户名或密码错误')
  await page.goto('/#admin')
  await expect(page.getByRole('dialog', { name: '管理后台' }).getByRole('alert')).toContainText('请重新登录')
  await page.getByRole('button', { name: '前往登录' }).click()
  await expect(login).toBeVisible()
  await expect(login.getByLabel('密码', { exact: true })).toHaveValue('')
})
