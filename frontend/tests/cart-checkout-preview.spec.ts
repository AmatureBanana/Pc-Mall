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

test('whole-group DIY editing can be cancelled and saved without creating a second group', async ({ page }) => {
  const original = await signedInWorkspace(page)
  const response = await write(page.request, path, { selections: original.selections.map(({ category, productId }) => ({ category, productId })) })
  expect(response.status()).toBe(200)
  const before = (await response.json()).data
  await page.getByRole('button', { name: /^购物车/ }).click()
  const group = () => cartPage(page).getByRole('region', { name: '装机方案 1' })
  await group().getByRole('button', { name: '返回 DIY 编辑' }).click()
  await expect(page.getByRole('button', { name: '保存整组修改' })).toBeVisible()
  await page.getByRole('button', { name: '取消整组编辑' }).click()
  expect((await (await page.request.get('/api/cart')).json()).data).toEqual(before)
  await group().getByRole('button', { name: '返回 DIY 编辑' }).click()
  await page.getByRole('button', { name: '替换 内存', exact: true }).click()
  await page.getByRole('button', { name: '增加内存数量', exact: true }).click()
  await page.getByRole('button', { name: '保存整组修改' }).click()
  const dialog = page.getByRole('dialog', { name: '保存整组修改' })
  await dialog.getByRole('button', { name: '确定', exact: true }).click()
  await expect(cartPage(page)).toBeVisible()
  await expect(group()).toContainText('×2')
  const after = (await (await page.request.get('/api/cart')).json()).data
  expect(after.configurations).toHaveLength(1)
  expect(after.configurations[0].id).toBe(before.configurations[0].id)
  await page.screenshot({ path: test.info().outputPath('cart-edited.png'), fullPage: true, animations: 'disabled' })
})


test('mixed server quote reconfirms a concurrent price change and unselected invalid items do not block a group', async ({ page }) => {
  const original = await signedInWorkspace(page)
  const { request } = await import('@playwright/test')
  const admin = await request.newContext({ baseURL: new URL(page.url()).origin })
  const username = process.env.BROWSER_ADMIN_USERNAME
  const password = process.env.BROWSER_ADMIN_PASSWORD
  if (!username || !password) throw new Error('Use the isolated scripts/test.ps1 environment.')
  let productPath = ''
  try {
    expect((await write(admin, '/api/auth/login', { username, password })).status()).toBe(200)
    const created = await write(admin, '/api/admin/products', {
      sku: `WEB15-${Date.now().toString(36)}`, category: 'PSU', name: '预结算测试电源', brand: '测试品牌', model: 'Preview-750',
      summary: '预结算验收夹具', description: '仅用于独立测试库', imageUrl: '/images/psu.svg',
      sourceUrl: 'https://example.com/psu', price: '100.00',
      parameters: [{ code: 'ratedPower', value: 750, unit: 'W', sourceUrl: 'https://example.com/psu' }],
    })
    expect(created.status()).toBe(201)
    const id = (await created.json()).data.product.id
    productPath = `/api/admin/products/${id}`
    expect((await write(admin, `${productPath}/status`, { revision: 0, status: 'ON_SALE' })).status()).toBe(200)
    expect((await write(admin, `${productPath}/stock`, { delta: 4, reason: '预结算测试备货' })).status()).toBe(200)
    expect((await write(page.request, path, { selections: original.selections.map(({ category, productId }) => ({ category, productId })) })).status()).toBe(200)
    expect((await write(page.request, '/api/cart/items', { productId: id, quantity: 2, requestKey: `preview-${Date.now()}` })).status()).toBe(200)
    await page.getByRole('button', { name: /^购物车/ }).click()
    await cartPage(page).getByRole('button', { name: '预结算', exact: true }).click()
    const dialog = page.getByRole('dialog', { name: '确认当前报价' })
    await expect(dialog.getByLabel('服务端选中总额')).toHaveText('¥10,302.50')
    const detail = (await (await admin.get(productPath)).json()).data
    expect((await write(admin, `${productPath}/price`, { revision: detail.product.revision, price: '100.01', reason: '报价二次变化验收' })).status()).toBe(200)
    await dialog.getByRole('button', { name: '确认并重新核验报价' }).click()
    await expect(dialog.getByRole('alert')).toContainText('发生变化')
    await expect(dialog).toContainText('100.00 → ¥100.01')
    await expect(dialog.getByLabel('服务端选中总额')).toHaveText('¥10,302.52')
    await dialog.getByRole('button', { name: '确认并重新核验报价' }).click()
    await expect(dialog.getByRole('status')).toContainText('预检查通过')
    await page.screenshot({ path: test.info().outputPath('checkout-confirmed.png'), fullPage: true, animations: 'disabled' })
    await dialog.getByRole('button', { name: '返回购物车' }).click()
    expect((await write(admin, `${productPath}/stock`, { delta: -4, reason: '未选中失效项验收' })).status()).toBe(200)
    await cartPage(page).getByRole('button', { name: '刷新价格与库存' }).click()
    await expect(cartPage(page).getByText('商品暂时缺货，数量已保留。')).toBeVisible()
    await cartPage(page).getByRole('checkbox', { name: '选中 预结算测试电源', exact: true }).uncheck()
    await cartPage(page).getByRole('button', { name: '预结算', exact: true }).click()
    await expect(dialog.getByLabel('服务端选中总额')).toHaveText('¥10,102.50')
    await dialog.getByRole('button', { name: '确认并重新核验报价' }).click()
    await expect(dialog.getByRole('status')).toContainText('预检查通过')
    expect((await (await page.request.get('/api/cart')).json()).data.lineCount).toBe(2)
    expect((await (await admin.get(productPath)).json()).data.product.stock).toBe(0)
  } finally {
    if (productPath) {
      const detail = (await (await admin.get(productPath)).json()).data
      expect((await write(admin, `${productPath}/status`, { revision: detail.product.revision, status: 'OFF_SALE' })).status()).toBe(200)
    }
    await admin.dispose()
  }
})



