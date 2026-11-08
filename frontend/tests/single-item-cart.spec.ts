import { expect, test, type APIRequestContext, type APIResponse, type Page, type Request } from '@playwright/test'

const PASSWORD = 'CartPassword123!'
const cartPage = (page: Page) => page.getByRole('main', { name: '我的购物车' })

async function csrf(api: import('@playwright/test').APIRequestContext) {
  return (await (await api.get('/api/auth/csrf')).json()).data as { token: string; headerName: string }
}

/** Registers a real ordinary account through the public API, then signs in through the UI. */
async function registerUser(page: Page, nickname?: string) {
  const { request } = await import('@playwright/test')
  const api = await request.newContext({ baseURL: new URL(page.url()).origin })
  try {
    const token = await csrf(api)
    const username = `c${Date.now().toString(36)}${Math.random().toString(36).slice(2, 5)}`
    const label = nickname ?? `购物车用户${username.slice(-4)}`
    const response = await api.post('/api/auth/register', {
      data: { username, password: PASSWORD, confirmPassword: PASSWORD, nickname: label },
      headers: { [token.headerName]: token.token },
    })
    expect(response.status()).toBe(201)
    return { username, nickname: label, password: PASSWORD }
  } finally { await api.dispose() }
}

async function signIn(page: Page, credentials: { username: string; password: string }) {
  await page.goto('/#login')
  const dialog = page.getByRole('dialog', { name: '统一登录' })
  await dialog.getByLabel('用户名', { exact: true }).fill(credentials.username)
  await dialog.getByLabel('密码', { exact: true }).fill(credentials.password)
  await dialog.getByRole('button', { name: '登录', exact: true }).click()
  await expect(page.getByRole('button', { name: '退出登录' })).toBeVisible()
}

/** A real on-sale fixture with stock, created by the ephemeral administrator. */
async function adminApi(page: Page) {
  const { request } = await import('@playwright/test')
  const username = process.env.BROWSER_ADMIN_USERNAME
  const password = process.env.BROWSER_ADMIN_PASSWORD
  if (!username || !password) throw new Error('Use scripts/test.ps1 to start an isolated server with ephemeral administrator credentials.')
  const api = await request.newContext({ baseURL: new URL(page.url()).origin })
  const token = await csrf(api)
  expect((await api.post('/api/auth/login', { data: { username, password }, headers: { [token.headerName]: token.token } })).status()).toBe(200)
  return {
    api,
    async post(path: string, data: unknown) {
      const fresh = await csrf(api)
      return api.post(path, { data, headers: { [fresh.headerName]: fresh.token } })
    },
    async detail(path: string) { return (await (await api.get(path)).json()).data },
    async dispose() { await api.dispose() },
  }
}

async function createFixture(admin: Awaited<ReturnType<typeof adminApi>>, stock = 5) {
  const suffix = `${Date.now().toString(36)}${Math.random().toString(36).slice(2, 5)}`
  const sku = `WEB13-${suffix}`
  const name = `购物车测试电源 ${suffix}`
  const created = await admin.post('/api/admin/products', {
    sku, category: 'PSU', name, brand: '测试品牌', model: 'Cart-750',
    summary: '750W 购物车夹具', description: '购物车自动化专用', imageUrl: '/images/psu.svg',
    sourceUrl: 'https://example.com/psu', price: '100.00',
    parameters: [{ code: 'ratedPower', value: 750, unit: 'W', sourceUrl: 'https://example.com/psu' }],
  })
  expect(created.status()).toBe(201)
  const id: number = (await created.json()).data.product.id
  const path = `/api/admin/products/${id}`
  expect((await admin.post(`${path}/status`, { revision: 0, status: 'ON_SALE' })).status()).toBe(200)
  expect((await admin.post(`${path}/stock`, { delta: stock, reason: '购物车夹具备货' })).status()).toBe(200)
  return { id, name, path, sku }
}

async function retire(admin: Awaited<ReturnType<typeof adminApi>>, path: string) {
  try {
    const current = await admin.detail(path)
    if (current.product.status === 'ON_SALE') {
      await admin.post(`${path}/status`, { revision: current.revision, status: 'OFF_SALE' })
    }
  } finally { await admin.dispose() }
}

