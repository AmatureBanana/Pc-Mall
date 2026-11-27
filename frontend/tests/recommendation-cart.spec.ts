import { expect, test, type APIRequestContext, type Locator, type Page } from '@playwright/test'

const password = 'RecommendCart23!'
const cartPath = '/api/cart/configurations'

async function post(api: APIRequestContext, path: string, data: unknown) {
  const csrf = (await (await api.get('/api/auth/csrf')).json()).data
  return api.post(path, { data, headers: { [csrf.headerName]: csrf.token } })
}

async function register(page: Page) {
  const username = `r${Date.now().toString(36)}${Math.random().toString(36).slice(2, 6)}`
  const response = await post(page.request, '/api/auth/register', { username, nickname: '推荐方案用户', password, confirmPassword: password })
  expect(response.status()).toBe(201)
  return username
}

async function signIn(page: Page, username: string) {
  const login = page.getByRole('dialog', { name: '统一登录' })
  await login.getByLabel('用户名', { exact: true }).fill(username)
  await login.getByLabel('密码', { exact: true }).fill(password)
  await login.getByRole('button', { name: '登录', exact: true }).click()
  await expect(login).not.toBeVisible()
}

async function recommendation(page: Page, label: string) {
  await page.getByRole('button', { name: new RegExp(`^${label}推荐 ·`) }).click()
  const dialog = page.getByRole('dialog', { name: `${label}推荐`, exact: true })
  await dialog.getByLabel('预算（CNY）').fill('20000')
  await dialog.getByRole('button', { name: `生成${label}方案` }).click()
  const plan = dialog.getByRole('region', { name: `${label}推荐方案` })
  await expect(plan).toBeVisible()
  return plan
}

async function cart(page: Page) {
  return (await (await page.request.get('/api/cart')).json()).data
}