test('a concurrent cart write refuses the stale DIY editor and preserves the old group', async ({ page }) => {
  const original = await signedInWorkspace(page)
  const added = await write(page.request, path, { selections: original.selections.map(({ category, productId }) => ({ category, productId })) })
  expect(added.status()).toBe(200)
  const before = (await added.json()).data
  await page.getByRole('button', { name: /^购物车/ }).click()
  await cartPage(page).getByRole('region', { name: '装机方案 1' }).getByRole('button', { name: '返回 DIY 编辑' }).click()
  const changed = await write(page.request, '/api/cart/items', { productId: 1, quantity: 1, requestKey: `editor-${Date.now()}` })
  expect(changed.status()).toBe(200)
  await page.getByRole('button', { name: '替换 内存', exact: true }).click()
  await page.getByRole('button', { name: '增加内存数量', exact: true }).click()
  await page.getByRole('button', { name: '保存整组修改' }).click()
  const dialog = page.getByRole('dialog', { name: '保存整组修改' })
  await dialog.getByRole('button', { name: '确定', exact: true }).click()
  await expect(dialog.getByRole('alert')).toContainText('并发修改')
  expect((await (await page.request.get('/api/cart')).json()).data.configurations).toEqual(before.configurations)
  await dialog.getByRole('button', { name: '取消', exact: true }).click()
  await page.getByRole('button', { name: '取消整组编辑' }).click()
  await expect(cartPage(page)).toBeVisible()
  await expect(cartPage(page).getByRole('region', { name: '装机方案 1' })).not.toContainText('×2')
})


test('editing survives storefront navigation and cancellation restores the prior independent DIY workspace', async ({ page }) => {
  const original = await signedInWorkspace(page)
  await page.getByRole('button', { name: '替换 内存', exact: true }).click()
  await page.getByRole('button', { name: '增加内存数量', exact: true }).click()
  await expect(page.getByLabel('当前内存根数')).toHaveText('2')
  const response = await write(page.request, path, { selections: original.selections.map(({ category, productId }) => ({ category, productId })) })
  expect(response.status()).toBe(200)
  const before = (await response.json()).data
  await page.getByRole('button', { name: /^购物车/ }).click()
  await cartPage(page).getByRole('region', { name: '装机方案 1' }).getByRole('button', { name: '返回 DIY 编辑' }).click()
  await page.getByRole('button', { name: '替换 内存', exact: true }).click()
  await expect(page.getByLabel('当前内存根数')).toHaveText('1')
  await page.getByRole('button', { name: '增加内存数量', exact: true }).click()
  await expect(page.getByLabel('当前内存根数')).toHaveText('2')
  await page.getByRole('button', { name: '增加内存数量', exact: true }).click()
  await expect(page.getByLabel('当前内存根数')).toHaveText('3')
  await page.getByRole('link', { name: '商品浏览', exact: true }).click()
  await expect(page.getByRole('region', { name: '商品列表', exact: true })).toBeVisible()
  await page.getByRole('link', { name: 'DIY 装机', exact: true }).click()
  await page.getByRole('button', { name: '替换 内存', exact: true }).click()
  await expect(page.getByLabel('当前内存根数')).toHaveText('3')
  await page.getByRole('button', { name: '取消整组编辑' }).click()
  await expect(cartPage(page)).toBeVisible()
  expect((await (await page.request.get('/api/cart')).json()).data).toEqual(before)
  expect(await page.evaluate(() => JSON.parse(sessionStorage.getItem('pc-mall.diy.workspace.v1')!).memoryQuantity)).toBe(2)
})