async function openCart(page: Page) {
  await page.getByRole('button', { name: /^购物车/ }).click()
  await expect(cartPage(page)).toBeVisible()
  await expect(page).toHaveURL(/#cart$/)
  return cartPage(page)
}

test('DIY confirms a whole configuration, shows success, and opens it from the main header', async ({ page }) => {
  await page.goto('/')
  await signIn(page, await registerUser(page))
  const memory = (await (await page.request.get('/api/products?category=MEMORY&keyword=KF560C36BBE2-16')).json()).data[0]
  await page.evaluate(memoryId => sessionStorage.setItem('pc-mall.diy.workspace.v1', JSON.stringify({
    version: 1, revision: 8,
    selections: Object.entries({ CPU: 1, MOTHERBOARD: 3, GPU: 5, MEMORY: memoryId, COOLER: 8, SSD: 9, PSU: 11, CASE: 12 })
      .map(([category, productId]) => ({ category, productId, source: 'DIY' })),
  })), Number(memory.id))
  await page.getByRole('link', { name: 'DIY 装机', exact: true }).click()
  const transfer = page.getByRole('button', { name: '转入购物车', exact: true })
  const confirmation = page.getByRole('dialog', { name: '转入购物车' })
  const header = page.locator('.header')
  await expect(header.getByRole('button', { name: /^购物车/ })).toBeVisible()
  await expect(page.locator('.account-bar').getByRole('button', { name: /^购物车/ })).toHaveCount(0)
  const memoryCategory = page.getByRole('navigation', { name: 'DIY 硬件分类' }).getByRole('button', { name: /^内存 / })
  const memoryQuantity = page.getByRole('group', { name: '内存数量' })
  await memoryCategory.click()
  await expect(page.getByText('主板 4 槽 · 当前最多 4 根')).toBeVisible()
  for (const count of [2, 3, 4]) {
    await memoryQuantity.getByRole('button', { name: '增加内存数量' }).click()
    await expect(memoryQuantity.getByLabel('当前内存根数')).toHaveText(String(count))
  }
  await page.reload()
  await memoryCategory.click()
  await expect(memoryQuantity.getByLabel('当前内存根数')).toHaveText('4')
  await expect(page.getByLabel('当前参考总价')).toHaveText('¥11,451.00')
  await transfer.click()
  const preview = confirmation.getByRole('region', { name: '待加入的装机方案' })
  await expect(preview.getByRole('listitem')).toHaveCount(8)
  await expect(preview).toContainText('Intel Core i5-14600KF')
  await expect(preview.getByRole('listitem').filter({ hasText: 'FURY Beast 16GB' })).toContainText('×4 根')
  await expect(confirmation).toContainText('¥11,451.00')
  await expect(cartPage(page)).toHaveCount(0)
  await page.screenshot({ path: test.info().outputPath('diy-cart-confirmation.png'), animations: 'disabled' })
  await confirmation.getByRole('button', { name: '取消', exact: true }).click()
  expect((await (await page.request.get('/api/cart')).json()).data.configurations).toEqual([])
  await transfer.click()
  await confirmation.getByRole('button', { name: '确定', exact: true }).click()
  await expect(page.getByText('装机方案已加入购物车', { exact: true })).toBeVisible()
  await expect(page.locator('.el-message')).toHaveCSS('position', 'fixed')
  await expect(header.getByLabel('购物车共 1 件')).toBeVisible()

  await seedCart(page, 9, 1)
  const cart = await openCart(page)
  await expect(confirmation).not.toBeVisible()
  await expect(page.getByRole('heading', { name: 'DIY 电脑装机' })).not.toBeVisible()
  const group = cart.getByRole('region', { name: '装机方案 1' })
  await expect(group.getByRole('listitem')).toHaveCount(8)
  await expect(group).toContainText('Intel Core i5-14600KF')
  await expect(group.getByRole('listitem').filter({ hasText: 'FURY Beast 16GB' })).toContainText('×4')
  const summary = cart.getByRole('complementary', { name: '结算信息' })
  await expect(summary.getByLabel('选中参考合计')).toHaveText('¥12,250.00')
  await expect(page.locator('.el-message')).not.toBeVisible()
  await page.screenshot({ path: test.info().outputPath('cart-page.png'), fullPage: true, animations: 'disabled' })
  await page.setViewportSize({ width: 390, height: 844 })
  expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBeLessThanOrEqual(390)
  await cart.getByRole('button', { name: '清空购物车' }).click()
  await page.getByRole('dialog', { name: '清空购物车' }).getByRole('button', { name: '取消' }).click()
  await expect(summary.getByLabel('选中参考合计')).toHaveText('¥12,250.00')
  await group.getByRole('checkbox', { name: '选中装机方案 1' }).uncheck()
  await expect(summary.getByLabel('选中参考合计')).toHaveText('¥799.00')
  await cart.getByRole('button', { name: '删除选中' }).click()
  await expect(cart.getByRole('region', { name: '单品商品' })).toHaveCount(0)
  await expect(group).toBeVisible()
  await expect(summary.getByLabel('选中参考合计')).toHaveText('¥0.00')
  await group.getByRole('checkbox', { name: '选中装机方案 1' }).check()
  await group.getByRole('button', { name: '收起装机方案 1' }).click()
  await expect(group.getByRole('list')).not.toBeVisible()
  await group.getByRole('button', { name: '展开装机方案 1' }).click()
  await expect(group.getByRole('listitem')).toHaveCount(8)
  await group.getByRole('button', { name: '移除整套' }).click()
  await expect(cart).toContainText('购物车还是空的')
  await cart.getByRole('button', { name: '继续购物', exact: true }).click()
  await expect(page).toHaveURL(/#diy$/)
  await expect(transfer).toBeEnabled()
})

/** Keeps the Vue module and every outstanding request alive while moving between dialogs. */
async function navigateInPage(page: Page, hash: string) {
  await page.evaluate(value => { window.location.hash = value }, hash)
}

async function signInInPage(page: Page, credentials: { username: string; password: string }) {
  await navigateInPage(page, 'login')
  const dialog = page.getByRole('dialog', { name: '统一登录' })
  await dialog.getByLabel('用户名', { exact: true }).fill(credentials.username)
  await dialog.getByLabel('密码', { exact: true }).fill(credentials.password)
  await dialog.getByRole('button', { name: '登录', exact: true }).click()
  await expect(page.getByRole('button', { name: '退出登录' })).toBeVisible()
  await expect(dialog).not.toBeVisible()
}

async function cartWrite(api: APIRequestContext, path: string, data?: unknown) {
  const token = await csrf(api)
  return api.post(path, { data, headers: { [token.headerName]: token.token } })
}

async function seedCart(page: Page, productId: number, quantity: number) {
  const response = await cartWrite(page.request, '/api/cart/items', {
    productId, quantity, requestKey: `test-${Date.now()}-${Math.random().toString(36).slice(2)}`,
  })
  expect(response.status()).toBe(200)
  return (await response.json()).data
}

/** Gates an actual server response after its transaction has completed; no timing sleeps. */
async function holdNextResponse(page: Page, path: string, method = 'GET') {
  let release!: () => void
  let received!: (response: APIResponse) => void
  let completed!: () => void
  let handled!: () => void
  const gate = new Promise<void>(resolve => { release = resolve })
  const response = new Promise<APIResponse>(resolve => { received = resolve })
  const settled = new Promise<void>(resolve => { completed = resolve })
  const handlerFinished = new Promise<void>(resolve => { handled = resolve })
  let captured = false
  await page.route(path, async route => {
    if (captured || route.request().method() !== method) { await route.continue(); return }
    captured = true
    const original = route.request()
    const finish = (request: Request) => {
      if (request !== original) return
      page.off('requestfinished', finish)
      page.off('requestfailed', finish)
      completed()
    }
    page.on('requestfinished', finish)
    page.on('requestfailed', finish)
    try {
      const actual = await route.fetch()
      received(actual)
      await gate
      await route.fulfill({ response: actual })
    } finally { handled() }
  })
  return {
    response, release,
    async finish() {
      release()
      await handlerFinished
      await page.unroute(path)
      await settled
      // Let fetch body parsing and Vue's queued render finish before checking that a late
      // response did NOT replace state. Failed/aborted requests use the same rendering boundary.
      await page.evaluate(() => new Promise<void>(resolve => requestAnimationFrame(() => resolve())))
    },
  }
}

test('a signed-in shopper merges the same SKU and reads a server-calculated amount', async ({ page }) => {
  await page.goto('/')
  const credentials = await registerUser(page)
  await signIn(page, credentials)

  await page.goto('/#product/1')
  const detail = page.getByRole('dialog', { name: '商品详情' })
  await detail.getByRole('button', { name: '加入购物车', exact: true }).click()
  await expect(detail.getByRole('status')).toContainText('已加入购物车')
  // The same SKU added twice merges into one line of two units.
  await detail.getByRole('button', { name: '加入购物车', exact: true }).click()
  await expect(page.getByLabel('购物车共 2 件')).toBeVisible()
  await detail.getByRole('button', { name: '关闭详情' }).click()

  const cart = await openCart(page)
  await expect(cart.getByRole('row')).toHaveCount(2)
  await expect(cart.getByRole('row').nth(1)).toContainText('Intel Core i5-14600KF')
  await expect(cart.getByRole('row').nth(1).getByRole('textbox', { name: 'Intel Core i5-14600KF 数量', exact: true })).toHaveValue('2')
  // ¥1,499.00 × 2 is computed by the server, not by the page.
  await expect(cart).toContainText('¥2,998.00')
  await expect(cart.getByLabel('选中参考合计')).toHaveText('¥2,998.00')
})

test('quantity 1..99 and the current stock limit are enforced with visible feedback', async ({ page }) => {
  await page.goto('/')
  const admin = await adminApi(page)
  const fixture = await createFixture(admin, 4)
  try {
    const credentials = await registerUser(page)
    await signIn(page, credentials)
    await page.goto(`/#product/${fixture.id}`)
    const detail = page.getByRole('dialog', { name: '商品详情' })
    await detail.getByRole('button', { name: '加入购物车', exact: true }).click()
    await detail.getByRole('button', { name: '关闭详情' }).click()
    const cart = await openCart(page)
    const line = cart.getByRole('row').nth(1)
    const quantity = line.getByRole('textbox', { name: `${fixture.name} 数量`, exact: true })

    await quantity.fill('0')
    await line.getByRole('button', { name: '更新' }).click()
    await expect(cart.getByRole('alert')).toContainText('1～99')
    await expect(quantity).toHaveValue('1')

    // The field accepts at most two digits, so 100 is not enterable and 99 stays reachable.
    await expect(quantity).toHaveAttribute('maxlength', '2')
    await quantity.fill('100')
    await expect(quantity).toHaveValue('10')

    // Above the current sellable quantity, the server refuses and the stored line is unchanged.
    await quantity.fill('9')
    await line.getByRole('button', { name: '更新' }).click()
    await expect(cart.getByRole('alert')).toContainText('可售库存不足')
    await expect(quantity).toHaveValue('1')

    await quantity.fill('4')
    await line.getByRole('button', { name: '更新' }).click()
    await expect(cart).toContainText('¥400.00')
    await expect(quantity).toHaveValue('4')

    await line.getByRole('button', { name: `移除 ${fixture.name}` }).click()
    await expect(cart).toContainText('购物车还是空的')
  } finally { await retire(admin, fixture.path) }
})

test('the cart survives signing out and in again, and an invalid line stays labelled', async ({ page }) => {
  await page.goto('/')
  const admin = await adminApi(page)
  const fixture = await createFixture(admin, 3)
  try {
    const credentials = await registerUser(page)
    await signIn(page, credentials)
    await page.goto(`/#product/${fixture.id}`)
    await page.getByRole('dialog', { name: '商品详情' }).getByRole('button', { name: '加入购物车', exact: true }).click()
    await page.getByRole('dialog', { name: '商品详情' }).getByRole('button', { name: '关闭详情' }).click()
    await expect(page.getByLabel('购物车共 1 件')).toBeVisible()

    await page.getByRole('button', { name: '退出登录' }).click()
    await expect(page.getByRole('button', { name: '登录', exact: true })).toBeVisible()
    await signIn(page, credentials)
    // A real re-login reads the persisted cart instead of an in-page memory.
    const restored = await openCart(page)
    await expect(restored.getByRole('row').nth(1)).toContainText(fixture.name)

    expect((await admin.post(`${fixture.path}/status`, {
      revision: (await admin.detail(fixture.path)).revision, status: 'OFF_SALE' })).status()).toBe(200)
    await restored.getByRole('button', { name: '刷新价格与库存' }).click()
    await expect(restored.getByRole('row').nth(1)).toContainText('商品已下架，请移除')
    await expect(restored.getByRole('row').nth(1)).toContainText(fixture.name)
    await expect(restored.getByRole('row').nth(1).getByRole('textbox', { name: `${fixture.name} 数量`, exact: true })).toBeDisabled()
    await expect(restored).toContainText('1 项当前不可售，未计入合计')
    await expect(restored.getByLabel('选中参考合计')).toHaveText('¥0.00')

    expect((await admin.post(`${fixture.path}/status`, {
      revision: (await admin.detail(fixture.path)).revision, status: 'ON_SALE' })).status()).toBe(200)
    await restored.getByRole('button', { name: '刷新价格与库存' }).click()
    // Raise the stored line to 3 while stock still allows it, then drop stock to 2: now repairable.
    const line = restored.getByRole('row').nth(1)
    await expect(line.getByRole('textbox', { name: `${fixture.name} 数量`, exact: true })).toBeEnabled()
    await line.getByRole('textbox', { name: `${fixture.name} 数量`, exact: true }).fill('3')
    await line.getByRole('button', { name: '更新' }).click()
    await expect(restored.getByLabel('选中参考合计')).toHaveText('¥300.00')
    expect((await admin.post(`${fixture.path}/stock`, { delta: -1, reason: '购物车库存不足验证' })).status()).toBe(200)
    await restored.getByRole('button', { name: '刷新价格与库存' }).click()
    const shortLine = restored.getByRole('row').nth(1)
    await expect(shortLine).toContainText('可售库存不足：购物车 3 件，当前仅剩 2 件')
    await expect(shortLine.getByRole('textbox', { name: `${fixture.name} 数量`, exact: true })).toBeEnabled()
    await shortLine.getByRole('textbox', { name: `${fixture.name} 数量`, exact: true }).fill('1')
    await shortLine.getByRole('button', { name: '更新' }).click()
    await expect(shortLine).not.toContainText('可售库存不足')
    await expect(shortLine).toContainText('可售 2 件')
    // The repaired line is valid again and priced at the server's current amount.
    await expect(restored.getByRole('alert')).toHaveCount(0)
    await expect(restored.getByLabel('选中参考合计')).toHaveText('¥100.00')

    await restored.getByRole('row').nth(1).getByRole('button', { name: `移除 ${fixture.name}` }).click()
    await expect(restored).toContainText('购物车还是空的')
  } finally { await retire(admin, fixture.path) }
})

test('a visitor is asked to sign in and cannot reach another owner cart', async ({ page }) => {
  await page.goto('/#product/1')
  const detail = page.getByRole('dialog', { name: '商品详情' })
  await detail.getByRole('button', { name: '登录后加入购物车', exact: true }).click()
  await expect(page.getByRole('dialog', { name: '统一登录' })).toBeVisible()
  await page.getByRole('dialog', { name: '统一登录' }).getByRole('button', { name: 'Close this dialog' }).click()
  // There is no cart entry at all before signing in.
  await expect(page.getByRole('button', { name: /^购物车/ })).toHaveCount(0)

  await page.goto('/')
  const first = await registerUser(page)
  await signIn(page, first)
  await page.goto('/#product/1')
  await page.getByRole('dialog', { name: '商品详情' }).getByRole('button', { name: '加入购物车', exact: true }).click()
  await page.getByRole('dialog', { name: '商品详情' }).getByRole('button', { name: '关闭详情' }).click()
  await page.getByRole('button', { name: '退出登录' }).click()

  // A second real account starts with an empty cart and never sees the first account's line.
  const second = await registerUser(page)
  await signIn(page, second)
  const cart = await openCart(page)
  await expect(cart).toContainText('购物车还是空的')
  await expect(cart).not.toContainText('Intel Core i5-14600KF')
  // The first account's line id cannot be reached by the second one, even when submitted directly.
  await page.goto('/')
  const firstLineId = await (async () => {
    const api = await (await import('@playwright/test')).request.newContext({ baseURL: new URL(page.url()).origin })
    try {
      const token = await csrf(api)
      expect((await api.post('/api/auth/login', {
        data: { username: first.username, password: PASSWORD }, headers: { [token.headerName]: token.token } })).status()).toBe(200)
      return (await (await api.get('/api/cart')).json()).data.items[0].id as number
    } finally { await api.dispose() }
  })()
  const token = await csrf(page.request)
  for (const [path, method] of [[`/api/cart/items/${firstLineId}/quantity`, 'POST'], [`/api/cart/items/${firstLineId}`, 'DELETE']] as const) {
    const response = await page.request.fetch(path, {
      method, headers: { [token.headerName]: token.token, 'Content-Type': 'application/json' },
      data: method === 'POST' ? { quantity: 99 } : undefined,
    })
    expect(response.status()).toBe(404)
    expect((await response.json()).code).toBe('CART_ITEM_NOT_FOUND')
  }
  // The second account's own cart is still empty after those rejected attempts.
  const reopened = await openCart(page)
  await expect(reopened).toContainText('购物车还是空的')
})

test('a refused add leaves the existing line untouched and explains why', async ({ page }) => {
  await page.goto('/')
  const admin = await adminApi(page)
  const fixture = await createFixture(admin, 2)
  try {
    const credentials = await registerUser(page)
    await signIn(page, credentials)
    await page.goto(`/#product/${fixture.id}`)
    const detail = page.getByRole('dialog', { name: '商品详情' })
    await detail.getByRole('button', { name: '加入购物车', exact: true }).click()
    await detail.getByRole('button', { name: '加入购物车', exact: true }).click()
    // The third unit exceeds the two on hand; the message is the server rule, not page guesswork.
    await detail.getByRole('button', { name: '加入购物车', exact: true }).click()
    await expect(detail.getByRole('alert')).toContainText('可售库存不足')
    await expect(page.getByLabel('购物车共 2 件')).toBeVisible()

    await detail.getByRole('button', { name: '关闭详情' }).click()
    const cart = await openCart(page)
    await expect(cart.getByRole('row').nth(1)).toContainText('2')
    await expect(cart).toContainText('¥200.00')
  } finally { await retire(admin, fixture.path) }
})

test('an expired session asks for a fresh sign-in instead of a rule error', async ({ page }) => {
  await page.goto('/')
  const credentials = await registerUser(page)
  await signIn(page, credentials)
  await page.goto('/#product/1')
  const detail = page.getByRole('dialog', { name: '商品详情' })
  await expect(detail.getByRole('button', { name: '加入购物车', exact: true })).toBeVisible()
  // The session is gone while the page still believes it is signed in.
  const token = await csrf(page.request)
  expect((await page.request.post('/api/auth/logout', { headers: { [token.headerName]: token.token } })).status()).toBe(200)
  await page.evaluate(() => window.dispatchEvent(new Event('focus')))
  await expect(page.locator('.account-bar').getByRole('button', { name: '登录', exact: true })).toBeVisible()
  await expect(detail.getByRole('button', { name: '登录后加入购物车', exact: true })).toBeVisible()
})


test('an in-flight add cannot be submitted a second time by an impatient click', async ({ page }) => {
  await page.goto('/')
  const admin = await adminApi(page)
  const fixture = await createFixture(admin, 10)
  try {
    const credentials = await registerUser(page)
    await signIn(page, credentials)
    await page.goto(`/#product/${fixture.id}`)
    const detail = page.getByRole('dialog', { name: '商品详情' })
    // Hold the add request open, then try to click again: only one submission may reach the server.
    let release!: () => void
    let started!: () => void
    const gate = new Promise<void>(resolve => { release = resolve })
    const pending = new Promise<void>(resolve => { started = resolve })
    const attempts: string[] = []
    await page.route('**/api/cart/items', async route => {
      if (route.request().method() !== 'POST') { await route.continue(); return }
      attempts.push(route.request().postData() ?? '')
      const response = await route.fetch()
      started()
      await gate
      await route.fulfill({ response })
    })
    const add = detail.getByRole('button', { name: '加入购物车', exact: true })
    await add.click()
    await pending
    // The disabled state is the guard: while it holds, a second press cannot dispatch at all.
    await expect(add).toBeDisabled()
    release()
    await page.unrouteAll({ behavior: 'wait' })
    await expect(page.getByLabel('购物车共 1 件')).toBeVisible()
    // Exactly one submission carried exactly one client request key.
    expect(attempts).toHaveLength(1)
    expect(JSON.parse(attempts[0])).toMatchObject({ productId: fixture.id, quantity: 1 })
    expect(String(JSON.parse(attempts[0]).requestKey)).toMatch(/^cart-/)
  } finally { await retire(admin, fixture.path) }
})

test('a delayed cart read cannot cross an in-page sign-out and sign-in', async ({ page }) => {
  await page.goto('/')
  const first = await registerUser(page)
  const second = await registerUser(page)
  await signInInPage(page, first)
  await seedCart(page, 1, 1)
  const before = await openCart(page)
  await expect(before.getByRole('row').nth(1)).toContainText('Intel Core i5-14600KF')

  const held = await holdNextResponse(page, '**/api/cart')
  try {
    await before.getByRole('button', { name: '刷新价格与库存' }).click()
    expect((await held.response).status()).toBe(200)
    await before.getByRole('button', { name: '继续购物', exact: true }).click()
    await page.getByRole('button', { name: '退出登录' }).click()
    await expect(page.locator('.account-bar').getByRole('button', { name: '登录', exact: true })).toBeVisible()
    await signInInPage(page, second)
    const after = await openCart(page)
    await held.finish()
    await expect(after).toContainText('购物车还是空的')
    await expect(page.getByLabel('购物车共 1 件')).toHaveCount(0)
  } finally { held.release(); await page.unrouteAll({ behavior: 'wait' }) }
})

for (const operation of ['add', 'update', 'remove'] as const) {
  test(`a delayed cart ${operation} cannot overwrite the account found on focus`, async ({ page }) => {
    await page.goto('/')
    const first = await registerUser(page)
    const second = await registerUser(page)
    await signInInPage(page, first)
    if (operation !== 'add') await seedCart(page, 1, 1)
    const initial = await openCart(page)
    if (operation !== 'add') await expect(initial.getByRole('row').nth(1)).toContainText('Intel Core i5-14600KF')
    else await expect(initial).toContainText('购物车还是空的')
    await initial.getByRole('button', { name: '继续购物', exact: true }).click()

    const path = operation === 'add' ? '**/api/cart/items' : operation === 'update'
      ? '**/api/cart/items/*/quantity' : '**/api/cart/items/*'
    const held = await holdNextResponse(page, path, operation === 'remove' ? 'DELETE' : 'POST')
    try {
      if (operation === 'add') {
        await navigateInPage(page, 'product/1')
        await page.getByRole('dialog', { name: '商品详情' }).getByRole('button', { name: '加入购物车', exact: true }).click()
      } else {
        const current = await openCart(page)
        const line = current.getByRole('row').nth(1)
        if (operation === 'update') {
          await line.getByRole('textbox', { name: 'Intel Core i5-14600KF 数量', exact: true }).fill('5')
          await line.getByRole('button', { name: '更新' }).click()
        } else await line.getByRole('button', { name: '移除 Intel Core i5-14600KF' }).click()
      }
      expect((await held.response).status()).toBe(200)
      // A second tab shares the session cookie, while this page and its request remain alive.
      expect((await cartWrite(page.request, '/api/auth/logout', {})).status()).toBe(200)
      expect((await cartWrite(page.request, '/api/auth/login', { username: second.username, password: second.password })).status()).toBe(200)
      await seedCart(page, 1, 3)
      await page.evaluate(() => window.dispatchEvent(new Event('focus')))
      await expect(page.locator('.account-name')).toContainText(second.nickname)
      await expect(page.getByLabel('购物车共 3 件')).toBeVisible()
      await held.finish()
      await navigateInPage(page, '')
      // Check the shared view before reopening: mounting the cart would issue a new GET and
      // could otherwise conceal the delayed write having briefly replaced the second account.
      await expect(page.getByLabel('购物车共 3 件')).toBeVisible()
      const after = await openCart(page)
      await expect(after.getByRole('row').nth(1).getByRole('textbox', { name: 'Intel Core i5-14600KF 数量', exact: true })).toHaveValue('3')
      await expect(page.getByLabel('购物车共 3 件')).toBeVisible()
    } finally { held.release(); await page.unrouteAll({ behavior: 'wait' }) }
  })
}

test('an old add waiting for CSRF never submits to a newly signed-in account', async ({ page }) => {
  await page.goto('/')
  const first = await registerUser(page)
  const second = await registerUser(page)
  await signInInPage(page, first)
  await navigateInPage(page, 'product/1')
  const detail = page.getByRole('dialog', { name: '商品详情' })
  const held = await holdNextResponse(page, '**/api/auth/csrf')
  const posts: string[] = []
  page.on('request', request => {
    if (new URL(request.url()).pathname === '/api/cart/items' && request.method() === 'POST') posts.push(request.postData() ?? '')
  })
  try {
    const add = detail.getByRole('button', { name: '加入购物车', exact: true })
    await add.click()
    expect((await held.response).status()).toBe(200)
    expect((await cartWrite(page.request, '/api/auth/logout', {})).status()).toBe(200)
    expect((await cartWrite(page.request, '/api/auth/login', { username: second.username, password: second.password })).status()).toBe(200)
    await page.evaluate(() => window.dispatchEvent(new Event('focus')))
    await expect(page.locator('.account-name')).toContainText(second.nickname)
    await held.finish()
    // The original handler has finished, so a wrongly dispatched POST cannot hide behind the gate.
    await expect(add).toBeEnabled()
    const current = await page.request.get('/api/cart')
    expect((await current.json()).data.items).toEqual([])
    expect(posts).toHaveLength(0)
  } finally { held.release(); await page.unrouteAll({ behavior: 'wait' }) }
})

test('retrying an add whose success response was lost reuses its key and reads the current cart', async ({ page }) => {
  await page.goto('/')
  const credentials = await registerUser(page)
  await signInInPage(page, credentials)
  await navigateInPage(page, 'product/1')
  const detail = page.getByRole('dialog', { name: '商品详情' })
  const attempts: { requestKey: string; productId: number; quantity: number }[] = []
  await page.route('**/api/cart/items', async route => {
    if (route.request().method() !== 'POST') { await route.continue(); return }
    attempts.push(route.request().postDataJSON())
    const response = await route.fetch()
    expect(response.status()).toBe(200)
    if (attempts.length === 1) await route.abort('failed')
    else await route.fulfill({ response })
  })
  const add = detail.getByRole('button', { name: '加入购物车', exact: true })
  await add.click()
  await expect(detail.getByRole('alert')).toBeVisible()
  // Another real change makes the idempotency replay's first snapshot stale.
  const first = (await (await page.request.get('/api/cart')).json()).data
  expect(first.items[0].quantity).toBe(1)
  expect((await cartWrite(page.request, `/api/cart/items/${first.items[0].id}/quantity`, { quantity: 4 })).status()).toBe(200)
  const held = await holdNextResponse(page, '**/api/cart')
  try {
    await add.click()
    expect((await held.response).status()).toBe(200)
    // Even while the reconciliation read is pending, the replay must not show its old snapshot.
    await expect(page.getByLabel('购物车共 1 件')).toHaveCount(0)
    await held.finish()
  } finally { await held.finish() }
  await expect(detail.getByRole('status')).toContainText('已加入购物车')
  await expect(page.getByLabel('购物车共 4 件')).toBeVisible()
  expect(attempts).toHaveLength(2)
  expect(attempts[1]).toEqual(attempts[0])
  expect((await (await page.request.get('/api/cart')).json()).data.items[0].quantity).toBe(4)
  // A deliberate add after the successful retry is a new operation.
  await add.click()
  await expect(page.getByLabel('购物车共 5 件')).toBeVisible()
  expect(attempts).toHaveLength(3)
  expect(attempts[2].requestKey).not.toBe(attempts[0].requestKey)
  await page.unrouteAll({ behavior: 'wait' })
})

test('a CSRF rejection while retrying an uncertain add preserves its original request key', async ({ page }) => {
  await page.goto('/')
  const credentials = await registerUser(page)
  await signInInPage(page, credentials)
  await navigateInPage(page, 'product/1')
  const detail = page.getByRole('dialog', { name: '商品详情' })
  const add = detail.getByRole('button', { name: '加入购物车', exact: true })
  const attempts: { requestKey: string; productId: number; quantity: number }[] = []
  const statuses: number[] = []
  await page.route('**/api/cart/items', async route => {
    if (route.request().method() !== 'POST') { await route.continue(); return }
    attempts.push(route.request().postDataJSON())
    const response = await route.fetch()
    statuses.push(response.status())
    if (attempts.length === 1) await route.abort('failed')
    else await route.fulfill({ response })
  })
  let held: Awaited<ReturnType<typeof holdNextResponse>> | undefined
  try {
    await add.click()
    await expect(detail.getByRole('alert')).toContainText('结果暂未确认')
    expect((await (await page.request.get('/api/cart')).json()).data.items[0].quantity).toBe(1)

    held = await holdNextResponse(page, '**/api/auth/csrf')
    await add.click()
    expect((await held.response).status()).toBe(200)
    // A real same-account reauthentication invalidates the captured CSRF token without
    // changing the account ID or clearing the page's unknown first operation.
    expect((await cartWrite(page.request, '/api/auth/login', {
      username: credentials.username, password: credentials.password,
    })).status()).toBe(200)
    held.release()
    await expect(detail.getByRole('alert')).toContainText('安全凭证已失效')
    const rejection = await detail.getByRole('alert').innerText()
    expect(statuses).toEqual([200, 403])
    expect(attempts[1]).toEqual(attempts[0])

    await add.click()
    await expect(detail.getByRole('status')).toContainText('已加入购物车')
    expect(attempts).toHaveLength(3)
    expect(attempts[2]).toEqual(attempts[0])
    expect((await (await page.request.get('/api/cart')).json()).data.items[0].quantity).toBe(1)
    expect(rejection).toContain('首次加购结果仍未确认')
    // Only a click after the original operation has been confirmed is a new add.
    await add.click()
    await expect(page.getByLabel('购物车共 2 件')).toBeVisible()
    expect(attempts).toHaveLength(4)
    expect(attempts[3].requestKey).not.toBe(attempts[0].requestKey)
  } finally { held?.release(); await page.unrouteAll({ behavior: 'wait' }) }
})

test('a delayed older cart read cannot roll back a successful add', async ({ page }) => {
  await page.goto('/')
  const credentials = await registerUser(page)
  await signInInPage(page, credentials)
  await seedCart(page, 1, 1)
  const cart = await openCart(page)
  await expect(cart.getByRole('textbox', { name: 'Intel Core i5-14600KF 数量', exact: true })).toHaveValue('1')
  const held = await holdNextResponse(page, '**/api/cart')
  try {
    await cart.getByRole('button', { name: '刷新价格与库存' }).click()
    expect((await held.response).status()).toBe(200)
    await cart.getByRole('button', { name: '继续购物', exact: true }).click()
    await navigateInPage(page, 'product/1')
    const detail = page.getByRole('dialog', { name: '商品详情' })
    await detail.getByRole('button', { name: '加入购物车', exact: true }).click()
    await expect(page.getByLabel('购物车共 2 件')).toBeVisible()
    await held.finish()
    await expect(page.getByLabel('购物车共 2 件')).toBeVisible()
    await detail.getByRole('button', { name: '关闭详情' }).click()
    const after = await openCart(page)
    await expect(after.getByRole('textbox', { name: 'Intel Core i5-14600KF 数量', exact: true })).toHaveValue('2')
    expect((await (await page.request.get('/api/cart')).json()).data.revision).toBe(2)
  } finally { held.release(); await page.unrouteAll({ behavior: 'wait' }) }
})

test('out-of-order cart writes preserve newer lines, independent busy states and saved quantities', async ({ page }) => {
  await page.goto('/')
  const admin = await adminApi(page)
  const first = await createFixture(admin, 10)
  const second = await createFixture(admin, 10)
  try {
    const credentials = await registerUser(page)
    await signInInPage(page, credentials)
    const seeded = await seedCart(page, first.id, 1)
    await seedCart(page, second.id, 1)
    const firstId = seeded.items[0].id
    const cart = await openCart(page)
    const firstRow = cart.getByRole('row').filter({ hasText: first.name })
    const secondRow = cart.getByRole('row').filter({ hasText: second.name })
    await expect(firstRow.getByRole('textbox', { name: `${first.name} 数量`, exact: true })).toHaveValue('1')
    const held = await holdNextResponse(page, `**/api/cart/items/${firstId}/quantity`, 'POST')
    try {
      await firstRow.getByRole('textbox', { name: `${first.name} 数量`, exact: true }).fill('5')
      await firstRow.getByRole('button', { name: '更新' }).click()
      expect((await held.response).status()).toBe(200)
      await secondRow.getByRole('textbox', { name: `${second.name} 数量`, exact: true }).fill('3')
      await secondRow.getByRole('button', { name: '更新' }).click()
      await expect(page.getByLabel('购物车共 8 件')).toBeVisible()
      // Finishing the second row must not unlock the still-pending first row.
      await expect(firstRow.getByRole('textbox', { name: `${first.name} 数量`, exact: true })).toBeDisabled()
      await held.finish()
      await expect(firstRow.getByRole('textbox', { name: `${first.name} 数量`, exact: true })).toHaveValue('5')
      await expect(firstRow.getByRole('textbox', { name: `${first.name} 数量`, exact: true })).toBeEnabled()
      await expect(secondRow.getByRole('textbox', { name: `${second.name} 数量`, exact: true })).toHaveValue('3')
      await expect(page.getByLabel('购物车共 8 件')).toBeVisible()
      expect((await (await page.request.get('/api/cart')).json()).data.revision).toBe(4)
    } finally { held.release(); await page.unrouteAll({ behavior: 'wait' }) }
  } finally {
    const current = await admin.detail(second.path)
    if (current.product.status === 'ON_SALE') await admin.post(`${second.path}/status`, { revision: current.revision, status: 'OFF_SALE' })
    await retire(admin, first.path)
  }
})