async function importRecommendation(page: Page, plan: Locator) {
  await expect(plan.getByRole('button', { name: '加入购物车', exact: true })).toHaveCount(0)
  await plan.getByRole('button', { name: '转入 DIY 调整', exact: true }).click()
  await expect(page).toHaveURL(/#diy$/)
  await expect(page.getByRole('region', { name: '当前配置' })).toBeVisible()
  await expect(page.getByRole('dialog', { name: '统一登录' })).not.toBeVisible()
  await expect(page.getByRole('dialog', { name: '转入购物车' })).not.toBeVisible()
}

const transfer = (page: Page) => page.getByRole('button', { name: '转入购物车', exact: true })

async function openCartSignIn(page: Page) {
  await transfer(page).click()
  await expect(page).toHaveURL(/#login$/)
}

for (const label of ['办公', '游戏', '创作']) {
  test(`${label}推荐经登录重验后直接整组加购`, async ({ page }) => {
    await page.goto('/')
    const username = await register(page)
    const inventory = async () => (await (await page.request.get('/api/products')).json()).data
    const before = await inventory()
    const plan = await recommendation(page, label)
    const links = await plan.getByRole('link').all()
    const ids = await Promise.all(links.map(async link => Number((await link.getAttribute('href'))?.split('/').pop())))
    expect(ids.length).toBeGreaterThanOrEqual(7)
    const posts: { origin: string; selections: { productId: number; category: string }[]; memoryQuantity?: number }[] = []
    const rechecks: string[] = []
    page.on('request', request => {
      const path = new URL(request.url()).pathname
      if (path === cartPath && request.method() === 'POST') posts.push(request.postDataJSON())
      if (['/api/diy/quote', '/api/compatibility/check'].includes(path)) rechecks.push(path)
    })
    await importRecommendation(page, plan)
    let adjustedMemoryQuantity: number | undefined
    if (label === '游戏') {
      await expect(page.getByRole('button', { name: '转入购物车', exact: true })).toBeEnabled()
      await page.getByRole('button', { name: '替换 内存', exact: true }).click()
      const quantity = page.locator('output[aria-label="当前内存根数"]')
      const originalQuantity = Number(await quantity.textContent())
      await page.getByRole('button', { name: originalQuantity > 1 ? '减少内存数量' : '增加内存数量' }).click()
      adjustedMemoryQuantity = originalQuantity > 1 ? originalQuantity - 1 : originalQuantity + 1
      await expect(quantity).toHaveText(String(adjustedMemoryQuantity))
    }
    await openCartSignIn(page)
    if (label === '办公') await page.reload()
    rechecks.length = 0
    expect(posts).toEqual([])
    const restored = await page.evaluate(() => JSON.parse(sessionStorage.getItem('pc-mall.diy.workspace.v1')!))
    expect(restored.origin).toBe('RECOMMENDATION')
    expect(restored.selections.map((part: { productId: number }) => part.productId)).toEqual(ids)
    expect(restored.selections.every((part: { source: string }) => part.source === 'RECOMMENDATION')).toBe(true)
    await signIn(page, username)
    await expect(page).toHaveURL(/#cart$/)
    expect(rechecks).toEqual(expect.arrayContaining(['/api/diy/quote', '/api/compatibility/check']))
    expect(posts).toHaveLength(1)
    expect(posts[0].selections.map(part => part.productId)).toEqual(ids)
    if (adjustedMemoryQuantity !== undefined) expect(posts[0].memoryQuantity ?? 1).toBe(adjustedMemoryQuantity)
    const groups = (await cart(page)).configurations
    expect(groups).toHaveLength(1)
    expect(groups[0].origin).toBe('RECOMMENDATION')
    expect(groups[0].items.map((part: { productId: number }) => part.productId).sort()).toEqual(ids.sort())
    expect(await inventory()).toEqual(before)
    await expect(page.getByRole('region', { name: '装机方案 1' })).toContainText('来源：智能推荐')
  })
}

test('登录失效时保留推荐配置，再次转入并登录后继续加购', async ({ page }) => {
  await page.goto('/#login')
  const username = await register(page)
  await signIn(page, username)
  await expect(page.getByRole('button', { name: /^游戏推荐 ·/ })).toBeVisible()
  const plan = await recommendation(page, '游戏')
  await importRecommendation(page, plan)
  await expect(transfer(page)).toBeEnabled()
  const draft = await page.evaluate(() => sessionStorage.getItem('pc-mall.diy.workspace.v1'))
  expect((await post(page.request, '/api/auth/logout', {})).status()).toBe(200)
  await transfer(page).click()
  await expect(page.locator('.diy-actions').getByRole('alert')).toContainText('登录已过期')
  expect(await page.evaluate(() => sessionStorage.getItem('pc-mall.diy.workspace.v1'))).toBe(draft)
  await openCartSignIn(page)
  await signIn(page, username)
  await expect(page).toHaveURL(/#cart$/)
  expect((await cart(page)).configurations).toHaveLength(1)
})

test('转入购物车时使用服务端的最新价格和库存', async ({ page, request: admin }) => {
  const adminName = process.env.BROWSER_ADMIN_USERNAME
  const adminPassword = process.env.BROWSER_ADMIN_PASSWORD
  if (!adminName || !adminPassword) throw new Error('Run with the isolated browser test administrator.')
  expect((await post(admin, '/api/auth/login', { username: adminName, password: adminPassword })).status()).toBe(200)
  await page.goto('/#login')
  const username = await register(page)
  await signIn(page, username)
  const fixture = await post(admin, '/api/admin/products', {
    sku: `WEB23-${Date.now().toString(36)}`, category: 'PSU', name: '推荐恢复测试电源', brand: '测试品牌',
    model: 'Web23-750', summary: '推荐恢复验收夹具', description: '仅用于独立测试库',
    imageUrl: '/images/psu.svg', sourceUrl: 'https://example.com/psu', price: '20.00',
    parameters: [{ code: 'ratedPower', value: 750, unit: 'W', sourceUrl: 'https://example.com/psu' }],
  })
  expect(fixture.status()).toBe(201)
  const id: number = (await fixture.json()).data.product.id
  const path = `/api/admin/products/${id}`
  const detail = async () => (await (await admin.get(path)).json()).data
  try {
    expect((await post(admin, `${path}/status`, { revision: 0, status: 'ON_SALE' })).status()).toBe(200)
    expect((await post(admin, `${path}/stock`, { delta: 2, reason: '推荐登录恢复夹具备货' })).status()).toBe(200)
    const plan = await recommendation(page, '办公')
    await expect(plan.getByRole('link', { name: '推荐恢复测试电源' })).toHaveAttribute('href', `#product/${id}`)
    await importRecommendation(page, plan)
    await expect(transfer(page)).toBeEnabled()
    await expect(page.getByRole('group', { name: '电源配置' })).toContainText('¥20.00')
    expect((await post(admin, `${path}/stock`, { delta: 1, reason: '推荐登录恢复库存测试' })).status()).toBe(200)
    const price = '22.00'
    expect((await post(admin, `${path}/price`, {
      revision: (await detail()).revision, price, reason: '转入前变价测试',
    })).status()).toBe(200)
    await transfer(page).click()
    await expect(page).toHaveURL(/#cart$/)
    await expect(page.getByRole('region', { name: '装机方案 1' }).getByRole('listitem')
      .filter({ hasText: '推荐恢复测试电源' })).toContainText('¥22.00')
    const groups = (await cart(page)).configurations
    expect(groups).toHaveLength(1)
    expect(groups[0].items.find((item: { productId: number }) => item.productId === id).price).toBe(price)
  } finally {
    const current = await detail()
    if (current.product.status === 'ON_SALE') {
      expect((await post(admin, `${path}/status`, { revision: current.revision, status: 'OFF_SALE' })).status()).toBe(200)
    }
  }
})
