import { expect, test, type APIRequestContext, type Page } from '@playwright/test'

const PASSWORD = 'ConfigPassword123!'
const path = '/api/cart/configurations'
const confirmation = (page: Page) => page.getByRole('dialog', { name: '转入购物车' })
const cartPage = (page: Page) => page.getByRole('main', { name: '我的购物车' })

async function write(api: APIRequestContext, target: string, data: unknown) {
  const csrf = (await (await api.get('/api/auth/csrf')).json()).data
  return api.post(target, { data, headers: { [csrf.headerName]: csrf.token } })
}

async function register(page: Page) {
  const username = `g${Date.now().toString(36)}${Math.random().toString(36).slice(2, 6)}`
  expect((await write(page.request, '/api/auth/register', {
    username, nickname: '配置组用户', password: PASSWORD, confirmPassword: PASSWORD,
  })).status()).toBe(201)
  return { username, password: PASSWORD }
}

async function signIn(page: Page, credentials: { username: string; password: string }) {
  const dialog = page.getByRole('dialog', { name: '统一登录' })
  await dialog.getByLabel('用户名', { exact: true }).fill(credentials.username)
  await dialog.getByLabel('密码', { exact: true }).fill(credentials.password)
  await dialog.getByRole('button', { name: '登录', exact: true }).click()
  await expect(page.getByRole('button', { name: '退出登录' })).toBeVisible()
  await expect(dialog).not.toBeVisible()
}

async function workspace(page: Page, integrated = false) {
  const products = (await (await page.request.get('/api/products')).json()).data as { id: number; sku: string }[]
  const id = (sku: string) => {
    const product = products.find(item => item.sku === sku)
    if (!product) throw new Error(`Missing catalog fixture ${sku}`)
    return product.id
  }
  const cpu = integrated ? id('INTEL-I5-14600K') : 1
  const selections = Object.entries({ CPU: cpu, MOTHERBOARD: 3, MEMORY: id('KINGSTON-KF560C36BBE2-16'),
    SSD: 9, PSU: 11, CASE: 12, COOLER: 8, ...(!integrated ? { GPU: 5 } : {}) })
    .map(([category, productId]) => ({ category, productId, source: 'DIY' }))
  const value = { version: 1, revision: 7, selections, ...(integrated ? { integratedGraphicsCpuId: cpu } : {}) }
  await page.evaluate(value => sessionStorage.setItem('pc-mall.diy.workspace.v1', JSON.stringify(value)), value)
  await page.goto('/#diy')
  await expect(page.getByLabel('当前参考总价')).not.toHaveText('待核价')
  return value
}

async function signedInWorkspace(page: Page) {
  await page.goto('/#login')
  await signIn(page, await register(page))
  return workspace(page)
}

test('a partial configuration response is rejected and retry displays only the complete persisted group', async ({ page }) => {
  await signedInWorkspace(page)
  let intercepted = false
  await page.route(`**${path}`, async route => {
    if (intercepted || route.request().method() !== 'POST') { await route.continue(); return }
    intercepted = true
    const actual = await route.fetch()
    expect(actual.status()).toBe(200)
    const body = await actual.json()
    body.data.configurations[0].items = body.data.configurations[0].items.filter((item: { category: string }) => item.category !== 'COOLER')
    await route.fulfill({ response: actual, json: body })
  })
  await page.getByRole('button', { name: '转入购物车', exact: true }).click()
  await confirmation(page).getByRole('button', { name: '确定', exact: true }).click()
  await expect(confirmation(page).getByRole('alert')).toContainText('结果暂未确认')
  await expect(page.getByText('装机方案已加入购物车', { exact: true })).toHaveCount(0)
  await confirmation(page).getByRole('button', { name: '确定', exact: true }).click()
  await expect(confirmation(page)).not.toBeVisible()
  await page.getByRole('button', { name: /^购物车/ }).click()
  const group = cartPage(page).getByRole('region', { name: '装机方案 1' })
  await expect(group.getByRole('listitem')).toHaveCount(8)
  await expect(cartPage(page).getByRole('region', { name: '装机方案 2' })).toHaveCount(0)
})

