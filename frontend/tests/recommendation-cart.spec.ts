import { expect, test, type APIRequestContext, type Page } from '@playwright/test'

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

for (const label of ['办公', '游戏', '创作']) {
  test(`${label}推荐经登录重验，再由用户确认整组加购`, async ({ page }) => {
    await page.goto('/')
    const username = await register(page)
    const inventory = async () => (await (await page.request.get('/api/products')).json()).data
    const before = await inventory()
    const plan = await recommendation(page, label)
    const links = await plan.getByRole('link').all()
    const ids = await Promise.all(links.map(async link => Number((await link.getAttribute('href'))?.split('/').pop())))
    expect(ids.length).toBeGreaterThanOrEqual(7)
    const posts: string[] = []
    const rechecks: string[] = []
    page.on('request', request => {
      const path = new URL(request.url()).pathname
      if (path === cartPath && request.method() === 'POST') posts.push(path)
      if (['/api/diy/quote', '/api/compatibility/check'].includes(path)) rechecks.push(path)
    })
    await plan.getByRole('button', { name: '加入购物车', exact: true }).click()
    await expect(page).toHaveURL(/#login$/)
    if (label === '办公') await page.reload()
    rechecks.length = 0
    await signIn(page, username)
    await expect(page).toHaveURL(/#diy$/)
    const confirmation = page.getByRole('dialog', { name: '转入购物车' })
    const confirm = confirmation.getByRole('button', { name: '确定', exact: true })
    await expect(confirm).toBeEnabled()
    expect(rechecks).toEqual(expect.arrayContaining(['/api/diy/quote', '/api/compatibility/check']))
    expect(posts).toEqual([])
    expect((await cart(page)).configurations).toEqual([])
    const restored = await page.evaluate(() => JSON.parse(sessionStorage.getItem('pc-mall.diy.workspace.v1')!))
    expect(restored.origin).toBe('RECOMMENDATION')
    expect(restored.selections.map((part: { productId: number }) => part.productId)).toEqual(ids)
    expect(restored.selections.every((part: { source: string }) => part.source === 'RECOMMENDATION')).toBe(true)
    await confirm.evaluate((button: HTMLButtonElement) => { button.click(); button.click() })
    await expect(confirmation).not.toBeVisible()
    expect(posts).toHaveLength(1)
    const groups = (await cart(page)).configurations
    expect(groups).toHaveLength(1)
    expect(groups[0].origin).toBe('RECOMMENDATION')
    expect(groups[0].items.map((part: { productId: number }) => part.productId).sort()).toEqual(ids.sort())
    expect(await inventory()).toEqual(before)
    await page.getByRole('button', { name: /^购物车/ }).click()
    await expect(page.getByRole('region', { name: '装机方案 1' })).toContainText('来源：智能推荐')
  })
}

test('登录失效时保留推荐配置，重新登录后仍需主动确认', async ({ page }) => {
  await page.goto('/#login')
  const username = await register(page)
  await signIn(page, username)
  await expect(page.getByRole('button', { name: /^游戏推荐 ·/ })).toBeVisible()
  const plan = await recommendation(page, '游戏')
  await plan.getByRole('button', { name: '加入购物车', exact: true }).click()
  const confirmation = page.getByRole('dialog', { name: '转入购物车' })
  const confirm = confirmation.getByRole('button', { name: '确定', exact: true })
  await expect(confirm).toBeEnabled()
  const draft = await page.evaluate(() => sessionStorage.getItem('pc-mall.diy.workspace.v1'))
  expect((await post(page.request, '/api/auth/logout', {})).status()).toBe(200)
  await confirm.click()
  await expect(confirmation.getByRole('alert')).toContainText('登录已过期')
  expect(await page.evaluate(() => sessionStorage.getItem('pc-mall.diy.workspace.v1'))).toBe(draft)
  await confirmation.getByRole('button', { name: '前往登录' }).click()
  await signIn(page, username)
  await expect(confirm).toBeEnabled()
  expect((await cart(page)).configurations).toEqual([])
  await confirm.click()
  await expect(confirmation).not.toBeVisible()
  expect((await cart(page)).configurations).toHaveLength(1)
})

test('登录时显示仍可购买的库存变动，确认时拦截再次改价', async ({ page, request: admin }) => {
  const adminName = process.env.BROWSER_ADMIN_USERNAME
  const adminPassword = process.env.BROWSER_ADMIN_PASSWORD
  if (!adminName || !adminPassword) throw new Error('Run with the isolated browser test administrator.')
  expect((await post(admin, '/api/auth/login', { username: adminName, password: adminPassword })).status()).toBe(200)
  await page.goto('/')
  const username = await register(page)
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
    await plan.getByRole('button', { name: '加入购物车', exact: true }).click()
    await expect(page).toHaveURL(/#login$/)
    expect((await post(admin, `${path}/stock`, { delta: 1, reason: '推荐登录恢复库存测试' })).status()).toBe(200)
    await page.reload()
    await signIn(page, username)
    const preview = page.getByRole('dialog', { name: '转入购物车' })
    const confirm = preview.getByRole('button', { name: '确定', exact: true })
    await expect(preview.getByRole('list', { name: '登录后发现的商品变化' })).toContainText('电源可售库存由 2 件变为 3 件')
    await expect(confirm).toBeEnabled()
    const reprice = async (price: string) => expect((await post(admin, `${path}/price`, {
      revision: (await detail()).revision, price, reason: '推荐确认后变价测试',
    })).status()).toBe(200)
    await reprice('21.00')
    await page.evaluate(() => window.dispatchEvent(new Event('focus')))
    await expect(preview.getByRole('list', { name: '核对期间的商品变化' })).toContainText('电源参考价由 ¥20.00 变为 ¥21.00')
    await expect(confirm).toBeEnabled()
    const price = '22.00'
    await reprice(price)
    await confirm.click()
    await expect(preview.getByRole('alert')).toContainText('商品价格已变化')
    expect((await cart(page)).configurations).toEqual([])
    await expect(confirm).toBeEnabled()
    await confirm.click()
    await expect(preview).not.toBeVisible()
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