test('guest signs in and explicitly confirms a seven-part build whose SKU and current price remain visible', async ({ page }) => {
  await page.goto('/')
  const credentials = await register(page)
  const original = await workspace(page, true)
  const submissions: unknown[] = []
  page.on('request', request => {
    if (new URL(request.url()).pathname === path && request.method() === 'POST') submissions.push(request.postDataJSON())
  })
  await page.getByRole('button', { name: '转入购物车', exact: true }).click()
  await expect(confirmation(page).getByRole('listitem')).toHaveCount(8)
  await expect(confirmation(page)).toContainText('核显')
  await confirmation(page).getByRole('button', { name: '前往登录', exact: true }).click()
  await signIn(page, credentials)
  await expect(page).toHaveURL(/#diy$/)
  await expect(page.getByText('核心检测通过', { exact: true })).toBeVisible()
  expect(submissions).toEqual([])
  expect((await (await page.request.get('/api/cart')).json()).data.configurations).toEqual([])
  expect(await page.evaluate(() => JSON.parse(sessionStorage.getItem('pc-mall.diy.workspace.v1')!))).toEqual(original)
  await page.getByRole('button', { name: '转入购物车', exact: true }).click()
  await confirmation(page).getByRole('button', { name: '取消', exact: true }).click()
  expect(submissions).toEqual([])
  await page.getByRole('button', { name: '转入购物车', exact: true }).click()
  await confirmation(page).getByRole('button', { name: '确定', exact: true }).click()
  await expect(confirmation(page)).not.toBeVisible()
  expect(submissions).toHaveLength(1)
  expect(submissions[0]).toEqual({
    selections: original.selections.map(({ category, productId }) => ({ category, productId })),
    integratedGraphicsCpuId: original.integratedGraphicsCpuId,
  })
  await page.getByRole('button', { name: /^购物车/ }).click()
  const group = cartPage(page).getByRole('region', { name: '装机方案 1' })
  await expect(group.getByRole('listitem')).toHaveCount(7)
  await expect(group).toContainText('INTEL-I5-14600K')
  await expect(group).toContainText('单价 ¥1,599.00')
  await expect(group.getByRole('checkbox')).toHaveCount(1)
  await expect(group.getByRole('spinbutton')).toHaveCount(0)
  await expect(group.getByRole('textbox')).toHaveCount(0)
  await page.screenshot({ path: test.info().outputPath('configuration-cart.png'), fullPage: true, animations: 'disabled' })
  await group.getByRole('button', { name: '移除整套' }).click()
  await expect(cartPage(page)).toContainText('购物车还是空的')
})

test('a lost successful response keeps the outcome uncertain and retry creates no second group', async ({ page }) => {
  const original = await signedInWorkspace(page)
  const submissions: unknown[] = []
  let lost = false
  await page.route(`**${path}`, async route => {
    if (route.request().method() !== 'POST') { await route.continue(); return }
    submissions.push(route.request().postDataJSON())
    if (lost) { await route.continue(); return }
    lost = true
    const committed = await route.fetch()
    expect(committed.status()).toBe(200)
    await route.abort('connectionreset')
  })
  await page.getByRole('button', { name: '转入购物车', exact: true }).click()
  await confirmation(page).getByRole('button', { name: '确定', exact: true }).click()
  await expect(confirmation(page).getByRole('alert')).toContainText('结果暂未确认')
  await expect(page.getByText('装机方案已加入购物车', { exact: true })).toHaveCount(0)
  expect((await (await page.request.get('/api/cart')).json()).data.configurations).toHaveLength(1)
  await confirmation(page).getByRole('button', { name: '确定', exact: true }).click()
  await expect(confirmation(page)).not.toBeVisible()
  expect(submissions).toHaveLength(2)
  expect(submissions[1]).toEqual(submissions[0])
  const persisted = (await (await page.request.get('/api/cart')).json()).data
  expect(persisted.configurations).toHaveLength(1)
  expect(persisted.configurations[0].items).toHaveLength(8)
  expect(await page.evaluate(() => JSON.parse(sessionStorage.getItem('pc-mall.diy.workspace.v1')!))).toEqual(original)
})

test('stock invalidated after confirmation opens refuses the whole build and preserves the workspace', async ({ page }) => {
  const original = await signedInWorkspace(page)
  const { request } = await import('@playwright/test')
  const admin = await request.newContext({ baseURL: new URL(page.url()).origin })
  const username = process.env.BROWSER_ADMIN_USERNAME
  const password = process.env.BROWSER_ADMIN_PASSWORD
  if (!username || !password) throw new Error('Run scripts/test.ps1 with its isolated test server and administrator.')
  let productPath = ''
  try {
    expect((await write(admin, '/api/auth/login', { username, password })).status()).toBe(200)
    const sku = `WEB14-${Date.now().toString(36)}`
    const created = await write(admin, '/api/admin/products', {
      sku, category: 'PSU', name: '配置组库存测试电源', brand: '测试品牌', model: 'Config-750',
      summary: '配置组验收夹具', description: '仅用于独立测试库', imageUrl: '/images/psu.svg',
      sourceUrl: 'https://example.com/psu', price: '100.00',
      parameters: [{ code: 'ratedPower', value: 750, unit: 'W', sourceUrl: 'https://example.com/psu' }],
    })
    expect(created.status()).toBe(201)
    const id: number = (await created.json()).data.product.id
    productPath = `/api/admin/products/${id}`
    expect((await write(admin, `${productPath}/status`, { revision: 0, status: 'ON_SALE' })).status()).toBe(200)
    expect((await write(admin, `${productPath}/stock`, { delta: 1, reason: '配置组测试备货' })).status()).toBe(200)
    original.selections.find(item => item.category === 'PSU')!.productId = id
    await page.evaluate(value => sessionStorage.setItem('pc-mall.diy.workspace.v1', JSON.stringify(value)), original)
    await page.reload()
    await expect(page.getByText('核心检测通过', { exact: true })).toBeVisible()
    await page.getByRole('button', { name: '转入购物车', exact: true }).click()
    await expect(confirmation(page)).toContainText('配置组库存测试电源')
    expect((await write(admin, `${productPath}/stock`, { delta: -1, reason: '确认后库存失效测试' })).status()).toBe(200)
    await confirmation(page).getByRole('button', { name: '确定', exact: true }).click()
    await expect(confirmation(page).getByRole('alert')).toContainText('整套配置未加入购物车')
    await expect(page.getByText('装机方案已加入购物车', { exact: true })).toHaveCount(0)
    const cart = (await (await page.request.get('/api/cart')).json()).data
    expect(cart.configurations).toEqual([])
    expect(cart.items).toEqual([])
    expect(cart.revision).toBe(0)
    expect(await page.evaluate(() => JSON.parse(sessionStorage.getItem('pc-mall.diy.workspace.v1')!))).toEqual(original)
    await page.screenshot({ path: test.info().outputPath('configuration-refused.png'), animations: 'disabled' })
  } finally {
    if (productPath) {
      const current = (await (await admin.get(productPath)).json()).data
      if (current.product.status === 'ON_SALE') {
        expect((await write(admin, `${productPath}/status`, { revision: current.revision, status: 'OFF_SALE' })).status()).toBe(200)
      }
    }
    await admin.dispose()
  }
})
